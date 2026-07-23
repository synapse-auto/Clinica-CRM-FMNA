package com.synapse.clinicafemina.integration.whatsapp.uazap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.clinicafemina.integration.whatsapp.WhatsappProviderType;
import com.synapse.clinicafemina.integration.whatsapp.config.WhatsappProperties;
import com.synapse.clinicafemina.service.WhatsappWebhookDispatchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Webhook inbound isolado da UAZAP: {@code POST /api/webhooks/whatsapp/uazap}.
 *
 * <p>Separado do endpoint Meta ({@code /api/webhooks/whatsapp}), que permanece inalterado.
 * Responde rápido e delega o processamento pesado ao {@link WhatsappWebhookDispatchService}
 * (RabbitMQ), fora da requisição HTTP.</p>
 *
 * <p><strong>Segurança do webhook (limitação documentada):</strong> a UAZAP não confirma
 * assinatura nativa. Nenhum header é inventado e o segredo NÃO é anexado automaticamente à URL.
 * Quando {@code UAZAP_WEBHOOK_SECRET} está configurado, ele é validado a partir do parâmetro
 * {@code ?secret=} com comparação de tempo constante — proteção FRACA, pois query strings vazam
 * em logs de servidor/proxy e histórico de URL. Enquanto o painel UAZAP não permitir header
 * dedicado, o segredo permanece opcional e a defesa principal é a validação estrutural do payload
 * e do {@code phone_number_id} esperado, com {@code WHATSAPP_ENABLED=false}.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks/whatsapp/uazap")
public class UazapWebhookController {

    /** Limite defensivo de tamanho do payload (evita processamento de corpos abusivos). */
    static final int MAX_PAYLOAD_BYTES = 512 * 1024;

    private final WhatsappProperties properties;
    private final WhatsappWebhookDispatchService dispatchService;
    private final ObjectMapper objectMapper;

    public UazapWebhookController(WhatsappProperties properties,
                                  WhatsappWebhookDispatchService dispatchService,
                                  ObjectMapper objectMapper) {
        this.properties = properties;
        this.dispatchService = dispatchService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<Void> receberWebhook(
            @RequestBody(required = false) byte[] rawBody,
            @RequestParam(value = "secret", required = false) String secret) {

        if (!properties.isEnabled() || properties.resolveProvider() != WhatsappProviderType.UAZAP) {
            log.warn("Webhook UAZAP inativo (WHATSAPP_ENABLED=false ou provider != UAZAP).");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        if (rawBody == null || rawBody.length == 0) {
            return ResponseEntity.badRequest().build();
        }
        if (rawBody.length > MAX_PAYLOAD_BYTES) {
            log.warn("Payload UAZAP excede o limite. tamanhoBytes={}", rawBody.length);
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }
        if (!segredoValido(secret)) {
            log.warn("Webhook UAZAP rejeitado: segredo ausente ou inválido.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!estruturaValida(rawBody)) {
            log.warn("Webhook UAZAP rejeitado: estrutura inválida ou phone_number_id inesperado.");
            return ResponseEntity.badRequest().build();
        }

        // Processamento pesado fora da requisição HTTP (mesma fila inbound existente).
        dispatchService.despachar(rawBody);
        return ResponseEntity.ok().build();
    }

    /**
     * Valida o segredo opcional. Quando não configurado, aceita (estado documentado como frágil).
     * Quando configurado, exige igualdade de tempo constante com o parâmetro {@code ?secret=}.
     */
    private boolean segredoValido(String secret) {
        String configured = properties.getUazap().getWebhookSecret();
        if (configured == null || configured.isBlank()) {
            return true;
        }
        if (secret == null) {
            return false;
        }
        return MessageDigest.isEqual(
                configured.getBytes(StandardCharsets.UTF_8),
                secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Valida estrutura mínima do envelope e, se configurado, o {@code phone_number_id} esperado.
     */
    private boolean estruturaValida(byte[] rawBody) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception error) {
            return false;
        }
        JsonNode entries = root.path("entry");
        if (!entries.isArray() || entries.isEmpty()) {
            return false;
        }
        String expected = properties.getUazap().getPhoneNumberId();
        if (expected == null || expected.isBlank()) {
            return true; // sem instância esperada configurada: estrutura básica basta.
        }
        for (JsonNode entry : entries) {
            for (JsonNode change : entry.path("changes")) {
                String phoneNumberId = change.path("value").path("metadata")
                        .path("phone_number_id").asText("");
                if (expected.equals(phoneNumberId)) {
                    return true;
                }
            }
        }
        return false;
    }
}
