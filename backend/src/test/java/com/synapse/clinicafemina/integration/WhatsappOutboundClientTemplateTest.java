package com.synapse.clinicafemina.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WhatsappOutboundClientTemplateTest {

    private MockRestServiceServer server;
    private WhatsappOutboundClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new WhatsappOutboundClient(builder);
        ReflectionTestUtils.setField(client, "enabled", true);
        ReflectionTestUtils.setField(client, "accessToken", "token-test-only");
        ReflectionTestUtils.setField(client, "phoneNumberId", "phone-1");
        ReflectionTestUtils.setField(client, "businessAccountId", "waba-1");
        ReflectionTestUtils.setField(client, "graphApiUrl", "https://graph.example/v20.0");
    }

    @Test
    void should_list_template_page_using_cursor_without_following_next_url() {
        server.expect(once(), requestTo(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("/waba-1/message_templates"),
                        org.hamcrest.Matchers.containsString("after=cursor-1")
                )))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token-test-only"))
                .andRespond(withSuccess("""
                        {
                          "data":[{
                            "id":"tpl-1",
                            "name":"confirmacao",
                            "language":"pt_BR",
                            "status":"APPROVED",
                            "category":"UTILITY",
                            "components":[]
                          }],
                          "paging":{"cursors":{"after":"cursor-2"},"next":"https://untrusted.example"}
                        }
                        """, MediaType.APPLICATION_JSON));

        var page = client.listarTemplatesPagina("cursor-1");

        assertEquals(1, page.templates().size());
        assertEquals("cursor-2", page.after());
        server.verify();
    }

    @Test
    void should_send_exact_template_payload_without_retry_wrapper() {
        server.expect(once(), requestTo("https://graph.example/v20.0/phone-1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token-test-only"))
                .andExpect(jsonPath("$.messaging_product").value("whatsapp"))
                .andExpect(jsonPath("$.recipient_type").value("individual"))
                .andExpect(jsonPath("$.type").value("template"))
                .andExpect(jsonPath("$.template.name").value("confirmacao"))
                .andExpect(jsonPath("$.template.language.code").value("pt_BR"))
                .andExpect(jsonPath("$.template.components[0].type").value("body"))
                .andExpect(jsonPath("$.template.components[0].parameters[0].text").value("16/07/2026"))
                .andRespond(withSuccess("{" + "\"messages\":[{\"id\":\"wamid-1\"}]}",
                        MediaType.APPLICATION_JSON));

        String wamid = client.enviarTemplate(
                "5511999990000",
                "confirmacao",
                "pt_BR",
                List.of(Map.of(
                        "type", "body",
                        "parameters", List.of(Map.of("type", "text", "text", "16/07/2026"))
                ))
        );

        assertEquals("wamid-1", wamid);
        assertTrue(client.templatesDisponiveis());
        server.verify();
    }
}
