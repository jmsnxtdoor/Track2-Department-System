package edu.department.portal;

import edu.department.portal.Attendance.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "track2.test.jdbcUrl", matches = "jdbc:postgresql:.*")
@DataJpaTest(properties = {
        "spring.datasource.url=${track2.test.jdbcUrl}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.datasource.username=${track2.test.username}",
        "spring.datasource.password=${track2.test.password}",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({Api.class, AuditService.class, StudentPortalService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AttendancePostgresIntegrationTest {
    private static final UsernamePasswordAuthenticationToken OFFICER =
            new UsernamePasswordAuthenticationToken("officer", "n/a");

    @Autowired private Api api;
    @Autowired private StudentRepo students;
    @Autowired private EventRepo events;
    @Autowired private AttendanceRepo attendance;
    @Autowired private StudentPortalService studentPortal;
    @Autowired private UserRepo users;

    @Test
    void recordsAttendanceWithServerTimestampAndRejectsDuplicateAndInvalidReferences() {
        Student student = createStudent();
        createStudentAccount(student);
        DepartmentEvent event = createEvent();
        LocalDateTime requestStartedAt = LocalDateTime.now();

        Attendance saved = api.scan(
                new Api.ScanInput(event.id, student.studentNumber, Status.LATE), OFFICER);

        assertNotNull(saved.id);
        assertEquals(Status.LATE, saved.status);
        assertEquals(student.id, saved.student.id);
        assertEquals(event.id, saved.event.id);
        assertNotNull(saved.scannedAt);
        assertEquals(saved.scannedAt, attendance.findById(saved.id).orElseThrow().scannedAt,
                "The returned server timestamp must match PostgreSQL persistence precision.");
        assertTrue(!saved.scannedAt.isBefore(requestStartedAt)
                        && !saved.scannedAt.isAfter(LocalDateTime.now()),
                "The server timestamp must be assigned during this scan request.");
        assertEquals(1, attendance.findByEventId(event.id).size());
        assertEquals("LATE", studentPortal.attendance("attendance-student").get(0).status());

        ResponseStatusException duplicate = assertThrows(ResponseStatusException.class,
                () -> api.scan(new Api.ScanInput(event.id, student.studentNumber, Status.PRESENT), OFFICER));
        assertEquals(HttpStatus.CONFLICT, duplicate.getStatusCode());

        ResponseStatusException missingStudent = assertThrows(ResponseStatusException.class,
                () -> api.scan(new Api.ScanInput(event.id, "INVALID-" + UUID.randomUUID(), Status.PRESENT), OFFICER));
        assertEquals(HttpStatus.NOT_FOUND, missingStudent.getStatusCode());

        ResponseStatusException missingEvent = assertThrows(ResponseStatusException.class,
                () -> api.scan(new Api.ScanInput(Long.MAX_VALUE, student.studentNumber, Status.PRESENT), OFFICER));
        assertEquals(HttpStatus.NOT_FOUND, missingEvent.getStatusCode());

        DepartmentEvent inactive = createEvent();
        inactive.active = false;
        events.saveAndFlush(inactive);
        ResponseStatusException inactiveEvent = assertThrows(ResponseStatusException.class,
                () -> api.scan(new Api.ScanInput(inactive.id, student.studentNumber, Status.PRESENT), OFFICER));
        assertEquals(HttpStatus.BAD_REQUEST, inactiveEvent.getStatusCode());

        assertEquals(1, attendance.findByStudentIdOrderByScannedAtDesc(student.id).size());
    }

    @Test
    void concurrentDuplicateScansPersistExactlyOneAttendanceRecord() throws Exception {
        Student student = createStudent();
        DepartmentEvent event = createEvent();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> scanConcurrently(event, student, ready, start));
            Future<Integer> second = executor.submit(() -> scanConcurrently(event, student, ready, start));

            assertTrue(ready.await(5, TimeUnit.SECONDS), "Both scans should be ready.");
            start.countDown();

            int firstStatus = first.get(10, TimeUnit.SECONDS);
            int secondStatus = second.get(10, TimeUnit.SECONDS);
            assertEquals(1, (firstStatus == HttpStatus.CREATED.value() ? 1 : 0)
                    + (secondStatus == HttpStatus.CREATED.value() ? 1 : 0));
            assertEquals(1, (firstStatus == HttpStatus.CONFLICT.value() ? 1 : 0)
                    + (secondStatus == HttpStatus.CONFLICT.value() ? 1 : 0));
            assertTrue(attendance.existsByStudentIdAndEventId(student.id, event.id));
            assertEquals(1, attendance.findByEventId(event.id).size());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private int scanConcurrently(DepartmentEvent event, Student student,
                                 CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS), "The concurrent scan gate should open.");
        try {
            api.scan(new Api.ScanInput(event.id, student.studentNumber, Status.PRESENT), OFFICER);
            return HttpStatus.CREATED.value();
        } catch (ResponseStatusException failure) {
            return failure.getStatusCode().value();
        }
    }

    private Student createStudent() {
        Student student = new Student();
        student.studentNumber = "ATT-" + UUID.randomUUID();
        student.fullName = "Fictional Attendance Student";
        return students.saveAndFlush(student);
    }

    private DepartmentEvent createEvent() {
        DepartmentEvent event = new DepartmentEvent();
        event.title = "Fictional Attendance " + UUID.randomUUID();
        event.eventDate = LocalDate.now();
        return events.saveAndFlush(event);
    }

    private void createStudentAccount(Student student) {
        UserAccount account = new UserAccount();
        account.username = "attendance-student";
        account.passwordHash = "$2a$10$placeholder";
        account.role = UserAccount.UserRole.STUDENT;
        account.student = student;
        users.saveAndFlush(account);
    }
}
