package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.config.RabbitMQConfig;
import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.Mensagem;
import com.synapse.clinicafemina.domain.MidiaMensagem;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.EnviarMensagemRequest;
import com.synapse.clinicafemina.dto.MensagemDTO;
import com.synapse.clinicafemina.dto.n8n.N8nResponderRequest;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.integration.WhatsappOutboundClient;
import com.synapse.clinicafemina.integration.WhatsappTemplateRequiredException;
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
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MensagemService {

    private static final long TAMANHO_MAXIMO = 16L * 1024 * 1024;
    private static final int TAMANHO_MAXIMO_WHATSAPP_MESSAGE_ID = 255;
    private static final int TAMANHO_MAXIMO_PREVIA = 60;
    private static final String SUFIXO_PREVIA_TRUNCADA = "...";

    public record RespostaIaResultado(MensagemDTO mensagem, boolean duplicada) {
    }

    private final MensagemRepository mensagemRepository;
    private final MidiaMensagemRepository midiaRepository;
    private final AtendimentoRepository atendimentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final WhatsappOutboundClient whatsappOutboundClient;
    private final RabbitTemplate rabbitTemplate;
    private final WhatsappWindowService whatsappWindowService;

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
        whatsappWindowService.exigirAberta(atendimentoId, clinicaId);

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
    public RespostaIaResultado responderIa(
            Long atendimentoId,
            Long clinicaId,
            N8nResponderRequest request
    ) {
        validarRespostaN8n(request);
        Atendimento atendimento = buscarAtendimentoAtivoParaN8n(atendimentoId, clinicaId);
        validarPacienteDoAtendimento(atendimento, request.pacienteId());
        Optional<Mensagem> existente = buscarMensagemN8nExistente(atendimento, request.whatsappMessageId());
        if (existente.isPresent()) {
            return new RespostaIaResultado(toDTO(existente.get()), true);
        }
        boolean enviarWhatsapp = deveEnviarWhatsapp(request);
        if (enviarWhatsapp) {
            whatsappWindowService.exigirAberta(atendimentoId, clinicaId);
        }

        String conteudo = request.mensagem().trim();
        Mensagem mensagem = criarMensagemSaida(
                atendimento,
                null,
                "IA",
                "TEXTO",
                conteudo,
                limitarPrevia(conteudo)
        );
        mensagem.setDataHora(request.enviadoEm() != null ? request.enviadoEm() : OffsetDateTime.now());
        if (request.whatsappMessageId() != null && !request.whatsappMessageId().isBlank()) {
            mensagem.setWhatsappMessageId(request.whatsappMessageId().trim());
        }
        mensagem = mensagemRepository.save(mensagem);
        atualizarUltimaMensagem(atendimento, mensagem);

        if (!enviarWhatsapp) {
            mensagem.setWhatsappStatus("REGISTRADA");
            return new RespostaIaResultado(toDTO(mensagemRepository.save(mensagem)), false);
        }

        try {
            whatsappOutboundClient.validarConfiguracao();
            String wamid = whatsappOutboundClient.enviarTexto(
                    atendimento.getPaciente().getTelefoneNormalizado(), conteudo
            );
            mensagem.setWhatsappMessageId(wamid);
            mensagem.setWhatsappStatus("ENVIADA");
        } catch (Exception exception) {
            registrarFalha(mensagem, exception, false);
        }
        return new RespostaIaResultado(toDTO(mensagemRepository.save(mensagem)), false);
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
        whatsappWindowService.exigirAberta(atendimentoId, clinicaId);
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

    public WhatsappOutboundClient.MidiaBaixada baixarBinarioMidia(String mediaId) {
        if (mediaId == null || mediaId.isBlank()) {
            return null;
        }
        try {
            return whatsappOutboundClient.baixarMidia(mediaId);
        } catch (Exception e) {
            log.error("Erro ao baixar binário da mídia: mediaId={}, tipoErro={}",
                    maskId(mediaId), e.getClass().getSimpleName());
            return null;
        }
    }

    public WhatsappOutboundClient.MidiaBaixada obterBinarioMidia(MidiaMensagem midia) {
        if (midia == null) {
            return null;
        }
        byte[] bytesPersistidos = midia.getS3Chave();
        if (bytesPersistidos != null && bytesPersistidos.length > 0) {
            return new WhatsappOutboundClient.MidiaBaixada(bytesPersistidos, midia.getMimeType());
        }

        WhatsappOutboundClient.MidiaBaixada baixada = baixarBinarioMidia(midia.getWhatsappMediaId());
        if (baixada != null && baixada.bytes() != null && baixada.bytes().length > 0) {
            armazenarLocalmente(midia, baixada);
            try {
                midiaRepository.save(midia);
            } catch (Exception exception) {
                log.warn("Mídia baixada servida, mas não persistida localmente. mensagemId={}, tipoErro={}",
                        midia.getMensagem() == null ? null : midia.getMensagem().getId(),
                        exception.getClass().getSimpleName());
            }
        }
        return baixada;
    }

    private void armazenarLocalmente(
            MidiaMensagem midia,
            WhatsappOutboundClient.MidiaBaixada baixada
    ) {
        midia.setS3Bucket("database");
        midia.setS3Chave(baixada.bytes());
        midia.setTamanhoBytes((long) baixada.bytes().length);
        if (baixada.mimeType() != null && !baixada.mimeType().isBlank()) {
            midia.setMimeType(baixada.mimeType());
        }
    }

    private Atendimento buscarAtendimentoAtivo(Long atendimentoId, Long clinicaId) {
        Atendimento atendimento = atendimentoRepository.findByIdAndClinicaId(atendimentoId, clinicaId)
                .orElseThrow(() -> new NotFoundException("Atendimento não encontrado"));
        if (!"ATIVO".equals(atendimento.getStatus())) {
            throw new IllegalStateException("Só é possível enviar mensagens para atendimentos ativos");
        }
        return atendimento;
    }

    private Atendimento buscarAtendimentoAtivoParaN8n(Long atendimentoId, Long clinicaId) {
        Atendimento atendimento = atendimentoRepository.findByIdAndClinicaId(atendimentoId, clinicaId)
                .orElseThrow(() -> new NotFoundException("Atendimento nao encontrado"));
        if (!"ATIVO".equals(atendimento.getStatus())) {
            throw new IllegalStateException("So e possivel responder atendimentos ativos");
        }
        if (!Boolean.TRUE.equals(atendimento.getTratadoPorIa())) {
            throw new IllegalStateException("Atendimento esta em modo humano");
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
        return criarMensagemSaida(atendimento, remetente, "ATENDENTE", tipoMedia, conteudo, previa);
    }

    private Mensagem criarMensagemSaida(
            Atendimento atendimento,
            Usuario remetente,
            String remetenteNome,
            String tipoMedia,
            String conteudo,
            String previa
    ) {
        Mensagem mensagem = new Mensagem();
        mensagem.setAtendimento(atendimento);
        mensagem.setDirecao("SAIDA");
        mensagem.setRemetente(remetenteNome);
        mensagem.setRemetenteUsuario(remetente);
        mensagem.setTipoMedia(tipoMedia);
        mensagem.setConteudo(conteudo);
        mensagem.setConteudoPrevia(previa);
        mensagem.setWhatsappStatus("PENDENTE");
        mensagem.setDataHora(OffsetDateTime.now());
        return mensagem;
    }

    private void validarRespostaN8n(N8nResponderRequest request) {
        if (request == null) {
            throw new BadRequestException("Requisicao invalida");
        }
        if (!"N8N".equalsIgnoreCase(request.origem())) {
            throw new BadRequestException("Origem invalida para resposta da IA");
        }
        if (!"TEXTO".equalsIgnoreCase(request.tipoMedia())) {
            throw new BadRequestException("Resposta N8N aceita apenas tipoMedia TEXTO");
        }
        if (request.mensagem() == null || request.mensagem().isBlank()) {
            throw new BadRequestException("Mensagem da IA nao pode ser vazia");
        }
        if (request.mensagem().length() > 4096) {
            throw new BadRequestException("Mensagem da IA excede 4096 caracteres");
        }
        if (request.whatsappMessageId() != null
                && request.whatsappMessageId().trim().length() > TAMANHO_MAXIMO_WHATSAPP_MESSAGE_ID) {
            throw new BadRequestException("whatsappMessageId excede 255 caracteres");
        }
    }

    private void validarPacienteDoAtendimento(Atendimento atendimento, Long pacienteId) {
        Long pacienteAtendimentoId = atendimento.getPaciente() == null ? null : atendimento.getPaciente().getId();
        if (pacienteId == null || !pacienteId.equals(pacienteAtendimentoId)) {
            throw new BadRequestException("Paciente nao pertence ao atendimento informado");
        }
    }

    private Optional<Mensagem> buscarMensagemN8nExistente(Atendimento atendimento, String whatsappMessageId) {
        if (whatsappMessageId == null || whatsappMessageId.isBlank()) {
            return Optional.empty();
        }
        Long clinicaId = atendimento.getClinica() == null ? null : atendimento.getClinica().getId();
        if (clinicaId == null) {
            return Optional.empty();
        }
        return mensagemRepository.findByClinicaIdAndWhatsappMessageId(clinicaId, whatsappMessageId.trim());
    }

    private boolean deveEnviarWhatsapp(N8nResponderRequest request) {
        return !Boolean.FALSE.equals(request.enviarWhatsapp());
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
        WhatsappTemplateRequiredException templateRequired = findTemplateRequired(exception);
        if (templateRequired != null) {
            throw templateRequired;
        }
        mensagem.setMotivoFalha(motivoFalha(exception));
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

    private String motivoFalha(Throwable exception) {
        WhatsappTemplateRequiredException templateRequired = findTemplateRequired(exception);
        if (templateRequired != null) {
            return templateRequired.getMessage();
        }
        return "WhatsApp/Meta indisponível ou não configurado";
    }

    private WhatsappTemplateRequiredException findTemplateRequired(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof WhatsappTemplateRequiredException exception) {
                return exception;
            }
            current = current.getCause();
        }
        return null;
    }

    private String maskId(String id) {
        if (id == null || id.isBlank()) {
            return "vazio";
        }
        String trimmed = id.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return "****" + trimmed.substring(trimmed.length() - 4);
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
        if (conteudo.length() <= TAMANHO_MAXIMO_PREVIA) {
            return conteudo;
        }
        int tamanhoTexto = TAMANHO_MAXIMO_PREVIA - SUFIXO_PREVIA_TRUNCADA.length();
        return conteudo.substring(0, tamanhoTexto) + SUFIXO_PREVIA_TRUNCADA;
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
                midia,
                mensagem.getTemplateNome(),
                mensagem.getTemplateIdioma()
        );
    }
}
