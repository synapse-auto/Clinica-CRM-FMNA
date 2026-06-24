package com.synapse.clinicafemina.service;
 
import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
 
class QueryProductionDbTest {
 
    @Test
    void queryProductionDb() {
        String url = "jdbc:postgresql://2.25.131.139:5432/clinicafemina";
        String user = "postgres";
        String pass = "junior@88jr83w88";
 
        try {
            Class.forName("org.postgresql.Driver");
            try (Connection conn = DriverManager.getConnection(url, user, pass);
                 Statement stmt = conn.createStatement()) {
                
                System.out.println("=== CLINICAS IN PRODUCTION ===");
                try (ResultSet rs = stmt.executeQuery("SELECT id, nome, slug, external_provider, whatsapp_phone_number_id FROM clinica")) {
                    while (rs.next()) {
                        System.out.printf("Clinica: ID=%d, Nome=%s, Slug=%s, Provider=%s, PhoneId=%s%n",
                                rs.getLong("id"), rs.getString("nome"), rs.getString("slug"),
                                rs.getString("external_provider"), rs.getString("whatsapp_phone_number_id"));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
