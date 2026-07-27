package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.dto.AtendimentoDetalheDTO;
import com.synapse.clinicafemina.dto.MensagemDTO;
import com.synapse.clinicafemina.dto.TransferirAtendimentoRequest;
import com.synapse.clinicafemina.dto.n8n.N8nResponderRequest;
import com.synapse.clinicafemina.service.AtendimentoService;
import com.synapse.clinicafemina.service.MensagemService;
import com.synapse.clinicafemina.service.N8nCallbackAuthorizationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/n8n/atendimentos")
@RequiredArgsConstructor
public class N8nAtendimentoController {

    private final MensagemService mensagemService;
    private final AtendimentoService atendimentoService;
    private final N8nCallbackAuthorizationService authorizationService;
    private final ObjectMapper objectMapper;

    @PostMapping("/{atendimentoId}/responder")
    public ResponseEntity<MensagemDTO> responder(
            @PathVariable Long atendimentoId,
            @RequestHeader(value = "X-N8N-SECRET", required = false) String secret,
            @RequestBody @Valid N8nResponderRequest request
    ) {
        N8nCallbackAuthorizationService.Autorizacao autorizacao =
                authorizationService.autorizar(secret, atendimentoId);
        MensagemService.RespostaIaResultado resultado = mensagemService.responderIa(
                atendimentoId,
                autorizacao.clinicaId(),
                request
        );
        HttpStatus status = resultado.duplicada() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(resultado.mensagem());
    }

    @PostMapping("/{atendimentoId}/transferir-humano")
    public ResponseEntity<Map<String, Object>> transferirHumano(
            @PathVariable Long atendimentoId,
            @RequestHeader(value = "X-N8N-SECRET", required = false) String secret,
            @RequestBody @Valid TransferirAtendimentoRequest request
    ) {
        N8nCallbackAuthorizationService.Autorizacao autorizacao =
                authorizationService.autorizar(secret, atendimentoId);
        AtendimentoService.TransferenciaHumanoResultado resultado = atendimentoService.transferirPorN8n(
                atendimentoId,
                request,
                autorizacao.clinicaId()
        );
        log.info("Atendimento {} transferido para humano por callback N8N. novoAtendente={}",
                atendimentoId, request.novoAtendenteId());
        Map<String, Object> body = new LinkedHashMap<>(objectMapper.convertValue(
                resultado.atendimento(), new TypeReference<Map<String, Object>>() {}
        ));
        body.put("atendimentoId", resultado.atendimento().id());
        body.put("modo", "HUMANO");
        body.put("transferido", resultado.transferido());
        body.put("jaEstavaTransferido", resultado.jaEstavaTransferido());
        body.put("novoAtendenteId", request.novoAtendenteId());
        body.put("destinatarioAlterado", resultado.destinatarioAlterado());
        body.put("eventosCriados", resultado.eventosCriados());
        body.put("resumoRegistrado", resultado.resumoRegistrado());
        body.put("notificacoesCriadas", resultado.notificacoesCriadas());
        body.put("transferidoEm", resultado.transferidoEm());
        return ResponseEntity.ok(body);
    }

    @PatchMapping("/{atendimentoId}/modo-ia")
    public ResponseEntity<AtendimentoDetalheDTO> ativarModoIa(
            @PathVariable Long atendimentoId,
            @RequestHeader(value = "X-N8N-SECRET", required = false) String secret
    ) {
        N8nCallbackAuthorizationService.Autorizacao autorizacao =
                authorizationService.autorizar(secret, atendimentoId);
        AtendimentoDetalheDTO atendimento = atendimentoService.ativarModoIa(
                atendimentoId,
                autorizacao.clinicaId()
        );
        log.info("Atendimento {} retornado para IA por callback N8N", atendimentoId);
        return ResponseEntity.ok(atendimento);
    }
}
