package com.synapse.clinicafemina.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

@Testcontainers(disabledWithoutDocker = true)
class IniciarAtendimentoPostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void migrateDatabase() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @Test
    void should_serialize_manual_start_and_expose_only_one_active_attendance_to_retry()
            throws Exception {
        try (Connection setup = connection()) {
            criarDadosBase(setup);
        }

        try (Connection first = connection();
             Connection retry = connection();
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            first.setAutoCommit(false);
            retry.setAutoCommit(false);
            bloquearClinica(first);
            criarPacienteEAtendimento(first);

            Future<Counts> retryResult = executor.submit(() -> {
                bloquearClinica(retry);
                return new Counts(
                        contar(retry, "paciente", "clinica_id = 1 AND telefone_normalizado = '5583999999999'"),
                        contar(retry, "atendimento", "clinica_id = 1 AND paciente_id = 1 AND status = 'ATIVO'")
                );
            });

            Thread.sleep(250);
            assertFalse(retryResult.isDone(), "O retry deve aguardar o lock da clinica");
            first.commit();

            Counts counts = assertTimeoutPreemptively(
                    Duration.ofSeconds(5),
                    () -> retryResult.get(5, TimeUnit.SECONDS)
            );
            assertEquals(1, counts.pacientes());
            assertEquals(1, counts.atendimentosAtivos());
            retry.rollback();
        }
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()
        );
    }

    private static void criarDadosBase(Connection connection) throws Exception {
        executar(connection, """
                INSERT INTO clinica (id, nome, slug, razao_social, cnpj, email_contato, telefone_contato)
                VALUES (1, 'Clinica Teste', 'clinica-teste', 'Clinica Teste LTDA',
                        '00000000000191', 'teste@example.test', '5511999999999')
                """);
        executar(connection, """
                INSERT INTO usuario (id, clinica_id, nome, email, senha_hash, perfil)
                VALUES (1, 1, 'Gestor Teste', 'gestor@example.test', 'hash', 'GESTOR')
                """);
    }

    private static void criarPacienteEAtendimento(Connection connection) throws Exception {
        executar(connection, """
                INSERT INTO paciente
                    (id, clinica_id, nome, nome_busca, telefone, telefone_normalizado,
                     external_source, external_id, status, criado_por, atualizado_por)
                VALUES
                    (1, 1, convert_to('Contato WhatsApp', 'UTF8'), 'CONTATO WHATSAPP',
                     convert_to('+5583999999999', 'UTF8'), '5583999999999',
                     'WHATSAPP', '5583999999999', 'EM_ATENDIMENTO', 1, 1)
                """);
        executar(connection, """
                INSERT INTO atendimento
                    (id, clinica_id, paciente_id, atendente_principal_id, status,
                     tratado_por_ia, humano_desde, nao_lidas)
                VALUES (1, 1, 1, 1, 'ATIVO', FALSE, NOW(), 0)
                """);
    }

    private static void bloquearClinica(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM clinica WHERE id = 1 FOR UPDATE"
        )) {
            statement.executeQuery();
        }
    }

    private static int contar(Connection connection, String tabela, String filtro) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM " + tabela + " WHERE " + filtro
        ); ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getInt(1);
        }
    }

    private static void executar(Connection connection, String sql) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }

    private record Counts(int pacientes, int atendimentosAtivos) {
    }
}
