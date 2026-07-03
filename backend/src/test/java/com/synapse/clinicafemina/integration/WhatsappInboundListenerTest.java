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
import static org.mockito.Mockito.times;
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

    @Test
    @SuppressWarnings("unchecked")
    void should_dispatch_all_messages_from_multiple_entries_and_changes() {
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
                                "from": "5511999990000",
                                "timestamp": "1781455200",
                                "type": "text",
                                "text": {"body": "Primeira"}
                              }
                            ]
                          }
                        },
                        {
                          "field": "messages",
                          "value": {
                            "metadata": {"phone_number_id": "phone-ultra"},
                            "contacts": [{"wa_id": "5511999990000"}],
                            "messages": [
                              {
                                "id": "wamid-2",
                                "from": "5511999990000",
                                "timestamp": "1781455260",
                                "type": "text",
                                "text": {"body": "Segunda"}
                              }
                            ]
                          }
                        }
                      ]
                    },
                    {
                      "id": "waba-2",
                      "changes": [
                        {
                          "field": "messages",
                          "value": {
                            "metadata": {"phone_number_id": "phone-ultra"},
                            "contacts": [{"wa_id": "5511999990000"}],
                            "messages": [
                              {
                                "id": "wamid-3",
                                "from": "5511999990000",
                                "timestamp": "1781455320",
                                "type": "text",
                                "text": {"body": "Terceira"}
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
        verify(inboundMapper, times(3)).processarMensagemTexto(valueCaptor.capture(), rawCaptor.capture());
        assertEquals(
                java.util.List.of("wamid-1", "wamid-2", "wamid-3"),
                valueCaptor.getAllValues().stream()
                        .map(value -> (java.util.List<Map<String, Object>>) value.get("messages"))
                        .map(messages -> String.valueOf(messages.getFirst().get("id")))
                        .toList()
        );
        rawCaptor.getAllValues().forEach(raw -> assertArrayEquals(rawBody, raw));
    }
}
