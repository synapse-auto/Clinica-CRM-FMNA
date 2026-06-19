package com.synapse.clinicafemina.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.dto.agendamento.AgendamentoResponse;
import com.synapse.clinicafemina.dto.agendamento.AgendaOptionResponse;
import com.synapse.clinicafemina.dto.agendamento.AgendaOptionsResponse;
import com.synapse.clinicafemina.service.AgendamentoService;
import com.synapse.clinicafemina.service.ClinicaConfigService;
import com.synapse.clinicafemina.security.JwtService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgendamentoController.class)
@AutoConfigureMockMvc(addFilters = false)
class AgendamentoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ClinicaConfigService clinicaConfigService;

    @MockBean
    private AgendamentoService agendamentoService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void should_list_appointments_by_period() throws Exception {
        Clinica clinica = clinic();
        AgendamentoResponse response = response("AGENDADO");
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica);
        when(agendamentoService.listar(eq(clinica), any(), any())).thenReturn(List.of(response));

        mockMvc.perform(get("/api/agendamentos")
                        .param("inicio", "2026-06-22T00:00:00-03:00")
                        .param("fim", "2026-06-27T00:00:00-03:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pacienteNome").value("Maria da Silva"));
    }

    @Test
    void should_create_appointment() throws Exception {
        Clinica clinica = clinic();
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica);
        when(agendamentoService.criar(eq(clinica), any())).thenReturn(response("AGENDADO"));

        mockMvc.perform(post("/api/agendamentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pacienteId": 20,
                                  "medicoId": 30,
                                  "dataHoraInicio": "2026-06-22T09:00:00-03:00",
                                  "dataHoraFim": "2026-06-22T09:30:00-03:00",
                                  "tipo": "CONSULTA",
                                  "servicoNome": "Pré-natal"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(40));
    }

    @Test
    void should_list_form_options() throws Exception {
        Clinica clinica = clinic();
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica);
        when(agendamentoService.listarOpcoes(clinica)).thenReturn(new AgendaOptionsResponse(
                List.of(new AgendaOptionResponse(20L, "Maria da Silva")),
                List.of(new AgendaOptionResponse(30L, "Dra. Renata"))
        ));

        mockMvc.perform(get("/api/agendamentos/opcoes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pacientes[0].id").value(20))
                .andExpect(jsonPath("$.medicos[0].nome").value("Dra. Renata"));
    }

    @Test
    void should_cancel_appointment_logically() throws Exception {
        Clinica clinica = clinic();
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica);
        when(agendamentoService.cancelar(eq(clinica), eq(40L), any()))
                .thenReturn(response("CANCELADO"));

        mockMvc.perform(patch("/api/agendamentos/40/cancelamento")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"motivo": "Paciente solicitou remarcação"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"));
    }

    @Test
    void should_update_appointment() throws Exception {
        Clinica clinica = clinic();
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica);
        when(agendamentoService.atualizar(eq(clinica), eq(40L), any()))
                .thenReturn(response("AGENDADO"));

        mockMvc.perform(put("/api/agendamentos/40")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pacienteId": 20,
                                  "medicoId": 30,
                                  "dataHoraInicio": "2026-06-23T10:00:00-03:00",
                                  "dataHoraFim": "2026-06-23T10:30:00-03:00",
                                  "tipo": "RETORNO",
                                  "servicoNome": "Retorno pré-natal"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(40));
    }

    private Clinica clinic() {
        Clinica clinica = new Clinica();
        clinica.setId(7L);
        return clinica;
    }

    private AgendamentoResponse response(String status) {
        return new AgendamentoResponse(
                40L,
                20L,
                "Maria da Silva",
                30L,
                "Dra. Renata",
                OffsetDateTime.parse("2026-06-22T09:00:00-03:00"),
                OffsetDateTime.parse("2026-06-22T09:30:00-03:00"),
                "CONSULTA",
                "Pré-natal",
                status,
                "MANUAL",
                0,
                "CANCELADO".equals(status) ? OffsetDateTime.now() : null,
                "CANCELADO".equals(status) ? "Paciente solicitou remarcação" : null
        );
    }
}
