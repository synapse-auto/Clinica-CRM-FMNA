package com.synapse.clinicafemina.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Gestor;
import com.synapse.clinicafemina.domain.Recepcionista;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.repository.ClinicaRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.clinic.slug=auth-test",
        "app.security.jwt.secret=test-only-secret-key-with-at-least-32-bytes",
        "app.security.jwt.expiration-ms=86400000",
        "app.initial-users.enabled=false",
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

    private String gestorEmail;
    private String recepcionistaEmail;
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
        trocaObrigatoriaEmail = "primeiro-acesso-" + UUID.randomUUID() + "@clinica.local";

        saveUser(new Gestor(), clinica, "Gestor Teste", gestorEmail, false, false);
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
    void should_reach_whatsapp_webhook_without_crm_authentication() throws Exception {
        mockMvc.perform(get("/api/webhooks/whatsapp/verify")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "test-token")
                        .param("hub.challenge", "challenge"))
                .andExpect(status().isForbidden());
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
        String novaSenha = "NovaSenhaSegura!2026";

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
                                  "novaSenha":"NovaSenhaSegura!2026",
                                  "confirmacaoNovaSenha":"SenhaDiferente!2026"
                                }
                                """.formatted(senha)))
                .andExpect(status().isBadRequest());
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
