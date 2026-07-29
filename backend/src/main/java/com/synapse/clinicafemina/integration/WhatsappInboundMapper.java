package com.synapse.clinicafemina.integration;
 
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.clinicafemina.config.RabbitMQConfig;
import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Mensagem;
import com.synapse.clinicafemina.domain.MidiaMensagem;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import com.synapse.clinicafemina.integration.WhatsappOutboundClient.MidiaBaixada;
import com.synapse.clinicafemina.integration.whatsapp.WhatsappMediaDownloader;
import com.synapse.clinicafemina.integration.whatsapp.WhatsappProviderType;
import com.synapse.clinicafemina.integration.whatsapp.config.WhatsappProperties;
import com.synapse.clinicafemina.integration.whatsapp.uazap.UazapPictureEnrichmentRequestedEvent;
import com.synapse.clinicafemina.messaging.MensagemEntradaEvent;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.ClinicaRepository;
import com.synapse.clinicafemina.repository.MensagemRepository;
import com.synapse.clinicafemina.repository.MidiaMensagemRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import com.synapse.clinicafemina.service.AtendimentoNotificationService;
import com.synapse.clinicafemina.service.HorarioIaService;
import com.synapse.clinicafemina.service.N8nEventPayload;
import com.synapse.clinicafemina.service.N8nEventService;
import com.synapse.clinicafemina.service.WhatsappPhoneIdentityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
 
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.synapse.clinicafemina.integration.WhatsappInboundPayloadParser.DadosMensagem;
 
@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsappInboundMapper {
 
    private static final long SESSAO_TIMEOUT_HORAS = 24;
    private static final String NOME_PLACEHOLDER = "Contato WhatsApp";
 
    private final PacienteRepository pacienteRepository;
    private final AtendimentoRepository atendimentoRepository;
    private final MensagemRepository mensagemRepository;
    private final MidiaMensagemRepository midiaRepository;
    private final ClinicaRepository clinicaRepository;
    private final RabbitTemplate rabbitTemplate;
    private final N8nEventService n8nEventService;
    private final HorarioIaService horarioIaService;
    private final AtendimentoNotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final WhatsappInboundPayloadParser payloadParser;
    private final Environment environment;
    private final WhatsappOutboundClient whatsappOutboundClient;
    private final List<WhatsappMediaDownloader> mediaDownloaders;
    private final ApplicationEventPublisher eventPublisher;
    private final WhatsappProperties whatsappProperties;
    private final WhatsappPhoneIdentityService phoneIdentityService;

    /**
     * Auto-referência para que {@link #processarEntradaIsolada} passe pelo proxy transacional do
     * Spring (uma transação por mensagem). Injetada por setter com {@link Lazy} para evitar
     * dependência circular; nos testes unitários (sem contexto Spring) permanece {@code this},
     * caso em que {@link Transactional} é inofensivamente ignorado.
     */
    private WhatsappInboundMapper self = this;

    @Autowired
    void setSelf(@Lazy WhatsappInboundMapper self) {
        this.self = self;
    }

    @Value("${WHATSAPP_PHONE_NUMBER_ID:}")
    private String envWhatsappPhoneId;

    @Value("${META_WHATSAPP_PHONE_NUMBER_ID:}")
    private String envMetaWhatsappPhoneId;

    @Value("${APP_CLINIC_WHATSAPP_PHONE_NUMBER_ID:}")
    private String envAppClinicPhoneId;

    @Value("${app.whatsapp.phone-number-id:}")
    private String resolvedPhoneId;

    /** Phone number ID do provider UAZAP (FMNA). Complementa — sem substituir — as variáveis da UltraMedical. */
    @Value("${app.whatsapp.uazap.phone-number-id:}")
    private String uazapPhoneId;
 
    public void processarMensagemTexto(Map<String, Object> value) {
        processarMensagemTexto(value, null);
    }

    /**
     * Orquestra o lote — NÃO é transacional. Cada mensagem é processada em sua própria transação
     * ({@link #processarEntradaIsolada}), de modo que uma mensagem malformada (ex.: timestamp
     * inválido) que provoque rollback nunca descarte as mensagens vizinhas válidas do mesmo
     * webhook. Um webhook com três mensagens gera três transações independentes.
     */
    public void processarMensagemTexto(Map<String, Object> value, byte[] payloadMetaOriginal) {
        for (EntradaResolvida resolvida : resolverEntradas(value, payloadMetaOriginal)) {
            try {
                self.processarEntradaIsolada(resolvida);
            } catch (Exception exception) {
                log.error(
                        "Mensagem inbound descartada por falha isolada (lote preservado): clinicaId={}, whatsappMessageId={}, tipoErro={}",
                        resolvida.clinica() == null ? null : resolvida.clinica().getId(),
                        maskId(normalizarMessageId(resolvida.mensagem())),
                        exception.getClass().getSimpleName());
            }
        }
    }

    /**
     * Processa UMA mensagem em transação própria ({@link Propagation#REQUIRES_NEW}). Executado via
     * {@link #self} para que o proxy transacional do Spring seja aplicado. Nos testes unitários
     * (sem proxy) comporta-se como chamada direta.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processarEntradaIsolada(EntradaResolvida resolvida) {
        processarEntrada(resolvida);
    }

    private void processarEntrada(EntradaResolvida resolvida) {
        Clinica clinica = resolvida.clinica();
        Map<String, Object> contato = resolvida.contato();
        Map<String, Object> payloadMensagem = resolvida.mensagem();
        String whatsappMessageId = normalizarMessageId(payloadMensagem);
        if (whatsappMessageId == null) {
            return;
        }
        Optional<Mensagem> mensagemExistente = mensagemRepository.findByClinicaIdAndWhatsappMessageId(
                clinica.getId(), whatsappMessageId
        );
        if (mensagemExistente.isPresent()) {
            Mensagem existente = mensagemExistente.get();
            Atendimento atendimentoExistente = existente.getAtendimento();
            Long atendimentoId = atendimentoExistente == null ? null : atendimentoExistente.getId();
            Long pacienteId = atendimentoExistente == null || atendimentoExistente.getPaciente() == null
                    ? null
                    : atendimentoExistente.getPaciente().getId();
            log.info(
                    "Mensagem inbound duplicada ignorada: clinicaId={}, mensagemId={}, atendimentoId={}, pacienteId={}, tipoMedia={}, whatsappMessageId={}",
                    clinica.getId(),
                    existente.getId(),
                    atendimentoId,
                    pacienteId,
                    existente.getTipoMedia(),
                    maskId(whatsappMessageId));
            return;
        }
 
        String telefone = resolverWhatsappChatId(payloadMensagem, contato);
        log.info("Processando mensagem inbound. Remetente: {}, clinicaId={}", maskPhone(telefone), clinica.getId());
        logDiagnosticoContatoSanitizado(contato);
 
        PacienteResolvido pacienteResolvido = resolverOuCriarPaciente(clinica, telefone, contato);
        Paciente paciente = pacienteResolvido.paciente();
        log.info("Paciente resolvido: id={}, criado={}", paciente.getId(), pacienteResolvido.criado());
        solicitarEnriquecimentoFotoUazap(paciente);
 
        Atendimento atendimento = resolverOuCriarAtendimento(clinica, paciente);
        persistirWhatsappChatId(atendimento, telefone);
        log.info("Atendimento resolvido: id={}, status={}", atendimento.getId(), atendimento.getStatus());
 
        DadosMensagem dados = payloadParser.extrairDados(payloadMensagem);
 
        Mensagem mensagem = mensagemRepository.save(criarMensagem(
                atendimento, payloadMensagem, whatsappMessageId, dados
        ));
        log.info(
                "Mensagem inbound nova persistida: mensagemId={}, atendimentoId={}, pacienteId={}, tipoMedia={}, whatsappMessageId={}, atendimentoOrigem={}, atendimentoModo={}, iaAtiva={}, textoRecebidoChars={}, textoPersistidoChars={}, previaChars={}, conteudoCodePoints={}, previaCodePoints={}",
                mensagem.getId(),
                atendimento.getId(),
                paciente.getId(),
                mensagem.getTipoMedia(),
                maskId(whatsappMessageId),
                origemAtendimento(atendimento),
                modoAtendimento(atendimento),
                iaAtiva(atendimento),
                tamanhoTexto(dados.conteudo()),
                tamanhoTexto(mensagem.getConteudo()),
                tamanhoTexto(mensagem.getConteudoPrevia()),
                contarCodePoints(mensagem.getConteudo()),
                contarCodePoints(mensagem.getConteudoPrevia()));
 
        if (dados.mediaId() != null) {
            midiaRepository.save(criarMidia(mensagem, dados, resolvida.phoneNumberId()));
        }
 
        atualizarConversa(atendimento, paciente, mensagem);
        // Notificação (escrita no banco) ANTES do N8N: assim todas as escritas de banco desta
        // mensagem se completam primeiro; se algo falhar, o rollback ocorre antes de qualquer
        // chamada externa. O N8N é o último passo — puro efeito colateral, com erros já contidos
        // dentro do N8nEventService (nunca provoca rollback da mensagem persistida).
        notificationService.notificarNovaMensagem(atendimento, mensagem);
        emitirEventos(clinica, pacienteResolvido, atendimento, paciente, mensagem, resolvida.payloadMetaN8n());
        log.info("Mensagem inbound processada com sucesso: atendimento={}", atendimento.getId());
    }
 
    @Transactional
    public Optional<Mensagem> processarStatusUpdate(
            Map<String, Object> value,
            Map<String, Object> status
    ) {
        Optional<Clinica> clinica = resolverClinicaPorPayload(value);
        if (clinica.isEmpty()) return Optional.empty();
 
        String whatsappMessageId = String.valueOf(status.get("id"));
        String novoStatus = normalizarStatus(String.valueOf(status.get("status")));
        OffsetDateTime dataHora = parseTimestamp(String.valueOf(status.get("timestamp")));
        return mensagemRepository.findByClinicaIdAndWhatsappMessageId(
                clinica.get().getId(), whatsappMessageId
        ).map(mensagem -> {
            if (!deveAtualizarStatus(mensagem.getWhatsappStatus(), novoStatus)) {
                return mensagem;
            }
            mensagem.setWhatsappStatus(novoStatus);
            if ("ENTREGUE".equals(novoStatus)) {
                mensagem.setEntregueEm(dataHora);
                mensagem.setMotivoFalha(null);
            }
            if ("LIDA".equals(novoStatus)) {
                if (mensagem.getEntregueEm() == null) {
                    mensagem.setEntregueEm(dataHora);
                }
                mensagem.setLidaEm(dataHora);
                mensagem.setMotivoFalha(null);
            }
            if ("FALHA".equals(novoStatus)) {
                mensagem.setMotivoFalha(motivoFalhaStatus(status));
            }
            Mensagem salva = mensagemRepository.save(mensagem);
            log.info("Status da mensagem atualizado: id={}, whatsappMessageId={}, novoStatus={}",
                    salva.getId(), maskId(whatsappMessageId), novoStatus);
            return salva;
        });
    }

    private String normalizarStatus(String rawStatus) {
        return switch (rawStatus == null ? "" : rawStatus.trim().toUpperCase()) {
            case "DELIVERED", "ENTREGUE" -> "ENTREGUE";
            case "READ", "LIDA" -> "LIDA";
            case "FAILED", "FALHA" -> "FALHA";
            case "SENT", "ENVIADA" -> "ENVIADA";
            case "PENDING", "PENDENTE" -> "PENDENTE";
            default -> "PENDENTE";
        };
    }

    private boolean deveAtualizarStatus(String currentStatus, String nextStatus) {
        String current = normalizarStatus(currentStatus);
        if ("LIDA".equals(current)) return "LIDA".equals(nextStatus);
        if ("ENTREGUE".equals(current)) return !"PENDENTE".equals(nextStatus) && !"ENVIADA".equals(nextStatus);
        if ("FALHA".equals(current)) return "ENTREGUE".equals(nextStatus) || "LIDA".equals(nextStatus);
        return true;
    }

    @SuppressWarnings("unchecked")
    private String motivoFalhaStatus(Map<String, Object> status) {
        Object errors = status.get("errors");
        if (errors instanceof List<?> list && !list.isEmpty() && list.getFirst() instanceof Map<?, ?> error) {
            return motivoFalhaPorCodigo(error.get("code"));
        }
        if (status.get("error") instanceof Map<?, ?> error) {
            return motivoFalhaPorCodigo(error.get("code"));
        }
        return "Mensagem não entregue pelo WhatsApp.";
    }

    private String motivoFalhaPorCodigo(Object rawCode) {
        String code = rawCode == null ? "" : String.valueOf(rawCode).trim();
        if (code.matches("[A-Za-z0-9_-]{1,32}")) {
            return "Mensagem não entregue pelo WhatsApp. Código do provider: " + code + ".";
        }
        return "Mensagem não entregue pelo WhatsApp.";
    }
 
    @SuppressWarnings("unchecked")
    private List<EntradaResolvida> resolverEntradas(Map<String, Object> value, byte[] payloadMetaOriginal) {
        List<Map<String, Object>> contatos = (List<Map<String, Object>>) value.get("contacts");
        List<Map<String, Object>> mensagens = (List<Map<String, Object>>) value.get("messages");
        if (contatos == null || contatos.isEmpty() || mensagens == null || mensagens.isEmpty()) {
            log.warn("Payload WhatsApp sem contato ou mensagem");
            return List.of();
        }
        Optional<Clinica> clinica = resolverClinicaPorPayload(value);
        if (clinica.isEmpty()) {
            return List.of();
        }
        String phoneNumberId = extrairPhoneNumberId(value);
        return mensagens.stream()
                .map(mensagem -> new EntradaResolvida(
                        clinica.get(),
                        contatoParaMensagem(contatos, mensagem),
                        mensagem,
                        payloadMetaParaMensagem(value, contatos, mensagem, payloadMetaOriginal),
                        phoneNumberId
                ))
                .toList();
    }

    private String extrairPhoneNumberId(Map<String, Object> value) {
        Object metadata = value.get("metadata");
        if (metadata instanceof Map<?, ?> mapa && mapa.get("phone_number_id") instanceof String id) {
            return id;
        }
        return null;
    }

    private byte[] payloadMetaParaMensagem(
            Map<String, Object> value,
            List<Map<String, Object>> contatos,
            Map<String, Object> mensagem,
            byte[] payloadMetaOriginal
    ) {
        if (payloadMetaOriginal == null || payloadMetaOriginal.length == 0) {
            return null;
        }
        try {
            return objectMapper.writeValueAsBytes(payloadMetaUnitario(value, contatos, mensagem));
        } catch (JsonProcessingException exception) {
            log.warn("Falha ao montar payload Meta unitario para N8N. whatsappMessageId={}, tipoErro={}",
                    maskId(normalizarMessageId(mensagem)), exception.getClass().getSimpleName());
            return null;
        }
    }

    private Map<String, Object> payloadMetaUnitario(
            Map<String, Object> value,
            List<Map<String, Object>> contatos,
            Map<String, Object> mensagem
    ) {
        Map<String, Object> valueUnitario = new LinkedHashMap<>();
        copiarSeExistir(value, valueUnitario, "messaging_product");
        copiarSeExistir(value, valueUnitario, "metadata");
        List<Map<String, Object>> contatosDaMensagem = contatoFiltrado(contatos, mensagem);
        if (!contatosDaMensagem.isEmpty()) {
            valueUnitario.put("contacts", contatosDaMensagem);
        }
        valueUnitario.put("messages", List.of(mensagem));

        Map<String, Object> change = new LinkedHashMap<>();
        change.put("field", "messages");
        change.put("value", valueUnitario);

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("changes", List.of(change));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("object", "whatsapp_business_account");
        payload.put("entry", List.of(entry));
        return payload;
    }

    private List<Map<String, Object>> contatoFiltrado(
            List<Map<String, Object>> contatos,
            Map<String, Object> mensagem
    ) {
        Object remetente = mensagem.get("from");
        if (remetente == null) {
            return contatos;
        }
        String from = String.valueOf(remetente);
        return contatos.stream()
                .filter(contato -> from.equals(String.valueOf(contato.get("wa_id"))))
                .findFirst()
                .map(List::of)
                .orElse(contatos);
    }

    private void copiarSeExistir(Map<String, Object> origem, Map<String, Object> destino, String chave) {
        if (origem.containsKey(chave)) {
            destino.put(chave, origem.get(chave));
        }
    }

    private Map<String, Object> contatoParaMensagem(
            List<Map<String, Object>> contatos,
            Map<String, Object> mensagem
    ) {
        Object remetente = mensagem.get("from");
        if (remetente != null) {
            String from = String.valueOf(remetente);
            return contatos.stream()
                    .filter(contato -> from.equals(String.valueOf(contato.get("wa_id"))))
                    .findFirst()
                    .orElse(contatos.getFirst());
        }
        return contatos.getFirst();
    }

    private String normalizarMessageId(Map<String, Object> payloadMensagem) {
        Object valor = payloadMensagem.get("id");
        if (valor == null || String.valueOf(valor).isBlank() || "null".equals(String.valueOf(valor))) {
            log.warn("Mensagem inbound ignorada por whatsapp message id ausente.");
            return null;
        }
        return String.valueOf(valor);
    }
 
    private Mensagem criarMensagem(
            Atendimento atendimento,
            Map<String, Object> payload,
            String whatsappMessageId,
            DadosMensagem dados
    ) {
        Mensagem mensagem = new Mensagem();
        mensagem.setAtendimento(atendimento);
        mensagem.setDirecao("ENTRADA");
        mensagem.setRemetente("PACIENTE");
        mensagem.setTipoMedia(dados.tipoMedia());
        mensagem.setConteudo(dados.conteudo());
        mensagem.setConteudoPrevia(payloadParser.limitarPrevia(dados.conteudo()));
        mensagem.setWhatsappMessageId(whatsappMessageId);
        mensagem.setWhatsappStatus("RECEBIDA");
        mensagem.setDataHora(parseTimestamp(String.valueOf(payload.get("timestamp"))));
        return mensagem;
    }
 
    private MidiaMensagem criarMidia(Mensagem mensagem, DadosMensagem dados, String phoneNumberId) {
        MidiaMensagem midia = new MidiaMensagem();
        midia.setMensagem(mensagem);
        midia.setTipoMedia(dados.tipoMedia());
        midia.setMimeType(dados.mimeType());
        midia.setNomeArquivo(dados.nomeArquivo());
        midia.setTamanhoBytes(0L);
        midia.setWhatsappMediaId(dados.mediaId());
        persistirBinarioRecebido(midia, dados.mediaId(), phoneNumberId);
        return midia;
    }

    private void persistirBinarioRecebido(MidiaMensagem midia, String mediaId, String phoneNumberId) {
        if (mediaId == null || mediaId.isBlank()) {
            return;
        }
        try {
            MidiaBaixada baixada = resolveMediaDownloader(phoneNumberId).download(mediaId);
            if (baixada == null || baixada.bytes() == null || baixada.bytes().length == 0) {
                log.warn("Mídia inbound não foi persistida localmente: bytes ausentes. mediaId={}", maskId(mediaId));
                return;
            }
            midia.setS3Bucket("database");
            midia.setS3Chave(baixada.bytes());
            midia.setTamanhoBytes((long) baixada.bytes().length);
            if (baixada.mimeType() != null && !baixada.mimeType().isBlank()) {
                midia.setMimeType(baixada.mimeType());
            }
        } catch (Exception exception) {
            log.warn("Mídia inbound não foi persistida localmente. mediaId={}, tipoErro={}",
                    maskId(mediaId), exception.getClass().getSimpleName());
        }
    }

    /** Resolve o downloader pelo phone_number_id do payload — sem if/else espalhado pelo chamador. */
    private WhatsappMediaDownloader resolveMediaDownloader(String phoneNumberId) {
        return mediaDownloaders.stream()
                .filter(downloader -> downloader.supports(phoneNumberId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Nenhum WhatsappMediaDownloader disponível para o phone_number_id informado"));
    }
 
    private void atualizarConversa(Atendimento atendimento, Paciente paciente, Mensagem mensagem) {
        atendimento.setNaoLidas(atendimento.getNaoLidas() + 1);
        atendimento.setUltimaMensagemEm(mensagem.getDataHora());
        paciente.setUltimaInteracaoEm(mensagem.getDataHora());
        atendimentoRepository.save(atendimento);
        pacienteRepository.save(paciente);
    }
 
    private void emitirEventos(
            Clinica clinica,
            PacienteResolvido pacienteResolvido,
            Atendimento atendimento,
            Paciente paciente,
            Mensagem mensagem,
            byte[] payloadMetaOriginal
    ) {
        if (pacienteResolvido.criado() && payloadMetaOriginal == null) {
            emitirN8n(clinica, "novo_lead", paciente, atendimento);
        }
        Long atendenteId = atendimento.getAtendentePrincipal() == null
                ? null
                : atendimento.getAtendentePrincipal().getId();
        String exchange = RabbitMQConfig.EXCHANGE_MENSAGEM_ENTRADA;
        String routingKey = RabbitMQConfig.ROUTING_KEY_MENSAGEM_ENTRADA;
        MensagemEntradaEvent evento = new MensagemEntradaEvent(
                atendimento.getId(),
                clinica.getId(),
                paciente.getId(),
                paciente.getNomeBusca(),
                mensagem.getId(),
                mensagem.getTipoMedia(),
                mensagem.getConteudoPrevia(),
                mensagem.getDataHora(),
                atendimento.getNaoLidas(),
                atendenteId
        );
        try {
            rabbitTemplate.convertAndSend(
                    exchange,
                    routingKey,
                    evento
            );
        } catch (Exception exception) {
            log.warn("RabbitMQ indisponível para evento de mensagem. atendimento={}, tipoErro={}",
                    atendimento.getId(), exception.getClass().getSimpleName());
        }
        emitirN8nMensagemRecebida(clinica, paciente, atendimento, mensagem, payloadMetaOriginal);
    }
 
    private void emitirN8n(Clinica clinica, String evento, Paciente paciente, Atendimento atendimento) {
        n8nEventService.emitir(n8nEventService.criarPayload(
                clinica,
                evento,
                paciente.getId(),
                atendimento.getId(),
                null,
                paciente.getTelefoneNormalizado()
        ));
    }

    /**
     * Decide (antes do commit) se a mensagem é elegível para o N8N e monta o contexto — mas
     * NUNCA chama o N8N diretamente. A chamada HTTP real só acontece em
     * {@link N8nMensagemRecebidaEventListener}, depois que esta transação (por mensagem,
     * {@code REQUIRES_NEW}) tiver sido efetivamente commitada — ver {@link #publicarEventoN8n}.
     * Bloqueio por modo humano permanece decidido aqui: nenhum evento é publicado nesse caso.
     */
    private void emitirN8nMensagemRecebida(
            Clinica clinica,
            Paciente paciente,
            Atendimento atendimento,
            Mensagem mensagem,
            byte[] payloadMetaOriginal
    ) {
        if (!iaAtiva(atendimento)) {
            log.info(
                    "Payload Meta para N8N bloqueado por atendimento humano: atendimento={}, paciente={}, mensagem={}, tipoMedia={}, whatsappMessageId={}, atendimentoOrigem={}, atendimentoModo={}, iaAtiva={}, horarioMotivo={}",
                    atendimento.getId(),
                    paciente.getId(),
                    mensagem.getId(),
                    mensagem.getTipoMedia(),
                    maskId(mensagem.getWhatsappMessageId()),
                    origemAtendimento(atendimento),
                    modoAtendimento(atendimento),
                    false,
                    HorarioIaService.HUMANO);
            return;
        }
        HorarioIaService.HorarioIaStatus horario = horarioIaService.avaliar(clinica);
        if (payloadMetaOriginal != null && payloadMetaOriginal.length > 0) {
            N8nEventService.MetaWebhookContext contexto = new N8nEventService.MetaWebhookContext(
                    "mensagem_recebida",
                    atendimento.getId(),
                    paciente.getId(),
                    mensagem.getId(),
                    mensagem.getTipoMedia(),
                    mensagem.getWhatsappMessageId(),
                    origemAtendimento(atendimento),
                    modoAtendimento(atendimento),
                    true,
                    horario.dentroHorario(),
                    horario.motivo()
            );
            publicarEventoN8n(clinica, payloadMetaOriginal, contexto, null);
            return;
        }
        N8nEventPayload payloadFallback = n8nEventService.criarPayloadMensagemRecebida(
                clinica, paciente, atendimento, mensagem);
        publicarEventoN8n(clinica, null, null, payloadFallback);
    }

    /**
     * Publica o evento que dispara a chamada N8N — NUNCA a chamada HTTP em si. A chamada real
     * acontece somente em {@link N8nMensagemRecebidaEventListener}
     * ({@code @TransactionalEventListener(phase = AFTER_COMMIT)}), ou seja, depois que esta
     * transação por mensagem tiver sido efetivamente commitada no PostgreSQL. Um rollback desta
     * transação (ex.: falha após o save) faz o evento nunca chegar ao listener — garantia nativa
     * do Spring, não um try/catch nosso. Só valores já resolvidos (nunca proxies JPA lazy) são
     * embutidos no evento.
     */
    private void publicarEventoN8n(
            Clinica clinica,
            byte[] payloadMetaOriginal,
            N8nEventService.MetaWebhookContext contexto,
            N8nEventPayload payloadFallback
    ) {
        eventPublisher.publishEvent(new N8nMensagemRecebidaEvent(
                clinica.getId(),
                clinica.getSlug(),
                clinica.getUsaN8n(),
                clinica.getN8nWebhookUrl(),
                payloadMetaOriginal,
                contexto,
                payloadFallback));
    }
 
    /**
     * Publica o pedido de enriquecimento de foto SOMENTE quando o provider de WhatsApp ativo é a
     * UAZAP. Para a UltraMedical (provider META, valor padrão), este método é um no-op — o evento
     * nunca é publicado e o comportamento existente permanece 100% inalterado. O enriquecimento em
     * si acontece de forma assíncrona, após o commit desta transação (ver
     * {@code UazapPictureEnrichmentEventListener}).
     */
    private void solicitarEnriquecimentoFotoUazap(Paciente paciente) {
        if (whatsappProperties.resolveProvider() != WhatsappProviderType.UAZAP) {
            return;
        }
        if (!whatsappProperties.getUazap().isPictureEnrichmentEnabled()) {
            log.debug("Enriquecimento automatico de foto UAZAP desativado");
            return;
        }
        eventPublisher.publishEvent(new UazapPictureEnrichmentRequestedEvent(
                paciente.getId(),
                paciente.getClinica().getId()
        ));
    }

    private Optional<Clinica> resolverClinicaPorPayload(Map<String, Object> value) {
        Object metadata = value.get("metadata");
        if (!(metadata instanceof Map<?, ?> mapa)
                || !(mapa.get("phone_number_id") instanceof String phoneNumberId)
                || phoneNumberId.isBlank()) {
            log.warn("Payload WhatsApp sem phone_number_id");
            return Optional.empty();
        }

        log.info("Diagnóstico Phone Number ID: Payload (fim) = {}, " +
                 "Resolved Config (fim) = {}, " +
                 "WHATSAPP_PHONE_NUMBER_ID (fim) = {}, " +
                 "META_WHATSAPP_PHONE_NUMBER_ID (fim) = {}, " +
                 "APP_CLINIC_WHATSAPP_PHONE_NUMBER_ID (fim) = {}",
                 maskId(phoneNumberId),
                 maskId(resolvedPhoneId),
                 maskId(envWhatsappPhoneId),
                 maskId(envMetaWhatsappPhoneId),
                 maskId(envAppClinicPhoneId));

        Optional<Clinica> clinica = clinicaRepository.findByWhatsappPhoneNumberId(phoneNumberId);
        if (clinica.isEmpty()) {
            boolean matchesEnv = phoneNumberId.equals(resolvedPhoneId)
                    || phoneNumberId.equals(envWhatsappPhoneId)
                    || phoneNumberId.equals(envMetaWhatsappPhoneId)
                    || phoneNumberId.equals(envAppClinicPhoneId)
                    || phoneNumberId.equals(uazapPhoneId);

            String clinicSlug = environment.getProperty("app.clinic.slug", "ultramedical");

            if (matchesEnv) {
                Optional<Clinica> clinicaFallback = clinicaRepository.findBySlug(clinicSlug);
                if (clinicaFallback.isPresent()) {
                    Clinica c = clinicaFallback.get();
                    log.info("Mapeamento da clínica resolvida por fallback (slug: {}). Atualizando whatsapp_phone_number_id para: {}", 
                             c.getSlug(), maskId(phoneNumberId));
                    c.setWhatsappPhoneNumberId(phoneNumberId);
                    clinicaRepository.save(c);
                    clinica = Optional.of(c);
                } else {
                    log.error("Clínica de slug '{}' não encontrada no banco durante o fallback.", clinicSlug);
                }
            } else {
                log.warn("Payload WhatsApp para phone_number_id {} não configurado. " +
                         "Não coincide com as envs (resolved={}, env={}, meta={}, appClinic={}, uazap={}). " +
                         "Clínica fallback considerada: '{}'. Motivo: ID recebido não coincide com nenhum ID configurado no ambiente.",
                         maskId(phoneNumberId), maskId(resolvedPhoneId), maskId(envWhatsappPhoneId),
                         maskId(envMetaWhatsappPhoneId), maskId(envAppClinicPhoneId), maskId(uazapPhoneId), clinicSlug);
            }
        }
        return clinica;
    }
 
    private PacienteResolvido resolverOuCriarPaciente(
            Clinica clinica,
            String telefone,
            Map<String, Object> contato
    ) {
        Optional<WhatsappPhoneIdentityService.PatientResolution> existente =
                phoneIdentityService.resolvePatient(clinica.getId(), telefone);
        if (existente.isPresent()) {
            Paciente paciente = existente.get().patient();
            atualizarPerfilDoContato(paciente, contato, telefone);
            log.info(
                    "Identidade inbound resolvida. clinicaId={} pacienteId={} origemResolucao={} "
                            + "finalTelefone={}",
                    clinica.getId(), paciente.getId(), existente.get().origin(), maskPhone(telefone)
            );
            return new PacienteResolvido(paciente, false);
        }
        return new PacienteResolvido(
                pacienteRepository.save(criarPaciente(clinica, telefone, contato)),
                true
        );
    }

    private String resolverWhatsappChatId(
            Map<String, Object> payloadMensagem,
            Map<String, Object> contato
    ) {
        String from = normalizeOptionalPhone(payloadMensagem.get("from"));
        String waId = normalizeOptionalPhone(contato == null ? null : contato.get("wa_id"));
        if (from == null && waId == null) {
            throw new IllegalStateException("Mensagem inbound sem identificador de contato valido.");
        }
        String confirmed = from != null ? from : waId;
        if (from != null && waId != null
                && !phoneIdentityService.identify(from).aliases().contains(waId)) {
            throw new IllegalStateException(
                    "Mensagem inbound com identificadores de contato conflitantes."
            );
        }
        return confirmed;
    }

    private String normalizeOptionalPhone(Object rawValue) {
        if (rawValue == null || String.valueOf(rawValue).isBlank()
                || "null".equalsIgnoreCase(String.valueOf(rawValue))) {
            return null;
        }
        return phoneIdentityService.identify(String.valueOf(rawValue)).normalized();
    }

    private void persistirWhatsappChatId(Atendimento atendimento, String confirmedChatId) {
        if (confirmedChatId == null || confirmedChatId.isBlank()) {
            return;
        }
        String normalized = phoneIdentityService.identify(confirmedChatId).normalized();
        String current = atendimento.getWhatsappChatId();
        if (current != null && !current.isBlank()
                && !phoneIdentityService.identify(current).aliases().contains(normalized)) {
            log.warn(
                    "whatsappChatId divergente nao atualizado. clinicaId={} pacienteId={} "
                            + "atendimentoId={} atual={} recebido={}",
                    atendimento.getClinica().getId(), atendimento.getPaciente().getId(),
                    atendimento.getId(), maskPhone(current), maskPhone(normalized)
            );
            throw new IllegalStateException("Identidade WhatsApp conflitante no atendimento.");
        }
        if (!normalized.equals(current)) {
            atendimento.setWhatsappChatId(normalized);
            atendimentoRepository.save(atendimento);
            log.info(
                    "whatsappChatId confirmado pelo inbound. clinicaId={} pacienteId={} "
                            + "atendimentoId={} finalTelefone={}",
                    atendimento.getClinica().getId(), atendimento.getPaciente().getId(),
                    atendimento.getId(), maskPhone(normalized)
            );
        }
    }

    private Paciente criarPaciente(Clinica clinica, String telefone, Map<String, Object> contato) {
        Map<String, Object> perfil = perfilDoContato(contato);
        String nome = nomeValido(perfil == null ? null : perfil.get("name")).orElse(NOME_PLACEHOLDER);

        Paciente paciente = new Paciente();
        paciente.setClinica(clinica);
        paciente.setNome(nome);
        paciente.setNomeBusca(nome.toUpperCase());
        paciente.setTelefone("+" + telefone);
        paciente.setTelefoneNormalizado(telefone);
        paciente.setExternalSource(ExternalProviderType.WHATSAPP);
        paciente.setExternalId(telefone);
        paciente.setFotoUrl(fotoUrlDoPerfil(perfil));
        paciente.setStatus("EM_ATENDIMENTO");
        return paciente;
    }

    /**
     * Atualiza foto e/ou nome de um paciente já cadastrado a partir de um novo contato recebido.
     * A foto só é preenchida se ainda não existir. O nome só é atualizado quando o valor atual é
     * um placeholder comprovado (vazio, "Contato WhatsApp", "null" ou o próprio telefone) — nunca
     * sobrescreve um nome legítimo já cadastrado no CRM.
     */
    private void atualizarPerfilDoContato(Paciente paciente, Map<String, Object> contato, String telefone) {
        Map<String, Object> perfil = perfilDoContato(contato);
        boolean alterado = false;

        if (paciente.getFotoUrl() == null || paciente.getFotoUrl().isBlank()) {
            String fotoUrl = fotoUrlDoPerfil(perfil);
            if (fotoUrl != null) {
                paciente.setFotoUrl(fotoUrl);
                alterado = true;
            }
        }

        Optional<String> nomeRecebido = nomeValido(perfil == null ? null : perfil.get("name"));
        if (nomeRecebido.isPresent() && ehNomePlaceholder(paciente.getNome(), telefone)) {
            paciente.setNome(nomeRecebido.get());
            paciente.setNomeBusca(nomeRecebido.get().toUpperCase());
            alterado = true;
        }

        if (alterado) {
            pacienteRepository.save(paciente);
        }
    }

    /**
     * Normaliza um valor bruto de nome vindo do payload: {@code null}, string vazia/em branco e a
     * string literal {@code "null"} (produzida por {@code String.valueOf(null)} quando
     * {@code profile} existe mas {@code profile.name} está ausente) são tratados como ausentes.
     */
    private Optional<String> nomeValido(Object valorBruto) {
        if (valorBruto == null) {
            return Optional.empty();
        }
        String valor = String.valueOf(valorBruto).trim();
        if (valor.isEmpty() || "null".equalsIgnoreCase(valor)) {
            return Optional.empty();
        }
        return Optional.of(valor);
    }

    /** Placeholders comprovados: nome vazio, o texto padrão, "null" literal ou o próprio telefone. */
    private boolean ehNomePlaceholder(String nomeAtual, String telefone) {
        if (nomeAtual == null || nomeAtual.isBlank()) {
            return true;
        }
        String normalizado = nomeAtual.trim();
        if (NOME_PLACEHOLDER.equalsIgnoreCase(normalizado) || "null".equalsIgnoreCase(normalizado)) {
            return true;
        }
        return normalizado.equals(telefone) || normalizado.equals("+" + telefone);
    }

    /**
     * Diagnóstico TEMPORÁRIO e sanitizado do contato inbound (Meta e UAZAP): registra apenas as
     * CHAVES existentes e presença booleana de campos de foto — nunca nome, telefone, URL ou
     * qualquer valor pessoal. Serve para confirmar, na próxima mensagem real, quais chaves a UAZAP
     * efetivamente envia em {@code contacts[]}/{@code contacts[].profile}.
     */
    private void logDiagnosticoContatoSanitizado(Map<String, Object> contato) {
        if (!log.isDebugEnabled()) {
            return;
        }
        if (contato == null) {
            log.debug("Diagnóstico contato inbound: contato ausente.");
            return;
        }
        Map<String, Object> perfil = perfilDoContato(contato);
        log.debug(
                "Diagnóstico sanitizado de contato inbound: contactKeys={}, profileKeys={}, "
                        + "hasName={}, hasPicture={}, hasAvatar={}, hasPhoto={}, hasProfilePicture={}",
                contato.keySet(),
                perfil == null ? java.util.Set.of() : perfil.keySet(),
                perfil != null && perfil.get("name") != null,
                perfil != null && perfil.get("picture") != null,
                perfil != null && perfil.get("avatar") != null,
                perfil != null && perfil.get("photo") != null,
                perfil != null && perfil.get("profilePicture") != null
        );
    }

    private Map<String, Object> perfilDoContato(Map<String, Object> contato) {
        Object perfil = contato == null ? null : contato.get("profile");
        if (perfil instanceof Map<?, ?> mapa) {
            Map<String, Object> resultado = new LinkedHashMap<>();
            mapa.forEach((chave, valor) -> resultado.put(String.valueOf(chave), valor));
            return resultado;
        }
        return null;
    }

    private String fotoUrlDoPerfil(Map<String, Object> perfil) {
        if (perfil == null) {
            return null;
        }
        Object valor = perfil.get("picture");
        if (valor == null) {
            valor = perfil.get("avatar");
        }
        return normalizarFotoUrl(valor == null ? null : String.valueOf(valor));
    }

    private String normalizarFotoUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getQuery() != null
                    || uri.getFragment() != null) {
                return null;
            }
            return uri.toString();
        } catch (URISyntaxException exception) {
            return null;
        }
    }
 
    private Atendimento resolverOuCriarAtendimento(Clinica clinica, Paciente paciente) {
        Optional<Atendimento> ativo = atendimentoRepository.findAtivo(clinica.getId(), paciente.getId());
        if (ativo.isPresent()) return ativo.get();
 
        OffsetDateTime limite = OffsetDateTime.now().minusHours(SESSAO_TIMEOUT_HORAS);
        if (atendimentoRepository.existeEncerradoDesde(clinica.getId(), paciente.getId(), limite)) {
            return atendimentoRepository.findUltimo(clinica.getId(), paciente.getId())
                    .map(this::reabrir)
                    .orElseGet(() -> criarAtendimento(clinica, paciente));
        }
        return criarAtendimento(clinica, paciente);
    }
 
    private Atendimento reabrir(Atendimento atendimento) {
        atendimento.setStatus("ATIVO");
        atendimento.setDataEncerramento(null);
        return atendimentoRepository.save(atendimento);
    }
 
    private Atendimento criarAtendimento(Clinica clinica, Paciente paciente) {
        Atendimento atendimento = new Atendimento();
        atendimento.setClinica(clinica);
        atendimento.setPaciente(paciente);
        atendimento.setStatus("ATIVO");
        atendimento.setTratadoPorIa(true);
        atendimento.setNaoLidas(0);
        return atendimentoRepository.save(atendimento);
    }
 
    /**
     * Converte o timestamp epoch-seconds do payload para UTC. Defensivo: se o valor estiver
     * ausente ou não for numérico (ex.: variação de formato entre provedores), NÃO lança —
     * registra diagnóstico sanitizado e usa o instante atual, para nunca perder a mensagem por
     * causa de um timestamp malformado.
     */
    private OffsetDateTime parseTimestamp(String timestamp) {
        try {
            return OffsetDateTime.ofInstant(
                    Instant.ofEpochSecond(Long.parseLong(timestamp.trim())),
                    ZoneOffset.UTC);
        } catch (NumberFormatException | NullPointerException exception) {
            log.warn("Timestamp inbound ausente/invalido; usando horario atual. tipoErro={}",
                    exception.getClass().getSimpleName());
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
    }
 
    private String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "vazio";
        }
        String normal = phone.replaceAll("\\D", "");
        if (normal.length() <= 4) {
            return "****";
        }
        return "******" + normal.substring(normal.length() - 4);
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

    private boolean iaAtiva(Atendimento atendimento) {
        return Boolean.TRUE.equals(atendimento.getTratadoPorIa());
    }

    private String origemAtendimento(Atendimento atendimento) {
        return iaAtiva(atendimento) ? "IA" : "HUMANO";
    }

    private String modoAtendimento(Atendimento atendimento) {
        return origemAtendimento(atendimento);
    }

    private int tamanhoTexto(String texto) {
        return texto == null ? 0 : texto.length();
    }

    /** Contagem por code points (observabilidade Unicode-safe da prévia/conteúdo). */
    private int contarCodePoints(String texto) {
        return texto == null ? 0 : texto.codePointCount(0, texto.length());
    }
 
    /**
     * Package-private (não {@code private}) porque é o tipo de parâmetro de
     * {@link #processarEntradaIsolada}, método público proxiado pelo Spring/CGLIB — a subclasse
     * gerada, no mesmo pacote, precisa enxergar este tipo.
     */
    record EntradaResolvida(
            Clinica clinica,
            Map<String, Object> contato,
            Map<String, Object> mensagem,
            byte[] payloadMetaN8n,
            String phoneNumberId
    ) {
    }
 
    private record PacienteResolvido(Paciente paciente, boolean criado) {
    }
}
