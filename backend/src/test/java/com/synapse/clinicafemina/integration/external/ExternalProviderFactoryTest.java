package com.synapse.clinicafemina.integration.external;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExternalProviderFactoryTest {

    @Test
    void should_return_provider_when_type_matches() {
        ExternalClinicProvider darwinProvider = mock(ExternalClinicProvider.class);
        ExternalClinicProvider medwareProvider = mock(ExternalClinicProvider.class);
        when(darwinProvider.getType()).thenReturn(ExternalProviderType.DARWIN);
        when(medwareProvider.getType()).thenReturn(ExternalProviderType.MEDWARE);

        ExternalProviderFactory factory = new ExternalProviderFactory(List.of(darwinProvider, medwareProvider));

        assertSame(darwinProvider, factory.getProvider(ExternalProviderType.DARWIN));
        assertSame(medwareProvider, factory.getProvider(ExternalProviderType.MEDWARE));
    }

    @Test
    void should_throw_when_provider_is_not_configured() {
        ExternalClinicProvider darwinProvider = mock(ExternalClinicProvider.class);
        when(darwinProvider.getType()).thenReturn(ExternalProviderType.DARWIN);
        ExternalProviderFactory factory = new ExternalProviderFactory(List.of(darwinProvider));

        assertThrows(IllegalArgumentException.class, () -> factory.getProvider(ExternalProviderType.MEDWARE));
    }
}
