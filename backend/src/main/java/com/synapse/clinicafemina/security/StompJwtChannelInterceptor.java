package com.synapse.clinicafemina.security;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

/**
 * Interceptador de canal STOMP que valida o JWT presente no header
 * {@code Authorization} do frame CONNECT.
 *
 * Conexões sem JWT válido são rejeitadas imediatamente (a exceção
 * propagada pelo Spring fecha o WebSocket com código 4401).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompJwtChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor
                .getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("STOMP CONNECT sem Authorization header — conexão rejeitada");
            throw new JwtException("TOKEN_INVALIDO: Authorization header ausente");
        }

        String jwt = authHeader.substring(7);
        String username;
        try {
            username = jwtService.extractUsername(jwt);
        } catch (JwtException e) {
            log.warn("STOMP CONNECT com JWT inválido: {}", e.getMessage());
            throw new JwtException("TOKEN_INVALIDO: " + e.getMessage());
        }

        if (username != null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            if (jwtService.isTokenValid(jwt, userDetails)) {
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                accessor.setUser(auth);
                log.debug("STOMP autenticado: {}", username);
            } else {
                log.warn("STOMP CONNECT com token expirado para usuário: {}", username);
                throw new JwtException("TOKEN_INVALIDO: token expirado");
            }
        }

        return message;
    }
}
