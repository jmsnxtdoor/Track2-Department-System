package edu.department.portal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalApiServiceTest {
    private static final String DEMO_ENDPOINT = "http://127.0.0.1:8091/v1/demo";

    @Test
    void forwardsAuthenticationAndReturnsStatusWithJson() throws Exception {
        var json = new ObjectMapper().readTree("{\"source\":\"demo\",\"echo\":\"isolated-key\"}");
        CapturingTransport transport = new CapturingTransport(new ExternalApiTransport.ExternalApiResponse(200, json));
        ExternalApiService service = new ExternalApiService(transport, DEMO_ENDPOINT, "");

        var result = service.fetch(DEMO_ENDPOINT, ExternalApiController.AuthenticationType.API_KEY, "isolated-key");

        assertEquals(200, result.status());
        assertEquals(URI.create(DEMO_ENDPOINT), transport.target);
        assertEquals(ExternalApiController.AuthenticationType.API_KEY, transport.authenticationType);
        assertEquals("isolated-key", transport.credential);
        assertEquals(false, transport.allowPrivateDestination);
        assertEquals("[REDACTED]", result.data().get("echo").asText());
    }

    @Test
    void acceptsBearerAuthenticationAndKeepsUpstreamUnauthorizedStatusVisible() throws Exception {
        var json = new ObjectMapper().readTree("{\"message\":\"invalid credentials\"}");
        CapturingTransport transport = new CapturingTransport(new ExternalApiTransport.ExternalApiResponse(401, json));
        ExternalApiService service = new ExternalApiService(transport, DEMO_ENDPOINT, "");

        var result = service.fetch(DEMO_ENDPOINT, ExternalApiController.AuthenticationType.BEARER, "temporary-token");

        assertEquals(401, result.status());
        assertEquals(ExternalApiController.AuthenticationType.BEARER, transport.authenticationType);
    }

    @Test
    void rejectsUntrustedHostsRemoteHttpAndCredentialQueryParametersBeforeCallingTransport() {
        CapturingTransport transport = new CapturingTransport(null);
        ExternalApiService service = new ExternalApiService(transport, DEMO_ENDPOINT, "");

        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.fetch("https://api.example.test/private",
                        ExternalApiController.AuthenticationType.NONE, ""))
                .getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.fetch("http://127.0.0.1:8091/v1/demo?api_key=x",
                        ExternalApiController.AuthenticationType.NONE, ""))
                .getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.fetch("http://192.168.1.5/private", ExternalApiController.AuthenticationType.NONE, ""))
                .getStatusCode().value());
        assertTrue(transport.target == null);
    }

    @Test
    void reportsMissingAllowlistAndCredentialWithoutMakingNetworkCalls() {
        CapturingTransport transport = new CapturingTransport(null);
        ExternalApiService service = new ExternalApiService(transport, "", "");

        assertEquals(503, assertThrows(ResponseStatusException.class,
                () -> service.fetch(DEMO_ENDPOINT, ExternalApiController.AuthenticationType.NONE, ""))
                .getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> new ExternalApiService(transport, DEMO_ENDPOINT, "").fetch(
                        DEMO_ENDPOINT, ExternalApiController.AuthenticationType.API_KEY, ""))
                .getStatusCode().value());
        assertTrue(transport.target == null);
    }

    @Test
    void allowsOnlyExplicitHttpsPrivateEndpointsAndMarksThemForPrivateAddressValidation() throws Exception {
        String lanEndpoint = "https://track2-a.lan:8443/api/integration/v1/summary";
        CapturingTransport transport = new CapturingTransport(
                new ExternalApiTransport.ExternalApiResponse(200, new ObjectMapper().readTree("{\"status\":\"Available\"}")));
        ExternalApiService service = new ExternalApiService(transport, "", lanEndpoint);

        service.fetch(lanEndpoint, ExternalApiController.AuthenticationType.API_KEY, "peer-key");

        assertEquals(URI.create(lanEndpoint), transport.target);
        assertEquals(true, transport.allowPrivateDestination);

        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.fetch("https://track2-a.lan:8443/api/other",
                        ExternalApiController.AuthenticationType.API_KEY, "peer-key"))
                .getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> new ExternalApiService(transport, "", "http://track2-a.lan:8443/api/integration/v1/summary")
                        .fetch("http://track2-a.lan:8443/api/integration/v1/summary",
                                ExternalApiController.AuthenticationType.API_KEY, "peer-key"))
                .getStatusCode().value());
    }

    private static final class CapturingTransport implements ExternalApiTransport {
        private final ExternalApiResponse response;
        private URI target;
        private ExternalApiController.AuthenticationType authenticationType;
        private String credential;
        private boolean allowPrivateDestination;

        private CapturingTransport(ExternalApiResponse response) {
            this.response = response;
        }

        @Override
        public ExternalApiResponse get(
                URI target,
                ExternalApiController.AuthenticationType authenticationType,
                String credential,
                boolean allowPrivateDestination) {
            this.target = target;
            this.authenticationType = authenticationType;
            this.credential = credential;
            this.allowPrivateDestination = allowPrivateDestination;
            return response;
        }
    }
}
