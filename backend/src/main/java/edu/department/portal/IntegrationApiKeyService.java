package edu.department.portal;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Service
public class IntegrationApiKeyService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final IntegrationApiKeyRepo keys;

    IntegrationApiKeyService(IntegrationApiKeyRepo keys) {
        this.keys = keys;
    }

    @Transactional(readOnly = true)
    List<ApiKeyView> list() {
        return keys.findAllByOrderByCreatedAtDesc().stream()
                .map(IntegrationApiKeyService::toView)
                .toList();
    }

    @Transactional
    CreatedApiKey create(String name, String createdBy) {
        byte[] randomBytes = new byte[32];
        RANDOM.nextBytes(randomBytes);
        String plaintext = "t2_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        IntegrationApiKey key = new IntegrationApiKey();
        key.name = name.trim();
        key.keyHash = hash(plaintext);
        key.createdBy = createdBy;
        key.createdAt = Instant.now();
        IntegrationApiKey saved = keys.save(key);

        return new CreatedApiKey(saved.id, saved.name, plaintext, saved.createdAt);
    }

    @Transactional
    void revoke(long id) {
        IntegrationApiKey key = keys.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "API key not found."));
        if (key.revokedAt == null) {
            key.revokedAt = Instant.now();
            keys.save(key);
        }
    }

    @Transactional(readOnly = true)
    boolean isValidKey(String plaintext) {
        return keys.existsByKeyHashAndRevokedAtIsNull(hash(plaintext));
    }

    private static String hash(String plaintext) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(plaintext.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is required to validate integration API keys.", unavailable);
        }
    }

    private static ApiKeyView toView(IntegrationApiKey key) {
        return new ApiKeyView(
                key.id,
                key.name,
                key.createdAt,
                key.revokedAt,
                key.revokedAt == null ? "ACTIVE" : "REVOKED");
    }

    public record ApiKeyView(long id, String name, Instant createdAt, Instant revokedAt, String status) {}

    public record CreatedApiKey(long id, String name, String key, Instant createdAt) {}
}
