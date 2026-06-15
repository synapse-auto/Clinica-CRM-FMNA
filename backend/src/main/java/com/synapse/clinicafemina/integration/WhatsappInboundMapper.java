package com.synapse.clinicafemina.integration;

import com.synapse.clinicafemina.config.RabbitMQConfig;
import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Mensagem;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import com.synapse.clinicafemina.messaging.MensagemEntradaEvent;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.ClinicaRepository;
import com.synapse.clinicafemina.repository.MensagemRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import com.synapse.clinicafemina.service.N8nEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Processa payloads inbound da Meta WhatsApp Cloud API.
 *
 * Implementa o fluxo descrito em {@code whatsapp.md}:
 * <ol>
 *   <li>Resolve ou cria {@link Paciente} por {@code wa_id} (E.164).</li>
 *   <li>Resolve {@link Atendimento} ativo ou cria nova sessão
 *       (se último encerrado há > 24h).</li>
 *   <li>Persiste {@link Mensagem} com conteúdo criptografado.</li>
 *   <li>Publica {@link MensagemEntradaEvent} no RabbitMQ.</li>
 * </ol>
 *
 * O payload bruto já foi validado (assinatura HMAC) pelo controller antes desta chamada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsappInboundMapper {

    private static final long SESSAO_TIMEOUT_HORAS = 24;

    private final PacienteRepository pacienteRepository;
    private final AtendimentoRepository atendimentoRepository;
    private final MensagemRepository mensagemRepository;
    private final ClinicaRepository clinicaRepository;
    private final RabbitTemplate rabbitTemplate;
    private final N8nEventService n8nEventService;

    // ─── Mensagem de texto inbound ────────────────────────────────────────

    @Transactional
    public void processarMensagemTexto(Map<String, Object> value) {
        // Extrai contatos e mensagem do payload Meta
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> contacts =
                (List<Map<String, Object>>) value.get("contacts");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages =
                (List<Map<String, Object>>) value.get("messages");

        if (contacts == null || messages == null || messages.isEmpty()) {
            log.warn("Payload sem contacts ou messages — ignorado");
            return;
        }

        Optional<Clinica> clinicaOpt = resolverClinicaPorPayload(value);
        if (clinicaOpt.isEmpty()) {
            return;
        }
        Clinica clinica = clinicaOpt.get();

        Map<String, Object> contact = contacts.getFirst();
        Map<String, Object> msg    = messages.getFirst();

        String waId      = (String) contact.get("wa_id");
        String wamid     = (String) msg.get("id");
        String timestamp = (String) msg.get("timestamp");
        @SuppressWarnings("unchecked")
        String body      = ((Map<String, String>) msg.get("text")).get("body");

        // Normaliza E.164 (remove '+' caso presente)
        String telefoneNormalizado = waId.startsWith("+") ? waId.substring(1) : waId;

        // Idempotência — ignora mensagem já processada
        if (mensagemRepository.findByClinicaIdAndWhatsappMessageId(clinica.getId(), wamid).isPresent()) {
            log.debug("Mensagem WhatsApp ja processada para clinica={} — skip", clinica.getId());
            return;
        }

        // 1. Resolve ou cria paciente
        PacienteResolvido pacienteResolvido = resolverOuCriarPaciente(clinica, telefoneNormalizado, contact);
        Paciente paciente = pacienteResolvido.paciente();

        // 2. Resolve ou cria atendimento
        Atendimento atendimento = resolverOuCriarAtendimento(clinica, paciente);

        // 3. Persiste mensagem
        Mensagem mensagem = new Mensagem();
        mensagem.setAtendimento(atendimento);
        mensagem.setDirecao("ENTRADA");
        mensagem.setRemetente("PACIENTE");
        mensagem.setTipoMedia("TEXTO");
        mensagem.setConteudo(body);                               // criptografado pelo converter JPA
        mensagem.setConteudoPrevia(body.length() > 60 ? body.substring(0, 60) + "…" : body);
        mensagem.setWhatsappMessageId(wamid);
        mensagem.setWhatsappStatus("RECEBIDA");
        mensagem.setDataHora(parseTimestamp(timestamp));
        mensagem = mensagemRepository.save(mensagem);

        // 4. Atualiza contadores do atendimento
        atendimento.setNaoLidas(atendimento.getNaoLidas() + 1);
        atendimento.setUltimaMensagemEm(mensagem.getDataHora());
        paciente.setUltimaInteracaoEm(mensagem.getDataHora());
        atendimentoRepository.save(atendimento);
        pacienteRepository.save(paciente);

        if (pacienteResolvido.criado()) {
            n8nEventService.emitir(n8nEventService.criarPayload(
                    clinica,
                    "novo_lead",
                    paciente.getId(),
                    atendimento.getId(),
                    null,
                    paciente.getTelefoneNormalizado()
            ));
        }

        // 5. Publica evento RabbitMQ
        Long atendenteId = atendimento.getAtendentePrincipal() != null
                ? atendimento.getAtendentePrincipal().getId() : null;

        MensagemEntradaEvent event = new MensagemEntradaEvent(
                atendimento.getId(),
                clinica.getId(),
                paciente.getId(),
                paciente.getNomeBusca(),
                mensagem.getId(),
                "TEXTO",
                mensagem.getConteudoPrevia(),
                mensagem.getDataHora(),
                atendimento.getNaoLidas(),
                atendenteId
        );

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_MENSAGEM_ENTRADA,
                RabbitMQConfig.ROUTING_KEY_MENSAGEM_ENTRADA,
                event);

        n8nEventService.emitir(n8nEventService.criarPayload(
                clinica,
                "nova_mensagem",
                paciente.getId(),
                atendimento.getId(),
                null,
                paciente.getTelefoneNormalizado()
        ));

        log.info("Mensagem inbound processada: atendimento={}", atendimento.getId());
    }

    // ─── Status update (entregue/lida) ────────────────────────────────────

    @Transactional
    public Optional<Mensagem> processarStatusUpdate(Map<String, Object> value, Map<String, Object> status) {
        Optional<Clinica> clinicaOpt = resolverClinicaPorPayload(value);
        if (clinicaOpt.isEmpty()) {
            return Optional.empty();
        }
        Clinica clinica = clinicaOpt.get();

        String wamid     = (String) status.get("id");
        String newStatus = ((String) status.get("status")).toUpperCase();
        String timestamp = (String) status.get("timestamp");

        return mensagemRepository.findByClinicaIdAndWhatsappMessageId(clinica.getId(), wamid).map(m -> {
            m.setWhatsappStatus(newStatus);
            OffsetDateTime dt = parseTimestamp(timestamp);
            if ("DELIVERED".equals(newStatus) || "ENTREGUE".equals(newStatus)) m.setEntregueEm(dt);
            if ("READ".equals(newStatus)      || "LIDA".equals(newStatus))      m.setLidaEm(dt);
            return mensagemRepository.save(m);
        });
    }

    // ─── Helpers privados ─────────────────────────────────────────────────

    private Optional<String> extrairPhoneNumberId(Map<String, Object> value) {
        Object metadata = value.get("metadata");
        if (!(metadata instanceof Map<?, ?> metadataMap)) {
            return Optional.empty();
        }
        Object phoneNumberId = metadataMap.get("phone_number_id");
        if (!(phoneNumberId instanceof String id) || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(id);
    }

    private Optional<Clinica> resolverClinicaPorPayload(Map<String, Object> value) {
        Optional<String> phoneNumberId = extrairPhoneNumberId(value);
        if (phoneNumberId.isEmpty()) {
            log.warn("Payload WhatsApp sem phone_number_id — ignorado");
            return Optional.empty();
        }

        Optional<Clinica> clinica = clinicaRepository.findByWhatsappPhoneNumberId(phoneNumberId.get());
        if (clinica.isEmpty()) {
            log.warn("Payload WhatsApp com phone_number_id nao configurado — ignorado");
        }
        return clinica;
    }

    private PacienteResolvido resolverOuCriarPaciente(Clinica clinica, String telefoneNormalizado,
                                                      Map<String, Object> contact) {
        Optional<Paciente> existente = pacienteRepository
                .findByClinicaIdAndTelefoneNormalizado(clinica.getId(), telefoneNormalizado);
        if (existente.isPresent()) {
            return new PacienteResolvido(existente.get(), false);
        }

        Paciente novoPaciente = pacienteRepository.save(criarPaciente(clinica, telefoneNormalizado, contact));
        return new PacienteResolvido(novoPaciente, true);
    }

    private Paciente criarPaciente(Clinica clinica, String telefoneNormalizado, Map<String, Object> contact) {
        @SuppressWarnings("unchecked")
        String nome = ((Map<String, String>) contact.get("profile")).get("name");
        Paciente p = new Paciente();
        p.setClinica(clinica);
        p.setNome(nome);                          // criptografado pelo converter
        p.setNomeBusca(nome.toUpperCase());       // campo de busca em clear text
        p.setTelefone("+" + telefoneNormalizado); // criptografado pelo converter
        p.setTelefoneNormalizado(telefoneNormalizado);
        p.setExternalSource(ExternalProviderType.WHATSAPP);
        p.setExternalId(telefoneNormalizado);
        p.setStatus("EM_ATENDIMENTO");
        log.info("Novo paciente criado via WhatsApp para clinica={}", clinica.getId());
        return p;
    }

    private Atendimento resolverOuCriarAtendimento(Clinica clinica, Paciente paciente) {
        // Verifica se há atendimento ativo
        Optional<Atendimento> ativo =
                atendimentoRepository.findAtivo(clinica.getId(), paciente.getId());
        if (ativo.isPresent()) return ativo.get();

        // Verifica se o último foi encerrado há menos de 24h (reutiliza sessão)
        OffsetDateTime limite = OffsetDateTime.now().minusHours(SESSAO_TIMEOUT_HORAS);
        boolean recenteEncerrado = atendimentoRepository
                .existeEncerradoDesde(clinica.getId(), paciente.getId(), limite);

        if (recenteEncerrado) {
            // Reabre o atendimento mais recente
            return atendimentoRepository.findUltimo(clinica.getId(), paciente.getId())
                    .map(a -> {
                        a.setStatus("ATIVO");
                        a.setDataEncerramento(null);
                        return atendimentoRepository.save(a);
                    }).orElseGet(() -> criarAtendimento(clinica, paciente));
        }

        return criarAtendimento(clinica, paciente);
    }

    private Atendimento criarAtendimento(Clinica clinica, Paciente paciente) {
        Atendimento a = new Atendimento();
        a.setClinica(clinica);
        a.setPaciente(paciente);
        a.setStatus("ATIVO");
        a.setTratadoPorIa(true);  // começa com IA até um humano assumir
        a.setNaoLidas(0);
        log.info("Novo atendimento criado para paciente {}", paciente.getId());
        return atendimentoRepository.save(a);
    }

    private OffsetDateTime parseTimestamp(String unixTimestamp) {
        return OffsetDateTime.ofInstant(
                Instant.ofEpochSecond(Long.parseLong(unixTimestamp)), ZoneOffset.UTC);
    }

    private record PacienteResolvido(Paciente paciente, boolean criado) {
    }
}
