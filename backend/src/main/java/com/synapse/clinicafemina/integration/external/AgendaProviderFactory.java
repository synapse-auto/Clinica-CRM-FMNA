package com.synapse.clinicafemina.integration.external;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class AgendaProviderFactory {

    private final Map<ExternalProviderType, AgendaExternalProvider> providers;

    public AgendaProviderFactory(List<AgendaExternalProvider> providers) {
        this.providers = new EnumMap<>(ExternalProviderType.class);
        for (AgendaExternalProvider provider : providers) {
            this.providers.put(provider.providerType(), provider);
        }
    }

    public AgendaExternalProvider getProvider(ExternalProviderType type) {
        AgendaExternalProvider provider = providers.get(type);
        if (provider == null) {
            throw new IllegalArgumentException("Provider de agenda nao configurado: " + type);
        }
        return provider;
    }
}
