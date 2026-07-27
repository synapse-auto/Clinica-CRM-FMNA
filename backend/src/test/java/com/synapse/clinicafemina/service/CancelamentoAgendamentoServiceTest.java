package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Agendamento;
import com.synapse.clinicafemina.domain.CancelamentoAgendamento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.dto.cancelamento.CancelarAgendamentoN8nRequest;
import com.synapse.clinicafemina.integration.external.AgendaProviderFactory;
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
}
