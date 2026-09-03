package com.synapse.clinicafemina.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true",
        "API_DOCS_USERNAME=docs-local",
        "API_DOCS_PASSWORD=senha-local-forte-123"
})
class ApiDocsSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void should_require_docs_basic_auth_and_expose_openapi_groups_only_after_authentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v3/api-docs").with(httpBasic("docs-local", "credencial-incorreta")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/v3/api-docs").with(httpBasic("docs-local", "senha-local-forte-123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("Clínica CRM API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/atendimentos/{atendimentoId}/mensagens']").exists())
                .andExpect(jsonPath("$.paths['/api/pacientes/importacoes/csv/preview'].post.requestBody.content['multipart/form-data']").exists())
                .andExpect(jsonPath("$.paths['/api/webhooks/whatsapp']").doesNotExist());

        mockMvc.perform(get("/v3/api-docs/swagger-config").with(httpBasic("docs-local", "senha-local-forte-123")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs.yaml").with(httpBasic("docs-local", "senha-local-forte-123")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui/index.html").with(httpBasic("docs-local", "senha-local-forte-123")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v3/api-docs/crm").with(httpBasic("docs-local", "senha-local-forte-123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/atendimentos']").exists());
        mockMvc.perform(get("/v3/api-docs/n8n").with(httpBasic("docs-local", "senha-local-forte-123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/n8n/atendimentos/{atendimentoId}/encerrar']").exists())
                .andExpect(jsonPath("$.paths['/api/n8n/atendimentos/{atendimentoId}/transferir-humano']").exists())
                .andExpect(jsonPath("$.paths['/api/n8n/atendimentos/{atendimentoId}/transferir-proximo-humano'].post.parameters[?(@.name == 'Idempotency-Key')]").exists());
    }

    @Test
    void should_not_grant_crm_access_with_docs_basic_credentials() throws Exception {
        mockMvc.perform(get("/api/atendimentos").with(httpBasic("docs-local", "senha-local-forte-123")))
                .andExpect(status().isUnauthorized());
    }
}
