package edu.department.portal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IntegrationApiController.class)
@Import({IntegrationSecurityConfig.class, IntegrationReadService.class})
class IntegrationApiTest {
    private static final String TEST_KEY = UUID.randomUUID().toString();
    private static final List<String> APPROVED_PATHS = List.of(
            "/api/integration/v1/summary",
            "/api/integration/v1/data-summary");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private StudentRepo students;

    @MockitoBean
    private AttendanceRepo attendance;

    @MockitoBean
    private IntegrationApiKeyService integrationApiKeys;

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
        mvc.perform(get("/api/integration/v1/data-summary"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/integration/v1/data-summary").header("X-API-Key", "incorrect-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsAnActiveAdminGeneratedKeyForTheReadOnlyEndpoints() throws Exception {
        String generatedKey = "t2_generated-test-key";
        when(integrationApiKeys.isValidKey(generatedKey)).thenReturn(true);

        mvc.perform(get("/api/integration/v1/summary").header("X-API-Key", generatedKey))
                .andExpect(status().isOk());
        mvc.perform(get("/api/integration/v1/data-summary").header("X-API-Key", generatedKey))
                .andExpect(status().isOk());
        mvc.perform(get("/api/integration/v1/summary").header("X-API-Key", "revoked-test-key"))
                .andExpect(status().isUnauthorized());
        org.mockito.Mockito.verify(integrationApiKeys, org.mockito.Mockito.times(2)).isValidKey(generatedKey);
    }

    @Test
    void returnsOnlyNonPersonalSummaryForAuthorizedSystem() throws Exception {
        String response = mvc.perform(get("/api/integration/v1/summary").header("X-API-Key", TEST_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.system").value("Track 2 Department System"))
                .andExpect(jsonPath("$.dataClassification").value("NON_PERSONAL_SUMMARY"))
                .andExpect(jsonPath("$.availableData[0]").value("service-status"))
                .andExpect(jsonPath("$.students").doesNotExist())
                .andExpect(jsonPath("$.financialRecords").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertFalse(response.contains(TEST_KEY));
        assertFalse(response.contains("password"));
        assertFalse(response.contains("jdbc:"));
        assertFalse(response.contains("secret"));
        assertFalse(response.contains("studentNumber"));
        assertFalse(response.contains("fullName"));
    }

    @Test
    void returnsRepositoryDerivedAggregateCountsWithoutPersonalOrFinancialData() throws Exception {
        when(students.count()).thenReturn(37L);
        when(attendance.count()).thenReturn(22L);
        when(attendance.countByStatus(Attendance.Status.PRESENT)).thenReturn(14L);
        when(attendance.countByStatus(Attendance.Status.LATE)).thenReturn(3L);
        when(attendance.countByStatus(Attendance.Status.ABSENT)).thenReturn(4L);
        when(attendance.countByStatus(Attendance.Status.EXCUSED)).thenReturn(1L);

        String response = mvc.perform(get("/api/integration/v1/data-summary").header("X-API-Key", TEST_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataClassification").value("NON_PERSONAL_AGGREGATE"))
                .andExpect(jsonPath("$.studentCount").value(37))
                .andExpect(jsonPath("$.attendance.total").value(22))
                .andExpect(jsonPath("$.attendance.present").value(14))
                .andExpect(jsonPath("$.attendance.late").value(3))
                .andExpect(jsonPath("$.attendance.absent").value(4))
                .andExpect(jsonPath("$.attendance.excused").value(1))
                .andExpect(jsonPath("$.generatedAt", endsWith("Z")))
                .andExpect(jsonPath("$.students").doesNotExist())
                .andExpect(jsonPath("$.transactions").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertFalse(response.contains("fullName"));
        assertFalse(response.contains("studentNumber"));
        assertFalse(response.contains("referenceNumber"));
        assertFalse(response.contains("email"));
        assertFalse(response.contains("phone"));
        assertFalse(response.contains("jdbc:"));
        assertFalse(response.contains("password"));
        assertFalse(response.contains("secret"));
        assertFalse(response.contains(TEST_KEY));
        verify(students).count();
        verify(attendance).count();
        verify(attendance).countByStatus(Attendance.Status.PRESENT);
        verify(attendance).countByStatus(Attendance.Status.LATE);
        verify(attendance).countByStatus(Attendance.Status.ABSENT);
        verify(attendance).countByStatus(Attendance.Status.EXCUSED);
    }

    @Test
    void rejectsUnapprovedPathsAndWriteMethodsEvenWithAValidKey() throws Exception {
        for (String path : APPROVED_PATHS) {
            List<MockHttpServletRequestBuilder> writeRequests = List.of(
                    post(path), put(path), patch(path), delete(path));
            for (MockHttpServletRequestBuilder request : writeRequests) {
                mvc.perform(request.header("X-API-Key", TEST_KEY))
                        .andExpect(status().isForbidden());
            }
        }

        mvc.perform(get("/api/integration/v1/students").header("X-API-Key", TEST_KEY))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/integration/v1/students").header("X-API-Key", "incorrect-key"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/integration/v1/keys").header("X-API-Key", TEST_KEY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(students, attendance);
    }
}
