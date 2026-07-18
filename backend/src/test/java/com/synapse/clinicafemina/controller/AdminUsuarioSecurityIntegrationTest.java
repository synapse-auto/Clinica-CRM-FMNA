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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.clinic.slug=admin-test",
        "app.security.jwt.secret=test-only-secret-key-with-at-least-32-bytes",
        "app.security.jwt.expiration-ms=86400000",
        "app.initial-users.enabled=false"
})
@Transactional
class AdminUsuarioSecurityIntegrationTest {

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

    private Clinica clinica;
    private String adminEmail;
    private String gestorComumEmail;
    private String recepcionistaEmail;
    private String medicoEmail;
    private String senha;

    @BeforeEach
    void setUp() {
        clinica = new Clinica();
        clinica.setNome("Clinica Admin");
        clinica.setSlug("admin-test");
        clinica.setRazaoSocial("Clinica Admin LTDA");
        clinica.setCnpj("33.333.333/0001-33");
        clinica.setEmailContato("admin@clinica.local");
        clinica.setTelefoneContato("44999999999");
        clinica = clinicaRepository.save(clinica);

        senha = UUID.randomUUID() + "!Aa1";
        adminEmail = "admin-" + UUID.randomUUID() + "@clinica.local";
        gestorComumEmail = "gestor-" + UUID.randomUUID() + "@clinica.local";
        recepcionistaEmail = "recepcao-" + UUID.randomUUID() + "@clinica.local";
        medicoEmail = "medico-" + UUID.randomUUID() + "@clinica.local";

        saveUser(new Gestor(), clinica, "Admin Lucas", adminEmail, false, true); // podeGerenciarUsuarios = true
        saveUser(new Gestor(), clinica, "Gestor Comum", gestorComumEmail, false, false); // podeGerenciarUsuarios = false
        saveUser(new Recepcionista(), clinica, "Recepcao Real", recepcionistaEmail, false, false);
        saveUser(new Medico(), clinica, "Medico Real", medicoEmail, false, false);

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void authorized_admin_should_list_and_create_users() throws Exception {
        String token = login(adminEmail);

        // Listar
        mockMvc.perform(get("/api/admin/usuarios")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.[?(@.email == '%s')]".formatted(adminEmail)).exists())
                .andExpect(jsonPath("$.[?(@.email == '%s')]".formatted(gestorComumEmail)).exists());

        // Criar
        String novoEmail = "novo.user-" + UUID.randomUUID() + "@clinica.local";
        mockMvc.perform(post("/api/admin/usuarios")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Medico Novo",
                                  "email":"%s",
                                  "perfil":"MEDICO",
                                  "senhaTemporaria":"Senha@123"
                                }
                                """.formatted(novoEmail)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.perfil").value("MEDICO"))
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.senhaHash").doesNotExist())
                .andExpect(jsonPath("$.senhaTemporaria").doesNotExist());

        login(novoEmail, "Senha@123");

        String gestorEmail = "gestor-criado-" + UUID.randomUUID() + "@clinica.local";
        mockMvc.perform(post("/api/admin/usuarios")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Gestor sem elevacao",
                                  "email":"%s",
                                  "perfil":"GESTOR",
                                  "senhaTemporaria":"Ultra#2026",
                                  "podeGerenciarUsuarios":true
                                }
                                """.formatted(gestorEmail)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.perfil").value("GESTOR"));

        Usuario gestorCriado = usuarioRepository.findByEmail(gestorEmail).orElseThrow();
        assertFalse(gestorCriado.getPodeGerenciarUsuarios());
        assertTrue(gestorCriado.getMustChangePassword());
        assertTrue(gestorCriado.getClinica().getId().equals(clinica.getId()));
    }

    @Test
    void common_gestor_should_be_forbidden() throws Exception {
        String token = login(gestorComumEmail);

        mockMvc.perform(get("/api/admin/usuarios")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/usuarios")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Bloqueado",
                                  "email":"bloq@clinica.local",
                                  "perfil":"RECEPCIONISTA",
                                  "senhaTemporaria":"Atendente1"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void receptionist_should_be_forbidden() throws Exception {
        String token = login(recepcionistaEmail);

        mockMvc.perform(get("/api/admin/usuarios")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void doctor_should_be_forbidden() throws Exception {
        String token = login(medicoEmail);

        mockMvc.perform(get("/api/admin/usuarios")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_should_not_access_user_from_another_clinic() throws Exception {
        Clinica outraClinica = new Clinica();
        outraClinica.setNome("Outra Clinica");
        outraClinica.setSlug("outra-" + UUID.randomUUID());
        outraClinica.setRazaoSocial("Outra Clinica LTDA");
        outraClinica.setCnpj("44.444.444/0001-44");
        outraClinica.setEmailContato("outra-" + UUID.randomUUID() + "@clinica.local");
        outraClinica.setTelefoneContato("44888888888");
        outraClinica = clinicaRepository.save(outraClinica);

        String outroEmail = "outro-" + UUID.randomUUID() + "@clinica.local";
        saveUser(new Recepcionista(), outraClinica, "Outro Usuario", outroEmail, false, false);
        Usuario outroUsuario = usuarioRepository.findByEmail(outroEmail).orElseThrow();
        String token = login(adminEmail);

        mockMvc.perform(patch("/api/admin/usuarios/%d/status".formatted(outroUsuario.getId()))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ativo\": false}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/admin/usuarios/%d/resetar-senha".formatted(outroUsuario.getId()))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaTemporaria\": \"Senha@123\"}"))
                .andExpect(status().isNotFound());

        assertTrue(usuarioRepository.findById(outroUsuario.getId()).orElseThrow().getAtivo());
    }

    @Test
    void admin_should_alter_status_and_reset_password() throws Exception {
        String token = login(adminEmail);

        // Criar usuário para testar
        Usuario recepcionista = usuarioRepository.findByEmail(recepcionistaEmail).orElseThrow();
        assertTrue(recepcionista.getAtivo());

        // Desativar
        mockMvc.perform(patch("/api/admin/usuarios/%d/status".formatted(recepcionista.getId()))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ativo\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));

        Usuario updated = usuarioRepository.findById(recepcionista.getId()).orElseThrow();
        assertFalse(updated.getAtivo());

        // Resetar Senha
        mockMvc.perform(patch("/api/admin/usuarios/%d/resetar-senha".formatted(recepcionista.getId()))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaTemporaria\": \"NovaSenha12\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        Usuario reseted = usuarioRepository.findById(recepcionista.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches("NovaSenha12", reseted.getSenhaHash()));
    }

    @Test
    void admin_should_reset_special_password_and_user_should_login_with_exact_value() throws Exception {
        String token = login(adminEmail);
        Usuario medico = usuarioRepository.findByEmail(medicoEmail).orElseThrow();

        mockMvc.perform(patch("/api/admin/usuarios/%d/resetar-senha".formatted(medico.getId()))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaTemporaria\": \"Acesso!123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        login(medicoEmail, "Acesso!123");
    }

    @Test
    void admin_cannot_deactivate_self() throws Exception {
        String token = login(adminEmail);
        Usuario admin = usuarioRepository.findByEmail(adminEmail).orElseThrow();

        mockMvc.perform(patch("/api/admin/usuarios/%d/status".formatted(admin.getId()))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ativo\": false}"))
                .andExpect(status().isBadRequest());
    }

    private String login(String email) throws Exception {
        return login(email, senha);
    }

    private String login(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","senha":"%s"}
                                """.formatted(email, password)))
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
            boolean podeGerenciarUsuarios
    ) {
        usuario.setClinica(clinica);
        usuario.setNome(nome);
        usuario.setEmail(email);
        usuario.setSenhaHash(passwordEncoder.encode(senha));
        usuario.setMustChangePassword(mustChangePassword);
        usuario.setAtivo(true);
        usuario.setPodeGerenciarUsuarios(podeGerenciarUsuarios);
        usuario.setAdminInterno(false);
        usuarioRepository.save(usuario);
    }
}
