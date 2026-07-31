package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.*;
import com.synapse.clinicafemina.dto.TransferirAtendimentoRequest;
import com.synapse.clinicafemina.dto.n8n.N8nTransferirProximoHumanoRequest;
import com.synapse.clinicafemina.exception.IdempotencyConflictException;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.dto.WhatsappCapabilitiesDTO;
import com.synapse.clinicafemina.repository.*;
import com.synapse.clinicafemina.integration.WhatsappOutboundClient;
import com.synapse.clinicafemina.integration.whatsapp.WhatsappProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AtendimentoServiceTest {

    @Mock private AtendimentoRepository atendimentoRepository;
    @Mock private ClinicaRepository clinicaRepository;
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
                clinicaRepository,
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
        stubList(List.of(atendimento));

        var result = service.listar(
                1L, null, "TODOS", "MEUS", null, 10L, PageRequest.of(0, 20)
        );

        assertFalse(result.isEmpty());
    }

    @Test
    void should_list_only_active_attendances_for_operational_filters() {
        stubList(List.of(atendimento));

        service.listar(1L, null, "TODOS", "TODOS", null, null, PageRequest.of(0, 20));
        service.listar(1L, null, "IA", "TODOS", null, null, PageRequest.of(0, 20));
        service.listar(1L, null, "HUMANO", "TODOS", null, null, PageRequest.of(0, 20));

        verify(atendimentoRepository, times(3)).findByClinica(
                eq(1L), eq("ATIVO"), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(),
                anyInt(), anyString(), anyString(), anyString(), anyString(), any(),
                anyString(), anyString(), anyString(), anyString(), anyString(), any()
        );
    }

    @Test
    void should_list_only_closed_attendances_for_finalizados_filter() {
        stubList(List.of(atendimento));

        service.listar(1L, null, "TODOS", "FINALIZADOS", null, null, PageRequest.of(0, 20));

        verify(atendimentoRepository).findByClinica(
                eq(1L), eq("ENCERRADO"), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(),
                anyInt(), anyString(), anyString(), anyString(), anyString(), any(),
                anyString(), anyString(), anyString(), anyString(), anyString(), any()
        );
    }

    @Test
    void should_close_active_attendance_with_default_reason_and_preserve_relations() {
        Paciente pacienteOriginal = atendimento.getPaciente();
        when(atendimentoRepository.findByIdAndClinicaIdForUpdate(3L, 1L))
                .thenReturn(Optional.of(atendimento));
        when(atendimentoRepository.save(atendimento)).thenReturn(atendimento);

        service.encerrar(3L, 1L, null);

        assertEquals("ENCERRADO", atendimento.getStatus());
        assertTrue(atendimento.getDataEncerramento() != null);
        assertEquals(MotivoEncerramentoAtendimento.PADRAO_MANUAL, atendimento.getMotivoEncerramento());
        assertSame(pacienteOriginal, atendimento.getPaciente());
        verify(atendimentoRepository).save(atendimento);
        verify(mensagemRepository, never()).save(any(Mensagem.class));
        verify(notificationService, never()).notificarAtribuicao(any(), any());
        verify(broadcastService, never()).broadcastTransferencia(anyLong(), anyLong(), anyLong(), anyString(), anyLong(), anyString(), anyString());
        verify(whatsappOutboundClient, never()).enviarTextoComResultado(anyString(), anyString());
    }

    @Test
    void should_keep_original_closure_when_attendance_is_already_closed() {
        OffsetDateTime encerradoEm = OffsetDateTime.parse("2026-07-29T10:00:00Z");
        atendimento.setStatus("ENCERRADO");
        atendimento.setDataEncerramento(encerradoEm);
        atendimento.setMotivoEncerramento("Motivo original");
        when(atendimentoRepository.findByIdAndClinicaIdForUpdate(3L, 1L))
                .thenReturn(Optional.of(atendimento));

        service.encerrar(3L, 1L, "Outro motivo");

        assertEquals(encerradoEm, atendimento.getDataEncerramento());
        assertEquals("Motivo original", atendimento.getMotivoEncerramento());
        verify(atendimentoRepository, never()).save(any());
    }

    @Test
    void should_not_close_attendance_from_another_clinic() {
        when(atendimentoRepository.findByIdAndClinicaIdForUpdate(3L, 2L))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.encerrar(3L, 2L, null));

        verify(atendimentoRepository, never()).save(any());
    }

    @Test
    void should_combine_smart_search_with_existing_filters() {
        stubList(List.of(atendimento));

        service.listar(
                1L, "ATIVO", "HUMANO", "MEUS", "silva joao", 10L, PageRequest.of(0, 20)
        );

        verify(atendimentoRepository).findByClinica(
                1L, "ATIVO", false, 10L,
                false, false, false, 1,
                "SILVA JOAO", "", "", "", null,
                "SILVA", "JOAO", "", "", "", PageRequest.of(0, 20)
        );
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
        stubList(List.of(atendimento, segundoAtendimento));

        var result = service.listar(
                1L, null, "TODOS", "TODOS", null, null, PageRequest.of(0, 20)
        );

        assertEquals(2, result.getTotalElements());
        verify(mensagemRepository, never()).findFirstByAtendimentoIdOrderByDataHoraDesc(3L);
        verify(mensagemRepository, never()).findFirstByAtendimentoIdOrderByDataHoraDesc(4L);
    }

    @Test
    void should_expose_uazap_capabilities_without_meta_template_lookup() {
        when(atendimentoRepository.findByIdAndClinicaId(3L, 1L)).thenReturn(Optional.of(atendimento));
        when(whatsappWindowService.capabilities())
                .thenReturn(WhatsappCapabilitiesDTO.forProvider(WhatsappProviderType.UAZAP));

        var result = service.buscarPorId(3L, 1L);

        assertEquals(WhatsappProviderType.UAZAP, result.whatsappCapabilities().provider());
        assertFalse(result.whatsappCapabilities().enforcesCustomerCareWindow());
        assertFalse(result.whatsappCapabilities().supportsMessageTemplates());
        assertFalse(result.whatsappTemplatesDisponiveis());
        verify(whatsappOutboundClient, never()).templatesDisponiveis();
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

        stubList(List.of(atendimento, segundoAtendimento));
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
        when(usuarioRepository.findById(10L))
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
    void should_register_ai_summary_and_notify_on_n8n_transfer() {
        Recepcionista destinatario = new Recepcionista();
        destinatario.setId(10L);
        destinatario.setClinica(clinica);
        destinatario.setNome("Recepção");
        destinatario.setPerfil("RECEPCIONISTA");

        when(atendimentoRepository.findByIdAndClinicaIdForUpdate(3L, 1L))
                .thenReturn(Optional.of(atendimento));
        when(usuarioRepository.findById(10L))
                .thenReturn(Optional.of(destinatario));
        when(atendimentoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferenciaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mensagemRepository.findLatestAiHandoffSummarySince(eq(3L), eq(1L), any()))
                .thenReturn(Optional.empty());
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> {
            Mensagem mensagem = invocation.getArgument(0);
            mensagem.setId(88L);
            return mensagem;
        });
        when(notificationService.notificarTransferenciaIa(
                eq(atendimento), any(Mensagem.class), anyString(), eq(destinatario), any()))
                .thenReturn(new AtendimentoNotificationService.TransferenciaNotificacaoResultado(2, List.of(10L)));

        var result = service.transferirPorN8n(
                3L,
                new TransferirAtendimentoRequest(
                        10L,
                        "Transferido pelo N8N",
                        "Paciente pediu continuidade com a recepção.",
                        "Solicitação explícita do paciente"
                ),
                1L
        );

        assertTrue(result.transferido());
        assertFalse(result.jaEstavaTransferido());
        assertTrue(result.resumoRegistrado());
        assertEquals(2, result.notificacoesCriadas());
        assertFalse(atendimento.getTratadoPorIa());
        List<Mensagem> eventos = org.mockito.Mockito.mockingDetails(mensagemRepository)
                .getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("save"))
                .map(invocation -> (Mensagem) invocation.getArgument(0))
                .filter(mensagem -> "SISTEMA".equals(mensagem.getDirecao()))
                .toList();
        assertEquals(List.of("AI_HANDOFF_ENDED", "HUMAN_HANDOFF_START", "AI_HANDOFF_SUMMARY"),
                eventos.stream().map(Mensagem::getTipoMedia).toList());
        assertEquals("Fim das mensagens com a IA", eventos.get(0).getConteudo());
        assertEquals("Atendimento #3 transferido para humano", eventos.get(1).getConteudo());
        verify(notificationService).notificarTransferenciaIa(
                eq(atendimento), any(Mensagem.class), anyString(), eq(destinatario), any());
    }

    @Test
    void should_register_a_new_ai_summary_after_returning_to_ai_for_a_second_handoff() {
        Recepcionista destinatario = atendente(10L);
        List<Mensagem> resumosPersistidos = new java.util.ArrayList<>();

        when(atendimentoRepository.findByIdAndClinicaIdForUpdate(3L, 1L))
                .thenReturn(Optional.of(atendimento));
        when(atendimentoRepository.findByIdAndClinicaId(3L, 1L))
                .thenReturn(Optional.of(atendimento));
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(destinatario));
        when(atendimentoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferenciaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mensagemRepository.existsSystemEvent(3L, 1L, "AI_HANDOFF_ENDED")).thenReturn(true);
        when(mensagemRepository.existsSystemEvent(3L, 1L, "HUMAN_HANDOFF_START")).thenReturn(true);
        when(mensagemRepository.findLatestAiHandoffSummarySince(eq(3L), eq(1L), any()))
                .thenAnswer(invocation -> resumosPersistidos.stream()
                        .filter(mensagem -> !mensagem.getDataHora().isBefore(invocation.getArgument(2)))
                        .max(java.util.Comparator.comparing(Mensagem::getDataHora)));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> {
            Mensagem mensagem = invocation.getArgument(0);
            if ("AI_HANDOFF_SUMMARY".equals(mensagem.getTipoMedia())) {
                mensagem.setId(80L + resumosPersistidos.size());
                resumosPersistidos.add(mensagem);
            }
            return mensagem;
        });
        when(notificationService.notificarTransferenciaIa(
                eq(atendimento), any(Mensagem.class), anyString(), eq(destinatario), any()))
                .thenReturn(new AtendimentoNotificationService.TransferenciaNotificacaoResultado(0, List.of()));

        service.transferirPorN8n(
                3L,
                new TransferirAtendimentoRequest(10L, "Primeiro handoff", "Resumo do primeiro ciclo", null),
                1L
        );
        service.ativarModoIa(3L, 1L);
        var segundoHandoff = service.transferirPorN8n(
                3L,
                new TransferirAtendimentoRequest(10L, "Segundo handoff", "Resumo do segundo ciclo", null),
                1L
        );
        var repeticaoDoSegundoHandoff = service.transferirPorN8n(
                3L,
                new TransferirAtendimentoRequest(10L, "Segundo handoff", "Resumo do segundo ciclo", null),
                1L
        );

        List<String> resumos = org.mockito.Mockito.mockingDetails(mensagemRepository)
                .getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("save"))
                .map(invocation -> (Mensagem) invocation.getArgument(0))
                .filter(mensagem -> "AI_HANDOFF_SUMMARY".equals(mensagem.getTipoMedia()))
                .map(Mensagem::getConteudo)
                .toList();
        assertTrue(segundoHandoff.resumoRegistrado());
        assertFalse(repeticaoDoSegundoHandoff.resumoRegistrado());
        assertEquals(List.of("Resumo do primeiro ciclo", "Resumo do segundo ciclo"), resumos);
        org.mockito.ArgumentCaptor<Mensagem> resumoCaptor =
                org.mockito.ArgumentCaptor.forClass(Mensagem.class);
        verify(notificationService, times(3)).notificarTransferenciaIa(
                eq(atendimento), resumoCaptor.capture(), anyString(), eq(destinatario), any());
        assertEquals(
                List.of("Resumo do primeiro ciclo", "Resumo do segundo ciclo", "Resumo do segundo ciclo"),
                resumoCaptor.getAllValues().stream().map(Mensagem::getConteudo).toList()
        );
    }

    @Test
    void should_select_next_attendant_in_n8n_rotation_without_using_manual_history() {
        Recepcionista primeira = atendente(10L);
        Recepcionista segunda = atendente(11L);
        when(clinicaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(clinica));
        when(transferenciaRepository.findByIdempotencyKey("rodizio-1"))
                .thenReturn(Optional.empty());
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(primeira));
        when(usuarioRepository.findById(11L)).thenReturn(Optional.of(segunda));
        when(transferenciaRepository.findDestinatariosPorOrigem(
                eq(1L), eq("N8N_RODIZIO"), eq(List.of(10L, 11L)), any()))
                .thenReturn(List.of(10L));
        when(atendimentoRepository.findByIdAndClinicaIdForUpdate(3L, 1L)).thenReturn(Optional.of(atendimento));
        when(atendimentoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferenciaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationService.notificarTransferenciaIa(
                eq(atendimento), isNull(), anyString(), eq(segunda), any()))
                .thenReturn(new AtendimentoNotificationService.TransferenciaNotificacaoResultado(0, List.of()));

        var result = service.transferirProximoPorN8n(
                3L,
                new N8nTransferirProximoHumanoRequest(List.of(10L, 11L), "Rodízio", null, null),
                "rodizio-1",
                1L
        );

        assertEquals(11L, result.novoAtendenteId());
        assertEquals(1, result.posicaoSelecionada());
        assertEquals(11L, atendimento.getAtendentePrincipal().getId());
        org.mockito.ArgumentCaptor<TransferenciaAtendimento> captor =
                org.mockito.ArgumentCaptor.forClass(TransferenciaAtendimento.class);
        verify(transferenciaRepository).save(captor.capture());
        assertEquals("N8N_RODIZIO", captor.getValue().getOrigem());
        assertEquals("rodizio-1", captor.getValue().getIdempotencyKey());
    }

    @Test
    void should_start_n8n_rotation_with_first_configured_attendant_when_no_history_exists() {
        Recepcionista primeira = atendente(10L);
        Recepcionista segunda = atendente(11L);
        when(clinicaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(clinica));
        when(transferenciaRepository.findByIdempotencyKey("rodizio-2"))
                .thenReturn(Optional.empty());
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(primeira));
        when(usuarioRepository.findById(11L)).thenReturn(Optional.of(segunda));
        when(transferenciaRepository.findDestinatariosPorOrigem(anyLong(), anyString(), anyList(), any()))
                .thenReturn(List.of());
        when(atendimentoRepository.findByIdAndClinicaIdForUpdate(3L, 1L)).thenReturn(Optional.of(atendimento));
        when(atendimentoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferenciaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationService.notificarTransferenciaIa(
                eq(atendimento), isNull(), anyString(), eq(primeira), any()))
                .thenReturn(new AtendimentoNotificationService.TransferenciaNotificacaoResultado(0, List.of()));

        var result = service.transferirProximoPorN8n(
                3L,
                new N8nTransferirProximoHumanoRequest(List.of(10L, 11L), "Rodízio", null, null),
                "rodizio-2",
                1L
        );

        assertEquals(10L, result.novoAtendenteId());
        assertEquals(0, result.posicaoSelecionada());
    }

    @Test
    void should_return_same_attendant_when_n8n_rotation_is_retried_with_same_key() {
        Recepcionista segunda = atendente(11L);
        atendimento.setTratadoPorIa(false);
        atendimento.setAtendentePrincipal(segunda);
        TransferenciaAtendimento transferenciaAnterior = new TransferenciaAtendimento();
        transferenciaAnterior.setAtendimento(atendimento);
        transferenciaAnterior.setParaUsuario(segunda);
        transferenciaAnterior.setTransferidoEm(OffsetDateTime.parse("2026-07-28T15:00:00Z"));
        N8nTransferirProximoHumanoRequest request =
                new N8nTransferirProximoHumanoRequest(List.of(10L, 11L), "Rodízio", null, null);
        transferenciaAnterior.setOrigem("N8N_RODIZIO");
        transferenciaAnterior.setIdempotencyFingerprint(request.fingerprint(3L));
        when(clinicaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(clinica));
        when(transferenciaRepository.findByIdempotencyKey("rodizio-retry"))
                .thenReturn(Optional.of(transferenciaAnterior));

        var result = service.transferirProximoPorN8n(
                3L,
                request,
                "rodizio-retry",
                1L
        );

        assertEquals(11L, result.novoAtendenteId());
        assertEquals(1, result.posicaoSelecionada());
        verify(transferenciaRepository, never()).findDestinatariosPorOrigem(anyLong(), anyString(), anyList(), any());
        verify(atendimentoRepository, never()).save(any());
        verify(notificationService, never()).notificarTransferenciaIa(any(), any(), anyString(), any(), any());
    }

    @Test
    void should_reject_reused_rotation_key_with_different_payload() {
        Recepcionista primeira = atendente(10L);
        atendimento.setAtendentePrincipal(primeira);
        N8nTransferirProximoHumanoRequest original =
                new N8nTransferirProximoHumanoRequest(List.of(10L, 11L), "Rodízio", null, null);
        TransferenciaAtendimento existente = new TransferenciaAtendimento();
        existente.setAtendimento(atendimento);
        existente.setParaUsuario(primeira);
        existente.setOrigem("N8N_RODIZIO");
        existente.setIdempotencyFingerprint(original.fingerprint(3L));
        when(clinicaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(clinica));
        when(transferenciaRepository.findByIdempotencyKey("rodizio-conflito"))
                .thenReturn(Optional.of(existente));

        N8nTransferirProximoHumanoRequest diferente =
                new N8nTransferirProximoHumanoRequest(List.of(11L, 10L), "Rodízio", null, null);

        assertThrows(
                IdempotencyConflictException.class,
                () -> service.transferirProximoPorN8n(3L, diferente, "rodizio-conflito", 1L)
        );
        verify(atendimentoRepository, never()).save(any());
        verify(notificationService, never()).notificarTransferenciaIa(any(), any(), anyString(), any(), any());
    }

    @Test
    void should_reject_reused_rotation_key_for_another_attendance() {
        Recepcionista primeira = atendente(10L);
        N8nTransferirProximoHumanoRequest request =
                new N8nTransferirProximoHumanoRequest(List.of(10L, 11L), "Rodízio", null, null);
        TransferenciaAtendimento existente = new TransferenciaAtendimento();
        existente.setAtendimento(atendimento);
        existente.setParaUsuario(primeira);
        existente.setOrigem("N8N_RODIZIO");
        existente.setIdempotencyFingerprint(request.fingerprint(3L));
        when(clinicaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(clinica));
        when(transferenciaRepository.findByIdempotencyKey("rodizio-outro-atendimento"))
                .thenReturn(Optional.of(existente));

        assertThrows(
                IdempotencyConflictException.class,
                () -> service.transferirProximoPorN8n(
                        99L, request, "rodizio-outro-atendimento", 1L
                )
        );
        verify(atendimentoRepository, never()).save(any());
    }

    @Test
    void should_register_handoff_markers_even_without_ai_summary() {
        Recepcionista destinatario = new Recepcionista();
        destinatario.setId(10L);
        destinatario.setClinica(clinica);
        destinatario.setPerfil("RECEPCIONISTA");
        when(atendimentoRepository.findByIdAndClinicaIdForUpdate(3L, 1L)).thenReturn(Optional.of(atendimento));
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(destinatario));
        when(atendimentoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferenciaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationService.notificarTransferenciaIa(
                eq(atendimento), isNull(), anyString(), eq(destinatario), any()))
                .thenReturn(new AtendimentoNotificationService.TransferenciaNotificacaoResultado(0, List.of()));

        var result = service.transferirPorN8n(
                3L,
                new TransferirAtendimentoRequest(10L, "Transferido pelo N8N"),
                1L
        );

        List<String> tipos = org.mockito.Mockito.mockingDetails(mensagemRepository)
                .getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("save"))
                .map(invocation -> ((Mensagem) invocation.getArgument(0)).getTipoMedia())
                .toList();
        assertEquals(List.of("AI_HANDOFF_ENDED", "HUMAN_HANDOFF_START"), tipos);
        assertFalse(result.resumoRegistrado());
    }

    @Test
    void should_make_n8n_transfer_idempotent_when_already_human() {
        Recepcionista destinatario = new Recepcionista();
        destinatario.setId(10L);
        destinatario.setClinica(clinica);
        destinatario.setPerfil("RECEPCIONISTA");
        atendimento.setTratadoPorIa(false);
        atendimento.setAtendentePrincipal(destinatario);
        atendimento.setHumanoDesde(OffsetDateTime.parse("2026-07-03T12:00:00Z"));
        when(atendimentoRepository.findByIdAndClinicaIdForUpdate(3L, 1L))
                .thenReturn(Optional.of(atendimento));
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(destinatario));
        when(mensagemRepository.existsSystemEvent(3L, 1L, "AI_HANDOFF_ENDED")).thenReturn(true);
        when(mensagemRepository.existsSystemEvent(3L, 1L, "HUMAN_HANDOFF_START")).thenReturn(true);
        when(notificationService.notificarTransferenciaIa(
                eq(atendimento), isNull(), anyString(), eq(destinatario), any()))
                .thenReturn(new AtendimentoNotificationService.TransferenciaNotificacaoResultado(0, List.of()));

        var result = service.transferirPorN8n(
                3L,
                new TransferirAtendimentoRequest(10L, "Retry"),
                1L
        );

        assertTrue(result.transferido());
        assertTrue(result.jaEstavaTransferido());
        assertFalse(result.resumoRegistrado());
        assertEquals(0, result.notificacoesCriadas());
        verify(atendimentoRepository, never()).save(any());
        verify(usuarioRepository).findById(10L);
        verify(transferenciaRepository, never()).save(any());
        verify(notificationService).notificarTransferenciaIa(
                eq(atendimento), isNull(), anyString(), eq(destinatario), any());
        verify(mensagemRepository, never()).save(any());
    }

    @Test
    void should_repair_missing_handoff_events_for_idempotent_n8n_transfer() {
        Recepcionista destinatario = new Recepcionista();
        destinatario.setId(10L);
        destinatario.setClinica(clinica);
        destinatario.setPerfil("RECEPCIONISTA");
        atendimento.setTratadoPorIa(false);
        atendimento.setAtendentePrincipal(destinatario);
        atendimento.setHumanoDesde(OffsetDateTime.parse("2026-07-03T12:00:00Z"));
        when(atendimentoRepository.findByIdAndClinicaIdForUpdate(3L, 1L)).thenReturn(Optional.of(atendimento));
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(destinatario));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationService.notificarTransferenciaIa(
                eq(atendimento), isNull(), anyString(), eq(destinatario), any()))
                .thenReturn(new AtendimentoNotificationService.TransferenciaNotificacaoResultado(0, List.of()));

        var result = service.transferirPorN8n(3L, new TransferirAtendimentoRequest(10L, "N8N"), 1L);

        assertTrue(result.jaEstavaTransferido());
        assertEquals(2, result.eventosCriados());
        verify(mensagemRepository, times(2)).save(any(Mensagem.class));
        verify(transferenciaRepository, never()).save(any());
    }

    @Test
    void should_broadcast_n8n_transfer_only_after_commit() {
        Recepcionista destinatario = new Recepcionista();
        destinatario.setId(10L);
        destinatario.setClinica(clinica);
        destinatario.setPerfil("RECEPCIONISTA");
        when(atendimentoRepository.findByIdAndClinicaIdForUpdate(3L, 1L)).thenReturn(Optional.of(atendimento));
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(destinatario));
        when(atendimentoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferenciaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationService.notificarTransferenciaIa(
                eq(atendimento), isNull(), anyString(), eq(destinatario), any()))
                .thenReturn(new AtendimentoNotificationService.TransferenciaNotificacaoResultado(1, List.of(10L)));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.transferirPorN8n(3L, new TransferirAtendimentoRequest(10L, "N8N"), 1L);
            verify(broadcastService, never()).broadcastTransferenciaParaDestinatarios(
                    any(), anyLong(), anyLong(), anyLong(), anyString(), anyLong(), anyString(), anyString());
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(broadcastService).broadcastTransferenciaParaDestinatarios(
                any(), eq(10L), eq(3L), eq(0L), eq("IA"), eq(2L), anyString(), anyString());
    }

    @Test
    void should_change_n8n_human_transfer_destination_when_requested() {
        Recepcionista anterior = new Recepcionista();
        anterior.setId(9L);
        anterior.setClinica(clinica);
        anterior.setPerfil("RECEPCIONISTA");
        Recepcionista destino = new Recepcionista();
        destino.setId(10L);
        destino.setClinica(clinica);
        destino.setPerfil("RECEPCIONISTA");
        atendimento.setTratadoPorIa(false);
        atendimento.setAtendentePrincipal(anterior);
        atendimento.setHumanoDesde(OffsetDateTime.parse("2026-07-03T12:00:00Z"));
        when(atendimentoRepository.findByIdAndClinicaIdForUpdate(3L, 1L)).thenReturn(Optional.of(atendimento));
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(destino));
        when(atendimentoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferenciaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mensagemRepository.existsSystemEvent(3L, 1L, "AI_HANDOFF_ENDED")).thenReturn(true);
        when(mensagemRepository.existsSystemEvent(3L, 1L, "HUMAN_HANDOFF_START")).thenReturn(true);
        when(notificationService.notificarTransferenciaIa(
                eq(atendimento), isNull(), anyString(), eq(destino), any()))
                .thenReturn(new AtendimentoNotificationService.TransferenciaNotificacaoResultado(1, List.of(10L)));

        var result = service.transferirPorN8n(3L, new TransferirAtendimentoRequest(10L, "Nova fila"), 1L);

        assertTrue(result.jaEstavaTransferido());
        assertTrue(result.destinatarioAlterado());
        assertEquals(10L, atendimento.getAtendentePrincipal().getId());
        verify(transferenciaRepository).save(any(TransferenciaAtendimento.class));
    }

    @Test
    void should_reject_n8n_transfer_destination_from_another_clinic() {
        Clinica outraClinica = new Clinica();
        outraClinica.setId(2L);
        Recepcionista destinatario = new Recepcionista();
        destinatario.setId(10L);
        destinatario.setClinica(outraClinica);
        destinatario.setPerfil("RECEPCIONISTA");
        when(atendimentoRepository.findByIdAndClinicaIdForUpdate(3L, 1L)).thenReturn(Optional.of(atendimento));
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(destinatario));

        NotFoundException error = assertThrows(NotFoundException.class,
                () -> service.transferirPorN8n(3L, new TransferirAtendimentoRequest(10L, "N8N"), 1L));

        assertTrue(error.getMessage().contains("não encontrado"));
        verify(transferenciaRepository, never()).save(any());
    }

    @Test
    void should_reject_inactive_n8n_transfer_destination() {
        Recepcionista destinatario = new Recepcionista();
        destinatario.setId(10L);
        destinatario.setClinica(clinica);
        destinatario.setPerfil("RECEPCIONISTA");
        destinatario.setAtivo(false);
        when(atendimentoRepository.findByIdAndClinicaIdForUpdate(3L, 1L)).thenReturn(Optional.of(atendimento));
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(destinatario));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.transferirPorN8n(3L, new TransferirAtendimentoRequest(10L, "N8N"), 1L));

        assertTrue(error.getMessage().contains("inativo"));
    }

    @Test
    void should_reject_medico_as_n8n_transfer_destination() {
        Medico destinatario = new Medico();
        destinatario.setId(10L);
        destinatario.setClinica(clinica);
        destinatario.setPerfil("MEDICO");
        when(atendimentoRepository.findByIdAndClinicaIdForUpdate(3L, 1L)).thenReturn(Optional.of(atendimento));
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(destinatario));

        assertThrows(IllegalStateException.class,
                () -> service.transferirPorN8n(3L, new TransferirAtendimentoRequest(10L, "N8N"), 1L));
    }

    @Test
    void should_reject_closed_n8n_transfer_without_side_effects() {
        atendimento.setStatus("CANCELADO");
        when(atendimentoRepository.findByIdAndClinicaIdForUpdate(3L, 1L))
                .thenReturn(Optional.of(atendimento));

        assertThrows(IllegalStateException.class, () -> service.transferirPorN8n(
                3L,
                new TransferirAtendimentoRequest(10L, "Transferido"),
                1L
        ));

        verify(atendimentoRepository, never()).save(any());
        verify(transferenciaRepository, never()).save(any());
        verify(mensagemRepository, never()).save(any());
        verify(notificationService, never()).notificarTransferenciaIa(any(), any(), anyString());
    }

    @Test
    void should_return_human_atendimento_to_ai_mode() {
        Recepcionista atendente = new Recepcionista();
        atendente.setId(10L);
        atendimento.setAtendentePrincipal(atendente);
        atendimento.setTratadoPorIa(false);

        when(atendimentoRepository.findByIdAndClinicaIdForUpdate(3L, 1L))
                .thenReturn(Optional.of(atendimento));
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

    private Recepcionista atendente(Long id) {
        Recepcionista atendente = new Recepcionista();
        atendente.setId(id);
        atendente.setClinica(clinica);
        atendente.setPerfil("RECEPCIONISTA");
        atendente.setAtivo(true);
        return atendente;
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
        when(atendimentoRepository.findByIdAndClinicaIdForUpdate(3L, 1L))
                .thenReturn(Optional.of(atendimento));
        when(atendimentoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        int total = service.retornarHumanosExpiradosParaIa(now);

        assertEquals(1, total);
        assertTrue(atendimento.getTratadoPorIa());
        assertNull(atendimento.getAtendentePrincipal());
        assertNull(atendimento.getHumanoDesde());
        verify(atendimentoRepository).save(atendimento);
    }

    @Test
    void should_keep_ai_mode_idempotent_without_saving_or_broadcasting_again() {
        atendimento.setTratadoPorIa(true);
        atendimento.setAtendentePrincipal(null);
        atendimento.setHumanoDesde(null);
        when(atendimentoRepository.findByIdAndClinicaIdForUpdate(3L, 1L))
                .thenReturn(Optional.of(atendimento));

        var resultado = service.ativarModoIa(
                3L, 1L, AtendimentoService.OrigemAtivacaoModoIa.COMANDO_RESET
        );

        assertFalse(resultado.alterado());
        assertTrue(resultado.estadoAnteriorIa());
        assertFalse(resultado.atendimentoEncerrado());
        verify(atendimentoRepository, never()).save(any());
        verify(broadcastService, never()).broadcastAtendimentoModoIa(anyLong(), anyLong());
    }

    @Test
    void should_not_reopen_closed_atendimento_for_reset_command() {
        atendimento.setStatus("ENCERRADO");
        atendimento.setTratadoPorIa(false);
        when(atendimentoRepository.findByIdAndClinicaIdForUpdate(3L, 1L))
                .thenReturn(Optional.of(atendimento));

        var resultado = service.ativarModoIa(
                3L, 1L, AtendimentoService.OrigemAtivacaoModoIa.COMANDO_RESET
        );

        assertTrue(resultado.atendimentoEncerrado());
        assertEquals("ENCERRADO", atendimento.getStatus());
        assertFalse(atendimento.getTratadoPorIa());
        verify(atendimentoRepository, never()).save(any());
    }

    @Test
    void should_broadcast_ai_mode_only_after_transaction_commit() {
        atendimento.setTratadoPorIa(false);
        when(atendimentoRepository.findByIdAndClinicaIdForUpdate(3L, 1L))
                .thenReturn(Optional.of(atendimento));
        when(atendimentoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.ativarModoIa(3L, 1L, AtendimentoService.OrigemAtivacaoModoIa.COMANDO_RESET);

            verify(broadcastService, never()).broadcastAtendimentoModoIa(anyLong(), anyLong());
            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, synchronizations.size());
            synchronizations.getFirst().afterCommit();
            verify(broadcastService).broadcastAtendimentoModoIa(1L, 3L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
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

    private void stubList(List<Atendimento> atendimentos) {
        when(atendimentoRepository.findByClinica(
                anyLong(), nullable(String.class), nullable(Boolean.class), nullable(Long.class),
                anyBoolean(), anyBoolean(), anyBoolean(), anyInt(),
                anyString(), anyString(), anyString(), anyString(), nullable(Long.class),
                anyString(), anyString(), anyString(), anyString(), anyString(), any()
        )).thenReturn(new PageImpl<>(atendimentos));
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
