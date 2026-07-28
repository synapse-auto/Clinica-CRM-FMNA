package com.synapse.clinicafemina.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class V37MigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V37__permitir_notificacao_transferencia_ia.sql"
    );

    @Test
    void should_preserve_existing_notification_types_and_allow_ai_handoff() throws Exception {
        String sql = Files.readString(MIGRATION).toUpperCase();

        assertTrue(sql.contains("DROP CONSTRAINT IF EXISTS CHK_NOTIFICACAO_ATENDIMENTO_TIPO"));
        assertTrue(sql.contains("'NOVA_MENSAGEM'"));
        assertTrue(sql.contains("'ATENDIMENTO_ATRIBUIDO'"));
        assertTrue(sql.contains("'TRANSFERENCIA_IA'"));
        assertFalse(sql.matches("(?s).*\\b(DELETE|UPDATE|INSERT|TRUNCATE)\\b.*"));
    }
}
