package com.synapse.clinicafemina.integration;

import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Mensagem;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.ClinicaRepository;
import com.synapse.clinicafemina.repository.MensagemRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import com.synapse.clinicafemina.service.AtendimentoNotificationService;
import com.synapse.clinicafemina.service.N8nEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Prova REAL (contexto Spring completo, transaction manager real, banco H2 real) de que:
 * <ul>
 *   <li>cada mensagem é processada em transação própria ({@code REQUIRES_NEW}), então uma
 *       mensagem malformada no meio de um lote não derruba as vizinhas válidas;</li>
 *   <li>a chamada ao N8N só acontece {@code @TransactionalEventListener(phase = AFTER_COMMIT)},
 *       ou seja, depois que a mensagem já está de fato commitada no PostgreSQL (verificado
 *       consultando o banco a partir de outro ponto de entrada, no exato momento da chamada);</li>
 *   <li>uma falha DEPOIS do save mas ANTES do fim do método transacional reverte a mensagem e
 *       nunca dispara o listener;</li>
 *   <li>retry do mesmo whatsappMessageId não duplica nem republica;</li>
 *   <li>modo humano persiste a mensagem mas não publica evento N8N.</li>
 * </ul>
 * Mocks não bastam para provar {@code REQUIRES_NEW}/{@code AFTER_COMMIT} — por isso este teste
 * sobe o contexto Spring de verdade e só troca {@link N8nEventService} (chamada HTTP externa) e,
 * no cenário de rollback, {@link AtendimentoNotificationService} (gatilho de falha controlada).
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.clinic.slug=n8n-after-commit-test"
})
class WhatsappInboundN8nAfterCommitIntegrationTest {

    private static final long TIMEOUT_SEGUNDOS = 5;

    @Autowired
    private WhatsappInboundMapper mapper;

    @Autowired
    private ClinicaRepository clinicaRepository;

    @Autowired
    private MensagemRepository mensagemRepository;

    @Autowired
    private AtendimentoRepository atendimentoRepository;

    @Autowired
    private PacienteRepository pacienteRepository;

    @MockBean
    private N8nEventService n8nEventService;

    @MockBean
    private AtendimentoNotificationService notificationService;

    private Clinica clinica;
    private String phoneNumberId;

    @BeforeEach
    void setUp() {
        reset(n8nEventService, notificationService);
        phoneNumberId = "n8n-commit-" + UUID.randomUUID();

        clinica = new Clinica();
        clinica.setNome("Clinica N8N After Commit");
        clinica.setSlug("n8n-commit-" + UUID.randomUUID());
        clinica.setRazaoSocial("Clinica N8N After Commit LTDA");
        clinica.setCnpj(UUID.randomUUID().toString().substring(0, 18));
        clinica.setEmailContato("n8n-commit-" + UUID.randomUUID() + "@clinica.local");
        clinica.setTelefoneContato("44999999999");
        clinica.setWhatsappPhoneNumberId(phoneNumberId);
        clinica.setUsaN8n(true);
        clinica.setN8nWebhookUrl("https://n8n.example/webhook");
        clinica = clinicaRepository.save(clinica);
    }

    private Map<String, Object> textMessage(String id, String waId, String body) {
        return Map.of("id", id, "from", waId, "timestamp", "1781455200", "type", "text",
                "text", Map.of("body", body));
    }

    private Map<String, Object> value(String waId, List<Map<String, Object>> messages) {
        return Map.of(
                "metadata", Map.of("phone_number_id", phoneNumberId),
                "contacts", List.of(Map.of("wa_id", waId, "profile", Map.of("name", "Paciente Teste"))),
                "messages", messages);
    }

    private static byte[] rawBody() {
        return "x".getBytes();
    }

    private Optional<Mensagem> buscarMensagem(String whatsappMessageId) {
        return mensagemRepository.findByClinicaIdAndWhatsappMessageId(clinica.getId(), whatsappMessageId);
    }

    // ── Cenário A: isolamento do lote (1 válida, 1 inválida, 1 válida) ─────────────────────────

    @Test
    @DisplayName("cenário A: lote com 1 válida + 1 inválida + 1 válida — só as válidas persistem e emitem, isoladas por transação")
    void scenarioA_batchWithInvalidMessage_isolatesFailureKeepsValidSiblings() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        doAnswer(inv -> {
            latch.countDown();
            return null;
        }).when(n8nEventService).enviarPayloadMetaOriginal(any(), any(), any());

        String waId = "5511900000001";
        Map<String, Object> valido1 = textMessage("REAL-A1", waId, "primeira valida");
        // malformada: "text" é String, não Map — dispara ClassCastException dentro da transação isolada.
        Map<String, Object> invalido = Map.of(
                "id", "REAL-A2", "from", waId, "timestamp", "1781455200", "type", "text",
                "text", "isto-deveria-ser-um-objeto");
        Map<String, Object> valido2 = textMessage("REAL-A3", waId, "terceira valida");

        mapper.processarMensagemTexto(value(waId, List.of(valido1, invalido, valido2)), rawBody());

        assertTrue(latch.await(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS), "esperava 2 chamadas N8N (mensagens válidas)");
        assertTrue(buscarMensagem("REAL-A1").isPresent(), "primeira mensagem válida deve persistir");
        assertTrue(buscarMensagem("REAL-A2").isEmpty(), "mensagem malformada nunca deve persistir");
        assertTrue(buscarMensagem("REAL-A3").isPresent(), "terceira mensagem válida deve persistir mesmo após a falha da segunda");
        verify(n8nEventService, times(2)).enviarPayloadMetaOriginal(any(), any(), any());
    }

    // ── Cenário B: AFTER_COMMIT real — visível no banco no momento exato da chamada N8N ────────

    @Test
    @DisplayName("cenário B: no momento em que o N8N é chamado, a mensagem já está commitada e visível via outro ponto de acesso ao banco")
    void scenarioB_n8nCalledAfterCommit_messageAlreadyVisibleInDatabase() throws InterruptedException {
        String waId = "5511900000002";
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean visivelNoMomentoDaChamada = new AtomicBoolean(false);
        doAnswer(inv -> {
            // "outro contexto": nova consulta ao repositório, fora da transação REQUIRES_NEW original
            // (que já terminou — estamos na thread assíncrona do listener, pós-commit).
            visivelNoMomentoDaChamada.set(buscarMensagem("REAL-B1").isPresent());
            latch.countDown();
            return null;
        }).when(n8nEventService).enviarPayloadMetaOriginal(any(), any(), any());

        mapper.processarMensagemTexto(value(waId, List.of(textMessage("REAL-B1", waId, "mensagem commitada"))), rawBody());

        assertTrue(latch.await(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS), "N8N deveria ter sido chamado");
        assertTrue(visivelNoMomentoDaChamada.get(),
                "a mensagem já deveria estar commitada e visível no banco no momento exato da chamada N8N");
    }

    // ── Cenário C: rollback — falha depois do save, antes do fim da transação ──────────────────

    @Test
    @DisplayName("cenário C: falha após o save mas antes do commit reverte a mensagem e nunca chama o listener/N8N")
    void scenarioC_failureAfterSaveBeforeCommit_rollsBackAndNeverCallsN8n() throws InterruptedException {
        String waId = "5511900000003";
        // dispara DEPOIS que mensagemRepository.save já rodou (persistirBinarioRecebido/atualizarConversa
        // já ocorreram), mas ainda DENTRO da transação REQUIRES_NEW — força o rollback dela.
        doThrow(new RuntimeException("falha forcada pos-save, pre-commit"))
                .when(notificationService).notificarNovaMensagem(any(), any());

        mapper.processarMensagemTexto(value(waId, List.of(textMessage("REAL-C1", waId, "mensagem que deve reverter"))), rawBody());

        // tempo suficiente para o listener assíncrono rodar, SE (indevidamente) tivesse sido publicado.
        Thread.sleep(800);

        assertTrue(buscarMensagem("REAL-C1").isEmpty(), "rollback deve reverter o INSERT da mensagem");
        verify(n8nEventService, never()).enviarPayloadMetaOriginal(any(), any(), any());
        verify(n8nEventService, never()).emitir(any());
    }

    // ── Cenário D: duplicidade real ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("cenário D: processar duas vezes o mesmo whatsappMessageId persiste uma vez e chama o N8N uma vez")
    void scenarioD_duplicateWhatsappMessageId_persistsOnceAndCallsN8nOnce() throws InterruptedException {
        String waId = "5511900000004";
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> {
            latch.countDown();
            return null;
        }).when(n8nEventService).enviarPayloadMetaOriginal(any(), any(), any());

        mapper.processarMensagemTexto(value(waId, List.of(textMessage("REAL-D1", waId, "mensagem original"))), rawBody());
        assertTrue(latch.await(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS));

        // retry do mesmo whatsappMessageId
        mapper.processarMensagemTexto(value(waId, List.of(textMessage("REAL-D1", waId, "mensagem original"))), rawBody());
        Thread.sleep(500); // tempo para uma (indevida) segunda chamada rodar, se existisse

        assertTrue(buscarMensagem("REAL-D1").isPresent());
        verify(n8nEventService, times(1)).enviarPayloadMetaOriginal(any(), any(), any());
    }

    // ── Cenário E: modo humano ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("cenário E: atendimento em modo humano persiste a mensagem mas não publica evento N8N")
    void scenarioE_humanMode_messagePersistsButNoN8nEventPublished() throws InterruptedException {
        String waId = "5511900000005";
        Paciente paciente = new Paciente();
        paciente.setClinica(clinica);
        paciente.setNome("Paciente Modo Humano");
        paciente.setNomeBusca("PACIENTE MODO HUMANO");
        paciente.setTelefone("+" + waId);
        paciente.setTelefoneNormalizado(waId);
        paciente.setStatus("EM_ATENDIMENTO");
        paciente = pacienteRepository.save(paciente);

        Atendimento atendimento = new Atendimento();
        atendimento.setClinica(clinica);
        atendimento.setPaciente(paciente);
        atendimento.setStatus("ATIVO");
        atendimento.setTratadoPorIa(false); // modo humano
        atendimento.setNaoLidas(0);
        atendimentoRepository.save(atendimento);

        mapper.processarMensagemTexto(value(waId, List.of(textMessage("REAL-E1", waId, "mensagem em modo humano"))), rawBody());
        Thread.sleep(800);

        assertTrue(buscarMensagem("REAL-E1").isPresent(), "mensagem deve persistir mesmo em modo humano");
        verify(n8nEventService, never()).enviarPayloadMetaOriginal(any(), any(), any());
        verify(n8nEventService, never()).emitir(any());
    }
}
