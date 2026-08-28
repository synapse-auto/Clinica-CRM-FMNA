package com.synapse.clinicafemina.integration.whatsapp.config;

import com.synapse.clinicafemina.integration.whatsapp.WhatsappProviderType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Configuração tipada da camada de WhatsApp (prefixo {@code app.whatsapp}).
 *
 * <p>Vincula apenas {@code enabled}, {@code provider} e o bloco {@code uazap}. As credenciais
 * Meta permanecem em propriedades planas ({@code access-token}, {@code app-secret}, ...) lidas
 * via {@code @Value} nos componentes Meta existentes — <strong>não são tocadas aqui</strong>
 * ({@code ignoreUnknownFields=true} por padrão).</p>
 *
 * <p>É independente do provider clínico ({@code EXTERNAL_PROVIDER}); ver
 * {@link WhatsappProviderType}.</p>
 */
@Component
@ConfigurationProperties(prefix = "app.whatsapp")
public class WhatsappProperties {

    /** Espelha {@code WHATSAPP_ENABLED}. Mantém o WhatsApp desligado por padrão. */
    private boolean enabled = false;

    /** Provider de WhatsApp ativo ({@code WHATSAPP_PROVIDER}). Padrão META preserva o comportamento atual. */
    private String provider = WhatsappProviderType.META.name();

    private final Uazap uazap = new Uazap();

    /**
     * Resolve o provider configurado para o enum, com mensagem clara em caso de valor inválido.
     */
    public WhatsappProviderType resolveProvider() {
        String raw = provider == null ? "" : provider.trim();
        try {
            return WhatsappProviderType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException(
                    "WHATSAPP_PROVIDER inválido: '" + provider + "'. Valores aceitos: META, UAZAP");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Uazap getUazap() {
        return uazap;
    }

    /**
     * Bloco de configuração isolado do provider UAZAP. Nunca reaproveita variáveis {@code META_WHATSAPP_*}.
     */
    public static class Uazap {
        private String baseUrl;
        private String username;
        private String version;
        private String phoneNumberId;
        private String token;
        /** Opcional: segredo verificado na URL do webhook, se o painel UAZAP permitir configurá-lo. */
        private String webhookSecret;
        private int connectTimeoutMs = 5000;
        private int readTimeoutMs = 15000;
        /** Limite defensivo do binário de mídia inbound baixado pela UAZAP (25 MiB por padrão). */
        private int mediaMaxBytes = 25 * 1024 * 1024;
        /** Hosts HTTPS adicionais, separados por vírgula, autorizados a servir mídia inbound. */
        private String mediaAllowedHosts = "lookaside.fbsbx.com";
        /** {@code UAZAP_PICTURE_ENRICHMENT_ENABLED} - fluxo automatico habilitado por padrao. */
        private boolean pictureEnrichmentEnabled = true;
        /** {@code UAZAP_PICTURE_DIAGNOSTICS_ENABLED} — desabilitado por padrão. Ver {@code UazapPictureDiagnosticoController}. */
        private boolean pictureDiagnosticsEnabled = false;
        /** Hosts HTTPS adicionais, separados por virgula, autorizados a servir fotos. */
        private String pictureAllowedHosts = "";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getPhoneNumberId() {
            return phoneNumberId;
        }

        public void setPhoneNumberId(String phoneNumberId) {
            this.phoneNumberId = phoneNumberId;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getWebhookSecret() {
            return webhookSecret;
        }

        public void setWebhookSecret(String webhookSecret) {
            this.webhookSecret = webhookSecret;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }

        public int getMediaMaxBytes() {
            return mediaMaxBytes;
        }

        public void setMediaMaxBytes(int mediaMaxBytes) {
            this.mediaMaxBytes = mediaMaxBytes;
        }

        public String getMediaAllowedHosts() {
            return mediaAllowedHosts;
        }

        public void setMediaAllowedHosts(String mediaAllowedHosts) {
            this.mediaAllowedHosts = mediaAllowedHosts;
        }

        public boolean isPictureEnrichmentEnabled() {
            return pictureEnrichmentEnabled;
        }

        public void setPictureEnrichmentEnabled(boolean pictureEnrichmentEnabled) {
            this.pictureEnrichmentEnabled = pictureEnrichmentEnabled;
        }

        public boolean isPictureDiagnosticsEnabled() {
            return pictureDiagnosticsEnabled;
        }

        public void setPictureDiagnosticsEnabled(boolean pictureDiagnosticsEnabled) {
            this.pictureDiagnosticsEnabled = pictureDiagnosticsEnabled;
        }

        public String getPictureAllowedHosts() {
            return pictureAllowedHosts;
        }

        public void setPictureAllowedHosts(String pictureAllowedHosts) {
            this.pictureAllowedHosts = pictureAllowedHosts;
        }
    }
}
