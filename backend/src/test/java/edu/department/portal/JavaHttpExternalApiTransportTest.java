package edu.department.portal;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JavaHttpExternalApiTransportTest {
    @Test
    void sendsKeyToLoopbackMockAndParsesJson() throws Exception {
        String key = UUID.randomUUID().toString();
        HttpServer server = mockServer("/data", exchange -> {
            int status = key.equals(exchange.getRequestHeaders().getFirst("X-API-Key")) ? 200 : 401;
            byte[] body = "{\"source\":\"DEMONSTRATION_ONLY\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        String endpoint = endpoint(server, "/data");
        try {
            var result = new ExternalApiService(new JavaHttpExternalApiTransport(new com.fasterxml.jackson.databind.ObjectMapper()),
                    endpoint, "").fetch(endpoint, ExternalApiController.AuthenticationType.API_KEY, key);

            assertEquals(200, result.status());
            assertEquals("DEMONSTRATION_ONLY", result.data().get("source").asText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sendsBearerCredentialOnlyAsAuthorizationHeader() throws Exception {
        String bearer = UUID.randomUUID().toString();
        HttpServer server = mockServer("/bearer", exchange -> {
            int status = ("Bearer " + bearer).equals(exchange.getRequestHeaders().getFirst("Authorization")) ? 200 : 401;
            byte[] body = "{\"source\":\"DEMONSTRATION_ONLY\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        String endpoint = endpoint(server, "/bearer");
        try {
            var result = new ExternalApiService(
                    new JavaHttpExternalApiTransport(new com.fasterxml.jackson.databind.ObjectMapper()), endpoint, "")
                    .fetch(endpoint, ExternalApiController.AuthenticationType.BEARER, bearer);

            assertEquals(200, result.status());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void cancelsLoopbackResponseWhenItExceedsOneMegabyte() throws Exception {
        byte[] oversized = new byte[1024 * 1024 + 1];
        HttpServer server = mockServer("/large", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, oversized.length);
            try {
                exchange.getResponseBody().write(oversized);
            } catch (IOException ignored) {
                // The bounded client cancels this response as soon as the limit is exceeded.
            } finally {
                exchange.close();
            }
        });
        String endpoint = endpoint(server, "/large");
        try {
            var failure = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                    () -> new ExternalApiService(
                            new JavaHttpExternalApiTransport(new com.fasterxml.jackson.databind.ObjectMapper()),
                            endpoint, "").fetch(endpoint, ExternalApiController.AuthenticationType.NONE, ""));
            assertEquals(502, failure.getStatusCode().value());
            assertEquals("The external API response exceeds the 1 MB limit.", failure.getReason());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void privateDestinationOptInAcceptsOnlyRfc1918OrUniqueLocalAddresses() throws Exception {
        assertEquals(true, JavaHttpExternalApiTransport.isPrivateNetworkAddress(
                InetAddress.getByName("192.168.1.20")));
        assertEquals(true, JavaHttpExternalApiTransport.isPrivateNetworkAddress(
                InetAddress.getByName("10.20.30.40")));
        assertEquals(false, JavaHttpExternalApiTransport.isPrivateNetworkAddress(
                InetAddress.getByName("127.0.0.1")));
        assertEquals(false, JavaHttpExternalApiTransport.isPrivateNetworkAddress(
                InetAddress.getByName("169.254.1.5")));
        assertEquals(false, JavaHttpExternalApiTransport.isPrivateNetworkAddress(
                InetAddress.getByName("8.8.8.8")));
    }

    private static HttpServer mockServer(String path, com.sun.net.httpserver.HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext(path, handler);
        server.start();
        return server;
    }

    private static String endpoint(HttpServer server, String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path).toString();
    }
}
