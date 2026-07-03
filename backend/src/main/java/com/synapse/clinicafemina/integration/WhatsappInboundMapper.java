package com.synapse.clinicafemina.integration;
 
import com.synapse.clinicafemina.config.RabbitMQConfig;
import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Mensagem;
import com.synapse.clinicafemina.domain.MidiaMensagem;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import com.synapse.clinicafemina.integration.WhatsappOutboundClient.MidiaBaixada;
import com.synapse.clinicafemina.messaging.MensagemEntradaEvent;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.ClinicaRepository;
import com.synapse.clinicafemina.repository.MensagemRepository;
import com.synapse.clinicafemina.repository.MidiaMensagemRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import com.synapse.clinicafemina.service.AtendimentoNotificationService;
import com.synapse.clinicafemina.service.N8nEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
 
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.synapse.clinicafemina.integration.WhatsappInboundPayloadParser.DadosMensagem;
 
@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsappInboundMapper {
 
    private static final long SESSAO_TIMEOUT_HORAS = 24;
 
    private final PacienteRepository pacienteRepository;
    private final AtendimentoRepository atendimentoRepository;
    private final MensagemRepository mensagemRepository;
    private final MidiaMensagemRepository midiaRepository;
    private final ClinicaRepository clinicaRepository;
    private final RabbitTemplate rabbitTemplate;
    private final N8nEventService n8nEventService;
    private final AtendimentoNotificationService notificationService;
    private final WhatsappInboundPayloadParser payloadParser;
    private final Environment environment;
    private final WhatsappOutboundClient whatsappOutboundClient;

    @Value("${WHATSAPP_PHONE_NUMBER_ID:}")
    private String envWhatsappPhoneId;

    @Value("${META_WHATSAPP_PHONE_NUMBER_ID:}")
    private String envMetaWhatsappPhoneId;

    @Value("${APP_CLINIC_WHATSAPP_PHONE_NUMBER_ID:}")
    private String envAppClinicPhoneId;

    @Value("${app.whatsapp.phone-number-id:}")
    private String resolvedPhoneId;
 
    @Transactional
    public void processarMensagemTexto(Map<String, Object> value) {
        processarMensagemTexto(value, null);
    }

    @Transactional
    public void processarMensagemTexto(Map<String, Object> value, byte[] payloadMetaOriginal) {
        Optional<EntradaResolvida> entrada = resolverEntrada(value);
        if (entrada.isEmpty()) return;
 
        EntradaResolvida resolvida = entrada.get();
        Clinica clinica = resolvida.clinica();
        Map<String, Object> contato = resolvida.contato();
        Map<String, Object> payloadMensagem = resolvida.mensagem();
        String whatsappMessageId = String.valueOf(payloadMensagem.get("id"));
        if (mensagemRepository.findByClinicaIdAndWhatsappMessageId(
                clinica.getId(), whatsappMessageId
        ).isPresent()) {
            return;
        }
 
        String telefone = payloadParser.normalizarTelefone(String.valueOf(contato.get("wa_id")));
        log.info("Processando mensagem inbound. Remetente: {}, clinicaId={}", maskPhone(telefone), clinica.getId());
 
        PacienteResolvido pacienteResolvido = resolverOuCriarPaciente(clinica, telefone, contato);
        Paciente paciente = pacienteResolvido.paciente();
        log.info("Paciente resolvido: id={}, criado={}", paciente.getId(), pacienteResolvido.criado());
 
        Atendimento atendimento = resolverOuCriarAtendimento(clinica, paciente);
        log.info("Atendimento resolvido: id={}, status={}", atendimento.getId(), atendimento.getStatus());
 
        DadosMensagem dados = payloadParser.extrairDados(payloadMensagem);
 
        Mensagem mensagem = mensagemRepository.save(criarMensagem(
                atendimento, payloadMensagem, whatsappMessageId, dados
        ));
        log.info("Mensagem persistida no banco de dados. id={}, whatsappMessageId={}", mensagem.getId(), whatsappMessageId);
 
        if (dados.mediaId() != null) {
            midiaRepository.save(criarMidia(mensagem, dados));
        }
 
        atualizarConversa(atendimento, paciente, mensagem);
        emitirEventos(clinica, pacienteResolvido, atendimento, paciente, mensagem, payloadMetaOriginal);
        notificationService.notificarNovaMensagem(atendimento, mensagem);
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
        String novoStatus = String.valueOf(status.get("status")).toUpperCase();
        OffsetDateTime dataHora = parseTimestamp(String.valueOf(status.get("timestamp")));
        return mensagemRepository.findByClinicaIdAndWhatsappMessageId(
                clinica.get().getId(), whatsappMessageId
        ).map(mensagem -> {
            mensagem.setWhatsappStatus(novoStatus);
            if ("DELIVERED".equals(novoStatus) || "ENTREGUE".equals(novoStatus)) {
                mensagem.setEntregueEm(dataHora);
            }
            if ("READ".equals(novoStatus) || "LIDA".equals(novoStatus)) {
                mensagem.setLidaEm(dataHora);
            }
            Mensagem salva = mensagemRepository.save(mensagem);
            log.info("Status da mensagem atualizado: id={}, whatsappMessageId={}, novoStatus={}", 
                    salva.getId(), whatsappMessageId, novoStatus);
            return salva;
        });
    }
 
    @SuppressWarnings("unchecked")
    private Optional<EntradaResolvida> resolverEntrada(Map<String, Object> value) {
        List<Map<String, Object>> contatos = (List<Map<String, Object>>) value.get("contacts");
        List<Map<String, Object>> mensagens = (List<Map<String, Object>>) value.get("messages");
        if (contatos == null || contatos.isEmpty() || mensagens == null || mensagens.isEmpty()) {
            log.warn("Payload WhatsApp sem contato ou mensagem");
            return Optional.empty();
        }
        return resolverClinicaPorPayload(value)
                .map(clinica -> new EntradaResolvida(clinica, contatos.getFirst(), mensagens.getFirst()));
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
 
    private MidiaMensagem criarMidia(Mensagem mensagem, DadosMensagem dados) {
        MidiaMensagem midia = new MidiaMensagem();
        midia.setMensagem(mensagem);
        midia.setTipoMedia(dados.tipoMedia());
        midia.setMimeType(dados.mimeType());
        midia.setNomeArquivo(dados.nomeArquivo());
        midia.setTamanhoBytes(0L);
        midia.setWhatsappMediaId(dados.mediaId());
        persistirBinarioRecebido(midia, dados.mediaId());
        return midia;
    }

    private void persistirBinarioRecebido(MidiaMensagem midia, String mediaId) {
        if (mediaId == null || mediaId.isBlank()) {
            return;
        }
        try {
            MidiaBaixada baixada = whatsappOutboundClient.baixarMidia(mediaId);
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

    private void emitirN8nMensagemRecebida(
            Clinica clinica,
            Paciente paciente,
            Atendimento atendimento,
            Mensagem mensagem,
            byte[] payloadMetaOriginal
    ) {
        if (payloadMetaOriginal != null && payloadMetaOriginal.length > 0) {
            n8nEventService.enviarPayloadMetaOriginal(
                    clinica,
                    payloadMetaOriginal,
                    new N8nEventService.MetaWebhookContext(
                            "mensagem_recebida",
                            atendimento.getId(),
                            paciente.getId(),
                            mensagem.getId()
                    )
            );
            return;
        }
        n8nEventService.emitir(n8nEventService.criarPayloadMensagemRecebida(
                clinica,
                paciente,
                atendimento,
                mensagem
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
                    || phoneNumberId.equals(envAppClinicPhoneId);

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
                         "Não coincide com as envs (resolved={}, env={}, meta={}, appClinic={}). " +
                         "Clínica fallback considerada: '{}'. Motivo: ID recebido não coincide com nenhum ID configurado no ambiente.",
                         maskId(phoneNumberId), maskId(resolvedPhoneId), maskId(envWhatsappPhoneId),
                         maskId(envMetaWhatsappPhoneId), maskId(envAppClinicPhoneId), clinicSlug);
            }
        }
        return clinica;
    }
 
    private PacienteResolvido resolverOuCriarPaciente(
            Clinica clinica,
            String telefone,
            Map<String, Object> contato
    ) {
        Optional<Paciente> existente = pacienteRepository.findByClinicaIdAndTelefoneNormalizado(
                clinica.getId(), telefone
        );
        if (existente.isPresent()) return new PacienteResolvido(existente.get(), false);
        return new PacienteResolvido(
                pacienteRepository.save(criarPaciente(clinica, telefone, contato)),
                true
        );
    }
 
    @SuppressWarnings("unchecked")
    private Paciente criarPaciente(Clinica clinica, String telefone, Map<String, Object> contato) {
        Map<String, Object> perfil = (Map<String, Object>) contato.get("profile");
        String nome = perfil == null ? "Contato WhatsApp" : String.valueOf(perfil.get("name"));
        Paciente paciente = new Paciente();
        paciente.setClinica(clinica);
        paciente.setNome(nome);
        paciente.setNomeBusca(nome.toUpperCase());
        paciente.setTelefone("+" + telefone);
        paciente.setTelefoneNormalizado(telefone);
        paciente.setExternalSource(ExternalProviderType.WHATSAPP);
        paciente.setExternalId(telefone);
        paciente.setStatus("EM_ATENDIMENTO");
        return paciente;
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
 
    private OffsetDateTime parseTimestamp(String timestamp) {
        return OffsetDateTime.ofInstant(
                Instant.ofEpochSecond(Long.parseLong(timestamp)),
                ZoneOffset.UTC
            );
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
 
    private record EntradaResolvida(
            Clinica clinica,
            Map<String, Object> contato,
            Map<String, Object> mensagem
    ) {
    }
 
    private record PacienteResolvido(Paciente paciente, boolean criado) {
    }
}
