package com.synapse.clinicafemina.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.clinicafemina.domain.Agendamento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.integration.external.ExternalAppointmentDTO;
import com.synapse.clinicafemina.integration.external.ExternalClinicProvider;
import com.synapse.clinicafemina.integration.external.ExternalClinicalNoteDTO;
import com.synapse.clinicafemina.integration.external.ExternalPatientDTO;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import com.synapse.clinicafemina.integration.external.PageResult;
import com.synapse.clinicafemina.repository.AgendamentoRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
@Service
public class ExternalSyncTransactionService {

    private final PacienteRepository pacienteRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;
    private final int pageSize;

    public ExternalSyncTransactionService(
            PacienteRepository pacienteRepository,
            AgendamentoRepository agendamentoRepository,
            UsuarioRepository usuarioRepository,
            ObjectMapper objectMapper,
            EntityManager entityManager,
            @Value("${app.integrations.page-size:100}") int pageSize
    ) {
        this.pacienteRepository = pacienteRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.usuarioRepository = usuarioRepository;
        this.objectMapper = objectMapper;
        this.entityManager = entityManager;
        this.pageSize = pageSize;
    }

    @Transactional
    public void sincronizar(
            Clinica clinica,
            ExternalClinicProvider provider,
            OffsetDateTime updatedAfter,
            LocalDate dataInicio,
            LocalDate dataFim,
            ExternalSyncProgress progress
    ) {
        syncPatients(clinica, provider, updatedAfter, progress);
        syncAppointments(clinica, provider, updatedAfter, dataInicio, dataFim, progress);
        executeAtStage(SyncStage.COMMIT_FLUSH, () -> {
            entityManager.flush();
            return null;
        });
    }

    private void syncPatients(
            Clinica clinica,
            ExternalClinicProvider provider,
            OffsetDateTime updatedAfter,
            ExternalSyncProgress progress
    ) {
        String cursor = null;
        boolean hasMore = true;
        while (hasMore) {
            String pageCursor = cursor;
            PageResult<ExternalPatientDTO> page = executeAtStage(
                    SyncStage.PACIENTES_FETCH,
                    () -> provider.getPatients(updatedAfter, pageCursor, pageSize));
            for (ExternalPatientDTO dto : page.data()) {
                if (dto.externalId() == null || dto.externalId().isBlank()) {
                    log.warn(
                            "Paciente externo ignorado: clinica={}, provider={}, motivo=externalId_ausente",
                            clinica.getId(), provider.getType());
                    continue;
                }
                boolean created = executeAtStage(
                        SyncStage.PACIENTE_PERSIST,
                        () -> upsertPatient(clinica, provider.getType(), provider, dto));
                progress.registrarPaciente(created);
            }
            hasMore = page.hasMore();
            cursor = page.nextCursor();
        }
    }

    private void syncAppointments(
            Clinica clinica,
            ExternalClinicProvider provider,
            OffsetDateTime updatedAfter,
            LocalDate dataInicio,
            LocalDate dataFim,
            ExternalSyncProgress progress
    ) {
        String cursor = null;
        boolean hasMore = true;
        while (hasMore) {
            String pageCursor = cursor;
            PageResult<ExternalAppointmentDTO> page = executeAtStage(
                    SyncStage.AGENDAMENTOS_FETCH,
                    () -> dataInicio == null
                            ? provider.getAppointments(updatedAfter, pageCursor, pageSize)
                            : provider.getAppointments(
                                    updatedAfter, dataInicio, dataFim, pageCursor, pageSize));
            log.info(
                    "Pagina de agendamentos externos recebida: clinica={}, provider={}, dataInicio={}, dataFim={}, cursor={}, quantidade={}",
                    clinica.getId(), provider.getType(), dataInicio, dataFim, cursor, page.data().size());
            for (ExternalAppointmentDTO dto : page.data()) {
                String invalidReason = invalidAppointmentReason(dto);
                if (invalidReason != null) {
                    progress.registrarAgendamentoIgnorado();
                    log.warn(
                            "Agendamento externo ignorado: clinica={}, provider={}, externalAppointmentId={}, motivo={}",
                            clinica.getId(), provider.getType(), dto.externalId(), invalidReason);
                    continue;
                }
                persistAppointment(clinica, provider, dto, progress);
            }
            hasMore = page.hasMore();
            cursor = page.nextCursor();
        }
    }

    private String invalidAppointmentReason(ExternalAppointmentDTO dto) {
        if (dto.externalId() == null || dto.externalId().isBlank()) {
            return "externalId_ausente";
        }
        if (dto.externalPatientId() == null || dto.externalPatientId().isBlank()) {
            return "externalPatientId_ausente";
        }
        if (dto.startAt() == null) {
            return "startAt_ausente";
        }
        return null;
    }

    private void persistAppointment(
            Clinica clinica,
            ExternalClinicProvider provider,
            ExternalAppointmentDTO dto,
            ExternalSyncProgress progress
    ) {
        Optional<Paciente> paciente = executeAtStage(
                SyncStage.AGENDAMENTO_MAP,
                () -> resolverPacienteDoAgendamento(clinica, provider.getType(), dto, progress));
        if (paciente.isEmpty()) {
            progress.registrarAgendamentoIgnorado();
            log.warn(
                    "Agendamento externo ignorado por paciente ausente: clinica={}, provider={}, externalAppointmentId={}",
                    clinica.getId(), provider.getType(), dto.externalId());
            return;
        }

        boolean created = executeAtStage(
                SyncStage.AGENDAMENTO_PERSIST,
                () -> upsertAppointment(clinica, provider.getType(), paciente.get(), dto));
        progress.registrarAgendamento(created);
    }

    private Optional<Paciente> resolverPacienteDoAgendamento(
            Clinica clinica,
            ExternalProviderType providerType,
            ExternalAppointmentDTO dto,
            ExternalSyncProgress progress
    ) {
        Optional<Paciente> paciente = pacienteRepository.findByClinicaIdAndExternalSourceAndExternalId(
                clinica.getId(), providerType, dto.externalPatientId());
        if (paciente.isPresent() || providerType != ExternalProviderType.MEDWARE) {
            return paciente;
        }
        Paciente criado = executeAtStage(
                SyncStage.PACIENTE_PERSIST,
                () -> criarPacienteMinimoMedware(clinica, dto));
        progress.registrarPacienteMinimoCriado();
        log.info(
                "Paciente minimo Medware criado a partir de agendamento: clinica={}, externalPatientId={}, externalAppointmentId={}",
                clinica.getId(), dto.externalPatientId(), dto.externalId());
        return Optional.of(criado);
    }

    private boolean upsertPatient(
            Clinica clinica,
            ExternalProviderType providerType,
            ExternalClinicProvider provider,
            ExternalPatientDTO dto
    ) {
        String cpfLimpo = onlyDigits(dto.documentNumber());
        String cpfHash = gerarSha256(cpfLimpo);
        String emailHash = gerarSha256(dto.email());

        Optional<Paciente> existing = pacienteRepository.findByClinicaIdAndExternalSourceAndExternalId(
                clinica.getId(), providerType, dto.externalId());
        if (existing.isEmpty() && cpfHash != null) {
            existing = pacienteRepository.findByClinicaIdAndCpfHash(clinica.getId(), cpfHash);
        }

        boolean created = existing.isEmpty();
        Paciente paciente = existing.orElseGet(Paciente::new);
        paciente.setClinica(clinica);
        paciente.setExternalSource(providerType);
        paciente.setExternalId(dto.externalId());
        paciente.setNome(nullToFallback(dto.fullName(), "Paciente sem nome"));
        paciente.setNomeBusca(normalizarBusca(dto.fullName()));
        paciente.setCpf(cpfLimpo);
        paciente.setCpfHash(cpfHash);
        paciente.setEmail(dto.email());
        paciente.setEmailHash(emailHash);
        paciente.setDataNascimento(dto.birthDate());
        paciente.setTelefone(nullToFallback(dto.phone(), "00000000000"));
        paciente.setTelefoneNormalizado(normalizarTelefone(dto.phone(), dto.externalId()));
        Map<String, Object> externalPayload = new LinkedHashMap<>();
        externalPayload.put("patient", payloadOrEmpty(dto.payload()));
        externalPayload.put("clinicalNotes", fetchNotes(provider, dto.externalId()));
        paciente.setExternalPayload(toJson(externalPayload));

        if (created) {
            paciente.setStatus("EM_ATENDIMENTO");
            paciente.setChaveCriptografiaId("v1");
        }
        pacienteRepository.save(paciente);
        return created;
    }

    private Paciente criarPacienteMinimoMedware(Clinica clinica, ExternalAppointmentDTO dto) {
        String nome = nullToFallback(
                payloadText(dto.payload(), "nomePaciente", "pacienteNome", "nomePacienteAgenda"),
                "Paciente Medware " + dto.externalPatientId());
        String telefone = payloadText(
                dto.payload(), "telefonePaciente", "celularPaciente", "telefone", "celular");
        String cpfLimpo = onlyDigits(
                payloadText(dto.payload(), "cpfPaciente", "cpf", "documentoPaciente"));

        Paciente paciente = new Paciente();
        paciente.setClinica(clinica);
        paciente.setExternalSource(ExternalProviderType.MEDWARE);
        paciente.setExternalId(dto.externalPatientId());
        paciente.setNome(nome);
        paciente.setNomeBusca(normalizarBusca(nome));
        paciente.setTelefone(nullToFallback(telefone, "00000000000"));
        paciente.setTelefoneNormalizado(normalizarTelefone(telefone, dto.externalPatientId()));
        paciente.setCpf(cpfLimpo);
        paciente.setCpfHash(gerarSha256(cpfLimpo));
        paciente.setStatus("EM_ATENDIMENTO");
        paciente.setChaveCriptografiaId("v1");
        Map<String, Object> externalPayload = new LinkedHashMap<>();
        externalPayload.put("source", "MEDWARE_APPOINTMENT");
        externalPayload.put("appointment", payloadOrEmpty(dto.payload()));
        paciente.setExternalPayload(toJson(externalPayload));
        return pacienteRepository.save(paciente);
    }

    private boolean upsertAppointment(
            Clinica clinica,
            ExternalProviderType providerType,
            Paciente paciente,
            ExternalAppointmentDTO dto
    ) {
        Optional<Agendamento> existing = agendamentoRepository
                .findByClinicaIdAndExternalSourceAndExternalId(
                        clinica.getId(), providerType, dto.externalId());
        boolean created = existing.isEmpty();
        Agendamento agendamento = existing.orElseGet(Agendamento::new);
        agendamento.setClinica(clinica);
        agendamento.setPaciente(paciente);
        agendamento.setExternalSource(providerType);
        agendamento.setExternalId(dto.externalId());
        agendamento.setDataHoraInicio(dto.startAt());
        agendamento.setDataHoraFim(dto.endAt());
        agendamento.setTipo(nullToFallback(dto.type(), "CONSULTA"));
        agendamento.setServicoNome(dto.serviceName());
        agendamento.setStatus(nullToFallback(dto.status(), "AGENDADO"));
        agendamento.setOrigem("INTEGRACAO_EXTERNA");
        agendamento.setConfirmadoEm(dto.confirmedAt());
        agendamento.setCanceladoEm(dto.canceledAt());
        agendamento.setMotivoCancelamento(dto.cancellationReason());
        agendamento.setExternalPayload(toJson(payloadOrEmpty(dto.payload())));
        associarMedicoLocal(clinica, agendamento, dto.payload());
        agendamentoRepository.save(agendamento);
        return created;
    }

    private void associarMedicoLocal(
            Clinica clinica,
            Agendamento agendamento,
            Map<String, Object> payload
    ) {
        String medicoNome = payloadText(payload, "medicoNome", "nomeMedico");
        if (medicoNome == null || medicoNome.isBlank()) {
            return;
        }
        usuarioRepository.findMedicosAtivosByClinicaId(clinica.getId()).stream()
                .filter(medico -> normalizarNome(medico.getNome()).equals(normalizarNome(medicoNome)))
                .findFirst()
                .ifPresent(agendamento::setMedico);
    }

    private List<Map<String, Object>> fetchNotes(
            ExternalClinicProvider provider,
            String externalPatientId
    ) {
        try {
            PageResult<ExternalClinicalNoteDTO> notes = provider.getPatientNotes(
                    externalPatientId, null, pageSize);
            return notes.data().stream().map(this::clinicalNotePayload).toList();
        } catch (Exception e) {
            log.warn("Falha ao buscar notas clinicas externas para provider={}", provider.getType());
            return List.of();
        }
    }

    private Map<String, Object> clinicalNotePayload(ExternalClinicalNoteDTO note) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("externalId", nullToFallback(note.externalId(), ""));
        payload.put("content", nullToFallback(note.content(), ""));
        payload.put("createdAt", note.createdAt() != null ? note.createdAt().toString() : "");
        payload.put("payload", payloadOrEmpty(note.payload()));
        return payload;
    }

    private Map<String, Object> payloadOrEmpty(Map<String, Object> payload) {
        return payload == null ? Map.of() : payload;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar payload externo", e);
        }
    }

    private String gerarSha256(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(input.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }

    private String normalizarBusca(String input) {
        if (input == null || input.isBlank()) {
            return "PACIENTE SEM NOME";
        }
        String ascii = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return ascii.toUpperCase();
    }

    private String normalizarNome(String input) {
        if (input == null) {
            return "";
        }
        return Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .trim()
                .replaceAll("\\s+", " ")
                .toUpperCase();
    }

    private String normalizarTelefone(String phone, String fallback) {
        String digits = onlyDigits(phone);
        if (digits != null && !digits.isBlank()) {
            return digits;
        }
        String fallbackDigits = onlyDigits((fallback == null ? "" : fallback) + "00000000000");
        if (fallbackDigits == null || fallbackDigits.length() < 11) {
            return "00000000000";
        }
        return fallbackDigits.substring(0, 11);
    }

    private String onlyDigits(String input) {
        if (input == null) {
            return null;
        }
        String digits = input.replaceAll("\\D", "");
        return digits.isBlank() ? null : digits;
    }

    @SuppressWarnings("unchecked")
    private String payloadText(Map<String, Object> payload, String... keys) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            Object value = payload.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        for (Object value : payload.values()) {
            if (value instanceof Map<?, ?> nested) {
                String nestedValue = payloadText((Map<String, Object>) nested, keys);
                if (nestedValue != null) {
                    return nestedValue;
                }
            }
        }
        return null;
    }

    private String nullToFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private <T> T executeAtStage(SyncStage stage, Supplier<T> action) {
        try {
            return action.get();
        } catch (RuntimeException e) {
            if (e instanceof SyncStageException) {
                throw e;
            }
            throw new SyncStageException(stage, e);
        }
    }

    enum SyncStage {
        PACIENTES_FETCH,
        PACIENTE_PERSIST,
        AGENDAMENTOS_FETCH,
        AGENDAMENTO_MAP,
        AGENDAMENTO_PERSIST,
        COMMIT_FLUSH
    }

    static final class SyncStageException extends RuntimeException {
        private final SyncStage stage;
        private final String causeType;

        SyncStageException(SyncStage stage, RuntimeException cause) {
            super(cause);
            this.stage = stage;
            this.causeType = cause.getClass().getSimpleName();
        }

        SyncStage getStage() {
            return stage;
        }

        String getCauseType() {
            return causeType;
        }
    }
}
