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

    SharedApiKeyFilter(String configuredKey) {
        this.configuredKey = configuredKey == null ? new byte[0] : configuredKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (configuredKey.length == 0) {
            response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), "System integration is not configured.");
            return;
        }

        String supplied = request.getHeader("X-API-Key");
        if (supplied == null || supplied.isEmpty() || supplied.length() > 512
                || !MessageDigest.isEqual(configuredKey, supplied.getBytes(StandardCharsets.UTF_8))) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "A valid X-API-Key is required.");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
