package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.service.RealtimeBroadcastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import java.time.OffsetDateTime;

/**
 * Controller STOMP para eventos de presença e UX em tempo real.
 *
 * Clientes enviam para {@code /app/presenca/ping} e {@code /app/digitando}.
 * O servidor propaga para os destinos STOMP adequados via {@link RealtimeBroadcastService}.
 *
 * Contratos de mensagem:
 * <pre>
 * PING   → { "atendimentoId": 123 }
 * TYPING → { "atendimentoId": 123, "digitando": true }
 * </pre>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class PresencaController {

    private final RealtimeBroadcastService broadcastService;

    /**
     * Heartbeat de presença. O frontend envia um ping a cada ~30s para
     * manter o status ONLINE do atendente visível para a equipe.
     *
     * Broadcast: {@code /topic/presenca-equipe}
     */
    @MessageMapping("/presenca/ping")
    public void ping(
            @Payload PingPayload payload,
            @AuthenticationPrincipal Usuario usuario) {
        if (usuario == null) return;
        log.debug("Ping de presença recebido do usuário {}", usuario.getId());
        broadcastService.broadcastPresenca(usuario.getId(), usuario.getNome(), "ONLINE",
                OffsetDateTime.now());
    }

    /**
     * Indicador de digitação. O frontend emite este evento quando o atendente
     * começa ou para de digitar.
     *
     * Broadcast: {@code /topic/atendimento/{id}/digitando} para todos no atendimento.
     */
    @MessageMapping("/digitando")
    public void digitando(
            @Payload DigitandoPayload payload,
            @AuthenticationPrincipal Usuario usuario) {
        if (usuario == null) return;
        log.debug("Indicador de digitação: atendimento={}, digitando={}",
                payload.atendimentoId(), payload.digitando());
        broadcastService.broadcastDigitando(
                payload.atendimentoId(),
                usuario.getId(),
                usuario.getNome(),
                payload.digitando()
        );
    }

    // ─── Payloads internos ────────────────────────────────────────────────

    public record PingPayload(Long atendimentoId) {}

    public record DigitandoPayload(Long atendimentoId, boolean digitando) {}
}
