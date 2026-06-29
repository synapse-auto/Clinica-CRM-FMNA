package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Agendamento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Medico;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.agendamento.AgendamentoCancelRequest;
import com.synapse.clinicafemina.dto.agendamento.AgendamentoCreateRequest;
import com.synapse.clinicafemina.dto.agendamento.AgendamentoResponse;
import com.synapse.clinicafemina.dto.agendamento.AgendamentoUpdateRequest;
import com.synapse.clinicafemina.dto.agendamento.AgendaOptionsResponse;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import com.synapse.clinicafemina.repository.AgendamentoRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AgendamentoServiceTest {

    @Mock
    private AgendamentoRepository agendamentoRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    private AgendamentoService service;
    private Clinica clinica;
    private Paciente paciente;
    private Medico medico;

    @BeforeEach
    void setUp() {
        service = new AgendamentoService(
                agendamentoRepository,
                pacienteRepository,
                usuarioRepository
        );
        clinica = new Clinica();
        clinica.setId(7L);

        paciente = new Paciente();
        paciente.setId(20L);
        paciente.setClinica(clinica);
        paciente.setNome("Maria da Silva");

        medico = new Medico();
        medico.setId(30L);
        medico.setClinica(clinica);
        medico.setNome("Dra. Renata");
        medico.setAtivo(true);
        medico.setPerfil("MEDICO");
    }

    @Test
    void should_create_manual_appointment_for_current_clinic() {
        clinica.setExternalProvider(ExternalProviderType.MEDWARE);
        OffsetDateTime inicio = OffsetDateTime.parse("2026-06-22T09:00:00-03:00");
        OffsetDateTime fim = OffsetDateTime.parse("2026-06-22T09:30:00-03:00");
        AgendamentoCreateRequest request = new AgendamentoCreateRequest(
                20L,
                30L,
                inicio,
                fim,
                "CONSULTA",
                "Pré-natal"
        );
        when(pacienteRepository.findByIdAndClinicaId(20L, 7L)).thenReturn(Optional.of(paciente));
        when(usuarioRepository.findAtivoByIdAndClinicaId(30L, 7L)).thenReturn(Optional.of(medico));
        when(agendamentoRepository.save(any(Agendamento.class))).thenAnswer(invocation -> {
            Agendamento saved = invocation.getArgument(0);
            saved.setId(40L);
            return saved;
        });

        AgendamentoResponse response = service.criar(clinica, request);

        ArgumentCaptor<Agendamento> captor = ArgumentCaptor.forClass(Agendamento.class);
        verify(agendamentoRepository).save(captor.capture());
        Agendamento saved = captor.getValue();
        assertSame(clinica, saved.getClinica());
        assertSame(paciente, saved.getPaciente());
        assertSame(medico, saved.getMedico());
        assertEquals(ExternalProviderType.MANUAL, saved.getExternalSource());
        assertTrue(saved.getExternalId().startsWith("crm-"));
        assertEquals("MANUAL", saved.getOrigem());
        assertEquals("AGENDADO", saved.getStatus());
        assertEquals(0, saved.getConfirmacaoEnviada());
        assertEquals(40L, response.id());
    }

    @Test
    void should_list_appointments_scoped_by_clinic_and_period() {
        OffsetDateTime inicio = OffsetDateTime.parse("2026-06-22T00:00:00-03:00");
        OffsetDateTime fim = OffsetDateTime.parse("2026-06-27T00:00:00-03:00");
        Agendamento agendamento = appointment();
        when(agendamentoRepository
                .findByClinicaIdAndDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(
                        7L, inicio, fim))
                .thenReturn(List.of(agendamento));

        List<AgendamentoResponse> response = service.listar(clinica, inicio, fim);

        assertEquals(1, response.size());
        assertEquals("Maria da Silva", response.getFirst().pacienteNome());
        assertEquals("Dra. Renata", response.getFirst().medicoNome());
    }

    @Test
    void should_update_appointment_without_changing_manual_origin() {
        Agendamento agendamento = appointment();
        OffsetDateTime novoInicio = OffsetDateTime.parse("2026-06-23T10:00:00-03:00");
        OffsetDateTime novoFim = OffsetDateTime.parse("2026-06-23T10:45:00-03:00");
        AgendamentoUpdateRequest request = new AgendamentoUpdateRequest(
                20L,
                30L,
                novoInicio,
                novoFim,
                "RETORNO",
                "Retorno pré-natal"
        );
        when(agendamentoRepository.findByIdAndClinicaId(40L, 7L)).thenReturn(Optional.of(agendamento));
        when(pacienteRepository.findByIdAndClinicaId(20L, 7L)).thenReturn(Optional.of(paciente));
        when(usuarioRepository.findAtivoByIdAndClinicaId(30L, 7L)).thenReturn(Optional.of(medico));
        when(agendamentoRepository.save(agendamento)).thenReturn(agendamento);

        AgendamentoResponse response = service.atualizar(clinica, 40L, request);

        assertEquals(novoInicio, agendamento.getDataHoraInicio());
        assertEquals("RETORNO", agendamento.getTipo());
        assertEquals("Retorno pré-natal", response.servicoNome());
        assertEquals(ExternalProviderType.MANUAL, agendamento.getExternalSource());
    }

    @Test
    void should_cancel_appointment_logically_and_preserve_record() {
        Agendamento agendamento = appointment();
        when(agendamentoRepository.findByIdAndClinicaId(40L, 7L)).thenReturn(Optional.of(agendamento));
        when(agendamentoRepository.save(agendamento)).thenReturn(agendamento);

        AgendamentoResponse response = service.cancelar(
                clinica,
                40L,
                new AgendamentoCancelRequest("Paciente solicitou remarcação")
        );

        assertEquals("CANCELADO", agendamento.getStatus());
        assertEquals("Paciente solicitou remarcação", agendamento.getMotivoCancelamento());
        assertNotNull(agendamento.getCanceladoEm());
        assertEquals("CANCELADO", response.status());
        verify(agendamentoRepository).save(agendamento);
    }

    @Test
    void should_list_patient_options_without_loading_encrypted_patient_entity() {
        when(pacienteRepository.findOpcoesDisponiveisByClinicaId(7L))
                .thenReturn(List.of(pacienteOption(20L, "maria da silva")));
        when(usuarioRepository.findMedicosAtivosByClinicaId(7L)).thenReturn(List.of(medico));

        AgendaOptionsResponse response = service.listarOpcoes(clinica);

        assertEquals(1, response.pacientes().size());
        assertEquals("maria da silva", response.pacientes().getFirst().nome());
        assertEquals(1, response.medicos().size());
        assertEquals("Dra. Renata", response.medicos().getFirst().nome());
        verify(pacienteRepository, never()).findDisponiveisByClinicaId(7L);
    }

    @Test
    void should_accept_active_doctor_by_profile_when_entity_is_proxied() {
        Agendamento agendamento = appointment();
        Usuario proxiedDoctor = mock(Usuario.class);
        when(proxiedDoctor.getPerfil()).thenReturn("MEDICO");
        when(agendamentoRepository.findByIdAndClinicaId(40L, 7L)).thenReturn(Optional.of(agendamento));
        when(pacienteRepository.findByIdAndClinicaId(20L, 7L)).thenReturn(Optional.of(paciente));
        when(usuarioRepository.findAtivoByIdAndClinicaId(30L, 7L)).thenReturn(Optional.of(proxiedDoctor));
        when(agendamentoRepository.save(agendamento)).thenReturn(agendamento);

        service.atualizar(clinica, 40L, new AgendamentoUpdateRequest(
                20L,
                30L,
                OffsetDateTime.parse("2026-06-23T10:00:00-03:00"),
                OffsetDateTime.parse("2026-06-23T10:30:00-03:00"),
                "CONSULTA",
                "Consulta"
        ));

        assertSame(proxiedDoctor, agendamento.getMedico());
    }

    private Agendamento appointment() {
        Agendamento agendamento = new Agendamento();
        agendamento.setId(40L);
        agendamento.setClinica(clinica);
        agendamento.setPaciente(paciente);
        agendamento.setMedico(medico);
        agendamento.setExternalSource(ExternalProviderType.MANUAL);
        agendamento.setExternalId("crm-existing");
        agendamento.setDataHoraInicio(OffsetDateTime.parse("2026-06-22T09:00:00-03:00"));
        agendamento.setDataHoraFim(OffsetDateTime.parse("2026-06-22T09:30:00-03:00"));
        agendamento.setTipo("CONSULTA");
        agendamento.setServicoNome("Pré-natal");
        agendamento.setStatus("AGENDADO");
        agendamento.setOrigem("MANUAL");
        agendamento.setConfirmacaoEnviada(0);
        return agendamento;
    }

    private PacienteRepository.PacienteOptionProjection pacienteOption(Long id, String nomeBusca) {
        return new PacienteRepository.PacienteOptionProjection() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public String getNomeBusca() {
                return nomeBusca;
            }
        };
    }
}
