package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.dto.agenda.AgendaAgendamentoDTO;
import com.synapse.clinicafemina.dto.agenda.AgendaHorarioDisponivelDTO;
import com.synapse.clinicafemina.dto.agenda.AgendaPacienteDTO;
import com.synapse.clinicafemina.dto.agenda.AgendaProfissionalDTO;
import com.synapse.clinicafemina.dto.agenda.AtualizarAgendamentoRequest;
import com.synapse.clinicafemina.dto.agenda.NovoAgendamentoRequest;
import com.synapse.clinicafemina.exception.IdempotencyConflictException;
import com.synapse.clinicafemina.security.JwtService;
import com.synapse.clinicafemina.service.AgendaService;
import com.synapse.clinicafemina.service.N8nCallbackAuthorizationService;
import com.synapse.clinicafemina.service.N8nIdempotencyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato N8N da Agenda: autenticação por X-N8N-SECRET (sem JWT), clínica resolvida
 * pelo backend (nunca pelo chamador), idempotência por Idempotency-Key explícita
 * (nunca por conteúdo), e nenhuma credencial exposta na resposta. Mesmo padrão de
 * {@link N8nAtendimentoControllerTest}.
 */
@WebMvcTest(N8nAgendaController.class)
@AutoConfigureMockMvc(addFilters = false)
class N8nAgendaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AgendaService agendaService;

    @MockBean
    private N8nCallbackAuthorizationService authorizationService;

    @MockBean
    private N8nIdempotencyService idempotencyService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void should_reject_request_without_n8n_secret() throws Exception {
        when(authorizationService.autorizarClinica(null))
                .thenThrow(new BadCredentialsException("Credencial N8N invalida."));

        mockMvc.perform(get("/api/n8n/agenda/profissionais"))
                .andExpect(status().isUnauthorized());

        verify(agendaService, never()).listarProfissionais(any());
    }

    @Test
    void should_reject_request_with_wrong_n8n_secret() throws Exception {
        when(authorizationService.autorizarClinica("wrong-secret"))
                .thenThrow(new BadCredentialsException("Credencial N8N invalida."));

        mockMvc.perform(get("/api/n8n/agenda/profissionais").header("X-N8N-SECRET", "wrong-secret"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_reject_when_clinic_does_not_use_n8n() throws Exception {
        when(authorizationService.autorizarClinica("test-secret"))
                .thenThrow(new AccessDeniedException("Integracao N8N desabilitada."));

        mockMvc.perform(get("/api/n8n/agenda/profissionais").header("X-N8N-SECRET", "test-secret"))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_list_professionals_for_clinic_resolved_by_backend() throws Exception {
        Clinica clinica = clinica(7L);
        when(authorizationService.autorizarClinica("test-secret")).thenReturn(clinica);
        when(agendaService.listarProfissionais(clinica))
                .thenReturn(List.of(new AgendaProfissionalDTO("prof-1", "Dra. Ana", "DARWIN")));

        mockMvc.perform(get("/api/n8n/agenda/profissionais").header("X-N8N-SECRET", "test-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Dra. Ana"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("test-secret"))));
    }

    @Test
    void should_list_available_slots() throws Exception {
        Clinica clinica = clinica(7L);
        when(authorizationService.autorizarClinica("test-secret")).thenReturn(clinica);
        when(agendaService.listarHorariosDisponiveis(eq(clinica), eq(LocalDate.parse("2026-07-20")), any()))
                .thenReturn(List.of(new AgendaHorarioDisponivelDTO(
                        "tt-1", "prof-1", "Dra. Ana", "loc-1", "Unidade Centro",
                        LocalDate.parse("2026-07-20"), "09:00", "09:30")));

        mockMvc.perform(get("/api/n8n/agenda/horarios")
                        .header("X-N8N-SECRET", "test-secret")
                        .param("date", "2026-07-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].horarioInicio").value("09:00"));
    }

    @Test
    void should_locate_appointments_by_patient_cpf() throws Exception {
        Clinica clinica = clinica(7L);
        when(authorizationService.autorizarClinica("test-secret")).thenReturn(clinica);
        when(agendaService.listarPorPaciente(clinica, "11144477735"))
                .thenReturn(List.of(agendamento(1L)));

        mockMvc.perform(get("/api/n8n/agenda/paciente")
                        .header("X-N8N-SECRET", "test-secret")
                        .param("cpf", "11144477735"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idLocal").value(1));
    }

    @Test
    void should_create_or_locate_patient() throws Exception {
        Clinica clinica = clinica(7L);
        when(authorizationService.autorizarClinica("test-secret")).thenReturn(clinica);
        when(agendaService.criarOuLocalizarPaciente(eq(clinica), any()))
                .thenReturn(new AgendaPacienteDTO(1L, "Paciente Teste", "***.***.777-35"));

        mockMvc.perform(post("/api/n8n/agenda/pacientes")
                        .header("X-N8N-SECRET", "test-secret")
                        .contentType("application/json")
                        .content("""
                                {"cpf":"11144477735","nome":"Paciente Teste","telefone":null,"email":null,"dataNascimento":null}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void should_create_appointment_via_idempotency_service_with_explicit_key() throws Exception {
        Clinica clinica = clinica(7L);
        when(authorizationService.autorizarClinica("test-secret")).thenReturn(clinica);
        when(idempotencyService.executarCriacaoIdempotente(
                eq(clinica), eq(N8nIdempotencyService.OPERACAO_CRIAR_AGENDAMENTO), eq("key-abc"),
                any(NovoAgendamentoRequest.class), any()))
                .thenReturn(agendamento(1L));

        mockMvc.perform(post("/api/n8n/agenda")
                        .header("X-N8N-SECRET", "test-secret")
                        .header("Idempotency-Key", "key-abc")
                        .contentType("application/json")
                        .content("""
                                {"pacienteId":1,"profissionalId":"prof-1","data":"2026-07-20","horarioInicio":"09:00","horarioFim":"09:30"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idLocal").value(1));

        verify(agendaService, never()).criarAgendamento(any(), any());
    }

    @Test
    void should_return_same_result_on_identical_retry_with_same_key() throws Exception {
        Clinica clinica = clinica(7L);
        when(authorizationService.autorizarClinica("test-secret")).thenReturn(clinica);
        when(idempotencyService.executarCriacaoIdempotente(
                eq(clinica), eq(N8nIdempotencyService.OPERACAO_CRIAR_AGENDAMENTO), eq("key-retry"),
                any(NovoAgendamentoRequest.class), any()))
                .thenReturn(agendamento(1L));

        String body = """
                {"pacienteId":1,"profissionalId":"prof-1","data":"2026-07-20","horarioInicio":"09:00","horarioFim":"09:30"}
                """;

        mockMvc.perform(post("/api/n8n/agenda").header("X-N8N-SECRET", "test-secret")
                        .header("Idempotency-Key", "key-retry")
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idLocal").value(1));
        mockMvc.perform(post("/api/n8n/agenda").header("X-N8N-SECRET", "test-secret")
                        .header("Idempotency-Key", "key-retry")
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idLocal").value(1));

        verify(idempotencyService, times(2)).executarCriacaoIdempotente(
                eq(clinica), eq(N8nIdempotencyService.OPERACAO_CRIAR_AGENDAMENTO), eq("key-retry"),
                any(NovoAgendamentoRequest.class), any());
    }

    @Test
    void should_return_409_when_same_key_used_with_different_payload() throws Exception {
        Clinica clinica = clinica(7L);
        when(authorizationService.autorizarClinica("test-secret")).thenReturn(clinica);
        when(idempotencyService.executarCriacaoIdempotente(
                eq(clinica), eq(N8nIdempotencyService.OPERACAO_CRIAR_AGENDAMENTO), eq("key-conflito"),
                any(NovoAgendamentoRequest.class), any()))
                .thenThrow(new IdempotencyConflictException(
                        "Esta Idempotency-Key já foi usada com dados diferentes para esta operação."));

        mockMvc.perform(post("/api/n8n/agenda")
                        .header("X-N8N-SECRET", "test-secret")
                        .header("Idempotency-Key", "key-conflito")
                        .contentType("application/json")
                        .content("""
                                {"pacienteId":1,"profissionalId":"prof-2","data":"2026-07-21","horarioInicio":"11:00","horarioFim":"11:30"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void should_isolate_idempotency_key_between_clinics() throws Exception {
        Clinica clinicaA = clinica(7L);
        Clinica clinicaB = clinica(8L);
        when(authorizationService.autorizarClinica("secret-a")).thenReturn(clinicaA);
        when(authorizationService.autorizarClinica("secret-b")).thenReturn(clinicaB);
        when(idempotencyService.executarCriacaoIdempotente(
                eq(clinicaA), eq(N8nIdempotencyService.OPERACAO_CRIAR_AGENDAMENTO), eq("chave-compartilhada"),
                any(NovoAgendamentoRequest.class), any()))
                .thenReturn(agendamento(1L));
        when(idempotencyService.executarCriacaoIdempotente(
                eq(clinicaB), eq(N8nIdempotencyService.OPERACAO_CRIAR_AGENDAMENTO), eq("chave-compartilhada"),
                any(NovoAgendamentoRequest.class), any()))
                .thenReturn(agendamento(2L));

        String body = """
                {"pacienteId":1,"profissionalId":"prof-1","data":"2026-07-20","horarioInicio":"09:00","horarioFim":"09:30"}
                """;

        mockMvc.perform(post("/api/n8n/agenda").header("X-N8N-SECRET", "secret-a")
                        .header("Idempotency-Key", "chave-compartilhada")
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idLocal").value(1));
        mockMvc.perform(post("/api/n8n/agenda").header("X-N8N-SECRET", "secret-b")
                        .header("Idempotency-Key", "chave-compartilhada")
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idLocal").value(2));
    }

    @Test
    void should_create_fitin_as_separate_operation_from_normal_creation_at_same_key() throws Exception {
        Clinica clinica = clinica(7L);
        when(authorizationService.autorizarClinica("test-secret")).thenReturn(clinica);
        when(idempotencyService.executarCriacaoIdempotente(
                eq(clinica), eq(N8nIdempotencyService.OPERACAO_CRIAR_AGENDAMENTO), eq("chave-mesmo-horario"),
                any(NovoAgendamentoRequest.class), any()))
                .thenReturn(agendamento(1L));
        when(idempotencyService.executarCriacaoIdempotente(
                eq(clinica), eq(N8nIdempotencyService.OPERACAO_CRIAR_ENCAIXE), eq("chave-mesmo-horario"),
                any(NovoAgendamentoRequest.class), any()))
                .thenReturn(agendamento(2L));

        String body = """
                {"pacienteId":1,"profissionalId":"prof-1","data":"2026-07-20","horarioInicio":"09:00","horarioFim":"09:30"}
                """;

        mockMvc.perform(post("/api/n8n/agenda").header("X-N8N-SECRET", "test-secret")
                        .header("Idempotency-Key", "chave-mesmo-horario")
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idLocal").value(1));
        mockMvc.perform(post("/api/n8n/agenda/encaixe").header("X-N8N-SECRET", "test-secret")
                        .header("Idempotency-Key", "chave-mesmo-horario")
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idLocal").value(2));
    }

    @Test
    void should_reschedule_appointment() throws Exception {
        Clinica clinica = clinica(7L);
        when(authorizationService.autorizarClinica("test-secret")).thenReturn(clinica);
        when(agendaService.atualizarAgendamento(eq(clinica), eq(1L), any(AtualizarAgendamentoRequest.class)))
                .thenReturn(agendamento(1L));

        mockMvc.perform(put("/api/n8n/agenda/1")
                        .header("X-N8N-SECRET", "test-secret")
                        .contentType("application/json")
                        .content("""
                                {"status":null,"data":"2026-07-21","horarioInicio":"10:00","horarioFim":"10:30","timetableId":null,"procedimentoId":null,"convenioId":null,"observacao":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idLocal").value(1));
    }

    @Test
    void should_cancel_appointment() throws Exception {
        Clinica clinica = clinica(7L);
        when(authorizationService.autorizarClinica("test-secret")).thenReturn(clinica);

        mockMvc.perform(delete("/api/n8n/agenda/1")
                        .header("X-N8N-SECRET", "test-secret")
                        .param("motivo", "Paciente cancelou"))
                .andExpect(status().isNoContent());

        verify(agendaService).cancelarAgendamento(clinica, 1L, "Paciente cancelou");
    }

    private Clinica clinica(Long id) {
        Clinica clinica = new Clinica();
        clinica.setId(id);
        return clinica;
    }

    private AgendaAgendamentoDTO agendamento(Long idLocal) {
        return new AgendaAgendamentoDTO(
                idLocal, "sch-1", "DARWIN", 1L, "Paciente Teste", "***.***.777-35",
                "prof-1", "Dra. Ana", null, "Consulta", null, null, "loc-1", "Unidade Centro",
                LocalDate.parse("2026-07-20"), "09:00", "09:30", "AGENDADO", "tt-1", null,
                "DARWIN", OffsetDateTime.parse("2026-07-19T12:00:00Z"), "SYNCED");
    }
}
