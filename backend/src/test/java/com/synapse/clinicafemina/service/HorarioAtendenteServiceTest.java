package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.HorarioAtendente;
import com.synapse.clinicafemina.domain.Recepcionista;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.operacional.HorarioAtendenteRequest;
import com.synapse.clinicafemina.dto.operacional.StatusRequest;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.repository.HorarioAtendenteRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HorarioAtendenteServiceTest {

    @Mock
    private HorarioAtendenteRepository repository;

    @Mock
    private UsuarioRepository usuarioRepository;

    private HorarioAtendenteService service;
    private Clinica clinica;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        service = new HorarioAtendenteService(repository, usuarioRepository);
        clinica = new Clinica();
        clinica.setId(7L);
        usuario = new Recepcionista();
        usuario.setId(3L);
        usuario.setClinica(clinica);
        usuario.setNome("Recepcao");
    }

    @Test
    void should_create_attendant_schedule_for_current_clinic_user() {
        HorarioAtendenteRequest request = new HorarioAtendenteRequest(
                3L,
                1,
                LocalTime.of(8, 0),
                LocalTime.of(12, 0),
                true
        );
        when(usuarioRepository.findAtivoByIdAndClinicaId(3L, 7L)).thenReturn(Optional.of(usuario));
        when(repository.save(any(HorarioAtendente.class))).thenAnswer(invocation -> {
            HorarioAtendente saved = invocation.getArgument(0);
            saved.setId(31L);
            return saved;
        });

        var response = service.criar(clinica, request);

        ArgumentCaptor<HorarioAtendente> captor = ArgumentCaptor.forClass(HorarioAtendente.class);
        verify(repository).save(captor.capture());
        HorarioAtendente saved = captor.getValue();
        assertEquals(usuario, saved.getUsuario());
        assertEquals(Short.valueOf((short) 1), saved.getDiaSemana());
        assertEquals(LocalTime.of(8, 0), saved.getHoraInicio());
        assertEquals(LocalTime.of(12, 0), saved.getHoraFim());
        assertEquals(true, saved.getAtivo());
        assertEquals(31L, response.id());
        assertEquals(1, response.diaSemana());
    }

    @Test
    void should_reject_schedule_when_start_is_after_end() {
        HorarioAtendenteRequest request = new HorarioAtendenteRequest(
                3L,
                1,
                LocalTime.of(18, 0),
                LocalTime.of(8, 0),
                true
        );

        assertThrows(BadRequestException.class, () -> service.criar(clinica, request));
        verify(repository, never()).save(any());
    }

    @Test
    void should_reject_schedule_when_week_day_is_out_of_range() {
        HorarioAtendenteRequest request = new HorarioAtendenteRequest(
                3L,
                7,
                LocalTime.of(8, 0),
                LocalTime.of(12, 0),
                true
        );

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.criar(clinica, request)
        );

        assertEquals("Dia da semana deve estar entre 0 e 6.", exception.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void should_soft_delete_attendant_schedule_by_clinic() {
        HorarioAtendente horario = horario(41L);
        when(repository.findByIdAndUsuarioClinicaIdAndDeletadoEmIsNull(41L, 7L)).thenReturn(Optional.of(horario));

        service.excluir(clinica, 41L);

        assertNotNull(horario.getDeletadoEm());
    }

    @Test
    void should_update_schedule_status_by_clinic() {
        HorarioAtendente horario = horario(42L);
        horario.setAtivo(true);
        when(repository.findByIdAndUsuarioClinicaIdAndDeletadoEmIsNull(42L, 7L)).thenReturn(Optional.of(horario));
        when(repository.save(any(HorarioAtendente.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.alterarStatus(clinica, 42L, new StatusRequest(false));

        assertEquals(false, horario.getAtivo());
        assertEquals(false, response.ativo());
    }

    private HorarioAtendente horario(Long id) {
        HorarioAtendente horario = new HorarioAtendente();
        horario.setId(id);
        horario.setUsuario(usuario);
        horario.setDiaSemana(Short.valueOf((short) 1));
        horario.setHoraInicio(LocalTime.of(8, 0));
        horario.setHoraFim(LocalTime.of(12, 0));
        horario.setAtivo(true);
        return horario;
    }
}
