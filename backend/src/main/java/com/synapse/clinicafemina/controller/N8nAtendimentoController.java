package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.dto.AtendimentoDetalheDTO;
import com.synapse.clinicafemina.dto.MensagemDTO;
import com.synapse.clinicafemina.dto.TransferirAtendimentoRequest;
import com.synapse.clinicafemina.dto.n8n.N8nResponderRequest;
import com.synapse.clinicafemina.service.AtendimentoService;
import com.synapse.clinicafemina.service.ClinicaConfigService;
import com.synapse.clinicafemina.service.MensagemService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
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
    private final AtendimentoService atendimentoService;
    private final ClinicaConfigService clinicaConfigService;

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

    @PostMapping("/{atendimentoId}/transferir-humano")
    public ResponseEntity<AtendimentoDetalheDTO> transferirHumano(
            @PathVariable Long atendimentoId,
            @RequestHeader(value = "X-N8N-SECRET", required = false) String secret,
            @RequestBody @Valid TransferirAtendimentoRequest request
    ) {
        validarSecret(secret, atendimentoId);
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        AtendimentoDetalheDTO atendimento = atendimentoService.transferir(
                atendimentoId,
                request,
                clinica.getId(),
                request.novoAtendenteId()
        );
        log.info("Atendimento {} transferido para humano por callback N8N. novoAtendente={}",
                atendimentoId, request.novoAtendenteId());
        return ResponseEntity.ok(atendimento);
    }

    @PatchMapping("/{atendimentoId}/modo-ia")
    public ResponseEntity<AtendimentoDetalheDTO> ativarModoIa(
            @PathVariable Long atendimentoId,
            @RequestHeader(value = "X-N8N-SECRET", required = false) String secret
    ) {
        validarSecret(secret, atendimentoId);
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        AtendimentoDetalheDTO atendimento = atendimentoService.ativarModoIa(atendimentoId, clinica.getId());
        log.info("Atendimento {} retornado para IA por callback N8N", atendimentoId);
        return ResponseEntity.ok(atendimento);
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
