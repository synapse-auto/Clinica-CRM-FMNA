package com.synapse.clinicafemina.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "springdoc.api-docs", name = "enabled", havingValue = "true")
public class OpenApiConfig {

    @Bean
    OpenAPI clinicaCrmOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Clínica CRM API")
                        .version("0.0.1-SNAPSHOT")
                        .description("API do CRM multiclínica para atendimentos, pacientes, equipe, agenda, mensageria e integrações. "
                                + "APIs CRM usam Bearer JWT; callbacks N8N possuem contrato próprio; webhooks de providers não são expostos nesta interface."))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }

    @Bean
    GroupedOpenApi crmOpenApiGroup() {
        return GroupedOpenApi.builder()
                .group("crm")
                .pathsToMatch("/api/auth/**", "/api/atendimentos/**", "/api/pacientes/**", "/api/equipe/**",
                        "/api/admin/usuarios/**", "/api/agenda/**", "/api/agendamentos/**", "/api/tags/**",
                        "/api/lembretes/**", "/api/mensagens-rapidas/**", "/api/configuracoes/**", "/api/**")
                .pathsToExclude("/api/n8n/**", "/api/webhooks/**", "/ws/**")
                .build();
    }

    @Bean
    GroupedOpenApi n8nOpenApiGroup() {
        return GroupedOpenApi.builder()
                .group("n8n")
                .pathsToMatch("/api/n8n/**")
                .pathsToExclude("/api/webhooks/**", "/ws/**")
                .build();
    }
}
