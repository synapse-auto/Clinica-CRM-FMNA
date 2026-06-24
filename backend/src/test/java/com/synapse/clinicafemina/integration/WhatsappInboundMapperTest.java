package com.synapse.clinicafemina.integration;

import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Mensagem;
import com.synapse.clinicafemina.domain.Paciente;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.env.Environment;

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
                new WhatsappInboundPayloadParser(),
                environment
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
        verify(notificationService).notificarNovaMensagem(any(), any());
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

        verify(midiaMensagemRepository).save(any());
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
}
