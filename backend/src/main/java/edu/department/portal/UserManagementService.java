package edu.department.portal;

import edu.department.portal.UserAccount.UserRole;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class UserManagementService {
    private final UserRepo users;
    private final StudentRepo students;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    UserManagementService(UserRepo users, StudentRepo students, PasswordEncoder passwordEncoder, AuditService audit) {
        this.users = users;
        this.students = students;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<UserView> list() {
        return users.findAllByOrderByUsernameAsc().stream().map(UserView::from).toList();
    }

    @Transactional
    public UserView create(CreateUser request, String actor) {
        String username = request.username().trim();
        if (users.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists.");
        }
        UserAccount user = new UserAccount();
        user.username = username;
        user.passwordHash = passwordEncoder.encode(request.password());
        user.role = request.role();
        user.enabled = true;
        user.createdAt = Instant.now();
        user.updatedAt = user.createdAt;
        user = users.save(user);
        audit.record(actor, "USER_CREATED", "UserAccount", user.id.toString(),
                "username=" + user.username + ";role=" + user.role);
        return UserView.from(user);
    }

    @Transactional
    public UserView update(long id, UpdateUser request, String actor) {
        UserAccount user = users.findWithStudentById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        String nextUsername = request.username().trim();
        if (!user.username.equals(nextUsername) && users.existsByUsername(nextUsername)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists.");
        }
        if (actor.equalsIgnoreCase(user.username) && user.role == UserRole.ADMIN && request.role() != UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot remove your own administrator role.");
        }
        if (!request.enabled() && user.enabled && actor.equalsIgnoreCase(user.username)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot deactivate your own account.");
        }
        if (!request.enabled() && user.enabled && user.role == UserRole.ADMIN
                && users.countByRoleAndEnabledTrue(UserRole.ADMIN) <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The last active administrator cannot be deactivated.");
        }
        if (user.student != null && request.role() != UserRole.STUDENT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unlink the student record before changing this account to another role.");
        }

        boolean enabledChanged = user.enabled != request.enabled();
        user.username = nextUsername;
        user.role = request.role();
        user.enabled = request.enabled();
        if (request.newPassword() != null && !request.newPassword().isBlank()) {
            user.passwordHash = passwordEncoder.encode(request.newPassword());
        }
        user.updatedAt = Instant.now();
        user = users.save(user);

        String action = enabledChanged
                ? (user.enabled ? "USER_ACTIVATED" : "USER_DEACTIVATED")
                : "USER_UPDATED";
        audit.record(actor, action, "UserAccount", user.id.toString(),
                "username=" + user.username + ";role=" + user.role + ";enabled=" + user.enabled);
        return UserView.from(user);
    }

    @Transactional
    public UserView linkStudent(long id, Long studentId, String actor) {
        UserAccount user = users.findWithStudentById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        if (user.role != UserRole.STUDENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only student accounts can be linked to student records.");
        }
        Student student = studentId == null ? null : students.findByIdForUpdate(studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student record not found."));
        if (student != null && users.existsByStudent_IdAndIdNot(student.id, user.id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This student record is linked to another account.");
        }
        Long existingStudentId = user.student == null ? null : user.student.id;
        if (java.util.Objects.equals(existingStudentId, studentId)) {
            return UserView.from(user);
        }

        user.student = student;
        user.updatedAt = Instant.now();
        UserAccount saved = users.save(user);
        String action = student == null ? "STUDENT_ACCOUNT_UNLINKED" : "STUDENT_ACCOUNT_LINKED";
        String details = student == null ? "student record unlinked"
                : "studentNumber=" + student.studentNumber;
        audit.record(actor, action, "UserAccount", saved.id.toString(), details);
        return UserView.from(saved);
    }

    public record CreateUser(
            @NotBlank @Size(max = 100) String username,
            @NotNull UserRole role,
            @NotBlank @Size(min = 12, max = 128) String password) {}

    public record UpdateUser(
            @NotBlank @Size(max = 100) String username,
            @NotNull UserRole role,
            boolean enabled,
            @Size(min = 12, max = 128) String newPassword) {}

    public record UserView(
            Long id,
            String username,
            UserRole role,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt,
            Long studentId,
            String studentNumber,
            String studentName) {
        static UserView from(UserAccount user) {
            return new UserView(
                    user.id,
                    user.username,
                    user.role,
                    user.enabled,
                    user.createdAt,
                    user.updatedAt,
                    user.student == null ? null : user.student.id,
                    user.student == null ? null : user.student.studentNumber,
                    user.student == null ? null : user.student.fullName);
        }
    }
}
