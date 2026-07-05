package com.synapse.clinicafemina.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.clinicafemina.domain.Agendamento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.IntegrationSyncLog;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.integration.external.ExternalAppointmentDTO;
import com.synapse.clinicafemina.integration.external.ExternalClinicProvider;
import com.synapse.clinicafemina.integration.external.ExternalClinicalNoteDTO;
import com.synapse.clinicafemina.integration.external.ExternalPatientDTO;
import com.synapse.clinicafemina.integration.external.ExternalProviderFactory;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import com.synapse.clinicafemina.integration.external.PageResult;
import com.synapse.clinicafemina.repository.AgendamentoRepository;
import com.synapse.clinicafemina.repository.IntegrationSyncLogRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class ExternalSyncService {

    private static final ZoneId SYNC_ZONE = ZoneId.of("America/Sao_Paulo");

    private final ExternalProviderFactory providerFactory;
    private final IntegrationSyncLogRepository syncLogRepository;
    private final PacienteRepository pacienteRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final ObjectMapper objectMapper;
    private final int pageSize;

    public ExternalSyncService(
            ExternalProviderFactory providerFactory,
            IntegrationSyncLogRepository syncLogRepository,
            PacienteRepository pacienteRepository,
            AgendamentoRepository agendamentoRepository,
            ObjectMapper objectMapper,
            @Value("${app.integrations.page-size:100}") int pageSize) {
        this.providerFactory = providerFactory;
        this.syncLogRepository = syncLogRepository;
        this.pacienteRepository = pacienteRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.objectMapper = objectMapper;
        this.pageSize = pageSize;
    }

    @Transactional
    public ExternalSyncResult sincronizar(Clinica clinica) {
        return sincronizar(clinica, null, null);
    }

    @Transactional
    public ExternalSyncResult sincronizar(Clinica clinica, LocalDate dataInicio, LocalDate dataFim) {
        validarPeriodoManual(dataInicio, dataFim);
        ExternalProviderType providerType = clinica.getExternalProvider();
        ExternalClinicProvider provider = providerFactory.getProvider(providerType);
        OffsetDateTime updatedAfter = dataInicio == null
                ? syncLogRepository.findUltimoSucesso(clinica.getId(), providerType)
                        .map(IntegrationSyncLog::getIniciadoEm)
                        .orElse(null)
                : null;

        IntegrationSyncLog runLog = new IntegrationSyncLog();
        runLog.setClinica(clinica);
        runLog.setExternalProvider(providerType);
        runLog.setUpdatedAfterUtilizado(dataInicio == null ? updatedAfter : inicioDoDia(dataInicio));
        runLog = syncLogRepository.save(runLog);

        SyncCounters counters = new SyncCounters();
        try {
            syncPatients(clinica, provider, updatedAfter, counters);
            syncAppointments(clinica, provider, updatedAfter, dataInicio, dataFim, counters);
            runLog.setStatus("SUCESSO");
        } catch (Exception e) {
            runLog.setStatus("FALHA_TOTAL");
            runLog.setMensagemErro("Falha na sincronizacao externa: " + e.getClass().getSimpleName());
            log.error("Falha na sincronizacao externa clinica={}, provider={}, tipoErro={}",
                    clinica.getId(), providerType, e.getClass().getSimpleName());
        } finally {
            applyCounters(runLog, counters);
            runLog.setConcluidoEm(OffsetDateTime.now());
            syncLogRepository.save(runLog);
        }

        return counters.toResult(runLog.getStatus());
    }

    private void syncPatients(Clinica clinica, ExternalClinicProvider provider,
                              OffsetDateTime updatedAfter, SyncCounters counters) {
        String cursor = null;
        boolean hasMore = true;
        while (hasMore) {
            PageResult<ExternalPatientDTO> page = provider.getPatients(updatedAfter, cursor, pageSize);
            for (ExternalPatientDTO dto : page.data()) {
                boolean created = upsertPatient(clinica, provider.getType(), provider, dto);
                counters.pacientesProcessados++;
                if (created) {
                    counters.pacientesCriados++;
                } else {
                    counters.pacientesAtualizados++;
                }
            }
            hasMore = page.hasMore();
            cursor = page.nextCursor();
        }
    }

    private void syncAppointments(Clinica clinica, ExternalClinicProvider provider,
                                  OffsetDateTime updatedAfter, LocalDate dataInicio,
                                  LocalDate dataFim, SyncCounters counters) {
        String cursor = null;
        boolean hasMore = true;
        while (hasMore) {
            PageResult<ExternalAppointmentDTO> page = dataInicio == null
                    ? provider.getAppointments(updatedAfter, cursor, pageSize)
                    : provider.getAppointments(updatedAfter, dataInicio, dataFim, cursor, pageSize);
            for (ExternalAppointmentDTO dto : page.data()) {
                Optional<Paciente> paciente = pacienteRepository.findByClinicaIdAndExternalSourceAndExternalId(
                        clinica.getId(), provider.getType(), dto.externalPatientId());
                if (paciente.isEmpty()) {
                    counters.agendamentosIgnorados++;
                    continue;
                }

                boolean created = upsertAppointment(clinica, provider.getType(), paciente.get(), dto);
                counters.agendamentosProcessados++;
                if (created) {
                    counters.agendamentosCriados++;
                } else {
                    counters.agendamentosAtualizados++;
                }
            }
            hasMore = page.hasMore();
            cursor = page.nextCursor();
        }
    }

    private void validarPeriodoManual(LocalDate dataInicio, LocalDate dataFim) {
        if (dataInicio == null && dataFim == null) {
            return;
        }
        if (dataInicio == null || dataFim == null || dataFim.isBefore(dataInicio)) {
            throw new BadRequestException("Periodo de sincronizacao invalido");
        }
    }

    private OffsetDateTime inicioDoDia(LocalDate dataInicio) {
        return dataInicio.atTime(LocalTime.MIDNIGHT).atZone(SYNC_ZONE).toOffsetDateTime();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean upsertPatient(Clinica clinica, ExternalProviderType providerType,
                                 ExternalClinicProvider provider, ExternalPatientDTO dto) {
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
        paciente.setExternalPayload(toJson(Map.of(
                "patient", dto.payload(),
                "clinicalNotes", fetchNotes(provider, dto.externalId())
        )));

        if (created) {
            paciente.setStatus("EM_ATENDIMENTO");
            paciente.setChaveCriptografiaId("v1");
        }

        pacienteRepository.save(paciente);
        return created;
    }

    private boolean upsertAppointment(Clinica clinica, ExternalProviderType providerType,
                                      Paciente paciente, ExternalAppointmentDTO dto) {
        Optional<Agendamento> existing = agendamentoRepository.findByClinicaIdAndExternalSourceAndExternalId(
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
        agendamento.setExternalPayload(toJson(dto.payload()));
        agendamentoRepository.save(agendamento);
        return created;
    }

    private List<Map<String, Object>> fetchNotes(ExternalClinicProvider provider, String externalPatientId) {
        try {
            PageResult<ExternalClinicalNoteDTO> notes = provider.getPatientNotes(externalPatientId, null, pageSize);
            return notes.data().stream()
                    .map(note -> Map.of(
                            "externalId", (Object) nullToFallback(note.externalId(), ""),
                            "content", nullToFallback(note.content(), ""),
                            "createdAt", note.createdAt() != null ? note.createdAt().toString() : "",
                            "payload", note.payload()
                    ))
                    .toList();
        } catch (Exception e) {
            log.warn("Falha ao buscar notas clínicas externas para provider={}", provider.getType());
            return List.of();
        }
    }

    private void applyCounters(IntegrationSyncLog log, SyncCounters counters) {
        log.setPacientesProcessados(counters.pacientesProcessados);
        log.setPacientesCriados(counters.pacientesCriados);
        log.setPacientesAtualizados(counters.pacientesAtualizados);
        log.setAgendamentosProcessados(counters.agendamentosProcessados);
        log.setAgendamentosCriados(counters.agendamentosCriados);
        log.setAgendamentosAtualizados(counters.agendamentosAtualizados);
        log.setAgendamentosIgnorados(counters.agendamentosIgnorados);
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
            throw new IllegalStateException("SHA-256 indisponível", e);
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

    private String nullToFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static class SyncCounters {
        private int pacientesProcessados;
        private int pacientesCriados;
        private int pacientesAtualizados;
        private int agendamentosProcessados;
        private int agendamentosCriados;
        private int agendamentosAtualizados;
        private int agendamentosIgnorados;

        private ExternalSyncResult toResult(String status) {
            return new ExternalSyncResult(
                    pacientesProcessados,
                    pacientesCriados,
                    pacientesAtualizados,
                    agendamentosProcessados,
                    agendamentosCriados,
                    agendamentosAtualizados,
                    agendamentosIgnorados,
                    status
            );
        }
    }
}
