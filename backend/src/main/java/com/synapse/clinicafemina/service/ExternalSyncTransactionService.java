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
import com.synapse.clinicafemina.integration.external.MedwareApiMapper;
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
import java.sql.SQLException;
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

    private static final int EXTERNAL_ID_MAX_LENGTH = 100;
    private static final int TELEFONE_NORMALIZADO_MAX_LENGTH = 20;
    private static final int NOME_BUSCA_MAX_LENGTH = 200;
    private static final int STATUS_MAX_LENGTH = 20;
    private static final int CHAVE_CRIPTOGRAFIA_MAX_LENGTH = 20;

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
                            "Agendamento externo ignorado: clinica={}, provider={}, motivo={}",
                            clinica.getId(), provider.getType(), invalidReason);
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
        executeAtStage(SyncStage.AGENDAMENTO_MAP, () -> {
            validarTamanho("externalId", dto.externalId(), EXTERNAL_ID_MAX_LENGTH);
            validarTamanho("externalPatientId", dto.externalPatientId(), EXTERNAL_ID_MAX_LENGTH);
            return null;
        });
        Optional<Paciente> paciente = executeAtStage(
                SyncStage.AGENDAMENTO_MAP,
                () -> resolverPacienteDoAgendamento(clinica, provider.getType(), dto, progress));
        if (paciente.isEmpty()) {
            progress.registrarAgendamentoIgnorado();
            log.warn(
                    "Agendamento externo ignorado por paciente ausente: clinica={}, provider={}",
                    clinica.getId(), provider.getType());
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
        if (providerType != ExternalProviderType.MEDWARE) {
            return pacienteRepository.findByClinicaIdAndExternalSourceAndExternalId(
                    clinica.getId(), providerType, dto.externalPatientId());
        }

        AppointmentPatientData patientData = appointmentPatientData(dto.payload());
        String cpfLimpo = onlyDigits(patientData.cpf());
        String cpfHash = gerarCpfHashSeguro(cpfLimpo);
        Optional<PatientMatch> existente = localizarPacienteExistente(
                clinica.getId(), providerType, dto.externalPatientId(), cpfHash);
        if (existente.isPresent()) {
            Paciente paciente = existente.get().paciente();
            if (existente.get().tipo() == PatientMatchType.CPF) {
                executeAtStage(SyncStage.PACIENTE_PERSIST, () -> {
                    vincularPacienteAoProvider(paciente, providerType, dto.externalPatientId());
                    if (paciente.getExternalPayload() == null) {
                        paciente.setExternalPayload(toJson(minimalPatientPayload(dto)));
                    }
                    validarPacienteAntesDePersistir(paciente);
                    return pacienteRepository.save(paciente);
                });
                log.info(
                        "Paciente existente reutilizado por identidade segura: clinica={}, provider={}, criterio=cpf_hash",
                        clinica.getId(), providerType);
            }
            return Optional.of(paciente);
        }

        Paciente criado = executeAtStage(
                SyncStage.PACIENTE_PERSIST,
                () -> criarPacienteMinimoMedware(
                        clinica, dto, patientData, cpfLimpo, cpfHash));
        progress.registrarPacienteMinimoCriado();
        log.info(
                "Paciente minimo Medware criado a partir de agendamento: clinica={}, provider={}",
                clinica.getId(), providerType);
        return Optional.of(criado);
    }

    private boolean upsertPatient(
            Clinica clinica,
            ExternalProviderType providerType,
            ExternalClinicProvider provider,
            ExternalPatientDTO dto
    ) {
        validarTamanho("externalId", dto.externalId(), EXTERNAL_ID_MAX_LENGTH);
        String cpfLimpo = onlyDigits(dto.documentNumber());
        String cpfHash = gerarCpfHashSeguro(cpfLimpo);
        String emailHash = gerarSha256(dto.email());

        Optional<PatientMatch> match = localizarPacienteExistente(
                clinica.getId(), providerType, dto.externalId(), cpfHash);

        boolean created = match.isEmpty();
        Paciente paciente = match.map(PatientMatch::paciente).orElseGet(Paciente::new);
        paciente.setClinica(clinica);
        vincularPacienteAoProvider(paciente, providerType, dto.externalId());
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
        validarPacienteAntesDePersistir(paciente);
        pacienteRepository.save(paciente);
        return created;
    }

    private Paciente criarPacienteMinimoMedware(
            Clinica clinica,
            ExternalAppointmentDTO dto,
            AppointmentPatientData patientData,
            String cpfLimpo,
            String cpfHash
    ) {
        String nome = nullToFallback(
                patientData.nome(),
                "Paciente Medware " + dto.externalPatientId());
        String telefone = patientData.telefone();

        Paciente paciente = new Paciente();
        paciente.setClinica(clinica);
        paciente.setExternalSource(ExternalProviderType.MEDWARE);
        paciente.setExternalId(dto.externalPatientId());
        paciente.setNome(nome);
        paciente.setNomeBusca(normalizarBusca(nome));
        paciente.setTelefone(nullToFallback(telefone, "00000000000"));
        paciente.setTelefoneNormalizado(normalizarTelefone(telefone, dto.externalPatientId()));
        paciente.setCpf(cpfLimpo);
        paciente.setCpfHash(cpfHash);
        paciente.setStatus("EM_ATENDIMENTO");
        paciente.setChaveCriptografiaId("v1");
        paciente.setExternalPayload(toJson(minimalPatientPayload(dto)));
        validarPacienteAntesDePersistir(paciente);
        return pacienteRepository.save(paciente);
    }

    private Map<String, Object> minimalPatientPayload(ExternalAppointmentDTO dto) {
        Map<String, Object> externalPayload = new LinkedHashMap<>();
        externalPayload.put("source", "MEDWARE_APPOINTMENT");
        externalPayload.put("appointment", payloadOrEmpty(dto.payload()));
        return externalPayload;
    }

    private Optional<PatientMatch> localizarPacienteExistente(
            Long clinicaId,
            ExternalProviderType providerType,
            String externalId,
            String cpfHash
    ) {
        validarTamanho("externalId", externalId, EXTERNAL_ID_MAX_LENGTH);
        Optional<Paciente> porExternalId = pacienteRepository
                .findByClinicaIdAndExternalSourceAndExternalId(clinicaId, providerType, externalId);
        if (porExternalId.isPresent()) {
            return porExternalId.map(paciente -> new PatientMatch(paciente, PatientMatchType.EXTERNAL_ID));
        }
        if (cpfHash == null) {
            return Optional.empty();
        }
        return pacienteRepository.findByClinicaIdAndCpfHash(clinicaId, cpfHash)
                .map(paciente -> new PatientMatch(paciente, PatientMatchType.CPF));
    }

    private void vincularPacienteAoProvider(
            Paciente paciente,
            ExternalProviderType providerType,
            String externalId
    ) {
        validarTamanho("externalId", externalId, EXTERNAL_ID_MAX_LENGTH);
        ExternalProviderType currentProvider = paciente.getExternalSource();
        String currentExternalId = paciente.getExternalId();

        if (currentProvider != null && currentProvider != providerType) {
            log.warn(
                    "Vinculo externo existente preservado ao reutilizar paciente por identidade segura: clinica={}, providerExistente={}, providerRecebido={}",
                    paciente.getClinica().getId(), currentProvider, providerType);
            return;
        }
        if (currentExternalId != null && !java.util.Objects.equals(currentExternalId, externalId)) {
            throw new ExternalIdentityConflictException("externalPatientId");
        }
        paciente.setExternalSource(providerType);
        paciente.setExternalId(externalId);
    }

    private AppointmentPatientData appointmentPatientData(Map<String, Object> payload) {
        Map<String, Object> medware = directMap(payload, "medware");
        Map<String, Object> medwarePatient = directMap(medware, "paciente");
        Map<String, Object> payloadPatient = directMap(payload, "paciente");

        String cpf = firstNonBlank(
                directText(medwarePatient, "cpf", "cpfPaciente", "documento"),
                directText(payloadPatient, "cpf", "cpfPaciente", "documento"),
                directText(medware, "cpfPaciente"),
                directText(payload, "cpfPaciente"));
        String nome = firstNonBlank(
                directText(medwarePatient, "nome", "nomePaciente"),
                directText(payloadPatient, "nome", "nomePaciente"),
                directText(medware, "nomePaciente"),
                directText(payload, "nomePaciente"));
        String telefone = firstNonBlank(
                directText(medwarePatient, "telefone", "celular", "telefonePaciente", "celularPaciente"),
                directText(payloadPatient, "telefone", "celular", "telefonePaciente", "celularPaciente"),
                directText(medware, "telefonePaciente", "celularPaciente"),
                directText(payload, "telefonePaciente", "celularPaciente"));
        return new AppointmentPatientData(cpf, nome, telefone);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> directMap(Map<String, Object> source, String key) {
        Object value = directValue(source, key);
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private String directText(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = directValue(source, key);
            if (value instanceof String text && !text.isBlank()) {
                return text;
            }
            if (value instanceof Number || value instanceof Character) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private Object directValue(Map<String, Object> source, String key) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private void validarPacienteAntesDePersistir(Paciente paciente) {
        validarTamanho("externalId", paciente.getExternalId(), EXTERNAL_ID_MAX_LENGTH);
        validarTamanho(
                "telefoneNormalizado", paciente.getTelefoneNormalizado(), TELEFONE_NORMALIZADO_MAX_LENGTH);
        validarTamanho("nomeBusca", paciente.getNomeBusca(), NOME_BUSCA_MAX_LENGTH);
        validarTamanho("status", paciente.getStatus(), STATUS_MAX_LENGTH);
        validarTamanho(
                "chaveCriptografiaId", paciente.getChaveCriptografiaId(), CHAVE_CRIPTOGRAFIA_MAX_LENGTH);
    }

    private void validarTamanho(String campo, String valor, int maximo) {
        if (valor != null && valor.codePointCount(0, valor.length()) > maximo) {
            throw new ExternalFieldValidationException(campo);
        }
    }

    private String gerarCpfHashSeguro(String cpfLimpo) {
        return cpfValido(cpfLimpo) ? gerarSha256(cpfLimpo) : null;
    }

    private boolean cpfValido(String cpf) {
        if (cpf == null || cpf.length() != 11 || cpf.chars().distinct().count() == 1) {
            return false;
        }
        return digitoCpf(cpf, 9) == cpf.charAt(9) - '0'
                && digitoCpf(cpf, 10) == cpf.charAt(10) - '0';
    }

    private int digitoCpf(String cpf, int quantidade) {
        int soma = 0;
        for (int i = 0; i < quantidade; i++) {
            soma += (cpf.charAt(i) - '0') * (quantidade + 1 - i);
        }
        int resto = 11 - (soma % 11);
        return resto >= 10 ? 0 : resto;
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
        return MedwareApiMapper.normalizarTelefoneExterno(phone, fallback);
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
        private final String sqlState;
        private final String constraintName;
        private final String fieldName;

        SyncStageException(SyncStage stage, RuntimeException cause) {
            super(cause);
            this.stage = stage;
            this.causeType = cause.getClass().getSimpleName();
            this.sqlState = findSqlState(cause);
            this.constraintName = findConstraintName(cause);
            this.fieldName = diagnosticField(cause);
        }

        SyncStage getStage() {
            return stage;
        }

        String getCauseType() {
            return causeType;
        }

        String getTechnicalDetails() {
            List<String> details = new java.util.ArrayList<>();
            if (fieldName != null) {
                details.add("field=" + fieldName);
            }
            if (sqlState != null) {
                details.add("sqlState=" + sqlState);
            }
            if (constraintName != null) {
                details.add("constraint=" + constraintName);
            }
            return details.isEmpty() ? "" : " [" + String.join(", ", details) + "]";
        }

        private static String findSqlState(Throwable error) {
            for (Throwable current = error; current != null; current = current.getCause()) {
                if (current instanceof SQLException sqlException) {
                    return safeSqlState(sqlException.getSQLState());
                }
            }
            return null;
        }

        private static String findConstraintName(Throwable error) {
            for (Throwable current = error; current != null; current = current.getCause()) {
                if (current instanceof org.hibernate.exception.ConstraintViolationException violation) {
                    return safeTechnicalName(violation.getConstraintName());
                }
            }
            return null;
        }

        private static String safeSqlState(String sqlState) {
            return sqlState != null && sqlState.matches("[A-Za-z0-9]{5}") ? sqlState : null;
        }

        private static String safeTechnicalName(String name) {
            return name != null && name.matches("[A-Za-z0-9_.-]{1,128}") ? name : null;
        }

        private static String diagnosticField(RuntimeException cause) {
            if (cause instanceof ExternalFieldValidationException validation) {
                return validation.getFieldName();
            }
            if (cause instanceof ExternalIdentityConflictException conflict) {
                return conflict.getFieldName();
            }
            return null;
        }
    }

    static final class ExternalFieldValidationException extends RuntimeException {
        private final String fieldName;

        ExternalFieldValidationException(String fieldName) {
            this.fieldName = fieldName;
        }

        String getFieldName() {
            return fieldName;
        }
    }

    static final class ExternalIdentityConflictException extends RuntimeException {
        private final String fieldName;

        ExternalIdentityConflictException(String fieldName) {
            this.fieldName = fieldName;
        }

        String getFieldName() {
            return fieldName;
        }
    }

    private enum PatientMatchType {
        EXTERNAL_ID,
        CPF
    }

    private record PatientMatch(Paciente paciente, PatientMatchType tipo) {
    }

    private record AppointmentPatientData(String cpf, String nome, String telefone) {
    }
}
