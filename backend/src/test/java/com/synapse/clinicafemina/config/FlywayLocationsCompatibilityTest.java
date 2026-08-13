package com.synapse.clinicafemina.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayLocationsCompatibilityTest {

    private static final String COMMON_LOCATION = "classpath:db/migration";
    private static final String OPTIONAL_LOCATION = "classpath:db/migration-valores-consulta-medico";
    private static final String V42_SCRIPT =
            "V42__adicionar_atende_convenio_em_clinica_valores_consulta_medico.sql";
    private static final String ORIGINAL_V42_SHA256 =
            "F38430CD438589B6522ACD728B3062BC8C6E97EC3BB62000824C3F7CA9EA773F";
    private static final String V46_SCRIPT = "V46__reestruturar_medicos_consultas.sql";
    private static final String ORIGINAL_V46_SHA256 =
            "F65B33DE1E0C88C07EDD0392B9816258C333A393B2AD3CA440D66745B51FBE60";
    private static final Pattern VERSIONED_MIGRATION = Pattern.compile("^V([^_]+)__.+\\.sql$");
    private static final Path DB_RESOURCES = Path.of("src/main/resources/db");
    private static final Path COMMON_DIRECTORY = DB_RESOURCES.resolve("migration");
    private static final Path OPTIONAL_DIRECTORY = DB_RESOURCES.resolve("migration-valores-consulta-medico");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(FlywayPropertiesConfiguration.class);

    @Test
    void should_load_only_common_location_when_no_override_is_configured() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(FlywayProperties.class).getLocations())
                    .containsExactly(COMMON_LOCATION);
        });
    }

    @Test
    void should_load_optional_location_when_fmna_override_is_configured() {
        contextRunner
                .withPropertyValues("spring.flyway.locations=" + COMMON_LOCATION + "," + OPTIONAL_LOCATION)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(FlywayProperties.class).getLocations())
                            .containsExactly(COMMON_LOCATION, OPTIONAL_LOCATION);
                });
    }

    @Test
    void should_keep_v42_only_in_sibling_optional_location_with_original_bytes() throws Exception {
        List<Path> version42Files;
        try (var resources = Files.walk(DB_RESOURCES)) {
            version42Files = resources
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("V42__"))
                    .toList();
        }

        assertThat(version42Files).containsExactly(OPTIONAL_DIRECTORY.resolve(V42_SCRIPT));
        assertThat(COMMON_DIRECTORY.resolve(V42_SCRIPT)).doesNotExist();
        assertThat(OPTIONAL_DIRECTORY.getParent()).isEqualTo(COMMON_DIRECTORY.getParent());
        assertThat(OPTIONAL_DIRECTORY.startsWith(COMMON_DIRECTORY)).isFalse();
        assertThat(sha256(OPTIONAL_DIRECTORY.resolve(V42_SCRIPT))).isEqualTo(ORIGINAL_V42_SHA256);
    }

    @Test
    void should_keep_applied_v46_only_in_optional_location_with_original_bytes() throws Exception {
        List<Path> version46Files;
        try (var resources = Files.walk(DB_RESOURCES)) {
            version46Files = resources
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("V46__"))
                    .toList();
        }

        assertThat(version46Files).containsExactly(OPTIONAL_DIRECTORY.resolve(V46_SCRIPT));
        assertThat(COMMON_DIRECTORY.resolve(V46_SCRIPT)).doesNotExist();
        assertThat(sha256(OPTIONAL_DIRECTORY.resolve(V46_SCRIPT))).isEqualTo(ORIGINAL_V46_SHA256);
    }

    @Test
    void should_not_repeat_versions_across_configurable_locations() throws Exception {
        Map<String, List<Path>> migrationsByVersion = new HashMap<>();
        try (var resources = Files.walk(DB_RESOURCES)) {
            resources
                    .filter(Files::isRegularFile)
                    .filter(path -> path.startsWith(COMMON_DIRECTORY) || path.startsWith(OPTIONAL_DIRECTORY))
                    .forEach(path -> {
                        var matcher = VERSIONED_MIGRATION.matcher(path.getFileName().toString());
                        if (matcher.matches()) {
                            migrationsByVersion.computeIfAbsent(matcher.group(1), ignored -> new java.util.ArrayList<>())
                                    .add(path);
                        }
                    });
        }

        var duplicatedVersions = migrationsByVersion.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toList();

        assertThat(duplicatedVersions).isEmpty();
    }

    @Test
    void should_keep_environment_override_documented_in_application_configuration() throws Exception {
        String applicationYaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(applicationYaml)
                .contains("SPRING_FLYWAY_LOCATIONS")
                .contains("classpath:db/migration");
    }

    private static String sha256(Path path) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
        return HexFormat.of().withUpperCase().formatHex(digest);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(FlywayProperties.class)
    static class FlywayPropertiesConfiguration {
    }
}
