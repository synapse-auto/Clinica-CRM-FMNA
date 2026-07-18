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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    private String gestorComumEmail;
    private String recepcionistaEmail;
    private String medicoEmail;
    private String senha;
    private Long gestorId;
    private Long gestorComumId;
    private Long recepcionistaId;
    private Long medicoId;
    private Long internoId;

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
        gestorComumEmail = "gestor-comum-" + UUID.randomUUID() + "@clinica.local";
        recepcionistaEmail = "recepcao-" + UUID.randomUUID() + "@clinica.local";
        medicoEmail = "medico-" + UUID.randomUUID() + "@clinica.local";

        gestorId = saveUser(new Gestor(), clinica, "Gestora Real", gestorEmail, false, false, true).getId();
        gestorComumId = saveUser(new Gestor(), clinica, "Gestor Comum", gestorComumEmail, false, false, false).getId();
        recepcionistaId = saveUser(new Recepcionista(), clinica, "Recepcao Real", recepcionistaEmail, false, false, false).getId();
        medicoId = saveUser(new Medico(), clinica, "Medico Real", medicoEmail, false, false, false).getId();
        internoId = saveUser(new Gestor(), clinica, "Admin Interno", "interno-" + UUID.randomUUID() + "@clinica.local", false, true, false).getId();
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
                .andExpect(jsonPath("$.grupos[0].usuarios[?(@.nome == 'Gestora Real')]").exists())
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
                                  "senhaTemporaria":"Atendente1"
                                }
                                """.formatted(novoEmail)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(novoEmail))
                .andExpect(jsonPath("$.perfil").value("RECEPCIONISTA"))
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        Usuario usuarioCriado = usuarioRepository.findByEmail(novoEmail).orElseThrow();
        assertTrue(usuarioCriado.getMustChangePassword());
        assertTrue(passwordEncoder.matches("Atendente1", usuarioCriado.getSenhaHash()));
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
                                  "senhaTemporaria":"Medico1"
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
                  "senhaTemporaria":"Atendente1"
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

    @Test
    void common_gestor_should_not_access_team_management() throws Exception {
        String token = login(gestorComumEmail);

        mockMvc.perform(get("/api/equipe")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/equipe/usuarios")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Usuario Bloqueado",
                                  "email":"bloqueado-equipe@clinica.local",
                                  "perfil":"RECEPCIONISTA",
                                  "senhaTemporaria":"Atendente1"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void authorized_manager_should_grant_permission_and_existing_session_should_be_recognized_immediately() throws Exception {
        String authorizedToken = login(gestorEmail);
        String commonManagerToken = login(gestorComumEmail);

        mockMvc.perform(patch("/api/equipe/usuarios/{id}/permissao-gerenciamento", gestorComumId)
                        .header("Authorization", "Bearer " + authorizedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"podeGerenciarUsuarios":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(gestorComumId))
                .andExpect(jsonPath("$.podeGerenciarUsuarios").value(true))
                .andExpect(jsonPath("$.senhaHash").doesNotExist());

        entityManager.flush();
        entityManager.clear();
        assertTrue(usuarioRepository.findById(gestorComumId).orElseThrow().getPodeGerenciarUsuarios());

        mockMvc.perform(get("/api/equipe")
                        .header("Authorization", "Bearer " + commonManagerToken))
                .andExpect(status().isOk());
    }

    @Test
    void authorized_manager_should_revoke_permission_when_another_manager_remains() throws Exception {
        Usuario commonManager = usuarioRepository.findById(gestorComumId).orElseThrow();
        commonManager.setPodeGerenciarUsuarios(true);
        usuarioRepository.saveAndFlush(commonManager);
        entityManager.clear();

        mockMvc.perform(patch("/api/equipe/usuarios/{id}/permissao-gerenciamento", gestorComumId)
                        .header("Authorization", "Bearer " + login(gestorEmail))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"podeGerenciarUsuarios":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.podeGerenciarUsuarios").value(false));
    }

    @Test
    void permission_endpoint_should_reject_invalid_targets_without_cross_clinic_disclosure() throws Exception {
        String token = login(gestorEmail);

        mockMvc.perform(patch("/api/equipe/usuarios/{id}/permissao-gerenciamento", medicoId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"podeGerenciarUsuarios\":true}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/equipe/usuarios/{id}/permissao-gerenciamento", recepcionistaId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"podeGerenciarUsuarios\":true}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/equipe/usuarios/{id}/permissao-gerenciamento", internoId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"podeGerenciarUsuarios\":true}"))
                .andExpect(status().isForbidden());

        Clinica otherClinic = new Clinica();
        otherClinic.setNome("Outra clinica");
        otherClinic.setSlug("outra-" + UUID.randomUUID());
        otherClinic.setRazaoSocial("Outra clinica LTDA");
        otherClinic.setCnpj("33.333.333/0001-33");
        otherClinic.setEmailContato("outra@clinica.test");
        otherClinic.setTelefoneContato("44988888888");
        otherClinic = clinicaRepository.save(otherClinic);
        Long otherUserId = saveUser(
                new Gestor(),
                otherClinic,
                "Gestor Externo",
                "externo-" + UUID.randomUUID() + "@clinica.test",
                false,
                false,
                false
        ).getId();
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(patch("/api/equipe/usuarios/{id}/permissao-gerenciamento", otherUserId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"podeGerenciarUsuarios\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Usuário não encontrado."));
    }

    @Test
    void permission_endpoint_should_reject_inactive_deleted_and_last_authorized_manager() throws Exception {
        String token = login(gestorEmail);

        Usuario inactive = usuarioRepository.findById(gestorComumId).orElseThrow();
        inactive.setAtivo(false);
        usuarioRepository.saveAndFlush(inactive);
        entityManager.clear();
        mockMvc.perform(patch("/api/equipe/usuarios/{id}/permissao-gerenciamento", gestorComumId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"podeGerenciarUsuarios\":true}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/equipe/usuarios/{id}/permissao-gerenciamento", gestorId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"podeGerenciarUsuarios\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("A clínica precisa manter ao menos um gestor com essa permissão."));
    }

    @Test
    void authorized_manager_should_rename_visible_user_and_preserve_other_fields() throws Exception {
        String token = login(gestorEmail);
        Usuario before = usuarioRepository.findById(recepcionistaId).orElseThrow();
        String emailBefore = before.getEmail();
        String passwordBefore = before.getSenhaHash();

        mockMvc.perform(patch("/api/equipe/usuarios/{id}/nome", recepcionistaId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"  Recepção   D'Ávila-Souza  "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Recepção D'Ávila-Souza"))
                .andExpect(jsonPath("$.senhaHash").doesNotExist());

        entityManager.flush();
        entityManager.clear();
        Usuario updated = usuarioRepository.findById(recepcionistaId).orElseThrow();
        assertTrue(updated.getNome().equals("Recepção D'Ávila-Souza"));
        assertTrue(updated.getEmail().equals(emailBefore));
        assertTrue(updated.getSenhaHash().equals(passwordBefore));
    }

    @Test
    void name_endpoint_should_enforce_permission_and_hide_cross_clinic_user() throws Exception {
        mockMvc.perform(patch("/api/equipe/usuarios/{id}/nome", recepcionistaId)
                        .header("Authorization", "Bearer " + login(gestorComumEmail))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Nome Válido\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/equipe/usuarios/{id}/nome", recepcionistaId)
                        .header("Authorization", "Bearer " + login(medicoEmail))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Nome Válido\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/equipe/usuarios/{id}/nome", internoId)
                        .header("Authorization", "Bearer " + login(gestorEmail))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Nome Válido\"}"))
                .andExpect(status().isForbidden());

        Clinica otherClinic = new Clinica();
        otherClinic.setNome("Outra clinica nome");
        otherClinic.setSlug("outra-nome-" + UUID.randomUUID());
        otherClinic.setRazaoSocial("Outra clinica nome LTDA");
        otherClinic.setCnpj("44.444.444/0001-44");
        otherClinic.setEmailContato("outra.nome@clinica.test");
        otherClinic.setTelefoneContato("44977777777");
        otherClinic = clinicaRepository.save(otherClinic);
        Long otherUserId = saveUser(
                new Recepcionista(), otherClinic, "Outro Usuário",
                "outro-nome-" + UUID.randomUUID() + "@clinica.test", false, false, false
        ).getId();
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(patch("/api/equipe/usuarios/{id}/nome", otherUserId)
                        .header("Authorization", "Bearer " + login(gestorEmail))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Nome Válido\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void name_endpoint_should_reject_invalid_values_before_persisting() throws Exception {
        String token = login(gestorEmail);

        mockMvc.perform(patch("/api/equipe/usuarios/{id}/nome", recepcionistaId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"A\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Nome deve ter entre 2 e 200 caracteres."));
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

    private Usuario saveUser(
            Usuario usuario,
            Clinica clinica,
            String nome,
            String email,
            boolean mustChangePassword,
            boolean adminInterno,
            boolean podeGerenciarUsuarios
    ) {
        usuario.setClinica(clinica);
        usuario.setNome(nome);
        usuario.setEmail(email);
        usuario.setSenhaHash(passwordEncoder.encode(senha));
        usuario.setMustChangePassword(mustChangePassword);
        usuario.setAdminInterno(adminInterno);
        usuario.setPodeGerenciarUsuarios(podeGerenciarUsuarios);
        usuario.setAtivo(true);
        return usuarioRepository.save(usuario);
    }
}
