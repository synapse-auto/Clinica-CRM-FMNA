package com.synapse.clinicafemina.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayLocationsCompatibilityIntegrationTest {

    private static final String COMMON_LOCATION = "classpath:db/migration";
    private static final String OPTIONAL_LOCATION = "classpath:db/migration-valores-consulta-medico";
    private static final String LATER_COMMON_LOCATION = "classpath:db/migration-common-after-optional-test";
    private static final String LATER_COMMON_VERSION = "48";
    private static final int FMNA_V42_CHECKSUM = 1792192730;

    @Test
    void should_start_ultramedical_context_without_optional_table_or_v42() throws Exception {
        Database database = database("ultramedical_locations");
        execute(database, "CREATE TABLE existing_schema_marker (id BIGINT PRIMARY KEY)");

        contextRunner(database, COMMON_LOCATION, "41").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(Flyway.class);
        });

        assertThat(migrationCount(database, "42")).isZero();
        assertThat(tableExists(database, "CLINICA_VALORES_CONSULTA_MEDICO")).isFalse();

        migrate(database, LATER_COMMON_LOCATION);
        assertThat(migrationCount(database, LATER_COMMON_VERSION)).isOne();
        assertThat(tableExists(database, "FLYWAY_COMMON_AFTER_OPTIONAL_PROBE")).isTrue();
    }

    @Test
    void should_start_fmna_context_with_optional_v42_and_later_common_migrations() throws Exception {
        Database database = database("fmna_locations");
        execute(database, "CREATE TABLE clinica_valores_consulta_medico (id BIGINT PRIMARY KEY)");

        contextRunner(database, COMMON_LOCATION + "," + OPTIONAL_LOCATION, "42").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(Flyway.class);
        });

        assertThat(migrationCount(database, "42")).isOne();
        assertThat(migrationChecksum(database, "42")).isEqualTo(FMNA_V42_CHECKSUM);
        assertThat(columnExists(database, "CLINICA_VALORES_CONSULTA_MEDICO", "ATENDE_CONVENIO")).isTrue();

        migrate(database, OPTIONAL_LOCATION, LATER_COMMON_LOCATION);
        assertThat(migrationCount(database, LATER_COMMON_VERSION)).isOne();
        assertThat(tableExists(database, "FLYWAY_COMMON_AFTER_OPTIONAL_PROBE")).isTrue();
    }

    private static ApplicationContextRunner contextRunner(Database database, String locations, String target) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DataSourceAutoConfiguration.class,
                        FlywayAutoConfiguration.class
                ))
                .withPropertyValues(
                        "spring.datasource.url=" + database.url(),
                        "spring.datasource.username=" + database.username(),
                        "spring.datasource.password=" + database.password(),
                        "spring.datasource.driver-class-name=org.h2.Driver",
                        "spring.flyway.locations=" + locations,
                        "spring.flyway.baseline-on-migrate=true",
                        "spring.flyway.baseline-version=41",
                        "spring.flyway.target=" + target
                );
    }

    private static void migrate(Database database, String... locations) {
        Flyway.configure()
                .dataSource(database.url(), database.username(), database.password())
                .locations(locations)
                .baselineOnMigrate(true)
                .baselineVersion("41")
                .load()
                .migrate();
    }

    private static Database database(String name) {
        return new Database(
                "jdbc:h2:mem:" + name + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
    }

    private static void execute(Database database, String sql) throws SQLException {
        try (Connection connection = connection(database); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static int migrationCount(Database database, String version) throws SQLException {
        try (Connection connection = connection(database);
             var statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM flyway_schema_history WHERE version = ? AND success = true"
             )) {
            statement.setString(1, version);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static int migrationChecksum(Database database, String version) throws SQLException {
        try (Connection connection = connection(database);
             var statement = connection.prepareStatement(
                     "SELECT checksum FROM flyway_schema_history WHERE version = ? AND success = true"
             )) {
            statement.setString(1, version);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getInt(1);
            }
        }
    }

    private static boolean tableExists(Database database, String table) throws SQLException {
        try (Connection connection = connection(database);
             var statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM information_schema.tables " +
                             "WHERE lower(table_schema) = 'public' AND lower(table_name) = lower(?)"
             )) {
            statement.setString(1, table);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1) == 1;
            }
        }
    }

    private static boolean columnExists(Database database, String table, String column) throws SQLException {
        try (Connection connection = connection(database);
             var statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM information_schema.columns " +
                             "WHERE lower(table_schema) = 'public' " +
                             "AND lower(table_name) = lower(?) AND lower(column_name) = lower(?)"
             )) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1) == 1;
            }
        }
    }

    private static Connection connection(Database database) throws SQLException {
        return DriverManager.getConnection(database.url(), database.username(), database.password());
    }

    private record Database(String url, String username, String password) {
    }
}
