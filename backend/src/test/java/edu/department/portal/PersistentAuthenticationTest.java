package edu.department.portal;

import edu.department.portal.UserAccount.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PersistentAuthenticationTest {
    @Test
    void loadsHashedPasswordRoleAndEnabledStateFromPersistentAccount() {
        UserRepo users = mock(UserRepo.class);
        SecurityConfig config = new SecurityConfig();
        UserAccount account = new UserAccount();
        account.username = "treasurer";
        account.passwordHash = "$2a$10$placeholder";
        account.role = UserRole.TREASURER;
        account.enabled = true;
        when(users.findByUsername("treasurer")).thenReturn(Optional.of(account));

        UserDetails details = config.users(users).loadUserByUsername("treasurer");

        assertEquals(account.passwordHash, details.getPassword());
        assertTrue(details.isEnabled());
        assertTrue(details.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_TREASURER")));
        when(users.findByUsername("missing")).thenReturn(Optional.empty());
        assertThrows(UsernameNotFoundException.class, () -> config.users(users).loadUserByUsername("missing"));
    }
}
