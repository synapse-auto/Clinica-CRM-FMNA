package com.synapse.clinicafemina.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.clinicafemina.domain.Agendamento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Gestor;
import com.synapse.clinicafemina.domain.Medico;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.domain.Recepcionista;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import com.synapse.clinicafemina.repository.AgendamentoRepository;
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

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Segurança real de /api/agenda: autenticação, GESTOR/MEDICO/RECEPCIONISTA (leitura),
 * GESTOR/RECEPCIONISTA (escrita), isolamento por clínica — mesmo padrão de
 * {@code UazapPictureDiagnosticoControllerSecurityTest}. Clínica Medware, para exercitar
 * o caminho 100% local (sem qualquer chamada externa).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.clinic.slug=agenda-security-test",
        "app.security.jwt.secret=test-only-secret-key-with-at-least-32-bytes",
        "app.security.jwt.expiration-ms=86400000",
        "app.initial-users.enabled=false"
})
@Transactional
class AgendaControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClinicaRepository clinicaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PacienteRepository pacienteRepository;

    @Autowired
    private AgendamentoRepository agendamentoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    private String senha;
    private String gestorEmail;
    private String medicoEmail;
    private String recepcionistaEmail;
    private Long agendamentoOutraClinicaId;

    @BeforeEach
    void setUp() {
        Clinica clinica = new Clinica();
        clinica.setNome("Clinica agenda teste");
        clinica.setSlug("agenda-security-test");
        clinica.setRazaoSocial("Clinica agenda teste LTDA");
        clinica.setCnpj("33.333.333/0001-33");
        clinica.setEmailContato("agenda@clinica.local");
        clinica.setTelefoneContato("44977777777");
        clinica.setExternalProvider(ExternalProviderType.MEDWARE);
        clinica = clinicaRepository.save(clinica);

        Clinica outraClinica = new Clinica();
        outraClinica.setNome("Outra clinica agenda");
        outraClinica.setSlug("outra-agenda-" + UUID.randomUUID());
        outraClinica.setRazaoSocial("Outra clinica LTDA");
        outraClinica.setCnpj("44.444.444/0001-44");
        outraClinica.setEmailContato("outra-agenda@clinica.local");
        outraClinica.setTelefoneContato("44966666666");
        outraClinica.setExternalProvider(ExternalProviderType.MEDWARE);
        outraClinica = clinicaRepository.save(outraClinica);

        senha = UUID.randomUUID() + "!Aa1";
        gestorEmail = "gestor-agenda-" + UUID.randomUUID() + "@clinica.local";
        medicoEmail = "medico-agenda-" + UUID.randomUUID() + "@clinica.local";
        recepcionistaEmail = "recepcao-agenda-" + UUID.randomUUID() + "@clinica.local";

        saveUser(new Gestor(), clinica, gestorEmail);
        saveUser(new Medico(), clinica, medicoEmail);
        saveUser(new Recepcionista(), clinica, recepcionistaEmail);

        Paciente pacienteOutraClinica = savePaciente(outraClinica, "Paciente Outra Clinica", "5511988880000");
        Agendamento agendamentoOutraClinica = new Agendamento();
        agendamentoOutraClinica.setClinica(outraClinica);
        agendamentoOutraClinica.setPaciente(pacienteOutraClinica);
        agendamentoOutraClinica.setExternalSource(ExternalProviderType.MANUAL);
        agendamentoOutraClinica.setExternalId("crm-" + UUID.randomUUID());
        agendamentoOutraClinica.setDataHoraInicio(OffsetDateTime.parse("2026-07-20T09:00:00-03:00"));
        agendamentoOutraClinica.setStatus("AGENDADO");
        agendamentoOutraClinicaId = agendamentoRepository.save(agendamentoOutraClinica).getId();

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void unauthenticated_request_isRejected() throws Exception {
        mockMvc.perform(get("/api/agenda")
                        .param("startDate", "2026-07-20T00:00:00-03:00")
                        .param("endDate", "2026-07-21T00:00:00-03:00"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void medico_canReadAgenda() throws Exception {
        mockMvc.perform(get("/api/agenda")
                        .header("Authorization", "Bearer " + login(medicoEmail))
                        .param("startDate", "2026-07-20T00:00:00-03:00")
                        .param("endDate", "2026-07-21T00:00:00-03:00"))
                .andExpect(status().isOk());
    }

    @Test
    void recepcionista_canReadAgenda() throws Exception {
        mockMvc.perform(get("/api/agenda")
                        .header("Authorization", "Bearer " + login(recepcionistaEmail))
                        .param("startDate", "2026-07-20T00:00:00-03:00")
                        .param("endDate", "2026-07-21T00:00:00-03:00"))
                .andExpect(status().isOk());
    }

    @Test
    void medico_cannotCreateAppointment() throws Exception {
        mockMvc.perform(post("/api/agenda")
                        .header("Authorization", "Bearer " + login(medicoEmail))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void recepcionista_canAttemptToCreateAppointment_notForbidden() throws Exception {
        // RECEPCIONISTA tem permissão de escrita — o corpo vazio ainda deve falhar por
        // validação/negócio (400), nunca por autorização (403).
        mockMvc.perform(post("/api/agenda")
                        .header("Authorization", "Bearer " + login(recepcionistaEmail))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertNotEquals(403, status);
                });
    }

    @Test
    void gestor_cannotSeeAppointmentFromAnotherClinic() throws Exception {
        mockMvc.perform(get("/api/agenda/" + agendamentoOutraClinicaId)
                        .header("Authorization", "Bearer " + login(gestorEmail)))
                .andExpect(status().isNotFound());
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

    private Usuario saveUser(Usuario usuario, Clinica clinica, String email) {
        usuario.setClinica(clinica);
        usuario.setNome("Usuario Teste");
        usuario.setEmail(email);
        usuario.setSenhaHash(passwordEncoder.encode(senha));
        usuario.setMustChangePassword(false);
        usuario.setAdminInterno(false);
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
