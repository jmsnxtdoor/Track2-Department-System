package edu.department.portal;

import edu.department.portal.UserAccount.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DevelopmentAccountSeederTest {
    @Test
    void doesNotRequireSeedPasswordsWhenAllAccountsAlreadyExist() throws Exception {
        UserRepo users = mock(UserRepo.class);
        for (String username : new String[]{"admin", "treasurer", "officer", "student"}) {
            UserAccount existing = new UserAccount();
            existing.username = username;
            when(users.findByUsername(username)).thenReturn(Optional.of(existing));
        }

        DevelopmentAccountSeeder seeder = new DevelopmentAccountSeeder(users, new BCryptPasswordEncoder(),
                "", "", "", "");

        assertDoesNotThrow(() -> seeder.run(new DefaultApplicationArguments(new String[0])));
        verify(users, never()).save(any(UserAccount.class));
    }

    @Test
    void insertsOnlyMissingAccountsAndHashesTheirPasswords() throws Exception {
        UserRepo users = mock(UserRepo.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        UserAccount existing = new UserAccount();
        existing.username = "admin";
        existing.role = UserRole.ADMIN;
        existing.passwordHash = encoder.encode("existing-password");
        String originalHash = existing.passwordHash;
        when(users.findByUsername("admin")).thenReturn(Optional.of(existing));
        when(users.findByUsername("treasurer")).thenReturn(Optional.empty());
        when(users.findByUsername("officer")).thenReturn(Optional.empty());
        when(users.findByUsername("student")).thenReturn(Optional.empty());
        when(users.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DevelopmentAccountSeeder seeder = new DevelopmentAccountSeeder(users, encoder,
                "admin-env-password", "treasurer-env-password", "officer-env-password", "student-env-password");
        seeder.run(new DefaultApplicationArguments(new String[0]));

        var captor = org.mockito.ArgumentCaptor.forClass(UserAccount.class);
        verify(users, times(3)).save(captor.capture());
        var inserted = captor.getAllValues();
        assertTrue(inserted.stream().anyMatch(user -> user.username.equals("treasurer")
                && encoder.matches("treasurer-env-password", user.passwordHash)));
        assertTrue(inserted.stream().anyMatch(user -> user.username.equals("officer")
                && encoder.matches("officer-env-password", user.passwordHash)));
        assertTrue(inserted.stream().anyMatch(user -> user.username.equals("student")
                && encoder.matches("student-env-password", user.passwordHash)));
        assertEquals(originalHash, existing.passwordHash);
    }
}
