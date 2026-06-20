package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.config.RabbitMQConfig;
import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.Mensagem;
import com.synapse.clinicafemina.domain.MidiaMensagem;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.EnviarMensagemRequest;
import com.synapse.clinicafemina.dto.MensagemDTO;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.integration.WhatsappOutboundClient;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.MensagemRepository;
import com.synapse.clinicafemina.repository.MidiaMensagemRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class MensagemService {

    private static final long TAMANHO_MAXIMO = 16L * 1024 * 1024;

    private final MensagemRepository mensagemRepository;
    private final MidiaMensagemRepository midiaRepository;
    private final AtendimentoRepository atendimentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final WhatsappOutboundClient whatsappOutboundClient;
    private final RabbitTemplate rabbitTemplate;

    @Transactional(readOnly = true)
    public Page<MensagemDTO> listarHistorico(Long atendimentoId, Long clinicaId, Pageable pageable) {
        return mensagemRepository
                .findByAtendimentoIdAndClinicaId(atendimentoId, clinicaId, pageable)
                .map(this::toDTO);
    }

    @Transactional
    public MensagemDTO enviar(
            Long atendimentoId,
            Long clinicaId,
            EnviarMensagemRequest request,
            Long remetenteUsuarioId
    ) {
        Atendimento atendimento = buscarAtendimentoAtivo(atendimentoId, clinicaId);
        Usuario remetente = buscarRemetente(remetenteUsuarioId, clinicaId);
        if (!"TEXTO".equalsIgnoreCase(request.tipoMedia())) {
            throw new BadRequestException("O endpoint de texto aceita apenas tipo TEXTO");
        }

        Mensagem mensagem = criarMensagemSaida(
                atendimento, remetente, "TEXTO", request.conteudo(), limitarPrevia(request.conteudo())
        );
        mensagem = mensagemRepository.save(mensagem);
        atualizarUltimaMensagem(atendimento, mensagem);

        try {
            whatsappOutboundClient.validarConfiguracao();
            String wamid = whatsappOutboundClient.enviarTexto(
                    atendimento.getPaciente().getTelefoneNormalizado(), request.conteudo()
            );
            mensagem.setWhatsappMessageId(wamid);
            mensagem.setWhatsappStatus("ENVIADA");
        } catch (Exception exception) {
            registrarFalha(mensagem, exception, false);
        }
        return toDTO(mensagemRepository.save(mensagem));
    }

    @Transactional
    public MensagemDTO enviarMidia(
            Long atendimentoId,
            Long clinicaId,
            MultipartFile arquivo,
            Long remetenteUsuarioId
    ) {
        validarArquivo(arquivo);
        Atendimento atendimento = buscarAtendimentoAtivo(atendimentoId, clinicaId);
        Usuario remetente = buscarRemetente(remetenteUsuarioId, clinicaId);
        String mimeType = arquivo.getContentType();
        String tipoMedia = resolverTipoMedia(mimeType);
        String nomeArquivo = sanitizarNomeArquivo(arquivo.getOriginalFilename());

        Mensagem mensagem = mensagemRepository.save(criarMensagemSaida(
                atendimento,
                remetente,
                tipoMedia,
                "[" + tipoMedia + "] " + nomeArquivo,
                "[" + tipoMedia + "] " + nomeArquivo
        ));
        atualizarUltimaMensagem(atendimento, mensagem);

        try {
            whatsappOutboundClient.validarConfiguracao();
            String mediaId = whatsappOutboundClient.uploadMidia(
                    arquivo.getResource(), mimeType, nomeArquivo
            );
            String wamid = whatsappOutboundClient.enviarMidia(
                    atendimento.getPaciente().getTelefoneNormalizado(),
                    tipoMedia.toLowerCase(Locale.ROOT),
                    mediaId
            );
            mensagem.setWhatsappMessageId(wamid);
            mensagem.setWhatsappStatus("ENVIADA");
            midiaRepository.save(criarMidia(
                    mensagem, tipoMedia, mimeType, nomeArquivo, arquivo.getSize(), mediaId
            ));
        } catch (Exception exception) {
            registrarFalha(mensagem, exception, true);
        }
        return toDTO(mensagemRepository.save(mensagem));
    }

    @Transactional(readOnly = true)
    public MidiaMensagem buscarMidia(
            Long atendimentoId,
            Long mensagemId,
            Long clinicaId
    ) {
        return midiaRepository.findAutorizada(mensagemId, atendimentoId, clinicaId)
                .orElseThrow(() -> new NotFoundException("Mídia não encontrada"));
    }

    private Atendimento buscarAtendimentoAtivo(Long atendimentoId, Long clinicaId) {
        Atendimento atendimento = atendimentoRepository.findByIdAndClinicaId(atendimentoId, clinicaId)
                .orElseThrow(() -> new NotFoundException("Atendimento não encontrado"));
        if (!"ATIVO".equals(atendimento.getStatus())) {
            throw new IllegalStateException("Só é possível enviar mensagens para atendimentos ativos");
        }
        return atendimento;
    }

    private Usuario buscarRemetente(Long usuarioId, Long clinicaId) {
        return usuarioRepository.findAtivoByIdAndClinicaId(usuarioId, clinicaId)
                .orElseThrow(() -> new NotFoundException("Usuário remetente não encontrado"));
    }

    private Mensagem criarMensagemSaida(
            Atendimento atendimento,
            Usuario remetente,
            String tipoMedia,
            String conteudo,
            String previa
    ) {
        Mensagem mensagem = new Mensagem();
        mensagem.setAtendimento(atendimento);
        mensagem.setDirecao("SAIDA");
        mensagem.setRemetente("ATENDENTE");
        mensagem.setRemetenteUsuario(remetente);
        mensagem.setTipoMedia(tipoMedia);
        mensagem.setConteudo(conteudo);
        mensagem.setConteudoPrevia(previa);
        mensagem.setWhatsappStatus("PENDENTE");
        mensagem.setDataHora(OffsetDateTime.now());
        return mensagem;
    }

    private MidiaMensagem criarMidia(
            Mensagem mensagem,
            String tipoMedia,
            String mimeType,
            String nomeArquivo,
            long tamanho,
            String mediaId
    ) {
        MidiaMensagem midia = new MidiaMensagem();
        midia.setMensagem(mensagem);
        midia.setTipoMedia(tipoMedia);
        midia.setMimeType(mimeType);
        midia.setNomeArquivo(nomeArquivo);
        midia.setTamanhoBytes(tamanho);
        midia.setWhatsappMediaId(mediaId);
        return midia;
    }

    private void atualizarUltimaMensagem(Atendimento atendimento, Mensagem mensagem) {
        atendimento.setUltimaMensagemEm(mensagem.getDataHora());
        atendimentoRepository.save(atendimento);
    }

    private void registrarFalha(Mensagem mensagem, Exception exception, boolean midia) {
        mensagem.setMotivoFalha("WhatsApp/Meta indisponível ou não configurado");
        mensagem.setWhatsappStatus("FALHA");
        log.error(
                "Falha ao enviar {} {} para WhatsApp. tipoErro={}",
                midia ? "mídia" : "mensagem",
                mensagem.getId(),
                exception.getClass().getSimpleName()
        );
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_WHATSAPP_SAIDA,
                    RabbitMQConfig.ROUTING_KEY_WHATSAPP_SAIDA,
                    mensagem.getId()
            );
        } catch (Exception publishException) {
            log.error("Falha ao registrar retry da mensagem {}. tipoErro={}",
                    mensagem.getId(), publishException.getClass().getSimpleName());
        }
    }

    private void validarArquivo(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new BadRequestException("Selecione um arquivo para enviar");
        }
        if (arquivo.getSize() > TAMANHO_MAXIMO) {
            throw new BadRequestException("O arquivo excede o limite de 16 MB");
        }
        String contentType = arquivo.getContentType();
        if (contentType == null || !java.util.Set.of(
                "image/jpeg", "image/png", "image/webp",
                "audio/ogg", "audio/mpeg", "audio/mp4",
                "application/pdf"
        ).contains(contentType)) {
            throw new BadRequestException("Tipo de arquivo não suportado");
        }
    }

    private String resolverTipoMedia(String contentType) {
        if (contentType.startsWith("image/")) return "IMAGEM";
        if (contentType.startsWith("audio/")) return "AUDIO";
        return "DOCUMENTO";
    }

    private String sanitizarNomeArquivo(String nome) {
        String seguro = nome == null || nome.isBlank() ? "arquivo" : nome;
        return seguro.replaceAll("[\\\\/\\r\\n]", "_");
    }

    private String limitarPrevia(String conteudo) {
        return conteudo.length() > 60 ? conteudo.substring(0, 60) + "…" : conteudo;
    }

    private MensagemDTO toDTO(Mensagem mensagem) {
        MensagemDTO.MidiaDTO midia = midiaRepository.findByMensagemId(mensagem.getId())
                .map(item -> new MensagemDTO.MidiaDTO(
                        item.getTipoMedia(),
                        item.getMimeType(),
                        item.getNomeArquivo(),
                        item.getTamanhoBytes(),
                        "/api/atendimentos/" + mensagem.getAtendimento().getId()
                                + "/mensagens/" + mensagem.getId() + "/midia"
                ))
                .orElse(null);
        return new MensagemDTO(
                mensagem.getId(),
                mensagem.getDirecao(),
                mensagem.getRemetente(),
                mensagem.getTipoMedia(),
                mensagem.getConteudo(),
                mensagem.getConteudoPrevia(),
                mensagem.getWhatsappStatus(),
                mensagem.getMotivoFalha(),
                mensagem.getDataHora(),
                mensagem.getEntregueEm(),
                mensagem.getLidaEm(),
                midia
        );
    }
}
