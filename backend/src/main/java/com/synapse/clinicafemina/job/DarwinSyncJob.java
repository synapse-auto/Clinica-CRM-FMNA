package com.synapse.clinicafemina.job;

import com.synapse.clinicafemina.service.DarwinSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job de sincronização com o ERP Darwin.
 * Executa periodicamente com base na configuração do cron via application.yml.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DarwinSyncJob {

    private final DarwinSyncService darwinSyncService;

    // Configurado no application.yml: app.darwin.cron
    @Scheduled(cron = "${app.darwin.cron:0 0 * * * *}")
    public void execute() {
        log.info("Iniciando Job de sincronização com o Darwin");
        try {
            darwinSyncService.sync();
        } catch (Exception e) {
            log.error("Erro fatal não tratado na execução do Job Darwin: {}", e.getMessage(), e);
        }
    }
}
