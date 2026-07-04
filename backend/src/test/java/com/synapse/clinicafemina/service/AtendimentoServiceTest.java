package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.*;
import com.synapse.clinicafemina.dto.TransferirAtendimentoRequest;
import com.synapse.clinicafemina.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AtendimentoServiceTest {

    @Mock private AtendimentoRepository atendimentoRepository;
    @Mock private MensagemRepository mensagemRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private TransferenciaAtendimentoRepository transferenciaRepository;
    @Mock private AtendimentoNotificationService notificationService;
    @Mock private RealtimeBroadcastService broadcastService;

    private AtendimentoService service;
    private Atendimento atendimento;
    private Clinica clinica;

    @BeforeEach
    void setUp() {
        service = new AtendimentoService(
                atendimentoRepository,
                mensagemRepository,
                usuarioRepository,
                transferenciaRepository,
                notificationService,
                broadcastService
        );
        clinica = new Clinica();
        clinica.setId(1L);
        Paciente paciente = new Paciente();
        paciente.setId(2L);
        paciente.setClinica(clinica);
        paciente.setNome("Paciente");
        paciente.setNomeBusca("PACIENTE");
        paciente.setTelefone("5544999999999");
        paciente.setTelefoneNormalizado("5544999999999");
        paciente.setRequerRevisao(false);
        atendimento = new Atendimento();
        atendimento.setId(3L);
        atendimento.setClinica(clinica);
        atendimento.setPaciente(paciente);
        atendimento.setStatus("ATIVO");
        atendimento.setTratadoPorIa(true);
        atendimento.setNaoLidas(1);
    }

    @Test
    void should_apply_real_filters_when_listing() {
        when(atendimentoRepository.findByClinica(
                eq(1L), isNull(), isNull(), eq(10L),
                eq(false), eq(false), eq(false), eq(""), any()
        )).thenReturn(new PageImpl<>(List.of(atendimento)));
        when(mensagemRepository.findFirstByAtendimentoIdOrderByDataHoraDesc(3L))
                .thenReturn(Optional.empty());

        var result = service.listar(
                1L, null, "TODOS", "MEUS", null, 10L, PageRequest.of(0, 20)
        );

        assertFalse(result.isEmpty());
    }

    @Test
    void should_audit_and_notify_when_transferring_from_ai_to_human() {
        Recepcionista destinatario = new Recepcionista();
        destinatario.setId(10L);
        destinatario.setClinica(clinica);
        destinatario.setNome("Recepção");
        destinatario.setPerfil("RECEPCIONISTA");
        Gestor responsavel = new Gestor();
        responsavel.setId(11L);
        responsavel.setClinica(clinica);
        responsavel.setNome("Gestor");
        responsavel.setPerfil("GESTOR");

        when(atendimentoRepository.findByIdAndClinicaId(3L, 1L))
                .thenReturn(Optional.of(atendimento));
        when(usuarioRepository.findAtivoByIdAndClinicaId(10L, 1L))
                .thenReturn(Optional.of(destinatario));
        when(usuarioRepository.findAtivoByIdAndClinicaId(11L, 1L))
                .thenReturn(Optional.of(responsavel));
        when(atendimentoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferenciaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.transferir(
                3L,
                new TransferirAtendimentoRequest(10L, "Transferência"),
                1L,
                11L
        );

        assertFalse(atendimento.getTratadoPorIa());
        verify(transferenciaRepository).save(any(TransferenciaAtendimento.class));
        verify(notificationService).notificarAtribuicao(atendimento, destinatario);
    }

    @Test
    void should_return_human_atendimento_to_ai_mode() {
        Recepcionista atendente = new Recepcionista();
        atendente.setId(10L);
        atendimento.setAtendentePrincipal(atendente);
        atendimento.setTratadoPorIa(false);

        when(atendimentoRepository.findByIdAndClinicaId(3L, 1L))
                .thenReturn(Optional.of(atendimento));
        when(atendimentoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.ativarModoIa(3L, 1L);

        assertTrue(atendimento.getTratadoPorIa());
        assertNull(atendimento.getAtendentePrincipal());
        assertTrue(result.tratadoPorIa());
        assertNull(result.atendentePrincipal());
        verify(atendimentoRepository).save(atendimento);
    }
}
