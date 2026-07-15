package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.IntegrationSyncLog;
import com.synapse.clinicafemina.integration.external.ExternalAppointmentDTO;
import com.synapse.clinicafemina.integration.external.ExternalClinicProvider;
import com.synapse.clinicafemina.integration.external.ExternalPatientDTO;
import com.synapse.clinicafemina.integration.external.ExternalProviderFactory;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import com.synapse.clinicafemina.integration.external.PageResult;
import com.synapse.clinicafemina.repository.AgendamentoRepository;
import com.synapse.clinicafemina.repository.ClinicaRepository;
import com.synapse.clinicafemina.repository.IntegrationSyncLogRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.clinic.slug=sync-transaction-test"
})
class ExternalSyncTransactionIntegrationTest {

    private static final LocalDate DATA_INICIO = LocalDate.of(2026, 7, 15);
    private static final LocalDate DATA_FIM = LocalDate.of(2026, 7, 15);

    @Autowired
    private ExternalSyncService externalSyncService;

    @Autowired
    private ClinicaRepository clinicaRepository;

    @Autowired
    private PacienteRepository pacienteRepository;

    @Autowired
    private AgendamentoRepository agendamentoRepository;

    @Autowired
    private IntegrationSyncLogRepository syncLogRepository;

    @MockBean
    private ExternalProviderFactory providerFactory;

    private ExternalClinicProvider provider;

    @BeforeEach
    void setUp() {
        reset(providerFactory);
        provider = mock(ExternalClinicProvider.class);
        when(providerFactory.getProvider(ExternalProviderType.MEDWARE)).thenReturn(provider);
        when(provider.getType()).thenReturn(ExternalProviderType.MEDWARE);
        when(provider.getPatientNotes(anyString(), isNull(), eq(100)))
                .thenReturn(new PageResult<>(List.of(), false, null));
    }

    @Test
    void should_rollback_synced_data_and_persist_failure_log_when_constraint_fails() {
        Clinica clinica = criarClinica("rollback");
        ExternalPatientDTO patient = pacienteFicticio("patient-rollback");
        ExternalAppointmentDTO invalidAppointment = agendamentoFicticio(
                "appointment-rollback",
                patient.externalId(),
                "S".repeat(121)
        );
        configurarProvider(patient, invalidAppointment);

        ExternalSyncResult result = externalSyncService.sincronizar(
                clinica, DATA_INICIO, DATA_FIM);

        assertEquals("FALHA_TOTAL", result.status());
        assertTrue(pacienteRepository.findByClinicaIdAndExternalSourceAndExternalId(
                clinica.getId(), ExternalProviderType.MEDWARE, patient.externalId()).isEmpty());
        assertTrue(agendamentoRepository.findByClinicaIdAndExternalSourceAndExternalId(
                clinica.getId(), ExternalProviderType.MEDWARE, invalidAppointment.externalId()).isEmpty());

        IntegrationSyncLog runLog = ultimoLog(clinica);
        assertNotNull(runLog.getId());
        assertEquals("FALHA_TOTAL", runLog.getStatus());
        assertTrue(runLog.getMensagemErro().contains("AGENDAMENTO_PERSIST"));
        assertFalse(runLog.getMensagemErro().contains("Paciente Ficticio"));
        assertFalse(runLog.getMensagemErro().contains("11999998888"));
    }

    @Test
    void should_commit_patients_appointments_and_success_log_when_data_is_valid() {
        Clinica clinica = criarClinica("success");
        ExternalPatientDTO patient = pacienteFicticio("patient-success");
        ExternalAppointmentDTO appointment = agendamentoFicticio(
                "appointment-success",
                patient.externalId(),
                "Procedimento ficticio"
        );
        configurarProvider(patient, appointment);

        ExternalSyncResult result = externalSyncService.sincronizar(
                clinica, DATA_INICIO, DATA_FIM);

        assertEquals("SUCESSO", result.status());
        assertTrue(pacienteRepository.findByClinicaIdAndExternalSourceAndExternalId(
                clinica.getId(), ExternalProviderType.MEDWARE, patient.externalId()).isPresent());
        assertTrue(agendamentoRepository.findByClinicaIdAndExternalSourceAndExternalId(
                clinica.getId(), ExternalProviderType.MEDWARE, appointment.externalId()).isPresent());

        IntegrationSyncLog runLog = ultimoLog(clinica);
        assertEquals("SUCESSO", runLog.getStatus());
        assertEquals(1, runLog.getPacientesProcessados());
        assertEquals(1, runLog.getAgendamentosProcessados());
        assertNull(runLog.getMensagemErro());
    }

    private void configurarProvider(
            ExternalPatientDTO patient,
            ExternalAppointmentDTO appointment
    ) {
        when(provider.getPatients(isNull(), isNull(), eq(100)))
                .thenReturn(new PageResult<>(List.of(patient), false, null));
        when(provider.getAppointments(isNull(), eq(DATA_INICIO), eq(DATA_FIM), isNull(), eq(100)))
                .thenReturn(new PageResult<>(List.of(appointment), false, null));
    }

    private Clinica criarClinica(String suffix) {
        String unique = UUID.randomUUID().toString();
        Clinica clinica = new Clinica();
        clinica.setNome("Clinica ficticia " + suffix);
        clinica.setSlug("sync-" + suffix + "-" + unique);
        clinica.setRazaoSocial("Clinica ficticia de testes");
        clinica.setCnpj(unique.substring(0, 18));
        clinica.setEmailContato("teste-" + unique + "@example.test");
        clinica.setTelefoneContato("11000000000");
        clinica.setExternalProvider(ExternalProviderType.MEDWARE);
        return clinicaRepository.save(clinica);
    }

    private ExternalPatientDTO pacienteFicticio(String externalId) {
        return new ExternalPatientDTO(
                externalId,
                "Paciente Ficticio",
                null,
                null,
                "11999998888",
                "1990-01-01",
                null,
                Map.of("source", "fixture")
        );
    }

    private ExternalAppointmentDTO agendamentoFicticio(
            String externalId,
            String externalPatientId,
            String serviceName
    ) {
        return new ExternalAppointmentDTO(
                externalId,
                externalPatientId,
                OffsetDateTime.parse("2026-07-15T09:00:00-03:00"),
                OffsetDateTime.parse("2026-07-15T09:15:00-03:00"),
                "EXAME",
                serviceName,
                "AGENDADO",
                null,
                null,
                null,
                Map.of("source", "fixture")
        );
    }

    private IntegrationSyncLog ultimoLog(Clinica clinica) {
        return syncLogRepository.findTopByClinicaIdAndExternalProviderOrderByIniciadoEmDesc(
                clinica.getId(), ExternalProviderType.MEDWARE).orElseThrow();
    }
}
