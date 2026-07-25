package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Agendamento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.dto.darwin.DarwinPatientScheduleResponse;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import com.synapse.clinicafemina.repository.AgendamentoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Mantém o espelho local (tabela {@code agendamento}, externalSource=DARWIN) sincronizado
 * com a API Darwin real. A API Darwin não oferece listagem geral por clínica/período —
 * só por CPF — então este serviço é o único caminho de escrita local para agendamentos
 * Darwin, alimentado por três mecanismos: escrita feita pelo CRM, consulta por CPF, e
 * backfill administrativo (ver DarwinAgendaProvider / DarwinBackfillService).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DarwinAgendaSyncService {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");

    private final AgendamentoRepository agendamentoRepository;

    /**
     * Sincroniza (upsert) todos os agendamentos retornados por uma consulta Darwin por CPF
     * para o espelho local. Idempotente: reexecutar não duplica nem perde dados.
     */
    @Transactional
    public List<Agendamento> sincronizarAgendamentosDoPaciente(
            Clinica clinica, Paciente paciente, DarwinPatientScheduleResponse resposta) {
        if (resposta == null || resposta.schedules() == null) {
            return List.of();
        }
        return resposta.schedules().stream()
                .map(schedule -> upsertSchedule(clinica, paciente, schedule))
                .toList();
    }

    /**
     * Upsert idempotente de um único agendamento Darwin (identificado por scheduleId)
     * no espelho local. Usado tanto pela sincronização por CPF quanto, após confirmação
     * de escrita bem-sucedida, pelo fluxo de criação/edição via CRM.
     */
    @Transactional
    public Agendamento upsertSchedule(Clinica clinica, Paciente paciente, DarwinPatientScheduleResponse.Schedule schedule) {
        Agendamento agendamento = agendamentoRepository
                .findByClinicaIdAndExternalSourceAndExternalId(clinica.getId(), ExternalProviderType.DARWIN, schedule.scheduleId())
                .orElseGet(Agendamento::new);
        agendamento.setClinica(clinica);
        agendamento.setPaciente(paciente);
        agendamento.setExternalSource(ExternalProviderType.DARWIN);
        agendamento.setExternalId(schedule.scheduleId());
        OffsetDateTime inicio = combinar(schedule.date(), schedule.time());
        OffsetDateTime fim = combinar(schedule.date(), schedule.endTime());
        agendamento.setDataHoraInicio(inicio);
        agendamento.setDataHoraFim(fim);
        agendamento.setStatus(schedule.statusName());
        agendamento.setOrigem("DARWIN");
        agendamento.setTimetableId(schedule.timetableId());
        agendamento.setProfissionalExternoId(schedule.professionalId());
        agendamento.setProfissionalNome(schedule.professionalName());
        agendamento.setLocalExternoId(schedule.locationId());
        agendamento.setLocalNome(schedule.locationName());
        if (schedule.scheduleProcedures() != null && !schedule.scheduleProcedures().isEmpty()) {
            var primeiro = schedule.scheduleProcedures().get(0);
            agendamento.setServicoNome(primeiro.procedures() == null ? null : primeiro.procedures().name());
            agendamento.setConvenioNome(primeiro.insurances() == null ? null : primeiro.insurances().name());
        }
        agendamento.setSyncStatus("SYNCED");
        agendamento.setSyncMensagemErro(null);
        agendamento.setSincronizadoEm(OffsetDateTime.now());
        return agendamentoRepository.save(agendamento);
    }

    /**
     * Registra uma pendência de reconciliação: a escrita na Darwin foi confirmada
     * (ou não pôde ser confirmada com certeza), mas o scheduleId real ainda não é
     * conhecido localmente. Roda em transação própria (REQUIRES_NEW) para persistir
     * mesmo que a transação que originou a chamada esteja marcada para rollback —
     * nunca deve parecer que "nada aconteceu" quando a Darwin pode ter escrito algo.
     * O externalId é um placeholder ("darwin-pending-...") até uma reconciliação futura
     * localizar o scheduleId real (fora do escopo desta versão).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Agendamento registrarPendenciaReconciliacao(
            Clinica clinica,
            Paciente paciente,
            LocalDate data,
            String horarioInicio,
            String horarioFim,
            String mensagemSanitizada
    ) {
        Agendamento agendamento = new Agendamento();
        agendamento.setClinica(clinica);
        agendamento.setPaciente(paciente);
        agendamento.setExternalSource(ExternalProviderType.DARWIN);
        agendamento.setExternalId("darwin-pending-" + UUID.randomUUID());
        agendamento.setDataHoraInicio(combinar(data, horarioInicio));
        agendamento.setDataHoraFim(combinar(data, horarioFim));
        agendamento.setStatus("PENDENTE_RECONCILIACAO");
        agendamento.setOrigem("DARWIN");
        agendamento.setSyncStatus("PENDING_RECONCILIATION");
        agendamento.setSyncMensagemErro(mensagemSanitizada);
        agendamento.setSincronizadoEm(OffsetDateTime.now());
        Agendamento salvo = agendamentoRepository.save(agendamento);
        log.warn(
                "Pendencia de reconciliacao Darwin registrada: clinica={}, agendamentoLocalId={}",
                clinica.getId(), salvo.getId());
        return salvo;
    }

    /**
     * Marca o agendamento local como cancelado após confirmação de exclusão na Darwin.
     * Idempotente: se o agendamento não existir localmente (ex.: nunca foi espelhado), não faz nada.
     */
    @Transactional
    public Optional<Agendamento> marcarCancelado(Clinica clinica, String scheduleId) {
        return agendamentoRepository
                .findByClinicaIdAndExternalSourceAndExternalId(clinica.getId(), ExternalProviderType.DARWIN, scheduleId)
                .map(agendamento -> {
                    agendamento.setStatus("CANCELADO");
                    agendamento.setCanceladoEm(OffsetDateTime.now());
                    agendamento.setSyncStatus("CANCELLED");
                    agendamento.setSincronizadoEm(OffsetDateTime.now());
                    return agendamentoRepository.save(agendamento);
                });
    }

    /**
     * Marca falha de escrita: a chamada à Darwin falhou, então nada deve ser persistido
     * como sucesso. Se já existir um agendamento local (ex.: reagendamento que falhou),
     * registra a falha sanitizada nele; caso contrário não cria nenhum registro novo.
     */
    @Transactional
    public void marcarFalha(Clinica clinica, Long agendamentoLocalId, String mensagemSanitizada) {
        if (agendamentoLocalId == null) {
            return;
        }
        agendamentoRepository.findByIdAndClinicaId(agendamentoLocalId, clinica.getId())
                .ifPresent(agendamento -> {
                    agendamento.setSyncStatus("FAILED");
                    agendamento.setSyncMensagemErro(mensagemSanitizada);
                    agendamentoRepository.save(agendamento);
                });
    }

    private OffsetDateTime combinar(OffsetDateTime data, String horarioHHmm) {
        if (data == null) {
            return null;
        }
        LocalDate localDate = data.atZoneSameInstant(ZONA).toLocalDate();
        return combinar(localDate, horarioHHmm);
    }

    private OffsetDateTime combinar(LocalDate data, String horarioHHmm) {
        if (data == null) {
            return null;
        }
        LocalTime hora = horarioHHmm == null || horarioHHmm.isBlank()
                ? LocalTime.MIDNIGHT
                : LocalTime.parse(horarioHHmm);
        return data.atTime(hora).atZone(ZONA).toOffsetDateTime();
    }
}
