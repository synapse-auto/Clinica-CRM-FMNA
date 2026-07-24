package com.synapse.clinicafemina.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Mensagem;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.integration.whatsapp.config.WhatsappProperties;
import com.synapse.clinicafemina.integration.whatsapp.uazap.UazapPictureEnrichmentRequestedEvent;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.ClinicaRepository;
import com.synapse.clinicafemina.repository.MensagemRepository;
import com.synapse.clinicafemina.repository.MidiaMensagemRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import com.synapse.clinicafemina.service.AtendimentoNotificationService;
import com.synapse.clinicafemina.service.HorarioIaService;
import com.synapse.clinicafemina.service.N8nEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica exclusivamente o gate de publicação do {@link UazapPictureEnrichmentRequestedEvent}:
 * a UltraMedical (provider META, valor padrão) nunca deve publicar o evento — comportamento 100%
 * inalterado. A FMNA (provider UAZAP) deve publicá-lo após o paciente ser resolvido.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WhatsappInboundMapper — gate de publicação do evento de foto UAZAP")
class WhatsappInboundMapperUazapPictureEventTest {

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
    @Mock private ApplicationEventPublisher eventPublisher;

    private WhatsappProperties whatsappProperties;
    private Clinica clinica;

    @BeforeEach
    void setUp() {
        whatsappProperties = new WhatsappProperties();
        clinica = new Clinica();
        clinica.setId(2L);
        clinica.setWhatsappPhoneNumberId("phone-1");
        lenient().when(clinicaRepository.findByWhatsappPhoneNumberId("phone-1")).thenReturn(Optional.of(clinica));
        lenient().when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(2L, "wamid-1")).thenReturn(Optional.empty());
        lenient().when(pacienteRepository.findByClinicaIdAndTelefoneNormalizado(2L, "5511999990000")).thenReturn(Optional.empty());
        lenient().when(pacienteRepository.save(any(Paciente.class))).thenAnswer(invocation -> {
            Paciente paciente = invocation.getArgument(0);
            paciente.setId(20L);
            return paciente;
        });
        lenient().when(atendimentoRepository.findAtivo(2L, null)).thenReturn(Optional.empty());
        lenient().when(atendimentoRepository.existeEncerradoDesde(any(), any(), any())).thenReturn(false);
        lenient().when(atendimentoRepository.save(any(Atendimento.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(horarioIaService.avaliar(any(Clinica.class)))
                .thenReturn(new HorarioIaService.HorarioIaStatus(true, HorarioIaService.DENTRO_HORARIO));
    }

    private WhatsappInboundMapper mapper() {
        return new WhatsappInboundMapper(
                pacienteRepository, atendimentoRepository, mensagemRepository, midiaMensagemRepository,
                clinicaRepository, rabbitTemplate, n8nEventService, horarioIaService,
                notificationService, new ObjectMapper(), new WhatsappInboundPayloadParser(),
                environment, whatsappOutboundClient,
                List.of(new com.synapse.clinicafemina.integration.whatsapp.meta.MetaWhatsappMediaDownloader(
                        whatsappOutboundClient, whatsappProperties)),
                eventPublisher, whatsappProperties);
    }

    private Map<String, Object> payload() {
        return Map.of(
                "metadata", Map.of("phone_number_id", "phone-1"),
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

    @Test
    @DisplayName("provider META (default, UltraMedical): nunca publica o evento de foto UAZAP")
    void metaProvider_neverPublishesEvent() {
        mapper().processarMensagemTexto(payload());

        verify(eventPublisher, never()).publishEvent(any(UazapPictureEnrichmentRequestedEvent.class));
    }

    @Test
    @DisplayName("provider UAZAP (FMNA): publica o evento com o pacienteId após a mensagem ser persistida")
    void uazapProvider_publishesEventWithPacienteId() {
        whatsappProperties.setProvider("UAZAP");

        mapper().processarMensagemTexto(payload());

        verify(eventPublisher).publishEvent(new UazapPictureEnrichmentRequestedEvent(20L));
    }
}
