package edu.department.portal;

import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        Api.class, UserAdminController.class, AuditAdminController.class, ExternalApiController.class
}, properties = {
        "app.cors.origin=https://127.0.0.1:5173,https://192.168.0.106:5173"
})
@Import(SecurityConfig.class)
class AdminAuthorizationTest {
    @Autowired
    private MockMvc mvc;

    @MockBean
    private UserRepo userRepo;

    @MockBean
    private UserManagementService userManagementService;

    @MockBean
    private AuditService auditService;

    @MockBean
    private StudentRepo studentRepo;

    @MockBean
    private LedgerRepo ledgerRepo;

    @MockBean
    private FundRepo fundRepo;

    @MockBean
    private EventRepo eventRepo;

    @MockBean
    private AttendanceRepo attendanceRepo;

    @MockBean
    private StudentPortalService studentPortalService;

    @MockBean
    private ExternalApiService externalApiService;

    @Test
    void rejectsWildcardCorsOrigins() {
        assertThrows(IllegalArgumentException.class, () -> SecurityConfig.corsOrigins("*"));
        assertThrows(IllegalArgumentException.class, () -> SecurityConfig.corsOrigins("https://*.example:5173"));
        assertEquals(
                List.of("https://127.0.0.1:5173", "https://192.168.0.106:5173"),
                SecurityConfig.corsOrigins("https://127.0.0.1:5173,https://192.168.0.106:5173"));
    }

    @Test
    void unauthenticatedApiRequestReturnsUnauthorizedWithoutBasicChallenge() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"));

        mvc.perform(post("/api/attendance/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Test activity\",\"eventDate\":\"2026-01-02\",\"active\":true}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/admin/external-api/fetch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://trusted.example.test/summary\","
                                + "\"authenticationType\":\"NONE\",\"credential\":\"\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanReadUsersAndAuditLogs() throws Exception {
        when(userManagementService.list()).thenReturn(List.of());
        when(auditService.recent()).thenReturn(List.of());

        mvc.perform(get("/api/admin/users")).andExpect(status().isOk());
        mvc.perform(get("/api/admin/audit-logs")).andExpect(status().isOk());
        when(externalApiService.fetch("https://trusted.example.test/summary",
                ExternalApiController.AuthenticationType.NONE, ""))
                .thenReturn(new ExternalApiService.ExternalApiResult(200,
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree("{\"source\":\"demo\"}")));
        mvc.perform(post("/api/admin/external-api/fetch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://trusted.example.test/summary\","
                                + "\"authenticationType\":\"NONE\",\"credential\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanCreateAndUpdateStudentRecords() throws Exception {
        Student student = new Student();
        student.id = 1L;
        student.studentNumber = "S-001";
        student.fullName = "Test Student";
        when(studentRepo.existsByStudentNumberIgnoreCase("S-001")).thenReturn(false);
        when(studentRepo.findById(1L)).thenReturn(Optional.of(student));
        when(studentRepo.existsByStudentNumberIgnoreCaseAndIdNot("S-001", 1L)).thenReturn(false);
        when(studentRepo.save(any(Student.class))).thenAnswer(invocation -> {
            Student saved = invocation.getArgument(0);
            saved.id = 1L;
            return saved;
        });

        mvc.perform(post("/api/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentNumber\":\"S-001\",\"fullName\":\"Test Student\"}"))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/students/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentNumber\":\"S-001\",\"fullName\":\"Updated Student\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TREASURER")
    void nonAdminCannotReadUsersOrAuditLogs() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/audit-logs")).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"new-user\",\"role\":\"OFFICER\",\"password\":\"long-enough-password\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/users/1/student-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/external-api/fetch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://trusted.example.test/summary\","
                                + "\"authenticationType\":\"NONE\",\"credential\":\"\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanAccessFinancialAndAttendanceModules() throws Exception {
        when(ledgerRepo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(eventRepo.findAllByOrderByEventDateDesc()).thenReturn(List.of());

        mvc.perform(get("/api/finance/ledger")).andExpect(status().isOk());
        mvc.perform(get("/api/attendance/events")).andExpect(status().isOk());
        mvc.perform(get("/api/attendance/records")).andExpect(status().isOk());
        mvc.perform(get("/api/students/1/ledger")).andExpect(status().isOk());
        mvc.perform(get("/api/students/1/balance")).andExpect(status().isOk());
        mvc.perform(get("/api/reports/finance")).andExpect(status().isOk());
        mvc.perform(get("/api/reports/funds")).andExpect(status().isOk());
        mvc.perform(get("/api/reports/attendance")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TREASURER")
    void treasurerCanReadFinanceButNotAttendance() throws Exception {
        when(ledgerRepo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(fundRepo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

        mvc.perform(get("/api/finance/ledger")).andExpect(status().isOk());
        mvc.perform(get("/api/finance/funds")).andExpect(status().isOk());
        mvc.perform(get("/api/students/1/ledger")).andExpect(status().isOk());
        mvc.perform(get("/api/students/1/balance")).andExpect(status().isOk());
        mvc.perform(get("/api/reports/finance")).andExpect(status().isOk());
        mvc.perform(get("/api/reports/funds")).andExpect(status().isOk());
        mvc.perform(get("/api/reports/students")).andExpect(status().isOk());
        mvc.perform(get("/api/reports/summary")).andExpect(status().isOk());
        mvc.perform(get("/api/attendance/events")).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/attendance")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users")).andExpect(status().isForbidden());
        mvc.perform(post("/api/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentNumber\":\"S-002\",\"fullName\":\"Test Student\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/students/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentNumber\":\"S-001\",\"fullName\":\"Updated Student\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OFFICER")
    void officerCanReadAttendanceButNotFinance() throws Exception {
        when(eventRepo.findAllByOrderByEventDateDesc()).thenReturn(List.of());

        mvc.perform(get("/api/attendance/events")).andExpect(status().isOk());
        mvc.perform(get("/api/attendance/records")).andExpect(status().isOk());
        mvc.perform(get("/api/reports/attendance")).andExpect(status().isOk());
        mvc.perform(get("/api/reports/students")).andExpect(status().isOk());
        mvc.perform(get("/api/reports/summary")).andExpect(status().isOk());
        mvc.perform(get("/api/students")).andExpect(status().isOk());
        mvc.perform(get("/api/students/1/ledger")).andExpect(status().isForbidden());
        mvc.perform(get("/api/students/1/balance")).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/finance")).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/funds")).andExpect(status().isForbidden());
        mvc.perform(get("/api/finance/ledger")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/audit-logs")).andExpect(status().isForbidden());
        mvc.perform(post("/api/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentNumber\":\"S-002\",\"fullName\":\"Test Student\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OFFICER")
    void deniedFinancialReportsDoNotReadFinancialRepositories() throws Exception {
        mvc.perform(get("/api/reports/finance")).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/funds")).andExpect(status().isForbidden());

        verifyNoInteractions(ledgerRepo, fundRepo);
    }

    @Test
    @WithMockUser(roles = "TREASURER")
    void deniedAttendanceReportDoesNotReadAttendanceRepositories() throws Exception {
        mvc.perform(get("/api/reports/attendance")).andExpect(status().isForbidden());

        verifyNoInteractions(eventRepo, attendanceRepo);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanCreateAttendanceEventsAndRecordsAndUpdateAttendance() throws Exception {
        stubAttendanceWrites();

        mvc.perform(post("/api/attendance/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Test activity\",\"eventDate\":\"2026-01-02\",\"active\":true}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/attendance/records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":1,\"studentNumber\":\"S-001\",\"status\":\"PRESENT\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/attendance/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":1,\"studentNumber\":\"S-001\",\"status\":\"PRESENT\"}"))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/attendance/records/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"LATE\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "OFFICER")
    void officerCanCreateAttendanceEventsAndRecordsAndUpdateAttendance() throws Exception {
        stubAttendanceWrites();

        mvc.perform(post("/api/attendance/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Test activity\",\"eventDate\":\"2026-01-02\",\"active\":true}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/attendance/records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":1,\"studentNumber\":\"S-001\",\"status\":\"PRESENT\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/attendance/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":1,\"studentNumber\":\"S-001\",\"status\":\"PRESENT\"}"))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/attendance/records/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"LATE\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TREASURER")
    void treasurerCannotWriteAttendance() throws Exception {
        assertAttendanceWritesForbidden();
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCannotWriteAttendance() throws Exception {
        assertAttendanceWritesForbidden();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void attendanceEndpointsRejectMissingRequiredFields() throws Exception {
        mvc.perform(post("/api/attendance/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/attendance/records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/attendance/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":1,\"studentNumber\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/attendance/records/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "TREASURER")
    void treasurerCanCreateFines() throws Exception {
        stubFineCreation();
        mvc.perform(post("/api/finance/ledger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fineJson()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanCreateFines() throws Exception {
        stubFineCreation();
        mvc.perform(post("/api/finance/ledger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fineJson()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rejectsInvalidFineAndFundAmountsAtTheApiBoundary() throws Exception {
        mvc.perform(post("/api/finance/ledger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":2,\"kind\":\"FINE\",\"amount\":0,"
                                + "\"description\":\"Invalid fine\",\"referenceNumber\":\"INVALID-1\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/finance/ledger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":2,\"kind\":\"FINE\",\"amount\":1.001,"
                                + "\"description\":\"Invalid precision\",\"referenceNumber\":\"INVALID-2\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/finance/funds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ADDITION\",\"amount\":0,\"description\":\"Invalid fund\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/finance/funds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ADDITION\",\"amount\":1.001,\"description\":\"Invalid precision\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void requiresIdempotencyKeysForFinancialWrites() throws Exception {
        mvc.perform(post("/api/finance/ledger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":2,\"kind\":\"FINE\",\"amount\":10,"
                                + "\"description\":\"Missing key\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/finance/funds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ADDITION\",\"amount\":10,"
                                + "\"description\":\"Missing key\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "OFFICER")
    void officerCannotCreateFines() throws Exception {
        mvc.perform(post("/api/finance/ledger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fineJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCannotCreateFines() throws Exception {
        mvc.perform(post("/api/finance/ledger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fineJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "TREASURER")
    void treasurerCanCreateFundEntries() throws Exception {
        stubFundCreation();
        mvc.perform(post("/api/finance/funds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fundJson()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "OFFICER")
    void officerCannotCreateFundEntries() throws Exception {
        mvc.perform(post("/api/finance/funds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fundJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanCreateUsers() throws Exception {
        mvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"new-user\",\"role\":\"OFFICER\",\"password\":\"long-enough-password\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "OFFICER")
    void officerCannotManageUsers() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"new-user\",\"role\":\"OFFICER\",\"password\":\"long-enough-password\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCannotManageUsers() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"new-user\",\"role\":\"OFFICER\",\"password\":\"long-enough-password\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsOnlyTheConfiguredHttpsCorsOrigins() throws Exception {
        for (String origin : List.of("https://127.0.0.1:5173", "https://192.168.0.106:5173")) {
            mvc.perform(options("/api/attendance/events")
                            .header(HttpHeaders.ORIGIN, origin)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin));
        }

        mvc.perform(options("/api/attendance/events")
                        .header(HttpHeaders.ORIGIN, "https://192.168.0.200:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCanReadOnlyTheirStudentPortalRoutes() throws Exception {
        mvc.perform(get("/api/students/me/attendance")).andExpect(status().isOk());
        mvc.perform(get("/api/students/me/summary")).andExpect(status().isOk());
        mvc.perform(get("/api/students/me/ledger")).andExpect(status().isOk());
        mvc.perform(get("/api/students/me/balance")).andExpect(status().isOk());
        mvc.perform(get("/api/students")).andExpect(status().isForbidden());
        mvc.perform(get("/api/students/1/ledger")).andExpect(status().isForbidden());
        mvc.perform(get("/api/students/1/balance")).andExpect(status().isForbidden());
        mvc.perform(get("/api/dashboard")).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/students")).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/finance")).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/attendance")).andExpect(status().isForbidden());
        mvc.perform(post("/api/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentNumber\":\"S-002\",\"fullName\":\"Test Student\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/finance/ledger")).andExpect(status().isForbidden());
        mvc.perform(get("/api/attendance/events")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OFFICER")
    void officerDashboardContainsAttendanceMetricsWithoutFinancialData() throws Exception {
        when(studentRepo.count()).thenReturn(3L);
        when(eventRepo.findByActiveTrue()).thenReturn(List.of());

        mvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students").value(3))
                .andExpect(jsonPath("$.attendanceRate").value(0.0))
                .andExpect(jsonPath("$.attendanceScans").value(0))
                .andExpect(jsonPath("$.collections").doesNotExist())
                .andExpect(jsonPath("$.unpaid").doesNotExist())
                .andExpect(jsonPath("$.fundBalance").doesNotExist())
                .andExpect(jsonPath("$.collectionChart").doesNotExist())
                .andExpect(jsonPath("$.recent").doesNotExist());
        mvc.perform(get("/api/reports/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collections").doesNotExist())
                .andExpect(jsonPath("$.attendanceRate").exists());

        verify(ledgerRepo, never()).findAllByOrderByCreatedAtDesc();
        verify(fundRepo, never()).findAllByOrderByCreatedAtDesc();
        verify(studentRepo, never()).findAll();
    }

    @Test
    @WithMockUser(roles = "TREASURER")
    void treasurerDashboardContainsFinancialMetricsWithoutAttendanceData() throws Exception {
        when(studentRepo.count()).thenReturn(3L);
        when(ledgerRepo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(fundRepo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

        mvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collections").exists())
                .andExpect(jsonPath("$.unpaid").exists())
                .andExpect(jsonPath("$.fundBalance").exists())
                .andExpect(jsonPath("$.collectionChart").exists())
                .andExpect(jsonPath("$.recent").exists())
                .andExpect(jsonPath("$.attendanceRate").doesNotExist())
                .andExpect(jsonPath("$.attendanceScans").doesNotExist());

        verify(eventRepo, never()).findByActiveTrue();
        verify(attendanceRepo, never()).count();
        verify(attendanceRepo, never()).findAll();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminDashboardContainsFinancialAndAttendanceMetrics() throws Exception {
        when(studentRepo.count()).thenReturn(3L);
        when(ledgerRepo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(fundRepo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(eventRepo.findByActiveTrue()).thenReturn(List.of());

        mvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collections").exists())
                .andExpect(jsonPath("$.fundBalance").exists())
                .andExpect(jsonPath("$.attendanceRate").exists())
                .andExpect(jsonPath("$.attendanceScans").exists());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void onlyErrorDispatchCanReachErrorHandling() throws Exception {
        mvc.perform(get("/error").with(request -> {
                    request.setDispatcherType(DispatcherType.ERROR);
                    return request;
                }))
                .andExpect(result -> assertNotEquals(
                        HttpStatus.FORBIDDEN.value(),
                        result.getResponse().getStatus()));
        mvc.perform(get("/error").with(request -> {
                    request.setDispatcherType(DispatcherType.REQUEST);
                    return request;
                }))
                .andExpect(status().isForbidden());
    }

    private void stubAttendanceWrites() {
        DepartmentEvent event = new DepartmentEvent();
        event.id = 1L;
        event.title = "Test activity";
        event.eventDate = LocalDate.of(2026, 1, 2);
        event.active = true;
        when(eventRepo.save(any(DepartmentEvent.class))).thenAnswer(invocation -> {
            DepartmentEvent saved = invocation.getArgument(0);
            saved.id = 1L;
            return saved;
        });
        when(eventRepo.findById(1L)).thenReturn(Optional.of(event));

        Student student = new Student();
        student.id = 2L;
        student.studentNumber = "S-001";
        when(studentRepo.findByStudentNumber("S-001")).thenReturn(Optional.of(student));
        when(attendanceRepo.existsByStudentIdAndEventId(2L, 1L)).thenReturn(false);
        when(attendanceRepo.saveAndFlush(any(Attendance.class))).thenAnswer(invocation -> {
            Attendance saved = invocation.getArgument(0);
            saved.id = 3L;
            return saved;
        });

        Attendance record = new Attendance();
        record.id = 9L;
        record.event = event;
        record.student = student;
        when(attendanceRepo.findById(9L)).thenReturn(Optional.of(record));
        when(attendanceRepo.save(any(Attendance.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void assertAttendanceWritesForbidden() throws Exception {
        mvc.perform(get("/api/attendance/events")).andExpect(status().isForbidden());
        mvc.perform(get("/api/attendance/records")).andExpect(status().isForbidden());
        mvc.perform(post("/api/attendance/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Test activity\",\"eventDate\":\"2026-01-02\",\"active\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/attendance/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":1,\"studentNumber\":\"S-001\",\"status\":\"PRESENT\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/attendance/records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":1,\"studentNumber\":\"S-001\",\"status\":\"PRESENT\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/attendance/records/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"LATE\"}"))
                .andExpect(status().isForbidden());
    }

    private void stubFineCreation() {
        Student student = new Student();
        student.id = 2L;
        student.studentNumber = "S-001";
        when(studentRepo.findByIdForUpdate(2L)).thenReturn(Optional.of(student));
        when(ledgerRepo.existsByReferenceNumber("TEST-FINE-001")).thenReturn(false);
        when(ledgerRepo.save(any(LedgerEntry.class))).thenAnswer(invocation -> {
            LedgerEntry saved = invocation.getArgument(0);
            saved.id = 4L;
            return saved;
        });
    }

    private String fineJson() {
        return "{\"studentId\":2,\"kind\":\"FINE\",\"amount\":10.00,"
                + "\"description\":\"Test fine\",\"referenceNumber\":\"TEST-FINE-001\","
                + "\"idempotencyKey\":\"authorization-test-fine\"}";
    }

    private void stubFundCreation() {
        when(fundRepo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(fundRepo.save(any(FundEntry.class))).thenAnswer(invocation -> {
            FundEntry saved = invocation.getArgument(0);
            saved.id = 5L;
            return saved;
        });
    }

    private String fundJson() {
        return "{\"type\":\"ADDITION\",\"amount\":100.00,\"description\":\"Test fund entry\","
                + "\"idempotencyKey\":\"authorization-test-fund\"}";
    }
}
