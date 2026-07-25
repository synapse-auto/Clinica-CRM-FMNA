package com.synapse.clinicafemina.integration.external;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgendaProviderFactoryTest {

    @Test
    void should_return_provider_when_type_matches() {
        AgendaExternalProvider darwinProvider = mock(AgendaExternalProvider.class);
        AgendaExternalProvider medwareProvider = mock(AgendaExternalProvider.class);
        when(darwinProvider.providerType()).thenReturn(ExternalProviderType.DARWIN);
        when(medwareProvider.providerType()).thenReturn(ExternalProviderType.MEDWARE);

        AgendaProviderFactory factory = new AgendaProviderFactory(List.of(darwinProvider, medwareProvider));

        assertSame(darwinProvider, factory.getProvider(ExternalProviderType.DARWIN));
        assertSame(medwareProvider, factory.getProvider(ExternalProviderType.MEDWARE));
    }

    @Test
    void should_throw_when_provider_is_not_configured() {
        AgendaExternalProvider darwinProvider = mock(AgendaExternalProvider.class);
        when(darwinProvider.providerType()).thenReturn(ExternalProviderType.DARWIN);
        AgendaProviderFactory factory = new AgendaProviderFactory(List.of(darwinProvider));

        assertThrows(IllegalArgumentException.class, () -> factory.getProvider(ExternalProviderType.MEDWARE));
    }
}
