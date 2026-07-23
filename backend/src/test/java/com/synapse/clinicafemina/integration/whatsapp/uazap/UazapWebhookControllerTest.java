package com.synapse.clinicafemina.integration.whatsapp.uazap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.clinicafemina.integration.whatsapp.config.WhatsappProperties;
import com.synapse.clinicafemina.service.WhatsappWebhookDispatchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("UazapWebhookController — endpoint isolado, validação e despacho assíncrono")
class UazapWebhookControllerTest {

    private static final String VALID_PAYLOAD = """
            {"object":"whatsapp_business_account","entry":[{"id":"INST","changes":[{"field":"messages",
            "value":{"metadata":{"phone_number_id":"PNID-1"},
            "messages":[{"from":"5511988887777","id":"UZ-1","type":"text","text":{"body":"oi"}}]}}]}]}
            """;

    private final WhatsappWebhookDispatchService dispatchService = mock(WhatsappWebhookDispatchService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private WhatsappProperties properties(boolean enabled, String provider, String expectedInstance, String secret) {
        WhatsappProperties properties = new WhatsappProperties();
        properties.setEnabled(enabled);
        properties.setProvider(provider);
        properties.getUazap().setPhoneNumberId(expectedInstance);
        properties.getUazap().setWebhookSecret(secret);
        return properties;
    }

    private UazapWebhookController controller(WhatsappProperties properties) {
        return new UazapWebhookController(properties, dispatchService, objectMapper);
    }

    private byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("payload válido responde 200 e despacha o corpo bruto para processamento assíncrono")
    void validPayload_dispatches() {
        ResponseEntity<Void> response = controller(properties(true, "UAZAP", "PNID-1", null))
                .receberWebhook(bytes(VALID_PAYLOAD), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(dispatchService, times(1)).despachar(bytes(VALID_PAYLOAD));
    }

    @Test
    @DisplayName("WhatsApp desligado responde 503 e não despacha")
    void disabled_returns503() {
        ResponseEntity<Void> response = controller(properties(false, "UAZAP", "PNID-1", null))
                .receberWebhook(bytes(VALID_PAYLOAD), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verify(dispatchService, never()).despachar(any());
    }

    @Test
    @DisplayName("payload acima do limite responde 413")
    void oversized_returns413() {
        byte[] big = new byte[UazapWebhookController.MAX_PAYLOAD_BYTES + 1];
        ResponseEntity<Void> response = controller(properties(true, "UAZAP", null, null))
                .receberWebhook(big, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        verify(dispatchService, never()).despachar(any());
    }

    @Test
    @DisplayName("JSON inválido responde 400")
    void invalidJson_returns400() {
        ResponseEntity<Void> response = controller(properties(true, "UAZAP", null, null))
                .receberWebhook(bytes("nao-e-json"), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(dispatchService, never()).despachar(any());
    }

    @Test
    @DisplayName("phone_number_id diferente do esperado responde 400")
    void wrongInstance_returns400() {
        ResponseEntity<Void> response = controller(properties(true, "UAZAP", "OUTRA-INSTANCIA", null))
                .receberWebhook(bytes(VALID_PAYLOAD), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(dispatchService, never()).despachar(any());
    }

    @Test
    @DisplayName("segredo configurado e ausente/errado responde 401")
    void wrongSecret_returns401() {
        ResponseEntity<Void> response = controller(properties(true, "UAZAP", "PNID-1", "s3cr3t"))
                .receberWebhook(bytes(VALID_PAYLOAD), "errado");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(dispatchService, never()).despachar(any());
    }

    @Test
    @DisplayName("segredo configurado e correto responde 200 e despacha")
    void correctSecret_dispatches() {
        ResponseEntity<Void> response = controller(properties(true, "UAZAP", "PNID-1", "s3cr3t"))
                .receberWebhook(bytes(VALID_PAYLOAD), "s3cr3t");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(dispatchService, times(1)).despachar(bytes(VALID_PAYLOAD));
    }
}
