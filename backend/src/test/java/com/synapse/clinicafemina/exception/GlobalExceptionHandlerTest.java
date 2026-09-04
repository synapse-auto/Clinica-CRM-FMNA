package com.synapse.clinicafemina.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void should_return_conflict_for_known_transfer_idempotency_constraint_without_sql_details() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "insert failed",
                new RuntimeException("duplicate key uq_transferencia_atendimento_idempotency_key")
        );

        ResponseEntity<Object> response = handler.handleDataIntegrity(exception, transferRequest());
        Map<?, ?> body = (Map<?, ?>) response.getBody();

        assertEquals(409, response.getStatusCode().value());
        assertEquals("IDEMPOTENCY_CONFLICT", body.get("code"));
        assertFalse(body.toString().contains("duplicate key"));
        assertFalse(body.toString().contains("uq_transferencia_atendimento_idempotency_key"));
    }

    @Test
    void should_keep_unknown_constraint_as_sanitized_bad_request() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "insert failed",
                new RuntimeException("constraint paciente_fk")
        );

        ResponseEntity<Object> response = handler.handleDataIntegrity(exception, transferRequest());
        Map<?, ?> body = (Map<?, ?>) response.getBody();

        assertEquals(400, response.getStatusCode().value());
        assertEquals("Dados violam uma regra de validação.", body.get("message"));
        assertFalse(body.containsKey("code"));
        assertFalse(body.toString().contains("paciente_fk"));
    }

    @Test
    void should_return_structured_conflict_for_ambiguous_whatsapp_patient_identity() {
        ResponseEntity<Object> response = handler.handleWhatsappPatientIdentityConflict(
                new WhatsappPatientIdentityConflictException("Conflito de identidade."),
                transferRequest()
        );
        Map<?, ?> body = (Map<?, ?>) response.getBody();

        assertEquals(409, response.getStatusCode().value());
        assertEquals(WhatsappPatientIdentityConflictException.CODE, body.get("code"));
        assertEquals("Conflito de identidade.", body.get("message"));
    }

    private WebRequest transferRequest() {
        WebRequest request = mock(WebRequest.class);
        when(request.getDescription(false))
                .thenReturn("uri=/api/n8n/atendimentos/30/transferir-proximo-humano");
        return request;
    }
}
