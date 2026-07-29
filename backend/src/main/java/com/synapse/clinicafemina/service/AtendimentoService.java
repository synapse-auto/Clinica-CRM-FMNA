package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.Mensagem;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.domain.Tag;
import com.synapse.clinicafemina.domain.TransferenciaAtendimento;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.AtendenteOptionDTO;
import com.synapse.clinicafemina.dto.AtendimentoDetalheDTO;
import com.synapse.clinicafemina.dto.AtendimentoResumoDTO;
import com.synapse.clinicafemina.dto.TransferirAtendimentoRequest;
import com.synapse.clinicafemina.dto.WhatsappCapabilitiesDTO;
import com.synapse.clinicafemina.dto.n8n.N8nTransferirProximoHumanoRequest;
import com.synapse.clinicafemina.dto.operacional.TagResponse;
import com.synapse.clinicafemina.exception.IdempotencyConflictException;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.integration.WhatsappOutboundClient;
import com.synapse.clinicafemina.integration.whatsapp.WhatsappProviderType;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.AtendimentoTagRepository;
import com.synapse.clinicafemina.repository.ClinicaRepository;
import com.synapse.clinicafemina.repository.MensagemRepository;
import com.synapse.clinicafemina.repository.PacienteTagRepository;
import com.synapse.clinicafemina.repository.TransferenciaAtendimentoRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import com.synapse.clinicafemina.service.search.SmartSearchCriteria;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AtendimentoService {

    private static final Set<String> PERFIS_ATENDENTES = Set.of("GESTOR", "RECEPCIONISTA");
    private static final String AI_HANDOFF_ENDED = "AI_HANDOFF_ENDED";
    private static final String HUMAN_HANDOFF_START = "HUMAN_HANDOFF_START";
    private static final String AI_HANDOFF_SUMMARY = "AI_HANDOFF_SUMMARY";
    private static final String ORIGEM_TRANSFERENCIA_MANUAL = "MANUAL";
    private static final String ORIGEM_TRANSFERENCIA_N8N = "N8N";
    private static final String ORIGEM_TRANSFERENCIA_N8N_RODIZIO = "N8N_RODIZIO";

    public record TransferenciaHumanoResultado(
            AtendimentoDetalheDTO atendimento,
            boolean transferido,
            boolean jaEstavaTransferido,
            boolean destinatarioAlterado,
            int eventosCriados,
            boolean resumoRegistrado,
            int notificacoesCriadas,
            OffsetDateTime transferidoEm
    ) {}

    public record TransferenciaRodizioHumanoResultado(
            TransferenciaHumanoResultado transferencia,
            Long novoAtendenteId,
            int posicaoSelecionada
    ) {}

    private record ResumoTransferencia(Mensagem mensagem, boolean registrado) {}

    private final AtendimentoRepository atendimentoRepository;
    private final ClinicaRepository clinicaRepository;
    private final MensagemRepository mensagemRepository;
    private final UsuarioRepository usuarioRepository;
    private final TransferenciaAtendimentoRepository transferenciaRepository;
    private final AtendimentoNotificationService notificationService;
    private final RealtimeBroadcastService broadcastService;
    private final AtendimentoTagRepository atendimentoTagRepository;
    private final PacienteTagRepository pacienteTagRepository;
    private final WhatsappWindowService whatsappWindowService;
    private final WhatsappOutboundClient whatsappOutboundClient;

    @Transactional(readOnly = true)
    public Page<AtendimentoResumoDTO> listar(
            Long clinicaId,
            String status,
            String tipo,
            String filtro,
            String busca,
            Long usuarioAtualId,
            Pageable pageable
    ) {
        Boolean tratadoPorIa = switch (tipo == null ? "TODOS" : tipo.toUpperCase()) {
            case "IA" -> true;
            case "HUMANO" -> false;
            default -> null;
        };
        String filtroNormalizado = filtro == null ? "TODOS" : filtro.toUpperCase();
        String statusEfetivo = "FINALIZADOS".equals(filtroNormalizado) ? "ENCERRADO" : normalizar(status);

        SmartSearchCriteria search = SmartSearchCriteria.from(busca);
        Page<Atendimento> atendimentos = atendimentoRepository.findByClinica(
                clinicaId,
                statusEfetivo,
                tratadoPorIa,
                "MEUS".equals(filtroNormalizado) ? usuarioAtualId : null,
                "NAO_LIDOS".equals(filtroNormalizado),
                "AGUARDANDO".equals(filtroNormalizado),
                "REVISAO".equals(filtroNormalizado),
                search.mode(),
                search.externalExact(),
                search.digits(),
                search.localPhoneDigits(),
                search.phoneWithCountryCode(),
                search.exactId(),
                search.token(0),
                search.token(1),
                search.token(2),
                search.token(3),
                search.token(4),
                pageable
        );
        Map<Long, String> previasPorAtendimento = ultimasPrevias(atendimentos.getContent());
        Map<Long, List<TagResponse>> tagsPorAtendimento =
                tagsDosAtendimentos(atendimentos.getContent(), clinicaId);
        return atendimentos.map(atendimento -> toResumoDTO(
                atendimento,
                previasPorAtendimento,
                tagsPorAtendimento
        ));
    }

    @Transactional(readOnly = true)
    public AtendimentoDetalheDTO buscarPorId(Long id, Long clinicaId) {
        return toDetalheDTO(buscarOuFalhar(id, clinicaId));
    }

    @Transactional
    public AtendimentoDetalheDTO transferir(
            Long id,
            TransferirAtendimentoRequest request,
            Long clinicaId,
            Long usuarioResponsavelId
    ) {
        Atendimento atendimento = buscarOuFalhar(id, clinicaId);
        if ("ENCERRADO".equals(atendimento.getStatus())) {
            throw new IllegalStateException("Não é possível transferir um atendimento encerrado");
        }

        Usuario novoAtendente = buscarAtendente(request.novoAtendenteId(), clinicaId);
        Usuario responsavel = buscarUsuario(usuarioResponsavelId, clinicaId);
        Usuario antigoAtendente = atendimento.getAtendentePrincipal();

        atendimento.setAtendentePrincipal(novoAtendente);
        atendimento.setTratadoPorIa(false);
        atendimento.setHumanoDesde(OffsetDateTime.now());
        atendimento.setStatus("ATIVO");
        atendimentoRepository.save(atendimento);
        transferenciaRepository.save(criarTransferencia(
                atendimento, antigoAtendente, novoAtendente, responsavel, request.motivo(),
                ORIGEM_TRANSFERENCIA_MANUAL, null, null
        ));
        notificationService.notificarAtribuicao(atendimento, novoAtendente);

        broadcastService.broadcastTransferencia(
                novoAtendente.getId(),
                atendimento.getId(),
                antigoAtendente != null ? antigoAtendente.getId() : 0L,
                antigoAtendente != null ? antigoAtendente.getNome() : "IA",
                atendimento.getPaciente().getId(),
                atendimento.getPaciente().getNomeBusca(),
                request.motivo()
        );
        log.info("Atendimento {} atribuído ao usuário {}", id, novoAtendente.getId());
        return toDetalheDTO(atendimento);
    }

    @Transactional
    public TransferenciaHumanoResultado transferirPorN8n(
            Long id,
            TransferirAtendimentoRequest request,
            Long clinicaId
    ) {
        return transferirPorN8n(id, request, clinicaId, ORIGEM_TRANSFERENCIA_N8N, null, null);
    }

    @Transactional
    public TransferenciaRodizioHumanoResultado transferirProximoPorN8n(
            Long id,
            N8nTransferirProximoHumanoRequest request,
            String idempotencyKey,
            Long clinicaId
    ) {
        List<Long> atendentesIds = request.idsOrdenados();
        String chaveIdempotencia = request.validarIdempotencyKey(idempotencyKey);
        String fingerprint = request.fingerprint(id);
        clinicaRepository.findByIdForUpdate(clinicaId)
                .orElseThrow(() -> new NotFoundException("Clínica não encontrada"));
        Optional<TransferenciaAtendimento> transferenciaExistente =
                transferenciaRepository.findByIdempotencyKey(chaveIdempotencia);
        if (transferenciaExistente.isPresent()) {
            TransferenciaAtendimento existente = transferenciaExistente.get();
            validarRetryRodizio(existente, id, clinicaId, fingerprint);
            return new TransferenciaRodizioHumanoResultado(
                    resultadoIdempotente(existente),
                    existente.getParaUsuario().getId(),
                    atendentesIds.indexOf(existente.getParaUsuario().getId())
            );
        }
        atendentesIds.forEach(atendenteId -> buscarAtendente(atendenteId, clinicaId));

        Long ultimoAtendenteId = transferenciaRepository.findDestinatariosPorOrigem(
                        clinicaId,
                        ORIGEM_TRANSFERENCIA_N8N_RODIZIO,
                        atendentesIds,
                        PageRequest.of(0, 1)
                )
                .stream()
                .findFirst()
                .orElse(null);
        int posicaoSelecionada = proximaPosicao(atendentesIds, ultimoAtendenteId);
        Long novoAtendenteId = atendentesIds.get(posicaoSelecionada);
        TransferirAtendimentoRequest transferencia = request.paraTransferencia(novoAtendenteId);
        TransferenciaHumanoResultado resultado = transferirPorN8n(
                id,
                transferencia,
                clinicaId,
                ORIGEM_TRANSFERENCIA_N8N_RODIZIO,
                chaveIdempotencia,
                fingerprint
        );
        return new TransferenciaRodizioHumanoResultado(resultado, novoAtendenteId, posicaoSelecionada);
    }

    private TransferenciaHumanoResultado transferirPorN8n(
            Long id,
            TransferirAtendimentoRequest request,
            Long clinicaId,
            String origemTransferencia,
            String idempotencyKey,
            String idempotencyFingerprint
    ) {
        Atendimento atendimento = atendimentoRepository.findByIdAndClinicaIdForUpdate(id, clinicaId)
                .orElseThrow(() -> new NotFoundException("Atendimento não encontrado"));
        validarAtendimentoTransferivel(atendimento);

        Usuario novoAtendente = buscarAtendente(request.novoAtendenteId(), clinicaId);
        Usuario responsavel = novoAtendente;
        Usuario antigoAtendente = atendimento.getAtendentePrincipal();
        boolean jaEstavaTransferido = Boolean.FALSE.equals(atendimento.getTratadoPorIa());
        boolean destinatarioAlterado = antigoAtendente == null
                || !antigoAtendente.getId().equals(novoAtendente.getId());
        OffsetDateTime transferidoEm = jaEstavaTransferido && !destinatarioAlterado
                && atendimento.getHumanoDesde() != null
                ? atendimento.getHumanoDesde()
                : OffsetDateTime.now();
        boolean transferenciaNova = !jaEstavaTransferido || destinatarioAlterado;

        if (transferenciaNova) {
            atendimento.setAtendentePrincipal(novoAtendente);
            atendimento.setTratadoPorIa(false);
            atendimento.setHumanoDesde(transferidoEm);
            atendimento.setStatus("ATIVO");
            atendimentoRepository.save(atendimento);
        }
        String motivoTransferencia = request.motivoTransferencia() != null
                ? request.motivoTransferencia()
                : request.motivo();
        boolean registrarTransferencia = transferenciaNova || idempotencyKey != null;
        if (registrarTransferencia) {
            transferenciaRepository.save(criarTransferencia(
                    atendimento,
                    antigoAtendente,
                    novoAtendente,
                    responsavel,
                    sanitizarTextoCurto(motivoTransferencia),
                    origemTransferencia,
                    idempotencyKey,
                    idempotencyFingerprint
            ));
        }

        int eventosCriados = registrarEventosTransferencia(atendimento, transferidoEm);
        ResumoTransferencia resumo = registrarResumoTransferencia(
                atendimento,
                request.resumoTransferencia(),
                transferidoEm.plusNanos(2)
        );
        AtendimentoNotificationService.TransferenciaNotificacaoResultado notificacoes = notificationService.notificarTransferenciaIa(
                atendimento,
                resumo.mensagem(),
                motivoTransferencia,
                novoAtendente,
                transferidoEm
        );
        boolean houveReparo = eventosCriados > 0 || resumo.registrado() || notificacoes.criadas() > 0;
        if (transferenciaNova || houveReparo) {
            LinkedHashSet<Long> destinatarios = new LinkedHashSet<>(notificacoes.destinatariosCriados());
            destinatarios.add(novoAtendente.getId());
            agendarBroadcastTransferenciaAposCommit(
                    destinatarios,
                    atendimento,
                    antigoAtendente,
                    novoAtendente,
                    motivoTransferencia
            );
        }
        log.info("Transferência N8N para humano concluída. atendimentoId={} resumoRegistrado={} notificacoesCriadas={}",
                id, resumo.registrado(), notificacoes.criadas());
        return new TransferenciaHumanoResultado(
                toDetalheDTO(atendimento),
                true,
                jaEstavaTransferido,
                destinatarioAlterado,
                eventosCriados,
                resumo.registrado(),
                notificacoes.criadas(),
                transferidoEm
        );
    }

    @Transactional
    public AtendimentoDetalheDTO assumir(Long id, Long clinicaId, Long usuarioId) {
        return transferir(
                id,
                new TransferirAtendimentoRequest(usuarioId, "Atendimento assumido"),
                clinicaId,
                usuarioId
        );
    }

    @Transactional
    public AtendimentoDetalheDTO ativarModoIa(Long id, Long clinicaId) {
        Atendimento atendimento = buscarOuFalhar(id, clinicaId);
        if ("ENCERRADO".equals(atendimento.getStatus())) {
            throw new IllegalStateException("Nao e possivel ativar IA em um atendimento encerrado");
        }
        atendimento.setAtendentePrincipal(null);
        atendimento.setTratadoPorIa(true);
        atendimento.setHumanoDesde(null);
        atendimento.setStatus("ATIVO");
        Atendimento salvo = atendimentoRepository.save(atendimento);
        log.info("Atendimento {} retornado para IA", id);
        return toDetalheDTO(salvo);
    }

    @Transactional
    public int retornarHumanosExpiradosParaIa(OffsetDateTime agora) {
        OffsetDateTime limite = agora.minusHours(24);
        List<Atendimento> expirados = atendimentoRepository.findHumanosParaRetornoIa(limite);
        for (Atendimento atendimento : expirados) {
            atendimento.setAtendentePrincipal(null);
            atendimento.setTratadoPorIa(true);
            atendimento.setHumanoDesde(null);
            atendimentoRepository.save(atendimento);
            log.info("Atendimento {} retornado automaticamente para IA apos 24h em modo humano",
                    atendimento.getId());
        }
        return expirados.size();
    }

    @Transactional
    public AtendimentoDetalheDTO encerrar(Long id, Long clinicaId, String motivo) {
        Atendimento atendimento = buscarOuFalhar(id, clinicaId);
        if ("ENCERRADO".equals(atendimento.getStatus())) {
            throw new IllegalStateException("Atendimento já encerrado");
        }
        atendimento.setStatus("ENCERRADO");
        atendimento.setDataEncerramento(OffsetDateTime.now());
        atendimento.setMotivoEncerramento(motivo);
        return toDetalheDTO(atendimentoRepository.save(atendimento));
    }

    @Transactional
    public void marcarComoLido(Long id, Long clinicaId) {
        Atendimento atendimento = buscarOuFalhar(id, clinicaId);
        mensagemRepository.marcarComoLidas(id, clinicaId, OffsetDateTime.now());
        atendimento.setNaoLidas(0);
        atendimentoRepository.save(atendimento);
    }

    @Transactional(readOnly = true)
    public List<AtendenteOptionDTO> listarAtendentes(Long clinicaId) {
        return usuarioRepository.findAtendentesVisiveisByClinicaId(clinicaId)
                .stream()
                .map(usuario -> new AtendenteOptionDTO(
                        usuario.getId(), usuario.getNome(), usuario.getPerfil()
                ))
                .toList();
    }

    private Atendimento buscarOuFalhar(Long id, Long clinicaId) {
        return atendimentoRepository.findByIdAndClinicaId(id, clinicaId)
                .orElseThrow(() -> new NotFoundException("Atendimento não encontrado"));
    }

    private void validarAtendimentoTransferivel(Atendimento atendimento) {
        String status = atendimento.getStatus();
        if ("ENCERRADO".equalsIgnoreCase(status)
                || "CANCELADO".equalsIgnoreCase(status)
                || "FINALIZADO".equalsIgnoreCase(status)
                || atendimento.getDataEncerramento() != null) {
            throw new IllegalStateException("Não é possível transferir um atendimento encerrado ou cancelado");
        }
    }

    private int registrarEventosTransferencia(Atendimento atendimento, OffsetDateTime transferidoEm) {
        Long atendimentoId = atendimento.getId();
        Long clinicaId = atendimento.getClinica().getId();
        int criados = registrarEventoSistemaSeAusente(
                atendimento,
                AI_HANDOFF_ENDED,
                "Fim das mensagens com a IA",
                transferidoEm
        );
        criados += registrarEventoSistemaSeAusente(
                atendimento,
                HUMAN_HANDOFF_START,
                "Atendimento #" + atendimentoId + " transferido para humano",
                transferidoEm.plusNanos(1)
        );
        return criados;
    }

    private int registrarEventoSistemaSeAusente(
            Atendimento atendimento,
            String tipoMedia,
            String conteudo,
            OffsetDateTime dataHora
    ) {
        Long atendimentoId = atendimento.getId();
        Long clinicaId = atendimento.getClinica().getId();
        if (mensagemRepository.existsSystemEvent(atendimentoId, clinicaId, tipoMedia)) {
            return 0;
        }
        Mensagem evento = new Mensagem();
        evento.setAtendimento(atendimento);
        evento.setDirecao("SISTEMA");
        evento.setRemetente("SISTEMA");
        evento.setTipoMedia(tipoMedia);
        evento.setConteudo(conteudo);
        evento.setConteudoPrevia(limitarPrevia(conteudo));
        evento.setWhatsappStatus("INTERNO");
        evento.setDataHora(dataHora);
        mensagemRepository.save(evento);
        return 1;
    }

    private void agendarBroadcastTransferenciaAposCommit(
            Set<Long> destinatarios,
            Atendimento atendimento,
            Usuario antigoAtendente,
            Usuario novoAtendente,
            String motivo
    ) {
        Long atendimentoId = atendimento.getId();
        Long pacienteId = atendimento.getPaciente().getId();
        String pacienteNome = atendimento.getPaciente().getNomeBusca();
        Long antigoAtendenteId = antigoAtendente != null ? antigoAtendente.getId() : 0L;
        String antigoAtendenteNome = antigoAtendente != null ? antigoAtendente.getNome() : "IA";
        Runnable broadcast = () -> broadcastService.broadcastTransferenciaParaDestinatarios(
                destinatarios,
                novoAtendente.getId(),
                atendimentoId,
                antigoAtendenteId,
                antigoAtendenteNome,
                pacienteId,
                pacienteNome,
                sanitizarTextoCurto(motivo)
        );
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.warn("Broadcast de transferência N8N não agendado sem transação sincronizada. atendimentoId={}", atendimentoId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                broadcast.run();
            }
        });
    }

    private ResumoTransferencia registrarResumoTransferencia(
            Atendimento atendimento,
            String resumo,
            OffsetDateTime dataHora
    ) {
        if (resumo == null || resumo.isBlank()) {
            return new ResumoTransferencia(null, false);
        }
        Long atendimentoId = atendimento.getId();
        Long clinicaId = atendimento.getClinica().getId();
        Optional<Mensagem> existente = mensagemRepository.findLatestAiHandoffSummary(atendimentoId, clinicaId);
        if (existente.isPresent()) {
            return new ResumoTransferencia(existente.get(), false);
        }
        String conteudo = sanitizarTextoCurto(resumo);
        if (conteudo.isBlank()) {
            return new ResumoTransferencia(null, false);
        }
        Mensagem mensagem = new Mensagem();
        mensagem.setAtendimento(atendimento);
        mensagem.setDirecao("SISTEMA");
        mensagem.setRemetente("IA");
        mensagem.setTipoMedia(AI_HANDOFF_SUMMARY);
        mensagem.setConteudo(conteudo);
        mensagem.setConteudoPrevia(limitarPrevia(conteudo));
        mensagem.setWhatsappStatus("INTERNO");
        mensagem.setDataHora(dataHora);
        return new ResumoTransferencia(mensagemRepository.save(mensagem), true);
    }

    private String limitarPrevia(String conteudo) {
        if (conteudo.length() <= 60) {
            return conteudo;
        }
        return conteudo.substring(0, 57) + "...";
    }

    private String sanitizarTextoCurto(String valor) {
        return valor == null ? "" : valor.replaceAll("<[^>]*>", "").trim();
    }

    private Usuario buscarUsuario(Long usuarioId, Long clinicaId) {
        return usuarioRepository.findAtivoByIdAndClinicaId(usuarioId, clinicaId)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));
    }

    private Usuario buscarAtendente(Long usuarioId, Long clinicaId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new NotFoundException("Usuário destinatário não encontrado"));
        if (usuario.getClinica() == null || !clinicaId.equals(usuario.getClinica().getId())) {
            throw new NotFoundException("Usuário destinatário não encontrado");
        }
        if (!Boolean.TRUE.equals(usuario.getAtivo()) || usuario.getDeletadoEm() != null) {
            throw new IllegalStateException("Usuário destinatário está inativo ou excluído");
        }
        if (!PERFIS_ATENDENTES.contains(usuario.getPerfil())) {
            throw new IllegalStateException("O usuário selecionado não pode receber atendimentos");
        }
        return usuario;
    }

    private TransferenciaAtendimento criarTransferencia(
            Atendimento atendimento,
            Usuario antigoAtendente,
            Usuario novoAtendente,
            Usuario responsavel,
            String motivo,
            String origem,
            String idempotencyKey,
            String idempotencyFingerprint
    ) {
        TransferenciaAtendimento transferencia = new TransferenciaAtendimento();
        transferencia.setAtendimento(atendimento);
        transferencia.setDeUsuario(antigoAtendente);
        transferencia.setParaUsuario(novoAtendente);
        transferencia.setTransferidoPor(responsavel);
        transferencia.setMotivo(motivo);
        transferencia.setOrigem(origem);
        transferencia.setIdempotencyKey(idempotencyKey);
        transferencia.setIdempotencyFingerprint(idempotencyFingerprint);
        return transferencia;
    }

    private void validarRetryRodizio(
            TransferenciaAtendimento existente,
            Long atendimentoId,
            Long clinicaId,
            String fingerprint
    ) {
        Atendimento atendimentoExistente = existente.getAtendimento();
        boolean mesmaOperacao = atendimentoExistente != null
                && atendimentoId.equals(atendimentoExistente.getId())
                && atendimentoExistente.getClinica() != null
                && clinicaId.equals(atendimentoExistente.getClinica().getId())
                && ORIGEM_TRANSFERENCIA_N8N_RODIZIO.equals(existente.getOrigem())
                && fingerprint.equals(existente.getIdempotencyFingerprint());
        if (!mesmaOperacao) {
            throw new IdempotencyConflictException(
                    "Esta Idempotency-Key já foi usada em outra transferência ou com dados diferentes."
            );
        }
    }

    private TransferenciaHumanoResultado resultadoIdempotente(TransferenciaAtendimento transferencia) {
        Atendimento atendimento = transferencia.getAtendimento();
        return new TransferenciaHumanoResultado(
                toDetalheDTO(atendimento),
                true,
                true,
                false,
                0,
                false,
                0,
                transferencia.getTransferidoEm()
        );
    }

    private int proximaPosicao(List<Long> atendentesIds, Long ultimoAtendenteId) {
        if (ultimoAtendenteId == null) {
            return 0;
        }
        int posicaoAnterior = atendentesIds.indexOf(ultimoAtendenteId);
        return posicaoAnterior < 0 || posicaoAnterior == atendentesIds.size() - 1
                ? 0
                : posicaoAnterior + 1;
    }

    private Map<Long, String> ultimasPrevias(List<Atendimento> atendimentos) {
        if (atendimentos.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = atendimentos.stream()
                .map(Atendimento::getId)
                .toList();
        Map<Long, String> previas = new HashMap<>();
        for (MensagemRepository.UltimaPreviaProjection previa : mensagemRepository.findUltimasPreviasByAtendimentoIds(ids)) {
            previas.put(previa.getAtendimentoId(), previa.getConteudoPrevia());
        }
        return previas;
    }

    private AtendimentoResumoDTO toResumoDTO(
            Atendimento atendimento,
            Map<Long, String> previasPorAtendimento,
            Map<Long, List<TagResponse>> tagsPorAtendimento
    ) {
        Paciente paciente = atendimento.getPaciente();
        Usuario atendente = atendimento.getAtendentePrincipal();
        String previa = previasPorAtendimento.getOrDefault(atendimento.getId(), "");
        return new AtendimentoResumoDTO(
                atendimento.getId(),
                atendimento.getStatus(),
                atendimento.getTratadoPorIa(),
                atendimento.getUltimaMensagemEm(),
                atendimento.getNaoLidas(),
                previa,
                paciente.getRequerRevisao(),
                paciente.getConvenioStatus(),
                new AtendimentoResumoDTO.PacienteResumoDTO(
                        paciente.getId(), paciente.getNomeBusca(), paciente.getTelefoneNormalizado(), paciente.getFotoUrl()
                ),
                atendente != null
                        ? new AtendimentoResumoDTO.AtendenteDTO(atendente.getId(), atendente.getNome())
                        : null,
                tagsPorAtendimento.getOrDefault(atendimento.getId(), List.of())
        );
    }

    private Map<Long, List<TagResponse>> tagsDosAtendimentos(List<Atendimento> atendimentos, Long clinicaId) {
        if (atendimentos.isEmpty()) {
            return Map.of();
        }
        List<Long> atendimentoIds = atendimentos.stream()
                .map(Atendimento::getId)
                .toList();
        Map<Long, LinkedHashMap<Long, TagResponse>> agrupadas = new HashMap<>();
        for (Object[] linha : atendimentoTagRepository.findTagsByAtendimentoIdsAndClinicaId(
                atendimentoIds, clinicaId
        )) {
            Long atendimentoId = (Long) linha[0];
            Tag tag = (Tag) linha[1];
            adicionarTag(agrupadas, atendimentoId, tag);
        }

        Map<Long, List<Long>> atendimentosPorPaciente = new HashMap<>();
        for (Atendimento atendimento : atendimentos) {
            Long pacienteId = atendimento.getPaciente().getId();
            atendimentosPorPaciente
                    .computeIfAbsent(pacienteId, ignored -> new ArrayList<>())
                    .add(atendimento.getId());
        }
        for (Object[] linha : pacienteTagRepository.findTagsByPacienteIdsAndClinicaId(
                atendimentosPorPaciente.keySet(), clinicaId
        )) {
            Long pacienteId = (Long) linha[0];
            Tag tag = (Tag) linha[1];
            for (Long atendimentoId : atendimentosPorPaciente.getOrDefault(pacienteId, List.of())) {
                adicionarTag(agrupadas, atendimentoId, tag);
            }
        }

        Map<Long, List<TagResponse>> resultado = new HashMap<>();
        agrupadas.forEach((atendimentoId, tags) ->
                resultado.put(atendimentoId, new ArrayList<>(tags.values()))
        );
        return resultado;
    }

    private void adicionarTag(
            Map<Long, LinkedHashMap<Long, TagResponse>> agrupadas,
            Long atendimentoId,
            Tag tag
    ) {
        agrupadas
                .computeIfAbsent(atendimentoId, ignored -> new LinkedHashMap<>())
                .putIfAbsent(tag.getId(), toTagResponse(tag));
    }

    private TagResponse toTagResponse(Tag tag) {
        return new TagResponse(
                tag.getId(),
                tag.getNome(),
                tag.getCor(),
                tag.getDescricao(),
                Boolean.TRUE.equals(tag.getAtivo()),
                tag.getCriadoEm(),
                tag.getAtualizadoEm()
        );
    }

    private AtendimentoDetalheDTO toDetalheDTO(Atendimento atendimento) {
        Paciente paciente = atendimento.getPaciente();
        Usuario atendente = atendimento.getAtendentePrincipal();
        Usuario convenioResponsavel = paciente.getConvenioRevisadoPor();
        WhatsappWindowService.WindowState janela = whatsappWindowService.avaliar(
                atendimento.getId(), atendimento.getClinica().getId()
        );
        WhatsappCapabilitiesDTO capabilities = whatsappWindowService.capabilities();
        if (capabilities == null) {
            capabilities = WhatsappCapabilitiesDTO.forProvider(WhatsappProviderType.META);
        }
        return new AtendimentoDetalheDTO(
                atendimento.getId(),
                atendimento.getStatus(),
                atendimento.getTratadoPorIa(),
                atendimento.getDataInicio(),
                atendimento.getDataEncerramento(),
                atendimento.getNaoLidas(),
                new AtendimentoDetalheDTO.PacienteDetalheDTO(
                        paciente.getId(),
                        paciente.getNome(),
                        paciente.getTelefone(),
                        paciente.getEmail(),
                        paciente.getStatus(),
                        paciente.getFotoUrl(),
                        paciente.getUltimaInteracaoEm(),
                        paciente.getRequerRevisao(),
                        paciente.getConvenioStatus(),
                        paciente.getConvenioRevisadoEm(),
                        convenioResponsavel != null ? convenioResponsavel.getId() : null,
                        convenioResponsavel != null ? convenioResponsavel.getNome() : null
                ),
                atendente != null
                        ? new AtendimentoDetalheDTO.AtendenteDTO(
                                atendente.getId(), atendente.getNome(), atendente.getPerfil()
                        )
                        : null,
                janela.aberta(),
                janela.expiraEm(),
                janela.ultimaMensagemEntradaEm(),
                janela.aguardandoRespostaTemplate(),
                capabilities.supportsMessageTemplates()
                        && whatsappOutboundClient.templatesDisponiveis(),
                capabilities
        );
    }

    private String normalizar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

}
