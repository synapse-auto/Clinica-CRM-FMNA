package com.synapse.clinicafemina.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class V50MigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V50__permitir_video_em_mensagem.sql"
    );

    @Test
    void should_allow_video_without_removing_previously_supported_message_types() throws Exception {
        String sql = Files.readString(MIGRATION).toUpperCase();

        assertThat(sql)
                .contains("DROP CONSTRAINT IF EXISTS CHK_MENSAGEM_TIPO_MEDIA")
                .contains("ADD CONSTRAINT CHK_MENSAGEM_TIPO_MEDIA")
                .contains("'VIDEO'")
                .contains("'TEXTO'")
                .contains("'AUDIO'")
                .contains("'IMAGEM'")
                .contains("'DOCUMENTO'")
                .contains("'TEMPLATE'")
                .contains("'OUTRO'")
                .contains("'AI_HANDOFF_ENDED'")
                .contains("'HUMAN_HANDOFF_START'")
                .contains("'AI_HANDOFF_SUMMARY'")
                .contains("NOT VALID")
                .contains("VALIDATE CONSTRAINT CHK_MENSAGEM_TIPO_MEDIA")
                .doesNotContain("DELETE ")
                .doesNotContain("UPDATE ")
                .doesNotContain("INSERT ")
                .doesNotContain("TRUNCATE ");
    }
}
