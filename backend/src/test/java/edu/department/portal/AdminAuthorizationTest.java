package edu.department.portal;

import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {Api.class, UserAdminController.class, AuditAdminController.class})
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

    @Test
    void unauthenticatedApiRequestReturnsUnauthorizedWithoutBasicChallenge() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanReadUsersAndAuditLogs() throws Exception {
        when(userManagementService.list()).thenReturn(List.of());
        when(auditService.recent()).thenReturn(List.of());

        mvc.perform(get("/api/admin/users")).andExpect(status().isOk());
        mvc.perform(get("/api/admin/audit-logs")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TREASURER")
    void nonAdminCannotReadUsersOrAuditLogs() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/audit-logs")).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/users/1/student-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanAccessFinancialAndAttendanceModules() throws Exception {
        when(ledgerRepo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(eventRepo.findAllByOrderByEventDateDesc()).thenReturn(List.of());

        mvc.perform(get("/api/finance/ledger")).andExpect(status().isOk());
        mvc.perform(get("/api/attendance/events")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TREASURER")
    void treasurerCanReadFinanceButNotAttendance() throws Exception {
        when(ledgerRepo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

        mvc.perform(get("/api/finance/ledger")).andExpect(status().isOk());
        mvc.perform(get("/api/attendance/events")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OFFICER")
    void officerCanReadAttendanceButNotFinance() throws Exception {
        when(eventRepo.findAllByOrderByEventDateDesc()).thenReturn(List.of());

        mvc.perform(get("/api/attendance/events")).andExpect(status().isOk());
        mvc.perform(get("/api/finance/ledger")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/audit-logs")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCanReadOnlyTheirStudentPortalRoutes() throws Exception {
        mvc.perform(get("/api/students/me/attendance")).andExpect(status().isOk());
        mvc.perform(get("/api/students/me/summary")).andExpect(status().isOk());
        mvc.perform(get("/api/students/me/ledger")).andExpect(status().isOk());
        mvc.perform(get("/api/students")).andExpect(status().isForbidden());
        mvc.perform(get("/api/finance/ledger")).andExpect(status().isForbidden());
        mvc.perform(get("/api/attendance/events")).andExpect(status().isForbidden());
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
}
