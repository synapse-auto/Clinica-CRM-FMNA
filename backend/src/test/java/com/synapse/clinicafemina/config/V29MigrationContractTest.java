package com.synapse.clinicafemina.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class V29MigrationContractTest {

    @Test
    void should_add_only_nullable_template_metadata_after_v28() throws Exception {
        Path migration = Path.of(
                "src/main/resources/db/migration/V29__adicionar_metadados_template_whatsapp.sql"
        );
        String sql = Files.readString(migration).toUpperCase();

        assertTrue(Files.exists(Path.of(
                "src/main/resources/db/migration/V28__corrigir_unicidade_agendamento_externo.sql"
        )));
        assertTrue(sql.contains("ALTER TABLE MENSAGEM"));
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS TEMPLATE_NOME VARCHAR(512)"));
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS TEMPLATE_IDIOMA VARCHAR(20)"));
        assertTrue(!sql.contains("DROP "));
        assertTrue(!sql.contains("DELETE "));
        assertTrue(!sql.contains("NOT NULL"));
    }
}
