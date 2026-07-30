package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.dto.atendimento.EncerramentoEmMassaRequest;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        AtendimentoEncerramentoEmMassaService service = new AtendimentoEncerramentoEmMassaService(atendimentoRepository);
        when(atendimentoRepository.countByClinicaIdAndStatus(1L, "ATIVO")).thenReturn(37L);

        var result = service.contarAtivos(1L);

        assertEquals(37L, result.total());
        verify(atendimentoRepository).countByClinicaIdAndStatus(1L, "ATIVO");
    }

    @Test
    void should_close_only_current_clinic_active_attendances() {
        AtendimentoEncerramentoEmMassaService service = new AtendimentoEncerramentoEmMassaService(atendimentoRepository);
        when(atendimentoRepository.encerrarTodosAtivos(eq(1L), org.mockito.ArgumentMatchers.any(), eq("Encerramento em massa pelo CRM")))
                .thenReturn(37);

        var result = service.encerrarTodos(1L, new EncerramentoEmMassaRequest(true, null));

        assertEquals(37, result.encerrados());
        ArgumentCaptor<OffsetDateTime> data = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(atendimentoRepository).encerrarTodosAtivos(eq(1L), data.capture(), eq("Encerramento em massa pelo CRM"));
        assertEquals(result.dataEncerramento(), data.getValue());
    }

    @Test
    void should_return_zero_when_no_active_attendance_remains() {
        AtendimentoEncerramentoEmMassaService service = new AtendimentoEncerramentoEmMassaService(atendimentoRepository);
        when(atendimentoRepository.encerrarTodosAtivos(eq(1L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(0);

        var result = service.encerrarTodos(1L, new EncerramentoEmMassaRequest(true, "Fim <b>manual</b>"));

        assertEquals(0, result.encerrados());
        verify(atendimentoRepository).encerrarTodosAtivos(eq(1L), org.mockito.ArgumentMatchers.any(), eq("Fim manual"));
    }

    @Test
    void should_reject_bulk_closure_without_confirmation() {
        AtendimentoEncerramentoEmMassaService service = new AtendimentoEncerramentoEmMassaService(atendimentoRepository);

        assertThrows(BadRequestException.class,
                () -> service.encerrarTodos(1L, new EncerramentoEmMassaRequest(false, null)));

        verify(atendimentoRepository, never()).encerrarTodosAtivos(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()
        );
    }
}
