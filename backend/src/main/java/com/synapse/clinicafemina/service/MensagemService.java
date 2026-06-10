package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.config.RabbitMQConfig;
import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.Mensagem;
import com.synapse.clinicafemina.dto.EnviarMensagemRequest;
import com.synapse.clinicafemina.dto.MensagemDTO;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.integration.WhatsappOutboundClient;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.MensagemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class MensagemService {

    private final MensagemRepository mensagemRepository;
    private final AtendimentoRepository atendimentoRepository;
    private final WhatsappOutboundClient whatsappOutboundClient;
    private final RabbitTemplate rabbitTemplate;

    // ─── Histórico paginado ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<MensagemDTO> listarHistorico(Long atendimentoId, Pageable pageable) {
        return mensagemRepository
                .findByAtendimentoId(atendimentoId, pageable)
                .map(this::toDTO);
    }

    // ─── Envio de mensagem outbound ───────────────────────────────────────

    @Transactional
    public MensagemDTO enviar(Long atendimentoId, EnviarMensagemRequest req, Long remetenteUsuarioId) {
        Atendimento atendimento = atendimentoRepository.findById(atendimentoId)
                .orElseThrow(() -> new NotFoundException("Atendimento não encontrado: " + atendimentoId));

        if (!"ATIVO".equals(atendimento.getStatus())) {
            throw new IllegalStateException("Só é possível enviar mensagens para atendimentos ATIVOS");
        }

        // 1. Persiste a mensagem no banco (conteúdo criptografado pelo converter)
        Mensagem mensagem = new Mensagem();
        mensagem.setAtendimento(atendimento);
        mensagem.setDirecao("SAIDA");
        mensagem.setRemetente("USUARIO");
        mensagem.setTipoMedia(req.tipoMedia());
        mensagem.setConteudo(req.conteudo());
        mensagem.setConteudoPrevia(req.conteudo().length() > 60
                ? req.conteudo().substring(0, 60) + "…"
                : req.conteudo());
        mensagem.setWhatsappStatus("PENDENTE");
        mensagem.setDataHora(OffsetDateTime.now());
        mensagem = mensagemRepository.save(mensagem);

        // 2. Atualiza o atendimento
        atendimento.setUltimaMensagemEm(mensagem.getDataHora());
        atendimentoRepository.save(atendimento);

        // 3. Dispara envio assíncrono para a Meta Cloud API (com Resilience4j)
        final Long mensagemId = mensagem.getId();
        final String conteudo = req.conteudo();
        final String telefone = atendimento.getPaciente().getTelefoneNormalizado();

        try {
            String wamid = whatsappOutboundClient.enviarTexto(telefone, conteudo);
            mensagem.setWhatsappMessageId(wamid);
            mensagem.setWhatsappStatus("ENVIADA");
            mensagemRepository.save(mensagem);
            log.info("Mensagem {} enviada ao WhatsApp, wamid={}", mensagemId, wamid);
        } catch (Exception e) {
            mensagem.setMotivoFalha(e.getMessage());
            mensagem.setWhatsappStatus("FALHA");
            mensagemRepository.save(mensagem);
            log.error("Falha ao enviar mensagem {} para WhatsApp: {}", mensagemId, e.getMessage());
            // Publica na DLX para reprocessamento futuro
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_WHATSAPP_SAIDA,
                    RabbitMQConfig.ROUTING_KEY_WHATSAPP_SAIDA,
                    mensagemId);
        }

        return toDTO(mensagem);
    }

    // ─── Upload e envio de mídia outbound ─────────────────────────────────

    @Transactional
    public MensagemDTO enviarMidia(Long atendimentoId, MultipartFile arquivo, Long remetenteUsuarioId) {
        Atendimento atendimento = atendimentoRepository.findById(atendimentoId)
                .orElseThrow(() -> new NotFoundException("Atendimento não encontrado: " + atendimentoId));

        if (!"ATIVO".equals(atendimento.getStatus())) {
            throw new IllegalStateException("Só é possível enviar mensagens para atendimentos ATIVOS");
        }

        String contentType = arquivo.getContentType() != null ? arquivo.getContentType() : "application/octet-stream";
        String tipoMedia = resolverTipoMedia(contentType);
        String nomeArquivo = arquivo.getOriginalFilename() != null ? arquivo.getOriginalFilename() : "arquivo";

        // 1. Persiste a mensagem no banco antes de enviar (garante rastreabilidade)
        Mensagem mensagem = new Mensagem();
        mensagem.setAtendimento(atendimento);
        mensagem.setDirecao("SAIDA");
        mensagem.setRemetente("USUARIO");
        mensagem.setTipoMedia(tipoMedia);
        mensagem.setConteudo("[" + tipoMedia + "] " + nomeArquivo); // criptografado pelo converter
        mensagem.setConteudoPrevia("[" + tipoMedia + "] " + nomeArquivo);
        mensagem.setWhatsappStatus("PENDENTE");
        mensagem.setDataHora(OffsetDateTime.now());
        mensagem = mensagemRepository.save(mensagem);

        atendimento.setUltimaMensagemEm(mensagem.getDataHora());
        atendimentoRepository.save(atendimento);

        final Long mensagemId = mensagem.getId();
        final String telefone = atendimento.getPaciente().getTelefoneNormalizado();

        try {
            // 2. Faz upload do arquivo para a Meta e obtém o media_id
            String mediaId = whatsappOutboundClient.uploadMidia(arquivo.getResource(), contentType, nomeArquivo);

            // 3. Envia a mensagem de mídia referenciando o media_id
            String wamid = whatsappOutboundClient.enviarMidia(telefone, tipoMedia.toLowerCase(), mediaId);

            mensagem.setWhatsappMessageId(wamid);
            mensagem.setWhatsappStatus("ENVIADA");
            mensagemRepository.save(mensagem);
            log.info("Mídia {} enviada ao WhatsApp, wamid={}, mediaId={}", mensagemId, wamid, mediaId);

        } catch (Exception e) {
            mensagem.setMotivoFalha(e.getMessage());
            mensagem.setWhatsappStatus("FALHA");
            mensagemRepository.save(mensagem);
            log.error("Falha ao enviar mídia {} para WhatsApp: {}", mensagemId, e.getMessage());
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_WHATSAPP_SAIDA,
                    RabbitMQConfig.ROUTING_KEY_WHATSAPP_SAIDA,
                    mensagemId);
        }

        return toDTO(mensagem);
    }

    private String resolverTipoMedia(String contentType) {
        return switch (contentType) {
            case "image/jpeg", "image/png", "image/webp" -> "IMAGEM";
            case "audio/ogg", "audio/mpeg", "audio/mp4"  -> "AUDIO";
            case "video/mp4", "video/3gpp"               -> "VIDEO";
            default                                      -> "DOCUMENTO";
        };
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private MensagemDTO toDTO(Mensagem m) {
        return new MensagemDTO(
                m.getId(), m.getDirecao(), m.getRemetente(),
                m.getTipoMedia(), m.getConteudo(), m.getConteudoPrevia(),
                m.getWhatsappStatus(), m.getDataHora(),
                m.getEntregueEm(), m.getLidaEm());
    }
}
