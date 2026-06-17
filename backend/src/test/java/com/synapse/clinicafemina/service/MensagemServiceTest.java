package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Mensagem;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.dto.EnviarMensagemRequest;
import com.synapse.clinicafemina.integration.WhatsappOutboundClient;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.MensagemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MensagemServiceTest {

    @Mock
    private MensagemRepository mensagemRepository;

    @Mock
    private AtendimentoRepository atendimentoRepository;

    @Mock
    private WhatsappOutboundClient whatsappOutboundClient;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private MensagemService service;
    private Atendimento atendimento;

    @BeforeEach
    void setUp() {
        service = new MensagemService(
                mensagemRepository,
                atendimentoRepository,
                whatsappOutboundClient,
                rabbitTemplate
        );

        Clinica clinica = new Clinica();
        clinica.setId(9L);

        Paciente paciente = new Paciente();
        paciente.setId(20L);
        paciente.setClinica(clinica);
        paciente.setTelefoneNormalizado("5544999990000");

        atendimento = new Atendimento();
        atendimento.setId(30L);
        atendimento.setClinica(clinica);
        atendimento.setPaciente(paciente);
        atendimento.setStatus("ATIVO");
    }

    @Test
    void should_persist_human_text_message_with_atendente_sender() {
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
    }

    @Test
    void should_persist_human_media_message_with_atendente_sender() {
        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo",
                "exame.png",
                "image/png",
                new byte[] {1, 2, 3}
        );
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
    }
}
