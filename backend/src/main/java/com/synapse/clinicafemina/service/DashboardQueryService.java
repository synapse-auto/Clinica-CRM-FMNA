package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.config.PerformanceCacheConfig;
import com.synapse.clinicafemina.dto.dashboard.HoraTotalDTO;
import com.synapse.clinicafemina.dto.dashboard.SerieDiariaDTO;
import com.synapse.clinicafemina.dto.dashboard.ServicoDistribuicaoDTO;
import com.synapse.clinicafemina.repository.AgendamentoRepository;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.MensagemRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import com.synapse.clinicafemina.service.cache.DashboardCacheKey;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardQueryService {

    private final UsuarioRepository usuarioRepository;
    private final PacienteRepository pacienteRepository;
    private final MensagemRepository mensagemRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final AtendimentoRepository atendimentoRepository;

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = PerformanceCacheConfig.DASHBOARD_SUMMARY, key = "#key", sync = true)
    public DashboardSummary summary(DashboardCacheKey key) {
        long novosPacientes = pacienteRepository.countNovosPorClinicaAndPeriodo(
                key.clinicId(), key.inicio(), key.fim());
        return new DashboardSummary(
                Math.toIntExact(usuarioRepository.countAtivosVisiveisByClinicaId(key.clinicId())),
                novosPacientes,
                mensagemRepository.countByClinicaAndPeriodo(key.clinicId(), key.inicio(), key.fim()),
                agendamentoRepository.countByClinicaAndPeriodo(key.clinicId(), key.inicio(), key.fim()),
                agendamentoRepository.countPendentesByClinicaAndPeriodo(key.clinicId(), key.inicio(), key.fim()),
                atendimentoRepository.calcularTempoMedioRespostaMinutos(
                        key.clinicId(), key.inicio(), key.fim()),
                pacienteRepository.countRecorrentesByClinicaAndPeriodo(
                        key.clinicId(), key.inicio(), key.fim())
        );
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = PerformanceCacheConfig.DASHBOARD_TIMESERIES, key = "#key", sync = true)
    public DashboardTimeseries timeseries(DashboardCacheKey key) {
        List<HoraTotalDTO> picoMensagens = mensagemRepository
                .countMensagensPorHora(key.clinicId(), key.inicio(), key.fim())
                .stream()
                .map(row -> new HoraTotalDTO(toInt(row[0]), toLong(row[1])))
                .toList();
        List<SerieDiariaDTO> pacientes = pacienteRepository
                .countPacientesPorDia(key.clinicId(), key.inicio(), key.fim())
                .stream()
                .map(row -> new SerieDiariaDTO(toLocalDate(row[0]), toLong(row[1])))
                .toList();
        List<SerieDiariaDTO> agendamentos = agendamentoRepository
                .countAgendamentosPorDia(key.clinicId(), key.inicio(), key.fim())
                .stream()
                .map(row -> new SerieDiariaDTO(toLocalDate(row[0]), toLong(row[1])))
                .toList();
        return new DashboardTimeseries(picoMensagens, pacientes, agendamentos);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = PerformanceCacheConfig.DASHBOARD_SERVICES, key = "#key", sync = true)
    public List<ServicoDistribuicaoDTO> services(DashboardCacheKey key) {
        List<Object[]> rows = agendamentoRepository.countServicosByClinicaAndPeriodo(
                key.clinicId(), key.inicio(), key.fim());
        List<Object[]> visible = rows.stream()
                .filter(row -> key.incluirCirurgias()
                        || !"CIRURGIA".equalsIgnoreCase(String.valueOf(row[0])))
                .toList();
        long total = visible.stream().mapToLong(row -> toLong(row[1])).sum();
        return visible.stream()
                .map(row -> {
                    long count = toLong(row[1]);
                    double percentage = total == 0 ? 0 : (count * 100.0) / total;
                    return new ServicoDistribuicaoDTO(
                            String.valueOf(row[0]), count, roundOne(percentage));
                })
                .toList();
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value));
    }

    private static int toInt(Object value) {
        return ((Number) value).intValue();
    }

    private static long toLong(Object value) {
        return ((Number) value).longValue();
    }

    private static double roundOne(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public record DashboardSummary(
            int equipeTotal,
            long novosPacientes,
            long totalMensagens,
            long consultasAgendadas,
            long confirmacoesPendentes,
            Double tempoMedioRespostaMinutos,
            long pacientesRecorrentes
    ) {
    }

    public record DashboardTimeseries(
            List<HoraTotalDTO> picoMensagensPorHora,
            List<SerieDiariaDTO> pacientes,
            List<SerieDiariaDTO> agendamentos
    ) {
    }
}
