package com.synapse.clinicafemina.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Gestor;
import com.synapse.clinicafemina.domain.Medico;
import com.synapse.clinicafemina.domain.Recepcionista;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.AtendimentoDetalheDTO;
import com.synapse.clinicafemina.dto.MensagemDTO;
import com.synapse.clinicafemina.dto.n8n.N8nResponderRequest;
import com.synapse.clinicafemina.repository.ClinicaRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import com.synapse.clinicafemina.service.AtendimentoService;
import com.synapse.clinicafemina.service.MensagemService;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.clinic.slug=auth-test",
        "app.security.jwt.secret=test-only-secret-key-with-at-least-32-bytes",
        "app.security.jwt.expiration-ms=86400000",
        "app.initial-users.enabled=false",
        "app.n8n.callback-secret=test-secret",
        "CORS_ALLOWED_ORIGINS=https://clinica-crm-fmna.vercel.app"
})
@Transactional
class AuthSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClinicaRepository clinicaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    @MockBean
    private MensagemService mensagemService;

    @MockBean
    private AtendimentoService atendimentoService;

    private String gestorEmail;
    private String recepcionistaEmail;
    private String medicoEmail;
    private String trocaObrigatoriaEmail;
    private String senha;
    private Long clinicaId;

    @BeforeEach
    void setUp() {
        Clinica clinica = new Clinica();
        clinica.setNome("Clínica autenticação");
        clinica.setSlug("auth-test");
        clinica.setRazaoSocial("Clínica autenticação LTDA");
        clinica.setCnpj("11.111.111/0001-11");
        clinica.setEmailContato("auth@clinica.local");
        clinica.setTelefoneContato("44999999999");
        clinica = clinicaRepository.save(clinica);
        clinicaId = clinica.getId();

        senha = UUID.randomUUID() + "!Aa1";
        gestorEmail = "gestor-" + UUID.randomUUID() + "@clinica.local";
        recepcionistaEmail = "recepcao-" + UUID.randomUUID() + "@clinica.local";
        medicoEmail = "medico-" + UUID.randomUUID() + "@clinica.local";
        trocaObrigatoriaEmail = "primeiro-acesso-" + UUID.randomUUID() + "@clinica.local";

        saveUser(new Gestor(), clinica, "Gestor Teste", gestorEmail, false, false);
        saveUser(new Medico(), clinica, "Medico Teste", medicoEmail, false, false);
        saveUser(new Recepcionista(), clinica, "Recepção Teste", recepcionistaEmail, false, false);
        saveUser(new Gestor(), clinica, "Primeiro Acesso", trocaObrigatoriaEmail, true, true);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void should_return_authenticated_user_without_token_when_calling_me() throws Exception {
        String token = login(gestorEmail, senha, false);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(gestorEmail))
                .andExpect(jsonPath("$.perfil").value("GESTOR"))
                .andExpect(jsonPath("$.mustChangePassword").value(false))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void should_allow_only_manager_to_access_administrative_configuration_summary() throws Exception {
        mockMvc.perform(get("/api/configuracoes/resumo")
                        .header("Authorization", "Bearer " + login(gestorEmail, senha, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identidade.slug").value("auth-test"))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.senha").doesNotExist());

        mockMvc.perform(get("/api/configuracoes/resumo")
                        .header("Authorization", "Bearer " + login(recepcionistaEmail, senha, false)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/configuracoes/resumo")
                        .header("Authorization", "Bearer " + login(medicoEmail, senha, false)))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_return_unauthorized_when_calling_me_without_token() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_allow_production_frontend_cors_preflight_with_required_headers() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "https://clinica-crm-fmna.vercel.app")
                        .header("Access-Control-Request-Method", "POST")
                        .header(
                                "Access-Control-Request-Headers",
                                "authorization,content-type,accept,origin,x-requested-with"
                        ))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Access-Control-Allow-Origin",
                        "https://clinica-crm-fmna.vercel.app"
                ))
                .andExpect(header().string(
                        "Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("GET"),
                                org.hamcrest.Matchers.containsString("POST"),
                                org.hamcrest.Matchers.containsString("PUT"),
                                org.hamcrest.Matchers.containsString("PATCH"),
                                org.hamcrest.Matchers.containsString("DELETE"),
                                org.hamcrest.Matchers.containsString("OPTIONS")
                        )
                ))
                .andExpect(header().string(
                        "Access-Control-Allow-Headers",
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsStringIgnoringCase("Authorization"),
                                org.hamcrest.Matchers.containsStringIgnoringCase("Content-Type"),
                                org.hamcrest.Matchers.containsStringIgnoringCase("Accept"),
                                org.hamcrest.Matchers.containsStringIgnoringCase("Origin"),
                                org.hamcrest.Matchers.containsStringIgnoringCase("X-Requested-With")
                        )
                ))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void should_return_unauthorized_when_sensitive_endpoint_has_no_token() throws Exception {
        mockMvc.perform(get("/api/agendamentos")
                        .param("inicio", "2026-06-22T00:00:00-03:00")
                        .param("fim", "2026-06-27T00:00:00-03:00"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_allow_all_authenticated_profiles_to_view_agenda() throws Exception {
        for (String token : new String[] {
                login(gestorEmail, senha, false),
                login(recepcionistaEmail, senha, false),
                login(medicoEmail, senha, false)
        }) {
            mockMvc.perform(get("/api/agendamentos")
                            .header("Authorization", "Bearer " + token)
                            .param("inicio", "2026-06-22T00:00:00-03:00")
                            .param("fim", "2026-06-27T00:00:00-03:00"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void should_forbid_doctor_from_mutating_agenda() throws Exception {
        String token = login(medicoEmail, senha, false);

        mockMvc.perform(post("/api/agendamentos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pacienteId": 20,
                                  "medicoId": 30,
                                  "dataHoraInicio": "2026-06-22T09:00:00-03:00",
                                  "dataHoraFim": "2026-06-22T09:30:00-03:00",
                                  "tipo": "CONSULTA",
                                  "servicoNome": "Consulta"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_forbid_doctor_from_mutating_internal_atendimento_reminders() throws Exception {
        String token = login(medicoEmail, senha, false);

        mockMvc.perform(post("/api/atendimentos/30/lembretes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "data":"2026-07-10",
                                  "hora":"10:00",
                                  "mensagem":"Ligar para paciente"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/atendimentos/30/lembretes/7/concluir")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_allow_n8n_response_endpoint_with_secret_without_jwt() throws Exception {
        when(mensagemService.responderIa(eq(30L), any(N8nResponderRequest.class)))
                .thenReturn(new MensagemService.RespostaIaResultado(
                        new MensagemDTO(
                                77L,
                                "SAIDA",
                                "IA",
                                "TEXTO",
                                "Resposta gerada pela IA",
                                "Resposta gerada pela IA",
                                "ENVIADA",
                                null,
                                OffsetDateTime.parse("2026-07-03T12:00:00Z"),
                                null,
                                null,
                                null
                        ),
                        false
                ));

        mockMvc.perform(post("/api/n8n/atendimentos/30/responder")
                        .header("X-N8N-SECRET", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pacienteId":20,
                                  "mensagem":"Resposta gerada pela IA",
                                  "tipoMedia":"TEXTO",
                                  "origem":"N8N",
                                  "enviarWhatsapp":true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.remetente").value("IA"));
    }

    @Test
    void should_allow_n8n_ai_mode_endpoint_with_secret_without_jwt() throws Exception {
        when(atendimentoService.ativarModoIa(30L, clinicaId))
                .thenReturn(atendimentoIaDto());

        mockMvc.perform(patch("/api/n8n/atendimentos/30/modo-ia")
                        .header("X-N8N-SECRET", "test-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(30))
                .andExpect(jsonPath("$.tratadoPorIa").value(true));
    }

    @Test
    void should_reach_whatsapp_webhook_without_crm_authentication() throws Exception {
        mockMvc.perform(get("/api/webhooks/whatsapp/verify")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "test-token")
                        .param("hub.challenge", "challenge"))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_verify_whatsapp_webhook_on_root_path_with_valid_token() throws Exception {
        mockMvc.perform(get("/api/webhooks/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "test")
                        .param("hub.challenge", "challenge-root"))
                .andExpect(status().isOk())
                .andExpect(content().string("challenge-root"));
    }

    @Test
    void should_verify_whatsapp_webhook_on_legacy_verify_path_with_valid_token() throws Exception {
        mockMvc.perform(get("/api/webhooks/whatsapp/verify")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "test")
                        .param("hub.challenge", "challenge-legacy"))
                .andExpect(status().isOk())
                .andExpect(content().string("challenge-legacy"));
    }

    @Test
    void should_return_forbidden_when_receptionist_calls_manager_integration() throws Exception {
        String token = login(recepcionistaEmail, senha, false);

        mockMvc.perform(post("/api/integracoes/sincronizar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_block_crm_api_until_initial_password_is_changed() throws Exception {
        String token = login(trocaObrigatoriaEmail, senha, true);

        mockMvc.perform(get("/api/agendamentos")
                        .header("Authorization", "Bearer " + token)
                        .param("inicio", "2026-06-22T00:00:00-03:00")
                        .param("fim", "2026-06-27T00:00:00-03:00"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    @Test
    void should_change_initial_password_and_allow_crm_access() throws Exception {
        String token = login(trocaObrigatoriaEmail, senha, true);
        String novaSenha = "Lucas123";

        String response = mockMvc.perform(patch("/api/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "senhaAtual":"%s",
                                  "novaSenha":"%s",
                                  "confirmacaoNovaSenha":"%s"
                                }
                                """.formatted(senha, novaSenha, novaSenha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.mustChangePassword").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        String refreshedToken = json.get("token").asText();

        mockMvc.perform(get("/api/agendamentos")
                        .header("Authorization", "Bearer " + refreshedToken)
                        .param("inicio", "2026-06-22T00:00:00-03:00")
                        .param("fim", "2026-06-27T00:00:00-03:00"))
                .andExpect(status().isOk());

        login(trocaObrigatoriaEmail, novaSenha, false);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","senha":"%s"}
                                """.formatted(trocaObrigatoriaEmail, senha)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_reject_password_change_when_confirmation_differs() throws Exception {
        String token = login(trocaObrigatoriaEmail, senha, true);

        mockMvc.perform(patch("/api/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "senhaAtual":"%s",
                                  "novaSenha":"Lucas123",
                                  "confirmacaoNovaSenha":"Atendente1"
                                }
                                """.formatted(senha)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_reject_password_change_with_special_character() throws Exception {
        String token = login(trocaObrigatoriaEmail, senha, true);

        mockMvc.perform(patch("/api/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "senhaAtual":"%s",
                                  "novaSenha":"abc@123",
                                  "confirmacaoNovaSenha":"abc@123"
                                }
                                """.formatted(senha)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A senha deve ter no mínimo 6 caracteres, contendo letras e números."));
    }

    @Test
    void should_hide_internal_admin_from_visible_clinic_users() {
        boolean internalAdminIsVisible = usuarioRepository
                .findAtivosVisiveisByClinicaId(clinicaId)
                .stream()
                .anyMatch(usuario -> trocaObrigatoriaEmail.equals(usuario.getEmail()));

        assertFalse(internalAdminIsVisible);
    }

    private String login(String email, String password, boolean mustChangePassword) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","senha":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.mustChangePassword").value(mustChangePassword))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.get("token").asText();
    }

    private AtendimentoDetalheDTO atendimentoIaDto() {
        return new AtendimentoDetalheDTO(
                30L,
                "ATIVO",
                true,
                OffsetDateTime.parse("2026-07-03T12:00:00Z"),
                null,
                0,
                new AtendimentoDetalheDTO.PacienteDetalheDTO(
                        20L,
                        "Paciente",
                        "5544999999999",
                        null,
                        "EM_ATENDIMENTO",
                        null,
                        false,
                        null,
                        null,
                        null,
                        null
                ),
                null
        );
    }

    private void saveUser(
            Usuario usuario,
            Clinica clinica,
            String nome,
            String email,
            boolean mustChangePassword,
            boolean adminInterno
    ) {
        usuario.setClinica(clinica);
        usuario.setNome(nome);
        usuario.setEmail(email);
        usuario.setSenhaHash(passwordEncoder.encode(senha));
        usuario.setMustChangePassword(mustChangePassword);
        usuario.setAdminInterno(adminInterno);
        usuarioRepository.save(usuario);
    }
}
