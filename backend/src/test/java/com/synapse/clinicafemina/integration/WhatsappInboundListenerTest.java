package com.synapse.clinicafemina.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.clinicafemina.service.RealtimeBroadcastService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WhatsappInboundListenerTest {

    @Mock
    private WhatsappInboundMapper inboundMapper;

    @Mock
    private RealtimeBroadcastService broadcastService;

    @Test
    @SuppressWarnings("unchecked")
    void should_pass_original_meta_payload_to_mapper() {
        byte[] rawBody = """
                {
                  "object": "whatsapp_business_account",
                  "entry": [
                    {
                      "id": "waba-1",
                      "changes": [
                        {
                          "field": "messages",
                          "value": {
                            "metadata": {"phone_number_id": "phone-ultra"},
                            "contacts": [{"wa_id": "5511999990000"}],
                            "messages": [
                              {
                                "id": "wamid-1",
                                "timestamp": "1781455200",
                                "text": {"body": "Ola"}
                              }
                            ]
                          }
                        }
                      ]
                    }
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8);
        WhatsappInboundListener listener = new WhatsappInboundListener(
                inboundMapper,
                new ObjectMapper(),
                broadcastService
        );

        listener.processarMensagem(rawBody);

        ArgumentCaptor<Map<String, Object>> valueCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<byte[]> rawCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(inboundMapper).processarMensagemTexto(valueCaptor.capture(), rawCaptor.capture());
        assertEquals("phone-ultra", ((Map<?, ?>) valueCaptor.getValue().get("metadata")).get("phone_number_id"));
        assertArrayEquals(rawBody, rawCaptor.getValue());
    }
}
