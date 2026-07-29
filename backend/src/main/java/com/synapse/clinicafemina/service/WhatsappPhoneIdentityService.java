package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import com.synapse.clinicafemina.integration.whatsapp.WhatsappPhoneNormalizer;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.MensagemRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsappPhoneIdentityService {

    public static final String CONFLICT_MESSAGE =
            "Foram encontrados cadastros conflitantes para este telefone. "
                    + "Selecione o paciente pela lista de pacientes ou solicite a revisão dos cadastros.";
    private static final String PLACEHOLDER = "Contato WhatsApp";

    private final PacienteRepository pacienteRepository;
    private final AtendimentoRepository atendimentoRepository;
    private final MensagemRepository mensagemRepository;

    public PhoneIdentity identify(String rawPhone) {
        String normalized = WhatsappPhoneNormalizer.normalize(rawPhone);
        return new PhoneIdentity(normalized, WhatsappPhoneNormalizer.safeAliases(normalized));
    }

    public Optional<PatientResolution> resolvePatient(Long clinicId, String rawPhone) {
        return resolvePatient(clinicId, identify(rawPhone));
    }

    public Optional<PatientResolution> resolvePatient(Long clinicId, PhoneIdentity identity) {
        List<Paciente> candidates = pacienteRepository.findByClinicaIdAndTelefoneNormalizadoIn(
                        clinicId, identity.aliases()
                ).stream()
                .filter(patient -> patient.getDeletadoEm() == null)
                .toList();
        if (candidates.isEmpty()) {
            candidates = pacienteRepository.findByClinicaIdAndTelefoneNormalizado(
                            clinicId, identity.normalized()
                    )
                    .filter(patient -> patient.getDeletadoEm() == null)
                    .map(List::of)
                    .orElseGet(List::of);
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        if (candidates.size() == 1) {
            return Optional.of(resolution(candidates.getFirst(), identity));
        }
        return Optional.of(resolveConflictSafely(clinicId, identity, candidates));
    }

    /**
     * Migra sob demanda a confirmação que já existe no histórico: antes desta correção, uma
     * mensagem inbound só era anexada ao paciente por igualdade exata de telefone. Assim, um
     * atendimento reutilizado com inbound persistido comprova que o telefone cadastral daquele
     * paciente foi aceito como remetente pelo provider.
     */
    public boolean applyHistoricalInboundChatId(
            Atendimento attendance,
            PhoneIdentity requestedIdentity
    ) {
        if (attendance.getWhatsappChatId() != null && !attendance.getWhatsappChatId().isBlank()) {
            return false;
        }
        String registeredPhone = attendance.getPaciente().getTelefoneNormalizado();
        if (!requestedIdentity.aliases().contains(registeredPhone)) {
            return false;
        }
        long inbound = mensagemRepository.countByAtendimentoAndDirecao(
                attendance.getClinica().getId(), attendance.getId(), "ENTRADA"
        );
        if (inbound == 0) {
            return false;
        }
        attendance.setWhatsappChatId(registeredPhone);
        atendimentoRepository.save(attendance);
        log.info(
                "whatsappChatId recuperado de inbound historico exato. clinicaId={} pacienteId={} "
                        + "atendimentoId={} origemResolucao=INBOUND_HISTORICO_EXATO finalTelefone={}",
                attendance.getClinica().getId(), attendance.getPaciente().getId(),
                attendance.getId(), maskPhone(registeredPhone)
        );
        return true;
    }

    private PatientResolution resolveConflictSafely(
            Long clinicId,
            PhoneIdentity identity,
            List<Paciente> candidates
    ) {
        List<CandidateEvidence> evidence = candidates.stream()
                .map(patient -> evidence(clinicId, patient))
                .toList();
        List<CandidateEvidence> established = evidence.stream()
                .filter(item -> !item.provisional())
                .toList();
        boolean remainingAreProvisional = established.size() == 1
                && evidence.stream()
                .filter(item -> !item.patient().getId().equals(established.getFirst().patient().getId()))
                .allMatch(CandidateEvidence::provisional);
        if (remainingAreProvisional) {
            CandidateEvidence winner = established.getFirst();
            log.warn(
                    "Identidade WhatsApp duplicada resolvida sem merge. clinicaId={} pacienteId={} "
                            + "candidatos={} inbound={} chatConfirmado={} origemResolucao={}",
                    clinicId, winner.patient().getId(), evidence.size(), winner.inboundMessages(),
                    winner.confirmedChat(), origin(winner.patient(), identity)
            );
            return resolution(winner.patient(), identity);
        }
        log.warn(
                "Conflito de identidade WhatsApp. clinicaId={} candidatos={} detalhes={}",
                clinicId,
                evidence.size(),
                evidence.stream().map(CandidateEvidence::safeDescription).toList()
        );
        throw new IllegalStateException(CONFLICT_MESSAGE);
    }

    private CandidateEvidence evidence(Long clinicId, Paciente patient) {
        List<Atendimento> attendances = atendimentoRepository.findHistoricoPaciente(
                clinicId, patient.getId()
        );
        long inbound = mensagemRepository.countByPacienteAndDirecao(
                clinicId, patient.getId(), "ENTRADA"
        );
        boolean confirmedChat = attendances.stream()
                .map(Atendimento::getWhatsappChatId)
                .anyMatch(value -> value != null && !value.isBlank());
        boolean provisional = patient.getExternalSource() == ExternalProviderType.WHATSAPP
                && isPlaceholder(patient)
                && inbound == 0
                && !confirmedChat
                && !hasClinicalData(patient);
        return new CandidateEvidence(patient, inbound, confirmedChat, provisional);
    }

    private boolean isPlaceholder(Paciente patient) {
        String name = patient.getNome();
        if (name == null || name.isBlank() || "null".equalsIgnoreCase(name.trim())
                || PLACEHOLDER.equalsIgnoreCase(name.trim())) {
            return true;
        }
        String phone = patient.getTelefoneNormalizado();
        return name.trim().equals(phone) || name.trim().equals("+" + phone);
    }

    private boolean hasClinicalData(Paciente patient) {
        return patient.getExternalSource() == ExternalProviderType.MEDWARE
                || patient.getExternalSource() == ExternalProviderType.DARWIN
                || hasText(patient.getCpf())
                || hasText(patient.getDataNascimento())
                || hasText(patient.getEmail())
                || hasText(patient.getEndereco())
                || patient.getMedicoPrincipal() != null
                || hasText(patient.getExternalPayload());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private PatientResolution resolution(Paciente patient, PhoneIdentity identity) {
        return new PatientResolution(patient, identity, origin(patient, identity));
    }

    private String origin(Paciente patient, PhoneIdentity identity) {
        return identity.normalized().equals(patient.getTelefoneNormalizado()) ? "EXATA" : "ALIAS";
    }

    private String maskPhone(String phone) {
        return phone == null || phone.length() < 4
                ? "****"
                : "******" + phone.substring(phone.length() - 4);
    }

    public record PhoneIdentity(String normalized, Set<String> aliases) {
    }

    public record PatientResolution(
            Paciente patient,
            PhoneIdentity identity,
            String origin
    ) {
    }

    private record CandidateEvidence(
            Paciente patient,
            long inboundMessages,
            boolean confirmedChat,
            boolean provisional
    ) {
        private String safeDescription() {
            return "pacienteId=" + patient.getId()
                    + ",externalSource=" + patient.getExternalSource()
                    + ",inbound=" + inboundMessages
                    + ",chatConfirmado=" + confirmedChat
                    + ",provisorio=" + provisional;
        }
    }
}
