package edu.department.portal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IntegrationApiController.class)
@Import(IntegrationSecurityConfig.class)
class IntegrationApiTest {
    private static final String TEST_KEY = UUID.randomUUID().toString();

    @Autowired
    private MockMvc mvc;

    @DynamicPropertySource
    static void integrationKey(DynamicPropertyRegistry registry) {
        registry.add("track2.integration.shared-api-key", () -> TEST_KEY);
    }

    @Test
    void requiresTheConfiguredSharedKey() throws Exception {
        mvc.perform(get("/api/integration/v1/summary"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/integration/v1/summary").header("X-API-Key", "incorrect-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsOnlyNonPersonalSummaryForAuthorizedSystem() throws Exception {
        mvc.perform(get("/api/integration/v1/summary").header("X-API-Key", TEST_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.system").value("Track 2 Department System"))
                .andExpect(jsonPath("$.dataClassification").value("NON_PERSONAL_SUMMARY"))
                .andExpect(jsonPath("$.availableData[0]").value("service-status"))
                .andExpect(jsonPath("$.students").doesNotExist())
                .andExpect(jsonPath("$.financialRecords").doesNotExist());
    }
}
