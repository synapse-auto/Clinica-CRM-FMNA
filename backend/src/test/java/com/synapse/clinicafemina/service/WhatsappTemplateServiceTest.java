package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Mensagem;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.domain.Recepcionista;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.EnviarTemplateWhatsappRequest;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.exception.WhatsappTemplateSendException;
import com.synapse.clinicafemina.integration.WhatsappOutboundClient;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.MensagemRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsappTemplateServiceTest {

    @Mock private AtendimentoRepository atendimentoRepository;
    @Mock private MensagemRepository mensagemRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private WhatsappOutboundClient whatsappClient;

    private WhatsappTemplateService service;
    private Atendimento atendimento;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        service = new WhatsappTemplateService(
                atendimentoRepository,
                mensagemRepository,
                usuarioRepository,
                whatsappClient,
                new WhatsappTemplateMapper(),
                Clock.fixed(Instant.parse("2026-07-16T12:00:00Z"), ZoneOffset.UTC)
        );
        Clinica clinica = new Clinica();
        clinica.setId(1L);
        Paciente paciente = new Paciente();
        paciente.setId(2L);
        paciente.setClinica(clinica);
        paciente.setTelefoneNormalizado("5511999990000");
        atendimento = new Atendimento();
        atendimento.setId(10L);
        atendimento.setClinica(clinica);
        atendimento.setPaciente(paciente);
        atendimento.setStatus("ATIVO");
        usuario = new Recepcionista();
        usuario.setId(20L);
        usuario.setClinica(clinica);
    }

    @Test
    void should_page_sort_and_cache_templates() {
        when(atendimentoRepository.findByIdAndClinicaId(10L, 1L)).thenReturn(Optional.of(atendimento));
        when(whatsappClient.configuracaoTemplatesKey()).thenReturn("graph|waba-1");
        when(whatsappClient.listarTemplatesPagina(null))
                .thenReturn(new WhatsappOutboundClient.TemplatePage(
                        List.of(template("z_pending", "PENDING")), "cursor-2"
                ));
        when(whatsappClient.listarTemplatesPagina("cursor-2"))
                .thenReturn(new WhatsappOutboundClient.TemplatePage(
                        List.of(template("a_approved", "APPROVED")), null
                ));

        var first = service.listar(10L, 1L);
        var second = service.listar(10L, 1L);

        assertEquals("a_approved", first.getFirst().nome());
        assertEquals(first, second);
        verify(whatsappClient, times(1)).listarTemplatesPagina(null);
        verify(whatsappClient, times(1)).listarTemplatesPagina("cursor-2");
    }

    @Test
    void should_stop_when_meta_repeats_cursor() {
        when(atendimentoRepository.findByIdAndClinicaId(10L, 1L)).thenReturn(Optional.of(atendimento));
        when(whatsappClient.configuracaoTemplatesKey()).thenReturn("graph|waba-1");
        when(whatsappClient.listarTemplatesPagina(null))
                .thenReturn(new WhatsappOutboundClient.TemplatePage(List.of(), "same"));
        when(whatsappClient.listarTemplatesPagina("same"))
                .thenReturn(new WhatsappOutboundClient.TemplatePage(List.of(), "same"));

        service.listar(10L, 1L);

        verify(whatsappClient, times(2)).listarTemplatesPagina(any());
    }

    @Test
    void should_stop_after_maximum_number_of_pages() {
        when(atendimentoRepository.findByIdAndClinicaId(10L, 1L)).thenReturn(Optional.of(atendimento));
        when(whatsappClient.configuracaoTemplatesKey()).thenReturn("graph|waba-1");
        AtomicInteger page = new AtomicInteger();
        when(whatsappClient.listarTemplatesPagina(any()))
                .thenAnswer(invocation -> new WhatsappOutboundClient.TemplatePage(
                        List.of(), "cursor-" + page.incrementAndGet()
                ));

        service.listar(10L, 1L);

        verify(whatsappClient, times(10)).listarTemplatesPagina(any());
    }

    @Test
    void should_not_share_cache_between_different_meta_configurations() {
        when(atendimentoRepository.findByIdAndClinicaId(10L, 1L)).thenReturn(Optional.of(atendimento));
        when(whatsappClient.configuracaoTemplatesKey()).thenReturn("graph|waba-1", "graph|waba-2");
        when(whatsappClient.listarTemplatesPagina(null))
                .thenReturn(new WhatsappOutboundClient.TemplatePage(List.of(template("primeiro", "APPROVED")), null))
                .thenReturn(new WhatsappOutboundClient.TemplatePage(List.of(template("segundo", "APPROVED")), null));

        assertEquals("primeiro", service.listar(10L, 1L).getFirst().nome());
        assertEquals("segundo", service.listar(10L, 1L).getFirst().nome());
    }

    @Test
    void should_send_and_persist_approved_template_with_metadata_and_wamid() {
        prepareApprovedTemplate();
        when(usuarioRepository.findAtivoByIdAndClinicaId(20L, 1L)).thenReturn(Optional.of(usuario));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> {
            Mensagem mensagem = invocation.getArgument(0);
            if (mensagem.getId() == null) mensagem.setId(99L);
            return mensagem;
        });
        when(whatsappClient.enviarTemplate(any(), any(), any(), any())).thenReturn("wamid-template-1");

        var result = service.enviar(10L, 1L, 20L, request());

        assertEquals("TEMPLATE", result.tipoMedia());
        assertEquals("ENVIADA", result.whatsappStatus());
        assertEquals("confirmacao", result.templateNome());
        assertEquals("pt_BR", result.templateIdioma());
        ArgumentCaptor<Mensagem> captor = ArgumentCaptor.forClass(Mensagem.class);
        verify(mensagemRepository, atLeastOnce()).save(captor.capture());
        Mensagem persisted = captor.getAllValues().getLast();
        assertEquals("wamid-template-1", persisted.getWhatsappMessageId());
        assertTrue(persisted.getConteudo().contains("16/07/2026"));
        assertEquals(20L, persisted.getRemetenteUsuario().getId());
    }

    @Test
    void should_block_double_click_without_second_meta_call() {
        prepareApprovedTemplate();
        when(usuarioRepository.findAtivoByIdAndClinicaId(20L, 1L)).thenReturn(Optional.of(usuario));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(whatsappClient.enviarTemplate(any(), any(), any(), any())).thenReturn("wamid-template-1");

        service.enviar(10L, 1L, 20L, request());
        assertThrows(IllegalStateException.class, () -> service.enviar(10L, 1L, 20L, request()));

        verify(whatsappClient, times(1)).enviarTemplate(any(), any(), any(), any());
    }

    @Test
    void should_block_cross_clinic_access() {
        when(atendimentoRepository.findByIdAndClinicaId(10L, 2L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.listar(10L, 2L));
        verify(whatsappClient, never()).listarTemplatesPagina(any());
    }

    @Test
    void should_show_but_not_send_unapproved_template() {
        when(atendimentoRepository.findByIdAndClinicaId(10L, 1L)).thenReturn(Optional.of(atendimento));
        when(usuarioRepository.findAtivoByIdAndClinicaId(20L, 1L)).thenReturn(Optional.of(usuario));
        when(whatsappClient.configuracaoTemplatesKey()).thenReturn("graph|waba-1");
        when(whatsappClient.listarTemplatesPagina(null))
                .thenReturn(new WhatsappOutboundClient.TemplatePage(
                        List.of(template("confirmacao", "REJECTED")), null
                ));

        assertThrows(BadRequestException.class, () -> service.enviar(10L, 1L, 20L, request()));
        verify(whatsappClient, never()).enviarTemplate(any(), any(), any(), any());
    }

    @Test
    void should_persist_sanitized_failure_when_meta_rejects_template_send() {
        prepareApprovedTemplate();
        when(usuarioRepository.findAtivoByIdAndClinicaId(20L, 1L)).thenReturn(Optional.of(usuario));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> {
            Mensagem mensagem = invocation.getArgument(0);
            if (mensagem.getId() == null) mensagem.setId(99L);
            return mensagem;
        });
        when(whatsappClient.enviarTemplate(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("erro Meta contendo detalhe sensivel"));

        assertThrows(WhatsappTemplateSendException.class,
                () -> service.enviar(10L, 1L, 20L, request()));

        ArgumentCaptor<Mensagem> captor = ArgumentCaptor.forClass(Mensagem.class);
        verify(mensagemRepository, atLeastOnce()).save(captor.capture());
        Mensagem failed = captor.getAllValues().getLast();
        assertEquals("FALHA", failed.getWhatsappStatus());
        assertEquals("Falha no envio do template pela Meta", failed.getMotivoFalha());
    }

    @Test
    void should_block_unknown_template_and_language() {
        prepareApprovedTemplate();
        when(usuarioRepository.findAtivoByIdAndClinicaId(20L, 1L)).thenReturn(Optional.of(usuario));

        assertThrows(BadRequestException.class, () -> service.enviar(
                10L, 1L, 20L,
                new EnviarTemplateWhatsappRequest("inexistente", "pt_BR", List.of())
        ));
        assertThrows(BadRequestException.class, () -> service.enviar(
                10L, 1L, 20L,
                new EnviarTemplateWhatsappRequest("confirmacao", "en_US", List.of())
        ));
        verify(whatsappClient, never()).enviarTemplate(any(), any(), any(), any());
    }

    private void prepareApprovedTemplate() {
        when(atendimentoRepository.findByIdAndClinicaId(10L, 1L)).thenReturn(Optional.of(atendimento));
        when(whatsappClient.configuracaoTemplatesKey()).thenReturn("graph|waba-1");
        when(whatsappClient.listarTemplatesPagina(null))
                .thenReturn(new WhatsappOutboundClient.TemplatePage(
                        List.of(template("confirmacao", "APPROVED")), null
                ));
    }

    private Map<String, Object> template(String name, String status) {
        return Map.of(
                "id", "tpl-1",
                "name", name,
                "language", "pt_BR",
                "status", status,
                "category", "UTILITY",
                "components", List.of(Map.of(
                        "type", "BODY",
                        "text", "Consulta em {{1}}"
                ))
        );
    }

    private EnviarTemplateWhatsappRequest request() {
        return new EnviarTemplateWhatsappRequest(
                "confirmacao",
                "pt_BR",
                List.of(new EnviarTemplateWhatsappRequest.Parametro(
                        "BODY", 1, null, "16/07/2026"
                ))
        );
    }
}
