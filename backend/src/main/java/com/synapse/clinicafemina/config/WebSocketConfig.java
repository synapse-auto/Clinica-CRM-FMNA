package com.synapse.clinicafemina.config;

import com.synapse.clinicafemina.security.StompJwtChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configura o broker WebSocket/STOMP conforme o contrato {@code stomp-topics.md}:
 * <ul>
 *   <li>Endpoint {@code /ws} com fallback SockJS</li>
 *   <li>Prefixos de broker: {@code /topic}, {@code /queue}, {@code /user}</li>
 *   <li>Prefixo de destino para controllers: {@code /app}</li>
 *   <li>Heartbeat: 10 000 ms nos dois sentidos</li>
 *   <li>Validação JWT via {@link StompJwtChannelInterceptor} no canal de entrada</li>
 * </ul>
 *
 * <p>O relay para o RabbitMQ STOMP plugin pode ser habilitado posteriormente
 * substituindo o broker simples por {@code registry.enableStompBrokerRelay(…)}.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompJwtChannelInterceptor stompJwtChannelInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")   // CORS refinado via SecurityConfig
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Prefixo para mensagens destinadas aos @MessageMapping dos controllers
        registry.setApplicationDestinationPrefixes("/app");

        // Broker simples com suporte a tópicos públicos e filas pessoais (/user)
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{10_000, 10_000})
                .setTaskScheduler(taskScheduler());

        // Prefixo para roteamento de filas /user/{userId}/queue/...
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompJwtChannelInterceptor);
    }

    /**
     * Scheduler necessário para o heartbeat do broker simples funcionar corretamente.
     */
    @org.springframework.context.annotation.Bean
    public org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler taskScheduler() {
        org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler scheduler =
                new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("stomp-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }
}
