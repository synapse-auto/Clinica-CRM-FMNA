package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.TipoClinica;
import com.synapse.clinicafemina.dto.configuracao.ConfiguracaoResumoResponse;
import com.synapse.clinicafemina.security.JwtService;
import com.synapse.clinicafemina.service.ClinicaConfigService;
import com.synapse.clinicafemina.service.ConfiguracaoResumoService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConfiguracaoController.class)
@AutoConfigureMockMvc(addFilters = false)
class ConfiguracaoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClinicaConfigService clinicaConfigService;

    @MockBean
    private ConfiguracaoResumoService configuracaoResumoService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void should_return_safe_configuration_summary_without_secrets() throws Exception {
        when(configuracaoResumoService.obterResumo()).thenReturn(resumo());

        mockMvc.perform(get("/api/configuracoes/resumo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identidade.nome").value("UltraMedical"))
                .andExpect(jsonPath("$.identidade.slug").value("ultramedical"))
                .andExpect(jsonPath("$.identidade.externalProvider").value("MEDWARE"))
                .andExpect(jsonPath("$.identidade.whatsappConfigurado").value(true))
                .andExpect(jsonPath("$.identidade.n8nConfigurado").value(true))
                .andExpect(jsonPath("$.integracoes[0].nome").value("WhatsApp Oficial"))
                .andExpect(jsonPath("$.integracoes[1].status").value("Configurado"))
                .andExpect(jsonPath("$.ultimaSincronizacaoMedware.status").value("FALHA_TOTAL"))
                .andExpect(jsonPath("$.operacao.retornoHumanoIa24h").value(true))
                .andExpect(content().string(not(containsString("n8nWebhookUrl"))))
                .andExpect(content().string(not(containsString("https://n8n"))))
                .andExpect(content().string(not(containsString("secret"))))
                .andExpect(content().string(not(containsString("token"))));
    }

    private ConfiguracaoResumoResponse resumo() {
        return new ConfiguracaoResumoResponse(
                new ConfiguracaoResumoResponse.Identidade(
                        "UltraMedical",
                        "ultramedical",
                        TipoClinica.ULTRASSONOGRAFIA,
                        "MEDWARE",
                        "Operacional",
                        true,
                        true
                ),
                List.of(
                        new ConfiguracaoResumoResponse.Integracao("WhatsApp Oficial", "Configurado", "Webhook oficial ativo"),
                        new ConfiguracaoResumoResponse.Integracao("N8N", "Configurado", "Callback protegido por segredo"),
                        new ConfiguracaoResumoResponse.Integracao("Medware", "Pendente", "Última sync falhou")
                ),
                new ConfiguracaoResumoResponse.UltimaSincronizacao(
                        "FALHA_TOTAL",
                        OffsetDateTime.parse("2026-07-06T15:00:00Z"),
                        OffsetDateTime.parse("2026-07-06T15:01:00Z"),
                        "01/06/2026",
                        "31/07/2026",
                        0,
                        0,
                        0,
                        "MEDWARE_API_URL invalida"
                ),
                new ConfiguracaoResumoResponse.Seguranca(
                        List.of(
                                new ConfiguracaoResumoResponse.PerfilAtivo("GESTOR", 2),
                                new ConfiguracaoResumoResponse.PerfilAtivo("RECEPCIONISTA", 5)
                        ),
                        List.of("Sessão protegida por JWT", "Logs sem dados sensíveis")
                ),
                new ConfiguracaoResumoResponse.Operacao(
                        true,
                        true,
                        true,
                        true,
                        true
                ),
                new ConfiguracaoResumoResponse.Ambiente(
                        "teste",
                        OffsetDateTime.parse("2026-07-06T14:55:00Z")
                )
        );
    }
}
