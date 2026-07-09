package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Recepcionista;
import com.synapse.clinicafemina.domain.MidiaMensagem;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.integration.WhatsappOutboundClient;
import com.synapse.clinicafemina.dto.atendimento.AtendimentoLembreteResponse;
import com.synapse.clinicafemina.security.JwtService;
import com.synapse.clinicafemina.service.AtendimentoService;
import com.synapse.clinicafemina.service.AtendimentoLembreteService;
import com.synapse.clinicafemina.service.AtendimentoTagService;
import com.synapse.clinicafemina.service.ClinicaConfigService;
import com.synapse.clinicafemina.service.ConvenioReviewService;
import com.synapse.clinicafemina.service.MensagemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.synapse.clinicafemina.dto.AtendimentoDetalheDTO;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AtendimentoController.class)
class AtendimentoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AtendimentoService atendimentoService;

    @MockBean
    private AtendimentoTagService atendimentoTagService;

    @MockBean
    private AtendimentoLembreteService atendimentoLembreteService;

    @MockBean
    private MensagemService mensagemService;

    @MockBean
    private ConvenioReviewService convenioReviewService;

    @MockBean
    private ClinicaConfigService clinicaConfigService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    private Clinica clinica;

    @BeforeEach
    void setUp() {
        clinica = new Clinica();
        clinica.setId(9L);
        clinica.setNome("Clinica Teste");
        when(clinicaConfigService.obterClinicaAtual()).thenReturn(clinica);
    }

    @Test
    @WithMockUser(roles = "GESTOR")
    void should_return_media_bytes_and_correct_content_type_when_successful() throws Exception {
        MidiaMensagem midia = new MidiaMensagem();
        midia.setWhatsappMediaId("media-123");
        midia.setNomeArquivo("exame.png");
        midia.setMimeType("image/png");

        byte[] content = new byte[] {1, 2, 3};
        WhatsappOutboundClient.MidiaBaixada baixada = new WhatsappOutboundClient.MidiaBaixada(content, "image/png");

        when(mensagemService.buscarMidia(eq(30L), eq(100L), eq(9L))).thenReturn(midia);
        when(mensagemService.obterBinarioMidia(midia)).thenReturn(baixada);

        mockMvc.perform(get("/api/atendimentos/30/mensagens/100/midia")
                        .accept("image/png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"exame.png\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store, must-revalidate"))
                .andExpect(content().bytes(content));
    }

    @Test
    @WithMockUser(roles = "GESTOR")
    void should_return_controlled_json_error_when_download_fails() throws Exception {
        MidiaMensagem midia = new MidiaMensagem();
        midia.setWhatsappMediaId("media-123");
        midia.setNomeArquivo("exame.png");
        midia.setMimeType("image/png");

        when(mensagemService.buscarMidia(eq(30L), eq(100L), eq(9L))).thenReturn(midia);
        when(mensagemService.obterBinarioMidia(midia)).thenReturn(null);

        mockMvc.perform(get("/api/atendimentos/30/mensagens/100/midia"))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Mídia indisponível no momento. Tente novamente em instantes."));
    }

    @Test
    @WithMockUser(roles = "GESTOR")
    void should_return_not_found_when_media_unauthorized_or_missing() throws Exception {
        when(mensagemService.buscarMidia(eq(30L), eq(100L), eq(9L)))
                .thenThrow(new NotFoundException("Mídia não encontrada"));

        mockMvc.perform(get("/api/atendimentos/30/mensagens/100/midia"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "RECEPCIONISTA")
    void should_return_atendimento_to_ai_mode() throws Exception {
        when(atendimentoService.ativarModoIa(30L, 9L))
                .thenReturn(new AtendimentoDetalheDTO(
                        30L,
                        "ATIVO",
                        true,
                        null,
                        null,
                        0,
                        new AtendimentoDetalheDTO.PacienteDetalheDTO(
                                20L,
                                "Paciente Teste",
                                "44999999999",
                                null,
                                "EM_ATENDIMENTO",
                                null,
                                null,
                                false,
                                null,
                                null,
                                null,
                                null
                        ),
                        null
                ));

        mockMvc.perform(patch("/api/atendimentos/30/modo-ia").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tratadoPorIa").value(true))
                .andExpect(jsonPath("$.atendentePrincipal").doesNotExist());

        verify(atendimentoService).ativarModoIa(30L, 9L);
    }

    @Test
    @WithMockUser(roles = "MEDICO")
    void should_allow_medico_to_read_internal_reminders() throws Exception {
        when(atendimentoLembreteService.listar(30L, 9L)).thenReturn(List.of(
                new AtendimentoLembreteResponse(
                        7L,
                        30L,
                        "Conferir autorizacao",
                        OffsetDateTime.parse("2026-07-10T10:00:00Z"),
                        "PENDENTE",
                        2L,
                        "Recepcao",
                        OffsetDateTime.parse("2026-07-01T10:00:00Z"),
                        OffsetDateTime.parse("2026-07-01T10:00:00Z")
                )
        ));

        mockMvc.perform(get("/api/atendimentos/30/lembretes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mensagem").value("Conferir autorizacao"))
                .andExpect(jsonPath("$[0].status").value("PENDENTE"));

        verify(atendimentoLembreteService).listar(30L, 9L);
    }

    @Test
    void should_allow_recepcionista_to_create_internal_reminder() throws Exception {
        Recepcionista recepcionista = new Recepcionista();
        recepcionista.setId(11L);
        recepcionista.setPerfil("RECEPCIONISTA");
        recepcionista.setEmail("recepcao@teste.local");
        recepcionista.setSenhaHash("hash");
        recepcionista.setNome("Recepcao");

        when(atendimentoLembreteService.criar(eq(30L), org.mockito.ArgumentMatchers.any(), eq(9L), eq(11L)))
                .thenReturn(new AtendimentoLembreteResponse(
                        8L,
                        30L,
                        "Ligar para paciente",
                        OffsetDateTime.parse("2026-07-10T10:00:00Z"),
                        "PENDENTE",
                        11L,
                        "Recepcao",
                        OffsetDateTime.parse("2026-07-01T10:00:00Z"),
                        OffsetDateTime.parse("2026-07-01T10:00:00Z")
                ));

        mockMvc.perform(post("/api/atendimentos/30/lembretes")
                        .with(user(recepcionista))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"data":"2026-07-10","hora":"10:00","mensagem":"Ligar para paciente"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(8))
                .andExpect(jsonPath("$.mensagem").value("Ligar para paciente"));
    }

}
