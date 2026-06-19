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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.clinic.slug=auth-test",
        "app.security.jwt.secret=test-only-secret-key-with-at-least-32-bytes",
        "app.security.jwt.expiration-ms=86400000"
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
    private String senha;

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

        senha = UUID.randomUUID().toString();
        gestorEmail = "gestor-" + UUID.randomUUID() + "@clinica.local";
        recepcionistaEmail = "recepcao-" + UUID.randomUUID() + "@clinica.local";

        saveUser(new Gestor(), clinica, "Gestor Teste", gestorEmail);
        saveUser(new Recepcionista(), clinica, "Recepção Teste", recepcionistaEmail);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void should_return_authenticated_user_without_token_when_calling_me() throws Exception {
        String token = login(gestorEmail);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(gestorEmail))
                .andExpect(jsonPath("$.perfil").value("GESTOR"))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void should_return_unauthorized_when_calling_me_without_token() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_return_unauthorized_when_sensitive_endpoint_has_no_token() throws Exception {
        mockMvc.perform(get("/api/agendamentos")
                        .param("inicio", "2026-06-22T00:00:00-03:00")
                        .param("fim", "2026-06-27T00:00:00-03:00"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_return_forbidden_when_receptionist_calls_manager_integration() throws Exception {
        String token = login(recepcionistaEmail);

        mockMvc.perform(post("/api/integracoes/sincronizar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private String login(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","senha":"%s"}
                                """.formatted(email, senha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.get("token").asText();
    }

    private void saveUser(Usuario usuario, Clinica clinica, String nome, String email) {
        usuario.setClinica(clinica);
        usuario.setNome(nome);
        usuario.setEmail(email);
        usuario.setSenhaHash(passwordEncoder.encode(senha));
        usuarioRepository.save(usuario);
    }
}
