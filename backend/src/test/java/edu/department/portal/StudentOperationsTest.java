package edu.department.portal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StudentOperationsTest {
    private StudentRepo students;
    private LedgerRepo ledger;
    private FundRepo funds;
    private EventRepo events;
    private AttendanceRepo attendance;
    private AuditService audit;
    private Api api;

    @BeforeEach
    void setUp() {
        students = mock(StudentRepo.class);
        ledger = mock(LedgerRepo.class);
        funds = mock(FundRepo.class);
        events = mock(EventRepo.class);
        attendance = mock(AttendanceRepo.class);
        audit = mock(AuditService.class);
        api = new Api(students, ledger, funds, events, attendance, audit, mock(StudentPortalService.class));
    }

    @Test
    void rejectsDuplicateStudentNumbersIgnoringCase() {
        when(students.existsByStudentNumberIgnoreCase("S-100")).thenReturn(true);

        assertThrows(ResponseStatusException.class, () -> api.createStudent(
                student(" S-100 ", "Taylor Student"), new UsernamePasswordAuthenticationToken("admin", "n/a")));
        verify(students).existsByStudentNumberIgnoreCase("S-100");
        verify(students, never()).save(any(Student.class));
    }

    @Test
    void createsValidStudentAndAuditsTheSavedRecord() {
        when(students.existsByStudentNumberIgnoreCase("s-100")).thenReturn(false);
        when(students.save(any(Student.class))).thenAnswer(invocation -> {
            Student saved = invocation.getArgument(0);
            saved.id = 9L;
            return saved;
        });

        Student created = api.createStudent(student(" s-100 ", " Taylor Student "),
                new UsernamePasswordAuthenticationToken("admin", "n/a"));

        assertEquals(9L, created.id);
        assertEquals("s-100", created.studentNumber);
        assertEquals("Taylor Student", created.fullName);
        verify(students).existsByStudentNumberIgnoreCase("s-100");
        verify(audit).record("admin", "STUDENT_CREATED", "Student", "9", "studentNumber=s-100");
    }

    @Test
    void updatesOnlyRequestedStudentAndAuditsChange() {
        Student existing = student("S-100", "Old Name");
        existing.id = 4L;
        when(students.findById(4L)).thenReturn(Optional.of(existing));
        when(students.existsByStudentNumberIgnoreCaseAndIdNot("S-101", 4L)).thenReturn(false);
        when(students.save(existing)).thenReturn(existing);

        Student updated = api.editStudent(4L, student("S-101", "New Name"),
                new UsernamePasswordAuthenticationToken("admin", "n/a"));

        assertEquals("S-101", updated.studentNumber);
        assertEquals("New Name", updated.fullName);
        assertEquals(4L, updated.id);
        verify(audit).record("admin", "STUDENT_UPDATED", "Student", "4", "studentNumber=S-101");
    }

    private Student student(String number, String name) {
        Student student = new Student();
        student.studentNumber = number;
        student.fullName = name;
        return student;
    }
}
