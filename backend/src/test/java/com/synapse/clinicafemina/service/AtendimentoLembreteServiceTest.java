package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.AtendimentoLembrete;
import com.synapse.clinicafemina.domain.AtendimentoLembreteStatus;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Gestor;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.dto.atendimento.AtendimentoLembreteRequest;
import com.synapse.clinicafemina.repository.AtendimentoLembreteRepository;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AtendimentoLembreteServiceTest {

    @Mock private AtendimentoLembreteRepository lembreteRepository;
    @Mock private AtendimentoRepository atendimentoRepository;
    @Mock private UsuarioRepository usuarioRepository;

    private AtendimentoLembreteService service;
    private Clinica clinica;
    private Atendimento atendimento;
    private Gestor usuario;

    @BeforeEach
    void setUp() {
        service = new AtendimentoLembreteService(
                lembreteRepository,
                atendimentoRepository,
                usuarioRepository
        );

        clinica = new Clinica();
        clinica.setId(1L);

        Paciente paciente = new Paciente();
        paciente.setId(2L);
        paciente.setClinica(clinica);

        atendimento = new Atendimento();
        atendimento.setId(3L);
        atendimento.setClinica(clinica);
        atendimento.setPaciente(paciente);
        atendimento.setStatus("ATIVO");
        atendimento.setTratadoPorIa(false);

        usuario = new Gestor();
        usuario.setId(10L);
        usuario.setClinica(clinica);
        usuario.setNome("Gestor");
    }

    @Test
    void should_create_internal_reminder_without_changing_ai_mode() {
        when(atendimentoRepository.findByIdAndClinicaId(3L, 1L)).thenReturn(Optional.of(atendimento));
        when(usuarioRepository.findAtivoByIdAndClinicaId(10L, 1L)).thenReturn(Optional.of(usuario));
        when(lembreteRepository.save(any())).thenAnswer(invocation -> {
            AtendimentoLembrete lembrete = invocation.getArgument(0);
            lembrete.setId(99L);
            return lembrete;
        });

        var result = service.criar(
                3L,
                new AtendimentoLembreteRequest(
                        LocalDate.parse("2026-07-10"),
                        LocalTime.parse("10:00"),
                        "Ligar para paciente e confirmar exame."
                ),
                1L,
                10L
        );

        assertEquals(99L, result.id());
        assertEquals("Ligar para paciente e confirmar exame.", result.mensagem());
        assertEquals(AtendimentoLembreteStatus.PENDENTE.name(), result.status());
        assertEquals(10, result.lembrarEm().getHour());
        assertFalse(atendimento.getTratadoPorIa());
        verify(lembreteRepository).save(any(AtendimentoLembrete.class));
    }

    @Test
    void should_list_reminders_inside_current_clinic() {
        AtendimentoLembrete lembrete = criarLembrete();
        when(atendimentoRepository.findByIdAndClinicaId(3L, 1L)).thenReturn(Optional.of(atendimento));
        when(lembreteRepository.findByAtendimentoIdAndClinicaIdOrderByStatusAscLembrarEmAsc(3L, 1L))
                .thenReturn(List.of(lembrete));

        var result = service.listar(3L, 1L);

        assertEquals(1, result.size());
        assertEquals("Conferir autorizacao", result.getFirst().mensagem());
    }

    @Test
    void should_conclude_and_cancel_pending_reminder() {
        AtendimentoLembrete lembrete = criarLembrete();
        when(lembreteRepository.findByIdAndAtendimentoIdAndClinicaId(7L, 3L, 1L))
                .thenReturn(Optional.of(lembrete));
        when(lembreteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var concluido = service.concluir(3L, 7L, 1L);
        assertEquals(AtendimentoLembreteStatus.CONCLUIDO.name(), concluido.status());

        lembrete.setStatus(AtendimentoLembreteStatus.PENDENTE);
        var cancelado = service.cancelar(3L, 7L, 1L);
        assertEquals(AtendimentoLembreteStatus.CANCELADO.name(), cancelado.status());
    }

    @Test
    void should_reject_blank_message_and_missing_date_or_time() {
        assertThrows(IllegalArgumentException.class, () -> service.criar(
                3L,
                new AtendimentoLembreteRequest(LocalDate.parse("2026-07-10"), LocalTime.parse("10:00"), " "),
                1L,
                10L
        ));
        assertThrows(IllegalArgumentException.class, () -> service.criar(
                3L,
                new AtendimentoLembreteRequest(null, LocalTime.parse("10:00"), "Ligar"),
                1L,
                10L
        ));
        assertThrows(IllegalArgumentException.class, () -> service.criar(
                3L,
                new AtendimentoLembreteRequest(LocalDate.parse("2026-07-10"), null, "Ligar"),
                1L,
                10L
        ));

        verify(lembreteRepository, never()).save(any());
    }

    private AtendimentoLembrete criarLembrete() {
        AtendimentoLembrete lembrete = new AtendimentoLembrete();
        lembrete.setId(7L);
        lembrete.setClinica(clinica);
        lembrete.setAtendimento(atendimento);
        lembrete.setMensagem("Conferir autorizacao");
        lembrete.setLembrarEm(OffsetDateTime.parse("2026-07-10T10:00:00Z"));
        lembrete.setStatus(AtendimentoLembreteStatus.PENDENTE);
        lembrete.setCriadoPor(usuario);
        return lembrete;
    }
}
