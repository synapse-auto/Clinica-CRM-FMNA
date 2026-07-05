package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.security.JwtService;
import com.synapse.clinicafemina.service.ClinicaConfigService;
import com.synapse.clinicafemina.service.ExternalSyncResult;
import com.synapse.clinicafemina.service.ExternalSyncService;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IntegracaoController.class)
class IntegracaoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClinicaConfigService clinicaConfigService;

    @MockBean
    private ExternalSyncService externalSyncService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    private Clinica clinica;

    @BeforeEach
    void setUp() {
        clinica = new Clinica();
        clinica.setId(9L);
        clinica.setSlug("ultramedical");
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica);
    }

    @Test
    @WithMockUser(roles = "GESTOR")
    void should_accept_optional_manual_date_window_for_medware_sync() throws Exception {
        LocalDate dataInicio = LocalDate.of(2026, 7, 1);
        LocalDate dataFim = LocalDate.of(2026, 7, 3);
        when(externalSyncService.sincronizar(clinica, dataInicio, dataFim))
                .thenReturn(new ExternalSyncResult(0, 0, 0, 2, 2, 0, 0, "SUCESSO"));

        mockMvc.perform(post("/api/integracoes/sincronizar")
                        .param("dataInicio", "01/07/2026")
                        .param("dataFim", "03/07/2026")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agendamentosCriados").value(2))
                .andExpect(jsonPath("$.status").value("SUCESSO"));

        verify(externalSyncService).sincronizar(clinica, dataInicio, dataFim);
    }
}
