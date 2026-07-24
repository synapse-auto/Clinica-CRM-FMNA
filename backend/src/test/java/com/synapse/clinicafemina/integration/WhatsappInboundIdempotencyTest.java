package com.synapse.clinicafemina.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Mensagem;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.ClinicaRepository;
import com.synapse.clinicafemina.repository.MensagemRepository;
import com.synapse.clinicafemina.repository.MidiaMensagemRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import com.synapse.clinicafemina.service.AtendimentoNotificationService;
import com.synapse.clinicafemina.service.HorarioIaService;
import com.synapse.clinicafemina.service.N8nEventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("WhatsappInboundMapper — idempotência: webhook duplicado é ignorado sem erro")
class WhatsappInboundIdempotencyTest {

    private final PacienteRepository pacienteRepository = mock(PacienteRepository.class);
    private final AtendimentoRepository atendimentoRepository = mock(AtendimentoRepository.class);
    private final MensagemRepository mensagemRepository = mock(MensagemRepository.class);
    private final MidiaMensagemRepository midiaRepository = mock(MidiaMensagemRepository.class);
    private final ClinicaRepository clinicaRepository = mock(ClinicaRepository.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final N8nEventService n8nEventService = mock(N8nEventService.class);
    private final HorarioIaService horarioIaService = mock(HorarioIaService.class);
    private final AtendimentoNotificationService notificationService = mock(AtendimentoNotificationService.class);
    private final WhatsappInboundPayloadParser payloadParser = mock(WhatsappInboundPayloadParser.class);
    private final Environment environment = mock(Environment.class);
    private final WhatsappOutboundClient whatsappOutboundClient = mock(WhatsappOutboundClient.class);

    private WhatsappInboundMapper mapper() {
        return new WhatsappInboundMapper(
                pacienteRepository, atendimentoRepository, mensagemRepository, midiaRepository,
                clinicaRepository, rabbitTemplate, n8nEventService, horarioIaService,
                notificationService, new ObjectMapper(), payloadParser, environment, whatsappOutboundClient,
                java.util.List.of(new com.synapse.clinicafemina.integration.whatsapp.meta.MetaWhatsappMediaDownloader(
                        whatsappOutboundClient, new com.synapse.clinicafemina.integration.whatsapp.config.WhatsappProperties())),
                mock(org.springframework.context.ApplicationEventPublisher.class),
                new com.synapse.clinicafemina.integration.whatsapp.config.WhatsappProperties());
    }

    @Test
    @DisplayName("mensagem com whatsapp_message_id já existente não é persistida novamente")
    void duplicateMessage_isIgnored() {
        Clinica clinica = mock(Clinica.class);
        when(clinica.getId()).thenReturn(1L);
        when(clinicaRepository.findByWhatsappPhoneNumberId("PNID-1")).thenReturn(Optional.of(clinica));

        Mensagem existente = mock(Mensagem.class);
        lenient().when(existente.getId()).thenReturn(10L);
        lenient().when(existente.getTipoMedia()).thenReturn("TEXTO");
        when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(1L, "UZ-DUP"))
                .thenReturn(Optional.of(existente));

        Map<String, Object> value = Map.of(
                "metadata", Map.of("phone_number_id", "PNID-1"),
                "contacts", List.of(Map.of("wa_id", "5511988887777", "profile", Map.of("name", "Maria"))),
                "messages", List.of(Map.of(
                        "from", "5511988887777",
                        "id", "UZ-DUP",
                        "type", "text",
                        "text", Map.of("body", "oi"),
                        "timestamp", "1700000000"))
        );

        assertThatCode(() -> mapper().processarMensagemTexto(value, null))
                .doesNotThrowAnyException();

        // Duplicada: nenhuma nova mensagem persistida.
        verify(mensagemRepository, never()).save(any(Mensagem.class));
    }
}
