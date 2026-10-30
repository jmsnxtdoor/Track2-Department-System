package edu.department.portal;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {
    private final UserManagementService users;

    UserAdminController(UserManagementService users) {
        this.users = users;
    }

    @GetMapping
    public List<UserManagementService.UserView> list() {
        return users.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserManagementService.UserView create(
            @Valid @RequestBody UserManagementService.CreateUser request,
            Authentication authentication) {
        return users.create(request, authentication.getName());
    }

    @PutMapping("/{id}")
    public UserManagementService.UserView update(
            @PathVariable long id,
            @Valid @RequestBody UserManagementService.UpdateUser request,
            Authentication authentication) {
        return users.update(id, request, authentication.getName());
    }

    @PutMapping("/{id}/student-link")
    public UserManagementService.UserView linkStudent(
            @PathVariable long id,
            @RequestBody StudentLink request,
            Authentication authentication) {
        return users.linkStudent(id, request.studentId(), authentication.getName());
    }

    public record StudentLink(Long studentId) {}
}
