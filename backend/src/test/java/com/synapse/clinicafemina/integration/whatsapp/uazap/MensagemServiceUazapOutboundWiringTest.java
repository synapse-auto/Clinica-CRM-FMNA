package com.synapse.clinicafemina.integration.whatsapp.uazap;

import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Mensagem;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.domain.Recepcionista;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.EnviarMensagemRequest;
import com.synapse.clinicafemina.integration.WhatsappOutboundClient;
import com.synapse.clinicafemina.integration.whatsapp.WhatsappProviderResolver;
import com.synapse.clinicafemina.integration.whatsapp.config.WhatsappProperties;
import com.synapse.clinicafemina.integration.whatsapp.meta.MetaWhatsappProvider;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.MensagemRepository;
import com.synapse.clinicafemina.repository.MidiaMensagemRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import com.synapse.clinicafemina.service.MensagemService;
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
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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
        paciente.setTelefoneNormalizado("5543988887777");

        atendimento = new Atendimento();
        atendimento.setId(30L);
        atendimento.setClinica(clinica);
        atendimento.setPaciente(paciente);
        atendimento.setStatus("ATIVO");
        atendimento.setTratadoPorIa(true);

        remetente = new Recepcionista();
        remetente.setId(99L);
        remetente.setClinica(clinica);

        when(atendimentoRepository.findByIdAndClinicaId(30L, 9L)).thenReturn(Optional.of(atendimento));
        when(usuarioRepository.findAtivoByIdAndClinicaId(99L, 9L)).thenReturn(Optional.of(remetente));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private MensagemService service(WhatsappProviderResolver resolver) {
        return new MensagemService(
                mensagemRepository, midiaMensagemRepository, atendimentoRepository, usuarioRepository,
                whatsappOutboundClient, rabbitTemplate, whatsappWindowService, resolver);
    }

    @Test
    @DisplayName("WHATSAPP_PROVIDER=UAZAP: mensagem de saída real atravessa resolver → UazapWhatsappProvider → POST correto; messageId é persistido; Meta não é tocado")
    void uazapProvider_sendsThroughRealPipeline_andPersistsMessageId() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient mockedRestClient = builder.build();

        WhatsappProperties properties = new WhatsappProperties();
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
                .andExpect(jsonPath("$.to").value("5543988887777"))
                .andExpect(jsonPath("$.type").value("text"))
                .andExpect(jsonPath("$.text.body").value("Ola FMNA via UAZAP"))
                .andRespond(withSuccess(
                        "{\"statusCode\":200,\"message\":\"ok\",\"queueId\":\"q1\",\"messageId\":\"UZ-OUT-1\"}",
                        MediaType.APPLICATION_JSON));

        service(resolver).enviar(30L, 9L, new EnviarMensagemRequest("TEXTO", "Ola FMNA via UAZAP"), 99L);

        server.verify(); // POST correto foi de fato recebido pelo mock HTTP UAZAP

        ArgumentCaptor<Mensagem> mensagemCaptor = ArgumentCaptor.forClass(Mensagem.class);
        verify(mensagemRepository, org.mockito.Mockito.atLeastOnce()).save(mensagemCaptor.capture());
        Mensagem mensagemFinal = mensagemCaptor.getAllValues().getLast();
        assertEquals("UZ-OUT-1", mensagemFinal.getWhatsappMessageId()); // messageId persistido/propagado
        assertEquals("ENVIADA", mensagemFinal.getWhatsappStatus());

        verifyNoInteractions(whatsappOutboundClient); // client Meta nunca foi chamado
    }

    @Test
    @DisplayName("WHATSAPP_PROVIDER=META (default): mensagem de saída real continua selecionando o WhatsappOutboundClient existente")
    void metaProvider_stillSelectsExistingClient() {
        WhatsappProperties properties = new WhatsappProperties(); // provider default = META
        WhatsappProviderResolver resolver = new WhatsappProviderResolver(
                List.of(new MetaWhatsappProvider(whatsappOutboundClient)), properties);
        when(whatsappOutboundClient.enviarTexto("5543988887777", "Ola FMNA via Meta"))
                .thenReturn("wamid-meta-out-1");

        service(resolver).enviar(30L, 9L, new EnviarMensagemRequest("TEXTO", "Ola FMNA via Meta"), 99L);

        verify(whatsappOutboundClient).validarConfiguracao();
        verify(whatsappOutboundClient).enviarTexto("5543988887777", "Ola FMNA via Meta");

        ArgumentCaptor<Mensagem> mensagemCaptor = ArgumentCaptor.forClass(Mensagem.class);
        verify(mensagemRepository, org.mockito.Mockito.atLeastOnce()).save(mensagemCaptor.capture());
        assertEquals("wamid-meta-out-1", mensagemCaptor.getAllValues().getLast().getWhatsappMessageId());
    }
}
