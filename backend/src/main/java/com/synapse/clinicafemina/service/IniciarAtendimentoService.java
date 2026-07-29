package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.domain.TransferenciaAtendimento;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.atendimento.IniciarAtendimentoRequest;
import com.synapse.clinicafemina.dto.atendimento.IniciarAtendimentoResponse;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.ClinicaRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import com.synapse.clinicafemina.repository.TransferenciaAtendimentoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class IniciarAtendimentoService {

    private static final Set<String> PERFIS_AUTORIZADOS = Set.of("GESTOR", "RECEPCIONISTA");
    private final ClinicaRepository clinicaRepository;
    private final PacienteRepository pacienteRepository;
    private final AtendimentoRepository atendimentoRepository;
    private final TransferenciaAtendimentoRepository transferenciaRepository;
    private final AtendimentoService atendimentoService;
    private final RealtimeBroadcastService broadcastService;
    private final WhatsappPhoneIdentityService phoneIdentityService;
    private final WhatsappContactNameService contactNameService;

    @Transactional
    public IniciarAtendimentoResponse iniciar(
            Clinica clinicaConfigurada,
            Usuario usuario,
            IniciarAtendimentoRequest request
    ) {
        validarUsuario(usuario, clinicaConfigurada.getId());
        Clinica clinica = clinicaRepository.findByIdForUpdate(clinicaConfigurada.getId())
                .orElseThrow(() -> new NotFoundException("Clinica nao encontrada"));
        PacienteResolvido pacienteResolvido = resolverPaciente(clinica, usuario, request);
        ResultadoAtendimento resultado = resolverAtendimento(
                clinica, pacienteResolvido.paciente(), usuario
        );
        if (!pacienteResolvido.criado()) {
            String requestedPhone = request.telefone() != null
                    ? request.telefone()
                    : pacienteResolvido.paciente().getTelefoneNormalizado();
            phoneIdentityService.applyHistoricalInboundChatId(
                    resultado.atendimento(),
                    phoneIdentityService.identify(requestedPhone)
            );
        }
        atualizarPaciente(pacienteResolvido.paciente(), resultado.atendimento(), usuario);

        log.info(
                "Atendimento manual iniciado. clinicaId={} usuarioId={} pacienteId={} atendimentoId={} "
                        + "pacienteCriado={} atendimentoCriado={} reutilizado={}",
                clinica.getId(), usuario.getId(), pacienteResolvido.paciente().getId(),
                resultado.atendimento().getId(), pacienteResolvido.criado(), resultado.criado(),
                !resultado.criado()
        );
        return new IniciarAtendimentoResponse(
                resultado.atendimento().getId(),
                pacienteResolvido.paciente().getId(),
                "HUMANO",
                pacienteResolvido.criado(),
                resultado.criado(),
                !resultado.criado(),
                resultado.destinatarioAlterado(),
                atendimentoService.buscarPorId(resultado.atendimento().getId(), clinica.getId())
        );
    }

    private PacienteResolvido resolverPaciente(
            Clinica clinica,
            Usuario usuario,
            IniciarAtendimentoRequest request
    ) {
        if (request.pacienteId() != null) {
            Paciente paciente = pacienteRepository.findByIdAndClinicaId(
                            request.pacienteId(), clinica.getId()
                    )
                    .filter(item -> item.getDeletadoEm() == null)
                    .orElseThrow(() -> new NotFoundException("Paciente nao encontrado"));
            phoneIdentityService.identify(paciente.getTelefoneNormalizado());
            return new PacienteResolvido(paciente, false);
        }
        WhatsappPhoneIdentityService.PhoneIdentity identity =
                phoneIdentityService.identify(request.telefone());
        String nome = contactNameService.sanitizeRequired(request.nome(), identity.normalized());
        var existing = phoneIdentityService.resolvePatient(clinica.getId(), identity);
        if (existing.isPresent()) {
            Paciente patient = existing.get().patient();
            updatePlaceholderName(patient, nome);
            log.info(
                    "Paciente WhatsApp reutilizado. clinicaId={} pacienteId={} origemResolucao={} "
                            + "aliases={}",
                    clinica.getId(), patient.getId(), existing.get().origin(),
                    maskedAliases(identity.aliases())
            );
            return new PacienteResolvido(patient, false);
        }
        pacienteRepository.findByClinicaIdAndTelefoneNormalizado(
                clinica.getId(), identity.normalized()
        ).filter(patient -> patient.getDeletadoEm() != null).ifPresent(patient -> {
            throw new IllegalStateException(
                    "Nao foi possivel iniciar o atendimento para este contato."
            );
        });
        Paciente paciente = new Paciente();
        paciente.setClinica(clinica);
        paciente.setNome(nome);
        paciente.setNomeBusca(contactNameService.toSearchName(nome));
        paciente.setTelefone("+" + identity.normalized());
        paciente.setTelefoneNormalizado(identity.normalized());
        paciente.setExternalSource(ExternalProviderType.WHATSAPP);
        paciente.setExternalId(identity.normalized());
        paciente.setStatus("EM_ATENDIMENTO");
        paciente.setCriadoPor(usuario);
        paciente.setAtualizadoPor(usuario);
        return new PacienteResolvido(pacienteRepository.save(paciente), true);
    }

    private void updatePlaceholderName(Paciente patient, String suppliedName) {
        if (patient.getExternalSource() == ExternalProviderType.MEDWARE
                || patient.getExternalSource() == ExternalProviderType.DARWIN
                || !contactNameService.isPlaceholder(
                        patient.getNome(), patient.getTelefoneNormalizado()
                )) {
            return;
        }
        patient.setNome(suppliedName);
        patient.setNomeBusca(contactNameService.toSearchName(suppliedName));
        pacienteRepository.save(patient);
    }

    private java.util.List<String> maskedAliases(Set<String> aliases) {
        return aliases.stream().map(this::maskPhone).toList();
    }

    private String maskPhone(String phone) {
        return phone == null || phone.length() < 4
                ? "****"
                : "******" + phone.substring(phone.length() - 4);
    }

    private ResultadoAtendimento resolverAtendimento(
            Clinica clinica,
            Paciente paciente,
            Usuario usuario
    ) {
        Optional<Atendimento> ativo = atendimentoRepository.findAtivo(
                clinica.getId(), paciente.getId()
        );
        if (ativo.isEmpty()) {
            Atendimento atendimento = new Atendimento();
            atendimento.setClinica(clinica);
            atendimento.setPaciente(paciente);
            atendimento.setAtendentePrincipal(usuario);
            atendimento.setStatus("ATIVO");
            atendimento.setTratadoPorIa(false);
            atendimento.setHumanoDesde(OffsetDateTime.now());
            atendimento.setNaoLidas(0);
            return new ResultadoAtendimento(atendimentoRepository.save(atendimento), true, true);
        }
        Atendimento atendimento = ativo.get();
        Usuario anterior = atendimento.getAtendentePrincipal();
        boolean estavaNaIa = !Boolean.FALSE.equals(atendimento.getTratadoPorIa());
        boolean destinatarioAlterado = anterior == null || !usuario.getId().equals(anterior.getId());
        if (estavaNaIa || destinatarioAlterado) {
            OffsetDateTime agora = OffsetDateTime.now();
            atendimento.setAtendentePrincipal(usuario);
            atendimento.setTratadoPorIa(false);
            atendimento.setHumanoDesde(agora);
            atendimento.setStatus("ATIVO");
            atendimentoRepository.save(atendimento);
            transferenciaRepository.save(criarTransferencia(atendimento, anterior, usuario));
            agendarBroadcast(atendimento, anterior, usuario);
        }
        return new ResultadoAtendimento(atendimento, false, destinatarioAlterado);
    }

    private TransferenciaAtendimento criarTransferencia(
            Atendimento atendimento,
            Usuario anterior,
            Usuario atual
    ) {
        TransferenciaAtendimento transferencia = new TransferenciaAtendimento();
        transferencia.setAtendimento(atendimento);
        transferencia.setDeUsuario(anterior);
        transferencia.setParaUsuario(atual);
        transferencia.setTransferidoPor(atual);
        transferencia.setMotivo("Atendimento iniciado manualmente");
        transferencia.setOrigem("MANUAL");
        return transferencia;
    }

    private void atualizarPaciente(Paciente paciente, Atendimento atendimento, Usuario usuario) {
        paciente.setStatus("EM_ATENDIMENTO");
        paciente.setAtendentePrincipal(usuario);
        paciente.setAtendimentoAtual(atendimento);
        paciente.setUltimaInteracaoEm(OffsetDateTime.now());
        paciente.setAtualizadoPor(usuario);
        pacienteRepository.save(paciente);
    }

    private void validarUsuario(Usuario usuario, Long clinicaId) {
        if (usuario == null
                || usuario.getClinica() == null
                || !clinicaId.equals(usuario.getClinica().getId())
                || !usuario.isEnabled()
                || !PERFIS_AUTORIZADOS.contains(usuario.getPerfil())) {
            throw new NotFoundException("Usuario nao encontrado");
        }
    }

    private void agendarBroadcast(
            Atendimento atendimento,
            Usuario anterior,
            Usuario atual
    ) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        LinkedHashSet<Long> destinatarios = new LinkedHashSet<>();
        destinatarios.add(atual.getId());
        if (anterior != null) {
            destinatarios.add(anterior.getId());
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                broadcastService.broadcastTransferenciaParaDestinatarios(
                        destinatarios,
                        atual.getId(),
                        atendimento.getId(),
                        anterior == null ? 0L : anterior.getId(),
                        anterior == null ? "IA" : anterior.getNome(),
                        atendimento.getPaciente().getId(),
                        atendimento.getPaciente().getNomeBusca(),
                        "Atendimento iniciado manualmente"
                );
            }
        });
    }

    private record PacienteResolvido(Paciente paciente, boolean criado) {
    }

    private record ResultadoAtendimento(
            Atendimento atendimento,
            boolean criado,
            boolean destinatarioAlterado
    ) {
    }
}
