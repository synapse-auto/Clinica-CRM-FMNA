package com.synapse.clinicafemina.integration.whatsapp;

import com.synapse.clinicafemina.integration.whatsapp.config.WhatsappProperties;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Ponto central de resolução do {@link WhatsappProvider} ativo.
 *
 * <p>A seleção acontece exclusivamente aqui, a partir de {@code app.whatsapp.provider}
 * ({@link WhatsappProperties#resolveProvider()}). Services e controllers dependem do
 * provider resolvido — nunca de {@code if/else} por tipo.</p>
 */
@Component
public class WhatsappProviderResolver {

    private final Map<WhatsappProviderType, WhatsappProvider> providers;
    private final WhatsappProperties properties;

    public WhatsappProviderResolver(List<WhatsappProvider> providerList, WhatsappProperties properties) {
        this.providers = new EnumMap<>(WhatsappProviderType.class);
        for (WhatsappProvider provider : providerList) {
            this.providers.put(provider.getType(), provider);
        }
        this.properties = properties;
    }

    /** Resolve o provider configurado como ativo. */
    public WhatsappProvider resolve() {
        return resolve(properties.resolveProvider());
    }

    /** Resolve um provider específico, falhando com mensagem clara quando indisponível. */
    public WhatsappProvider resolve(WhatsappProviderType type) {
        WhatsappProvider provider = providers.get(type);
        if (provider == null) {
            throw new WhatsappProviderException(
                    "Provider de WhatsApp não disponível: " + type
                            + ". Providers registrados: " + providers.keySet());
        }
        return provider;
    }
}
