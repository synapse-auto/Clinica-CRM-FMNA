package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.config.RabbitMQConfig;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Controller de webhooks inbound da Meta WhatsApp Cloud API.
 *
 * Responsabilidades (conforme {@code whatsapp.md}):
 * <ul>
 *   <li>GET  /api/webhooks/whatsapp/verify — verification handshake</li>
 *   <li>POST /api/webhooks/whatsapp — processa mensagens e status updates</li>
 * </ul>
 *
 * Segurança:
 * <ul>
 *   <li>GET: compara {@code hub.verify_token} com {@code META_WHATSAPP_VERIFY_TOKEN}</li>
 *   <li>POST: valida assinatura HMAC-SHA256 do body com {@code META_WHATSAPP_APP_SECRET}</li>
 *   <li>Responde em &lt; 5s (requisito Meta) — processamento assíncrono via RabbitMQ</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks/whatsapp")
@RequiredArgsConstructor
public class WhatsappWebhookController {

    @Value("${app.whatsapp.verify-token}")
    private String verifyToken;

    @Value("${app.whatsapp.app-secret}")
    private String appSecret;

    private final RabbitTemplate rabbitTemplate;

    // ─── GET: Verification handshake ─────────────────────────────────────

    /**
     * Handshake de verificação exigido pela Meta ao configurar o webhook.
     * Responde com {@code hub.challenge} se o {@code hub.verify_token} for válido.
     */
    @GetMapping("/verify")
    public ResponseEntity<String> verify(
            @RequestParam("hub.mode")         String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge")    String challenge) {

        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            log.info("WhatsApp webhook verificado com sucesso");
            return ResponseEntity.ok(challenge);
        }

        log.warn("Tentativa de verificação inválida: mode={}, token fornecido não bate", mode);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    // ─── POST: Inbound messages & status updates ──────────────────────────

    /**
     * Recebe e despacha payloads inbound da Meta.
     * Retorna 200 imediatamente e processa de forma síncrona (rápida) + async via RabbitMQ.
     */
    @PostMapping
    public ResponseEntity<Void> receberWebhook(
            HttpServletRequest httpRequest,
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {

        // 1. Valida assinatura HMAC-SHA256
        if (!validarAssinatura(rawBody, signature)) {
            log.warn("Assinatura X-Hub-Signature-256 inválida — payload rejeitado");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 2. Envia para o RabbitMQ para processamento assíncrono rápido
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.WHATSAPP_EXCHANGE,
                RabbitMQConfig.INBOUND_ROUTING_KEY,
                rawBody
        );

        // 3. Responde 200 < 5s (requisito Meta)
        return ResponseEntity.ok().build();
    }

    // ─── Validação HMAC-SHA256 ────────────────────────────────────────────

    private boolean validarAssinatura(byte[] body, String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] computed = mac.doFinal(body);
            String computedHex = "sha256=" + HexFormat.of().formatHex(computed);

            // Comparação de tempo constante para prevenir timing attacks
            return MessageDigest.isEqual(
                    computedHex.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.getBytes(StandardCharsets.UTF_8));

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Erro ao validar assinatura HMAC: {}", e.getMessage());
            return false;
        }
    }
}
