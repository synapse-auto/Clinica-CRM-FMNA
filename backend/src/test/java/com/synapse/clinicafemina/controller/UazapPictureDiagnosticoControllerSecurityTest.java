package com.synapse.clinicafemina.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Gestor;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.domain.Recepcionista;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.repository.ClinicaRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Segurança do endpoint de diagnóstico UAZAP: autenticação, perfil GESTOR, adminInterno=true e
 * isolamento por clínica — mesmo padrão de {@code EquipeSecurityIntegrationTest}. A flag é
 * habilitada via {@code @TestPropertySource} apenas nesta classe (padrão de produção continua
 * {@code false}); o endpoint tenta uma chamada real à UAZAP contra uma porta que recusa conexão
 * de propósito, para exercitar o caminho de falha sanitizada sem depender de rede externa.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.clinic.slug=uazap-picture-diag-test",
        "app.security.jwt.secret=test-only-secret-key-with-at-least-32-bytes",
        "app.security.jwt.expiration-ms=86400000",
        "app.initial-users.enabled=false",
        "app.whatsapp.provider=UAZAP",
        "app.whatsapp.uazap.base-url=http://127.0.0.1:1",
        "app.whatsapp.uazap.username=test",
        "app.whatsapp.uazap.version=v1",
        "app.whatsapp.uazap.phone-number-id=test-instance",
        "app.whatsapp.uazap.token=test-token",
        "app.whatsapp.uazap.connect-timeout-ms=300",
        "app.whatsapp.uazap.read-timeout-ms=300",
        "app.whatsapp.uazap.picture-diagnostics-enabled=true"
})
@Transactional
class UazapPictureDiagnosticoControllerSecurityTest {

    private static final String ENDPOINT = "/api/admin/integracoes/uazap/foto/diagnostico";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClinicaRepository clinicaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PacienteRepository pacienteRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    private String senha;
    private String gestorComumEmail;
    private String recepcionistaEmail;
    private String adminInternoEmail;
    private Long pacienteId;
    private Long pacienteOutraClinicaId;

    @BeforeEach
    void setUp() {
        Clinica clinica = new Clinica();
        clinica.setNome("Clinica diagnostico");
        clinica.setSlug("uazap-picture-diag-test");
        clinica.setRazaoSocial("Clinica diagnostico LTDA");
        clinica.setCnpj("11.111.111/0001-11");
        clinica.setEmailContato("diag@clinica.local");
        clinica.setTelefoneContato("44999999999");
        clinica = clinicaRepository.save(clinica);

        Clinica outraClinica = new Clinica();
        outraClinica.setNome("Outra clinica");
        outraClinica.setSlug("outra-" + UUID.randomUUID());
        outraClinica.setRazaoSocial("Outra clinica LTDA");
        outraClinica.setCnpj("22.222.222/0001-22");
        outraClinica.setEmailContato("outra@clinica.local");
        outraClinica.setTelefoneContato("44988888888");
        outraClinica = clinicaRepository.save(outraClinica);

        senha = UUID.randomUUID() + "!Aa1";
        gestorComumEmail = "gestor-comum-" + UUID.randomUUID() + "@clinica.local";
        recepcionistaEmail = "recepcao-" + UUID.randomUUID() + "@clinica.local";
        adminInternoEmail = "admin-interno-" + UUID.randomUUID() + "@clinica.local";

        saveUser(new Gestor(), clinica, gestorComumEmail, false);
        saveUser(new Recepcionista(), clinica, recepcionistaEmail, false);
        saveUser(new Gestor(), clinica, adminInternoEmail, true);

        pacienteId = savePaciente(clinica, "Paciente Diagnostico", "5511999990000").getId();
        pacienteOutraClinicaId = savePaciente(outraClinica, "Paciente Outra Clinica", "5511988880000").getId();

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void unauthenticated_request_isRejected() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pacienteId\":%d}".formatted(pacienteId)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void receptionist_isRejected() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .header("Authorization", "Bearer " + login(recepcionistaEmail))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pacienteId\":%d}".formatted(pacienteId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void gestorWithoutAdminInterno_isRejected() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .header("Authorization", "Bearer " + login(gestorComumEmail))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pacienteId\":%d}".formatted(pacienteId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminInterno_cannotDiagnoseCrossClinicPatientAndResponseIsSanitized() throws Exception {
        String token = login(adminInternoEmail);

        mockMvc.perform(post(ENDPOINT)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pacienteId\":%d}".formatted(pacienteOutraClinicaId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminInterno_ownClinicPatient_returnsSanitizedResponseOnly() throws Exception {
        String token = login(adminInternoEmail);

        String response = mockMvc.perform(post(ENDPOINT)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pacienteId\":%d}".formatted(pacienteId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        assertFalse(json.has("fotoUrl"), "resposta nunca deve conter a URL completa da foto");
        assertFalse(json.has("token"), "resposta nunca deve conter token");
        assertFalse(json.has("telefone"), "resposta nunca deve conter telefone");
        assertFalse(json.has("nome"), "resposta nunca deve conter nome");
        assertFalse(json.toString().contains("5511999990000"), "resposta nunca deve conter o telefone completo");
        assertFalse(json.toString().contains("Paciente Diagnostico"), "resposta nunca deve conter o nome do paciente");
        // Sem rede real disponível: a chamada à UAZAP falha (conexão recusada) — o motivo sanitizado é reportado.
        assertFalse(json.get("fotoPersistida").asBoolean());
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

    private Usuario saveUser(Usuario usuario, Clinica clinica, String email, boolean adminInterno) {
        usuario.setClinica(clinica);
        usuario.setNome("Usuario Teste");
        usuario.setEmail(email);
        usuario.setSenhaHash(passwordEncoder.encode(senha));
        usuario.setMustChangePassword(false);
        usuario.setAdminInterno(adminInterno);
        usuario.setPodeGerenciarUsuarios(false);
        usuario.setAtivo(true);
        return usuarioRepository.save(usuario);
    }

    private Paciente savePaciente(Clinica clinica, String nome, String telefoneNormalizado) {
        Paciente paciente = new Paciente();
        paciente.setClinica(clinica);
        paciente.setNome(nome);
        paciente.setNomeBusca(nome.toUpperCase());
        paciente.setTelefone("+" + telefoneNormalizado);
        paciente.setTelefoneNormalizado(telefoneNormalizado);
        paciente.setStatus("EM_ATENDIMENTO");
        return pacienteRepository.save(paciente);
    }
}
