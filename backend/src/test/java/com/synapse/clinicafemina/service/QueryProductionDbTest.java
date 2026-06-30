package com.synapse.clinicafemina.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Diagnóstico manual opcional. Não roda na suíte normal e não contém credenciais.
 */
class QueryProductionDbTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "PROD_DB_DIAGNOSTIC_ENABLED", matches = "true")
    void queryProductionDbWhenExplicitlyEnabled() throws Exception {
        String url = requiredEnv("PROD_DB_DIAGNOSTIC_URL");
        String user = requiredEnv("PROD_DB_DIAGNOSTIC_USER");
        String pass = requiredEnv("PROD_DB_DIAGNOSTIC_PASSWORD");

        try (Connection conn = DriverManager.getConnection(url, user, pass);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS total FROM clinica")) {
            assertTrue(rs.next());
            assertTrue(rs.getLong("total") >= 0);
        }
    }

    private String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " não configurada");
        }
        return value;
    }
}
