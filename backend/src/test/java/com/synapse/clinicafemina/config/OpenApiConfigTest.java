package com.synapse.clinicafemina.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenApiConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(OpenApiConfig.class, ApiDocsSecurityConfig.class);

    @Test
    void should_expose_bearer_jwt_scheme_and_named_groups() {
        OpenApiConfig config = new OpenApiConfig();

        assertEquals("Clínica CRM API", config.clinicaCrmOpenApi().getInfo().getTitle());
        assertNotNull(config.clinicaCrmOpenApi().getComponents().getSecuritySchemes().get("bearerAuth"));
        assertEquals("crm", config.crmOpenApiGroup().getGroup());
        assertEquals("n8n", config.n8nOpenApiGroup().getGroup());
    }

    @Test
    void should_fail_fast_only_for_missing_or_weak_docs_credentials() {
        assertThrows(IllegalStateException.class, () -> ApiDocsSecurityConfig.validarCredenciais("", "senha-forte-123"));
        assertThrows(IllegalStateException.class, () -> ApiDocsSecurityConfig.validarCredenciais("docs", ""));
        assertThrows(IllegalStateException.class, () -> ApiDocsSecurityConfig.validarCredenciais("docs", "docs"));
        ApiDocsSecurityConfig.validarCredenciais("docs-local", "senha-local-forte-123");
    }

    @Test
    void should_not_register_docs_configuration_beans_when_docs_are_disabled() {
        contextRunner.withPropertyValues("springdoc.api-docs.enabled=false")
                .run(context -> {
                    assertFalse(context.containsBean("clinicaCrmOpenApi"));
                    assertFalse(context.containsBean("crmOpenApiGroup"));
                    assertFalse(context.containsBean("n8nOpenApiGroup"));
                    assertFalse(context.containsBean("apiDocsSecurityFilterChain"));
                });
    }
}
