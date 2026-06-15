package com.synapse.clinicafemina.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    // TODO: if broadcastService is needed for STOMP, it should be injected here.
    // The previous implementation had a comment about it.
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "whatsapp.inbound.queue")
    public void processarMensagem(byte[] rawBody) {
        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(rawBody, new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            log.error("Erro ao parsear payload WhatsApp no RabbitMQ. tipoErro={}", e.getClass().getSimpleName());
            return;
        }

        despachar(payload);
    }

    @SuppressWarnings("unchecked")
    private void despachar(Map<String, Object> payload) {
        List<Map<String, Object>> entries =
                (List<Map<String, Object>>) payload.get("entry");
        if (entries == null) return;

        for (Map<String, Object> entry : entries) {
            List<Map<String, Object>> changes =
                    (List<Map<String, Object>>) entry.get("changes");
            if (changes == null) continue;

            for (Map<String, Object> change : changes) {
                if (!"messages".equals(change.get("field"))) continue;

                Map<String, Object> value = (Map<String, Object>) change.get("value");
                if (value == null) continue;

                // Mensagens inbound
                List<?> messages = (List<?>) value.get("messages");
                if (messages != null && !messages.isEmpty()) {
                    try {
                        inboundMapper.processarMensagemTexto(value);
                    } catch (Exception e) {
                        log.error("Erro ao processar mensagem inbound. tipoErro={}", e.getClass().getSimpleName());
                    }
                }

                // Status updates (entregue, lida)
                List<Map<String, Object>> statuses =
                        (List<Map<String, Object>>) value.get("statuses");
                if (statuses != null) {
                    for (Map<String, Object> status : statuses) {
                        try {
                            inboundMapper.processarStatusUpdate(value, status).ifPresent(mensagem -> {
                                // TODO: buscar o atendente responsável pelo atendimento
                                // e publicar STOMP via broadcastService.broadcastStatusMensagem(...)
                                log.debug("Status de mensagem {} atualizado para {}",
                                        mensagem.getId(), mensagem.getWhatsappStatus());
                            });
                        } catch (Exception e) {
                            log.error("Erro ao processar status update. tipoErro={}", e.getClass().getSimpleName());
                        }
                    }
                }
            }
        }
    }
}
