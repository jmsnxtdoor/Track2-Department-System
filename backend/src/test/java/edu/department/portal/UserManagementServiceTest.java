package edu.department.portal;

import edu.department.portal.UserAccount.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserManagementServiceTest {
    private final UserRepo users = mock(UserRepo.class);
    private final StudentRepo students = mock(StudentRepo.class);
    private final AuditService audit = mock(AuditService.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final UserManagementService service = new UserManagementService(users, students, encoder, audit);

    @Test
    void createHashesPasswordAndReturnsOnlyPublicFields() {
        when(users.existsByUsername("newuser")).thenReturn(false);
        when(users.save(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount user = invocation.getArgument(0);
            user.id = 7L;
            return user;
        });

        UserManagementService.UserView result = service.create(
                new UserManagementService.CreateUser("newuser", UserRole.STUDENT, "test-password-123"), "admin");

        assertEquals("newuser", result.username());
        assertTrue(encoder.matches("test-password-123",
                getSavedUserHash()));
        assertFalse(getSavedUserHash().equals("test-password-123"));
        assertFalse(java.util.Arrays.stream(result.getClass().getRecordComponents())
                .anyMatch(component -> component.getName().toLowerCase().contains("password")));
        verify(audit).record("admin", "USER_CREATED", "UserAccount", "7",
                "username=newuser;role=STUDENT");
    }

    @Test
    void adminCannotDeactivateOwnAccount() {
        UserAccount admin = new UserAccount();
        admin.id = 1L;
        admin.username = "admin";
        admin.role = UserRole.ADMIN;
        admin.enabled = true;
        when(users.findById(1L)).thenReturn(Optional.of(admin));

        assertThrows(ResponseStatusException.class, () -> service.update(1L,
                new UserManagementService.UpdateUser("admin", UserRole.ADMIN, false, null), "admin"));
        verify(users, never()).save(any(UserAccount.class));
        verifyNoInteractions(audit);
    }

    @Test
    void studentAccountCanBeLinkedToOneExistingStudentRecord() {
        UserAccount user = new UserAccount();
        user.id = 7L;
        user.username = "student-one";
        user.role = UserRole.STUDENT;
        Student student = new Student();
        student.id = 12L;
        student.studentNumber = "S-001";
        student.fullName = "Student One";
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(students.findById(12L)).thenReturn(Optional.of(student));
        when(users.existsByStudent_IdAndIdNot(12L, 7L)).thenReturn(false);
        when(users.save(user)).thenReturn(user);

        UserManagementService.UserView result = service.linkStudent(7L, 12L, "admin");

        assertEquals(12L, result.studentId());
        assertEquals("S-001", result.studentNumber());
        assertEquals("Student One", result.studentName());
        verify(audit).record("admin", "STUDENT_ACCOUNT_LINKED", "UserAccount", "7", "studentNumber=S-001");
    }

    @Test
    void studentRecordCannotBeLinkedToTwoAccounts() {
        UserAccount user = new UserAccount();
        user.id = 7L;
        user.role = UserRole.STUDENT;
        Student student = new Student();
        student.id = 12L;
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(students.findById(12L)).thenReturn(Optional.of(student));
        when(users.existsByStudent_IdAndIdNot(12L, 7L)).thenReturn(true);

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.linkStudent(7L, 12L, "admin"));

        assertEquals(409, failure.getStatusCode().value());
        verify(users, never()).save(any(UserAccount.class));
    }

    @Test
    void nonStudentAccountCannotBeLinked() {
        UserAccount user = new UserAccount();
        user.id = 7L;
        user.role = UserRole.ADMIN;
        when(users.findById(7L)).thenReturn(Optional.of(user));

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.linkStudent(7L, 12L, "admin"));

        assertEquals(400, failure.getStatusCode().value());
        verifyNoInteractions(students);
    }

    @Test
    void linkedStudentAccountMustBeUnlinkedBeforeChangingItsRole() {
        UserAccount user = new UserAccount();
        user.id = 7L;
        user.username = "student-one";
        user.role = UserRole.STUDENT;
        user.student = new Student();
        when(users.findById(7L)).thenReturn(Optional.of(user));

        ResponseStatusException failure = assertThrows(ResponseStatusException.class, () -> service.update(
                7L,
                new UserManagementService.UpdateUser("student-one", UserRole.OFFICER, true, null),
                "admin"));

        assertEquals(400, failure.getStatusCode().value());
        verify(users, never()).save(any(UserAccount.class));
    }

    private String getSavedUserHash() {
        var captor = org.mockito.ArgumentCaptor.forClass(UserAccount.class);
        verify(users, atLeastOnce()).save(captor.capture());
        return captor.getValue().passwordHash;
    }
}
