package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Mensagem;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class N8nEventServiceTest {

    @Test
    void should_forward_original_meta_payload_without_envelope() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        N8nEventService service = new N8nEventService(builder.build());
        Clinica clinica = new Clinica();
        clinica.setId(1L);
        clinica.setSlug("ultramedical");
        clinica.setUsaN8n(true);
        clinica.setN8nWebhookUrl("https://n8n.example/webhook/secret");
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
                                "type": "text",
                                "text": {"body": "Ola"}
                              }
                            ]
                          }
                        }
                      ]
                    }
                  ]
                }
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        server.expect(once(), requestTo("https://n8n.example/webhook/secret"))
                .andExpect(method(POST))
                .andExpect(header("X-CRM-Event", "mensagem_recebida"))
                .andExpect(header("X-CRM-Clinic-Slug", "ultramedical"))
                .andExpect(header("X-CRM-Atendimento-Id", "123"))
                .andExpect(header("X-CRM-Paciente-Id", "456"))
                .andExpect(header("X-CRM-Mensagem-Id", "789"))
                .andExpect(header("X-CRM-Whatsapp-Message-Id", "wamid-1"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "object": "whatsapp_business_account",
                          "entry": [
                            {
                              "changes": [
                                {
                                  "value": {
                                    "messages": [
                                      {
                                        "id": "wamid-1",
                                        "text": {"body": "Ola"}
                                      }
                                    ],
                                    "contacts": [{"wa_id": "5511999990000"}],
                                    "metadata": {"phone_number_id": "phone-ultra"}
                                  }
                                }
                              ]
                            }
                          ]
                        }
                        """))
                .andExpect(content().string(not(containsString("evento"))))
                .andExpect(content().string(not(containsString("n8nWebhookUrl"))))
                .andExpect(content().string(not(containsString("https://n8n.example"))))
                .andRespond(withSuccess());

        service.enviarPayloadMetaOriginal(
                clinica,
                rawBody,
                new N8nEventService.MetaWebhookContext(
                        "mensagem_recebida",
                        123L,
                        456L,
                        789L,
                        "TEXTO",
                        "wamid-1"
                )
        );

        server.verify();
    }

    @Test
    void should_create_message_received_payload_from_persisted_entities() {
        N8nEventService service = new N8nEventService(RestClient.builder().build());

        Clinica clinica = new Clinica();
        clinica.setId(1L);
        clinica.setSlug("ultramedical");
        clinica.setExternalProvider(ExternalProviderType.MEDWARE);
        clinica.setUsaN8n(true);
        clinica.setN8nWebhookUrl("https://n8n.example/webhook/secret");

        Paciente paciente = new Paciente();
        paciente.setId(456L);

        Atendimento atendimento = new Atendimento();
        atendimento.setId(123L);

        Mensagem mensagem = new Mensagem();
        mensagem.setId(789L);
        mensagem.setTipoMedia("TEXTO");
        mensagem.setDirecao("ENTRADA");
        mensagem.setCriadoEm(OffsetDateTime.parse("2026-06-15T12:00:00Z"));

        N8nEventPayload payload = service.criarPayloadMensagemRecebida(
                clinica,
                paciente,
                atendimento,
                mensagem
        );

        assertEquals("mensagem_recebida", payload.evento());
        assertEquals(1L, payload.clinicaId());
        assertEquals(123L, payload.atendimentoId());
        assertEquals(456L, payload.pacienteId());
        assertEquals(789L, payload.mensagemId());
        assertEquals("TEXTO", payload.tipoMedia());
        assertEquals("ENTRADA", payload.direcao());
        assertEquals("WHATSAPP", payload.origem());
        assertNull(payload.telefone());
        assertEquals("2026-06-15T12:00:00Z", payload.semConfiguracaoInterna().criadoEm());
    }

    @Test
    void should_emit_message_received_payload_without_internal_configuration() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        N8nEventService service = new N8nEventService(builder.build());

        server.expect(once(), requestTo("https://n8n.example/webhook/secret"))
                .andExpect(method(POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "evento": "mensagem_recebida",
                          "clinicaId": 1,
                          "atendimentoId": 123,
                          "pacienteId": 456,
                          "mensagemId": 789,
                          "tipoMedia": "TEXTO",
                          "direcao": "ENTRADA",
                          "origem": "WHATSAPP",
                          "criadoEm": "2026-06-15T12:00:00Z"
                        }
                        """))
                .andExpect(content().string(not(containsString("n8nWebhookUrl"))))
                .andExpect(content().string(not(containsString("usaN8n"))))
                .andExpect(content().string(not(containsString("https://n8n.example"))))
                .andRespond(withSuccess());

        service.emitir(new N8nEventPayload(
                1L,
                "ultramedical",
                ExternalProviderType.MEDWARE,
                "mensagem_recebida",
                456L,
                123L,
                null,
                null,
                789L,
                "TEXTO",
                "ENTRADA",
                "WHATSAPP",
                OffsetDateTime.parse("2026-06-15T12:00:00Z"),
                true,
                "https://n8n.example/webhook/secret"
        ));

        server.verify();
    }

    @Test
    void should_not_break_flow_when_n8n_returns_error() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        N8nEventService service = new N8nEventService(builder.build());

        server.expect(once(), requestTo("https://n8n.example/webhook/secret"))
                .andExpect(method(POST))
                .andRespond(withStatus(SERVICE_UNAVAILABLE));

        N8nEventPayload payload = new N8nEventPayload(
                1L,
                "ultramedical",
                ExternalProviderType.MEDWARE,
                "mensagem_recebida",
                456L,
                123L,
                null,
                null,
                789L,
                "TEXTO",
                "ENTRADA",
                "WHATSAPP",
                OffsetDateTime.parse("2026-06-15T12:00:00Z"),
                true,
                "https://n8n.example/webhook/secret"
        );

        assertDoesNotThrow(() -> service.emitir(payload));
        server.verify();
    }

    @Test
    void should_not_break_original_meta_forward_when_n8n_returns_error() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        N8nEventService service = new N8nEventService(builder.build());
        Clinica clinica = new Clinica();
        clinica.setId(1L);
        clinica.setSlug("ultramedical");
        clinica.setUsaN8n(true);
        clinica.setN8nWebhookUrl("https://n8n.example/webhook/secret");
        byte[] rawBody = """
                {
                  "object": "whatsapp_business_account",
                  "entry": []
                }
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        server.expect(once(), requestTo("https://n8n.example/webhook/secret"))
                .andExpect(method(POST))
                .andExpect(header("X-CRM-Event", "mensagem_recebida"))
                .andExpect(header("X-CRM-Atendimento-Id", "123"))
                .andExpect(header("X-CRM-Paciente-Id", "456"))
                .andExpect(header("X-CRM-Mensagem-Id", "789"))
                .andExpect(header("X-CRM-Whatsapp-Message-Id", "wamid-audio"))
                .andRespond(withStatus(SERVICE_UNAVAILABLE));

        assertDoesNotThrow(() -> service.enviarPayloadMetaOriginal(
                clinica,
                rawBody,
                new N8nEventService.MetaWebhookContext(
                        "mensagem_recebida",
                        123L,
                        456L,
                        789L,
                        "AUDIO",
                        "wamid-audio"
                )
        ));
        server.verify();
    }
}
