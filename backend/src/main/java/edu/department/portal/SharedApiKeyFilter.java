package edu.department.portal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class SharedApiKeyFilter extends OncePerRequestFilter {
    private final byte[] configuredKey;
    private final IntegrationApiKeyService integrationApiKeys;

    SharedApiKeyFilter(String configuredKey, IntegrationApiKeyService integrationApiKeys) {
        this.configuredKey = configuredKey == null ? new byte[0] : configuredKey.getBytes(StandardCharsets.UTF_8);
        this.integrationApiKeys = integrationApiKeys;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String supplied = request.getHeader("X-API-Key");
        boolean matchesConfiguredKey = supplied != null && !supplied.isEmpty() && supplied.length() <= 512
                && configuredKey.length > 0
                && MessageDigest.isEqual(configuredKey, supplied.getBytes(StandardCharsets.UTF_8));
        boolean matchesManagedKey = supplied != null && !supplied.isEmpty() && supplied.length() <= 512
                && integrationApiKeys.isValidKey(supplied);
        if (!matchesConfiguredKey && !matchesManagedKey) {
            int status = configuredKey.length == 0
                    ? HttpStatus.SERVICE_UNAVAILABLE.value()
                    : HttpStatus.UNAUTHORIZED.value();
            String message = configuredKey.length == 0
                    ? "System integration is not configured."
                    : "A valid X-API-Key is required.";
            response.sendError(status, message);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
