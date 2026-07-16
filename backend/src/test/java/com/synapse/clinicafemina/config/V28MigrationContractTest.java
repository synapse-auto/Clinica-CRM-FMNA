package com.synapse.clinicafemina.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V28MigrationContractTest {

    private static final String MIGRATION =
            "db/migration/V28__corrigir_unicidade_agendamento_externo.sql";

    @Test
    void should_replace_global_external_id_constraint_with_partial_composite_identity()
            throws IOException {
        String sql = readMigration();
        String normalized = sql.replaceAll("\\s+", " ").toLowerCase();

        assertTrue(normalized.contains(
                "group by clinica_id, external_source, external_id having count(*) > 1"));
        assertTrue(normalized.contains(
                "drop constraint if exists agendamento_external_id_key"));
        assertTrue(normalized.contains(
                "create unique index ux_agendamento_clinica_source_external_id "
                        + "on agendamento (clinica_id, external_source, external_id) "
                        + "where external_source is not null"));
        assertFalse(normalized.contains("drop index"));
    }

    @Test
    void should_keep_migration_additive_and_free_of_data_rewrites() throws IOException {
        String sql = readMigration();
        String normalized = sql.replaceAll("(?m)^\\s*--.*$", " ")
                .replaceAll("\\s+", " ")
                .toLowerCase();

        assertFalse(normalized.matches(".*\\b(delete|update|insert|truncate)\\b.*"));
        assertFalse(normalized.contains("alter column"));
        assertFalse(normalized.contains("drop table"));
    }

    private String readMigration() throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(MIGRATION)) {
            assertNotNull(input, "Migration V28 deve estar disponivel no classpath de testes");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
