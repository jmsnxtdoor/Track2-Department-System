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
    void declaresHttpBasicSchemeAndDocumentsOnlyProtectedApiOperations() {
        OpenApiConfig config = new OpenApiConfig();
        OpenAPI openApi = config.track2OpenApi();
        Operation students = new Operation();
        Operation health = new Operation();
        Operation swaggerUi = new Operation();
        openApi.path("/api/students", new PathItem().get(students));
        openApi.path("/api/health", new PathItem().get(health));
        openApi.path("/swagger-ui.html", new PathItem().get(swaggerUi));

        config.basicAuthForProtectedApiOperations().customise(openApi);

        SecurityScheme scheme = openApi.getComponents().getSecuritySchemes().get("basicAuth");
        assertEquals(SecurityScheme.Type.HTTP, scheme.getType());
        assertEquals("basic", scheme.getScheme());
        assertEquals("basicAuth", students.getSecurity().get(0).keySet().iterator().next());
        assertNull(health.getSecurity());
        assertNull(swaggerUi.getSecurity());
        assertTrue(openApi.getComponents().getSecuritySchemes().containsKey("basicAuth"));
    }
}
