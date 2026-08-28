package com.synapse.clinicafemina.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers(disabledWithoutDocker = true)
class V50VideoMessagePostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void migrateAndCreateAttendance() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = connection()) {
            execute(connection, """
                    INSERT INTO clinica (id, nome, slug, razao_social, cnpj, email_contato, telefone_contato)
                    VALUES (1, 'Clinica Teste', 'clinica-teste', 'Clinica Teste LTDA',
                            '00000000000191', 'teste@example.test', '5511999999999')
                    """);
            execute(connection, """
                    INSERT INTO paciente (id, clinica_id, nome, nome_busca, telefone, telefone_normalizado)
                    VALUES (1, 1, convert_to('Paciente Teste', 'UTF8'), 'PACIENTE TESTE',
                            convert_to('5511999999999', 'UTF8'), '5511999999999')
                    """);
            execute(connection, """
                    INSERT INTO atendimento (id, clinica_id, paciente_id, status, tratado_por_ia)
                    VALUES (1, 1, 1, 'ATIVO', TRUE)
                    """);
        }
    }

    @Test
    void should_accept_video_and_continue_rejecting_unknown_message_types() throws Exception {
        try (Connection connection = connection()) {
            assertDoesNotThrow(() -> insertMessage(connection, "VIDEO"));
            assertThrows(SQLException.class, () -> insertMessage(connection, "TIPO_INVALIDO"));
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()
        );
    }

    private static void insertMessage(Connection connection, String tipoMedia) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO mensagem
                    (atendimento_id, direcao, remetente, tipo_media, conteudo_previa,
                     whatsapp_status, chave_criptografia_id)
                VALUES (1, 'ENTRADA', 'PACIENTE', ?, 'Midia recebida', 'RECEBIDA', 'v1')
                """)) {
            statement.setString(1, tipoMedia);
            statement.executeUpdate();
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }
}
