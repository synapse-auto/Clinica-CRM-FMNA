package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Medico;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import com.synapse.clinicafemina.repository.AgendamentoRepository;
import com.synapse.clinicafemina.repository.ClinicaRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.clinic.slug=fmna"
})
@Transactional
@WithMockUser(roles = "GESTOR")
class AgendamentoPersistenceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClinicaRepository clinicaRepository;

    @Autowired
    private PacienteRepository pacienteRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AgendamentoRepository agendamentoRepository;

    private Paciente paciente;
    private Medico medico;

    @BeforeEach
    void setUp() {
        Clinica clinica = new Clinica();
        clinica.setNome("Clínica de teste");
        clinica.setSlug("fmna");
        clinica.setRazaoSocial("Clínica de teste LTDA");
        clinica.setCnpj("00.000.000/0001-00");
        clinica.setEmailContato("teste@clinica.local");
        clinica.setTelefoneContato("44999999999");
        clinica = clinicaRepository.save(clinica);

        paciente = new Paciente();
        paciente.setClinica(clinica);
        paciente.setNome("Maria da Silva");
        paciente.setNomeBusca("MARIA DA SILVA");
        paciente.setTelefone("44999999999");
        paciente.setTelefoneNormalizado("+5544999999999");
        paciente.setExternalSource(ExternalProviderType.MANUAL);
        paciente.setExternalId("test-paciente-" + UUID.randomUUID());
        paciente = pacienteRepository.save(paciente);

        medico = new Medico();
        medico.setClinica(clinica);
        medico.setNome("Dra. Renata");
        medico.setEmail("renata-" + UUID.randomUUID() + "@clinica.local");
        medico.setSenhaHash(new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString()));
        medico = (Medico) usuarioRepository.save(medico);
    }

    @Test
    void should_persist_reload_and_cancel_appointment_without_deleting_it() throws Exception {
        String body = mockMvc.perform(post("/api/agendamentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pacienteId": %d,
                                  "medicoId": %d,
                                  "dataHoraInicio": "2026-06-22T09:00:00-03:00",
                                  "dataHoraFim": "2026-06-22T09:30:00-03:00",
                                  "tipo": "CONSULTA",
                                  "servicoNome": "Pré-natal"
                                }
                                """.formatted(paciente.getId(), medico.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AGENDADO"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long id = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(body)
                .get("id")
                .asLong();

        mockMvc.perform(get("/api/agendamentos")
                        .param("inicio", "2026-06-22T00:00:00-03:00")
                        .param("fim", "2026-06-27T00:00:00-03:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].pacienteNome").value("Maria da Silva"));

        mockMvc.perform(patch("/api/agendamentos/{id}/cancelamento", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"motivo": "Paciente solicitou remarcação"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"));

        assertEquals(1, agendamentoRepository.count());
        var persisted = agendamentoRepository.findById(id).orElseThrow();
        assertEquals("CANCELADO", persisted.getStatus());
        assertNotNull(persisted.getCanceladoEm());
        assertEquals("Paciente solicitou remarcação", persisted.getMotivoCancelamento());
    }
}
