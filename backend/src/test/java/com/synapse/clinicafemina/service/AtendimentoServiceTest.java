package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.*;
import com.synapse.clinicafemina.dto.TransferirAtendimentoRequest;
import com.synapse.clinicafemina.repository.*;
import com.synapse.clinicafemina.integration.WhatsappOutboundClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
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
    @Mock private AtendimentoTagRepository atendimentoTagRepository;
    @Mock private PacienteTagRepository pacienteTagRepository;
    @Mock private WhatsappWindowService whatsappWindowService;
    @Mock private WhatsappOutboundClient whatsappOutboundClient;

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
                broadcastService,
                atendimentoTagRepository,
                pacienteTagRepository,
                whatsappWindowService,
                whatsappOutboundClient
        );
        org.mockito.Mockito.lenient().when(whatsappWindowService.avaliar(anyLong(), anyLong()))
                .thenReturn(new WhatsappWindowService.WindowState(false, null, null, false));
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

        var result = service.listar(
                1L, null, "TODOS", "MEUS", null, 10L, PageRequest.of(0, 20)
        );

        assertFalse(result.isEmpty());
    }

    @Test
    void should_not_fetch_last_message_one_by_one_when_listing() {
        Atendimento segundoAtendimento = new Atendimento();
        segundoAtendimento.setId(4L);
        segundoAtendimento.setClinica(clinica);
        segundoAtendimento.setPaciente(atendimento.getPaciente());
        segundoAtendimento.setStatus("ATIVO");
        segundoAtendimento.setTratadoPorIa(false);
        segundoAtendimento.setNaoLidas(0);
        when(atendimentoRepository.findByClinica(
                eq(1L), isNull(), isNull(), isNull(),
                eq(false), eq(false), eq(false), eq(""), any()
        )).thenReturn(new PageImpl<>(List.of(atendimento, segundoAtendimento)));

        var result = service.listar(
                1L, null, "TODOS", "TODOS", null, null, PageRequest.of(0, 20)
        );

        assertEquals(2, result.getTotalElements());
        verify(mensagemRepository, never()).findFirstByAtendimentoIdOrderByDataHoraDesc(3L);
        verify(mensagemRepository, never()).findFirstByAtendimentoIdOrderByDataHoraDesc(4L);
    }

    @Test
    void should_list_atendimentos_with_real_tags_in_batch() {
        Atendimento segundoAtendimento = new Atendimento();
        segundoAtendimento.setId(4L);
        segundoAtendimento.setClinica(clinica);
        Paciente outroPaciente = new Paciente();
        outroPaciente.setId(5L);
        outroPaciente.setClinica(clinica);
        outroPaciente.setNome("Outra Paciente");
        outroPaciente.setNomeBusca("OUTRA PACIENTE");
        outroPaciente.setTelefoneNormalizado("5544888888888");
        outroPaciente.setRequerRevisao(false);
        segundoAtendimento.setPaciente(outroPaciente);
        segundoAtendimento.setStatus("ATIVO");
        segundoAtendimento.setTratadoPorIa(false);
        segundoAtendimento.setNaoLidas(0);

        Tag tagAtendimento = criarTag(100L, "Retorno", "#0d9488");
        Tag tagPaciente = criarTag(101L, "Particular", "#f97316");

        when(atendimentoRepository.findByClinica(
                eq(1L), isNull(), isNull(), isNull(),
                eq(false), eq(false), eq(false), eq(""), any()
        )).thenReturn(new PageImpl<>(List.of(atendimento, segundoAtendimento)));
        when(atendimentoTagRepository.findTagsByAtendimentoIdsAndClinicaId(any(Collection.class), eq(1L)))
                .thenReturn(List.<Object[]>of(new Object[] {3L, tagAtendimento}));
        when(pacienteTagRepository.findTagsByPacienteIdsAndClinicaId(any(Collection.class), eq(1L)))
                .thenReturn(List.<Object[]>of(new Object[] {2L, tagPaciente}));

        var result = service.listar(
                1L, null, "TODOS", "TODOS", null, null, PageRequest.of(0, 20)
        );

        var tags = result.getContent().getFirst().tags();
        assertEquals(2, tags.size());
        assertTrue(tags.stream().anyMatch(tag -> tag.nome().equals("Retorno")));
        assertTrue(tags.stream().anyMatch(tag -> tag.nome().equals("Particular")));
        verify(atendimentoTagRepository).findTagsByAtendimentoIdsAndClinicaId(any(Collection.class), eq(1L));
        verify(pacienteTagRepository).findTagsByPacienteIdsAndClinicaId(any(Collection.class), eq(1L));
        verify(atendimentoTagRepository, never()).findTagsByAtendimentoIdAndClinicaId(anyLong(), anyLong());
        verify(pacienteTagRepository, never()).findTagsByPacienteIdAndClinicaId(anyLong(), anyLong());
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
        assertTrue(atendimento.getHumanoDesde() != null);
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
        assertNull(atendimento.getHumanoDesde());
        assertTrue(result.tratadoPorIa());
        assertNull(result.atendentePrincipal());
        verify(atendimentoRepository).save(atendimento);
    }

    @Test
    void should_return_human_atendimentos_to_ai_after_24_hours() {
        Recepcionista atendente = new Recepcionista();
        atendente.setId(10L);
        atendimento.setAtendentePrincipal(atendente);
        atendimento.setTratadoPorIa(false);
        atendimento.setHumanoDesde(OffsetDateTime.parse("2026-07-04T10:00:00Z"));

        OffsetDateTime now = OffsetDateTime.parse("2026-07-05T10:00:00Z");
        when(atendimentoRepository.findHumanosParaRetornoIa(now.minusHours(24)))
                .thenReturn(List.of(atendimento));
        when(atendimentoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        int total = service.retornarHumanosExpiradosParaIa(now);

        assertEquals(1, total);
        assertTrue(atendimento.getTratadoPorIa());
        assertNull(atendimento.getAtendentePrincipal());
        assertNull(atendimento.getHumanoDesde());
        verify(atendimentoRepository).save(atendimento);
    }

    @Test
    void should_keep_human_atendimentos_before_24_hours() {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-05T10:00:00Z");
        when(atendimentoRepository.findHumanosParaRetornoIa(now.minusHours(24)))
                .thenReturn(List.of());

        int total = service.retornarHumanosExpiradosParaIa(now);

        assertEquals(0, total);
        verify(atendimentoRepository, never()).save(any());
    }

    private Tag criarTag(Long id, String nome, String cor) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setClinica(clinica);
        tag.setNome(nome);
        tag.setCor(cor);
        tag.setAtivo(true);
        return tag;
    }
}
