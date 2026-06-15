package com.synapse.clinicafemina.integration.external;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ExternalProviderFactory {

    private final Map<ExternalProviderType, ExternalClinicProvider> providers;

    public ExternalProviderFactory(List<ExternalClinicProvider> providers) {
        this.providers = new EnumMap<>(ExternalProviderType.class);
        for (ExternalClinicProvider provider : providers) {
            this.providers.put(provider.getType(), provider);
        }
    }

    public ExternalClinicProvider getProvider(ExternalProviderType type) {
        ExternalClinicProvider provider = providers.get(type);
        if (provider == null) {
            throw new IllegalArgumentException("Provider externo não configurado: " + type);
        }
        return provider;
    }
}
