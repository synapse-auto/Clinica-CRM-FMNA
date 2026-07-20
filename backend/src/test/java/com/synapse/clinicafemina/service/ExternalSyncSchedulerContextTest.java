package com.synapse.clinicafemina.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;

@SpringBootTest(properties = {
        "app.external-sync.scheduler.enabled=true",
        "app.external-sync.scheduler.cron=0 0/10 * * * ?",
        "app.external-sync.scheduler.timezone=America/Sao_Paulo"
})
class ExternalSyncSchedulerContextTest {

    @Autowired
    private ApplicationContext applicationContext;

    @MockBean
    private ExternalSyncService externalSyncService;

    @Test
    void should_create_scheduler_when_enabled_without_running_sync() {
        assertThat(applicationContext.getBean(ExternalSyncScheduler.class)).isNotNull();
        verifyNoInteractions(externalSyncService);
    }
}
