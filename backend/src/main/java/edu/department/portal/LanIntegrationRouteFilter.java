package edu.department.portal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

final class LanIntegrationRouteFilter extends OncePerRequestFilter {
    private static final Set<String> ALLOWED_PATHS = Set.of(
            "/api/integration/v1/summary",
            "/api/integration/v1/data-summary");

    private final int integrationPort;

    LanIntegrationRouteFilter(int integrationPort) {
        this.integrationPort = integrationPort;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getLocalPort() == integrationPort
                && (!request.isSecure()
                || !"GET".equals(request.getMethod())
                || !ALLOWED_PATHS.contains(request.getRequestURI())
                || request.getQueryString() != null)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
