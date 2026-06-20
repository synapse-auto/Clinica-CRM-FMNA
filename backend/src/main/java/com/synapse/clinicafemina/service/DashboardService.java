package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.dto.dashboard.DashboardPeriodo;
import com.synapse.clinicafemina.dto.dashboard.DashboardResponse;
import com.synapse.clinicafemina.dto.dashboard.HoraTotalDTO;
import com.synapse.clinicafemina.dto.dashboard.SerieDiariaDTO;
import com.synapse.clinicafemina.dto.dashboard.ServicoDistribuicaoDTO;
import com.synapse.clinicafemina.repository.AgendamentoRepository;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.MensagemRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final ZoneId ZONE_SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private final UsuarioRepository usuarioRepository;
    private final PacienteRepository pacienteRepository;
    private final MensagemRepository mensagemRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final AtendimentoRepository atendimentoRepository;

    @Transactional(readOnly = true)
    public DashboardResponse obterDashboard(Clinica clinica, DashboardPeriodo periodo, LocalDate data) {
        PeriodoCalculado intervalo = calcularPeriodo(periodo, data);
        Long clinicaId = clinica.getId();

        int equipeTotal = usuarioRepository.findAtivosVisiveisByClinicaId(clinicaId).size();
        long novosPacientes = pacienteRepository.countNovosPorClinicaAndPeriodo(
                clinicaId, intervalo.inicio(), intervalo.fim());
        long totalMensagens = mensagemRepository.countByClinicaAndPeriodo(
                clinicaId, intervalo.inicio(), intervalo.fim());
        long consultasAgendadas = agendamentoRepository.countByClinicaAndPeriodo(
                clinicaId, intervalo.inicio(), intervalo.fim());
        long confirmacoesPendentes = agendamentoRepository.countPendentesByClinicaAndPeriodo(
                clinicaId, intervalo.inicio(), intervalo.fim());
        Double tempoMedio = atendimentoRepository.calcularTempoMedioRespostaMinutos(
                clinicaId, intervalo.inicio(), intervalo.fim());
        long recorrentes = pacienteRepository.countRecorrentesByClinicaAndPeriodo(
                clinicaId, intervalo.inicio(), intervalo.fim());

        List<HoraTotalDTO> picoMensagens = mensagemRepository
                .countMensagensPorHora(clinicaId, intervalo.inicio(), intervalo.fim())
                .stream()
                .map(row -> new HoraTotalDTO(toInt(row[0]), toLong(row[1])))
                .toList();

        List<SerieDiariaDTO> pacientesSemana = pacienteRepository
                .countPacientesPorDia(clinicaId, intervalo.inicio(), intervalo.fim())
                .stream()
                .map(row -> new SerieDiariaDTO(toLocalDate(row[0]), toLong(row[1])))
                .toList();

        List<SerieDiariaDTO> agendamentosSemana = agendamentoRepository
                .countAgendamentosPorDia(clinicaId, intervalo.inicio(), intervalo.fim())
                .stream()
                .map(row -> new SerieDiariaDTO(toLocalDate(row[0]), toLong(row[1])))
                .toList();

        List<Object[]> servicosRows = agendamentoRepository.countServicosByClinicaAndPeriodo(
                clinicaId, intervalo.inicio(), intervalo.fim());
        List<Object[]> servicosVisiveis = servicosRows.stream()
                .filter(row -> clinica.getUsaCirurgiasNaAgenda()
                        || !"CIRURGIA".equalsIgnoreCase(String.valueOf(row[0])))
                .toList();
        long totalServicos = servicosVisiveis.stream().mapToLong(row -> toLong(row[1])).sum();
        List<ServicoDistribuicaoDTO> distribuicao = servicosVisiveis.stream()
                .map(row -> {
                    long total = toLong(row[1]);
                    double percentual = totalServicos == 0 ? 0 : (total * 100.0) / totalServicos;
                    return new ServicoDistribuicaoDTO(String.valueOf(row[0]), total, roundOne(percentual));
                })
                .toList();

        double taxaFidelizacao = novosPacientes == 0 ? 0 : roundOne((recorrentes * 100.0) / novosPacientes);

        return new DashboardResponse(
                equipeTotal,
                equipeTotal,
                novosPacientes,
                totalMensagens,
                consultasAgendadas,
                confirmacoesPendentes,
                formatMinutes(tempoMedio),
                picoMensagens,
                pacientesSemana,
                agendamentosSemana,
                distribuicao,
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

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value));
    }

    private int toInt(Object value) {
        return ((Number) value).intValue();
    }

    private long toLong(Object value) {
        return ((Number) value).longValue();
    }

    private double roundOne(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record PeriodoCalculado(OffsetDateTime inicio, OffsetDateTime fim) {
    }
}
