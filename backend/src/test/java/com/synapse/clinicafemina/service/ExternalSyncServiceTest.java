package com.synapse.clinicafemina.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.clinicafemina.domain.Agendamento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.integration.external.ExternalAppointmentDTO;
import com.synapse.clinicafemina.integration.external.ExternalClinicProvider;
import com.synapse.clinicafemina.integration.external.ExternalClinicalNoteDTO;
import com.synapse.clinicafemina.integration.external.ExternalPatientDTO;
import com.synapse.clinicafemina.integration.external.ExternalProviderFactory;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import com.synapse.clinicafemina.integration.external.PageResult;
import com.synapse.clinicafemina.domain.Medico;
import com.synapse.clinicafemina.repository.AgendamentoRepository;
import com.synapse.clinicafemina.repository.IntegrationSyncLogRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalSyncServiceTest {

    @Mock
    private ExternalProviderFactory providerFactory;

    @Mock
    private ExternalClinicProvider provider;

    @Mock
    private IntegrationSyncLogRepository syncLogRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private AgendamentoRepository agendamentoRepository;

    @Mock
    private com.synapse.clinicafemina.repository.UsuarioRepository usuarioRepository;

    @Mock
    private IntegrationSyncLogService integrationSyncLogService;

    @Mock
    private EntityManager entityManager;

    private ExternalSyncService service;
    private Clinica clinica;

    @BeforeEach
    void setUp() {
        ExternalSyncTransactionService transactionService = new ExternalSyncTransactionService(
                pacienteRepository,
                agendamentoRepository,
                usuarioRepository,
                new ObjectMapper(),
                entityManager,
                100
        );
        service = new ExternalSyncService(
                providerFactory,
                syncLogRepository,
                transactionService,
                integrationSyncLogService
        );

        clinica = new Clinica();
        clinica.setId(7L);
        clinica.setSlug("fmna");
        clinica.setExternalProvider(ExternalProviderType.DARWIN);

        lenient().when(providerFactory.getProvider(ExternalProviderType.DARWIN)).thenReturn(provider);
        lenient().when(providerFactory.getProvider(ExternalProviderType.MEDWARE)).thenReturn(provider);
        lenient().when(syncLogRepository.findUltimoSucesso(7L, ExternalProviderType.DARWIN)).thenReturn(Optional.empty());
        lenient().when(syncLogRepository.findUltimoSucesso(7L, ExternalProviderType.MEDWARE)).thenReturn(Optional.empty());
        lenient().when(integrationSyncLogService.iniciar(any(), any(), any(), any(), any()))
                .thenReturn(99L);
    }

    @Test
    void should_upsert_patient_by_external_source_and_external_id_when_syncing_patients() {
        when(provider.getType()).thenReturn(ExternalProviderType.DARWIN);
        ExternalPatientDTO externalPatient = new ExternalPatientDTO(
                "pat-1",
                "Maria da Silva",
                "123.456.789-00",
                "maria@example.com",
                "+5511999990000",
                "1990-01-10",
                OffsetDateTime.parse("2026-06-01T10:00:00Z"),
                Map.of("id", "pat-1")
        );

        when(provider.getPatients(null, null, 100))
                .thenReturn(new PageResult<>(List.of(externalPatient), false, null));
        when(provider.getAppointments(null, null, 100))
                .thenReturn(new PageResult<>(List.of(), false, null));
        when(provider.getPatientNotes("pat-1", null, 100))
                .thenReturn(new PageResult<>(List.of(), false, null));
        when(pacienteRepository.findByClinicaIdAndExternalSourceAndExternalId(
                7L, ExternalProviderType.DARWIN, "pat-1"))
                .thenReturn(Optional.empty());
        when(pacienteRepository.findByClinicaIdAndCpfHash(eq(7L), any()))
                .thenReturn(Optional.empty());
        when(pacienteRepository.save(any(Paciente.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExternalSyncResult result = service.sincronizar(clinica);

        ArgumentCaptor<Paciente> pacienteCaptor = ArgumentCaptor.forClass(Paciente.class);
        verify(pacienteRepository).save(pacienteCaptor.capture());
        Paciente saved = pacienteCaptor.getValue();
        assertSame(clinica, saved.getClinica());
        assertEquals(ExternalProviderType.DARWIN, saved.getExternalSource());
        assertEquals("pat-1", saved.getExternalId());
        assertEquals("MARIA DA SILVA", saved.getNomeBusca());
        assertEquals(1, result.pacientesCriados());
        assertEquals(0, result.pacientesAtualizados());
    }

    @Test
    void should_persist_medware_patient_when_optional_fields_and_note_payload_are_null() {
        clinica.setSlug("ultramedical");
        clinica.setExternalProvider(ExternalProviderType.MEDWARE);
        when(provider.getType()).thenReturn(ExternalProviderType.MEDWARE);

        Map<String, Object> rawPayload = new LinkedHashMap<>();
        rawPayload.put("campoOpcional", null);
        ExternalPatientDTO externalPatient = new ExternalPatientDTO(
                "1023", null, null, null, null, null, null, rawPayload);
        ExternalClinicalNoteDTO note = new ExternalClinicalNoteDTO(
                "nota-1", "1023", null, null, null);

        when(provider.getPatients(null, null, 100))
                .thenReturn(new PageResult<>(List.of(externalPatient), false, null));
        when(provider.getAppointments(null, null, 100))
                .thenReturn(new PageResult<>(List.of(), false, null));
        when(provider.getPatientNotes("1023", null, 100))
                .thenReturn(new PageResult<>(List.of(note), false, null));
        when(pacienteRepository.findByClinicaIdAndExternalSourceAndExternalId(
                7L, ExternalProviderType.MEDWARE, "1023"))
                .thenReturn(Optional.empty());
        when(pacienteRepository.save(any(Paciente.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExternalSyncResult result = service.sincronizar(clinica);

        ArgumentCaptor<Paciente> pacienteCaptor = ArgumentCaptor.forClass(Paciente.class);
        ArgumentCaptor<ExternalSyncProgress> progressCaptor = ArgumentCaptor.forClass(ExternalSyncProgress.class);
        verify(pacienteRepository).save(pacienteCaptor.capture());
        verify(integrationSyncLogService).finalizar(
                eq(99L), eq("SUCESSO"), progressCaptor.capture(), isNull());

        Paciente saved = pacienteCaptor.getValue();
        ExternalSyncProgress finalProgress = progressCaptor.getValue();
        assertEquals("Paciente sem nome", saved.getNome());
        assertEquals("PACIENTE SEM NOME", saved.getNomeBusca());
        assertEquals("00000000000", saved.getTelefone());
        assertNotNull(saved.getExternalPayload());
        assertTrue(saved.getExternalPayload().contains("\"campoOpcional\":null"));
        assertTrue(saved.getExternalPayload().contains("\"payload\":{}"));
        assertEquals(1, result.pacientesProcessados());
        assertEquals(1, result.pacientesCriados());
        assertEquals(1, finalProgress.getPacientesProcessados());
    }

    @Test
    void should_ignore_external_patient_without_external_id() {
        when(provider.getType()).thenReturn(ExternalProviderType.DARWIN);
        ExternalPatientDTO invalidPatient = new ExternalPatientDTO(
                null, "Paciente Ficticio", null, null, null, null, null, null);

        when(provider.getPatients(null, null, 100))
                .thenReturn(new PageResult<>(List.of(invalidPatient), false, null));
        when(provider.getAppointments(null, null, 100))
                .thenReturn(new PageResult<>(List.of(), false, null));

        ExternalSyncResult result = service.sincronizar(clinica);

        assertEquals("SUCESSO", result.status());
        assertEquals(0, result.pacientesProcessados());
        verifyNoInteractions(pacienteRepository);
    }

    @Test
    void should_sync_appointments_using_internal_patient_when_external_patient_exists() {
        when(provider.getType()).thenReturn(ExternalProviderType.DARWIN);
        Paciente paciente = new Paciente();
        paciente.setId(20L);
        paciente.setClinica(clinica);
        paciente.setExternalSource(ExternalProviderType.DARWIN);
        paciente.setExternalId("pat-1");

        ExternalAppointmentDTO appointment = new ExternalAppointmentDTO(
                "appt-1",
                "pat-1",
                OffsetDateTime.parse("2026-06-15T13:00:00Z"),
                OffsetDateTime.parse("2026-06-15T13:30:00Z"),
                "CONSULTA",
                "Pré-natal",
                "AGENDADO",
                null,
                null,
                null,
                Map.of("id", "appt-1")
        );

        when(provider.getPatients(null, null, 100))
                .thenReturn(new PageResult<>(List.of(), false, null));
        when(provider.getAppointments(null, null, 100))
                .thenReturn(new PageResult<>(List.of(appointment), false, null));
        when(pacienteRepository.findByClinicaIdAndExternalSourceAndExternalId(
                7L, ExternalProviderType.DARWIN, "pat-1"))
                .thenReturn(Optional.of(paciente));
        when(agendamentoRepository.findByClinicaIdAndExternalSourceAndExternalId(
                7L, ExternalProviderType.DARWIN, "appt-1"))
                .thenReturn(Optional.empty());
        when(agendamentoRepository.save(any(Agendamento.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExternalSyncResult result = service.sincronizar(clinica);

        ArgumentCaptor<Agendamento> agendamentoCaptor = ArgumentCaptor.forClass(Agendamento.class);
        verify(agendamentoRepository).save(agendamentoCaptor.capture());
        Agendamento saved = agendamentoCaptor.getValue();
        assertSame(clinica, saved.getClinica());
        assertSame(paciente, saved.getPaciente());
        assertEquals(ExternalProviderType.DARWIN, saved.getExternalSource());
        assertEquals("appt-1", saved.getExternalId());
        assertEquals("Pré-natal", saved.getServicoNome());
        assertEquals(1, result.agendamentosCriados());
        assertTrue(result.agendamentosIgnorados() == 0);
    }

    @Test
    void should_sync_medware_appointments_using_manual_date_window_and_log_period() {
        clinica.setSlug("ultramedical");
        clinica.setExternalProvider(ExternalProviderType.MEDWARE);
        when(provider.getType()).thenReturn(ExternalProviderType.MEDWARE);
        LocalDate dataInicio = LocalDate.of(2026, 7, 1);
        LocalDate dataFim = LocalDate.of(2026, 7, 3);

        Paciente paciente = new Paciente();
        paciente.setId(20L);
        paciente.setClinica(clinica);
        paciente.setExternalSource(ExternalProviderType.MEDWARE);
        paciente.setExternalId("1023");

        ExternalAppointmentDTO appointment = new ExternalAppointmentDTO(
                "98765",
                "1023",
                OffsetDateTime.parse("2026-07-03T17:30:00-03:00"),
                null,
                "EXAME",
                "Ultrassonografia transvaginal",
                "AGENDADO",
                null,
                null,
                null,
                Map.of("codAgendamento", "98765")
        );

        when(provider.getPatients(null, null, 100))
                .thenReturn(new PageResult<>(List.of(), false, null));
        when(provider.getAppointments(null, dataInicio, dataFim, null, 100))
                .thenReturn(new PageResult<>(List.of(appointment), false, null));
        when(pacienteRepository.findByClinicaIdAndExternalSourceAndExternalId(
                7L, ExternalProviderType.MEDWARE, "1023"))
                .thenReturn(Optional.of(paciente));
        when(agendamentoRepository.findByClinicaIdAndExternalSourceAndExternalId(
                7L, ExternalProviderType.MEDWARE, "98765"))
                .thenReturn(Optional.empty());
        when(agendamentoRepository.save(any(Agendamento.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExternalSyncResult result = service.sincronizar(clinica, dataInicio, dataFim);

        ArgumentCaptor<Agendamento> agendamentoCaptor = ArgumentCaptor.forClass(Agendamento.class);
        verify(provider).getAppointments(null, dataInicio, dataFim, null, 100);
        verify(agendamentoRepository).save(agendamentoCaptor.capture());
        verify(integrationSyncLogService).iniciar(
                7L,
                ExternalProviderType.MEDWARE,
                OffsetDateTime.parse("2026-07-01T00:00:00-03:00"),
                dataInicio,
                dataFim
        );

        Agendamento saved = agendamentoCaptor.getValue();
        assertEquals(ExternalProviderType.MEDWARE, saved.getExternalSource());
        assertEquals("98765", saved.getExternalId());
        assertEquals(1, result.agendamentosCriados());
    }

    @Test
    void should_create_minimal_medware_patient_when_appointment_patient_was_not_imported() {
        clinica.setSlug("ultramedical");
        clinica.setExternalProvider(ExternalProviderType.MEDWARE);
        when(provider.getType()).thenReturn(ExternalProviderType.MEDWARE);
        LocalDate dataInicio = LocalDate.of(2026, 6, 1);
        LocalDate dataFim = LocalDate.of(2026, 7, 31);

        ExternalAppointmentDTO appointment = new ExternalAppointmentDTO(
                "98765",
                "1023",
                OffsetDateTime.parse("2026-07-03T17:30:00-03:00"),
                null,
                "EXAME",
                "Ultrassonografia transvaginal",
                "AGENDADO",
                null,
                null,
                null,
                Map.of("medware", Map.of(
                        "codAgendamento", "98765",
                        "codPaciente", "1023",
                        "nomePaciente", "Maria Medware",
                        "telefonePaciente", "11999990000"
                ))
        );

        when(provider.getPatients(null, null, 100))
                .thenReturn(new PageResult<>(List.of(), false, null));
        when(provider.getAppointments(null, dataInicio, dataFim, null, 100))
                .thenReturn(new PageResult<>(List.of(appointment), false, null));
        when(pacienteRepository.findByClinicaIdAndExternalSourceAndExternalId(
                7L, ExternalProviderType.MEDWARE, "1023"))
                .thenReturn(Optional.empty());
        when(pacienteRepository.save(any(Paciente.class))).thenAnswer(invocation -> {
            Paciente paciente = invocation.getArgument(0);
            paciente.setId(20L);
            return paciente;
        });
        when(agendamentoRepository.findByClinicaIdAndExternalSourceAndExternalId(
                7L, ExternalProviderType.MEDWARE, "98765"))
                .thenReturn(Optional.empty());
        when(agendamentoRepository.save(any(Agendamento.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExternalSyncResult result = service.sincronizar(clinica, dataInicio, dataFim);

        ArgumentCaptor<Paciente> pacienteCaptor = ArgumentCaptor.forClass(Paciente.class);
        ArgumentCaptor<Agendamento> agendamentoCaptor = ArgumentCaptor.forClass(Agendamento.class);
        verify(pacienteRepository).save(pacienteCaptor.capture());
        verify(agendamentoRepository).save(agendamentoCaptor.capture());

        Paciente pacienteCriado = pacienteCaptor.getValue();
        Agendamento agendamentoCriado = agendamentoCaptor.getValue();
        assertEquals(ExternalProviderType.MEDWARE, pacienteCriado.getExternalSource());
        assertEquals("1023", pacienteCriado.getExternalId());
        assertEquals("MARIA MEDWARE", pacienteCriado.getNomeBusca());
        assertEquals("11999990000", pacienteCriado.getTelefoneNormalizado());
        assertSame(pacienteCriado, agendamentoCriado.getPaciente());
        assertEquals(1, result.agendamentosCriados());
        assertEquals(0, result.agendamentosIgnorados());
    }

    @Test
    void should_create_minimal_medware_patient_and_appointment_when_payload_is_null() {
        clinica.setSlug("ultramedical");
        clinica.setExternalProvider(ExternalProviderType.MEDWARE);
        when(provider.getType()).thenReturn(ExternalProviderType.MEDWARE);

        ExternalAppointmentDTO appointment = new ExternalAppointmentDTO(
                "98767",
                "1024",
                OffsetDateTime.parse("2026-07-03T18:00:00-03:00"),
                null,
                "EXAME",
                null,
                null,
                null,
                null,
                null,
                null
        );

        when(provider.getPatients(null, null, 100))
                .thenReturn(new PageResult<>(List.of(), false, null));
        when(provider.getAppointments(null, null, 100))
                .thenReturn(new PageResult<>(List.of(appointment), false, null));
        when(pacienteRepository.findByClinicaIdAndExternalSourceAndExternalId(
                7L, ExternalProviderType.MEDWARE, "1024"))
                .thenReturn(Optional.empty());
        when(pacienteRepository.save(any(Paciente.class))).thenAnswer(invocation -> {
            Paciente paciente = invocation.getArgument(0);
            paciente.setId(21L);
            return paciente;
        });
        when(agendamentoRepository.findByClinicaIdAndExternalSourceAndExternalId(
                7L, ExternalProviderType.MEDWARE, "98767"))
                .thenReturn(Optional.empty());
        when(agendamentoRepository.save(any(Agendamento.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExternalSyncResult result = service.sincronizar(clinica);

        ArgumentCaptor<Paciente> pacienteCaptor = ArgumentCaptor.forClass(Paciente.class);
        ArgumentCaptor<Agendamento> agendamentoCaptor = ArgumentCaptor.forClass(Agendamento.class);
        verify(pacienteRepository).save(pacienteCaptor.capture());
        verify(agendamentoRepository).save(agendamentoCaptor.capture());
        assertTrue(pacienteCaptor.getValue().getExternalPayload().contains("\"appointment\":{}"));
        assertEquals("{}", agendamentoCaptor.getValue().getExternalPayload());
        assertEquals(1, result.pacientesCriados());
        assertEquals(1, result.agendamentosProcessados());
        assertEquals(1, result.agendamentosCriados());
    }

    @Test
    void should_update_existing_medware_appointment_without_creating_duplicate() {
        clinica.setExternalProvider(ExternalProviderType.MEDWARE);
        when(provider.getType()).thenReturn(ExternalProviderType.MEDWARE);
        Paciente paciente = new Paciente();
        paciente.setId(20L);
        paciente.setClinica(clinica);

        Agendamento existing = new Agendamento();
        existing.setClinica(clinica);
        existing.setPaciente(paciente);
        existing.setExternalSource(ExternalProviderType.MEDWARE);
        existing.setExternalId("98765");

        ExternalAppointmentDTO appointment = new ExternalAppointmentDTO(
                "98765",
                "1023",
                OffsetDateTime.parse("2026-07-03T17:30:00-03:00"),
                null,
                "EXAME",
                "Ultrassonografia transvaginal",
                "AGENDADO",
                null,
                null,
                null,
                Map.of("codAgendamento", "98765")
        );

        when(provider.getPatients(null, null, 100))
                .thenReturn(new PageResult<>(List.of(), false, null));
        when(provider.getAppointments(null, null, 100))
                .thenReturn(new PageResult<>(List.of(appointment), false, null));
        when(pacienteRepository.findByClinicaIdAndExternalSourceAndExternalId(
                7L, ExternalProviderType.MEDWARE, "1023"))
                .thenReturn(Optional.of(paciente));
        when(agendamentoRepository.findByClinicaIdAndExternalSourceAndExternalId(
                7L, ExternalProviderType.MEDWARE, "98765"))
                .thenReturn(Optional.of(existing));
        when(agendamentoRepository.save(any(Agendamento.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExternalSyncResult result = service.sincronizar(clinica);

        verify(agendamentoRepository).save(existing);
        assertEquals(0, result.agendamentosCriados());
        assertEquals(1, result.agendamentosAtualizados());
    }

    @Test
    void should_link_medware_appointment_to_active_local_doctor_by_normalized_name() {
        clinica.setExternalProvider(ExternalProviderType.MEDWARE);
        when(provider.getType()).thenReturn(ExternalProviderType.MEDWARE);

        Paciente paciente = new Paciente();
        paciente.setId(20L);
        paciente.setClinica(clinica);
        paciente.setExternalSource(ExternalProviderType.MEDWARE);
        paciente.setExternalId("1023");

        Medico medico = new Medico();
        medico.setId(44L);
        medico.setNome("Médico Teste");
        medico.setPerfil("MEDICO");

        ExternalAppointmentDTO appointment = new ExternalAppointmentDTO(
                "98766",
                "1023",
                OffsetDateTime.parse("2026-07-03T17:30:00-03:00"),
                null,
                "EXAME",
                "Ultrassonografia",
                "AGENDADO",
                null,
                null,
                null,
                Map.of("medicoNome", "MEDICO  TESTE", "codMedico", "7")
        );

        when(provider.getPatients(null, null, 100))
                .thenReturn(new PageResult<>(List.of(), false, null));
        when(provider.getAppointments(null, null, 100))
                .thenReturn(new PageResult<>(List.of(appointment), false, null));
        when(pacienteRepository.findByClinicaIdAndExternalSourceAndExternalId(
                7L, ExternalProviderType.MEDWARE, "1023"))
                .thenReturn(Optional.of(paciente));
        when(agendamentoRepository.findByClinicaIdAndExternalSourceAndExternalId(
                7L, ExternalProviderType.MEDWARE, "98766"))
                .thenReturn(Optional.empty());
        when(agendamentoRepository.save(any(Agendamento.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(usuarioRepository.findMedicosAtivosByClinicaId(7L))
                .thenReturn(List.of(medico));

        service.sincronizar(clinica);

        ArgumentCaptor<Agendamento> captor = ArgumentCaptor.forClass(Agendamento.class);
        verify(agendamentoRepository).save(captor.capture());
        assertSame(medico, captor.getValue().getMedico());
    }

    @Test
    void should_report_total_failure_without_exposing_provider_error_details() {
        when(provider.getPatients(null, null, 100))
                .thenThrow(new IllegalStateException("detalhe interno simulado"));

        ExternalSyncResult result = service.sincronizar(clinica);

        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(integrationSyncLogService).finalizar(
                eq(99L), eq("FALHA_TOTAL"), any(ExternalSyncProgress.class), errorCaptor.capture());

        assertEquals("FALHA_TOTAL", result.status());
        assertEquals(
                "Falha na sincronizacao externa na etapa PACIENTES_FETCH: IllegalStateException",
                errorCaptor.getValue()
        );
        assertFalse(errorCaptor.getValue().contains("detalhe interno simulado"));
        verifyNoInteractions(agendamentoRepository);
    }

    @Test
    void should_record_safe_medware_error_message_in_sync_log() {
        clinica.setSlug("ultramedical");
        clinica.setExternalProvider(ExternalProviderType.MEDWARE);
        when(provider.getPatients(null, null, 100))
                .thenThrow(new IllegalStateException(
                        "MEDWARE_API_URL invalida ou endpoint nao retornou JSON. Verifique se a URL termina com /api."
                ));

        ExternalSyncResult result = service.sincronizar(clinica);

        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(integrationSyncLogService).finalizar(
                eq(99L), eq("FALHA_TOTAL"), any(ExternalSyncProgress.class), errorCaptor.capture());

        assertEquals("FALHA_TOTAL", result.status());
        assertEquals(
                "Falha na sincronizacao externa na etapa PACIENTES_FETCH: IllegalStateException",
                errorCaptor.getValue()
        );
    }

    @Test
    void should_record_sanitized_stage_without_patient_data_when_persistence_fails() {
        clinica.setSlug("ultramedical");
        clinica.setExternalProvider(ExternalProviderType.MEDWARE);
        when(provider.getType()).thenReturn(ExternalProviderType.MEDWARE);
        ExternalPatientDTO externalPatient = new ExternalPatientDTO(
                "1025", "Paciente Ficticio", null, null, null, null, null, null);

        when(provider.getPatients(null, null, 100))
                .thenReturn(new PageResult<>(List.of(externalPatient), false, null));
        when(provider.getPatientNotes("1025", null, 100))
                .thenReturn(new PageResult<>(List.of(), false, null));
        when(pacienteRepository.findByClinicaIdAndExternalSourceAndExternalId(
                7L, ExternalProviderType.MEDWARE, "1025"))
                .thenReturn(Optional.empty());
        when(pacienteRepository.save(any(Paciente.class)))
                .thenThrow(new IllegalStateException("Paciente Ficticio documento 11122233344"));

        ExternalSyncResult result = service.sincronizar(clinica);

        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(integrationSyncLogService).finalizar(
                eq(99L), eq("FALHA_TOTAL"), any(ExternalSyncProgress.class), errorCaptor.capture());
        assertEquals("FALHA_TOTAL", result.status());
        assertEquals(
                "Falha na sincronizacao externa na etapa PACIENTE_PERSIST: IllegalStateException",
                errorCaptor.getValue()
        );
        assertFalse(errorCaptor.getValue().contains("Paciente Ficticio"));
        assertFalse(errorCaptor.getValue().contains("11122233344"));
    }

    @Test
    void should_ignore_appointment_without_start_at_before_creating_minimal_patient() {
        clinica.setSlug("ultramedical");
        clinica.setExternalProvider(ExternalProviderType.MEDWARE);
        when(provider.getType()).thenReturn(ExternalProviderType.MEDWARE);
        ExternalAppointmentDTO appointment = new ExternalAppointmentDTO(
                "appt-without-start",
                "patient-not-imported",
                null,
                null,
                "EXAME",
                "Procedimento ficticio",
                "AGENDADO",
                null,
                null,
                null,
                Map.of("codAgendamento", "appt-without-start")
        );

        when(provider.getPatients(null, null, 100))
                .thenReturn(new PageResult<>(List.of(), false, null));
        when(provider.getAppointments(null, null, 100))
                .thenReturn(new PageResult<>(List.of(appointment), false, null));

        ExternalSyncResult result = service.sincronizar(clinica);

        assertEquals("SUCESSO", result.status());
        assertEquals(1, result.agendamentosIgnorados());
        assertEquals(0, result.pacientesCriados());
        verify(pacienteRepository, never()).save(any(Paciente.class));
        verify(agendamentoRepository, never()).save(any(Agendamento.class));
    }
}
