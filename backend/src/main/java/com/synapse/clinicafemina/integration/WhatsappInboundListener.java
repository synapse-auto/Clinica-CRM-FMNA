package com.synapse.clinicafemina.integration;
 
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.clinicafemina.service.RealtimeBroadcastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
 
import java.io.IOException;
import java.util.List;
import java.util.Map;
 
@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsappInboundListener {
 
    private final WhatsappInboundMapper inboundMapper;
    private final ObjectMapper objectMapper;
    private final RealtimeBroadcastService broadcastService;
 
    @RabbitListener(queues = "whatsapp.inbound.queue")
    public void processarMensagem(byte[] rawBody) {
        log.info("Iniciando processamento de payload inbound recebido.");
        try {
            Map<String, Object> payload = objectMapper.readValue(
                    rawBody, new TypeReference<Map<String, Object>>() {}
            );
            despachar(payload);
        } catch (IOException exception) {
            log.error("Erro ao interpretar payload WhatsApp. tipoErro={}",
                    exception.getClass().getSimpleName());
        }
    }
 
    @SuppressWarnings("unchecked")
    private void despachar(Map<String, Object> payload) {
        List<Map<String, Object>> entries = (List<Map<String, Object>>) payload.get("entry");
        if (entries == null) {
            log.warn("Payload recebido não possui campo 'entry'.");
            return;
        }
 
        for (Map<String, Object> entry : entries) {
            List<Map<String, Object>> changes = (List<Map<String, Object>>) entry.get("changes");
            if (changes == null) continue;
            changes.stream()
                    .filter(change -> "messages".equals(change.get("field")))
                    .map(change -> (Map<String, Object>) change.get("value"))
                    .filter(java.util.Objects::nonNull)
                    .forEach(this::processarValue);
        }
    }
 
    @SuppressWarnings("unchecked")
    private void processarValue(Map<String, Object> value) {
        List<?> mensagens = (List<?>) value.get("messages");
        int numMensagens = mensagens != null ? mensagens.size() : 0;
 
        List<Map<String, Object>> statuses = (List<Map<String, Object>>) value.get("statuses");
        int numStatus = statuses != null ? statuses.size() : 0;
 
        log.info("Processando payload de eventos do WhatsApp: mensagens={}, status_updates={}", numMensagens, numStatus);
 
        if (numMensagens > 0) {
            executarSeguro(() -> inboundMapper.processarMensagemTexto(value), "mensagem inbound");
        }
 
        if (statuses == null) return;
        statuses.forEach(status -> executarSeguro(
                () -> inboundMapper.processarStatusUpdate(value, status)
                        .filter(mensagem -> mensagem.getAtendimento().getAtendentePrincipal() != null)
                        .ifPresent(mensagem -> broadcastService.broadcastStatusMensagem(
                                mensagem.getAtendimento().getAtendentePrincipal().getId(),
                                mensagem.getId(),
                                mensagem.getAtendimento().getId(),
                                mensagem.getWhatsappStatus()
                        )),
                "status WhatsApp"
        ));
    }
 
    private void executarSeguro(Runnable operacao, String contexto) {
        try {
            operacao.run();
        } catch (Exception exception) {
            log.error("Erro ao processar {}. tipoErro={}",
                    contexto, exception.getClass().getSimpleName());
        }
    }
}
