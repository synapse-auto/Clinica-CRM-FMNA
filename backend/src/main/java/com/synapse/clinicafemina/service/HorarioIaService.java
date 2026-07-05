package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.HorarioAtendente;
import com.synapse.clinicafemina.repository.HorarioAtendenteRepository;
import java.time.Clock;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class HorarioIaService {

    public static final String DENTRO_HORARIO = "DENTRO_HORARIO";
    public static final String FORA_HORARIO = "FORA_HORARIO";
    public static final String SEM_CONFIGURACAO = "SEM_CONFIGURACAO";
    public static final String HUMANO = "HUMANO";

    private static final ZoneId ZONE_ID = ZoneId.of("America/Sao_Paulo");

    private final HorarioAtendenteRepository repository;
    private final Clock clock;

    @Autowired
    public HorarioIaService(HorarioAtendenteRepository repository) {
        this(repository, Clock.system(ZONE_ID));
    }

    HorarioIaService(HorarioAtendenteRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public HorarioIaStatus avaliar(Clinica clinica) {
        return avaliar(clinica, OffsetDateTime.now(clock));
    }

    public HorarioIaStatus avaliar(Clinica clinica, OffsetDateTime agora) {
        if (clinica == null || clinica.getId() == null) {
            return new HorarioIaStatus(true, SEM_CONFIGURACAO);
        }

        List<HorarioAtendente> horarios = repository.findAtivosByClinicaId(clinica.getId());
        if (horarios.isEmpty()) {
            return new HorarioIaStatus(true, SEM_CONFIGURACAO);
        }

        ZonedDateTime local = agora.atZoneSameInstant(ZONE_ID);
        short diaSemana = (short) (local.getDayOfWeek().getValue() % 7);
        LocalTime hora = local.toLocalTime();
        boolean dentro = horarios.stream().anyMatch(horario -> horario.getDiaSemana() != null
                && horario.getDiaSemana() == diaSemana
                && dentroDaJanela(hora, horario.getHoraInicio(), horario.getHoraFim()));

        return dentro
                ? new HorarioIaStatus(true, DENTRO_HORARIO)
                : new HorarioIaStatus(false, FORA_HORARIO);
    }

    private boolean dentroDaJanela(LocalTime hora, LocalTime inicio, LocalTime fim) {
        if (hora == null || inicio == null || fim == null) {
            return false;
        }
        if (fim.equals(inicio)) {
            return true;
        }
        if (fim.isAfter(inicio)) {
            return !hora.isBefore(inicio) && hora.isBefore(fim);
        }
        return !hora.isBefore(inicio) || hora.isBefore(fim);
    }

    public record HorarioIaStatus(boolean dentroHorario, String motivo) {
    }
}
