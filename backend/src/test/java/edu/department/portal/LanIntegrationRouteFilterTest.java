package edu.department.portal;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanIntegrationRouteFilterTest {
    private static final int LAN_PORT = 8443;
    private static final List<String> ALLOWED_PATHS = List.of(
            "/api/integration/v1/summary",
            "/api/integration/v1/data-summary");
    private final LanIntegrationRouteFilter filter = new LanIntegrationRouteFilter(LAN_PORT);

    @Test
    void permitsOnlySecureReadOnlyIntegrationGetRoutesOnTheLanConnector() throws Exception {
        for (String path : ALLOWED_PATHS) {
            assertRequest(8443, true, "GET", path, null, 200, true);
            assertRequest(8443, true, "POST", path, null, 404, false);
            assertRequest(8443, true, "PUT", path, null, 404, false);
            assertRequest(8443, true, "PATCH", path, null, 404, false);
            assertRequest(8443, true, "DELETE", path, null, 404, false);
        }
        assertRequest(8443, true, "GET", "/api/admin/users", null, 404, false);
        assertRequest(8443, true, "GET", "/api/integration/v1/students", null, 404, false);
        assertRequest(8443, true, "GET", "/api/integration/v1/summary/extra", null, 404, false);
        assertRequest(8443, true, "GET", "/api/integration/v1/summary", "key=value", 404, false);
        assertRequest(8443, false, "GET", "/api/integration/v1/summary", null, 404, false);
    }

    @Test
    void doesNotChangeRequestsOnTheLoopbackApplicationConnector() throws Exception {
        assertRequest(8080, false, "GET", "/api/admin/users", null, 200, true);
    }

    @Test
    void acceptsOnlyExplicitPrivateIpv4BindAddresses() {
        assertEquals(true, LanIntegrationServerConfig.isPrivateIpv4("192.168.1.20"));
        assertEquals(true, LanIntegrationServerConfig.isPrivateIpv4("10.20.30.40"));
        assertEquals(false, LanIntegrationServerConfig.isPrivateIpv4("127.0.0.1"));
        assertEquals(false, LanIntegrationServerConfig.isPrivateIpv4("169.254.1.2"));
        assertEquals(false, LanIntegrationServerConfig.isPrivateIpv4("8.8.8.8"));
        assertEquals(false, LanIntegrationServerConfig.isPrivateIpv4("0.0.0.0"));
    }

    private void assertRequest(
            int port,
            boolean secure,
            String method,
            String path,
            String query,
            int expectedStatus,
            boolean expectedChainInvocation) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setLocalPort(port);
        request.setSecure(secure);
        request.setQueryString(query);
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] chainInvoked = { false };
        FilterChain chain = (servletRequest, servletResponse) -> chainInvoked[0] = true;

        filter.doFilter(request, response, chain);

        assertEquals(expectedStatus, response.getStatus());
        assertEquals(expectedChainInvocation, chainInvoked[0]);
    }
}
