package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.dto.AtendimentoDetalheDTO;
import com.synapse.clinicafemina.dto.MensagemDTO;
import com.synapse.clinicafemina.dto.TransferirAtendimentoRequest;
import com.synapse.clinicafemina.dto.n8n.N8nResponderRequest;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.security.JwtService;
import com.synapse.clinicafemina.service.AtendimentoService;
import com.synapse.clinicafemina.service.MensagemService;
import com.synapse.clinicafemina.service.N8nCallbackAuthorizationService;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.BadCredentialsException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(N8nAtendimentoController.class)
@AutoConfigureMockMvc(addFilters = false)
class N8nAtendimentoControllerTest {

    private static final String META_WAMID_LONGO =
            "wamid.HBgMNTU1NDkxMDgyNDk4FQIAEhgWM0VCMDA1MThDODExOUJERTJEQzlEOQA=";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MensagemService mensagemService;

    @MockBean
    private AtendimentoService atendimentoService;

    @MockBean
    private N8nCallbackAuthorizationService authorizationService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void should_accept_valid_n8n_secret_without_jwt_and_delegate_response() throws Exception {
        autorizar("test-secret");
        when(mensagemService.responderIa(eq(30L), eq(7L), any(N8nResponderRequest.class)))
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

        verify(mensagemService).responderIa(eq(30L), eq(7L), any(N8nResponderRequest.class));
    }

    @Test
    void should_return_ok_when_n8n_retries_existing_whatsapp_message_id() throws Exception {
        autorizar("test-secret");
        when(mensagemService.responderIa(eq(30L), eq(7L), any(N8nResponderRequest.class)))
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

        verify(mensagemService, never()).responderIa(any(), any(), any());
    }

    @Test
    void should_reject_request_without_n8n_secret() throws Exception {
        rejeitarSecret(null);

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

        verify(mensagemService, never()).responderIa(any(), any(), any());
    }

    @Test
    void should_transfer_to_human_with_valid_n8n_secret_without_jwt() throws Exception {
        autorizar("test-secret");
        when(atendimentoService.transferir(eq(30L), any(TransferirAtendimentoRequest.class), eq(7L), eq(1L)))
                .thenReturn(atendimentoHumano());

        mockMvc.perform(post("/api/n8n/atendimentos/30/transferir-humano")
                        .header("X-N8N-SECRET", "test-secret")
                        .contentType("application/json")
                        .content("""
                                {
                                  "novoAtendenteId": 1,
                                  "motivo": "Transferido pelo N8N"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(30))
                .andExpect(jsonPath("$.status").value("ATIVO"))
                .andExpect(jsonPath("$.tratadoPorIa").value(false))
                .andExpect(jsonPath("$.atendentePrincipal.id").value(1));

        verify(atendimentoService).transferir(eq(30L), any(TransferirAtendimentoRequest.class), eq(7L), eq(1L));
    }

    @Test
    void should_reject_transfer_to_human_without_n8n_secret() throws Exception {
        rejeitarSecret(null);

        mockMvc.perform(post("/api/n8n/atendimentos/30/transferir-humano")
                        .contentType("application/json")
                        .content("""
                                {
                                  "novoAtendenteId": 1,
                                  "motivo": "Transferido pelo N8N"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        verify(atendimentoService, never()).transferir(any(), any(), any(), any());
    }

    @Test
    void should_reject_transfer_to_human_with_invalid_n8n_secret() throws Exception {
        rejeitarSecret("wrong-secret");

        mockMvc.perform(post("/api/n8n/atendimentos/30/transferir-humano")
                        .header("X-N8N-SECRET", "wrong-secret")
                        .contentType("application/json")
                        .content("""
                                {
                                  "novoAtendenteId": 1,
                                  "motivo": "Transferido pelo N8N"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        verify(atendimentoService, never()).transferir(any(), any(), any(), any());
    }

    @Test
    void should_return_clear_error_when_n8n_transfer_target_attendant_is_invalid() throws Exception {
        autorizar("test-secret");
        when(atendimentoService.transferir(eq(30L), any(TransferirAtendimentoRequest.class), eq(7L), eq(999L)))
                .thenThrow(new NotFoundException("Usuário não encontrado"));

        mockMvc.perform(post("/api/n8n/atendimentos/30/transferir-humano")
                        .header("X-N8N-SECRET", "test-secret")
                        .contentType("application/json")
                        .content("""
                                {
                                  "novoAtendenteId": 999,
                                  "motivo": "Transferido pelo N8N"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Usuário não encontrado"));
    }

    @Test
    void should_not_allow_n8n_transfer_when_atendimento_is_closed() throws Exception {
        autorizar("test-secret");
        when(atendimentoService.transferir(eq(30L), any(TransferirAtendimentoRequest.class), eq(7L), eq(1L)))
                .thenThrow(new IllegalStateException("Não é possível transferir um atendimento encerrado"));

        mockMvc.perform(post("/api/n8n/atendimentos/30/transferir-humano")
                        .header("X-N8N-SECRET", "test-secret")
                        .contentType("application/json")
                        .content("""
                                {
                                  "novoAtendenteId": 1,
                                  "motivo": "Transferido pelo N8N"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Não é possível transferir um atendimento encerrado"));
    }

    @Test
    void should_return_to_ai_mode_with_valid_n8n_secret_without_jwt() throws Exception {
        autorizar("test-secret");
        when(atendimentoService.ativarModoIa(30L, 7L)).thenReturn(atendimentoIa());

        mockMvc.perform(patch("/api/n8n/atendimentos/30/modo-ia")
                        .header("X-N8N-SECRET", "test-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(30))
                .andExpect(jsonPath("$.status").value("ATIVO"))
                .andExpect(jsonPath("$.tratadoPorIa").value(true))
                .andExpect(jsonPath("$.atendentePrincipal").doesNotExist());

        verify(atendimentoService).ativarModoIa(30L, 7L);
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

    private void autorizar(String secret) {
        when(authorizationService.autorizar(secret, 30L))
                .thenReturn(new N8nCallbackAuthorizationService.Autorizacao(7L));
    }

    private void rejeitarSecret(String secret) {
        when(authorizationService.autorizar(secret, 30L))
                .thenThrow(new BadCredentialsException("Credencial N8N invalida."));
    }

    private AtendimentoDetalheDTO atendimentoHumano() {
        return new AtendimentoDetalheDTO(
                30L,
                "ATIVO",
                false,
                OffsetDateTime.parse("2026-07-03T12:00:00Z"),
                null,
                0,
                pacienteDto(),
                new AtendimentoDetalheDTO.AtendenteDTO(1L, "Atendente", "RECEPCIONISTA")
        );
    }

    private AtendimentoDetalheDTO atendimentoIa() {
        return new AtendimentoDetalheDTO(
                30L,
                "ATIVO",
                true,
                OffsetDateTime.parse("2026-07-03T12:00:00Z"),
                null,
                0,
                pacienteDto(),
                null
        );
    }

    private AtendimentoDetalheDTO.PacienteDetalheDTO pacienteDto() {
        return new AtendimentoDetalheDTO.PacienteDetalheDTO(
                20L,
                "Paciente",
                "5544999999999",
                null,
                "EM_ATENDIMENTO",
                null,
                null,
                false,
                null,
                null,
                null,
                null
        );
    }
}
