package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.dto.agenda.AtualizarAgendamentoRequest;
import com.synapse.clinicafemina.dto.agenda.NovoAgendamentoRequest;
import com.synapse.clinicafemina.dto.agendamento.AgendamentoResponse;
import com.synapse.clinicafemina.exception.AgendaOperationNotSupportedException;
import com.synapse.clinicafemina.integration.external.AgendaExternalProvider;
import com.synapse.clinicafemina.integration.external.AgendaProviderFactory;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgendaService — resolucao provider-agnostic; Medware reutiliza fluxo local existente")
class AgendaServiceTest {

    @Mock
    private AgendaProviderFactory providerFactory;

    @Mock
    private AgendamentoService agendamentoService;

    @Mock
    private AgendaExternalProvider medwareProvider;

    @Mock
    private AgendaExternalProvider darwinProvider;

    private AgendaService service;
    private Clinica clinicaMedware;
    private Clinica clinicaDarwin;

    @BeforeEach
    void setUp() {
        service = new AgendaService(providerFactory, agendamentoService);
        clinicaMedware = new Clinica();
        clinicaMedware.setId(1L);
        clinicaMedware.setExternalProvider(ExternalProviderType.MEDWARE);
        clinicaDarwin = new Clinica();
        clinicaDarwin.setId(2L);
        clinicaDarwin.setExternalProvider(ExternalProviderType.DARWIN);
    }

    @Test
    @DisplayName("criarAgendamento para clinica Medware (supportsWriteOperations=false) reutiliza AgendamentoService, nao chama o provider")
    void criarAgendamento_medwareDelegatesToLegacyFlow() {
        when(providerFactory.getProvider(ExternalProviderType.MEDWARE)).thenReturn(medwareProvider);
        when(medwareProvider.supportsWriteOperations()).thenReturn(false);
        when(agendamentoService.criar(eq(clinicaMedware), any())).thenReturn(new AgendamentoResponse(
                1L, 5L, "Paciente", null, null, OffsetDateTime.now(), null, "CONSULTA", "Agendamento",
                "AGENDADO", "MANUAL", 0, null, null));

        var resultado = service.criarAgendamento(clinicaMedware, new NovoAgendamentoRequest(
                5L, null, null, null, null, LocalDate.of(2026, 7, 20), "09:00", "09:30",
                null, "Consulta", null, null));

        assertThat(resultado.idLocal()).isEqualTo(1L);
        verify(medwareProvider, never()).criarAgendamento(any(), any());
    }

    @Test
    @DisplayName("criarAgendamento para clinica Darwin (supportsWriteOperations=true) delega ao provider, nao usa fluxo legado")
    void criarAgendamento_darwinDelegatesToProvider() {
        when(providerFactory.getProvider(ExternalProviderType.DARWIN)).thenReturn(darwinProvider);
        when(darwinProvider.supportsWriteOperations()).thenReturn(true);
        NovoAgendamentoRequest dados = new NovoAgendamentoRequest(
                5L, null, "prof-1", null, "tt-1", LocalDate.of(2026, 7, 20), "09:00", "09:30",
                "proc-1", "Consulta", "ins-1", null);

        service.criarAgendamento(clinicaDarwin, dados);

        verify(darwinProvider).criarAgendamento(clinicaDarwin, dados);
        verify(agendamentoService, never()).criar(any(), any());
    }

    @Test
    @DisplayName("criarEncaixe lanca AgendaOperationNotSupportedException quando o provider nao suporta encaixe")
    void criarEncaixe_throwsWhenFitInNotSupported() {
        when(providerFactory.getProvider(ExternalProviderType.MEDWARE)).thenReturn(medwareProvider);
        when(medwareProvider.supportsFitIn()).thenReturn(false);

        assertThatThrownBy(() -> service.criarEncaixe(clinicaMedware, new NovoAgendamentoRequest(
                5L, null, null, null, null, LocalDate.of(2026, 7, 20), "09:00", "09:30",
                null, "Consulta", null, null)))
                .isInstanceOf(AgendaOperationNotSupportedException.class);
    }

    @Test
    @DisplayName("cancelarAgendamento para Medware reutiliza AgendamentoService.cancelar")
    void cancelarAgendamento_medwareDelegatesToLegacyFlow() {
        when(providerFactory.getProvider(ExternalProviderType.MEDWARE)).thenReturn(medwareProvider);
        when(medwareProvider.supportsWriteOperations()).thenReturn(false);

        service.cancelarAgendamento(clinicaMedware, 1L, "Cancelado pelo paciente");

        verify(agendamentoService).cancelar(eq(clinicaMedware), eq(1L), any());
        verify(medwareProvider, never()).cancelarAgendamento(any(), any(), any());
    }

    @Test
    @DisplayName("atualizarAgendamento para Darwin delega ao provider")
    void atualizarAgendamento_darwinDelegatesToProvider() {
        when(providerFactory.getProvider(ExternalProviderType.DARWIN)).thenReturn(darwinProvider);
        when(darwinProvider.supportsWriteOperations()).thenReturn(true);
        AtualizarAgendamentoRequest dados = new AtualizarAgendamentoRequest(
                "Confirmado", null, null, null, null, null, null, null);

        service.atualizarAgendamento(clinicaDarwin, 3L, dados);

        verify(darwinProvider).atualizarAgendamento(clinicaDarwin, 3L, dados);
    }
}
