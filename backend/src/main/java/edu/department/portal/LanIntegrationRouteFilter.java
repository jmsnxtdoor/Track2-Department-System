package edu.department.portal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

final class LanIntegrationRouteFilter extends OncePerRequestFilter {
    private static final String ALLOWED_PATH = "/api/integration/v1/summary";

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
                || !ALLOWED_PATH.equals(request.getRequestURI())
                || request.getQueryString() != null)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
