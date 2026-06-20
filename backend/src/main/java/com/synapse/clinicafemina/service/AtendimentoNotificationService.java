package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.Mensagem;
import com.synapse.clinicafemina.domain.NotificacaoAtendimento;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.NotificacaoAtendimentoDTO;
import com.synapse.clinicafemina.dto.NotificacaoResumoDTO;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.repository.NotificacaoAtendimentoRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AtendimentoNotificationService {

    private static final String NOVA_MENSAGEM = "NOVA_MENSAGEM";
    private static final String ATENDIMENTO_ATRIBUIDO = "ATENDIMENTO_ATRIBUIDO";

    private final NotificacaoAtendimentoRepository repository;
    private final UsuarioRepository usuarioRepository;

    @Transactional
    public void notificarNovaMensagem(Atendimento atendimento, Mensagem mensagem) {
        List<Usuario> destinatarios = atendimento.getAtendentePrincipal() != null
                ? List.of(atendimento.getAtendentePrincipal())
                : usuarioRepository.findAtendentesVisiveisByClinicaId(atendimento.getClinica().getId());

        destinatarios.forEach(usuario -> criarSeInexistente(
                usuario, atendimento, mensagem, NOVA_MENSAGEM,
                "Nova mensagem recebida em um atendimento"
        ));
    }

    @Transactional
    public void notificarAtribuicao(Atendimento atendimento, Usuario destinatario) {
        repository.save(novaNotificacao(
                destinatario, atendimento, null, ATENDIMENTO_ATRIBUIDO,
                "Um atendimento foi atribuído a você"
        ));
    }

    @Transactional(readOnly = true)
    public Page<NotificacaoAtendimentoDTO> listar(Long usuarioId, boolean somenteNaoLidas, Pageable pageable) {
        return repository.listar(usuarioId, somenteNaoLidas, pageable).map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public NotificacaoResumoDTO resumo(Long usuarioId) {
        return new NotificacaoResumoDTO(repository.countByUsuarioIdAndLidaEmIsNull(usuarioId));
    }

    @Transactional
    public void marcarComoLida(Long notificacaoId, Long usuarioId) {
        NotificacaoAtendimento notificacao = repository.findByIdAndUsuarioId(notificacaoId, usuarioId)
                .orElseThrow(() -> new NotFoundException("Notificação não encontrada"));
        if (notificacao.getLidaEm() == null) {
            notificacao.setLidaEm(OffsetDateTime.now());
            repository.save(notificacao);
        }
    }

    @Transactional
    public void marcarTodasComoLidas(Long usuarioId) {
        repository.marcarTodasComoLidas(usuarioId, OffsetDateTime.now());
    }

    private void criarSeInexistente(
            Usuario usuario,
            Atendimento atendimento,
            Mensagem mensagem,
            String tipo,
            String descricao
    ) {
        if (!repository.existsByUsuarioIdAndMensagemIdAndTipo(usuario.getId(), mensagem.getId(), tipo)) {
            repository.save(novaNotificacao(usuario, atendimento, mensagem, tipo, descricao));
        }
    }

    private NotificacaoAtendimento novaNotificacao(
            Usuario usuario,
            Atendimento atendimento,
            Mensagem mensagem,
            String tipo,
            String descricao
    ) {
        NotificacaoAtendimento notificacao = new NotificacaoAtendimento();
        notificacao.setUsuario(usuario);
        notificacao.setAtendimento(atendimento);
        notificacao.setMensagem(mensagem);
        notificacao.setTipo(tipo);
        notificacao.setDescricao(descricao);
        return notificacao;
    }

    private NotificacaoAtendimentoDTO toDTO(NotificacaoAtendimento notificacao) {
        return new NotificacaoAtendimentoDTO(
                notificacao.getId(),
                notificacao.getAtendimento().getId(),
                notificacao.getTipo(),
                notificacao.getDescricao(),
                notificacao.getLidaEm() != null,
                notificacao.getCriadoEm()
        );
    }
}
