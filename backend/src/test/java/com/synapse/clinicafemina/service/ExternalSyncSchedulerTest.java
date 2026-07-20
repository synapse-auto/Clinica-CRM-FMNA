package com.synapse.clinicafemina.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.synapse.clinicafemina.config.ExternalSyncSchedulerProperties;
import com.synapse.clinicafemina.domain.Clinica;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExternalSyncSchedulerTest {

    @Mock
    private ClinicaConfigService clinicaConfigService;

    @Mock
    private ExternalSyncService externalSyncService;

    @Test
    void should_capture_one_window_in_configured_timezone_and_delegate_as_scheduled() {
        ExternalSyncSchedulerProperties properties = new ExternalSyncSchedulerProperties();
        properties.setEnabled(true);
        properties.setCron("0 0 * * * *");
        properties.setTimezone("America/Sao_Paulo");
        properties.setStartDaysBack(30);
        properties.setEndDaysForward(60);

        Clinica clinica = new Clinica();
        clinica.setId(41L);
        clinica.setExternalProvider(com.synapse.clinicafemina.integration.external.ExternalProviderType.DARWIN);
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica);
        when(externalSyncService.sincronizar(
                eq(clinica), eq(LocalDate.of(2026, 6, 20)), eq(LocalDate.of(2026, 9, 18)),
                eq(ExternalSyncOrigin.AGENDADA)))
                .thenReturn(new ExternalSyncResult(0, 0, 0, 0, 0, 0, 0, "SUCESSO"));
        ExternalSyncScheduler scheduler = new ExternalSyncScheduler(
                properties,
                clinicaConfigService,
                externalSyncService,
                Clock.fixed(
                        Instant.parse("2026-07-20T03:00:00Z"),
                        ZoneId.of("America/Sao_Paulo")));

        scheduler.sincronizarClinicaConfigurada();

        verify(externalSyncService).sincronizar(
                clinica,
                LocalDate.of(2026, 6, 20),
                LocalDate.of(2026, 9, 18),
                ExternalSyncOrigin.AGENDADA);
    }
}
