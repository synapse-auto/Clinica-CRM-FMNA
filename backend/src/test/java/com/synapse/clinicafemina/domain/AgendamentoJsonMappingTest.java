package com.synapse.clinicafemina.domain;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AgendamentoJsonMappingTest {

    @Test
    void should_bind_external_payload_as_postgresql_json() throws Exception {
        JdbcTypeCode annotation = Agendamento.class
                .getDeclaredField("externalPayload")
                .getAnnotation(JdbcTypeCode.class);

        assertNotNull(annotation);
        assertEquals(SqlTypes.JSON, annotation.value());
    }
}
