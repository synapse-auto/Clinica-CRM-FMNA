package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.dto.agenda.AgendaAgendamentoDTO;
import com.synapse.clinicafemina.dto.agenda.AgendaConvenioDTO;
import com.synapse.clinicafemina.dto.agenda.AgendaHorarioDisponivelDTO;
import com.synapse.clinicafemina.dto.agenda.AgendaLocalDTO;
import com.synapse.clinicafemina.dto.agenda.AgendaPacienteDTO;
import com.synapse.clinicafemina.dto.agenda.AgendaProcedimentoDTO;
import com.synapse.clinicafemina.dto.agenda.AgendaProfissionalDTO;
import com.synapse.clinicafemina.exception.AgendaOperationNotSupportedException;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.exception.DarwinIntegrationException;
import com.synapse.clinicafemina.exception.DarwinNotAvailableException;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.security.JwtService;
import com.synapse.clinicafemina.service.AgendaService;
import com.synapse.clinicafemina.service.ClinicaConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comportamento REST de /api/agenda: rotas, validação de body, mapeamento de erros.
 * Autenticação/autorização real (@PreAuthorize) é coberta separadamente em
 * {@link AgendaControllerSecurityTest} (contexto Spring Security completo).
 */
@WebMvcTest(AgendaController.class)
@AutoConfigureMockMvc(addFilters = false)
class AgendaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClinicaConfigService clinicaConfigService;

    @MockBean
    private AgendaService agendaService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    private Clinica clinica() {
        Clinica clinica = new Clinica();
        clinica.setId(7L);
        return clinica;
    }

    private AgendaAgendamentoDTO agendamento() {
        return new AgendaAgendamentoDTO(
                1L, "sch-1", "DARWIN", 10L, "Paciente Teste", "***.***.777-35",
                "prof-1", "Dra. Fulana", "proc-1", "Consulta", "ins-1", "Particular",
                "loc-1", "Unidade Centro", java.time.LocalDate.of(2026, 7, 20), "09:00", "09:30",
                "Marcado", "tt-1", "obs", "DARWIN", null, "SYNCED");
    }

    @Test
    void listar_returnsAgendaForPeriod() throws Exception {
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica());
        when(agendaService.listarAgenda(any(), any(), any())).thenReturn(List.of(agendamento()));

        mockMvc.perform(get("/api/agenda")
                        .param("startDate", "2026-07-20T00:00:00-03:00")
                        .param("endDate", "2026-07-21T00:00:00-03:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pacienteNome").value("Paciente Teste"))
                .andExpect(jsonPath("$[0].pacienteCpfMascarado").value("***.***.777-35"));
    }

    @Test
    void listar_withInvalidDate_returns400() throws Exception {
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica());

        mockMvc.perform(get("/api/agenda")
                        .param("startDate", "nao-e-uma-data")
                        .param("endDate", "2026-07-21T00:00:00-03:00"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void capabilities_returnsRealCapabilitiesForResolvedProvider() throws Exception {
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica());
        when(agendaService.capacidades(any())).thenReturn(new com.synapse.clinicafemina.dto.agenda.AgendaCapabilitiesDTO(
                "DARWIN", true, true, true, false, true, true, "KNOWN_CRM_PATIENTS_ONLY"));

        mockMvc.perform(get("/api/agenda/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("DARWIN"))
                .andExpect(jsonPath("$.supportsFitIn").value(true))
                .andExpect(jsonPath("$.supportsCatalog").value(true))
                .andExpect(jsonPath("$.coverage").value("KNOWN_CRM_PATIENTS_ONLY"));
    }

    @Test
    void buscarPorId_found_returns200() throws Exception {
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica());
        when(agendaService.buscarPorId(any(), eq(1L))).thenReturn(agendamento());

        mockMvc.perform(get("/api/agenda/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idLocal").value(1));
    }

    @Test
    void buscarPorId_notFound_returns404() throws Exception {
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica());
        when(agendaService.buscarPorId(any(), eq(99L))).thenThrow(new NotFoundException("Agendamento não encontrado"));

        mockMvc.perform(get("/api/agenda/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listarPorPaciente_returnsAgendaForCpf() throws Exception {
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica());
        when(agendaService.listarPorPaciente(any(), eq("11144477735"))).thenReturn(List.of(agendamento()));

        mockMvc.perform(get("/api/agenda/paciente").param("cpf", "11144477735"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].externalId").value("sch-1"));
    }

    @Test
    void profissionais_returnsList() throws Exception {
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica());
        when(agendaService.listarProfissionais(any()))
                .thenReturn(List.of(new AgendaProfissionalDTO("prof-1", "Dra. Fulana", "DARWIN")));

        mockMvc.perform(get("/api/agenda/profissionais"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Dra. Fulana"));
    }

    @Test
    void horarios_missingRequiredDate_returns400() throws Exception {
        mockMvc.perform(get("/api/agenda/horarios"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void horarios_withInvalidDateFormat_returns400() throws Exception {
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica());

        mockMvc.perform(get("/api/agenda/horarios").param("date", "20/07/2026"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void horarios_returnsAvailableSlots() throws Exception {
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica());
        when(agendaService.listarHorariosDisponiveis(any(), any(), any())).thenReturn(List.of(
                new AgendaHorarioDisponivelDTO("tt-1", "prof-1", "Dra. Fulana", "loc-1", "Unidade Centro",
                        java.time.LocalDate.of(2026, 7, 20), "09:00", "09:30")));

        mockMvc.perform(get("/api/agenda/horarios").param("date", "2026-07-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].horarioInicio").value("09:00"));
    }

    @Test
    void procedimentos_returnsList() throws Exception {
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica());
        when(agendaService.listarProcedimentos(any(), any()))
                .thenReturn(List.of(new AgendaProcedimentoDTO("proc-1", "Consulta")));

        mockMvc.perform(get("/api/agenda/procedimentos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Consulta"));
    }

    @Test
    void convenios_returnsList() throws Exception {
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica());
        when(agendaService.listarConvenios(any(), any()))
                .thenReturn(List.of(new AgendaConvenioDTO("ins-1", "Particular")));

        mockMvc.perform(get("/api/agenda/convenios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Particular"));
    }

    @Test
    void locais_returnsList() throws Exception {
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica());
        when(agendaService.listarLocais(any()))
                .thenReturn(List.of(new AgendaLocalDTO("loc-1", "Unidade Centro")));

        mockMvc.perform(get("/api/agenda/locais"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Unidade Centro"));
    }

    @Test
    void criarOuLocalizarPaciente_withValidBody_returns201() throws Exception {
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica());
        when(agendaService.criarOuLocalizarPaciente(any(), any()))
                .thenReturn(new AgendaPacienteDTO(10L, "Paciente Teste", "***.***.777-35"));

        mockMvc.perform(post("/api/agenda/pacientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cpf":"111.444.777-35","nome":"Paciente Teste","telefone":"11999998888"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Paciente Teste"));
    }

    @Test
    void criar_withValidBody_returns201() throws Exception {
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica());
        when(agendaService.criarAgendamento(any(), any())).thenReturn(agendamento());

        mockMvc.perform(post("/api/agenda")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId":10,"timetableId":"tt-1","data":"2026-07-20",
                                 "horarioInicio":"09:00","horarioFim":"09:30",
                                 "procedimentoId":"proc-1","convenioId":"ins-1"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.syncStatus").value("SYNCED"));
    }

    @Test
    void criar_whenDarwinNotAvailable_returns409() throws Exception {
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica());
        when(agendaService.criarAgendamento(any(), any()))
                .thenThrow(new DarwinNotAvailableException("A integração Darwin não está habilitada."));

        mockMvc.perform(post("/api/agenda")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId":10,"data":"2026-07-20","horarioInicio":"09:00","horarioFim":"09:30"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void criar_whenDarwinReturns404_propagatesSanitized404() throws Exception {
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica());
        when(agendaService.criarAgendamento(any(), any()))
                .thenThrow(new DarwinIntegrationException(404, "Recurso nao encontrado na integracao Darwin."));

        mockMvc.perform(post("/api/agenda")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId":10,"data":"2026-07-20","horarioInicio":"09:00","horarioFim":"09:30"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void criar_whenDarwinTimesOut_propagatesSanitized504() throws Exception {
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica());
        when(agendaService.criarAgendamento(any(), any()))
                .thenThrow(new DarwinIntegrationException(504, "Tempo limite ao comunicar com a integracao Darwin."));

        mockMvc.perform(post("/api/agenda")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId":10,"data":"2026-07-20","horarioInicio":"09:00","horarioFim":"09:30"}
                                """))
                .andExpect(status().isGatewayTimeout());
    }

    @Test
    void criarEncaixe_whenNotSupported_returns501() throws Exception {
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica());
        when(agendaService.criarEncaixe(any(), any()))
                .thenThrow(new AgendaOperationNotSupportedException("Encaixe não é suportado pelo provider desta clínica."));

        mockMvc.perform(post("/api/agenda/encaixe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId":10,"data":"2026-07-20","horarioInicio":"09:00","horarioFim":"09:30"}
                                """))
                .andExpect(status().isNotImplemented());
    }

    @Test
    void criarEncaixe_withValidBody_returns201() throws Exception {
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica());
        when(agendaService.criarEncaixe(any(), any())).thenReturn(agendamento());

        mockMvc.perform(post("/api/agenda/encaixe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId":10,"profissionalId":"prof-1","localId":"loc-1",
                                 "data":"2026-07-20","horarioInicio":"09:00","horarioFim":"09:30",
                                 "procedimentoId":"proc-1","convenioId":"ins-1"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void atualizar_withValidBody_returns200() throws Exception {
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica());
        when(agendaService.atualizarAgendamento(any(), eq(1L), any())).thenReturn(agendamento());

        mockMvc.perform(put("/api/agenda/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"Confirmado"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idLocal").value(1));
    }

    @Test
    void cancelar_returns204() throws Exception {
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica());

        mockMvc.perform(delete("/api/agenda/1").param("motivo", "Paciente desistiu"))
                .andExpect(status().isNoContent());
    }

    @Test
    void cancelar_alreadyCancelled_stillReturns204WhenServiceIsIdempotent() throws Exception {
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica());

        mockMvc.perform(delete("/api/agenda/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void cancelar_whenAgendamentoNotFound_returns404() throws Exception {
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica());
        org.mockito.Mockito.doThrow(new NotFoundException("Agendamento não encontrado"))
                .when(agendaService).cancelarAgendamento(any(), eq(404L), any());

        mockMvc.perform(delete("/api/agenda/404"))
                .andExpect(status().isNotFound());
    }

    @Test
    void criar_whenBodyMissingRequiredFields_returns400() throws Exception {
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica());
        when(agendaService.criarAgendamento(any(), any()))
                .thenThrow(new BadRequestException("pacienteId é obrigatório para este provider."));

        mockMvc.perform(post("/api/agenda")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
