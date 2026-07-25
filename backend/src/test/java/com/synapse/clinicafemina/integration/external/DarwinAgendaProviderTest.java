package com.synapse.clinicafemina.integration.external;

import com.synapse.clinicafemina.domain.Agendamento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.dto.agenda.NovoAgendamentoRequest;
import com.synapse.clinicafemina.dto.darwin.DarwinPatientScheduleResponse;
import com.synapse.clinicafemina.dto.darwin.DarwinWriteMessageResponse;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.exception.DarwinIntegrationException;
import com.synapse.clinicafemina.integration.DarwinClient;
import com.synapse.clinicafemina.repository.AgendamentoRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import com.synapse.clinicafemina.service.DarwinAgendaSyncService;
import com.synapse.clinicafemina.service.DarwinConsultaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DarwinAgendaProvider — escrita real Darwin + consistencia com o espelho local")
class DarwinAgendaProviderTest {

    @Mock
    private DarwinClient darwinClient;

    @Mock
    private DarwinConsultaService darwinConsultaService;

    @Mock
    private DarwinAgendaSyncService syncService;

    @Mock
    private AgendamentoRepository agendamentoRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    private DarwinAgendaProvider provider;
    private Clinica clinica;
    private Paciente paciente;

    @BeforeEach
    void setUp() {
        provider = new DarwinAgendaProvider(
                darwinClient, darwinConsultaService, syncService, agendamentoRepository, pacienteRepository);
        clinica = new Clinica();
        clinica.setId(1L);
        paciente = new Paciente();
        paciente.setId(10L);
        paciente.setNome("Paciente Teste");
        paciente.setCpf("11144477735");
    }

    @Test
    @DisplayName("capacidades: clinicWide=false, fitIn=true, write=true")
    void capabilities_matchDarwinReality() {
        assertThat(provider.providerType()).isEqualTo(ExternalProviderType.DARWIN);
        assertThat(provider.supportsClinicWideListing()).isFalse();
        assertThat(provider.supportsFitIn()).isTrue();
        assertThat(provider.supportsWriteOperations()).isTrue();
    }

    @Test
    @DisplayName("capacidades explicitas: catalogo, busca de paciente e backfill suportados, coverage=KNOWN_CRM_PATIENTS_ONLY")
    void capabilities_explicit_matchDarwinReality() {
        assertThat(provider.supportsCatalog()).isTrue();
        assertThat(provider.supportsPatientLookup()).isTrue();
        assertThat(provider.supportsBackfill()).isTrue();
        assertThat(provider.coverage()).isEqualTo("KNOWN_CRM_PATIENTS_ONLY");
    }

    @Test
    @DisplayName("criarAgendamento: sucesso na Darwin + reconciliacao encontra o schedule -> SYNCED")
    void criarAgendamento_successAndReconciliationFindsMatch() {
        when(pacienteRepository.findByIdAndClinicaId(10L, 1L)).thenReturn(Optional.of(paciente));
        when(darwinClient.criarAgendamento(any())).thenReturn(new DarwinWriteMessageResponse("ok"));
        DarwinPatientScheduleResponse.Schedule scheduleCorrespondente = schedule("sch-novo", "09:00");
        when(darwinConsultaService.listarAgendamentosPorCpf(any(), any(), any(), any()))
                .thenReturn(new DarwinPatientScheduleResponse(null, List.of(), List.of(scheduleCorrespondente)));
        Agendamento salvo = new Agendamento();
        salvo.setId(50L);
        salvo.setSyncStatus("SYNCED");
        when(syncService.upsertSchedule(clinica, paciente, scheduleCorrespondente)).thenReturn(salvo);

        var resultado = provider.criarAgendamento(clinica, new NovoAgendamentoRequest(
                10L, null, null, null, "tt-1", LocalDate.of(2026, 7, 20), "09:00", "09:30",
                "proc-1", "Consulta", "ins-1", "obs"));

        assertThat(resultado.syncStatus()).isEqualTo("SYNCED");
        verify(syncService, never()).registrarPendenciaReconciliacao(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("criarAgendamento: sucesso na Darwin mas reconciliacao nao encontra correspondencia -> PENDING_RECONCILIATION")
    void criarAgendamento_successButReconciliationFindsNothing() {
        when(pacienteRepository.findByIdAndClinicaId(10L, 1L)).thenReturn(Optional.of(paciente));
        when(darwinClient.criarAgendamento(any())).thenReturn(new DarwinWriteMessageResponse("ok"));
        when(darwinConsultaService.listarAgendamentosPorCpf(any(), any(), any(), any()))
                .thenReturn(new DarwinPatientScheduleResponse(null, List.of(), List.of()));
        Agendamento pendente = new Agendamento();
        pendente.setId(51L);
        pendente.setSyncStatus("PENDING_RECONCILIATION");
        when(syncService.registrarPendenciaReconciliacao(any(), any(), any(), any(), any(), any()))
                .thenReturn(pendente);

        var resultado = provider.criarAgendamento(clinica, new NovoAgendamentoRequest(
                10L, null, null, null, "tt-1", LocalDate.of(2026, 7, 20), "09:00", "09:30",
                "proc-1", "Consulta", "ins-1", "obs"));

        assertThat(resultado.syncStatus()).isEqualTo("PENDING_RECONCILIATION");
        verify(syncService, never()).upsertSchedule(any(), any(), any());
    }

    @Test
    @DisplayName("criarAgendamento: falha na Darwin propaga erro sanitizado e nao persiste sucesso local")
    void criarAgendamento_darwinFailurePropagatesWithoutLocalSuccess() {
        when(pacienteRepository.findByIdAndClinicaId(10L, 1L)).thenReturn(Optional.of(paciente));
        when(darwinClient.criarAgendamento(any())).thenThrow(
                HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "erro", HttpHeaders.EMPTY, new byte[0], null));

        assertThatThrownBy(() -> provider.criarAgendamento(clinica, new NovoAgendamentoRequest(
                10L, null, null, null, "tt-1", LocalDate.of(2026, 7, 20), "09:00", "09:30",
                "proc-1", "Consulta", "ins-1", "obs")))
                .isInstanceOf(DarwinIntegrationException.class);

        verify(syncService, never()).upsertSchedule(any(), any(), any());
        verify(syncService, never()).registrarPendenciaReconciliacao(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("cancelarAgendamento: sucesso na Darwin marca o espelho local como cancelado")
    void cancelarAgendamento_success() {
        Agendamento existente = new Agendamento();
        existente.setId(20L);
        existente.setExternalId("sch-1");
        when(agendamentoRepository.findByIdAndClinicaId(20L, 1L)).thenReturn(Optional.of(existente));
        when(darwinClient.excluirAgendamento("sch-1")).thenReturn(new DarwinWriteMessageResponse("ok"));

        provider.cancelarAgendamento(clinica, 20L, "Paciente desistiu");

        verify(syncService).marcarCancelado(clinica, "sch-1");
    }

    @Test
    @DisplayName("cancelarAgendamento: falha na Darwin nao marca o espelho local como cancelado")
    void cancelarAgendamento_darwinFailureDoesNotCancelLocally() {
        Agendamento existente = new Agendamento();
        existente.setId(20L);
        existente.setExternalId("sch-1");
        when(agendamentoRepository.findByIdAndClinicaId(20L, 1L)).thenReturn(Optional.of(existente));
        when(darwinClient.excluirAgendamento("sch-1")).thenThrow(
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "erro", HttpHeaders.EMPTY, new byte[0], null));

        assertThatThrownBy(() -> provider.cancelarAgendamento(clinica, 20L, "motivo"))
                .isInstanceOf(DarwinIntegrationException.class);

        verify(syncService, never()).marcarCancelado(any(), any());
    }

    @Test
    @DisplayName("listarPorPaciente rejeita CPF invalido antes de chamar a Darwin")
    void listarPorPaciente_rejectsInvalidCpfBeforeCallingDarwin() {
        assertThatThrownBy(() -> provider.listarPorPaciente(clinica, "123"))
                .isInstanceOf(BadRequestException.class);

        verify(darwinConsultaService, never()).listarAgendamentosPorCpf(any(), any(), any(), any());
    }

    @Test
    @DisplayName("buscarPaciente retorna vazio quando a Darwin responde 404 (CPF fora do escopo)")
    void buscarPaciente_returnsEmptyOn404() {
        when(darwinConsultaService.buscarPacientePorCpf(any()))
                .thenThrow(new DarwinIntegrationException(404, "nao encontrado"));

        Optional<?> resultado = provider.buscarPaciente(clinica, "11144477735");

        assertThat(resultado).isEmpty();
    }

    private DarwinPatientScheduleResponse.Schedule schedule(String scheduleId, String time) {
        return new DarwinPatientScheduleResponse.Schedule(
                OffsetDateTime.parse("2026-07-20T00:00:00-03:00"), time, "st-1", scheduleId, "Marcado",
                "loc-1", "prof-1", "Dra. Fulana", "09:30", "tt-1", "Unidade Centro", null, null);
    }
}
