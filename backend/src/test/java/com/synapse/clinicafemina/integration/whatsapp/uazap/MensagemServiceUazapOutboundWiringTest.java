package com.synapse.clinicafemina.integration.whatsapp.uazap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Mensagem;
import com.synapse.clinicafemina.domain.MidiaMensagem;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.domain.Recepcionista;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.EnviarMensagemRequest;
import com.synapse.clinicafemina.integration.WhatsappOutboundClient;
import com.synapse.clinicafemina.integration.whatsapp.WhatsappProviderResolver;
import com.synapse.clinicafemina.integration.whatsapp.WhatsappMediaDownloader;
import com.synapse.clinicafemina.integration.whatsapp.model.WhatsappMessageType;
import com.synapse.clinicafemina.integration.whatsapp.config.WhatsappProperties;
import com.synapse.clinicafemina.integration.whatsapp.meta.MetaWhatsappProvider;
import com.synapse.clinicafemina.integration.whatsapp.meta.MetaWhatsappMediaDownloader;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.MensagemRepository;
import com.synapse.clinicafemina.repository.MidiaMensagemRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import com.synapse.clinicafemina.service.MensagemService;
import com.synapse.clinicafemina.service.WhatsappRecipientService;
import com.synapse.clinicafemina.service.WhatsappWindowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Prova de fiação REAL do outbound: dirige {@link MensagemService#enviar} (o método de produção,
 * não um dublê) através do {@link WhatsappProviderResolver} real, comprovando que a troca de
 * {@code WHATSAPP_PROVIDER} efetivamente muda qual cliente HTTP recebe a chamada — e que nenhum
 * provider "vaza" para o outro.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MensagemService.enviar — fiação real do outbound via WhatsappProviderResolver")
class MensagemServiceUazapOutboundWiringTest {

    @Mock private MensagemRepository mensagemRepository;
    @Mock private MidiaMensagemRepository midiaMensagemRepository;
    @Mock private AtendimentoRepository atendimentoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private WhatsappOutboundClient whatsappOutboundClient; // client Meta — deve ficar intocado quando provider=UAZAP
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private WhatsappWindowService whatsappWindowService;

    private Atendimento atendimento;
    private Usuario remetente;

    @BeforeEach
    void setUp() {
        Clinica clinica = new Clinica();
        clinica.setId(9L);
        clinica.setSlug("fmna");

        Paciente paciente = new Paciente();
        paciente.setId(20L);
        paciente.setClinica(clinica);
        paciente.setTelefoneNormalizado("5583991114004");

        atendimento = new Atendimento();
        atendimento.setId(30L);
        atendimento.setClinica(clinica);
        atendimento.setPaciente(paciente);
        atendimento.setWhatsappChatId("558391114004");
        atendimento.setStatus("ATIVO");
        atendimento.setTratadoPorIa(true);

        remetente = new Recepcionista();
        remetente.setId(99L);
        remetente.setClinica(clinica);

        lenient().when(atendimentoRepository.findByIdAndClinicaId(30L, 9L)).thenReturn(Optional.of(atendimento));
        lenient().when(usuarioRepository.findAtivoByIdAndClinicaId(99L, 9L)).thenReturn(Optional.of(remetente));
        lenient().when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private MensagemService service(WhatsappProviderResolver resolver) {
        WhatsappProperties mediaProperties = new WhatsappProperties();
        mediaProperties.getUazap().setPhoneNumberId("uazap-fmna");
        return service(resolver, List.of(
                new MetaWhatsappMediaDownloader(whatsappOutboundClient, mediaProperties),
                new UazapWhatsappMediaDownloader(mediaProperties)), mediaProperties);
    }

    private MensagemService service(
            WhatsappProviderResolver resolver,
            List<WhatsappMediaDownloader> mediaDownloaders,
            WhatsappProperties mediaProperties
    ) {
        return new MensagemService(
                mensagemRepository, midiaMensagemRepository, atendimentoRepository, usuarioRepository,
                whatsappOutboundClient, rabbitTemplate, whatsappWindowService,
                new WhatsappRecipientService(resolver, atendimentoRepository),
                new ObjectMapper().findAndRegisterModules(),
                mediaDownloaders,
                mediaProperties);
    }

    @Test
    @DisplayName("WHATSAPP_PROVIDER=UAZAP: mensagem de saída real atravessa resolver → UazapWhatsappProvider → POST correto; messageId é persistido; Meta não é tocado")
    void uazapProvider_sendsThroughRealPipeline_andPersistsMessageId() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient mockedRestClient = builder.build();

        WhatsappProperties properties = new WhatsappProperties();
        properties.setEnabled(true);
        properties.setProvider("UAZAP");
        properties.getUazap().setBaseUrl("https://uazap.test");
        properties.getUazap().setUsername("user");
        properties.getUazap().setVersion("v2");
        properties.getUazap().setPhoneNumberId("inst-fmna");
        properties.getUazap().setToken("secret-token");

        UazapClient uazapClient = new UazapClient(mockedRestClient, properties);
        WhatsappProviderResolver resolver = new WhatsappProviderResolver(
                List.of(new MetaWhatsappProvider(whatsappOutboundClient), new UazapWhatsappProvider(uazapClient)),
                properties);

        server.expect(requestTo("https://uazap.test/user/v2/inst-fmna/messages"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer secret-token"))
                .andExpect(jsonPath("$.to").value("558391114004"))
                .andExpect(jsonPath("$.delayMessage").value(0))
                .andExpect(jsonPath("$.delayTyping").value(0))
                .andExpect(jsonPath("$.type").value("text"))
                .andExpect(jsonPath("$.text.body").value("Ola FMNA via UAZAP"))
                .andRespond(withSuccess(
                        "{\"status\":\"success\",\"message\":\"Mensagem colocada na fila de envios com sucesso!\","
                                + "\"queueId\":\"QUEUE-1\",\"messageId\":\"INTERNO-1\","
                                + "\"contacts\":[{\"input\":\"5583991114004\",\"wa_id\":\"558391114004\"}],"
                                + "\"messages\":[{\"id\":\"wamid.UZAPI-1\"}]}",
                        MediaType.APPLICATION_JSON));

        service(resolver).enviar(30L, 9L, new EnviarMensagemRequest("TEXTO", "Ola FMNA via UAZAP"), 99L);

        server.verify(); // POST correto foi de fato recebido pelo mock HTTP UAZAP

        ArgumentCaptor<Mensagem> mensagemCaptor = ArgumentCaptor.forClass(Mensagem.class);
        verify(mensagemRepository, org.mockito.Mockito.atLeastOnce()).save(mensagemCaptor.capture());
        Mensagem mensagemFinal = mensagemCaptor.getAllValues().getLast();
        assertEquals("wamid.UZAPI-1", mensagemFinal.getWhatsappMessageId());
        assertEquals("ENVIADA", mensagemFinal.getWhatsappStatus());

        verifyNoInteractions(whatsappOutboundClient); // client Meta nunca foi chamado
    }

    @Test
    @DisplayName("WHATSAPP_PROVIDER=UAZAP: PDF faz upload na Uzapi e é enviado por media ID; Meta não é tocado")
    void uazapProvider_sendsPdfThroughUploadAndMediaPipeline() {
        com.synapse.clinicafemina.integration.whatsapp.uazap.UazapClient uazapClient =
                org.mockito.Mockito.mock(com.synapse.clinicafemina.integration.whatsapp.uazap.UazapClient.class);
        WhatsappProperties properties = new WhatsappProperties();
        properties.setEnabled(true);
        properties.setProvider("UAZAP");
        WhatsappProviderResolver resolver = new WhatsappProviderResolver(
                List.of(new UazapWhatsappProvider(uazapClient)), properties);
        when(uazapClient.uploadMedia(any(), org.mockito.ArgumentMatchers.eq("application/pdf"),
                org.mockito.ArgumentMatchers.eq("guia.pdf"))).thenReturn("media-pdf-1");
        when(uazapClient.sendMedia("558391114004", WhatsappMessageType.DOCUMENT, "media-pdf-1", null))
                .thenReturn(new com.synapse.clinicafemina.integration.whatsapp.model.WhatsappSendResult(
                        "wamid.UZAPI-PDF", com.synapse.clinicafemina.integration.whatsapp.WhatsappProviderType.UAZAP));

        MensagemService service = service(resolver);
        service.enviarMidia(30L, 9L,
                new MockMultipartFile("arquivo", "guia.pdf", "application/pdf", "pdf".getBytes()), 99L);

        verify(uazapClient).uploadMedia(any(), org.mockito.ArgumentMatchers.eq("application/pdf"),
                org.mockito.ArgumentMatchers.eq("guia.pdf"));
        verify(uazapClient).sendMedia("558391114004", WhatsappMessageType.DOCUMENT, "media-pdf-1", null);
        verifyNoInteractions(whatsappOutboundClient);
    }

    @Test
    @DisplayName("WHATSAPP_PROVIDER=UAZAP: visualização de mídia usa o downloader UAZAP, não o Meta")
    void uazapProvider_readsMediaThroughUazapDownloader() {
        WhatsappProperties properties = new WhatsappProperties();
        properties.setProvider("UAZAP");
        properties.getUazap().setPhoneNumberId("uazap-fmna");
        WhatsappMediaDownloader downloader = mock(WhatsappMediaDownloader.class);
        when(downloader.supports("uazap-fmna")).thenReturn(true);
        byte[] pdf = "%PDF-1.7".getBytes();
        when(downloader.download("media-pdf-1"))
                .thenReturn(new WhatsappOutboundClient.MidiaBaixada(pdf, "application/pdf"));

        MidiaMensagem midia = new MidiaMensagem();
        midia.setWhatsappMediaId("media-pdf-1");
        midia.setMimeType("application/pdf");
        midia.setTamanhoBytes((long) pdf.length);

        MensagemService service = service(
                new WhatsappProviderResolver(List.of(), properties),
                List.of(downloader),
                properties);

        WhatsappOutboundClient.MidiaBaixada resultado = service.obterBinarioMidia(midia);

        assertArrayEquals(pdf, resultado.bytes());
        assertEquals("application/pdf", resultado.mimeType());
        verify(downloader).download("media-pdf-1");
        verifyNoInteractions(whatsappOutboundClient);
    }

    @Test
    @DisplayName("resposta logica de erro da Uzapi persiste FALHA e nunca o ID interno")
    void uazapLogicalError_persistsFailure() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient mockedRestClient = builder.build();

        WhatsappProperties properties = new WhatsappProperties();
        properties.setEnabled(true);
        properties.setProvider("UAZAP");
        properties.getUazap().setBaseUrl("https://uazap.test");
        properties.getUazap().setUsername("user");
        properties.getUazap().setVersion("v2");
        properties.getUazap().setPhoneNumberId("inst-fmna");
        properties.getUazap().setToken("secret-token");

        UazapClient uazapClient = new UazapClient(mockedRestClient, properties);
        WhatsappProviderResolver resolver = new WhatsappProviderResolver(
                List.of(new UazapWhatsappProvider(uazapClient)), properties);
        server.expect(requestTo("https://uazap.test/user/v2/inst-fmna/messages"))
                .andRespond(withSuccess(
                        "{\"status\":\"error\",\"message\":\"Instancia desconectada\","
                                + "\"queueId\":null,\"messageId\":null,\"contacts\":[],\"messages\":[]}",
                        MediaType.APPLICATION_JSON));

        service(resolver).enviar(30L, 9L, new EnviarMensagemRequest("TEXTO", "Mensagem inicial"), 99L);

        server.verify();
        ArgumentCaptor<Mensagem> mensagemCaptor = ArgumentCaptor.forClass(Mensagem.class);
        verify(mensagemRepository, org.mockito.Mockito.atLeastOnce()).save(mensagemCaptor.capture());
        Mensagem mensagemFinal = mensagemCaptor.getAllValues().getLast();
        assertEquals("FALHA", mensagemFinal.getWhatsappStatus());
        assertEquals(null, mensagemFinal.getWhatsappMessageId());
        verifyNoInteractions(whatsappOutboundClient);
    }

    @Test
    @DisplayName("WHATSAPP_PROVIDER=META (default): mensagem de saída real continua selecionando o WhatsappOutboundClient existente")
    void metaProvider_stillSelectsExistingClient() {
        WhatsappProperties properties = new WhatsappProperties(); // provider default = META
        WhatsappProviderResolver resolver = new WhatsappProviderResolver(
                List.of(new MetaWhatsappProvider(whatsappOutboundClient)), properties);
        when(whatsappOutboundClient.enviarTextoComResultado("558391114004", "Ola FMNA via Meta"))
                .thenReturn(new com.synapse.clinicafemina.integration.whatsapp.model.WhatsappSendResult(
                        "wamid-meta-out-1", com.synapse.clinicafemina.integration.whatsapp.WhatsappProviderType.META
                ));

        service(resolver).enviar(30L, 9L, new EnviarMensagemRequest("TEXTO", "Ola FMNA via Meta"), 99L);

        verify(whatsappOutboundClient).validarConfiguracao();
        verify(whatsappOutboundClient).enviarTextoComResultado("558391114004", "Ola FMNA via Meta");

        ArgumentCaptor<Mensagem> mensagemCaptor = ArgumentCaptor.forClass(Mensagem.class);
        verify(mensagemRepository, org.mockito.Mockito.atLeastOnce()).save(mensagemCaptor.capture());
        assertEquals("wamid-meta-out-1", mensagemCaptor.getAllValues().getLast().getWhatsappMessageId());
    }

    @Test
    void uazapProvider_should_send_new_mobile_contact_to_its_registered_phone_once() {
        atendimento.setWhatsappChatId(null);
        atendimento.getPaciente().setTelefoneNormalizado("5583991114004");
        WhatsappProperties properties = new WhatsappProperties();
        properties.setProvider("UAZAP");
        UazapClient uazapClient = org.mockito.Mockito.mock(UazapClient.class);
        WhatsappProviderResolver resolver = new WhatsappProviderResolver(
                List.of(new UazapWhatsappProvider(uazapClient)),
                properties
        );

        when(uazapClient.sendText("5583991114004", "Mensagem inicial"))
                .thenReturn(new com.synapse.clinicafemina.integration.whatsapp.model.WhatsappSendResult(
                        "wamid.UZAPI-NOVO", com.synapse.clinicafemina.integration.whatsapp.WhatsappProviderType.UAZAP,
                        "558391114004"
                ));
        service(resolver).enviar(
                30L,
                9L,
                new EnviarMensagemRequest("TEXTO", "Mensagem inicial"),
                99L
        );

        verify(uazapClient).sendText("5583991114004", "Mensagem inicial");
        verify(uazapClient, org.mockito.Mockito.never()).sendText("558391114004", "Mensagem inicial");
        ArgumentCaptor<Mensagem> captor = ArgumentCaptor.forClass(Mensagem.class);
        verify(mensagemRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        Mensagem persisted = captor.getAllValues().getLast();
        assertEquals("ENVIADA", persisted.getWhatsappStatus());
        assertEquals("wamid.UZAPI-NOVO", persisted.getWhatsappMessageId());
        assertEquals("558391114004", atendimento.getWhatsappChatId());
    }
}
