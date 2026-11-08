package edu.department.portal;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI track2OpenApi() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes(
                        "basicAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic"))
                        .addSecuritySchemes(
                                "integrationApiKey",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-API-Key")));
    }

    @Bean
    OpenApiCustomizer securitySchemesForApiOperations() {
        return openApi -> openApi.getPaths().forEach((path, pathItem) -> {
            if (path.startsWith("/api/integration/")) {
                pathItem.readOperations().forEach(operation ->
                        operation.addSecurityItem(new SecurityRequirement().addList("integrationApiKey")));
            } else if (path.startsWith("/api/") && !path.equals("/api/health")) {
                pathItem.readOperations().forEach(operation ->
                        operation.addSecurityItem(new SecurityRequirement().addList("basicAuth")));
            }
        });
    }
}
