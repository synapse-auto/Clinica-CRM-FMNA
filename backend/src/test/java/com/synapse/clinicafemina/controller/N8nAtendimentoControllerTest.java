package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.dto.MensagemDTO;
import com.synapse.clinicafemina.dto.n8n.N8nResponderRequest;
import com.synapse.clinicafemina.security.JwtService;
import com.synapse.clinicafemina.service.MensagemService;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(N8nAtendimentoController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "app.n8n.callback-secret=test-secret")
class N8nAtendimentoControllerTest {

    private static final String META_WAMID_LONGO =
            "wamid.HBgMNTU1NDkxMDgyNDk4FQIAEhgWM0VCMDA1MThDODExOUJERTJEQzlEOQA=";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MensagemService mensagemService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void should_accept_valid_n8n_secret_without_jwt_and_delegate_response() throws Exception {
        when(mensagemService.responderIa(eq(30L), any(N8nResponderRequest.class)))
                .thenReturn(new MensagemService.RespostaIaResultado(
                        mensagemDto(77L, "Resposta gerada pela IA", "ENVIADA"),
                        false
                ));

        mockMvc.perform(post("/api/n8n/atendimentos/30/responder")
                        .header("X-N8N-SECRET", "test-secret")
                        .contentType("application/json")
                        .content("""
                                {
                                  "pacienteId": 20,
                                  "mensagem": "Resposta gerada pela IA",
                                  "tipoMedia": "TEXTO",
                                  "origem": "N8N",
                                  "enviarWhatsapp": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(77))
                .andExpect(jsonPath("$.direcao").value("SAIDA"))
                .andExpect(jsonPath("$.remetente").value("IA"));

        verify(mensagemService).responderIa(eq(30L), any(N8nResponderRequest.class));
    }

    @Test
    void should_return_ok_when_n8n_retries_existing_whatsapp_message_id() throws Exception {
        when(mensagemService.responderIa(eq(30L), any(N8nResponderRequest.class)))
                .thenReturn(new MensagemService.RespostaIaResultado(
                        mensagemDto(88L, "Resposta ja registrada", "REGISTRADA"),
                        true
                ));

        mockMvc.perform(post("/api/n8n/atendimentos/30/responder")
                        .header("X-N8N-SECRET", "test-secret")
                        .contentType("application/json")
                        .content("""
                                {
                                  "pacienteId": 20,
                                  "mensagem": "Resposta ja registrada",
                                  "tipoMedia": "TEXTO",
                                  "origem": "N8N",
                                  "enviarWhatsapp": false,
                                  "whatsappMessageId": "%s",
                                  "enviadoEm": "2026-07-03T12:00:00Z"
                                }
                                """.formatted(META_WAMID_LONGO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(88))
                .andExpect(jsonPath("$.remetente").value("IA"))
                .andExpect(jsonPath("$.whatsappStatus").value("REGISTRADA"));
    }

    @Test
    void should_reject_n8n_response_when_whatsapp_message_id_exceeds_limit() throws Exception {
        String whatsappMessageId = "w".repeat(256);

        mockMvc.perform(post("/api/n8n/atendimentos/30/responder")
                        .header("X-N8N-SECRET", "test-secret")
                        .contentType("application/json")
                        .content("""
                                {
                                  "pacienteId": 20,
                                  "mensagem": "Resposta ja registrada",
                                  "tipoMedia": "TEXTO",
                                  "origem": "N8N",
                                  "enviarWhatsapp": false,
                                  "whatsappMessageId": "%s",
                                  "enviadoEm": "2026-07-03T12:00:00Z"
                                }
                                """.formatted(whatsappMessageId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.whatsappMessageId").exists());

        verify(mensagemService, never()).responderIa(any(), any());
    }

    @Test
    void should_reject_request_without_n8n_secret() throws Exception {
        mockMvc.perform(post("/api/n8n/atendimentos/30/responder")
                        .contentType("application/json")
                        .content("""
                                {
                                  "pacienteId": 20,
                                  "mensagem": "Resposta gerada pela IA",
                                  "tipoMedia": "TEXTO",
                                  "origem": "N8N",
                                  "enviarWhatsapp": true
                                }
                                """))
                .andExpect(status().isUnauthorized());

        verify(mensagemService, never()).responderIa(any(), any());
    }

    private MensagemDTO mensagemDto(Long id, String conteudo, String status) {
        return new MensagemDTO(
                id,
                "SAIDA",
                "IA",
                "TEXTO",
                conteudo,
                conteudo,
                status,
                null,
                OffsetDateTime.parse("2026-07-03T12:00:00Z"),
                null,
                null,
                null
        );
    }
}
