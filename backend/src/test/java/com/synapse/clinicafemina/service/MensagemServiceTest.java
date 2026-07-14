package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Mensagem;
import com.synapse.clinicafemina.domain.MidiaMensagem;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.domain.Recepcionista;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.EnviarMensagemRequest;
import com.synapse.clinicafemina.integration.WhatsappOutboundClient;
import com.synapse.clinicafemina.integration.WhatsappTemplateRequiredException;
import com.synapse.clinicafemina.dto.n8n.N8nResponderRequest;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.MensagemRepository;
import com.synapse.clinicafemina.repository.MidiaMensagemRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MensagemServiceTest {

    private static final String META_WAMID_LONGO =
            "wamid.HBgMNTU1NDkxMDgyNDk4FQIAEhgWM0VCMDA1MThDODExOUJERTJEQzlEOQA=";

    @Mock
    private MensagemRepository mensagemRepository;

    @Mock
    private MidiaMensagemRepository midiaMensagemRepository;

    @Mock
    private AtendimentoRepository atendimentoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private WhatsappOutboundClient whatsappOutboundClient;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private MensagemService service;
    private Atendimento atendimento;
    private Usuario remetente;

    @BeforeEach
    void setUp() {
        service = new MensagemService(
                mensagemRepository,
                midiaMensagemRepository,
                atendimentoRepository,
                usuarioRepository,
                whatsappOutboundClient,
                rabbitTemplate
        );

        Clinica clinica = new Clinica();
        clinica.setId(9L);
        clinica.setSlug("fmna");

        Paciente paciente = new Paciente();
        paciente.setId(20L);
        paciente.setClinica(clinica);
        paciente.setTelefoneNormalizado("5544999990000");

        atendimento = new Atendimento();
        atendimento.setId(30L);
        atendimento.setClinica(clinica);
        atendimento.setPaciente(paciente);
        atendimento.setStatus("ATIVO");
        atendimento.setTratadoPorIa(true);

        remetente = new Recepcionista();
        remetente.setId(99L);
        remetente.setClinica(clinica);
    }

    @Test
    void should_persist_ai_response_and_send_whatsapp_without_human_sender() {
        when(atendimentoRepository.findByIdAndClinicaId(30L, 9L)).thenReturn(Optional.of(atendimento));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> {
            Mensagem mensagem = invocation.getArgument(0);
            if (mensagem.getId() == null) {
                mensagem.setId(77L);
            }
            return mensagem;
        });
        when(whatsappOutboundClient.enviarTexto("5544999990000", "Resposta gerada pela IA"))
                .thenReturn("wamid-ai-1");

        MensagemService.RespostaIaResultado resultado = service.responderIa(
                30L,
                9L,
                new N8nResponderRequest(20L, "Resposta gerada pela IA", "TEXTO", "N8N", true)
        );

        ArgumentCaptor<Mensagem> mensagemCaptor = ArgumentCaptor.forClass(Mensagem.class);
        verify(mensagemRepository, atLeastOnce()).save(mensagemCaptor.capture());
        Mensagem mensagemFinal = mensagemCaptor.getAllValues().getLast();
        assertEquals("SAIDA", mensagemFinal.getDirecao());
        assertEquals("IA", mensagemFinal.getRemetente());
        org.junit.jupiter.api.Assertions.assertNull(mensagemFinal.getRemetenteUsuario());
        assertEquals("TEXTO", mensagemFinal.getTipoMedia());
        assertEquals("Resposta gerada pela IA", mensagemFinal.getConteudo());
        assertEquals("ENVIADA", mensagemFinal.getWhatsappStatus());
        assertEquals("wamid-ai-1", mensagemFinal.getWhatsappMessageId());
        org.junit.jupiter.api.Assertions.assertFalse(resultado.duplicada());
        verify(usuarioRepository, never()).findAtivoByIdAndClinicaId(any(), any());
        verify(atendimentoRepository, atLeastOnce()).save(atendimento);
    }

    @Test
    void should_register_ai_response_without_sending_whatsapp_when_requested() {
        OffsetDateTime enviadoEm = OffsetDateTime.parse("2026-07-03T12:00:00Z");
        when(atendimentoRepository.findByIdAndClinicaId(30L, 9L)).thenReturn(Optional.of(atendimento));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.responderIa(
                30L,
                9L,
                new N8nResponderRequest(
                        20L,
                        "Resposta ja enviada pelo N8N",
                        "TEXTO",
                        "N8N",
                        false,
                        META_WAMID_LONGO,
                        enviadoEm
                )
        );

        ArgumentCaptor<Mensagem> mensagemCaptor = ArgumentCaptor.forClass(Mensagem.class);
        verify(mensagemRepository, atLeastOnce()).save(mensagemCaptor.capture());
        Mensagem mensagemFinal = mensagemCaptor.getAllValues().getLast();
        assertEquals("IA", mensagemFinal.getRemetente());
        assertEquals("REGISTRADA", mensagemFinal.getWhatsappStatus());
        assertEquals(META_WAMID_LONGO, mensagemFinal.getWhatsappMessageId());
        assertEquals(enviadoEm, mensagemFinal.getDataHora());
        verify(whatsappOutboundClient, never()).validarConfiguracao();
        verify(whatsappOutboundClient, never()).enviarTexto(any(), any());
    }

    @Test
    void should_return_existing_ai_response_when_whatsapp_message_id_is_retried() {
        OffsetDateTime enviadoEm = OffsetDateTime.parse("2026-07-03T12:00:00Z");
        Mensagem existente = new Mensagem();
        existente.setId(88L);
        existente.setAtendimento(atendimento);
        existente.setDirecao("SAIDA");
        existente.setRemetente("IA");
        existente.setTipoMedia("TEXTO");
        existente.setConteudo("Resposta ja registrada");
        existente.setConteudoPrevia("Resposta ja registrada");
        existente.setWhatsappStatus("REGISTRADA");
        existente.setWhatsappMessageId(META_WAMID_LONGO);
        existente.setDataHora(enviadoEm);

        when(atendimentoRepository.findByIdAndClinicaId(30L, 9L)).thenReturn(Optional.of(atendimento));
        when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(9L, META_WAMID_LONGO))
                .thenReturn(Optional.of(existente));

        MensagemService.RespostaIaResultado resultado = service.responderIa(
                30L,
                9L,
                new N8nResponderRequest(
                        20L,
                        "Resposta ja registrada",
                        "TEXTO",
                        "N8N",
                        false,
                        META_WAMID_LONGO,
                        enviadoEm
                )
        );

        org.junit.jupiter.api.Assertions.assertTrue(resultado.duplicada());
        assertEquals(88L, resultado.mensagem().id());
        verify(mensagemRepository, never()).save(any(Mensagem.class));
        verify(whatsappOutboundClient, never()).enviarTexto(any(), any());
    }

    @Test
    void should_register_each_ai_response_callback_when_whatsapp_message_id_is_absent() {
        OffsetDateTime enviadoEm = OffsetDateTime.parse("2026-07-03T12:00:00Z");
        when(atendimentoRepository.findByIdAndClinicaId(30L, 9L)).thenReturn(Optional.of(atendimento));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MensagemService.RespostaIaResultado primeira = service.responderIa(
                30L,
                9L,
                new N8nResponderRequest(
                        20L,
                        "Resposta sem chave externa",
                        "TEXTO",
                        "N8N",
                        false,
                        null,
                        enviadoEm
                )
        );
        MensagemService.RespostaIaResultado segunda = service.responderIa(
                30L,
                9L,
                new N8nResponderRequest(
                        20L,
                        "Resposta sem chave externa",
                        "TEXTO",
                        "N8N",
                        false,
                        null,
                        enviadoEm
                )
        );

        org.junit.jupiter.api.Assertions.assertFalse(primeira.duplicada());
        org.junit.jupiter.api.Assertions.assertFalse(segunda.duplicada());
        verify(mensagemRepository, never()).findByClinicaIdAndWhatsappMessageId(any(), any());
        verify(mensagemRepository, atLeastOnce()).save(any(Mensagem.class));
        verify(whatsappOutboundClient, never()).enviarTexto(any(), any());
    }

    @Test
    void should_keep_ai_response_preview_within_column_limit_when_message_is_long() {
        when(atendimentoRepository.findByIdAndClinicaId(30L, 9L)).thenReturn(Optional.of(atendimento));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.responderIa(
                30L,
                9L,
                new N8nResponderRequest(
                        20L,
                        "Resposta automatica com texto maior que sessenta caracteres para validar previa.",
                        "TEXTO",
                        "N8N",
                        false,
                        META_WAMID_LONGO,
                        OffsetDateTime.parse("2026-07-03T12:00:00Z")
                )
        );

        ArgumentCaptor<Mensagem> mensagemCaptor = ArgumentCaptor.forClass(Mensagem.class);
        verify(mensagemRepository, atLeastOnce()).save(mensagemCaptor.capture());
        Mensagem mensagemFinal = mensagemCaptor.getAllValues().getLast();
        assertEquals(60, mensagemFinal.getConteudoPrevia().length());
    }

    @Test
    void should_reject_ai_response_for_patient_not_linked_to_attendance() {
        when(atendimentoRepository.findByIdAndClinicaId(30L, 9L)).thenReturn(Optional.of(atendimento));

        org.junit.jupiter.api.Assertions.assertThrows(
                BadRequestException.class,
                () -> service.responderIa(
                        30L,
                        9L,
                        new N8nResponderRequest(999L, "Resposta", "TEXTO", "N8N", true)
                )
        );
    }

    @Test
    void should_reject_ai_response_when_attendance_is_in_human_mode() {
        atendimento.setTratadoPorIa(false);
        when(atendimentoRepository.findByIdAndClinicaId(30L, 9L)).thenReturn(Optional.of(atendimento));

        IllegalStateException error = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> service.responderIa(
                        30L,
                        9L,
                        new N8nResponderRequest(20L, "Resposta", "TEXTO", "N8N", true)
                )
        );

        assertEquals("Atendimento esta em modo humano", error.getMessage());
        verify(mensagemRepository, never()).save(any(Mensagem.class));
    }

    @Test
    void should_persist_human_text_message_with_atendente_sender() {
        when(usuarioRepository.findAtivoByIdAndClinicaId(99L, 9L))
                .thenReturn(Optional.of(remetente));
        when(atendimentoRepository.findByIdAndClinicaId(30L, 9L)).thenReturn(Optional.of(atendimento));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(whatsappOutboundClient.enviarTexto("5544999990000", "Oi, tudo bem?"))
                .thenReturn("wamid-1");

        service.enviar(
                30L,
                9L,
                new EnviarMensagemRequest("TEXTO", "Oi, tudo bem?"),
                99L
        );

        ArgumentCaptor<Mensagem> mensagemCaptor = ArgumentCaptor.forClass(Mensagem.class);
        verify(mensagemRepository, atLeastOnce()).save(mensagemCaptor.capture());
        mensagemCaptor.getAllValues().forEach(mensagem ->
                assertEquals("ATENDENTE", mensagem.getRemetente()));
        mensagemCaptor.getAllValues().forEach(mensagem ->
                assertEquals(99L, mensagem.getRemetenteUsuario().getId()));
    }

    @Test
    void should_explain_template_requirement_when_meta_rejects_first_outbound_message() {
        when(usuarioRepository.findAtivoByIdAndClinicaId(99L, 9L))
                .thenReturn(Optional.of(remetente));
        when(atendimentoRepository.findByIdAndClinicaId(30L, 9L)).thenReturn(Optional.of(atendimento));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(whatsappOutboundClient.enviarTexto("5544999990000", "Mensagem inicial"))
                .thenThrow(new WhatsappTemplateRequiredException());

        service.enviar(
                30L,
                9L,
                new EnviarMensagemRequest("TEXTO", "Mensagem inicial"),
                99L
        );

        ArgumentCaptor<Mensagem> mensagemCaptor = ArgumentCaptor.forClass(Mensagem.class);
        verify(mensagemRepository, atLeastOnce()).save(mensagemCaptor.capture());
        Mensagem mensagemFinal = mensagemCaptor.getAllValues().getLast();
        assertEquals("FALHA", mensagemFinal.getWhatsappStatus());
        assertEquals(
                "A Meta exige template aprovado para iniciar conversa ativa ou responder fora da janela de 24h.",
                mensagemFinal.getMotivoFalha()
        );
        org.mockito.Mockito.verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void should_persist_human_media_message_with_atendente_sender() {
        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo",
                "exame.png",
                "image/png",
                new byte[] {1, 2, 3}
        );
        when(usuarioRepository.findAtivoByIdAndClinicaId(99L, 9L))
                .thenReturn(Optional.of(remetente));
        when(atendimentoRepository.findByIdAndClinicaId(30L, 9L)).thenReturn(Optional.of(atendimento));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(whatsappOutboundClient.uploadMidia(any(), eq("image/png"), eq("exame.png")))
                .thenReturn("media-1");
        when(whatsappOutboundClient.enviarMidia("5544999990000", "imagem", "media-1"))
                .thenReturn("wamid-2");

        service.enviarMidia(30L, 9L, arquivo, 99L);

        ArgumentCaptor<Mensagem> mensagemCaptor = ArgumentCaptor.forClass(Mensagem.class);
        verify(mensagemRepository, atLeastOnce()).save(mensagemCaptor.capture());
        mensagemCaptor.getAllValues().forEach(mensagem ->
                assertEquals("ATENDENTE", mensagem.getRemetente()));
        verify(midiaMensagemRepository).save(any());
    }

    @Test
    void should_download_media_successfully() {
        byte[] content = new byte[] {1, 2, 3};
        com.synapse.clinicafemina.integration.WhatsappOutboundClient.MidiaBaixada mockBaixada = 
                new com.synapse.clinicafemina.integration.WhatsappOutboundClient.MidiaBaixada(content, "image/png");
        when(whatsappOutboundClient.baixarMidia("media-id")).thenReturn(mockBaixada);

        com.synapse.clinicafemina.integration.WhatsappOutboundClient.MidiaBaixada result = service.baixarBinarioMidia("media-id");

        org.junit.jupiter.api.Assertions.assertNotNull(result);
        assertEquals(content, result.bytes());
        assertEquals("image/png", result.mimeType());
    }

    @Test
    void should_return_null_when_download_fails() {
        when(whatsappOutboundClient.baixarMidia("media-id")).thenReturn(null);

        com.synapse.clinicafemina.integration.WhatsappOutboundClient.MidiaBaixada result = service.baixarBinarioMidia("media-id");

        org.junit.jupiter.api.Assertions.assertNull(result);
    }

    @Test
    void should_return_null_when_media_id_is_blank() {
        org.junit.jupiter.api.Assertions.assertNull(service.baixarBinarioMidia(""));
        org.junit.jupiter.api.Assertions.assertNull(service.baixarBinarioMidia(null));
    }

    @Test
    void should_serve_persisted_media_without_downloading_from_meta_again() {
        byte[] persisted = new byte[] {9, 8, 7};
        MidiaMensagem midia = new MidiaMensagem();
        midia.setS3Chave(persisted);
        midia.setMimeType("image/jpeg");

        WhatsappOutboundClient.MidiaBaixada result = service.obterBinarioMidia(midia);

        assertArrayEquals(persisted, result.bytes());
        assertEquals("image/jpeg", result.mimeType());
        verify(whatsappOutboundClient, never()).baixarMidia(any());
    }

    @Test
    void should_throw_not_found_exception_when_media_unauthorized_or_missing() {
        when(midiaMensagemRepository.findAutorizada(100L, 30L, 9L)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(
                com.synapse.clinicafemina.exception.NotFoundException.class,
                () -> service.buscarMidia(30L, 100L, 9L)
        );
    }

    @Test
    void should_return_media_metadata_when_authorized() {
        com.synapse.clinicafemina.domain.MidiaMensagem midia = new com.synapse.clinicafemina.domain.MidiaMensagem();
        midia.setWhatsappMediaId("media-123");
        midia.setMimeType("image/png");
        when(midiaMensagemRepository.findAutorizada(100L, 30L, 9L)).thenReturn(Optional.of(midia));

        com.synapse.clinicafemina.domain.MidiaMensagem result = service.buscarMidia(30L, 100L, 9L);

        org.junit.jupiter.api.Assertions.assertNotNull(result);
        assertEquals("media-123", result.getWhatsappMediaId());
        assertEquals("image/png", result.getMimeType());
    }
}
