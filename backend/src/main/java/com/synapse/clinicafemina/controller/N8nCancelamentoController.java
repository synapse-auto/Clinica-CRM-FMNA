package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.dto.cancelamento.CancelamentoAgendamentoResponse;
import com.synapse.clinicafemina.dto.cancelamento.CancelarAgendamentoN8nRequest;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.service.CancelamentoAgendamentoService;
import com.synapse.clinicafemina.service.N8nCallbackAuthorizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/n8n/cancelamentos")
@RequiredArgsConstructor
public class N8nCancelamentoController {
    private final N8nCallbackAuthorizationService authorizationService;
    private final CancelamentoAgendamentoService service;

    @PostMapping
    public ResponseEntity<CancelamentoAgendamentoResponse> registrar(
            @RequestHeader(value = "X-N8N-SECRET", required = false) String secret,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CancelarAgendamentoN8nRequest request
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Idempotency-Key e obrigatoria.");
        }
        Clinica clinica = authorizationService.autorizarClinica(secret);
        CancelamentoAgendamentoService.ResultadoN8n result = service.cancelarPorN8n(clinica, idempotencyKey.trim(), request);
        return ResponseEntity.status(result.criado() ? HttpStatus.CREATED : HttpStatus.OK).body(result.response());
    }
}
