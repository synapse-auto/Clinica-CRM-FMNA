package com.synapse.clinicafemina.integration;

import com.fasterxml.jackson.databind.JsonNode;
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
import com.synapse.clinicafemina.service.HorarioIaService;
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
import static org.mockito.Mockito.lenient;
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
    private HorarioIaService horarioIaService;

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
                horarioIaService,
                notificationService,
                new ObjectMapper(),
                new WhatsappInboundPayloadParser(),
                environment,
                whatsappOutboundClient,
                List.of(new com.synapse.clinicafemina.integration.whatsapp.meta.MetaWhatsappMediaDownloader(
                        whatsappOutboundClient, new com.synapse.clinicafemina.integration.whatsapp.config.WhatsappProperties()))
        );

        clinica = new Clinica();
        clinica.setId(2L);
        clinica.setNome("UltraMedical");
        clinica.setWhatsappPhoneNumberId("phone-ultra");
        lenient().when(horarioIaService.avaliar(any(Clinica.class)))
                .thenReturn(new HorarioIaService.HorarioIaStatus(true, HorarioIaService.DENTRO_HORARIO));
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
    void should_persist_safe_contact_avatar_when_meta_sends_profile_picture() {
        when(clinicaRepository.findByWhatsappPhoneNumberId("phone-ultra")).thenReturn(Optional.of(clinica));
        when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(2L, "wamid-1")).thenReturn(Optional.empty());
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizado(2L, "5511999990000"))
                .thenReturn(Optional.empty());
        when(pacienteRepository.save(any(Paciente.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(atendimentoRepository.findAtivo(2L, null)).thenReturn(Optional.empty());
        when(atendimentoRepository.existeEncerradoDesde(any(), any(), any())).thenReturn(false);
        when(atendimentoRepository.save(any(Atendimento.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mapper.processarMensagemTexto(Map.of(
                "metadata", Map.of("phone_number_id", "phone-ultra"),
                "contacts", List.of(Map.of(
                        "wa_id", "5511999990000",
                        "profile", Map.of(
                                "name", "Paciente Teste",
                                "picture", "https://provider.example/avatar/abc"
                        )
                )),
                "messages", List.of(Map.of(
                        "id", "wamid-1",
                        "timestamp", "1781455200",
                        "text", Map.of("body", "Olá")
                ))
        ));

        ArgumentCaptor<Paciente> captor = ArgumentCaptor.forClass(Paciente.class);
        verify(pacienteRepository, atLeastOnce()).save(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .anyMatch(paciente -> "https://provider.example/avatar/abc".equals(paciente.getFotoUrl())));
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
        atendimento.setTratadoPorIa(true);

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
        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<N8nEventService.MetaWebhookContext> contextCaptor =
                ArgumentCaptor.forClass(N8nEventService.MetaWebhookContext.class);
        InOrder ordem = inOrder(mensagemRepository, n8nEventService);
        ordem.verify(mensagemRepository).save(mensagemCaptor.capture());
        ordem.verify(n8nEventService)
                .enviarPayloadMetaOriginal(eq(clinica), payloadCaptor.capture(), contextCaptor.capture());
        assertEquals("mensagem_recebida", contextCaptor.getValue().evento());
        assertEquals(30L, contextCaptor.getValue().atendimentoId());
        assertEquals(20L, contextCaptor.getValue().pacienteId());
        assertEquals(40L, contextCaptor.getValue().mensagemId());
        assertEquals("wamid-1", contextCaptor.getValue().whatsappMessageId());
        assertEquals("IA", contextCaptor.getValue().atendimentoOrigem());
        assertEquals("IA", contextCaptor.getValue().atendimentoModo());
        assertTrue(contextCaptor.getValue().iaAtiva());
        assertTrue(contextCaptor.getValue().dentroHorario());
        assertEquals(HorarioIaService.DENTRO_HORARIO, contextCaptor.getValue().horarioMotivo());
        assertTrue(new String(payloadCaptor.getValue(), StandardCharsets.UTF_8).contains("\"wamid-1\""));
        verify(n8nEventService, never()).criarPayloadMensagemRecebida(any(), any(), any(), any());
    }

    @Test
    void should_emit_n8n_with_outside_schedule_headers_when_ai_mode_is_active() {
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
        atendimento.setTratadoPorIa(true);

        when(horarioIaService.avaliar(clinica))
                .thenReturn(new HorarioIaService.HorarioIaStatus(false, HorarioIaService.FORA_HORARIO));
        when(clinicaRepository.findByWhatsappPhoneNumberId("phone-ultra")).thenReturn(Optional.of(clinica));
        when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(2L, "wamid-1")).thenReturn(Optional.empty());
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

        mapper.processarMensagemTexto(validValuePayload("phone-ultra"), rawBody);

        ArgumentCaptor<N8nEventService.MetaWebhookContext> contextCaptor =
                ArgumentCaptor.forClass(N8nEventService.MetaWebhookContext.class);
        verify(n8nEventService).enviarPayloadMetaOriginal(eq(clinica), any(), contextCaptor.capture());
        assertFalse(contextCaptor.getValue().dentroHorario());
        assertEquals(HorarioIaService.FORA_HORARIO, contextCaptor.getValue().horarioMotivo());
    }

    @Test
    void should_not_emit_n8n_when_atendimento_is_humano() {
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
        atendimento.setTratadoPorIa(false);

        when(clinicaRepository.findByWhatsappPhoneNumberId("phone-ultra")).thenReturn(Optional.of(clinica));
        when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(2L, "wamid-1")).thenReturn(Optional.empty());
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

        mapper.processarMensagemTexto(validValuePayload("phone-ultra"), rawBody);

        verify(mensagemRepository).save(any(Mensagem.class));
        verify(n8nEventService, never()).enviarPayloadMetaOriginal(any(), any(), any());
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
        atendimento.setTratadoPorIa(true);

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
    void should_emit_unit_payload_when_original_meta_payload_has_multiple_changes_with_single_messages() {
        clinica.setSlug("ultramedical");
        clinica.setUsaN8n(true);
        clinica.setN8nWebhookUrl("https://n8n.example/webhook");
        byte[] rawBody = fullMetaPayloadWithTwoChanges("phone-ultra").getBytes(StandardCharsets.UTF_8);

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
        atendimento.setTratadoPorIa(true);

        when(clinicaRepository.findByWhatsappPhoneNumberId("phone-ultra")).thenReturn(Optional.of(clinica));
        when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(2L, "wamid-2")).thenReturn(Optional.empty());
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizado(2L, "5511999990000"))
                .thenReturn(Optional.of(paciente));
        when(atendimentoRepository.findAtivo(2L, 20L)).thenReturn(Optional.of(atendimento));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> {
            Mensagem mensagem = invocation.getArgument(0);
            mensagem.setId(41L);
            return mensagem;
        });
        when(atendimentoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pacienteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        mapper.processarMensagemTexto(valuePayloadSingleMessage("phone-ultra", "wamid-2", "Segunda mensagem"), rawBody);

        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<N8nEventService.MetaWebhookContext> contextCaptor =
                ArgumentCaptor.forClass(N8nEventService.MetaWebhookContext.class);
        verify(n8nEventService).enviarPayloadMetaOriginal(eq(clinica), payloadCaptor.capture(), contextCaptor.capture());

        String body = new String(payloadCaptor.getValue(), StandardCharsets.UTF_8);
        assertTrue(body.contains("\"wamid-2\""));
        assertFalse(body.contains("\"wamid-1\""));
        assertEquals("wamid-2", contextCaptor.getValue().whatsappMessageId());
    }

    @Test
    void should_persist_long_text_complete_and_emit_unit_payload_with_complete_body() throws Exception {
        clinica.setSlug("ultramedical");
        clinica.setUsaN8n(true);
        clinica.setN8nWebhookUrl("https://n8n.example/webhook");
        String textoLongo = """
                nasci em 28/01/1999 cpf 00000000000 telefone 5500000000000
                quero ultrassonografia transvaginal com observacoes adicionais
                """.strip();
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
        atendimento.setTratadoPorIa(true);

        when(clinicaRepository.findByWhatsappPhoneNumberId("phone-ultra")).thenReturn(Optional.of(clinica));
        when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(2L, "wamid-long")).thenReturn(Optional.empty());
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

        mapper.processarMensagemTexto(valuePayloadSingleMessage("phone-ultra", "wamid-long", textoLongo), rawBody);

        ArgumentCaptor<Mensagem> mensagemCaptor = ArgumentCaptor.forClass(Mensagem.class);
        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<N8nEventService.MetaWebhookContext> contextCaptor =
                ArgumentCaptor.forClass(N8nEventService.MetaWebhookContext.class);
        verify(mensagemRepository).save(mensagemCaptor.capture());
        verify(n8nEventService).enviarPayloadMetaOriginal(eq(clinica), payloadCaptor.capture(), contextCaptor.capture());

        Mensagem mensagem = mensagemCaptor.getValue();
        assertEquals(textoLongo, mensagem.getConteudo());
        assertTrue(mensagem.getConteudoPrevia().length() <= 60);
        assertEquals("TEXTO", mensagem.getTipoMedia());
        assertEquals("TEXTO", contextCaptor.getValue().tipoMedia());
        assertEquals("wamid-long", contextCaptor.getValue().whatsappMessageId());

        JsonNode payloadN8n = new ObjectMapper().readTree(payloadCaptor.getValue());
        JsonNode messages = payloadN8n.at("/entry/0/changes/0/value/messages");
        assertEquals(1, messages.size());
        assertEquals(textoLongo, messages.get(0).at("/text/body").asText());
    }

    @Test
    void should_process_same_text_messages_when_wamids_are_different() {
        clinica.setSlug("ultramedical");
        clinica.setUsaN8n(true);
        clinica.setN8nWebhookUrl("https://n8n.example/webhook");
        byte[] rawBody = fullMetaPayloadWithThreeSameTextMessages("phone-ultra").getBytes(StandardCharsets.UTF_8);

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
        atendimento.setTratadoPorIa(true);

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

        mapper.processarMensagemTexto(valuePayloadWithThreeSameTextMessages("phone-ultra"), rawBody);

        ArgumentCaptor<Mensagem> mensagemCaptor = ArgumentCaptor.forClass(Mensagem.class);
        ArgumentCaptor<N8nEventService.MetaWebhookContext> contextCaptor =
                ArgumentCaptor.forClass(N8nEventService.MetaWebhookContext.class);
        verify(mensagemRepository, times(3)).save(mensagemCaptor.capture());
        verify(n8nEventService, times(3)).enviarPayloadMetaOriginal(eq(clinica), any(), contextCaptor.capture());
        assertEquals(List.of("wamid-1", "wamid-2", "wamid-3"), mensagemCaptor.getAllValues().stream()
                .map(Mensagem::getWhatsappMessageId)
                .toList());
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
        atendimento.setTratadoPorIa(true);

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
        atendimento.setTratadoPorIa(true);

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
        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<N8nEventService.MetaWebhookContext> contextCaptor =
                ArgumentCaptor.forClass(N8nEventService.MetaWebhookContext.class);
        verify(midiaMensagemRepository).save(midiaCaptor.capture());
        verify(n8nEventService).enviarPayloadMetaOriginal(eq(clinica), payloadCaptor.capture(), contextCaptor.capture());
        assertEquals("AUDIO", midiaCaptor.getValue().getTipoMedia());
        assertEquals(0L, midiaCaptor.getValue().getTamanhoBytes());
        assertEquals(40L, contextCaptor.getValue().mensagemId());
        assertTrue(new String(payloadCaptor.getValue(), StandardCharsets.UTF_8).contains("\"wamid-audio\""));
        assertFalse(new String(payloadCaptor.getValue(), StandardCharsets.UTF_8).contains("\"wamid-1\""));
    }

    @Test
    void should_not_emit_audio_to_n8n_when_atendimento_is_humano() {
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
        atendimento.setTratadoPorIa(false);

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

        verify(midiaMensagemRepository).save(any(MidiaMensagem.class));
        verify(n8nEventService, never()).enviarPayloadMetaOriginal(any(), any(), any());
        verify(n8nEventService, never()).criarPayloadMensagemRecebida(any(), any(), any(), any());
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
        atendimento.setTratadoPorIa(true);

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
        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<N8nEventService.MetaWebhookContext> contextCaptor =
                ArgumentCaptor.forClass(N8nEventService.MetaWebhookContext.class);
        verify(mensagemRepository).save(mensagemCaptor.capture());
        verify(n8nEventService).enviarPayloadMetaOriginal(eq(clinica), payloadCaptor.capture(), contextCaptor.capture());
        assertEquals("OUTRO", mensagemCaptor.getValue().getTipoMedia());
        assertEquals(40L, contextCaptor.getValue().mensagemId());
        assertTrue(new String(payloadCaptor.getValue(), StandardCharsets.UTF_8).contains("\"wamid-sticker\""));
        assertFalse(new String(payloadCaptor.getValue(), StandardCharsets.UTF_8).contains("\"wamid-1\""));
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
        atendimento.setTratadoPorIa(true);

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

        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mensagemRepository, times(1)).save(any(Mensagem.class));
        verify(midiaMensagemRepository, times(1)).save(any(MidiaMensagem.class));
        verify(n8nEventService, times(1)).enviarPayloadMetaOriginal(eq(clinica), payloadCaptor.capture(), any());
        assertTrue(new String(payloadCaptor.getValue(), StandardCharsets.UTF_8).contains("\"wamid-audio\""));
        assertFalse(new String(payloadCaptor.getValue(), StandardCharsets.UTF_8).contains("\"wamid-1\""));
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
        atendimento.setTratadoPorIa(true);

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

        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mensagemRepository, times(1)).save(any(Mensagem.class));
        verify(n8nEventService, times(1)).enviarPayloadMetaOriginal(eq(clinica), payloadCaptor.capture(), any());
        assertTrue(new String(payloadCaptor.getValue(), StandardCharsets.UTF_8).contains("\"wamid-sticker\""));
        assertFalse(new String(payloadCaptor.getValue(), StandardCharsets.UTF_8).contains("\"wamid-1\""));
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
        clinica.setSlug("ultramedical");
        clinica.setUsaN8n(true);
        clinica.setN8nWebhookUrl("https://n8n.example/webhook");
        byte[] rawBody = fullMetaPayload("phone-ultra").getBytes(StandardCharsets.UTF_8);

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
        atendimento.setTratadoPorIa(true);
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
        ), rawBody);

        ArgumentCaptor<MidiaMensagem> midiaCaptor = ArgumentCaptor.forClass(MidiaMensagem.class);
        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<N8nEventService.MetaWebhookContext> contextCaptor =
                ArgumentCaptor.forClass(N8nEventService.MetaWebhookContext.class);
        verify(midiaMensagemRepository).save(midiaCaptor.capture());
        verify(n8nEventService).enviarPayloadMetaOriginal(eq(clinica), payloadCaptor.capture(), contextCaptor.capture());
        assertArrayEquals(documentBytes, midiaCaptor.getValue().getS3Chave());
        assertEquals("database", midiaCaptor.getValue().getS3Bucket());
        assertEquals(documentBytes.length, midiaCaptor.getValue().getTamanhoBytes());
        assertEquals("DOCUMENTO", contextCaptor.getValue().tipoMedia());
        assertTrue(new String(payloadCaptor.getValue(), StandardCharsets.UTF_8).contains("\"wamid-doc\""));
        assertFalse(new String(payloadCaptor.getValue(), StandardCharsets.UTF_8).contains("\"wamid-1\""));
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

    @Test
    void should_normalize_null_profile_name_to_placeholder_not_literal_null_string() {
        when(clinicaRepository.findByWhatsappPhoneNumberId("phone-ultra")).thenReturn(Optional.of(clinica));
        when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(2L, "wamid-1")).thenReturn(Optional.empty());
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizado(2L, "5511999990000"))
                .thenReturn(Optional.empty());
        when(pacienteRepository.save(any(Paciente.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(atendimentoRepository.findAtivo(2L, null)).thenReturn(Optional.empty());
        when(atendimentoRepository.existeEncerradoDesde(any(), any(), any())).thenReturn(false);
        when(atendimentoRepository.save(any(Atendimento.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // profile presente, mas profile.name é null — reproduz o bug de String.valueOf(null) == "null".
        Map<String, Object> profileComNomeNulo = new java.util.HashMap<>();
        profileComNomeNulo.put("name", null);
        mapper.processarMensagemTexto(Map.of(
                "metadata", Map.of("phone_number_id", "phone-ultra"),
                "contacts", List.of(Map.of(
                        "wa_id", "5511999990000",
                        "profile", profileComNomeNulo
                )),
                "messages", List.of(Map.of(
                        "id", "wamid-1",
                        "timestamp", "1781455200",
                        "text", Map.of("body", "Olá")
                ))
        ));

        ArgumentCaptor<Paciente> pacienteCaptor = ArgumentCaptor.forClass(Paciente.class);
        verify(pacienteRepository, atLeastOnce()).save(pacienteCaptor.capture());
        Paciente salvo = pacienteCaptor.getAllValues().getFirst();
        assertEquals("Contato WhatsApp", salvo.getNome());
        assertEquals("CONTATO WHATSAPP", salvo.getNomeBusca());
    }

    @Test
    void should_update_placeholder_patient_name_when_valid_name_arrives_in_new_message() {
        Paciente existente = new Paciente();
        existente.setId(21L);
        existente.setClinica(clinica);
        existente.setNome("Contato WhatsApp");
        existente.setNomeBusca("CONTATO WHATSAPP");
        existente.setTelefoneNormalizado("5511999990000");

        when(clinicaRepository.findByWhatsappPhoneNumberId("phone-ultra")).thenReturn(Optional.of(clinica));
        when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(2L, "wamid-1")).thenReturn(Optional.empty());
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizado(2L, "5511999990000"))
                .thenReturn(Optional.of(existente));
        when(atendimentoRepository.findAtivo(2L, 21L)).thenReturn(Optional.empty());
        when(atendimentoRepository.existeEncerradoDesde(any(), any(), any())).thenReturn(false);
        when(atendimentoRepository.save(any(Atendimento.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mapper.processarMensagemTexto(validValuePayload("phone-ultra")); // profile.name = "Paciente Teste"

        assertEquals("Paciente Teste", existente.getNome());
        assertEquals("PACIENTE TESTE", existente.getNomeBusca());
    }

    @Test
    void should_not_overwrite_legitimate_existing_patient_name() {
        Paciente existente = new Paciente();
        existente.setId(22L);
        existente.setClinica(clinica);
        existente.setNome("Ana Souza");
        existente.setNomeBusca("ANA SOUZA");
        existente.setTelefoneNormalizado("5511999990000");

        when(clinicaRepository.findByWhatsappPhoneNumberId("phone-ultra")).thenReturn(Optional.of(clinica));
        when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(2L, "wamid-1")).thenReturn(Optional.empty());
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizado(2L, "5511999990000"))
                .thenReturn(Optional.of(existente));
        when(atendimentoRepository.findAtivo(2L, 22L)).thenReturn(Optional.empty());
        when(atendimentoRepository.existeEncerradoDesde(any(), any(), any())).thenReturn(false);
        when(atendimentoRepository.save(any(Atendimento.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // profile.name do payload ("Paciente Teste") é diferente do nome legítimo já cadastrado.
        mapper.processarMensagemTexto(validValuePayload("phone-ultra"));

        assertEquals("Ana Souza", existente.getNome());
        assertEquals("ANA SOUZA", existente.getNomeBusca());
    }

    @Test
    void should_process_uazap_documented_webhook_shape_and_extract_profile_name_and_photo() {
        when(clinicaRepository.findByWhatsappPhoneNumberId("uazap-fmna")).thenReturn(Optional.of(clinica));
        when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(2L, "UZ-100")).thenReturn(Optional.empty());
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizado(2L, "554391241788"))
                .thenReturn(Optional.empty());
        when(pacienteRepository.save(any(Paciente.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(atendimentoRepository.findAtivo(2L, null)).thenReturn(Optional.empty());
        when(atendimentoRepository.existeEncerradoDesde(any(), any(), any())).thenReturn(false);
        when(atendimentoRepository.save(any(Atendimento.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Formato CONFIRMADO no OpenAPI oficial da UAZAP (/webhook/message/text): contacts[].profile.name + wa_id.
        mapper.processarMensagemTexto(Map.of(
                "metadata", Map.of("phone_number_id", "uazap-fmna"),
                "contacts", List.of(Map.of(
                        "wa_id", "554391241788",
                        "profile", Map.of("name", "Ayumi Soluções Em Tecnologia")
                )),
                "messages", List.of(Map.of(
                        "id", "UZ-100",
                        "timestamp", "1781455200",
                        "text", Map.of("body", "Olá FMNA")
                ))
        ));

        ArgumentCaptor<Paciente> pacienteCaptor = ArgumentCaptor.forClass(Paciente.class);
        verify(pacienteRepository, atLeastOnce()).save(pacienteCaptor.capture());
        Paciente salvo = pacienteCaptor.getAllValues().getFirst();
        assertEquals("Ayumi Soluções Em Tecnologia", salvo.getNome());
        assertEquals("AYUMI SOLUÇÕES EM TECNOLOGIA", salvo.getNomeBusca());
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

    private Map<String, Object> valuePayloadSingleMessage(String phoneNumberId, String wamid, String text) {
        return Map.of(
                "metadata", Map.of("phone_number_id", phoneNumberId),
                "contacts", List.of(Map.of(
                        "wa_id", "5511999990000",
                        "profile", Map.of("name", "Paciente Teste")
                )),
                "messages", List.of(Map.of(
                        "id", wamid,
                        "from", "5511999990000",
                        "timestamp", "1781455260",
                        "type", "text",
                        "text", Map.of("body", text)
                ))
        );
    }

    private Map<String, Object> valuePayloadWithThreeSameTextMessages(String phoneNumberId) {
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
                                "text", Map.of("body", "Mesmo texto")
                        ),
                        Map.of(
                                "id", "wamid-2",
                                "from", "5511999990000",
                                "timestamp", "1781455260",
                                "type", "text",
                                "text", Map.of("body", "Mesmo texto")
                        ),
                        Map.of(
                                "id", "wamid-3",
                                "from", "5511999990000",
                                "timestamp", "1781455320",
                                "type", "text",
                                "text", Map.of("body", "Mesmo texto")
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

    private String fullMetaPayloadWithTwoChanges(String phoneNumberId) {
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
                                "text": {"body": "Primeira mensagem"}
                              }
                            ]
                          }
                        },
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
                                "id": "wamid-2",
                                "from": "5511999990000",
                                "timestamp": "1781455260",
                                "type": "text",
                                "text": {"body": "Segunda mensagem"}
                              }
                            ]
                          }
                        }
                      ]
                    }
                  ]
                }
                """.formatted(phoneNumberId, phoneNumberId);
    }

    private String fullMetaPayloadWithThreeSameTextMessages(String phoneNumberId) {
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
                                "text": {"body": "Mesmo texto"}
                              },
                              {
                                "id": "wamid-2",
                                "from": "5511999990000",
                                "timestamp": "1781455260",
                                "type": "text",
                                "text": {"body": "Mesmo texto"}
                              },
                              {
                                "id": "wamid-3",
                                "from": "5511999990000",
                                "timestamp": "1781455320",
                                "type": "text",
                                "text": {"body": "Mesmo texto"}
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
