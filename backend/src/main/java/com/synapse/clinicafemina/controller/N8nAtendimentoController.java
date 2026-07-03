package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.dto.MensagemDTO;
import com.synapse.clinicafemina.dto.n8n.N8nResponderRequest;
import com.synapse.clinicafemina.service.MensagemService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/api/n8n/atendimentos")
@RequiredArgsConstructor
public class N8nAtendimentoController {

    private final MensagemService mensagemService;

    @Value("${app.n8n.callback-secret:${N8N_CALLBACK_SECRET:}}")
    private String callbackSecret;

    @PostMapping("/{atendimentoId}/responder")
    public ResponseEntity<MensagemDTO> responder(
            @PathVariable Long atendimentoId,
            @RequestHeader(value = "X-N8N-SECRET", required = false) String secret,
            @RequestBody @Valid N8nResponderRequest request
    ) {
        validarSecret(secret, atendimentoId);
        MensagemService.RespostaIaResultado resultado = mensagemService.responderIa(atendimentoId, request);
        HttpStatus status = resultado.duplicada() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(resultado.mensagem());
    }

    private void validarSecret(String secret, Long atendimentoId) {
        if (callbackSecret == null || callbackSecret.isBlank()) {
            log.warn("Chamada N8N recusada por secret nao configurado. atendimento={}", atendimentoId);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credencial N8N invalida.");
        }
        if (secret == null || secret.isBlank() || !segredoIgual(secret, callbackSecret)) {
            log.warn("Chamada N8N recusada por secret invalido. atendimento={}", atendimentoId);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credencial N8N invalida.");
        }
    }

    private boolean segredoIgual(String recebido, String configurado) {
        byte[] recebidoBytes = recebido.getBytes(StandardCharsets.UTF_8);
        byte[] configuradoBytes = configurado.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(recebidoBytes, configuradoBytes);
    }
}
