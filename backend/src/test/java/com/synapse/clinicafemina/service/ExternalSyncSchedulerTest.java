package com.synapse.clinicafemina.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.synapse.clinicafemina.config.ExternalSyncSchedulerProperties;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.integration.external.ExternalClinicProvider;
import com.synapse.clinicafemina.integration.external.ExternalProviderFactory;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
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

    @Mock
    private ExternalProviderFactory providerFactory;

    @Mock
    private ExternalClinicProvider provider;

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
        clinica.setExternalProvider(ExternalProviderType.MEDWARE);
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica);
        when(providerFactory.getProvider(ExternalProviderType.MEDWARE)).thenReturn(provider);
        when(provider.supportsBulkSync()).thenReturn(true);
        when(externalSyncService.sincronizar(
                eq(clinica), eq(LocalDate.of(2026, 6, 20)), eq(LocalDate.of(2026, 9, 18)),
                eq(ExternalSyncOrigin.AGENDADA)))
                .thenReturn(new ExternalSyncResult(0, 0, 0, 0, 0, 0, 0, "SUCESSO"));
        ExternalSyncScheduler scheduler = new ExternalSyncScheduler(
                properties,
                clinicaConfigService,
                externalSyncService,
                providerFactory,
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

    @Test
    void should_skip_darwin_clinic_without_calling_sincronizar_when_bulk_sync_not_supported() {
        ExternalSyncSchedulerProperties properties = new ExternalSyncSchedulerProperties();
        properties.setEnabled(true);
        properties.setCron("0 0 * * * *");
        properties.setTimezone("America/Sao_Paulo");
        properties.setStartDaysBack(30);
        properties.setEndDaysForward(60);

        Clinica clinica = new Clinica();
        clinica.setId(41L);
        clinica.setExternalProvider(ExternalProviderType.DARWIN);
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica);
        when(providerFactory.getProvider(ExternalProviderType.DARWIN)).thenReturn(provider);
        when(provider.supportsBulkSync()).thenReturn(false);
        ExternalSyncScheduler scheduler = new ExternalSyncScheduler(
                properties,
                clinicaConfigService,
                externalSyncService,
                providerFactory,
                Clock.fixed(
                        Instant.parse("2026-07-20T03:00:00Z"),
                        ZoneId.of("America/Sao_Paulo")));

        scheduler.sincronizarClinicaConfigurada();

        verify(externalSyncService, never()).sincronizar(any(), any(), any(), any());
    }
}
