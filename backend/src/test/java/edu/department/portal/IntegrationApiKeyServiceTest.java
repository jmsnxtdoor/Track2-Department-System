package edu.department.portal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationApiKeyServiceTest {
    @Mock
    private IntegrationApiKeyRepo keys;

    @InjectMocks
    private IntegrationApiKeyService service;

    @Test
    void createsRandomKeyAndPersistsOnlyItsSha256Hash() throws Exception {
        when(keys.save(org.mockito.ArgumentMatchers.any(IntegrationApiKey.class)))
                .thenAnswer(invocation -> {
                    IntegrationApiKey saved = invocation.getArgument(0);
                    saved.id = 7L;
                    return saved;
                });

        IntegrationApiKeyService.CreatedApiKey created = service.create("  reporting  ", "admin");

        ArgumentCaptor<IntegrationApiKey> saved = ArgumentCaptor.forClass(IntegrationApiKey.class);
        verify(keys).save(saved.capture());
        assertTrue(created.key().matches("t2_[A-Za-z0-9_-]{43}"));
        assertEquals("reporting", created.name());
        assertNotEquals(created.key(), saved.getValue().keyHash);
        String expectedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(created.key().getBytes(StandardCharsets.UTF_8)));
        assertEquals(expectedHash, saved.getValue().keyHash);
        assertEquals("admin", saved.getValue().createdBy);
        assertNotNull(created.createdAt());
    }

    @Test
    void validatesByHashAndRejectsKeysAfterRevocation() throws Exception {
        String candidate = "t2_in-memory-integration-test-key";
        String expectedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(candidate.getBytes(StandardCharsets.UTF_8)));
        when(keys.existsByKeyHashAndRevokedAtIsNull(expectedHash)).thenReturn(true, false);

        assertTrue(service.isValidKey(candidate));
        assertFalse(service.isValidKey(candidate));
        verify(keys, times(2)).existsByKeyHashAndRevokedAtIsNull(expectedHash);
    }

    @Test
    void acceptsManagedKeysEvenWhenTheLegacySharedKeyIsNotConfigured() throws Exception {
        IntegrationApiKeyService managedKeys = org.mockito.Mockito.mock(IntegrationApiKeyService.class);
        when(managedKeys.isValidKey("t2_managed-key")).thenReturn(true);
        SharedApiKeyFilter filter = new SharedApiKeyFilter("", managedKeys);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "t2_managed-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        java.util.concurrent.atomic.AtomicBoolean chainInvoked = new java.util.concurrent.atomic.AtomicBoolean();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> chainInvoked.set(true));

        assertTrue(chainInvoked.get());
        assertEquals(200, response.getStatus());
    }

    @Test
    void listsOnlyMetadataWithoutReturningStoredHash() {
        IntegrationApiKey key = new IntegrationApiKey();
        key.id = 3L;
        key.name = "reporting";
        key.keyHash = "stored-hash-not-for-display";
        key.createdAt = Instant.parse("2026-01-02T03:04:05Z");
        when(keys.findAllByOrderByCreatedAtDesc()).thenReturn(java.util.List.of(key));

        assertEquals(
                new IntegrationApiKeyService.ApiKeyView(3L, "reporting", key.createdAt, null, "ACTIVE"),
                service.list().getFirst());
    }

    @Test
    void revocationIsIdempotentAndUnknownIdsReturnNotFound() {
        IntegrationApiKey key = new IntegrationApiKey();
        when(keys.findById(3L)).thenReturn(Optional.of(key));

        service.revoke(3L);
        assertNotNull(key.revokedAt);
        service.revoke(3L);
        verify(keys, times(1)).save(key);
        when(keys.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.revoke(999L));
    }
}
