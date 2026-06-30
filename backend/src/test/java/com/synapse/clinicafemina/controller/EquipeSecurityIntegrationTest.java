package com.synapse.clinicafemina.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Gestor;
import com.synapse.clinicafemina.domain.Medico;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.clinic.slug=equipe-test",
        "app.security.jwt.secret=test-only-secret-key-with-at-least-32-bytes",
        "app.security.jwt.expiration-ms=86400000",
        "app.initial-users.enabled=false"
})
@Transactional
class EquipeSecurityIntegrationTest {

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
    private String medicoEmail;
    private String senha;

    @BeforeEach
    void setUp() {
        Clinica clinica = new Clinica();
        clinica.setNome("Clinica equipe");
        clinica.setSlug("equipe-test");
        clinica.setRazaoSocial("Clinica equipe LTDA");
        clinica.setCnpj("22.222.222/0001-22");
        clinica.setEmailContato("equipe@clinica.local");
        clinica.setTelefoneContato("44999999999");
        clinica = clinicaRepository.save(clinica);

        senha = UUID.randomUUID() + "!Aa1";
        gestorEmail = "gestor-" + UUID.randomUUID() + "@clinica.local";
        recepcionistaEmail = "recepcao-" + UUID.randomUUID() + "@clinica.local";
        medicoEmail = "medico-" + UUID.randomUUID() + "@clinica.local";

        saveUser(new Gestor(), clinica, "Gestora Real", gestorEmail, false, false);
        saveUser(new Recepcionista(), clinica, "Recepcao Real", recepcionistaEmail, false, false);
        saveUser(new Medico(), clinica, "Medico Real", medicoEmail, false, false);
        saveUser(new Gestor(), clinica, "Admin Interno", "interno-" + UUID.randomUUID() + "@clinica.local", false, true);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void gestor_should_list_visible_team_without_internal_admins() throws Exception {
        String token = login(gestorEmail);

        mockMvc.perform(get("/api/equipe")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grupos[0].perfil").value("GESTOR"))
                .andExpect(jsonPath("$.grupos[0].usuarios[0].nome").value("Gestora Real"))
                .andExpect(jsonPath("$.grupos[1].perfil").value("MEDICO"))
                .andExpect(jsonPath("$.grupos[1].usuarios[0].nome").value("Medico Real"))
                .andExpect(jsonPath("$.grupos[2].perfil").value("RECEPCIONISTA"))
                .andExpect(jsonPath("$.grupos[2].usuarios[0].nome").value("Recepcao Real"))
                .andExpect(jsonPath("$..[?(@.nome == 'Admin Interno')]").doesNotExist());
    }

    @Test
    void gestor_should_create_receptionist_and_new_user_should_appear_in_team() throws Exception {
        String token = login(gestorEmail);
        String novoEmail = "nova.recepcao-" + UUID.randomUUID() + "@clinica.local";

        mockMvc.perform(post("/api/equipe/usuarios")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Recepcao Nova",
                                  "email":"%s",
                                  "perfil":"RECEPCIONISTA",
                                  "telefone":"44988887777",
                                  "senhaTemporaria":"12345678"
                                }
                                """.formatted(novoEmail)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(novoEmail))
                .andExpect(jsonPath("$.perfil").value("RECEPCIONISTA"))
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        Usuario usuarioCriado = usuarioRepository.findByEmail(novoEmail).orElseThrow();
        assertTrue(usuarioCriado.getMustChangePassword());
        assertTrue(passwordEncoder.matches("12345678", usuarioCriado.getSenhaHash()));
        assertFalse(usuarioCriado.getAdminInterno());

        mockMvc.perform(get("/api/equipe")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grupos[2].usuarios[?(@.email == '%s')].nome".formatted(novoEmail))
                        .value("Recepcao Nova"));
    }

    @Test
    void gestor_should_create_doctor_and_new_user_should_appear_in_team() throws Exception {
        String token = login(gestorEmail);
        String novoEmail = "medico.novo-" + UUID.randomUUID() + "@clinica.local";

        mockMvc.perform(post("/api/equipe/usuarios")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Medico Novo",
                                  "email":"%s",
                                  "perfil":"MEDICO",
                                  "senhaTemporaria":"12345678"
                                }
                                """.formatted(novoEmail)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.perfil").value("MEDICO"))
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        mockMvc.perform(get("/api/equipe")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grupos[1].usuarios[?(@.email == '%s')].nome".formatted(novoEmail))
                        .value("Medico Novo"));
    }

    @Test
    void receptionist_and_doctor_should_not_create_users() throws Exception {
        String recepcionistaToken = login(recepcionistaEmail);
        String medicoToken = login(medicoEmail);
        String body = """
                {
                  "nome":"Usuario Bloqueado",
                  "email":"bloqueado-%s@clinica.local",
                  "perfil":"RECEPCIONISTA",
                  "senhaTemporaria":"12345678"
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/equipe/usuarios")
                        .header("Authorization", "Bearer " + recepcionistaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/equipe/usuarios")
                        .header("Authorization", "Bearer " + medicoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    private String login(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","senha":"%s"}
                                """.formatted(email, senha)))
                .andExpect(status().isOk())
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
        usuario.setAtivo(true);
        usuarioRepository.save(usuario);
    }
}
