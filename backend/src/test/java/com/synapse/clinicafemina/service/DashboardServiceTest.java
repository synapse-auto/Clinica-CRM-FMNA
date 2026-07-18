package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.dto.dashboard.DashboardPeriodo;
import com.synapse.clinicafemina.dto.dashboard.DashboardResponse;
import com.synapse.clinicafemina.dto.dashboard.HoraTotalDTO;
import com.synapse.clinicafemina.dto.dashboard.SerieDiariaDTO;
import com.synapse.clinicafemina.dto.dashboard.ServicoDistribuicaoDTO;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private DashboardQueryService dashboardQueryService;

    @Test
    void should_aggregate_dashboard_from_internal_data_without_calling_external_provider() {
        Clinica clinica = new Clinica();
        clinica.setId(7L);
        clinica.setSlug("ultramedical");
        clinica.setExternalProvider(ExternalProviderType.MEDWARE);
        clinica.setUsaCirurgiasNaAgenda(false);

        when(dashboardQueryService.summary(any())).thenReturn(new DashboardQueryService.DashboardSummary(
                1, 5, 12, 8, 3, 4.5, 2));
        when(dashboardQueryService.timeseries(any())).thenReturn(new DashboardQueryService.DashboardTimeseries(
                List.of(new HoraTotalDTO(9, 4)),
                List.of(new SerieDiariaDTO(LocalDate.of(2026, 6, 8), 2)),
                List.of(new SerieDiariaDTO(LocalDate.of(2026, 6, 8), 6))
        ));
        when(dashboardQueryService.services(any())).thenReturn(List.of(
                new ServicoDistribuicaoDTO("Ultrassom", 6, 100.0)));

        DashboardService service = new DashboardService(dashboardQueryService);

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
}
