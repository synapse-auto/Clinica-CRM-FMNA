package com.synapse.clinicafemina.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.clinicafemina.config.RabbitMQConfig;
import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Mensagem;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.ClinicaRepository;
import com.synapse.clinicafemina.repository.MensagemRepository;
import com.synapse.clinicafemina.repository.MidiaMensagemRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import com.synapse.clinicafemina.service.AtendimentoNotificationService;
import com.synapse.clinicafemina.service.HorarioIaService;
import com.synapse.clinicafemina.service.N8nEventService;
import com.synapse.clinicafemina.service.RealtimeBroadcastService;
import com.synapse.clinicafemina.service.WhatsappWebhookDispatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Fluxo inbound FMNA de ponta a ponta com o PIPELINE EXISTENTE (o mesmo da UltraMedical):
 * payload UAZAP → WhatsappInboundListener → WhatsappInboundMapper → persistência/dedup → N8N.
 * Sem parser próprio, sem fila própria, sem envelope interno.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Inbound UAZAP → pipeline existente → N8N (padrão UltraMedical)")
class UazapInboundPipelineIntegrationTest {

    // Fixture UAZAP real (envelope Meta-compatível confirmado no OpenAPI): texto simples.
    private static final String UAZAP_RAW = """
            {"object":"whatsapp_business_account","entry":[{"id":"INST-FMNA","changes":[{"field":"messages","value":{
            "messaging_product":"whatsapp",
            "metadata":{"display_phone_number":"5543000000000","phone_number_id":"uazap-fmna"},
            "contacts":[{"profile":{"name":"Paciente FMNA"},"wa_id":"5543988887777"}],
            "messages":[{"from":"5543988887777","id":"UZ-100","timestamp":"1781455200","type":"text","text":{"body":"Olá FMNA"}}]}}]}]}
            """;

    // Fixture Meta/UltraMedical (regressão): mesmo pipeline, wamid.
    private static final String META_RAW = """
            {"object":"whatsapp_business_account","entry":[{"id":"WABA","changes":[{"field":"messages","value":{
            "messaging_product":"whatsapp",
            "metadata":{"display_phone_number":"5511000000000","phone_number_id":"phone-ultra"},
            "contacts":[{"profile":{"name":"Paciente Ultra"},"wa_id":"5511999990000"}],
            "messages":[{"from":"5511999990000","id":"wamid-meta-1","timestamp":"1781455200","type":"text","text":{"body":"Olá Ultra"}}]}}]}]}
            """;

    @Mock private PacienteRepository pacienteRepository;
    @Mock private AtendimentoRepository atendimentoRepository;
    @Mock private MensagemRepository mensagemRepository;
    @Mock private MidiaMensagemRepository midiaMensagemRepository;
    @Mock private ClinicaRepository clinicaRepository;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private N8nEventService n8nEventService;
    @Mock private HorarioIaService horarioIaService;
    @Mock private AtendimentoNotificationService notificationService;
    @Mock private Environment environment;
    @Mock private WhatsappOutboundClient whatsappOutboundClient;
    @Mock private RealtimeBroadcastService broadcastService;

    private WhatsappInboundMapper mapper;
    private WhatsappInboundListener listener;

    @BeforeEach
    void setUp() {
        com.synapse.clinicafemina.integration.whatsapp.config.WhatsappProperties whatsappProperties =
                new com.synapse.clinicafemina.integration.whatsapp.config.WhatsappProperties();
        whatsappProperties.getUazap().setPhoneNumberId("uazap-fmna");
        mapper = new WhatsappInboundMapper(
                pacienteRepository, atendimentoRepository, mensagemRepository, midiaMensagemRepository,
                clinicaRepository, rabbitTemplate, n8nEventService, horarioIaService,
                notificationService, new ObjectMapper(), new WhatsappInboundPayloadParser(),
                environment, whatsappOutboundClient,
                java.util.List.of(
                        new com.synapse.clinicafemina.integration.whatsapp.meta.MetaWhatsappMediaDownloader(
                                whatsappOutboundClient, whatsappProperties),
                        new com.synapse.clinicafemina.integration.whatsapp.uazap.UazapWhatsappMediaDownloader(
                                whatsappProperties)),
                org.mockito.Mockito.mock(org.springframework.context.ApplicationEventPublisher.class),
                whatsappProperties);
        listener = new WhatsappInboundListener(mapper, new ObjectMapper(), broadcastService);
        lenient().when(horarioIaService.avaliar(any(Clinica.class)))
                .thenReturn(new HorarioIaService.HorarioIaStatus(true, HorarioIaService.DENTRO_HORARIO));
    }

    private Clinica clinicaFmna() {
        Clinica clinica = new Clinica();
        clinica.setId(5L);
        clinica.setNome("FMNA");
        clinica.setSlug("fmna");
        clinica.setUsaN8n(true);
        clinica.setN8nWebhookUrl("https://n8n.example/fmna");
        clinica.setWhatsappPhoneNumberId("uazap-fmna");
        return clinica;
    }

    private Paciente paciente(Clinica clinica) {
        Paciente paciente = new Paciente();
        paciente.setId(20L);
        paciente.setClinica(clinica);
        paciente.setNomeBusca("PACIENTE FMNA");
        paciente.setTelefoneNormalizado("5543988887777");
        return paciente;
    }

    private Atendimento atendimento(Clinica clinica, Paciente paciente, boolean tratadoPorIa) {
        Atendimento atendimento = new Atendimento();
        atendimento.setId(30L);
        atendimento.setClinica(clinica);
        atendimento.setPaciente(paciente);
        atendimento.setNaoLidas(0);
        atendimento.setTratadoPorIa(tratadoPorIa);
        return atendimento;
    }

    private void stubHappyPath(Clinica clinica, Paciente paciente, Atendimento atendimento, String messageId) {
        when(clinicaRepository.findByWhatsappPhoneNumberId("uazap-fmna")).thenReturn(Optional.of(clinica));
        when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(5L, messageId)).thenReturn(Optional.empty());
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizado(5L, "5543988887777"))
                .thenReturn(Optional.of(paciente));
        when(atendimentoRepository.findAtivo(5L, 20L)).thenReturn(Optional.of(atendimento));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> {
            Mensagem mensagem = invocation.getArgument(0);
            mensagem.setId(40L);
            return mensagem;
        });
        when(atendimentoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pacienteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("payload UAZAP atravessa o pipeline existente: persiste, resolve paciente/atendimento e emite N8N unitário WhatsApp-compatible")
    void uazapPayload_flowsThroughExistingPipeline_andReachesN8n() throws Exception {
        Clinica clinica = clinicaFmna();
        Paciente paciente = paciente(clinica);
        Atendimento atendimento = atendimento(clinica, paciente, true);
        stubHappyPath(clinica, paciente, atendimento, "UZ-100");

        listener.processarMensagem(UAZAP_RAW.getBytes(StandardCharsets.UTF_8));

        // Persistência com whatsapp_message_id salvo (idempotência) e vínculo ao atendimento resolvido.
        ArgumentCaptor<Mensagem> mensagemCaptor = ArgumentCaptor.forClass(Mensagem.class);
        verify(mensagemRepository).save(mensagemCaptor.capture());
        assertEquals("UZ-100", mensagemCaptor.getValue().getWhatsappMessageId());
        assertEquals("ENTRADA", mensagemCaptor.getValue().getDirecao());
        assertSame(atendimento, mensagemCaptor.getValue().getAtendimento());
        verify(notificationService).notificarNovaMensagem(eq(atendimento), any());

        // N8N recebe o payload WhatsApp unitário (padrão UltraMedical), não um DTO interno.
        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<N8nEventService.MetaWebhookContext> contextCaptor =
                ArgumentCaptor.forClass(N8nEventService.MetaWebhookContext.class);
        verify(n8nEventService).enviarPayloadMetaOriginal(eq(clinica), payloadCaptor.capture(), contextCaptor.capture());

        JsonNode n8nBody = new ObjectMapper().readTree(payloadCaptor.getValue());
        assertEquals("whatsapp_business_account", n8nBody.path("object").asText());
        assertEquals("messages", n8nBody.at("/entry/0/changes/0/field").asText());
        JsonNode value = n8nBody.at("/entry/0/changes/0/value");
        assertEquals("uazap-fmna", value.at("/metadata/phone_number_id").asText());          // metadata preservado
        assertEquals(1, value.path("messages").size());                                       // somente a mensagem processada
        assertEquals("UZ-100", value.at("/messages/0/id").asText());
        assertEquals("Olá FMNA", value.at("/messages/0/text/body").asText());
        assertEquals("5543988887777", value.at("/contacts/0/wa_id").asText());               // contatos correspondentes
        // Não é evento interno do CRM:
        assertFalse(n8nBody.has("provider"));
        assertFalse(n8nBody.has("rawPayload"));
        assertFalse(n8nBody.has("clinicaId"));
        assertFalse(n8nBody.has("pacienteId"));
        assertFalse(n8nBody.has("atendimentoId"));
        // Dados internos seguem no contexto (headers X-CRM-*), não no body:
        assertEquals("UZ-100", contextCaptor.getValue().whatsappMessageId());
        assertEquals(40L, contextCaptor.getValue().mensagemId());
    }

    @Test
    @DisplayName("segunda entrega do mesmo messageId UAZAP é ignorada e não reenviada ao N8N")
    void duplicateUazapDelivery_isIgnored_andNotResentToN8n() {
        Clinica clinica = clinicaFmna();
        Paciente paciente = paciente(clinica);
        Atendimento atendimento = atendimento(clinica, paciente, true);

        Mensagem existente = new Mensagem();
        existente.setId(40L);
        existente.setWhatsappMessageId("UZ-100");
        existente.setAtendimento(atendimento);
        existente.setTipoMedia("TEXTO");

        when(clinicaRepository.findByWhatsappPhoneNumberId("uazap-fmna")).thenReturn(Optional.of(clinica));
        when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(5L, "UZ-100"))
                .thenReturn(Optional.empty(), Optional.of(existente));
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizado(5L, "5543988887777"))
                .thenReturn(Optional.of(paciente));
        when(atendimentoRepository.findAtivo(5L, 20L)).thenReturn(Optional.of(atendimento));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> {
            Mensagem mensagem = invocation.getArgument(0);
            mensagem.setId(40L);
            return mensagem;
        });
        when(atendimentoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pacienteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        byte[] raw = UAZAP_RAW.getBytes(StandardCharsets.UTF_8);
        listener.processarMensagem(raw);
        listener.processarMensagem(raw);

        verify(mensagemRepository, times(1)).save(any(Mensagem.class));
        verify(n8nEventService, times(1)).enviarPayloadMetaOriginal(eq(clinica), any(), any());
    }

    @Test
    @DisplayName("atendimento humano persiste a mensagem mas bloqueia o envio ao N8N")
    void humanAtendimento_persists_butBlocksN8n() {
        Clinica clinica = clinicaFmna();
        Paciente paciente = paciente(clinica);
        Atendimento atendimento = atendimento(clinica, paciente, false);
        stubHappyPath(clinica, paciente, atendimento, "UZ-100");

        listener.processarMensagem(UAZAP_RAW.getBytes(StandardCharsets.UTF_8));

        verify(mensagemRepository).save(any(Mensagem.class));
        verify(n8nEventService, never()).enviarPayloadMetaOriginal(any(), any(), any());
        verify(n8nEventService, never()).criarPayloadMensagemRecebida(any(), any(), any(), any());
    }

    @Test
    @DisplayName("UAZAP_PHONE_NUMBER_ID resolve a clínica FMNA por fallback quando ainda não mapeado no banco")
    void fmnaPhoneNumberId_resolvesClinic_viaUazapEnvFallback() {
        Clinica clinica = clinicaFmna();
        clinica.setWhatsappPhoneNumberId(null); // ainda não mapeado no banco
        Paciente paciente = paciente(clinica);
        Atendimento atendimento = atendimento(clinica, paciente, true);

        ReflectionTestUtils.setField(mapper, "uazapPhoneId", "uazap-fmna");
        when(clinicaRepository.findByWhatsappPhoneNumberId("uazap-fmna")).thenReturn(Optional.empty());
        when(environment.getProperty("app.clinic.slug", "ultramedical")).thenReturn("fmna");
        when(clinicaRepository.findBySlug("fmna")).thenReturn(Optional.of(clinica));
        when(clinicaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(5L, "UZ-100")).thenReturn(Optional.empty());
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizado(5L, "5543988887777"))
                .thenReturn(Optional.of(paciente));
        when(atendimentoRepository.findAtivo(5L, 20L)).thenReturn(Optional.of(atendimento));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(atendimentoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pacienteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        listener.processarMensagem(UAZAP_RAW.getBytes(StandardCharsets.UTF_8));

        verify(clinicaRepository).save(clinica);
        assertEquals("uazap-fmna", clinica.getWhatsappPhoneNumberId());
        verify(mensagemRepository).save(any(Mensagem.class));
    }

    @Test
    @DisplayName("payload Meta da UltraMedical continua atravessando o mesmo pipeline sem alteração")
    void metaPayload_continuesWorking_throughSamePipeline() {
        Clinica ultra = new Clinica();
        ultra.setId(2L);
        ultra.setNome("UltraMedical");
        ultra.setSlug("ultramedical");
        ultra.setUsaN8n(true);
        ultra.setN8nWebhookUrl("https://n8n.example/ultra");
        ultra.setWhatsappPhoneNumberId("phone-ultra");

        Paciente paciente = new Paciente();
        paciente.setId(21L);
        paciente.setClinica(ultra);
        paciente.setNomeBusca("PACIENTE ULTRA");
        paciente.setTelefoneNormalizado("5511999990000");

        Atendimento atendimento = new Atendimento();
        atendimento.setId(31L);
        atendimento.setClinica(ultra);
        atendimento.setPaciente(paciente);
        atendimento.setNaoLidas(0);
        atendimento.setTratadoPorIa(true);

        when(clinicaRepository.findByWhatsappPhoneNumberId("phone-ultra")).thenReturn(Optional.of(ultra));
        when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(2L, "wamid-meta-1")).thenReturn(Optional.empty());
        when(pacienteRepository.findByClinicaIdAndTelefoneNormalizado(2L, "5511999990000"))
                .thenReturn(Optional.of(paciente));
        when(atendimentoRepository.findAtivo(2L, 21L)).thenReturn(Optional.of(atendimento));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> {
            Mensagem mensagem = invocation.getArgument(0);
            mensagem.setId(41L);
            return mensagem;
        });
        when(atendimentoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pacienteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        listener.processarMensagem(META_RAW.getBytes(StandardCharsets.UTF_8));

        ArgumentCaptor<Mensagem> mensagemCaptor = ArgumentCaptor.forClass(Mensagem.class);
        verify(mensagemRepository).save(mensagemCaptor.capture());
        assertEquals("wamid-meta-1", mensagemCaptor.getValue().getWhatsappMessageId());
        verify(n8nEventService).enviarPayloadMetaOriginal(eq(ultra), any(), any());
    }

    @Test
    @DisplayName("dispatch publica no RabbitMQ exatamente o mesmo byte[] recebido, sem envelope interno")
    void dispatch_publishesSameRawBytes_withoutEnvelope() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        WhatsappInboundListener listenerMock = mock(WhatsappInboundListener.class);
        WhatsappWebhookDispatchService dispatch = new WhatsappWebhookDispatchService(template, listenerMock);
        byte[] raw = UAZAP_RAW.getBytes(StandardCharsets.UTF_8);

        dispatch.despachar(raw);

        ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);
        verify(template).convertAndSend(
                eq(RabbitMQConfig.WHATSAPP_EXCHANGE),
                eq(RabbitMQConfig.INBOUND_ROUTING_KEY),
                bodyCaptor.capture());
        assertSame(raw, bodyCaptor.getValue()); // mesmo array — nenhum envelope {provider, rawPayload}
        assertTrue(bodyCaptor.getValue() instanceof byte[]);
        verifyNoInteractions(listenerMock); // fallback síncrono só em falha do RabbitMQ
    }

    // Fixture UAZAP com mídia (imagem): contrato de download do binário não confirmado (ver
    // UazapWhatsappMediaDownloader) — a mensagem e os metadados de mídia devem persistir mesmo assim.
    private static final String UAZAP_IMAGE_RAW = """
            {"object":"whatsapp_business_account","entry":[{"id":"INST-FMNA","changes":[{"field":"messages","value":{
            "messaging_product":"whatsapp",
            "metadata":{"display_phone_number":"5543000000000","phone_number_id":"uazap-fmna"},
            "contacts":[{"profile":{"name":"Paciente FMNA"},"wa_id":"5543988887777"}],
            "messages":[{"from":"5543988887777","id":"UZ-IMG-1","timestamp":"1781455200","type":"image",
            "image":{"id":"media-uazap-img","mime_type":"image/jpeg"}}]}}]}]}
            """;

    @Test
    @DisplayName("5/6. Mídia UAZAP: falha/pendência no download do binário não impede persistência da mensagem nem o envio ao N8N")
    void uazapMediaDownloadPending_doesNotBlockPersistence_orN8nDelivery() {
        Clinica clinica = clinicaFmna();
        Paciente paciente = paciente(clinica);
        Atendimento atendimento = atendimento(clinica, paciente, true);
        stubHappyPath(clinica, paciente, atendimento, "UZ-IMG-1");

        listener.processarMensagem(UAZAP_IMAGE_RAW.getBytes(StandardCharsets.UTF_8));

        // Mensagem persistida normalmente.
        verify(mensagemRepository).save(any(Mensagem.class));
        // Metadados de mídia persistidos mesmo sem o binário (download pendente).
        ArgumentCaptor<com.synapse.clinicafemina.domain.MidiaMensagem> midiaCaptor =
                ArgumentCaptor.forClass(com.synapse.clinicafemina.domain.MidiaMensagem.class);
        verify(midiaMensagemRepository).save(midiaCaptor.capture());
        assertEquals("media-uazap-img", midiaCaptor.getValue().getWhatsappMediaId());
        assertEquals(0L, midiaCaptor.getValue().getTamanhoBytes());
        // Meta nunca é chamado para mídia UAZAP.
        verifyNoInteractions(whatsappOutboundClient);
        // Payload ainda é entregue ao N8N (IA ativa).
        verify(n8nEventService).enviarPayloadMetaOriginal(eq(clinica), any(), any());
        verify(notificationService).notificarNovaMensagem(eq(atendimento), any());
    }
}
