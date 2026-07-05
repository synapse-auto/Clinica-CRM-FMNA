package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.HorarioAtendente;
import com.synapse.clinicafemina.domain.Recepcionista;
import com.synapse.clinicafemina.repository.HorarioAtendenteRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HorarioIaServiceTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    @Test
    void should_report_inside_configured_schedule() {
        HorarioAtendenteRepository repository = mock(HorarioAtendenteRepository.class);
        HorarioIaService service = new HorarioIaService(
                repository,
                Clock.fixed(Instant.parse("2026-07-06T13:00:00Z"), SAO_PAULO)
        );
        Clinica clinica = clinica();
        when(repository.findAtivosByClinicaId(7L)).thenReturn(List.of(horario(1, 8, 18, true)));

        HorarioIaService.HorarioIaStatus status = service.avaliar(clinica);

        assertTrue(status.dentroHorario());
        assertEquals("DENTRO_HORARIO", status.motivo());
    }

    @Test
    void should_report_outside_configured_schedule() {
        HorarioAtendenteRepository repository = mock(HorarioAtendenteRepository.class);
        HorarioIaService service = new HorarioIaService(
                repository,
                Clock.fixed(Instant.parse("2026-07-06T22:00:00Z"), SAO_PAULO)
        );
        Clinica clinica = clinica();
        when(repository.findAtivosByClinicaId(7L)).thenReturn(List.of(horario(1, 8, 18, true)));

        HorarioIaService.HorarioIaStatus status = service.avaliar(clinica);

        assertFalse(status.dentroHorario());
        assertEquals("FORA_HORARIO", status.motivo());
    }

    @Test
    void should_allow_ai_when_schedule_is_not_configured() {
        HorarioAtendenteRepository repository = mock(HorarioAtendenteRepository.class);
        HorarioIaService service = new HorarioIaService(
                repository,
                Clock.fixed(Instant.parse("2026-07-06T22:00:00Z"), SAO_PAULO)
        );
        Clinica clinica = clinica();
        when(repository.findAtivosByClinicaId(7L)).thenReturn(List.of());

        HorarioIaService.HorarioIaStatus status = service.avaliar(clinica);

        assertTrue(status.dentroHorario());
        assertEquals("SEM_CONFIGURACAO", status.motivo());
    }

    private Clinica clinica() {
        Clinica clinica = new Clinica();
        clinica.setId(7L);
        return clinica;
    }

    private HorarioAtendente horario(int diaSemana, int horaInicio, int horaFim, boolean ativo) {
        Clinica clinica = clinica();
        Recepcionista usuario = new Recepcionista();
        usuario.setId(3L);
        usuario.setClinica(clinica);
        HorarioAtendente horario = new HorarioAtendente();
        horario.setUsuario(usuario);
        horario.setDiaSemana((short) diaSemana);
        horario.setHoraInicio(LocalTime.of(horaInicio, 0));
        horario.setHoraFim(LocalTime.of(horaFim, 0));
        horario.setAtivo(ativo);
        return horario;
    }
}
