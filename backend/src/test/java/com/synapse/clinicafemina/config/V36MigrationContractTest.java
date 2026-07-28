package com.synapse.clinicafemina.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class V36MigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V36__permitir_eventos_sistema_transferencia.sql"
    );

    @Test
    void should_allow_system_handoff_events_without_rewriting_existing_messages() throws Exception {
        String sql = Files.readString(MIGRATION).toUpperCase();

        assertTrue(sql.contains("DROP CONSTRAINT IF EXISTS CHK_MENSAGEM_DIRECAO"));
        assertTrue(sql.contains("'SISTEMA'"));
        assertTrue(sql.contains("DROP CONSTRAINT IF EXISTS CHK_MENSAGEM_TIPO_MEDIA"));
        assertTrue(sql.contains("'AI_HANDOFF_ENDED'"));
        assertTrue(sql.contains("'HUMAN_HANDOFF_START'"));
        assertTrue(sql.contains("'AI_HANDOFF_SUMMARY'"));
        assertTrue(sql.contains("'OUTRO'"));
        assertFalse(sql.matches("(?s).*\\b(DELETE|UPDATE|INSERT|TRUNCATE)\\b.*"));
    }
}
