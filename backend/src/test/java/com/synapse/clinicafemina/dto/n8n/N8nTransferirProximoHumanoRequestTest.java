package com.synapse.clinicafemina.dto.n8n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.clinicafemina.exception.N8nTransferPayloadException;
import java.util.List;
import org.junit.jupiter.api.Test;

class N8nTransferirProximoHumanoRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void should_accept_numeric_array_and_preserve_order() throws Exception {
        var request = read("{\"atendentesIds\":[11,10]}");

        assertEquals(List.of(11L, 10L), request.idsOrdenados());
    }

    @Test
    void should_accept_numeric_strings() throws Exception {
        var request = read("{\"atendentesIds\":[\"10\",\"11\"]}");

        assertEquals(List.of(10L, 11L), request.idsOrdenados());
    }

    @Test
    void should_accept_serialized_json_array() throws Exception {
        var request = read("{\"atendentesIds\":\"[10,11]\"}");

        assertEquals(List.of(10L, 11L), request.idsOrdenados());
    }

    @Test
    void should_accept_supported_aliases() throws Exception {
        assertEquals(List.of(10L, 11L), read("{\"atendentes_ids\":[10,11]}").idsOrdenados());
        assertEquals(List.of(10L, 11L), read("{\"atendenteIds\":[10,11]}").idsOrdenados());
        assertEquals(List.of(10L, 11L), read("{\"idsAtendentes\":[10,11]}").idsOrdenados());
    }

    @Test
    void should_reject_empty_repeated_oversized_and_invalid_lists_with_field_details() throws Exception {
        assertFieldError("{\"atendentesIds\":[]}", "Informe ao menos um atendente para o rodízio.");
        assertFieldError("{\"atendentesIds\":[10,10]}", "Não repita atendentes no rodízio.");
        assertFieldError("{\"atendentesIds\":[0]}", "Informe uma lista de IDs numéricos positivos");
        assertFieldError("{\"atendentesIds\":[1.5]}", "Informe uma lista de IDs numéricos positivos");
        assertFieldError("{\"atendentesIds\":[true]}", "Informe uma lista de IDs numéricos positivos");
        assertFieldError("{\"atendentesIds\":[{}]}", "Informe uma lista de IDs numéricos positivos");
        assertFieldError("{\"atendentesIds\":[\"abc\"]}", "Informe uma lista de IDs numéricos positivos");
        assertFieldError("{\"atendentesIds\":\"não é JSON\"}", "Informe uma lista de IDs numéricos positivos");
        assertFieldError(
                "{\"atendentesIds\":[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21]}",
                "Informe no máximo 20 atendentes."
        );
    }

    @Test
    void should_trim_and_validate_idempotency_key() throws Exception {
        var request = read("{\"atendentesIds\":[10,11]}");

        assertEquals("rodizio-1", request.validarIdempotencyKey("  rodizio-1  "));

        N8nTransferPayloadException missing = assertThrows(
                N8nTransferPayloadException.class,
                () -> request.validarIdempotencyKey("   ")
        );
        assertEquals("Informe o header Idempotency-Key.", missing.getDetails().get("idempotencyKey"));
    }

    private N8nTransferirProximoHumanoRequest read(String json) throws Exception {
        return objectMapper.readValue(json, N8nTransferirProximoHumanoRequest.class);
    }

    private void assertFieldError(String json, String expectedMessagePart) throws Exception {
        N8nTransferPayloadException exception = assertThrows(
                N8nTransferPayloadException.class,
                () -> read(json).idsOrdenados()
        );
        String message = exception.getDetails().get("atendentesIds");
        org.junit.jupiter.api.Assertions.assertTrue(message.contains(expectedMessagePart), message);
    }
}
