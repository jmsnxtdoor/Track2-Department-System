package edu.department.portal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class DevelopmentAccountSeeder implements ApplicationRunner {
    private final UserRepo users;
    private final PasswordEncoder passwordEncoder;
    private final List<SeedAccount> accounts;

    DevelopmentAccountSeeder(
            UserRepo users,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.password:}") String adminPassword,
            @Value("${app.treasurer.password:}") String treasurerPassword,
            @Value("${app.officer.password:}") String officerPassword,
            @Value("${app.student.password:}") String studentPassword) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.accounts = List.of(
                new SeedAccount("admin", UserAccount.UserRole.ADMIN, adminPassword),
                new SeedAccount("treasurer", UserAccount.UserRole.TREASURER, treasurerPassword),
                new SeedAccount("officer", UserAccount.UserRole.OFFICER, officerPassword),
                new SeedAccount("student", UserAccount.UserRole.STUDENT, studentPassword));
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (SeedAccount account : accounts) {
            if (users.findByUsername(account.username()).isEmpty()) {
                if (account.password().isBlank()) {
                    throw new IllegalStateException("A development account password is not configured.");
                }
                UserAccount user = new UserAccount();
                user.username = account.username();
                user.passwordHash = passwordEncoder.encode(account.password());
                user.role = account.role();
                users.save(user);
            }
        }
    }

    private record SeedAccount(String username, UserAccount.UserRole role, String password) {}
}
