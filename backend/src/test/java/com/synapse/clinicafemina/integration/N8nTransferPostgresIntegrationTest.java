package com.synapse.clinicafemina.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class N8nTransferPostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void migrateDatabase() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @Test
    void should_persist_complete_n8n_handoff_and_rollback_on_invalid_notification() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            criarDadosBase(connection);

            long transferenciaId = inserirTransferencia(connection);
            long handoffEndedId = inserirMensagem(connection, "SISTEMA", "SISTEMA", "AI_HANDOFF_ENDED");
            inserirMensagem(connection, "SISTEMA", "SISTEMA", "HUMAN_HANDOFF_START");
            inserirMensagem(connection, "SISTEMA", "IA", "AI_HANDOFF_SUMMARY");
            inserirNotificacao(connection, handoffEndedId, "TRANSFERENCIA_IA");
            inserirNotificacao(connection, handoffEndedId, "NOVA_MENSAGEM");

            assertEquals(1, contar(connection, "transferencia_atendimento"));
            assertEquals(3, contar(connection, "mensagem"));
            assertEquals(2, contar(connection, "notificacao_atendimento"));
            assertEquals(1L, transferenciaId);

            connection.setAutoCommit(false);
            inserirMensagem(connection, "SISTEMA", "SISTEMA", "AI_HANDOFF_ENDED");
            assertThrows(SQLException.class,
                    () -> inserirNotificacao(connection, handoffEndedId, "TIPO_INVALIDO"));
            connection.rollback();
            connection.setAutoCommit(true);

            assertEquals(3, contar(connection, "mensagem"));
            assertEquals(2, contar(connection, "notificacao_atendimento"));
            assertThrows(SQLException.class,
                    () -> inserirMensagem(connection, "SISTEMA", "SISTEMA", "TIPO_INVALIDO"));
        }
    }

    private void criarDadosBase(Connection connection) throws SQLException {
        executar(connection, """
                INSERT INTO clinica (id, nome, slug, razao_social, cnpj, email_contato, telefone_contato)
                VALUES (1, 'Clinica Teste', 'clinica-teste', 'Clinica Teste LTDA',
                        '00000000000191', 'teste@example.test', '5511999999999')
                """);
        executar(connection, """
                INSERT INTO usuario (id, clinica_id, nome, email, senha_hash, perfil)
                VALUES (1, 1, 'Gestor Teste', 'gestor@example.test', 'hash', 'GESTOR')
                """);
        executar(connection, """
                INSERT INTO paciente (id, clinica_id, nome, nome_busca, telefone, telefone_normalizado)
                VALUES (1, 1, convert_to('Paciente Teste', 'UTF8'), 'Paciente Teste',
                        convert_to('5511999999999', 'UTF8'), '5511999999999')
                """);
        executar(connection, """
                INSERT INTO atendimento (id, clinica_id, paciente_id, atendente_principal_id, status, tratado_por_ia)
                VALUES (1, 1, 1, 1, 'ATIVO', FALSE)
                """);
    }

    private long inserirTransferencia(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO transferencia_atendimento
                    (atendimento_id, para_usuario_id, transferido_por, motivo)
                VALUES (1, 1, 1, 'Transferido pelo fluxo N8N')
                RETURNING id
                """)) {
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private long inserirMensagem(Connection connection, String direcao, String remetente, String tipoMedia)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO mensagem
                    (atendimento_id, direcao, remetente, tipo_media, conteudo, conteudo_previa,
                     whatsapp_status, chave_criptografia_id)
                VALUES (1, ?, ?, ?, convert_to('Evento interno', 'UTF8'), 'Evento interno', 'INTERNO', 'v1')
                RETURNING id
                """)) {
            statement.setString(1, direcao);
            statement.setString(2, remetente);
            statement.setString(3, tipoMedia);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private void inserirNotificacao(Connection connection, long mensagemId, String tipo) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO notificacao_atendimento (usuario_id, atendimento_id, mensagem_id, tipo, descricao)
                VALUES (1, 1, ?, ?, 'Atendimento transferido para humano')
                """)) {
            statement.setLong(1, mensagemId);
            statement.setString(2, tipo);
            statement.executeUpdate();
        }
    }

    private int contar(Connection connection, String tabela) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + tabela);
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getInt(1);
        }
    }

    private void executar(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }
}
