package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Gestor;
import com.synapse.clinicafemina.dto.atendimento.EncerramentoEmMassaRequest;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AtendimentoEncerramentoEmMassaServiceTest {

    @Mock
    private AtendimentoRepository atendimentoRepository;

    @Test
    void should_count_only_active_attendances_for_current_clinic() {
        AtendimentoEncerramentoEmMassaService service = service("");
        when(atendimentoRepository.countByClinicaIdAndStatus(1L, "ATIVO")).thenReturn(37L);

        var result = service.contarAtivos(1L);

        assertEquals(37L, result.total());
        verify(atendimentoRepository).countByClinicaIdAndStatus(1L, "ATIVO");
    }

    @Test
    void should_close_only_current_clinic_active_attendances() {
        AtendimentoEncerramentoEmMassaService service = service("");
        when(atendimentoRepository.encerrarTodosAtivos(eq(1L), org.mockito.ArgumentMatchers.any(), eq("Encerramento em massa pelo CRM")))
                .thenReturn(37);

        var result = service.encerrarTodos(1L, gestor(), new EncerramentoEmMassaRequest(true, null));

        assertEquals(37, result.encerrados());
        ArgumentCaptor<OffsetDateTime> data = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(atendimentoRepository).encerrarTodosAtivos(eq(1L), data.capture(), eq("Encerramento em massa pelo CRM"));
        assertEquals(result.dataEncerramento(), data.getValue());
    }

    @Test
    void should_return_zero_when_no_active_attendance_remains() {
        AtendimentoEncerramentoEmMassaService service = service("");
        when(atendimentoRepository.encerrarTodosAtivos(eq(1L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(0);

        var result = service.encerrarTodos(1L, gestor(), new EncerramentoEmMassaRequest(true, "Fim <b>manual</b>"));

        assertEquals(0, result.encerrados());
        verify(atendimentoRepository).encerrarTodosAtivos(eq(1L), org.mockito.ArgumentMatchers.any(), eq("Fim manual"));
    }

    @Test
    void should_reject_bulk_closure_without_boolean_confirmation() {
        AtendimentoEncerramentoEmMassaService service = service("");

        assertThrows(BadRequestException.class,
                () -> service.encerrarTodos(1L, gestor(), new EncerramentoEmMassaRequest(false, null)));

        verify(atendimentoRepository, never()).encerrarTodosAtivos(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void should_reject_bulk_closure_without_boolean_confirmation_when_absent() {
        AtendimentoEncerramentoEmMassaService service = service("");

        assertThrows(BadRequestException.class,
                () -> service.encerrarTodos(1L, gestor(), new EncerramentoEmMassaRequest(null, null)));

        verify(atendimentoRepository, never()).encerrarTodosAtivos(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void should_block_configured_user_from_bulk_closure_before_repository_update() {
        AtendimentoEncerramentoEmMassaService service = service("7, 12");

        assertThrows(AccessDeniedException.class,
                () -> service.encerrarTodos(1L, gestor(7L), new EncerramentoEmMassaRequest(true, null)));
        assertThrows(AccessDeniedException.class,
                () -> service.encerrarTodos(1L, gestor(12L), new EncerramentoEmMassaRequest(true, null)));

        verify(atendimentoRepository, never()).encerrarTodosAtivos(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void should_allow_unblocked_manager_and_expose_matching_capability() {
        AtendimentoEncerramentoEmMassaService service = service("7, 12");
        Gestor gestor = gestor();
        gestor.setId(13L);

        assertTrue(service.podeExecutar(gestor));
    }

    @Test
    void should_hide_bulk_capability_for_blocked_manager() {
        AtendimentoEncerramentoEmMassaService service = service("7,12");
        assertFalse(service.podeExecutar(gestor(7L)));
    }

    private AtendimentoEncerramentoEmMassaService service(String blockedUserIds) {
        return new AtendimentoEncerramentoEmMassaService(atendimentoRepository, blockedUserIds);
    }

    private Gestor gestor() {
        return gestor(12L);
    }

    private Gestor gestor(Long id) {
        Gestor gestor = new Gestor();
        gestor.setId(id);
        gestor.setPerfil("GESTOR");
        return gestor;
    }
}
