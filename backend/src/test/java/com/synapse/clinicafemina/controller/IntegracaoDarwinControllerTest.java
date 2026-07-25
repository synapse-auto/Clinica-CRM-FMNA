package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.dto.darwin.DarwinPatientRecordDTO;
import com.synapse.clinicafemina.dto.darwin.DarwinStatusResponse;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.exception.DarwinNotAvailableException;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import com.synapse.clinicafemina.service.ClinicaConfigService;
import com.synapse.clinicafemina.service.DarwinConsultaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("IntegracaoDarwinController — guarda de disponibilidade + delegação sob demanda")
class IntegracaoDarwinControllerTest {

    @Mock
    private ClinicaConfigService clinicaConfigService;

    @Mock
    private DarwinConsultaService darwinConsultaService;

    private IntegracaoDarwinController controller;
    private Clinica clinicaDarwin;

    @BeforeEach
    void setUp() {
        controller = new IntegracaoDarwinController(clinicaConfigService, darwinConsultaService);
        clinicaDarwin = new Clinica();
        clinicaDarwin.setId(1L);
        clinicaDarwin.setExternalProvider(ExternalProviderType.DARWIN);
    }

    @Test
    @DisplayName("status() nao depende da clinica atual nem da guarda de disponibilidade")
    void status_bypassesGuard() {
        when(darwinConsultaService.status())
                .thenReturn(new DarwinStatusResponse(
                        true, "DARWIN", true, false, true, false, true, true, "KNOWN_CRM_PATIENTS_ONLY"));

        DarwinStatusResponse result = controller.status();

        assertThat(result.enabled()).isTrue();
        verifyNoInteractions(clinicaConfigService);
    }

    @Test
    @DisplayName("rotas operacionais rejeitam clinica que nao usa provider Darwin")
    void operationalRoutes_rejectNonDarwinClinic() {
        Clinica clinicaMedware = new Clinica();
        clinicaMedware.setExternalProvider(ExternalProviderType.MEDWARE);
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinicaMedware);

        assertThatThrownBy(() -> controller.profissionais())
                .isInstanceOf(DarwinNotAvailableException.class);
        verifyNoInteractions(darwinConsultaService);
    }

    @Test
    @DisplayName("rotas operacionais rejeitam quando a integracao Darwin esta desabilitada")
    void operationalRoutes_rejectWhenDisabled() {
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinicaDarwin);
        when(darwinConsultaService.isEnabled()).thenReturn(false);

        assertThatThrownBy(() -> controller.profissionais())
                .isInstanceOf(DarwinNotAvailableException.class);
        verify(darwinConsultaService, never()).listarProfissionaisDoLocal();
    }

    @Test
    @DisplayName("profissionais() delega ao service quando disponivel")
    void profissionais_delegatesWhenAvailable() {
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinicaDarwin);
        when(darwinConsultaService.isEnabled()).thenReturn(true);
        when(darwinConsultaService.listarProfissionaisDoLocal()).thenReturn(List.of());

        controller.profissionais();

        verify(darwinConsultaService).listarProfissionaisDoLocal();
    }

    @Test
    @DisplayName("horarios() rejeita data ausente/invalida antes de chamar o service")
    void horarios_rejectsMissingOrInvalidDate() {
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinicaDarwin);
        when(darwinConsultaService.isEnabled()).thenReturn(true);

        assertThatThrownBy(() -> controller.horarios("nao-e-uma-data", null))
                .isInstanceOf(BadRequestException.class);
        verify(darwinConsultaService, never()).listarHorariosDisponiveis(any(), any());
    }

    @Test
    @DisplayName("horarios() parseia data ISO e delega ao service")
    void horarios_parsesIsoDateAndDelegates() {
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinicaDarwin);
        when(darwinConsultaService.isEnabled()).thenReturn(true);

        controller.horarios("2026-07-20", null);

        verify(darwinConsultaService).listarHorariosDisponiveis(LocalDate.of(2026, 7, 20), null);
    }

    @Test
    @DisplayName("paciente() delega cpf recebido diretamente ao service (validacao no service)")
    void paciente_delegatesCpfToService() {
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinicaDarwin);
        when(darwinConsultaService.isEnabled()).thenReturn(true);
        when(darwinConsultaService.buscarPacientePorCpf("000.000.000-00"))
                .thenReturn(new DarwinPatientRecordDTO(
                        "000.000.000-00", "Paciente Teste", null, null, null, null, null,
                        null, null, null, null, null, null, null, List.of(), null));

        DarwinPatientRecordDTO result = controller.paciente("000.000.000-00");

        assertThat(result.name()).isEqualTo("Paciente Teste");
    }

    @Test
    @DisplayName("agendamentos() parseia startDate/endDate opcionais e delega")
    void agendamentos_parsesOptionalDatesAndDelegates() {
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinicaDarwin);
        when(darwinConsultaService.isEnabled()).thenReturn(true);

        controller.agendamentos("000.000.000-00", "2026-07-01", "2026-07-31", "Marcado");

        verify(darwinConsultaService).listarAgendamentosPorCpf(
                "000.000.000-00", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), "Marcado");
    }

    @Test
    @DisplayName("agendamentos() rejeita data em formato invalido")
    void agendamentos_rejectsInvalidDateFormat() {
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinicaDarwin);
        when(darwinConsultaService.isEnabled()).thenReturn(true);

        assertThatThrownBy(() -> controller.agendamentos("000.000.000-00", "20/07/2026", null, null))
                .isInstanceOf(BadRequestException.class);
        verify(darwinConsultaService, never()).listarAgendamentosPorCpf(any(), any(), any(), any());
    }

    @Test
    @DisplayName("procedimentos() delega page/amount diretamente ao service")
    void procedimentos_delegatesPagination() {
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinicaDarwin);
        when(darwinConsultaService.isEnabled()).thenReturn(true);

        controller.procedimentos("loc-1", "Consulta", 1, 20);

        verify(darwinConsultaService).listarProcedimentos("loc-1", "Consulta", 1, 20);
    }

    @Test
    @DisplayName("locais() delega ao service quando disponivel")
    void locais_delegatesWhenAvailable() {
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinicaDarwin);
        when(darwinConsultaService.isEnabled()).thenReturn(true);

        controller.locais();

        verify(darwinConsultaService).listarLocaisDoProfissional();
    }

    @Test
    @DisplayName("convenios() delega page/amount diretamente ao service")
    void convenios_delegatesPagination() {
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinicaDarwin);
        when(darwinConsultaService.isEnabled()).thenReturn(true);

        controller.convenios("loc-1", "Particular", 1, 20);

        verify(darwinConsultaService).listarConvenios("loc-1", "Particular", 1, 20);
    }

    @Test
    @DisplayName("grades() delega parametros opcionais e startDate parseada")
    void grades_delegatesOptionalParamsAndParsedStartDate() {
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinicaDarwin);
        when(darwinConsultaService.isEnabled()).thenReturn(true);

        controller.grades("prof-1", null, "ativa", "monday", true, "2026-07-01");

        verify(darwinConsultaService).listarGradesDoProfissional(
                "prof-1", null, "ativa", "monday", true, LocalDate.of(2026, 7, 1));
    }
}
