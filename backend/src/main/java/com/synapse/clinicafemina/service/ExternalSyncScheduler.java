package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.config.ExternalSyncSchedulerProperties;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.integration.external.ExternalClinicProvider;
import com.synapse.clinicafemina.integration.external.ExternalProviderFactory;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final ExternalProviderFactory providerFactory;
    private final Clock clock;

    @Autowired
    public ExternalSyncScheduler(
            ExternalSyncSchedulerProperties properties,
            ClinicaConfigService clinicaConfigService,
            ExternalSyncService externalSyncService,
            ExternalProviderFactory providerFactory
    ) {
        this(properties, clinicaConfigService, externalSyncService, providerFactory,
                Clock.system(properties.zoneId()));
    }

    ExternalSyncScheduler(
            ExternalSyncSchedulerProperties properties,
            ClinicaConfigService clinicaConfigService,
            ExternalSyncService externalSyncService,
            ExternalProviderFactory providerFactory,
            Clock clock
    ) {
        this.properties = properties;
        this.clinicaConfigService = clinicaConfigService;
        this.externalSyncService = externalSyncService;
        this.providerFactory = providerFactory;
        this.clock = clock;
    }

    @Scheduled(
            cron = "${app.external-sync.scheduler.cron}",
            zone = "${app.external-sync.scheduler.timezone:America/Sao_Paulo}"
    )
    public void sincronizarClinicaConfigurada() {
        try {
            Clinica clinica = clinicaConfigService.obterClinicaAtual();
            ExternalProviderType providerType = clinica.getExternalProvider();
            ExternalClinicProvider provider = providerFactory.getProvider(providerType);
            if (!provider.supportsBulkSync()) {
                log.info(
                        "Sincronizacao externa agendada ignorada: provider={} nao suporta sincronizacao em "
                                + "massa (integracao sob demanda)",
                        providerType);
                return;
            }
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
