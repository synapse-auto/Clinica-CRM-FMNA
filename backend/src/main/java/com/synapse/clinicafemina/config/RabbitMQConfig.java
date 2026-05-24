package com.synapse.clinicafemina.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração central do RabbitMQ.
 * Define exchanges, filas principais e Dead Letter Exchange (DLX).
 *
 * Fluxo de mensagem inbound:
 *   Meta Webhook → mensagem.entrada exchange → fila mensagem.entrada
 *   → RealtimeBroadcastService (STOMP) + N8nWebhookPublisher
 *
 * DLX para mensagens outbound que falharam definitivamente:
 *   whatsapp.saida exchange → whatsapp.saida.dlx exchange → whatsapp.saida.dlq
 */
@Configuration
public class RabbitMQConfig {

    // ─── Nomes de Exchanges ────────────────────────────────────────────────
    public static final String EXCHANGE_MENSAGEM_ENTRADA   = "mensagem.entrada";
    public static final String EXCHANGE_WHATSAPP_SAIDA     = "whatsapp.saida";
    public static final String EXCHANGE_WHATSAPP_SAIDA_DLX = "whatsapp.saida.dlx";
    public static final String WHATSAPP_EXCHANGE           = "whatsapp.exchange";

    // ─── Nomes de Filas ────────────────────────────────────────────────────
    public static final String QUEUE_MENSAGEM_ENTRADA      = "mensagem.entrada";
    public static final String QUEUE_WHATSAPP_SAIDA        = "whatsapp.saida";
    public static final String QUEUE_WHATSAPP_SAIDA_DLQ    = "whatsapp.saida.dlq";
    public static final String INBOUND_QUEUE               = "whatsapp.inbound.queue";

    // ─── Routing Keys ──────────────────────────────────────────────────────
    public static final String ROUTING_KEY_MENSAGEM_ENTRADA = "mensagem.entrada";
    public static final String ROUTING_KEY_WHATSAPP_SAIDA   = "whatsapp.saida";
    public static final String INBOUND_ROUTING_KEY          = "whatsapp.inbound.routing";

    // ─── Exchanges ─────────────────────────────────────────────────────────

    @Bean
    public DirectExchange mensagemEntradaExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE_MENSAGEM_ENTRADA)
                .durable(true).build();
    }

    @Bean
    public DirectExchange whatsappSaidaExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE_WHATSAPP_SAIDA)
                .durable(true).build();
    }

    @Bean
    public DirectExchange whatsappSaidaDlxExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE_WHATSAPP_SAIDA_DLX)
                .durable(true).build();
    }

    @Bean
    public DirectExchange whatsappExchange() {
        return ExchangeBuilder.directExchange(WHATSAPP_EXCHANGE)
                .durable(true).build();
    }

    // ─── Filas ─────────────────────────────────────────────────────────────

    @Bean
    public Queue mensagemEntradaQueue() {
        return QueueBuilder.durable(QUEUE_MENSAGEM_ENTRADA).build();
    }

    @Bean
    public Queue whatsappSaidaQueue() {
        return QueueBuilder.durable(QUEUE_WHATSAPP_SAIDA)
                .withArgument("x-dead-letter-exchange", EXCHANGE_WHATSAPP_SAIDA_DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY_WHATSAPP_SAIDA)
                .build();
    }

    @Bean
    public Queue whatsappSaidaDlq() {
        return QueueBuilder.durable(QUEUE_WHATSAPP_SAIDA_DLQ).build();
    }

    @Bean
    public Queue inboundQueue() {
        return QueueBuilder.durable(INBOUND_QUEUE).build();
    }

    // ─── Bindings ──────────────────────────────────────────────────────────

    @Bean
    public Binding mensagemEntradaBinding() {
        return BindingBuilder.bind(mensagemEntradaQueue())
                .to(mensagemEntradaExchange())
                .with(ROUTING_KEY_MENSAGEM_ENTRADA);
    }

    @Bean
    public Binding whatsappSaidaBinding() {
        return BindingBuilder.bind(whatsappSaidaQueue())
                .to(whatsappSaidaExchange())
                .with(ROUTING_KEY_WHATSAPP_SAIDA);
    }

    @Bean
    public Binding whatsappSaidaDlqBinding() {
        return BindingBuilder.bind(whatsappSaidaDlq())
                .to(whatsappSaidaDlxExchange())
                .with(ROUTING_KEY_WHATSAPP_SAIDA);
    }

    @Bean
    public Binding inboundBinding() {
        return BindingBuilder.bind(inboundQueue())
                .to(whatsappExchange())
                .with(INBOUND_ROUTING_KEY);
    }

    // ─── Infraestrutura ────────────────────────────────────────────────────

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
