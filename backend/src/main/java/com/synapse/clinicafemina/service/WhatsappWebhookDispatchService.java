package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.config.RabbitMQConfig;
import com.synapse.clinicafemina.integration.WhatsappInboundListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsappWebhookDispatchService {

    private final RabbitTemplate rabbitTemplate;
    private final WhatsappInboundListener inboundListener;

    public void despachar(byte[] rawBody) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.WHATSAPP_EXCHANGE,
                    RabbitMQConfig.INBOUND_ROUTING_KEY,
                    rawBody
            );
        } catch (Exception exception) {
            log.warn("RabbitMQ indisponível; processando webhook WhatsApp de forma síncrona. tipoErro={}",
                    exception.getClass().getSimpleName());
            inboundListener.processarMensagem(rawBody);
        }
    }
}
