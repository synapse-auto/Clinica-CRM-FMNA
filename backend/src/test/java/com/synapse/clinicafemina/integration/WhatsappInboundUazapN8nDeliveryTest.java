package com.synapse.clinicafemina.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Mensagem;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.integration.whatsapp.config.WhatsappProperties;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.ClinicaRepository;
import com.synapse.clinicafemina.repository.MensagemRepository;
import com.synapse.clinicafemina.repository.MidiaMensagemRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import com.synapse.clinicafemina.service.AtendimentoNotificationService;
import com.synapse.clinicafemina.service.AtendimentoService;
import com.synapse.clinicafemina.service.HorarioIaService;
import com.synapse.clinicafemina.service.N8nEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regressão do bug N8N da FMNA (formato UAZAP, envelope Meta-compatible): mensagens grandes,
 * lotes, deduplicação, mensagem malformada isolada, falha de mídia e bloqueio por modo humano.
 * Usa o {@link WhatsappInboundPayloadParser} e {@link ObjectMapper} reais para exercitar de fato
 * a limitação Unicode-safe da prévia e a montagem do payload unitário enviado ao N8N.
 *
 * <p>A chamada HTTP ao N8N em si só acontece em {@code N8nMensagemRecebidaEventListener}
 * (AFTER_COMMIT) — sem contexto Spring/transação real aqui, estes testes verificam que o
 * {@link N8nMensagemRecebidaEvent} correto foi publicado (o teste transacional real com banco
 * de dados e transaction manager reais fica em
 * {@code WhatsappInboundN8nAfterCommitIntegrationTest}).</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WhatsappInboundMapper — entrega ao N8N (formato UAZAP)")
class WhatsappInboundUazapN8nDeliveryTest {

    private static final String PHONE_ID = "uazap-fmna";
    private static final String WA_ID = "554391241788";
    private static final byte[] RAW_BODY = "{\"entry\":[{\"changes\":[{\"field\":\"messages\"}]}]}"
            .getBytes(StandardCharsets.UTF_8);

    @Mock private PacienteRepository pacienteRepository;
    @Mock private AtendimentoRepository atendimentoRepository;
    @Mock private MensagemRepository mensagemRepository;
    @Mock private MidiaMensagemRepository midiaRepository;
    @Mock private ClinicaRepository clinicaRepository;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private N8nEventService n8nEventService;
    @Mock private HorarioIaService horarioIaService;
    @Mock private AtendimentoNotificationService notificationService;
    @Mock private AtendimentoService atendimentoService;
    @Mock private Environment environment;
    @Mock private WhatsappOutboundClient whatsappOutboundClient;
    @Mock private ApplicationEventPublisher eventPublisher;

    private WhatsappInboundMapper mapper;
    private Clinica clinica;
    private Paciente paciente;
    private Atendimento atendimento;

    @BeforeEach
    void setUp() {
        WhatsappProperties whatsappProperties = new WhatsappProperties();
        whatsappProperties.getUazap().setPhoneNumberId(PHONE_ID);

        mapper = new WhatsappInboundMapper(
                pacienteRepository, atendimentoRepository, mensagemRepository, midiaRepository,
                clinicaRepository, rabbitTemplate, n8nEventService, horarioIaService,
                notificationService, new ObjectMapper(), new WhatsappInboundPayloadParser(),
                environment, whatsappOutboundClient,
                List.of(
                        new com.synapse.clinicafemina.integration.whatsapp.meta.MetaWhatsappMediaDownloader(
                                whatsappOutboundClient, whatsappProperties),
                        new com.synapse.clinicafemina.integration.whatsapp.uazap.UazapWhatsappMediaDownloader(
                                whatsappProperties)),
                eventPublisher, whatsappProperties,
                new com.synapse.clinicafemina.service.WhatsappPhoneIdentityService(
                        pacienteRepository, atendimentoRepository, mensagemRepository),
                atendimentoService);

        clinica = new Clinica();
        clinica.setId(5L);
        clinica.setSlug("fmna");
        clinica.setWhatsappPhoneNumberId(PHONE_ID);
        clinica.setUsaN8n(true);
        clinica.setN8nWebhookUrl("https://n8n.example/webhook");

        paciente = new Paciente();
        paciente.setId(20L);
        paciente.setClinica(clinica);
        paciente.setNomeBusca("PACIENTE");
        paciente.setTelefoneNormalizado(WA_ID);

        atendimento = new Atendimento();
        atendimento.setId(30L);
        atendimento.setClinica(clinica);
        atendimento.setPaciente(paciente);
        atendimento.setNaoLidas(0);
        atendimento.setTratadoPorIa(true);

        lenient().when(clinicaRepository.findByWhatsappPhoneNumberId(PHONE_ID)).thenReturn(Optional.of(clinica));
        lenient().when(pacienteRepository.findByClinicaIdAndTelefoneNormalizado(5L, WA_ID))
                .thenReturn(Optional.of(paciente));
        lenient().when(atendimentoRepository.findAtivo(5L, 20L)).thenReturn(Optional.of(atendimento));
        lenient().when(atendimentoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(pacienteRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(horarioIaService.avaliar(any(Clinica.class)))
                .thenReturn(new HorarioIaService.HorarioIaStatus(true, HorarioIaService.DENTRO_HORARIO));
    }

    private Map<String, Object> textMessage(String id, String body) {
        return Map.of("id", id, "from", WA_ID, "timestamp", "1781455200", "type", "text",
                "text", Map.of("body", body));
    }

    private Map<String, Object> value(List<Map<String, Object>> messages) {
        return Map.of(
                "metadata", Map.of("phone_number_id", PHONE_ID),
                "contacts", List.of(Map.of("wa_id", WA_ID, "profile", Map.of("name", "Paciente Teste"))),
                "messages", messages);
    }

    private void stubSave(long id) {
        when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(eq(5L), any())).thenReturn(Optional.empty());
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(i -> {
            Mensagem m = i.getArgument(0);
            m.setId(id);
            return m;
        });
    }

    private N8nMensagemRecebidaEvent capturarUnicoEventoN8n() {
        ArgumentCaptor<N8nMensagemRecebidaEvent> captor = ArgumentCaptor.forClass(N8nMensagemRecebidaEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    private List<N8nMensagemRecebidaEvent> capturarEventosN8n(int quantidade) {
        ArgumentCaptor<N8nMensagemRecebidaEvent> captor = ArgumentCaptor.forClass(N8nMensagemRecebidaEvent.class);
        verify(eventPublisher, times(quantidade)).publishEvent(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("mensagem UAZAP longa (2000 chars) é persistida integralmente, prévia cabe na coluna e evento N8N leva o conteúdo integral")
    void longUazapMessage_persistedFully_previewWithinColumn_forwardedFullyToN8n() {
        String corpoAscii = "Detalhamento clínico ".repeat(100); // ~2100 chars, com acentos
        String corpoLongo = corpoAscii + "fim-do-texto-😀";
        stubSave(40L);

        mapper.processarMensagemTexto(value(List.of(textMessage("UZ-1", corpoLongo))), RAW_BODY);

        ArgumentCaptor<Mensagem> mensagemCaptor = ArgumentCaptor.forClass(Mensagem.class);
        verify(mensagemRepository).save(mensagemCaptor.capture());
        N8nMensagemRecebidaEvent evento = capturarUnicoEventoN8n();

        Mensagem salva = mensagemCaptor.getValue();
        assertThat(salva.getConteudo()).isEqualTo(corpoLongo); // conteúdo persistido íntegro (com emoji)
        assertThat(salva.getConteudoPrevia().codePointCount(0, salva.getConteudoPrevia().length()))
                .isLessThanOrEqualTo(60);
        // O emoji é escapado em JSON como 😀 (sem perda); o corpo grande ASCII/acentuado
        // aparece verbatim — prova que o conteúdo integral é embutido no evento pré-commit.
        String payloadN8n = new String(evento.payloadMetaOriginal(), StandardCharsets.UTF_8);
        assertThat(payloadN8n).contains("UZ-1");
        assertThat(payloadN8n).contains(corpoAscii);
    }

    @Test
    @DisplayName("mensagem com emoji na região do corte é persistida sem falha (não corrompe UTF-8)")
    void messageWithEmojiAtPreviewCut_persistsWithoutFailure() {
        String corpo = "a".repeat(56) + "😀".repeat(20);
        stubSave(41L);

        mapper.processarMensagemTexto(value(List.of(textMessage("UZ-emoji", corpo))), RAW_BODY);

        ArgumentCaptor<Mensagem> captor = ArgumentCaptor.forClass(Mensagem.class);
        verify(mensagemRepository).save(captor.capture());
        String previa = captor.getValue().getConteudoPrevia();
        // Sem surrogate solto: nenhum high surrogate sem o low seguinte.
        for (int i = 0; i < previa.length(); i++) {
            if (Character.isHighSurrogate(previa.charAt(i))) {
                assertThat(i + 1).isLessThan(previa.length());
                assertThat(Character.isLowSurrogate(previa.charAt(i + 1))).isTrue();
                i++;
            } else {
                assertThat(Character.isLowSurrogate(previa.charAt(i))).isFalse();
            }
        }
        capturarUnicoEventoN8n();
    }

    @Test
    @DisplayName("lote com três mensagens gera três persistências e três eventos N8N, cada um com seu id")
    void batchOfThree_producesThreeSavesAndThreeEmissions() {
        when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(eq(5L), any())).thenReturn(Optional.empty());
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(i -> {
            Mensagem m = i.getArgument(0);
            m.setId(switch (m.getWhatsappMessageId()) {
                case "UZ-1" -> 40L;
                case "UZ-2" -> 41L;
                case "UZ-3" -> 42L;
                default -> throw new IllegalArgumentException("id inesperado");
            });
            return m;
        });

        mapper.processarMensagemTexto(value(List.of(
                textMessage("UZ-1", "primeira"),
                textMessage("UZ-2", "segunda"),
                textMessage("UZ-3", "terceira"))), RAW_BODY);

        ArgumentCaptor<Mensagem> mensagemCaptor = ArgumentCaptor.forClass(Mensagem.class);
        verify(mensagemRepository, times(3)).save(mensagemCaptor.capture());
        List<N8nMensagemRecebidaEvent> eventos = capturarEventosN8n(3);

        assertThat(mensagemCaptor.getAllValues().stream().map(Mensagem::getWhatsappMessageId).toList())
                .containsExactly("UZ-1", "UZ-2", "UZ-3");
        List<String> payloads = eventos.stream()
                .map(e -> new String(e.payloadMetaOriginal(), StandardCharsets.UTF_8)).toList();
        assertThat(payloads.get(0)).contains("UZ-1");
        assertThat(payloads.get(0)).doesNotContain("UZ-2");
        assertThat(payloads.get(1)).contains("UZ-2");
        assertThat(payloads.get(2)).contains("UZ-3");
    }

    @Test
    @DisplayName("retry do mesmo whatsappMessageId não persiste nem publica evento novamente")
    void retrySameId_doesNotDuplicate() {
        when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(5L, "UZ-1"))
                .thenReturn(Optional.empty(), Optional.of(existente("UZ-1")));
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(i -> {
            Mensagem m = i.getArgument(0);
            m.setId(40L);
            return m;
        });

        mapper.processarMensagemTexto(value(List.of(textMessage("UZ-1", "ola"))), RAW_BODY);
        mapper.processarMensagemTexto(value(List.of(textMessage("UZ-1", "ola"))), RAW_BODY);

        verify(mensagemRepository, times(1)).save(any(Mensagem.class));
        capturarEventosN8n(1);
    }

    @Test
    @DisplayName("mensagens com mesmo texto e ids diferentes são processadas separadamente (não deduplica por texto)")
    void sameTextDifferentIds_processedSeparately() {
        when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(eq(5L), any())).thenReturn(Optional.empty());
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(i -> {
            Mensagem m = i.getArgument(0);
            m.setId(m.getWhatsappMessageId().equals("UZ-1") ? 40L : 41L);
            return m;
        });

        mapper.processarMensagemTexto(value(List.of(
                textMessage("UZ-1", "texto identico"),
                textMessage("UZ-2", "texto identico"))), RAW_BODY);

        verify(mensagemRepository, times(2)).save(any(Mensagem.class));
        capturarEventosN8n(2);
    }

    @Test
    @DisplayName("mensagem malformada em um lote não impede a mensagem válida vizinha")
    void malformedMessage_doesNotBlockValidSibling() {
        when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(eq(5L), any())).thenReturn(Optional.empty());
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(i -> {
            Mensagem m = i.getArgument(0);
            m.setId(40L);
            return m;
        });

        // 2ª mensagem malformada: "text" é uma String (não um objeto) → ClassCastException no parser.
        Map<String, Object> malformada = Map.of(
                "id", "UZ-bad", "from", WA_ID, "timestamp", "1781455200", "type", "text",
                "text", "isto-deveria-ser-um-objeto");

        mapper.processarMensagemTexto(value(List.of(
                textMessage("UZ-ok", "mensagem valida"),
                malformada)), RAW_BODY);

        ArgumentCaptor<Mensagem> captor = ArgumentCaptor.forClass(Mensagem.class);
        verify(mensagemRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getWhatsappMessageId()).isEqualTo("UZ-ok");
        capturarEventosN8n(1);
    }

    @Test
    @DisplayName("falha de mídia (download indisponível) não bloqueia persistência nem publicação do evento N8N")
    void mediaFailure_doesNotBlockPersistenceOrN8n() {
        when(mensagemRepository.findByClinicaIdAndWhatsappMessageId(eq(5L), any())).thenReturn(Optional.empty());
        when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(i -> {
            Mensagem m = i.getArgument(0);
            m.setId(40L);
            return m;
        });

        // UAZAP: o downloader retorna null (segundo hop não confirmado) → mídia pendente, mensagem segue.
        Map<String, Object> comMidia = Map.of(
                "id", "UZ-audio", "from", WA_ID, "timestamp", "1781455200", "type", "audio",
                "audio", Map.of("id", "media-uz", "mime_type", "audio/ogg"));

        mapper.processarMensagemTexto(value(List.of(comMidia)), RAW_BODY);

        ArgumentCaptor<com.synapse.clinicafemina.domain.MidiaMensagem> midiaCaptor =
                ArgumentCaptor.forClass(com.synapse.clinicafemina.domain.MidiaMensagem.class);
        verify(midiaRepository).save(midiaCaptor.capture());
        assertThat(midiaCaptor.getValue().getTamanhoBytes()).isZero();
        assertThat(midiaCaptor.getValue().getWhatsappMediaId()).isEqualTo("media-uz");
        capturarUnicoEventoN8n();
    }

    @Test
    @DisplayName("modo humano bloqueia intencionalmente a publicação do evento N8N (não é falha de encaminhamento)")
    void humanMode_intentionallyBlocksN8n() {
        atendimento.setTratadoPorIa(false);
        stubSave(40L);

        mapper.processarMensagemTexto(value(List.of(textMessage("UZ-1", "mensagem em modo humano"))), RAW_BODY);

        verify(mensagemRepository).save(any(Mensagem.class)); // persistida no CRM
        verify(eventPublisher, never()).publishEvent(any(N8nMensagemRecebidaEvent.class)); // bloqueio intencional
    }

    @Test
    @DisplayName("timestamp malformado não descarta a mensagem — persiste com horário de fallback")
    void malformedTimestamp_stillPersistsMessage() {
        stubSave(40L);
        Map<String, Object> comTimestampRuim = Map.of(
                "id", "UZ-ts", "from", WA_ID, "timestamp", "nao-e-numero", "type", "text",
                "text", Map.of("body", "mensagem com timestamp invalido"));

        mapper.processarMensagemTexto(value(List.of(comTimestampRuim)), RAW_BODY);

        ArgumentCaptor<Mensagem> captor = ArgumentCaptor.forClass(Mensagem.class);
        verify(mensagemRepository).save(captor.capture());
        assertThat(captor.getValue().getDataHora()).isNotNull();
        capturarUnicoEventoN8n();
    }

    private Mensagem existente(String whatsappMessageId) {
        Mensagem m = new Mensagem();
        m.setId(99L);
        m.setAtendimento(atendimento);
        m.setWhatsappMessageId(whatsappMessageId);
        m.setTipoMedia("TEXTO");
        return m;
    }
}
