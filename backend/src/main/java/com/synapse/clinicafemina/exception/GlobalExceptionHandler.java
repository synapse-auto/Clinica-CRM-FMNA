package com.synapse.clinicafemina.exception;

import lombok.extern.slf4j.Slf4j;
import com.synapse.clinicafemina.integration.WhatsappTemplateRequiredException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── 401 Credenciais inválidas ─────────────────────────────────────────────
    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class})
    public ResponseEntity<Object> handleAuthFailure(RuntimeException ex, WebRequest request) {
        // Logamos internamente para rastreabilidade, mas retornamos mensagem genérica
        // para não confirmar ao atacante se o email existe ou a senha está errada.
        log.warn("Falha de autenticação em [{}]: {}",
                request.getDescription(false).replace("uri=", ""),
                ex.getMessage());
        return buildResponse(HttpStatus.UNAUTHORIZED, "Credenciais inválidas.", request);
    }

    // ── 403 Acesso negado ─────────────────────────────────────────────────────
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        log.warn("Acesso negado em [{}]: {}",
                request.getDescription(false).replace("uri=", ""),
                ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, "Acesso negado.", request);
    }

    // ── 404 / rotas não encontradas (elimina o falso 500 anterior) ────────────
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Object> handleNotFound(NotFoundException ex, WebRequest request) {
        log.debug("Recurso não encontrado: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    // Captura NoResourceFoundException e HttpRequestMethodNotSupportedException
    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<Object> handleErrorResponse(ErrorResponseException ex, WebRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        String message = status == HttpStatus.NOT_FOUND
                ? "Recurso não encontrado."
                : ex.getMessage();
        log.debug("ErrorResponseException [{}] em {}: {}",
                status.value(),
                request.getDescription(false).replace("uri=", ""),
                ex.getMessage());
        return buildResponse(status, message, request);
    }

    // ── 409 Conflito de estado ────────────────────────────────────────────────
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Object> handleIllegalState(IllegalStateException ex, WebRequest request) {
        log.warn("IllegalStateException: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler({WhatsappWindowClosedException.class, WhatsappTemplateRequiredException.class})
    public ResponseEntity<Object> handleWhatsappWindowClosed(RuntimeException ex, WebRequest request) {
        log.info("Envio WhatsApp bloqueado porque a janela exige template");
        return buildResponse(
                HttpStatus.CONFLICT,
                WhatsappWindowClosedException.MESSAGE,
                WhatsappWindowClosedException.CODE,
                request
        );
    }

    @ExceptionHandler(WhatsappTemplateSendException.class)
    public ResponseEntity<Object> handleWhatsappTemplateSend(
            WhatsappTemplateSendException ex,
            WebRequest request
    ) {
        log.error("Falha sanitizada no envio de template WhatsApp. tipoErro={}",
                ex.getCause() == null ? "desconhecido" : ex.getCause().getClass().getSimpleName());
        return buildResponse(
                HttpStatus.BAD_GATEWAY,
                ex.getMessage(),
                "WHATSAPP_TEMPLATE_SEND_FAILED",
                request
        );
    }

    @ExceptionHandler(WhatsappTemplateParametersException.class)
    public ResponseEntity<Object> handleWhatsappTemplateParameters(
            WhatsappTemplateParametersException ex,
            WebRequest request
    ) {
        log.info("Parametros de template WhatsApp rejeitados antes do envio");
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                ex.getMessage(),
                WhatsappTemplateParametersException.CODE,
                request
        );
    }

    // ── 409 Idempotency-Key reutilizada com payload diferente ─────────────────
    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<Object> handleIdempotencyConflict(
            IdempotencyConflictException ex,
            WebRequest request
    ) {
        log.info("Idempotency-Key reutilizada com payload diferente em [{}]",
                request.getDescription(false).replace("uri=", ""));
        return buildResponse(
                HttpStatus.CONFLICT, ex.getMessage(), IdempotencyConflictException.CODE, request);
    }

    // ── 409 Provider externo sem suporte a sincronização em massa (Darwin) ────
    @ExceptionHandler(BulkSyncNotSupportedException.class)
    public ResponseEntity<Object> handleBulkSyncNotSupported(
            BulkSyncNotSupportedException ex,
            WebRequest request
    ) {
        log.info("Sincronizacao em massa solicitada para provider sem suporte a bulk sync");
        return buildResponse(
                HttpStatus.CONFLICT, ex.getMessage(), BulkSyncNotSupportedException.CODE, request);
    }

    // ── 409 Integração Darwin indisponível para a clínica/configuração atual ──
    @ExceptionHandler(DarwinNotAvailableException.class)
    public ResponseEntity<Object> handleDarwinNotAvailable(
            DarwinNotAvailableException ex,
            WebRequest request
    ) {
        log.info("Consulta Darwin sob demanda rejeitada: integracao indisponivel");
        return buildResponse(
                HttpStatus.CONFLICT, ex.getMessage(), DarwinNotAvailableException.CODE, request);
    }

    // ── Erros sanitizados repassados pela integração Darwin (sem stack trace) ─
    @ExceptionHandler(DarwinIntegrationException.class)
    public ResponseEntity<Object> handleDarwinIntegration(
            DarwinIntegrationException ex,
            WebRequest request
    ) {
        log.warn("Falha sanitizada na integracao Darwin: statusOrigem={}", ex.upstreamStatus());
        HttpStatus status = HttpStatus.resolve(ex.upstreamStatus());
        if (status == null) {
            status = HttpStatus.BAD_GATEWAY;
        }
        return buildResponse(status, ex.getMessage(), DarwinIntegrationException.CODE, request);
    }

    // ── 501 Não implementado ──────────────────────────────────────────────────
    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<Object> handleUnsupported(UnsupportedOperationException ex, WebRequest request) {
        log.warn("Operação não suportada: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_IMPLEMENTED, ex.getMessage(), request);
    }

    // ── 400 Validação de campos ───────────────────────────────────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidationExceptions(MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage()));

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", OffsetDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Bad Request");
        body.put("message", "Erro de validação nos campos informados.");
        body.put("details", fieldErrors);
        body.put("path", request.getDescription(false).replace("uri=", ""));
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Object> handleBadRequest(BadRequestException ex, WebRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(N8nTransferPayloadException.class)
    public ResponseEntity<Object> handleN8nTransferPayload(
            N8nTransferPayloadException ex,
            WebRequest request
    ) {
        log.warn("Payload de transferência N8N rejeitado em [{}]. camposInvalidos={}",
                path(request),
                ex.getDetails().keySet());
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Payload de transferência inválido.",
                "INVALID_TRANSFER_PAYLOAD",
                ex.getDetails(),
                request
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Object> handleMissingParameter(
            MissingServletRequestParameterException ex, WebRequest request) {
        log.warn("Parâmetro obrigatório ausente em [{}]: {}",
                request.getDescription(false).replace("uri=", ""),
                ex.getParameterName());
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Parametro obrigatório ausente: " + ex.getParameterName(),
                request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException ex, WebRequest request) {
        log.warn("Requisição inválida em [{}]: {}",
                request.getDescription(false).replace("uri=", ""),
                ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleUnreadableMessage(HttpMessageNotReadableException ex, WebRequest request) {
        if (isN8nTransferPath(request)) {
            log.warn("JSON inválido no callback de transferência N8N em [{}]", path(request));
            return buildResponse(
                    HttpStatus.BAD_REQUEST,
                    "O corpo da requisição não contém um JSON válido.",
                    "INVALID_JSON",
                    request
            );
        }
        log.warn("JSON inválido em [{}]: {}",
                request.getDescription(false).replace("uri=", ""),
                ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "Requisição inválida.", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrity(DataIntegrityViolationException ex, WebRequest request) {
        log.warn("Violação de integridade em [{}]: {}",
                request.getDescription(false).replace("uri=", ""),
                ex.getMostSpecificCause().getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "Dados violam uma regra de validação.", request);
    }

    // ── 500 Catchall — SEMPRE loga a stack trace completa internamente ─────────
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleAllExceptions(Exception ex, WebRequest request) {
        // log.error registra a stack trace completa no console/arquivo de log.
        // O cliente NUNCA recebe a mensagem técnica.
        log.error("Exceção não tratada em [{}]",
                request.getDescription(false).replace("uri=", ""), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "Um erro inesperado ocorreu. Contacte o suporte.", request);
    }

    // ── Builder ───────────────────────────────────────────────────────────────
    private ResponseEntity<Object> buildResponse(HttpStatus status, String message, WebRequest request) {
        return buildResponse(status, message, null, request);
    }

    private ResponseEntity<Object> buildResponse(
            HttpStatus status,
            String message,
            String code,
            WebRequest request
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", OffsetDateTime.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        if (code != null) {
            body.put("code", code);
        }
        body.put("path", request.getDescription(false).replace("uri=", ""));
        return new ResponseEntity<>(body, status);
    }

    private ResponseEntity<Object> buildResponse(
            HttpStatus status,
            String message,
            String code,
            Map<String, String> details,
            WebRequest request
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", OffsetDateTime.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("code", code);
        body.put("message", message);
        body.put("details", details);
        body.put("path", path(request));
        return new ResponseEntity<>(body, status);
    }

    private boolean isN8nTransferPath(WebRequest request) {
        return path(request).matches("/api/n8n/atendimentos/[^/]+/transferir(-proximo)?-humano");
    }

    private String path(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }
}
