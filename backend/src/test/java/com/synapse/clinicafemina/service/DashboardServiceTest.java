package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Gestor;
import com.synapse.clinicafemina.dto.dashboard.DashboardPeriodo;
import com.synapse.clinicafemina.dto.dashboard.DashboardResponse;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import com.synapse.clinicafemina.repository.AgendamentoRepository;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.MensagemRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private MensagemRepository mensagemRepository;

    @Mock
    private AgendamentoRepository agendamentoRepository;

    @Mock
    private AtendimentoRepository atendimentoRepository;

    @Test
    void should_aggregate_dashboard_from_internal_data_without_calling_external_provider() {
        Clinica clinica = new Clinica();
        clinica.setId(7L);
        clinica.setSlug("ultramedical");
        clinica.setExternalProvider(ExternalProviderType.MEDWARE);
        clinica.setUsaCirurgiasNaAgenda(false);

        Gestor gestor = new Gestor();
        gestor.setClinica(clinica);
        gestor.setAtivo(true);

        when(usuarioRepository.findAtivosVisiveisByClinicaId(7L)).thenReturn(List.of(gestor));
        when(pacienteRepository.countNovosPorClinicaAndPeriodo(eqLong(7L), any(), any())).thenReturn(5L);
        when(mensagemRepository.countByClinicaAndPeriodo(eqLong(7L), any(), any())).thenReturn(12L);
        when(agendamentoRepository.countByClinicaAndPeriodo(eqLong(7L), any(), any())).thenReturn(8L);
        when(agendamentoRepository.countPendentesByClinicaAndPeriodo(eqLong(7L), any(), any())).thenReturn(3L);
        when(atendimentoRepository.calcularTempoMedioRespostaMinutos(eqLong(7L), any(), any())).thenReturn(4.5);
        when(mensagemRepository.countMensagensPorHora(eqLong(7L), any(), any())).thenReturn(List.<Object[]>of(new Object[]{9, 4L}));
        when(pacienteRepository.countPacientesPorDia(eqLong(7L), any(), any())).thenReturn(List.<Object[]>of(new Object[]{LocalDate.of(2026, 6, 8), 2L}));
        when(agendamentoRepository.countAgendamentosPorDia(eqLong(7L), any(), any())).thenReturn(List.<Object[]>of(new Object[]{LocalDate.of(2026, 6, 8), 6L}));
        when(agendamentoRepository.countServicosByClinicaAndPeriodo(eqLong(7L), any(), any())).thenReturn(List.<Object[]>of(
                new Object[]{"Ultrassom", 6L},
                new Object[]{"CIRURGIA", 4L}
        ));
        when(pacienteRepository.countRecorrentesByClinicaAndPeriodo(eqLong(7L), any(), any())).thenReturn(2L);

        DashboardService service = new DashboardService(
                usuarioRepository,
                pacienteRepository,
                mensagemRepository,
                agendamentoRepository,
                atendimentoRepository
        );

        DashboardResponse response = service.obterDashboard(
                clinica,
                DashboardPeriodo.DIA,
                LocalDate.of(2026, 6, 14)
        );

        assertEquals(1, response.equipeOnline());
        assertEquals(5L, response.novosPacientes());
        assertEquals(12L, response.totalMensagens());
        assertEquals(8L, response.consultasAgendadas());
        assertEquals(3L, response.confirmacoesPendentes());
        assertEquals("4,5 min", response.tempoMedioResposta());
        assertEquals(1, response.distribuicaoServicos().size());
        assertEquals("Ultrassom", response.distribuicaoServicos().getFirst().servico());
        assertEquals(100.0, response.distribuicaoServicos().getFirst().percentual());
        assertEquals(40.0, response.taxaFidelizacao());
    }

    private Long eqLong(Long value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
