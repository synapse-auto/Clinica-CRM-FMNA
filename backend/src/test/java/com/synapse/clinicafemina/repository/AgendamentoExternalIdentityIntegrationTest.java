package com.synapse.clinicafemina.repository;

import com.synapse.clinicafemina.domain.Agendamento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.clinic.slug=external-appointment-identity-test"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
class AgendamentoExternalIdentityIntegrationTest {

    private static final OffsetDateTime START_AT =
            OffsetDateTime.parse("2026-07-15T09:00:00-03:00");

    @Autowired
    private AgendamentoRepository agendamentoRepository;

    @Autowired
    private ClinicaRepository clinicaRepository;

    @Autowired
    private PacienteRepository pacienteRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    void configureCompositeExternalIdentityForH2() {
        jdbcTemplate.execute(
                "ALTER TABLE agendamento ALTER COLUMN external_source DROP NOT NULL");
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS ux_agendamento_clinica_source_external_id
                ON agendamento (clinica_id, external_source, external_id)
                """);
    }

    @Test
    void should_block_duplicate_when_composite_external_identity_is_repeated() {
        Clinica clinica = criarClinica("duplicada");
        Paciente paciente = criarPaciente(clinica, "paciente-duplicada");
        agendamentoRepository.saveAndFlush(criarAgendamento(
                clinica, paciente, ExternalProviderType.MEDWARE, "agenda-duplicada", "INTEGRACAO_EXTERNA"));

        Agendamento duplicado = criarAgendamento(
                clinica, paciente, ExternalProviderType.MEDWARE, "agenda-duplicada", "INTEGRACAO_EXTERNA");

        assertThrows(
                DataIntegrityViolationException.class,
                () -> agendamentoRepository.saveAndFlush(duplicado));
    }

    @Test
    void should_allow_same_external_id_for_different_providers_in_same_clinic() {
        Clinica clinica = criarClinica("providers");
        Paciente paciente = criarPaciente(clinica, "paciente-providers");
        String externalId = "agenda-provider-compartilhada";

        agendamentoRepository.save(criarAgendamento(
                clinica, paciente, ExternalProviderType.MEDWARE, externalId, "INTEGRACAO_EXTERNA"));
        agendamentoRepository.saveAndFlush(criarAgendamento(
                clinica, paciente, ExternalProviderType.DARWIN, externalId, "INTEGRACAO_EXTERNA"));

        assertTrue(agendamentoRepository.findByClinicaIdAndExternalSourceAndExternalId(
                clinica.getId(), ExternalProviderType.MEDWARE, externalId).isPresent());
        assertTrue(agendamentoRepository.findByClinicaIdAndExternalSourceAndExternalId(
                clinica.getId(), ExternalProviderType.DARWIN, externalId).isPresent());
    }

    @Test
    void should_allow_same_external_id_for_same_provider_in_different_clinics() {
        Clinica primeiraClinica = criarClinica("clinica-a");
        Clinica segundaClinica = criarClinica("clinica-b");
        Paciente primeiroPaciente = criarPaciente(primeiraClinica, "paciente-clinica-a");
        Paciente segundoPaciente = criarPaciente(segundaClinica, "paciente-clinica-b");
        String externalId = "agenda-entre-clinicas";

        agendamentoRepository.save(criarAgendamento(
                primeiraClinica, primeiroPaciente, ExternalProviderType.MEDWARE,
                externalId, "INTEGRACAO_EXTERNA"));
        agendamentoRepository.saveAndFlush(criarAgendamento(
                segundaClinica, segundoPaciente, ExternalProviderType.MEDWARE,
                externalId, "INTEGRACAO_EXTERNA"));

        assertTrue(agendamentoRepository.findByClinicaIdAndExternalSourceAndExternalId(
                primeiraClinica.getId(), ExternalProviderType.MEDWARE, externalId).isPresent());
        assertTrue(agendamentoRepository.findByClinicaIdAndExternalSourceAndExternalId(
                segundaClinica.getId(), ExternalProviderType.MEDWARE, externalId).isPresent());
    }

    @Test
    void should_preserve_legacy_null_source_when_medware_uses_same_external_id() {
        Clinica clinica = criarClinica("legado");
        Paciente paciente = criarPaciente(clinica, "paciente-legado");
        String externalId = "agenda-legada-compartilhada";
        inserirAgendamentoLegado(clinica, paciente, externalId);

        agendamentoRepository.saveAndFlush(criarAgendamento(
                clinica, paciente, ExternalProviderType.MEDWARE, externalId, "INTEGRACAO_EXTERNA"));

        Integer legacyCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM agendamento
                WHERE clinica_id = ? AND external_source IS NULL AND external_id = ?
                """, Integer.class, clinica.getId(), externalId);
        Integer totalCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM agendamento
                WHERE clinica_id = ? AND external_id = ?
                """, Integer.class, clinica.getId(), externalId);

        assertEquals(1, legacyCount);
        assertEquals(2, totalCount);
        assertTrue(agendamentoRepository.findByClinicaIdAndExternalSourceAndExternalId(
                clinica.getId(), ExternalProviderType.MEDWARE, externalId).isPresent());
    }

    @Test
    void should_keep_manual_and_medware_appointments_separate_when_ids_match() {
        Clinica clinica = criarClinica("manual-medware");
        Paciente paciente = criarPaciente(clinica, "paciente-manual-medware");
        String externalId = "agenda-tecnica-compartilhada";

        agendamentoRepository.save(criarAgendamento(
                clinica, paciente, ExternalProviderType.MANUAL, externalId, "MANUAL"));
        agendamentoRepository.saveAndFlush(criarAgendamento(
                clinica, paciente, ExternalProviderType.MEDWARE, externalId, "INTEGRACAO_EXTERNA"));

        Agendamento manual = agendamentoRepository
                .findByClinicaIdAndExternalSourceAndExternalId(
                        clinica.getId(), ExternalProviderType.MANUAL, externalId)
                .orElseThrow();
        Agendamento medware = agendamentoRepository
                .findByClinicaIdAndExternalSourceAndExternalId(
                        clinica.getId(), ExternalProviderType.MEDWARE, externalId)
                .orElseThrow();
        assertEquals("MANUAL", manual.getOrigem());
        assertEquals("INTEGRACAO_EXTERNA", medware.getOrigem());
    }

    private void inserirAgendamentoLegado(Clinica clinica, Paciente paciente, String externalId) {
        jdbcTemplate.update("""
                INSERT INTO agendamento (
                    clinica_id, paciente_id, external_source, external_id,
                    data_hora_inicio, status, origem, criado_em, atualizado_em
                ) VALUES (?, ?, NULL, ?, ?, 'AGENDADO', 'INTEGRACAO_EXTERNA', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, clinica.getId(), paciente.getId(), externalId, START_AT);
    }

    private Clinica criarClinica(String suffix) {
        String unique = UUID.randomUUID().toString();
        Clinica clinica = new Clinica();
        clinica.setNome("Clinica ficticia " + suffix);
        clinica.setSlug("identity-" + suffix + "-" + unique);
        clinica.setRazaoSocial("Clinica ficticia de testes");
        clinica.setCnpj(unique.substring(0, 18));
        clinica.setEmailContato("teste-" + unique + "@example.test");
        clinica.setTelefoneContato("11000000000");
        clinica.setExternalProvider(ExternalProviderType.MEDWARE);
        return clinicaRepository.save(clinica);
    }

    private Paciente criarPaciente(Clinica clinica, String externalId) {
        Paciente paciente = new Paciente();
        paciente.setClinica(clinica);
        paciente.setNome("Paciente ficticio");
        paciente.setNomeBusca("PACIENTE FICTICIO");
        paciente.setTelefone("11000000000");
        paciente.setTelefoneNormalizado("+55" + clinica.getId() + externalId.hashCode());
        paciente.setExternalSource(ExternalProviderType.MEDWARE);
        paciente.setExternalId(externalId);
        return pacienteRepository.save(paciente);
    }

    private Agendamento criarAgendamento(
            Clinica clinica,
            Paciente paciente,
            ExternalProviderType provider,
            String externalId,
            String origem
    ) {
        Agendamento agendamento = new Agendamento();
        agendamento.setClinica(clinica);
        agendamento.setPaciente(paciente);
        agendamento.setExternalSource(provider);
        agendamento.setExternalId(externalId);
        agendamento.setDataHoraInicio(START_AT);
        agendamento.setDataHoraFim(START_AT.plusMinutes(15));
        agendamento.setTipo("EXAME");
        agendamento.setServicoNome("Procedimento ficticio");
        agendamento.setStatus("AGENDADO");
        agendamento.setOrigem(origem);
        return agendamento;
    }
}
