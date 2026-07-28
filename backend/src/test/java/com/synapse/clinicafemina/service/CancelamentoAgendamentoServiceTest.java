package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Agendamento;
import com.synapse.clinicafemina.domain.CancelamentoAgendamento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.dto.cancelamento.CancelarAgendamentoN8nRequest;
import com.synapse.clinicafemina.integration.external.AgendaProviderFactory;
import com.synapse.clinicafemina.integration.external.AgendaExternalProvider;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import com.synapse.clinicafemina.exception.DarwinIntegrationException;
import com.synapse.clinicafemina.repository.AgendamentoRepository;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.CancelamentoAgendamentoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CancelamentoAgendamentoServiceTest {
    @Mock private CancelamentoAgendamentoRepository repository;
    @Mock private AgendamentoRepository agendamentoRepository;
    @Mock private AtendimentoRepository atendimentoRepository;
    @Mock private AgendaProviderFactory providerFactory;
    @Mock private AgendamentoService agendamentoService;

    @Test
    void should_return_existing_record_for_same_idempotency_key_and_payload() {
        Clinica clinica = new Clinica(); clinica.setId(1L);
        Paciente paciente = new Paciente(); paciente.setId(20L); paciente.setNome("Paciente de teste"); paciente.setTelefone("11999999999");
        Agendamento agendamento = new Agendamento(); agendamento.setId(10L); agendamento.setPaciente(paciente);
        CancelamentoAgendamento existing = new CancelamentoAgendamento();
        existing.setId(50L); existing.setAgendamento(agendamento); existing.setPaciente(paciente);
        existing.setMotivo("Paciente nao podera comparecer"); existing.setOrigem("LEMBRETE_NEGADO");
        existing.setStatusCancelamento("CANCELADO"); existing.setStatusSincronizacao("NAO_APLICAVEL");
        when(repository.findByClinicaIdAndIdempotencyKey(1L, "evt-1")).thenReturn(Optional.of(existing));
        CancelamentoAgendamentoService service = new CancelamentoAgendamentoService(repository, agendamentoRepository,
                atendimentoRepository, providerFactory, agendamentoService);

        var result = service.cancelarPorN8n(clinica, "evt-1", new CancelarAgendamentoN8nRequest(
                10L, null, "Paciente nao podera comparecer", "LEMBRETE_NEGADO"));

        assertFalse(result.criado());
        verifyNoInteractions(providerFactory, agendamentoService);
    }

    @Test
    void should_register_manual_cancellation_once_per_appointment() {
        Clinica clinica = new Clinica(); clinica.setId(1L);
        Paciente paciente = new Paciente(); paciente.setId(20L); paciente.setNome("Paciente de teste");
        Agendamento agendamento = new Agendamento(); agendamento.setId(10L); agendamento.setPaciente(paciente);
        when(agendamentoRepository.findByIdAndClinicaId(10L, 1L)).thenReturn(Optional.of(agendamento));
        when(repository.existsByClinicaIdAndAgendamentoIdAndOrigem(1L, 10L, "CRM_MANUAL")).thenReturn(false);
        CancelamentoAgendamentoService service = new CancelamentoAgendamentoService(repository, agendamentoRepository,
                atendimentoRepository, providerFactory, agendamentoService);

        service.registrarCancelamentoManual(clinica, 10L, "Cancelado no CRM");

        verify(repository).save(any(CancelamentoAgendamento.class));
        assertTrue(true);
    }

    @Test
    void should_cancel_local_appointment_without_calling_darwin() {
        Clinica clinica = new Clinica(); clinica.setId(1L); clinica.setExternalProvider(ExternalProviderType.DARWIN);
        Paciente paciente = new Paciente(); paciente.setId(20L); paciente.setNome("Paciente de teste");
        Agendamento agendamento = new Agendamento(); agendamento.setId(10L); agendamento.setPaciente(paciente);
        agendamento.setStatus("AGENDADO"); agendamento.setExternalSource(null); agendamento.setExternalId("local-10");
        when(repository.findByClinicaIdAndIdempotencyKey(1L, "evt-local")).thenReturn(Optional.empty());
        when(agendamentoRepository.findByIdAndClinicaId(10L, 1L)).thenReturn(Optional.of(agendamento));
        when(repository.save(any(CancelamentoAgendamento.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CancelamentoAgendamentoService service = new CancelamentoAgendamentoService(repository, agendamentoRepository,
                atendimentoRepository, providerFactory, agendamentoService);

        var result = service.cancelarPorN8n(clinica, "evt-local", new CancelarAgendamentoN8nRequest(
                10L, null, "Paciente nao podera comparecer", "N8N"));

        assertTrue(result.criado());
        org.junit.jupiter.api.Assertions.assertEquals("CANCELADO", result.response().statusCancelamento());
        org.junit.jupiter.api.Assertions.assertEquals("NAO_APLICAVEL", result.response().statusSincronizacao());
        verify(agendamentoService).cancelar(eq(clinica), eq(10L), any());
        verifyNoInteractions(providerFactory);
    }

    @Test
    void should_return_success_for_already_cancelled_appointment_without_external_call() {
        Clinica clinica = new Clinica(); clinica.setId(1L);
        Paciente paciente = new Paciente(); paciente.setId(20L); paciente.setNome("Paciente de teste");
        Agendamento agendamento = new Agendamento(); agendamento.setId(10L); agendamento.setPaciente(paciente);
        agendamento.setStatus("CANCELADO"); agendamento.setExternalSource(ExternalProviderType.DARWIN); agendamento.setExternalId("darwin-10");
        when(repository.findByClinicaIdAndIdempotencyKey(1L, "evt-cancelado")).thenReturn(Optional.empty());
        when(agendamentoRepository.findByIdAndClinicaId(10L, 1L)).thenReturn(Optional.of(agendamento));
        when(repository.save(any(CancelamentoAgendamento.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CancelamentoAgendamentoService service = new CancelamentoAgendamentoService(repository, agendamentoRepository,
                atendimentoRepository, providerFactory, agendamentoService);

        var result = service.cancelarPorN8n(clinica, "evt-cancelado", new CancelarAgendamentoN8nRequest(
                10L, null, "Paciente ja cancelou", "N8N"));

        org.junit.jupiter.api.Assertions.assertEquals("CANCELADO", result.response().statusCancelamento());
        org.junit.jupiter.api.Assertions.assertEquals("NAO_APLICAVEL", result.response().statusSincronizacao());
        verifyNoInteractions(providerFactory, agendamentoService);
    }

    @Test
    void should_mark_darwin_not_found_as_permanent_failure() {
        Clinica clinica = new Clinica(); clinica.setId(1L); clinica.setExternalProvider(ExternalProviderType.MEDWARE);
        Paciente paciente = new Paciente(); paciente.setId(20L); paciente.setNome("Paciente de teste");
        Agendamento agendamento = new Agendamento(); agendamento.setId(10L); agendamento.setPaciente(paciente);
        agendamento.setStatus("AGENDADO"); agendamento.setExternalSource(ExternalProviderType.DARWIN); agendamento.setExternalId("darwin-10");
        AgendaExternalProvider darwinProvider = mock(AgendaExternalProvider.class);
        when(repository.findByClinicaIdAndIdempotencyKey(1L, "evt-darwin")).thenReturn(Optional.empty());
        when(agendamentoRepository.findByIdAndClinicaId(10L, 1L)).thenReturn(Optional.of(agendamento));
        when(repository.save(any(CancelamentoAgendamento.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(providerFactory.getProvider(ExternalProviderType.DARWIN)).thenReturn(darwinProvider);
        doThrow(new DarwinIntegrationException(404, "Recurso nao encontrado na integracao Darwin."))
                .when(darwinProvider).cancelarAgendamento(clinica, 10L, "Paciente nao podera comparecer");
        CancelamentoAgendamentoService service = new CancelamentoAgendamentoService(repository, agendamentoRepository,
                atendimentoRepository, providerFactory, agendamentoService);

        var result = service.cancelarPorN8n(clinica, "evt-darwin", new CancelarAgendamentoN8nRequest(
                10L, null, "Paciente nao podera comparecer", "N8N"));

        org.junit.jupiter.api.Assertions.assertEquals("FALHA_CANCELAMENTO", result.response().statusCancelamento());
        org.junit.jupiter.api.Assertions.assertEquals("FALHA_PERMANENTE", result.response().statusSincronizacao());
        verify(providerFactory).getProvider(ExternalProviderType.DARWIN);
        verifyNoInteractions(agendamentoService);
    }

    @Test
    void should_not_cancel_locally_when_darwin_appointment_has_no_external_id() {
        Clinica clinica = new Clinica(); clinica.setId(1L);
        Paciente paciente = new Paciente(); paciente.setId(20L); paciente.setNome("Paciente de teste");
        Agendamento agendamento = new Agendamento(); agendamento.setId(10L); agendamento.setPaciente(paciente);
        agendamento.setStatus("AGENDADO"); agendamento.setExternalSource(ExternalProviderType.DARWIN);
        when(repository.findByClinicaIdAndIdempotencyKey(1L, "evt-darwin-sem-id")).thenReturn(Optional.empty());
        when(agendamentoRepository.findByIdAndClinicaId(10L, 1L)).thenReturn(Optional.of(agendamento));
        when(repository.save(any(CancelamentoAgendamento.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CancelamentoAgendamentoService service = new CancelamentoAgendamentoService(repository, agendamentoRepository,
                atendimentoRepository, providerFactory, agendamentoService);

        var result = service.cancelarPorN8n(clinica, "evt-darwin-sem-id", new CancelarAgendamentoN8nRequest(
                10L, null, "Paciente nao podera comparecer", "N8N"));

        org.junit.jupiter.api.Assertions.assertEquals("FALHA_CANCELAMENTO", result.response().statusCancelamento());
        org.junit.jupiter.api.Assertions.assertEquals("FALHA_PERMANENTE", result.response().statusSincronizacao());
        verifyNoInteractions(providerFactory, agendamentoService);
    }
}
