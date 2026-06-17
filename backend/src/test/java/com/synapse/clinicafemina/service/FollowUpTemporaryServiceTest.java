package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.FollowUpTemporary;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.dto.followup.FollowUpTemporaryRequest;
import com.synapse.clinicafemina.dto.followup.FollowUpTemporaryStatusRequest;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.repository.FollowUpConfigRepository;
import com.synapse.clinicafemina.repository.FollowUpTemporaryRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowUpTemporaryServiceTest {

    @Mock
    private FollowUpTemporaryRepository followUpTemporaryRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private FollowUpConfigRepository followUpConfigRepository;

    @Mock
    private N8nEventService n8nEventService;

    private FollowUpTemporaryService service;
    private Clinica clinica;
    private Paciente paciente;

    @BeforeEach
    void setUp() {
        service = new FollowUpTemporaryService(
                followUpTemporaryRepository,
                pacienteRepository,
                followUpConfigRepository,
                n8nEventService
        );

        clinica = new Clinica();
        clinica.setId(9L);
        clinica.setSlug("ultramedical");

        paciente = new Paciente();
        paciente.setId(20L);
        paciente.setClinica(clinica);
        paciente.setTelefoneNormalizado("5544999990000");
    }

    @Test
    void should_create_temporary_follow_up_for_patient_in_current_clinic() {
        OffsetDateTime scheduledAt = OffsetDateTime.parse("2026-06-20T13:30:00Z");
        FollowUpTemporaryRequest request = new FollowUpTemporaryRequest(
                null,
                "Reativar paciente",
                "Paciente sem retorno ha 90 dias",
                "MANUAL",
                null,
                scheduledAt,
                "{\"fonte\":\"teste\"}"
        );
        when(pacienteRepository.findByIdAndClinicaId(20L, 9L)).thenReturn(Optional.of(paciente));
        when(followUpTemporaryRepository.save(any(FollowUpTemporary.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(n8nEventService.criarPayload(clinica, "follow_up_criado", 20L, null, null, "5544999990000"))
                .thenReturn(new N8nEventPayload(9L, "ultramedical", null, "follow_up_criado", 20L, null, null,
                        "5544999990000", false, null));

        service.criar(clinica, 20L, request);

        ArgumentCaptor<FollowUpTemporary> captor = ArgumentCaptor.forClass(FollowUpTemporary.class);
        verify(followUpTemporaryRepository).save(captor.capture());
        FollowUpTemporary saved = captor.getValue();
        assertSame(clinica, saved.getClinica());
        assertSame(paciente, saved.getPaciente());
        assertEquals("Reativar paciente", saved.getTitulo());
        assertEquals("MANUAL", saved.getOrigem());
        assertEquals("PENDENTE", saved.getStatus());
        assertEquals(scheduledAt, saved.getScheduledAt());
        verify(n8nEventService).emitir(any(N8nEventPayload.class));
    }

    @Test
    void should_reject_temporary_follow_up_when_patient_is_not_in_current_clinic() {
        FollowUpTemporaryRequest request = new FollowUpTemporaryRequest(
                null,
                "Retorno",
                null,
                "MANUAL",
                null,
                OffsetDateTime.parse("2026-06-20T13:30:00Z"),
                null
        );
        when(pacienteRepository.findByIdAndClinicaId(20L, 9L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.criar(clinica, 20L, request));
    }

    @Test
    void should_require_scheduled_at_when_creating_temporary_follow_up() {
        FollowUpTemporaryRequest request = new FollowUpTemporaryRequest(
                null,
                "Retorno",
                null,
                "MANUAL",
                null,
                null,
                null
        );
        when(pacienteRepository.findByIdAndClinicaId(20L, 9L)).thenReturn(Optional.of(paciente));

        assertThrows(IllegalArgumentException.class, () -> service.criar(clinica, 20L, request));
    }

    @Test
    void should_list_temporary_follow_ups_by_patient_after_validating_clinic() {
        FollowUpTemporary followUp = new FollowUpTemporary();
        followUp.setClinica(clinica);
        followUp.setPaciente(paciente);
        followUp.setTitulo("Retorno");
        followUp.setStatus("PENDENTE");
        followUp.setScheduledAt(OffsetDateTime.parse("2026-06-20T13:30:00Z"));

        when(pacienteRepository.findByIdAndClinicaId(20L, 9L)).thenReturn(Optional.of(paciente));
        when(followUpTemporaryRepository.findByPaciente(9L, 20L, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(followUp)));

        var result = service.listarPorPaciente(clinica, 20L, PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void should_update_status_and_emit_cancel_event() {
        FollowUpTemporary followUp = new FollowUpTemporary();
        followUp.setId(40L);
        followUp.setClinica(clinica);
        followUp.setPaciente(paciente);
        followUp.setTitulo("Retorno");
        followUp.setStatus("PENDENTE");
        followUp.setScheduledAt(OffsetDateTime.parse("2026-06-20T13:30:00Z"));

        when(followUpTemporaryRepository.findByIdAndClinicaId(40L, 9L)).thenReturn(Optional.of(followUp));
        when(followUpTemporaryRepository.save(any(FollowUpTemporary.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(n8nEventService.criarPayload(clinica, "follow_up_cancelado", 20L, null, null, "5544999990000"))
                .thenReturn(new N8nEventPayload(9L, "ultramedical", null, "follow_up_cancelado", 20L, null, null,
                        "5544999990000", false, null));

        service.alterarStatus(clinica, 40L, new FollowUpTemporaryStatusRequest("CANCELADO", "Paciente reagendou"));

        assertEquals("CANCELADO", followUp.getStatus());
        assertEquals("Paciente reagendou", followUp.getCancelReason());
        verify(n8nEventService).emitir(any(N8nEventPayload.class));
    }
}
