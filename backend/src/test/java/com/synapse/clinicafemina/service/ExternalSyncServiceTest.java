package com.synapse.clinicafemina.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.clinicafemina.domain.Agendamento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.IntegrationSyncLog;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.integration.external.ExternalAppointmentDTO;
import com.synapse.clinicafemina.integration.external.ExternalClinicProvider;
import com.synapse.clinicafemina.integration.external.ExternalPatientDTO;
import com.synapse.clinicafemina.integration.external.ExternalProviderFactory;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import com.synapse.clinicafemina.integration.external.PageResult;
import com.synapse.clinicafemina.repository.AgendamentoRepository;
import com.synapse.clinicafemina.repository.IntegrationSyncLogRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
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

    private ExternalSyncService service;
    private Clinica clinica;

    @BeforeEach
    void setUp() {
        service = new ExternalSyncService(
                providerFactory,
                syncLogRepository,
                pacienteRepository,
                agendamentoRepository,
                new ObjectMapper(),
                100
        );

        clinica = new Clinica();
        clinica.setId(7L);
        clinica.setSlug("fmna");
        clinica.setExternalProvider(ExternalProviderType.DARWIN);

        lenient().when(providerFactory.getProvider(ExternalProviderType.DARWIN)).thenReturn(provider);
        lenient().when(providerFactory.getProvider(ExternalProviderType.MEDWARE)).thenReturn(provider);
        lenient().when(syncLogRepository.findUltimoSucesso(7L, ExternalProviderType.DARWIN)).thenReturn(Optional.empty());
        lenient().when(syncLogRepository.findUltimoSucesso(7L, ExternalProviderType.MEDWARE)).thenReturn(Optional.empty());
        lenient().when(syncLogRepository.save(any(IntegrationSyncLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
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
        ArgumentCaptor<IntegrationSyncLog> logCaptor = ArgumentCaptor.forClass(IntegrationSyncLog.class);
        verify(provider).getAppointments(null, dataInicio, dataFim, null, 100);
        verify(agendamentoRepository).save(agendamentoCaptor.capture());
        verify(syncLogRepository, times(2)).save(logCaptor.capture());

        Agendamento saved = agendamentoCaptor.getValue();
        IntegrationSyncLog finalLog = logCaptor.getAllValues().getLast();
        assertEquals(ExternalProviderType.MEDWARE, saved.getExternalSource());
        assertEquals("98765", saved.getExternalId());
        assertEquals(dataInicio, finalLog.getDataInicio());
        assertEquals(dataFim, finalLog.getDataFim());
        assertEquals(OffsetDateTime.parse("2026-07-01T00:00:00-03:00"), finalLog.getUpdatedAfterUtilizado());
        assertEquals(1, finalLog.getAgendamentosProcessados());
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
    void should_report_total_failure_without_exposing_provider_error_details() {
        when(provider.getPatients(null, null, 100))
                .thenThrow(new IllegalStateException("detalhe interno simulado"));

        ExternalSyncResult result = service.sincronizar(clinica);

        ArgumentCaptor<IntegrationSyncLog> logCaptor = ArgumentCaptor.forClass(IntegrationSyncLog.class);
        verify(syncLogRepository, times(2)).save(logCaptor.capture());
        IntegrationSyncLog finalLog = logCaptor.getAllValues().getLast();

        assertEquals("FALHA_TOTAL", result.status());
        assertEquals("FALHA_TOTAL", finalLog.getStatus());
        assertEquals("Falha na sincronizacao externa: IllegalStateException", finalLog.getMensagemErro());
        assertFalse(finalLog.getMensagemErro().contains("detalhe interno simulado"));
        verifyNoInteractions(agendamentoRepository);
    }
}
