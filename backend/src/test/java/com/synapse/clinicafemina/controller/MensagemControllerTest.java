package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Recepcionista;
import com.synapse.clinicafemina.dto.MensagemDTO;
import com.synapse.clinicafemina.dto.WhatsappTemplateDTO;
import com.synapse.clinicafemina.exception.GlobalExceptionHandler;
import com.synapse.clinicafemina.exception.WhatsappWindowClosedException;
import com.synapse.clinicafemina.service.ClinicaConfigService;
import com.synapse.clinicafemina.service.MensagemService;
import com.synapse.clinicafemina.service.WhatsappTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@ExtendWith(MockitoExtension.class)
class MensagemControllerTest {

    @Mock private MensagemService mensagemService;
    @Mock private ClinicaConfigService clinicaConfigService;
    @Mock private WhatsappTemplateService templateService;

    private MockMvc mockMvc;
    private Recepcionista usuario;

    @BeforeEach
    void setUp() {
        Clinica clinica = new Clinica();
        clinica.setId(1L);
        usuario = new Recepcionista();
        usuario.setId(20L);
        usuario.setClinica(clinica);
        usuario.setEmail("teste@example.test");
        usuario.setSenhaHash("hash-test-only");
        usuario.setPerfil("RECEPCIONISTA");
        usuario.setAtivo(true);
        lenient().when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new MensagemController(mensagemService, clinicaConfigService, templateService)
                )
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void should_list_sanitized_whatsapp_templates() throws Exception {
        when(templateService.listar(10L, 1L)).thenReturn(List.of(new WhatsappTemplateDTO(
                "tpl-1", "confirmacao", "pt_BR", "APPROVED", "UTILITY",
                null, "Consulta confirmada", null, List.of(), List.of(), true, null
        )));

        mockMvc.perform(get("/api/atendimentos/10/templates-whatsapp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("confirmacao"))
                .andExpect(jsonPath("$[0].status").value("APPROVED"))
                .andExpect(jsonPath("$[0].accessToken").doesNotExist());
    }

    @Test
    void should_send_one_template_and_return_created_message() throws Exception {
        MensagemDTO response = new MensagemDTO(
                99L, "SAIDA", "ATENDENTE", "TEMPLATE", "Consulta confirmada",
                "Consulta confirmada", "ENVIADA", null,
                OffsetDateTime.parse("2026-07-16T12:00:00Z"), null, null, null,
                "confirmacao", "pt_BR"
        );
        when(templateService.enviar(eq(10L), eq(1L), isNull(), any())).thenReturn(response);

        mockMvc.perform(post("/api/atendimentos/10/mensagens-template")
                        .with(user(usuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"confirmacao",
                                  "idioma":"pt_BR",
                                  "parametros":[]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoMedia").value("TEMPLATE"))
                .andExpect(jsonPath("$.templateNome").value("confirmacao"));
    }

    @Test
    void should_return_structured_conflict_when_whatsapp_window_is_closed() throws Exception {
        when(mensagemService.enviar(eq(10L), eq(1L), any(), isNull()))
                .thenThrow(new WhatsappWindowClosedException());

        mockMvc.perform(post("/api/atendimentos/10/mensagens")
                        .with(user(usuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoMedia\":\"TEXTO\",\"conteudo\":\"Ola\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WHATSAPP_TEMPLATE_REQUIRED"))
                .andExpect(jsonPath("$.message").value(WhatsappWindowClosedException.MESSAGE));
    }

    @Test
    void should_reject_phone_and_clinic_fields_in_template_request() throws Exception {
        mockMvc.perform(post("/api/atendimentos/10/mensagens-template")
                        .with(user(usuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"confirmacao",
                                  "idioma":"pt_BR",
                                  "parametros":[],
                                  "telefone":"5511999990000",
                                  "clinicaId":99
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
