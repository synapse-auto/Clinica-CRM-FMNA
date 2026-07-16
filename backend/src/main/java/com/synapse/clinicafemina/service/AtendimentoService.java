package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.domain.Tag;
import com.synapse.clinicafemina.domain.TransferenciaAtendimento;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.AtendenteOptionDTO;
import com.synapse.clinicafemina.dto.AtendimentoDetalheDTO;
import com.synapse.clinicafemina.dto.AtendimentoResumoDTO;
import com.synapse.clinicafemina.dto.TransferirAtendimentoRequest;
import com.synapse.clinicafemina.dto.operacional.TagResponse;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.integration.WhatsappOutboundClient;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.AtendimentoTagRepository;
import com.synapse.clinicafemina.repository.MensagemRepository;
import com.synapse.clinicafemina.repository.PacienteTagRepository;
import com.synapse.clinicafemina.repository.TransferenciaAtendimentoRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AtendimentoService {

    private static final Set<String> PERFIS_ATENDENTES = Set.of("GESTOR", "RECEPCIONISTA");

    private final AtendimentoRepository atendimentoRepository;
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

        Page<Atendimento> atendimentos = atendimentoRepository.findByClinica(
                clinicaId,
                statusEfetivo,
                tratadoPorIa,
                "MEUS".equals(filtroNormalizado) ? usuarioAtualId : null,
                "NAO_LIDOS".equals(filtroNormalizado),
                "AGUARDANDO".equals(filtroNormalizado),
                "REVISAO".equals(filtroNormalizado),
                normalizarBusca(busca),
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
                atendimento, antigoAtendente, novoAtendente, responsavel, request.motivo()
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

    private Usuario buscarUsuario(Long usuarioId, Long clinicaId) {
        return usuarioRepository.findAtivoByIdAndClinicaId(usuarioId, clinicaId)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));
    }

    private Usuario buscarAtendente(Long usuarioId, Long clinicaId) {
        Usuario usuario = buscarUsuario(usuarioId, clinicaId);
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
            String motivo
    ) {
        TransferenciaAtendimento transferencia = new TransferenciaAtendimento();
        transferencia.setAtendimento(atendimento);
        transferencia.setDeUsuario(antigoAtendente);
        transferencia.setParaUsuario(novoAtendente);
        transferencia.setTransferidoPor(responsavel);
        transferencia.setMotivo(motivo);
        return transferencia;
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
                whatsappOutboundClient.templatesDisponiveis()
        );
    }

    private String normalizar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private String normalizarBusca(String valor) {
        String normalizado = normalizar(valor);
        return normalizado == null ? "" : normalizado.toUpperCase(Locale.ROOT);
    }
}
