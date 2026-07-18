package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.dto.dashboard.DashboardPeriodo;
import com.synapse.clinicafemina.dto.dashboard.DashboardResponse;
import com.synapse.clinicafemina.service.cache.DashboardCacheKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final ZoneId ZONE_SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private final DashboardQueryService dashboardQueryService;

    @Transactional(readOnly = true)
    public DashboardResponse obterDashboard(Clinica clinica, DashboardPeriodo periodo, LocalDate data) {
        PeriodoCalculado intervalo = calcularPeriodo(periodo, data);
        DashboardCacheKey key = new DashboardCacheKey(
                clinica.getId(), intervalo.inicio(), intervalo.fim(),
                ZONE_SAO_PAULO.getId(), clinica.getUsaCirurgiasNaAgenda());
        DashboardQueryService.DashboardSummary summary = dashboardQueryService.summary(key);
        DashboardQueryService.DashboardTimeseries timeseries = dashboardQueryService.timeseries(key);
        double taxaFidelizacao = summary.novosPacientes() == 0
                ? 0
                : roundOne((summary.pacientesRecorrentes() * 100.0) / summary.novosPacientes());

        return new DashboardResponse(
                summary.equipeTotal(),
                summary.equipeTotal(),
                summary.novosPacientes(),
                summary.totalMensagens(),
                summary.consultasAgendadas(),
                summary.confirmacoesPendentes(),
                formatMinutes(summary.tempoMedioRespostaMinutos()),
                timeseries.picoMensagensPorHora(),
                timeseries.pacientes(),
                timeseries.agendamentos(),
                dashboardQueryService.services(key),
                taxaFidelizacao
        );
    }

    private PeriodoCalculado calcularPeriodo(DashboardPeriodo periodo, LocalDate data) {
        LocalDate base = data != null ? data : LocalDate.now(ZONE_SAO_PAULO);
        LocalDate inicioData = switch (periodo != null ? periodo : DashboardPeriodo.DIA) {
            case DIA -> base;
            case SEMANA -> base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MES -> base.withDayOfMonth(1);
        };
        LocalDate fimData = switch (periodo != null ? periodo : DashboardPeriodo.DIA) {
            case DIA -> inicioData.plusDays(1);
            case SEMANA -> inicioData.plusDays(7);
            case MES -> inicioData.plusMonths(1);
        };
        return new PeriodoCalculado(
                inicioData.atStartOfDay(ZONE_SAO_PAULO).toOffsetDateTime(),
                fimData.atStartOfDay(ZONE_SAO_PAULO).toOffsetDateTime()
        );
    }

    private String formatMinutes(Double minutes) {
        double value = minutes == null ? 0 : minutes;
        return String.format(Locale.forLanguageTag("pt-BR"), "%.1f min", value);
    }

    private double roundOne(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record PeriodoCalculado(OffsetDateTime inicio, OffsetDateTime fim) {
    }
}
