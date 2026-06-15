package com.synapse.clinicafemina.integration;

import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Mensagem;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.ClinicaRepository;
import com.synapse.clinicafemina.repository.MensagemRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import com.synapse.clinicafemina.service.N8nEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
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
    private ClinicaRepository clinicaRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private N8nEventService n8nEventService;

    private WhatsappInboundMapper mapper;
    private Clinica clinica;

    @BeforeEach
    void setUp() {
        mapper = new WhatsappInboundMapper(
                pacienteRepository,
                atendimentoRepository,
                mensagemRepository,
                clinicaRepository,
                rabbitTemplate,
                n8nEventService
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
        verify(n8nEventService).criarPayload(eq(clinica), eq("nova_mensagem"), any(), any(), any(), any());
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
    }

    @Test
    void should_ignore_payload_when_phone_number_id_is_unknown() {
        when(clinicaRepository.findByWhatsappPhoneNumberId("unknown-phone")).thenReturn(Optional.empty());

        mapper.processarMensagemTexto(validValuePayload("unknown-phone"));

        verify(pacienteRepository, never()).save(any(Paciente.class));
        verify(atendimentoRepository, never()).save(any(Atendimento.class));
        verify(mensagemRepository, never()).save(any(Mensagem.class));
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
}
