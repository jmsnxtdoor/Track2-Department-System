package edu.department.portal;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiConfigTest {
    @Test
    void declaresBasicAndApiKeySchemesForTheirRespectiveOperations() {
        OpenApiConfig config = new OpenApiConfig();
        OpenAPI openApi = config.track2OpenApi();
        Operation students = new Operation();
        Operation health = new Operation();
        Operation swaggerUi = new Operation();
        Operation integrationSummary = new Operation();
        Operation integrationDataSummary = new Operation();
        openApi.path("/api/students", new PathItem().get(students));
        openApi.path("/api/health", new PathItem().get(health));
        openApi.path("/swagger-ui.html", new PathItem().get(swaggerUi));
        openApi.path("/api/integration/v1/summary", new PathItem().get(integrationSummary));
        openApi.path("/api/integration/v1/data-summary", new PathItem().get(integrationDataSummary));

        config.securitySchemesForApiOperations().customise(openApi);

        SecurityScheme basicAuth = openApi.getComponents().getSecuritySchemes().get("basicAuth");
        SecurityScheme apiKey = openApi.getComponents().getSecuritySchemes().get("integrationApiKey");
        assertEquals(SecurityScheme.Type.HTTP, basicAuth.getType());
        assertEquals("basic", basicAuth.getScheme());
        assertEquals(SecurityScheme.Type.APIKEY, apiKey.getType());
        assertEquals(SecurityScheme.In.HEADER, apiKey.getIn());
        assertEquals("X-API-Key", apiKey.getName());
        assertEquals("basicAuth", students.getSecurity().get(0).keySet().iterator().next());
        assertEquals("integrationApiKey", integrationSummary.getSecurity().get(0).keySet().iterator().next());
        assertEquals("integrationApiKey", integrationDataSummary.getSecurity().get(0).keySet().iterator().next());
        assertNull(health.getSecurity());
        assertNull(swaggerUi.getSecurity());
        assertTrue(openApi.getComponents().getSecuritySchemes().containsKey("integrationApiKey"));
    }
}
