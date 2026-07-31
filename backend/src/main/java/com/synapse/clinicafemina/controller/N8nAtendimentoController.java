package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.dto.AtendimentoDetalheDTO;
import com.synapse.clinicafemina.dto.AtendenteOptionDTO;
import com.synapse.clinicafemina.dto.MensagemDTO;
import com.synapse.clinicafemina.dto.TransferirAtendimentoRequest;
import com.synapse.clinicafemina.dto.n8n.N8nResponderRequest;
import com.synapse.clinicafemina.dto.n8n.N8nTransferirHumanoRequest;
import com.synapse.clinicafemina.dto.n8n.N8nTransferirProximoHumanoRequest;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Slf4j
@RestController
@RequestMapping("/api/n8n/atendimentos")
@RequiredArgsConstructor
@Tag(name = "N8N", description = "Callbacks internos autorizados pelo contrato X-N8N-SECRET, isolados por clínica.")
public class N8nAtendimentoController {

    private final MensagemService mensagemService;
    private final AtendimentoService atendimentoService;
    private final N8nCallbackAuthorizationService authorizationService;
    private final ObjectMapper objectMapper;

    @GetMapping("/atendentes-transferencia")
    @Operation(summary = "Listar atendentes elegíveis para transferência", description = "Exige X-N8N-SECRET; não usa JWT do CRM.")
    public List<AtendenteOptionDTO> listarAtendentesTransferencia(
            @Parameter(name = "X-N8N-SECRET", description = "Segredo do callback N8N.", required = true)
            @RequestHeader(value = "X-N8N-SECRET", required = false) String secret
    ) {
        return atendimentoService.listarAtendentes(authorizationService.autorizarClinica(secret).getId());
    }

    @PostMapping("/{atendimentoId}/responder")
    @Operation(summary = "Registrar resposta da IA", description = "Exige X-N8N-SECRET; a resposta é isolada pela clínica do atendimento.")
    public ResponseEntity<MensagemDTO> responder(
            @PathVariable Long atendimentoId,
            @Parameter(name = "X-N8N-SECRET", description = "Segredo do callback N8N.", required = true)
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
    @Operation(summary = "Transferir atendimento para humano", description = "Exige X-N8N-SECRET e respeita o isolamento por clínica.")
    public ResponseEntity<Map<String, Object>> transferirHumano(
            @PathVariable Long atendimentoId,
            @Parameter(name = "X-N8N-SECRET", description = "Segredo do callback N8N.", required = true)
            @RequestHeader(value = "X-N8N-SECRET", required = false) String secret,
            @RequestBody N8nTransferirHumanoRequest request
    ) {
        N8nCallbackAuthorizationService.Autorizacao autorizacao =
                authorizationService.autorizar(secret, atendimentoId);
        TransferirAtendimentoRequest transferencia = request.paraTransferencia();
        log.info("Callback N8N de transferencia validado. atendimentoId={} clinicaId={} novoAtendenteIdTipo={} motivoChars={} resumoChars={}",
                atendimentoId,
                autorizacao.clinicaId(),
                request.tipoNovoAtendenteId(),
                request.motivoChars(),
                request.resumoChars());
        AtendimentoService.TransferenciaHumanoResultado resultado = atendimentoService.transferirPorN8n(
                atendimentoId,
                transferencia,
                autorizacao.clinicaId()
        );
        log.info("Atendimento {} transferido para humano por callback N8N. novoAtendente={}",
                atendimentoId, transferencia.novoAtendenteId());
        Map<String, Object> body = transferirHumanoResponse(resultado);
        body.put("novoAtendenteId", transferencia.novoAtendenteId());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/{atendimentoId}/transferir-proximo-humano")
    @Operation(summary = "Transferir para o próximo humano", description = "Exige X-N8N-SECRET e Idempotency-Key; atendentesIds é um array JSON.")
    public ResponseEntity<Map<String, Object>> transferirProximoHumano(
            @PathVariable Long atendimentoId,
            @Parameter(name = "X-N8N-SECRET", description = "Segredo do callback N8N.", required = true)
            @RequestHeader(value = "X-N8N-SECRET", required = false) String secret,
            @Parameter(name = "Idempotency-Key", description = "Chave obrigatória para repetição idempotente da transferência.", required = true)
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody N8nTransferirProximoHumanoRequest request
    ) {
        N8nCallbackAuthorizationService.Autorizacao autorizacao =
                authorizationService.autorizar(secret, atendimentoId);
        log.info("Callback N8N de rodizio recebido. path=/api/n8n/atendimentos/{}/transferir-proximo-humano "
                        + "atendimentoId={} clinicaId={} idempotencyKeyPresente={} atendentesIdsTipo={} atendentesQuantidade={}",
                atendimentoId,
                atendimentoId,
                autorizacao.clinicaId(),
                idempotencyKey != null && !idempotencyKey.isBlank(),
                request.tipoAtendentesIds(),
                request.quantidadeAtendentes());
        List<Long> atendentesIds = request.idsOrdenados();
        String chaveIdempotencia = request.validarIdempotencyKey(idempotencyKey);
        AtendimentoService.TransferenciaRodizioHumanoResultado resultado =
                atendimentoService.transferirProximoPorN8n(
                        atendimentoId, request, chaveIdempotencia, autorizacao.clinicaId()
                );
        Map<String, Object> body = transferirHumanoResponse(resultado.transferencia());
        body.put("novoAtendenteId", resultado.novoAtendenteId());
        body.put("posicaoSelecionada", resultado.posicaoSelecionada());
        body.put("modoSelecao", "RODIZIO");
        log.info("Atendimento transferido por rodízio N8N. atendimentoId={} clinicaId={} posicaoSelecionada={}",
                atendimentoId, autorizacao.clinicaId(), resultado.posicaoSelecionada());
        return ResponseEntity.ok(body);
    }

    @PatchMapping("/{atendimentoId}/modo-ia")
    @Operation(summary = "Retornar atendimento ao modo IA", description = "Exige X-N8N-SECRET e respeita o isolamento por clínica.")
    public ResponseEntity<AtendimentoDetalheDTO> ativarModoIa(
            @PathVariable Long atendimentoId,
            @Parameter(name = "X-N8N-SECRET", description = "Segredo do callback N8N.", required = true)
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

    private Map<String, Object> transferirHumanoResponse(AtendimentoService.TransferenciaHumanoResultado resultado) {
        Map<String, Object> body = new LinkedHashMap<>(objectMapper.convertValue(
                resultado.atendimento(), new TypeReference<Map<String, Object>>() {}
        ));
        body.put("atendimentoId", resultado.atendimento().id());
        body.put("modo", "HUMANO");
        body.put("transferido", resultado.transferido());
        body.put("jaEstavaTransferido", resultado.jaEstavaTransferido());
        body.put("destinatarioAlterado", resultado.destinatarioAlterado());
        body.put("eventosCriados", resultado.eventosCriados());
        body.put("resumoRegistrado", resultado.resumoRegistrado());
        body.put("notificacoesCriadas", resultado.notificacoesCriadas());
        body.put("transferidoEm", resultado.transferidoEm());
        return body;
    }
}
