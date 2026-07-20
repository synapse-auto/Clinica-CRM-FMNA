package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.config.ExternalSyncSchedulerProperties;
import com.synapse.clinicafemina.domain.Clinica;
import java.time.Clock;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "app.external-sync.scheduler",
        name = "enabled",
        havingValue = "true"
)
public class ExternalSyncScheduler {

    private final ExternalSyncSchedulerProperties properties;
    private final ClinicaConfigService clinicaConfigService;
    private final ExternalSyncService externalSyncService;
    private final Clock clock;

    public ExternalSyncScheduler(
            ExternalSyncSchedulerProperties properties,
            ClinicaConfigService clinicaConfigService,
            ExternalSyncService externalSyncService
    ) {
        this(properties, clinicaConfigService, externalSyncService, Clock.system(properties.zoneId()));
    }

    ExternalSyncScheduler(
            ExternalSyncSchedulerProperties properties,
            ClinicaConfigService clinicaConfigService,
            ExternalSyncService externalSyncService,
            Clock clock
    ) {
        this.properties = properties;
        this.clinicaConfigService = clinicaConfigService;
        this.externalSyncService = externalSyncService;
        this.clock = clock;
    }

    @Scheduled(
            cron = "${app.external-sync.scheduler.cron}",
            zone = "${app.external-sync.scheduler.timezone:America/Sao_Paulo}"
    )
    public void sincronizarClinicaConfigurada() {
        try {
            Clinica clinica = clinicaConfigService.obterClinicaAtual();
            LocalDate hoje = LocalDate.now(clock);
            LocalDate dataInicio = hoje.minusDays(properties.getStartDaysBack());
            LocalDate dataFim = hoje.plusDays(properties.getEndDaysForward());
            log.info(
                    "Sincronizacao externa agendada iniciada: clinica={}, provider={}, dataInicio={}, dataFim={}",
                    clinica.getId(), clinica.getExternalProvider(), dataInicio, dataFim);
            ExternalSyncResult result = externalSyncService.sincronizar(
                    clinica, dataInicio, dataFim, ExternalSyncOrigin.AGENDADA);
            log.info(
                    "Sincronizacao externa agendada finalizada: clinica={}, provider={}, status={}, "
                            + "pacientesProcessados={}, agendamentosProcessados={}",
                    clinica.getId(), clinica.getExternalProvider(), result.status(),
                    result.pacientesProcessados(), result.agendamentosProcessados());
        } catch (Exception error) {
            log.error(
                    "Falha segura ao iniciar sincronizacao externa agendada: tipoErro={}",
                    error.getClass().getSimpleName());
        }
    }
}
