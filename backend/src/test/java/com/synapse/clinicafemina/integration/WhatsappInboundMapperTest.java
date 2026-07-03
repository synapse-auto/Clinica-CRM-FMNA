package com.synapse.clinicafemina.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Mensagem;
import com.synapse.clinicafemina.domain.MidiaMensagem;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.integration.WhatsappOutboundClient.MidiaBaixada;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.ClinicaRepository;
import com.synapse.clinicafemina.repository.MensagemRepository;
import com.synapse.clinicafemina.repository.MidiaMensagemRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import com.synapse.clinicafemina.service.N8nEventService;
import com.synapse.clinicafemina.service.AtendimentoNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.env.Environment;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsappInboundMapperTest {

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private AtendimentoRepository atendimentoRepository;

    @Mock
    private MensagemRepository mensagemRepository;

    @Mock
    private MidiaMensagemRepository midiaMensagemRepository;

    @Mock
    private ClinicaRepository clinicaRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private N8nEventService n8nEventService;

    @Mock
    private AtendimentoNotificationService notificationService;

    @Mock
    private Environment environment;

    @Mock
    private WhatsappOutboundClient whatsappOutboundClient;

    private WhatsappInboundMapper mapper;
    private Clinica clinica;

    @BeforeEach
    void setUp() {
        mapper = new WhatsappInboundMapper(
                pacienteRepository,
                atendimentoRepository,
                mensagemRepository,
                midiaMensagemRepository,
                clinicaRepository,
                rabbitTemplate,
                n8nEventService,
                notificationService,
                new ObjectMapper(),
                new WhatsappInboundPayloadParser(),
                environment,
                whatsappOutboundClient
        );

        clinica = new Clinica();
        clinica.setId(2L);
        clinica.setNome("UltraMedical");
        clinica.setWhatsappPhoneNumberId("phone-ultra");
    }

    @Test
    void should_resolve_clinic_by_phone_number_id_when_processing_inbound_message() {
        when(clinicaRepository.findByWhatsappPhoneNumberId("phone-ultra")).thenReturn(Optional.of(clinica));
        when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(2L, "wamid-1")).thenReturn(Optional.empty());
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizado(2L, "5511999990000"))
                .thenReturn(Optional.empty());
        when(pacienteRepository.save(any(Paciente.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(atendimentoRepository.findAtivo(2L, null)).thenReturn(Optional.empty());
        when(atendimentoRepository.existeEncerradoDesde(any(), any(), any())).thenReturn(false);
        when(atendimentoRepository.save(any(Atendimento.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mapper.processarMensagemTexto(validValuePayload("phone-ultra"));

        ArgumentCaptor<Paciente> pacienteCaptor = ArgumentCaptor.forClass(Paciente.class);
        ArgumentCaptor<Atendimento> atendimentoCaptor = ArgumentCaptor.forClass(Atendimento.class);
        verify(pacienteRepository, atLeastOnce()).save(pacienteCaptor.capture());
        verify(atendimentoRepository, atLeastOnce()).save(atendimentoCaptor.capture());
        assertSame(clinica, pacienteCaptor.getAllValues().getFirst().getClinica());
        assertSame(clinica, atendimentoCaptor.getAllValues().getFirst().getClinica());
        assertEquals(ExternalProviderType.WHATSAPP, pacienteCaptor.getAllValues().getFirst().getExternalSource());
        assertEquals("5511999990000", pacienteCaptor.getAllValues().getFirst().getExternalId());
        verify(n8nEventService).criarPayload(eq(clinica), eq("novo_lead"), any(), any(), any(), any());
        verify(n8nEventService).criarPayloadMensagemRecebida(eq(clinica), any(), any(), any());
        verify(notificationService).notificarNovaMensagem(any(), any());
    }

    @Test
    void should_emit_n8n_message_received_after_persisting_inbound_message() {
        clinica.setSlug("ultramedical");
        clinica.setUsaN8n(true);
        clinica.setN8nWebhookUrl("https://n8n.example/webhook");
        byte[] rawBody = fullMetaPayload("phone-ultra").getBytes(StandardCharsets.UTF_8);

        Paciente paciente = new Paciente();
        paciente.setId(20L);
        paciente.setClinica(clinica);
        paciente.setNomeBusca("PACIENTE");
        paciente.setTelefoneNormalizado("5511999990000");

        Atendimento atendimento = new Atendimento();
        atendimento.setId(30L);
        atendimento.setClinica(clinica);
        atendimento.setPaciente(paciente);
        atendimento.setNaoLidas(0);

        when(clinicaRepository.findByWhatsappPhoneNumberId("phone-ultra")).thenReturn(Optional.of(clinica));
        when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(2L, "wamid-1")).thenReturn(Optional.empty());
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizado(2L, "5511999990000"))
                .thenReturn(Optional.of(paciente));
        when(atendimentoRepository.findAtivo(2L, 20L)).thenReturn(Optional.of(atendimento));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> {
            Mensagem mensagem = invocation.getArgument(0);
            mensagem.setId(40L);
            mensagem.setCriadoEm(OffsetDateTime.parse("2026-06-15T12:00:00Z"));
            return mensagem;
        });
        when(atendimentoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pacienteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        mapper.processarMensagemTexto(validValuePayload("phone-ultra"), rawBody);

        ArgumentCaptor<Mensagem> mensagemCaptor = ArgumentCaptor.forClass(Mensagem.class);
        ArgumentCaptor<N8nEventService.MetaWebhookContext> contextCaptor =
                ArgumentCaptor.forClass(N8nEventService.MetaWebhookContext.class);
        InOrder ordem = inOrder(mensagemRepository, n8nEventService);
        ordem.verify(mensagemRepository).save(mensagemCaptor.capture());
        ordem.verify(n8nEventService)
                .enviarPayloadMetaOriginal(eq(clinica), eq(rawBody), contextCaptor.capture());
        assertEquals("mensagem_recebida", contextCaptor.getValue().evento());
        assertEquals(30L, contextCaptor.getValue().atendimentoId());
        assertEquals(20L, contextCaptor.getValue().pacienteId());
        assertEquals(40L, contextCaptor.getValue().mensagemId());
        verify(n8nEventService, never()).criarPayloadMensagemRecebida(any(), any(), any(), any());
    }

    @Test
    void should_emit_n8n_for_each_inbound_message_in_same_meta_payload_without_repeating_full_batch() {
        clinica.setSlug("ultramedical");
        clinica.setUsaN8n(true);
        clinica.setN8nWebhookUrl("https://n8n.example/webhook");
        byte[] rawBody = fullMetaPayloadWithThreeMessages("phone-ultra").getBytes(StandardCharsets.UTF_8);

        Paciente paciente = new Paciente();
        paciente.setId(20L);
        paciente.setClinica(clinica);
        paciente.setNomeBusca("PACIENTE");
        paciente.setTelefoneNormalizado("5511999990000");

        Atendimento atendimento = new Atendimento();
        atendimento.setId(30L);
        atendimento.setClinica(clinica);
        atendimento.setPaciente(paciente);
        atendimento.setNaoLidas(0);

        when(clinicaRepository.findByWhatsappPhoneNumberId("phone-ultra")).thenReturn(Optional.of(clinica));
        when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(2L, "wamid-1")).thenReturn(Optional.empty());
        when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(2L, "wamid-2")).thenReturn(Optional.empty());
        when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(2L, "wamid-3")).thenReturn(Optional.empty());
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizado(2L, "5511999990000"))
                .thenReturn(Optional.of(paciente));
        when(atendimentoRepository.findAtivo(2L, 20L)).thenReturn(Optional.of(atendimento));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> {
            Mensagem mensagem = invocation.getArgument(0);
            mensagem.setId(switch (mensagem.getWhatsappMessageId()) {
                case "wamid-1" -> 40L;
                case "wamid-2" -> 41L;
                case "wamid-3" -> 42L;
                default -> throw new IllegalArgumentException("wamid inesperado");
            });
            return mensagem;
        });
        when(atendimentoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pacienteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        mapper.processarMensagemTexto(valuePayloadWithThreeMessages("phone-ultra"), rawBody);

        ArgumentCaptor<Mensagem> mensagemCaptor = ArgumentCaptor.forClass(Mensagem.class);
        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<N8nEventService.MetaWebhookContext> contextCaptor =
                ArgumentCaptor.forClass(N8nEventService.MetaWebhookContext.class);
        verify(mensagemRepository, times(3)).save(mensagemCaptor.capture());
        verify(n8nEventService, times(3))
                .enviarPayloadMetaOriginal(eq(clinica), payloadCaptor.capture(), contextCaptor.capture());

        assertEquals(List.of("wamid-1", "wamid-2", "wamid-3"), mensagemCaptor.getAllValues().stream()
                .map(Mensagem::getWhatsappMessageId)
                .toList());
        assertEquals(List.of(40L, 41L, 42L), contextCaptor.getAllValues().stream()
                .map(N8nEventService.MetaWebhookContext::mensagemId)
                .toList());

        List<String> bodies = payloadCaptor.getAllValues().stream()
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
                .toList();
        assertTrue(bodies.get(0).contains("\"wamid-1\""));
        assertFalse(bodies.get(0).contains("\"wamid-2\""));
        assertFalse(bodies.get(0).contains("\"wamid-3\""));
        assertTrue(bodies.get(1).contains("\"wamid-2\""));
        assertFalse(bodies.get(1).contains("\"wamid-1\""));
        assertFalse(bodies.get(1).contains("\"wamid-3\""));
        assertTrue(bodies.get(2).contains("\"wamid-3\""));
        assertFalse(bodies.get(2).contains("\"wamid-1\""));
        assertFalse(bodies.get(2).contains("\"wamid-2\""));
        assertEquals(List.of("wamid-1", "wamid-2", "wamid-3"), contextCaptor.getAllValues().stream()
                .map(N8nEventService.MetaWebhookContext::whatsappMessageId)
                .toList());
    }

    @Test
    void should_ignore_meta_retry_without_repersisting_or_reemitting_messages() {
        clinica.setSlug("ultramedical");
        clinica.setUsaN8n(true);
        clinica.setN8nWebhookUrl("https://n8n.example/webhook");
        byte[] rawBody = fullMetaPayloadWithThreeMessages("phone-ultra").getBytes(StandardCharsets.UTF_8);

        Paciente paciente = new Paciente();
        paciente.setId(20L);
        paciente.setClinica(clinica);
        paciente.setNomeBusca("PACIENTE");
        paciente.setTelefoneNormalizado("5511999990000");

        Atendimento atendimento = new Atendimento();
        atendimento.setId(30L);
        atendimento.setClinica(clinica);
        atendimento.setPaciente(paciente);
        atendimento.setNaoLidas(0);

        Mensagem existente1 = mensagemExistente("wamid-1");
        Mensagem existente2 = mensagemExistente("wamid-2");
        Mensagem existente3 = mensagemExistente("wamid-3");

        when(clinicaRepository.findByWhatsappPhoneNumberId("phone-ultra")).thenReturn(Optional.of(clinica));
        when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(2L, "wamid-1"))
                .thenReturn(Optional.empty(), Optional.of(existente1));
        when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(2L, "wamid-2"))
                .thenReturn(Optional.empty(), Optional.of(existente2));
        when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(2L, "wamid-3"))
                .thenReturn(Optional.empty(), Optional.of(existente3));
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizado(2L, "5511999990000"))
                .thenReturn(Optional.of(paciente));
        when(atendimentoRepository.findAtivo(2L, 20L)).thenReturn(Optional.of(atendimento));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> {
            Mensagem mensagem = invocation.getArgument(0);
            mensagem.setId(switch (mensagem.getWhatsappMessageId()) {
                case "wamid-1" -> 40L;
                case "wamid-2" -> 41L;
                case "wamid-3" -> 42L;
                default -> throw new IllegalArgumentException("wamid inesperado");
            });
            return mensagem;
        });
        when(atendimentoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pacienteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        mapper.processarMensagemTexto(valuePayloadWithThreeMessages("phone-ultra"), rawBody);
        mapper.processarMensagemTexto(valuePayloadWithThreeMessages("phone-ultra"), rawBody);

        verify(mensagemRepository, times(3)).save(any(Mensagem.class));
        verify(n8nEventService, times(3)).enviarPayloadMetaOriginal(eq(clinica), any(), any());
    }

    @Test
    void should_emit_n8n_for_audio_even_when_media_download_fails() {
        clinica.setSlug("ultramedical");
        clinica.setUsaN8n(true);
        clinica.setN8nWebhookUrl("https://n8n.example/webhook");
        byte[] rawBody = fullMetaPayload("phone-ultra").getBytes(StandardCharsets.UTF_8);

        Paciente paciente = new Paciente();
        paciente.setId(20L);
        paciente.setClinica(clinica);
        paciente.setNomeBusca("PACIENTE");
        paciente.setTelefoneNormalizado("5511999990000");

        Atendimento atendimento = new Atendimento();
        atendimento.setId(30L);
        atendimento.setClinica(clinica);
        atendimento.setPaciente(paciente);
        atendimento.setNaoLidas(0);

        when(clinicaRepository.findByWhatsappPhoneNumberId("phone-ultra")).thenReturn(Optional.of(clinica));
        when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(2L, "wamid-audio")).thenReturn(Optional.empty());
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizado(2L, "5511999990000"))
                .thenReturn(Optional.of(paciente));
        when(atendimentoRepository.findAtivo(2L, 20L)).thenReturn(Optional.of(atendimento));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> {
            Mensagem mensagem = invocation.getArgument(0);
            mensagem.setId(40L);
            return mensagem;
        });
        when(whatsappOutboundClient.baixarMidia("media-audio")).thenThrow(new IllegalStateException("meta indisponivel"));
        when(atendimentoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pacienteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        mapper.processarMensagemTexto(Map.of(
                "metadata", Map.of("phone_number_id", "phone-ultra"),
                "contacts", List.of(Map.of(
                        "wa_id", "5511999990000",
                        "profile", Map.of("name", "Paciente Teste")
                )),
                "messages", List.of(Map.of(
                        "id", "wamid-audio",
                        "timestamp", "1781455200",
                        "type", "audio",
                        "audio", Map.of(
                                "id", "media-audio",
                                "mime_type", "audio/ogg"
                        )
                ))
        ), rawBody);

        ArgumentCaptor<MidiaMensagem> midiaCaptor = ArgumentCaptor.forClass(MidiaMensagem.class);
        ArgumentCaptor<N8nEventService.MetaWebhookContext> contextCaptor =
                ArgumentCaptor.forClass(N8nEventService.MetaWebhookContext.class);
        verify(midiaMensagemRepository).save(midiaCaptor.capture());
        verify(n8nEventService).enviarPayloadMetaOriginal(eq(clinica), eq(rawBody), contextCaptor.capture());
        assertEquals("AUDIO", midiaCaptor.getValue().getTipoMedia());
        assertEquals(0L, midiaCaptor.getValue().getTamanhoBytes());
        assertEquals(40L, contextCaptor.getValue().mensagemId());
    }

    @Test
    void should_persist_unknown_meta_message_type_and_emit_original_payload_to_n8n() {
        clinica.setSlug("ultramedical");
        clinica.setUsaN8n(true);
        clinica.setN8nWebhookUrl("https://n8n.example/webhook");
        byte[] rawBody = fullMetaPayload("phone-ultra").getBytes(StandardCharsets.UTF_8);

        Paciente paciente = new Paciente();
        paciente.setId(20L);
        paciente.setClinica(clinica);
        paciente.setNomeBusca("PACIENTE");
        paciente.setTelefoneNormalizado("5511999990000");

        Atendimento atendimento = new Atendimento();
        atendimento.setId(30L);
        atendimento.setClinica(clinica);
        atendimento.setPaciente(paciente);
        atendimento.setNaoLidas(0);

        when(clinicaRepository.findByWhatsappPhoneNumberId("phone-ultra")).thenReturn(Optional.of(clinica));
        when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(2L, "wamid-sticker")).thenReturn(Optional.empty());
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizado(2L, "5511999990000"))
                .thenReturn(Optional.of(paciente));
        when(atendimentoRepository.findAtivo(2L, 20L)).thenReturn(Optional.of(atendimento));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> {
            Mensagem mensagem = invocation.getArgument(0);
            mensagem.setId(40L);
            return mensagem;
        });
        when(atendimentoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pacienteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        mapper.processarMensagemTexto(Map.of(
                "metadata", Map.of("phone_number_id", "phone-ultra"),
                "contacts", List.of(Map.of(
                        "wa_id", "5511999990000",
                        "profile", Map.of("name", "Paciente Teste")
                )),
                "messages", List.of(Map.of(
                        "id", "wamid-sticker",
                        "timestamp", "1781455200",
                        "type", "sticker",
                        "sticker", Map.of("id", "media-sticker")
                ))
        ), rawBody);

        ArgumentCaptor<Mensagem> mensagemCaptor = ArgumentCaptor.forClass(Mensagem.class);
        ArgumentCaptor<N8nEventService.MetaWebhookContext> contextCaptor =
                ArgumentCaptor.forClass(N8nEventService.MetaWebhookContext.class);
        verify(mensagemRepository).save(mensagemCaptor.capture());
        verify(n8nEventService).enviarPayloadMetaOriginal(eq(clinica), eq(rawBody), contextCaptor.capture());
        assertEquals("OUTRO", mensagemCaptor.getValue().getTipoMedia());
        assertEquals(40L, contextCaptor.getValue().mensagemId());
    }

    @Test
    void should_ignore_audio_retry_without_reemitting_to_n8n() {
        clinica.setSlug("ultramedical");
        clinica.setUsaN8n(true);
        clinica.setN8nWebhookUrl("https://n8n.example/webhook");
        byte[] rawBody = fullMetaPayload("phone-ultra").getBytes(StandardCharsets.UTF_8);

        Paciente paciente = new Paciente();
        paciente.setId(20L);
        paciente.setClinica(clinica);
        paciente.setNomeBusca("PACIENTE");
        paciente.setTelefoneNormalizado("5511999990000");

        Atendimento atendimento = new Atendimento();
        atendimento.setId(30L);
        atendimento.setClinica(clinica);
        atendimento.setPaciente(paciente);
        atendimento.setNaoLidas(0);

        when(clinicaRepository.findByWhatsappPhoneNumberId("phone-ultra")).thenReturn(Optional.of(clinica));
        when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(2L, "wamid-audio"))
                .thenReturn(Optional.empty(), Optional.of(mensagemExistente("wamid-audio")));
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizado(2L, "5511999990000"))
                .thenReturn(Optional.of(paciente));
        when(atendimentoRepository.findAtivo(2L, 20L)).thenReturn(Optional.of(atendimento));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> {
            Mensagem mensagem = invocation.getArgument(0);
            mensagem.setId(40L);
            return mensagem;
        });
        when(whatsappOutboundClient.baixarMidia("media-audio")).thenThrow(new IllegalStateException("meta indisponivel"));
        when(atendimentoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pacienteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> audioPayload = Map.of(
                "metadata", Map.of("phone_number_id", "phone-ultra"),
                "contacts", List.of(Map.of(
                        "wa_id", "5511999990000",
                        "profile", Map.of("name", "Paciente Teste")
                )),
                "messages", List.of(Map.of(
                        "id", "wamid-audio",
                        "timestamp", "1781455200",
                        "type", "audio",
                        "audio", Map.of(
                                "id", "media-audio",
                                "mime_type", "audio/ogg"
                        )
                ))
        );

        mapper.processarMensagemTexto(audioPayload, rawBody);
        mapper.processarMensagemTexto(audioPayload, rawBody);

        verify(mensagemRepository, times(1)).save(any(Mensagem.class));
        verify(midiaMensagemRepository, times(1)).save(any(MidiaMensagem.class));
        verify(n8nEventService, times(1)).enviarPayloadMetaOriginal(eq(clinica), eq(rawBody), any());
    }

    @Test
    void should_ignore_unknown_type_retry_without_reemitting_to_n8n() {
        clinica.setSlug("ultramedical");
        clinica.setUsaN8n(true);
        clinica.setN8nWebhookUrl("https://n8n.example/webhook");
        byte[] rawBody = fullMetaPayload("phone-ultra").getBytes(StandardCharsets.UTF_8);

        Paciente paciente = new Paciente();
        paciente.setId(20L);
        paciente.setClinica(clinica);
        paciente.setNomeBusca("PACIENTE");
        paciente.setTelefoneNormalizado("5511999990000");

        Atendimento atendimento = new Atendimento();
        atendimento.setId(30L);
        atendimento.setClinica(clinica);
        atendimento.setPaciente(paciente);
        atendimento.setNaoLidas(0);

        when(clinicaRepository.findByWhatsappPhoneNumberId("phone-ultra")).thenReturn(Optional.of(clinica));
        when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(2L, "wamid-sticker"))
                .thenReturn(Optional.empty(), Optional.of(mensagemExistente("wamid-sticker")));
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizado(2L, "5511999990000"))
                .thenReturn(Optional.of(paciente));
        when(atendimentoRepository.findAtivo(2L, 20L)).thenReturn(Optional.of(atendimento));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> {
            Mensagem mensagem = invocation.getArgument(0);
            mensagem.setId(40L);
            return mensagem;
        });
        when(atendimentoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pacienteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> stickerPayload = Map.of(
                "metadata", Map.of("phone_number_id", "phone-ultra"),
                "contacts", List.of(Map.of(
                        "wa_id", "5511999990000",
                        "profile", Map.of("name", "Paciente Teste")
                )),
                "messages", List.of(Map.of(
                        "id", "wamid-sticker",
                        "timestamp", "1781455200",
                        "type", "sticker",
                        "sticker", Map.of("id", "media-sticker")
                ))
        );

        mapper.processarMensagemTexto(stickerPayload, rawBody);
        mapper.processarMensagemTexto(stickerPayload, rawBody);

        verify(mensagemRepository, times(1)).save(any(Mensagem.class));
        verify(n8nEventService, times(1)).enviarPayloadMetaOriginal(eq(clinica), eq(rawBody), any());
    }

    @Test
    void should_resolve_clinic_by_phone_number_id_when_processing_status_update() {
        Atendimento atendimento = new Atendimento();
        atendimento.setClinica(clinica);

        Mensagem mensagem = new Mensagem();
        mensagem.setAtendimento(atendimento);
        mensagem.setWhatsappMessageId("wamid-1");

        when(clinicaRepository.findByWhatsappPhoneNumberId("phone-ultra")).thenReturn(Optional.of(clinica));
        when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(2L, "wamid-1")).thenReturn(Optional.of(mensagem));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Mensagem> result = mapper.processarStatusUpdate(validValuePayload("phone-ultra"), Map.of(
                "id", "wamid-1",
                "status", "read",
                "timestamp", "1781455200"
        ));

        assertTrue(result.isPresent());
        assertEquals("READ", result.get().getWhatsappStatus());
        verify(mensagemRepository).save(mensagem);
        verify(n8nEventService, never()).enviarPayloadMetaOriginal(any(), any(), any());
    }

    @Test
    void should_ignore_payload_when_phone_number_id_is_unknown() {
        when(clinicaRepository.findByWhatsappPhoneNumberId("unknown-phone")).thenReturn(Optional.empty());

        mapper.processarMensagemTexto(validValuePayload("unknown-phone"));

        verify(pacienteRepository, never()).save(any(Paciente.class));
        verify(atendimentoRepository, never()).save(any(Atendimento.class));
        verify(mensagemRepository, never()).save(any(Mensagem.class));
    }

    @Test
    void should_persist_received_document_metadata() {
        when(clinicaRepository.findByWhatsappPhoneNumberId("phone-ultra")).thenReturn(Optional.of(clinica));
        when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(2L, "wamid-doc")).thenReturn(Optional.empty());
        Paciente paciente = new Paciente();
        paciente.setId(20L);
        paciente.setClinica(clinica);
        paciente.setNomeBusca("PACIENTE");
        paciente.setTelefoneNormalizado("5511999990000");
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizado(2L, "5511999990000"))
                .thenReturn(Optional.of(paciente));
        Atendimento atendimento = new Atendimento();
        atendimento.setId(30L);
        atendimento.setClinica(clinica);
        atendimento.setPaciente(paciente);
        atendimento.setNaoLidas(0);
        when(atendimentoRepository.findAtivo(2L, 20L)).thenReturn(Optional.of(atendimento));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> {
            Mensagem mensagem = invocation.getArgument(0);
            mensagem.setId(40L);
            return mensagem;
        });
        byte[] documentBytes = new byte[] {1, 3, 5, 7};
        when(whatsappOutboundClient.baixarMidia("media-doc"))
                .thenReturn(new MidiaBaixada(documentBytes, "application/pdf"));
        when(atendimentoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pacienteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        mapper.processarMensagemTexto(Map.of(
                "metadata", Map.of("phone_number_id", "phone-ultra"),
                "contacts", List.of(Map.of(
                        "wa_id", "5511999990000",
                        "profile", Map.of("name", "Paciente Teste")
                )),
                "messages", List.of(Map.of(
                        "id", "wamid-doc",
                        "timestamp", "1781455200",
                        "type", "document",
                        "document", Map.of(
                                "id", "media-doc",
                                "filename", "guia.pdf",
                                "mime_type", "application/pdf"
                        )
                ))
        ));

        ArgumentCaptor<MidiaMensagem> midiaCaptor = ArgumentCaptor.forClass(MidiaMensagem.class);
        verify(midiaMensagemRepository).save(midiaCaptor.capture());
        assertArrayEquals(documentBytes, midiaCaptor.getValue().getS3Chave());
        assertEquals("database", midiaCaptor.getValue().getS3Bucket());
        assertEquals(documentBytes.length, midiaCaptor.getValue().getTamanhoBytes());
        verify(notificationService).notificarNovaMensagem(eq(atendimento), any());
    }

    @Test
    void should_resolve_clinic_when_phone_id_is_already_in_db() {
        when(clinicaRepository.findByWhatsappPhoneNumberId("phone-ultra")).thenReturn(Optional.of(clinica));
        when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(2L, "wamid-1")).thenReturn(Optional.empty());
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizado(2L, "5511999990000")).thenReturn(Optional.empty());
        when(pacienteRepository.save(any(Paciente.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(atendimentoRepository.findAtivo(2L, null)).thenReturn(Optional.empty());
        when(atendimentoRepository.existeEncerradoDesde(any(), any(), any())).thenReturn(false);
        when(atendimentoRepository.save(any(Atendimento.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mapper.processarMensagemTexto(validValuePayload("phone-ultra"));

        verify(clinicaRepository, never()).findBySlug(any());
        verify(clinicaRepository, never()).save(any());
    }

    @Test
    void should_resolve_and_update_clinic_when_phone_id_matches_env_but_missing_in_db() {
        org.springframework.test.util.ReflectionTestUtils.setField(mapper, "resolvedPhoneId", "env-phone-id");
        when(clinicaRepository.findByWhatsappPhoneNumberId("env-phone-id")).thenReturn(Optional.empty());
        when(environment.getProperty("app.clinic.slug", "ultramedical")).thenReturn("ultramedical");
        when(clinicaRepository.findBySlug("ultramedical")).thenReturn(Optional.of(clinica));
        
        when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(2L, "wamid-1")).thenReturn(Optional.empty());
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizado(2L, "5511999990000")).thenReturn(Optional.empty());
        when(pacienteRepository.save(any(Paciente.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(atendimentoRepository.findAtivo(2L, null)).thenReturn(Optional.empty());
        when(atendimentoRepository.existeEncerradoDesde(any(), any(), any())).thenReturn(false);
        when(atendimentoRepository.save(any(Atendimento.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mapper.processarMensagemTexto(validValuePayload("env-phone-id"));

        verify(clinicaRepository).save(clinica);
        assertEquals("env-phone-id", clinica.getWhatsappPhoneNumberId());
    }

    @Test
    void should_fail_when_phone_id_does_not_match_env_and_missing_in_db() {
        org.springframework.test.util.ReflectionTestUtils.setField(mapper, "resolvedPhoneId", "env-phone-id");
        when(clinicaRepository.findByWhatsappPhoneNumberId("wrong-phone-id")).thenReturn(Optional.empty());
        when(environment.getProperty("app.clinic.slug", "ultramedical")).thenReturn("ultramedical");

        mapper.processarMensagemTexto(validValuePayload("wrong-phone-id"));

        verify(clinicaRepository, never()).findBySlug(any());
        verify(clinicaRepository, never()).save(any());
        verify(pacienteRepository, never()).save(any());
    }

    @Test
    void should_fail_when_clinic_by_slug_not_found_on_fallback() {
        org.springframework.test.util.ReflectionTestUtils.setField(mapper, "resolvedPhoneId", "env-phone-id");
        when(clinicaRepository.findByWhatsappPhoneNumberId("env-phone-id")).thenReturn(Optional.empty());
        when(environment.getProperty("app.clinic.slug", "ultramedical")).thenReturn("ultramedical");
        when(clinicaRepository.findBySlug("ultramedical")).thenReturn(Optional.empty());

        mapper.processarMensagemTexto(validValuePayload("env-phone-id"));

        verify(clinicaRepository, never()).save(any());
        verify(pacienteRepository, never()).save(any());
    }

    private Map<String, Object> validValuePayload(String phoneNumberId) {
        return Map.of(
                "metadata", Map.of("phone_number_id", phoneNumberId),
                "contacts", List.of(Map.of(
                        "wa_id", "5511999990000",
                        "profile", Map.of("name", "Paciente Teste")
                )),
                "messages", List.of(Map.of(
                        "id", "wamid-1",
                        "timestamp", "1781455200",
                        "text", Map.of("body", "Olá")
                ))
        );
    }

    private Map<String, Object> valuePayloadWithThreeMessages(String phoneNumberId) {
        return Map.of(
                "metadata", Map.of("phone_number_id", phoneNumberId),
                "contacts", List.of(Map.of(
                        "wa_id", "5511999990000",
                        "profile", Map.of("name", "Paciente Teste")
                )),
                "messages", List.of(
                        Map.of(
                                "id", "wamid-1",
                                "from", "5511999990000",
                                "timestamp", "1781455200",
                                "type", "text",
                                "text", Map.of("body", "Ola")
                        ),
                        Map.of(
                                "id", "wamid-2",
                                "from", "5511999990000",
                                "timestamp", "1781455260",
                                "type", "text",
                                "text", Map.of("body", "Segunda mensagem")
                        ),
                        Map.of(
                                "id", "wamid-3",
                                "from", "5511999990000",
                                "timestamp", "1781455320",
                                "type", "text",
                                "text", Map.of("body", "Terceira mensagem")
                        )
                )
        );
    }

    private String fullMetaPayload(String phoneNumberId) {
        return """
                {
                  "object": "whatsapp_business_account",
                  "entry": [
                    {
                      "id": "waba-1",
                      "changes": [
                        {
                          "field": "messages",
                          "value": {
                            "metadata": {"phone_number_id": "%s"},
                            "contacts": [
                              {
                                "wa_id": "5511999990000",
                                "profile": {"name": "Paciente Teste"}
                              }
                            ],
                            "messages": [
                              {
                                "id": "wamid-1",
                                "timestamp": "1781455200",
                                "text": {"body": "Ola"}
                              }
                            ]
                          }
                        }
                      ]
                    }
                  ]
                }
                """.formatted(phoneNumberId);
    }

    private String fullMetaPayloadWithThreeMessages(String phoneNumberId) {
        return """
                {
                  "object": "whatsapp_business_account",
                  "entry": [
                    {
                      "id": "waba-1",
                      "changes": [
                        {
                          "field": "messages",
                          "value": {
                            "metadata": {"phone_number_id": "%s"},
                            "contacts": [
                              {
                                "wa_id": "5511999990000",
                                "profile": {"name": "Paciente Teste"}
                              }
                            ],
                            "messages": [
                              {
                                "id": "wamid-1",
                                "from": "5511999990000",
                                "timestamp": "1781455200",
                                "type": "text",
                                "text": {"body": "Ola"}
                              },
                              {
                                "id": "wamid-2",
                                "from": "5511999990000",
                                "timestamp": "1781455260",
                                "type": "text",
                                "text": {"body": "Segunda mensagem"}
                              },
                              {
                                "id": "wamid-3",
                                "from": "5511999990000",
                                "timestamp": "1781455320",
                                "type": "text",
                                "text": {"body": "Terceira mensagem"}
                              }
                            ]
                          }
                        }
                      ]
                    }
                  ]
                }
                """.formatted(phoneNumberId);
    }

    private Mensagem mensagemExistente(String whatsappMessageId) {
        Mensagem mensagem = new Mensagem();
        mensagem.setWhatsappMessageId(whatsappMessageId);
        return mensagem;
    }
}
