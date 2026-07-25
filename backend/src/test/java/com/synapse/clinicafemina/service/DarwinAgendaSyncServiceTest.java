package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Agendamento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.dto.darwin.DarwinPatientScheduleResponse;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import com.synapse.clinicafemina.repository.AgendamentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DarwinAgendaSyncService — espelho local idempotente + estados de sincronizacao")
class DarwinAgendaSyncServiceTest {

    @Mock
    private AgendamentoRepository agendamentoRepository;

    private DarwinAgendaSyncService service;
    private Clinica clinica;
    private Paciente paciente;

    @BeforeEach
    void setUp() {
        service = new DarwinAgendaSyncService(agendamentoRepository);
        clinica = new Clinica();
        clinica.setId(1L);
        paciente = new Paciente();
        paciente.setId(10L);
    }

    private DarwinPatientScheduleResponse.Schedule schedule(String scheduleId) {
        return new DarwinPatientScheduleResponse.Schedule(
                OffsetDateTime.parse("2026-07-20T00:00:00-03:00"), "09:00", "st-1", scheduleId, "Marcado",
                "loc-1", "prof-1", "Dra. Fulana", "09:30", "tt-1", "Unidade Centro", null, null);
    }

    @Test
    @DisplayName("upsertSchedule cria novo agendamento local quando nao existe ainda")
    void upsertSchedule_createsWhenAbsent() {
        when(agendamentoRepository.findByClinicaIdAndExternalSourceAndExternalId(1L, ExternalProviderType.DARWIN, "sch-1"))
                .thenReturn(Optional.empty());
        when(agendamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Agendamento resultado = service.upsertSchedule(clinica, paciente, schedule("sch-1"));

        assertThat(resultado.getExternalId()).isEqualTo("sch-1");
        assertThat(resultado.getExternalSource()).isEqualTo(ExternalProviderType.DARWIN);
        assertThat(resultado.getSyncStatus()).isEqualTo("SYNCED");
        assertThat(resultado.getProfissionalNome()).isEqualTo("Dra. Fulana");
    }

    @Test
    @DisplayName("upsertSchedule reutiliza o mesmo registro local em chamadas repetidas (idempotente, sem duplicar)")
    void upsertSchedule_isIdempotent() {
        Agendamento existente = new Agendamento();
        existente.setId(99L);
        when(agendamentoRepository.findByClinicaIdAndExternalSourceAndExternalId(1L, ExternalProviderType.DARWIN, "sch-1"))
                .thenReturn(Optional.of(existente));
        when(agendamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Agendamento primeira = service.upsertSchedule(clinica, paciente, schedule("sch-1"));
        Agendamento segunda = service.upsertSchedule(clinica, paciente, schedule("sch-1"));

        assertThat(primeira.getId()).isEqualTo(99L);
        assertThat(segunda.getId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("registrarPendenciaReconciliacao persiste com syncStatus=PENDING_RECONCILIATION e externalId placeholder")
    void registrarPendenciaReconciliacao_persistsPendingState() {
        when(agendamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Agendamento resultado = service.registrarPendenciaReconciliacao(
                clinica, paciente, LocalDate.of(2026, 7, 20), "09:00", "09:30", "mensagem sanitizada");

        assertThat(resultado.getSyncStatus()).isEqualTo("PENDING_RECONCILIATION");
        assertThat(resultado.getExternalId()).startsWith("darwin-pending-");
        assertThat(resultado.getSyncMensagemErro()).isEqualTo("mensagem sanitizada");
    }

    @Test
    @DisplayName("marcarCancelado marca agendamento existente como CANCELADO/CANCELLED")
    void marcarCancelado_updatesExistingAgendamento() {
        Agendamento existente = new Agendamento();
        existente.setId(5L);
        when(agendamentoRepository.findByClinicaIdAndExternalSourceAndExternalId(1L, ExternalProviderType.DARWIN, "sch-1"))
                .thenReturn(Optional.of(existente));
        when(agendamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<Agendamento> resultado = service.marcarCancelado(clinica, "sch-1");

        assertThat(resultado).isPresent();
        assertThat(resultado.get().getStatus()).isEqualTo("CANCELADO");
        assertThat(resultado.get().getSyncStatus()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("marcarCancelado e idempotente: agendamento inexistente localmente nao gera erro nem registro novo")
    void marcarCancelado_isNoOpWhenNotFoundLocally() {
        when(agendamentoRepository.findByClinicaIdAndExternalSourceAndExternalId(1L, ExternalProviderType.DARWIN, "sch-x"))
                .thenReturn(Optional.empty());

        Optional<Agendamento> resultado = service.marcarCancelado(clinica, "sch-x");

        assertThat(resultado).isEmpty();
        verify(agendamentoRepository, never()).save(any());
    }

    @Test
    @DisplayName("marcarFalha registra FAILED com mensagem sanitizada no agendamento existente")
    void marcarFalha_recordsFailureOnExistingAgendamento() {
        Agendamento existente = new Agendamento();
        existente.setId(7L);
        when(agendamentoRepository.findByIdAndClinicaId(7L, 1L)).thenReturn(Optional.of(existente));
        when(agendamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.marcarFalha(clinica, 7L, "falha sanitizada");

        verify(agendamentoRepository).save(existente);
        assertThat(existente.getSyncStatus()).isEqualTo("FAILED");
        assertThat(existente.getSyncMensagemErro()).isEqualTo("falha sanitizada");
    }

    @Test
    @DisplayName("marcarFalha com id nulo nao cria nem persiste nada")
    void marcarFalha_noOpWhenIdIsNull() {
        service.marcarFalha(clinica, null, "falha sanitizada");

        verify(agendamentoRepository, never()).findByIdAndClinicaId(any(), any());
        verify(agendamentoRepository, never()).save(any());
    }

    @Test
    @DisplayName("sincronizarAgendamentosDoPaciente faz upsert de todos os agendamentos retornados")
    void sincronizarAgendamentosDoPaciente_upsertsAllSchedules() {
        when(agendamentoRepository.findByClinicaIdAndExternalSourceAndExternalId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(agendamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        DarwinPatientScheduleResponse resposta = new DarwinPatientScheduleResponse(
                null, List.of(), List.of(schedule("sch-1"), schedule("sch-2")));

        List<Agendamento> resultado = service.sincronizarAgendamentosDoPaciente(clinica, paciente, resposta);

        assertThat(resultado).hasSize(2);
    }

    @Test
    @DisplayName("sincronizarAgendamentosDoPaciente com resposta nula nao falha, retorna lista vazia")
    void sincronizarAgendamentosDoPaciente_handlesNullResponse() {
        List<Agendamento> resultado = service.sincronizarAgendamentosDoPaciente(clinica, paciente, null);

        assertThat(resultado).isEmpty();
    }
}
