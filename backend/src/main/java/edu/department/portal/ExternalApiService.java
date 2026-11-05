package edu.department.portal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ExternalApiService {
    private static final Set<String> CREDENTIAL_QUERY_PARAMETERS =
            Set.of("access_token", "api_key", "apikey", "client_secret", "password", "secret", "token");
    private final ExternalApiTransport transport;
    private final String allowedEndpoints;
    private final String allowedPrivateEndpoints;

    ExternalApiService(
            ExternalApiTransport transport,
            @Value("${external-api.allowed-endpoints:}") String allowedEndpoints,
            @Value("${external-api.allowed-private-endpoints:}") String allowedPrivateEndpoints) {
        this.transport = transport;
        this.allowedEndpoints = allowedEndpoints;
        this.allowedPrivateEndpoints = allowedPrivateEndpoints;
    }

    public ExternalApiResult fetch(
            String rawUrl, ExternalApiController.AuthenticationType authenticationType, String rawCredential) {
        if ((allowedEndpoints == null || allowedEndpoints.isBlank())
                && (allowedPrivateEndpoints == null || allowedPrivateEndpoints.isBlank())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No trusted external API endpoints are configured.");
        }
        if (authenticationType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose an external API authentication method.");
        }
        String credential = rawCredential == null ? "" : rawCredential.trim();
        if (credential.length() > 4096) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The external API credential is too long.");
        }
        if (credential.chars().anyMatch(character -> character < 0x20 || character == 0x7f)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The external API credential contains unsupported characters.");
        }
        if (authenticationType != ExternalApiController.AuthenticationType.NONE && credential.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the external API credential.");
        }
        if (authenticationType == ExternalApiController.AuthenticationType.NONE && !credential.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Clear the credential or choose its authentication method.");
        }

        ValidatedTarget validatedTarget = validateTarget(rawUrl);
        try {
            ExternalApiTransport.ExternalApiResponse response =
                    transport.get(
                            validatedTarget.uri(), authenticationType, credential, validatedTarget.allowPrivate());
            JsonNode safeData = redactCredentialEchoes(response.data(), credential);
            return new ExternalApiResult(response.status(), safeData);
        } catch (JavaHttpExternalApiTransport.ExternalApiFailure failure) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, failure.getMessage());
        }
    }

    private ValidatedTarget validateTarget(String rawUrl) {
        URI target;
        try {
            target = new URI(rawUrl.trim()).normalize();
        } catch (URISyntaxException failure) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid external API URL.");
        }
        if (target.getHost() == null || target.getUserInfo() != null || target.getFragment() != null
                || target.getQuery() != null && Arrays.stream(target.getQuery().split("&"))
                .map(parameter -> parameter.split("=", 2)[0].toLowerCase(Locale.ROOT))
                .anyMatch(CREDENTIAL_QUERY_PARAMETERS::contains)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Remove URL credentials, credential query parameters, and fragments.");
        }

        boolean loopbackDemo = "http".equalsIgnoreCase(target.getScheme())
                && "127.0.0.1".equals(target.getHost());
        if (!"https".equalsIgnoreCase(target.getScheme()) && !loopbackDemo) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Use HTTPS; HTTP is allowed only for explicitly configured 127.0.0.1 development endpoints.");
        }
        if ("https".equalsIgnoreCase(target.getScheme()) && isLoopbackLiteral(target.getHost())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Loopback development endpoints must use HTTP and be explicitly allowlisted.");
        }

        List<String> approved = Arrays.stream(allowedEndpoints.split(","))
                .map(String::trim)
                .filter(endpoint -> !endpoint.isEmpty())
                .toList();
        List<String> approvedPrivate = Arrays.stream(allowedPrivateEndpoints.split(","))
                .map(String::trim)
                .filter(endpoint -> !endpoint.isEmpty())
                .toList();
        String normalized = target.toASCIIString();
        boolean allowPrivate = approvedPrivate.contains(normalized);
        if (!approved.contains(normalized) && !allowPrivate) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This external API endpoint is not on the trusted endpoint allowlist.");
        }
        if (allowPrivate && !"https".equalsIgnoreCase(target.getScheme())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Private-network endpoints must use HTTPS.");
        }
        return new ValidatedTarget(target, allowPrivate);
    }

    private static boolean isLoopbackLiteral(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized.equals("localhost") || normalized.equals("::1") || normalized.startsWith("127.");
    }

    private static JsonNode redactCredentialEchoes(JsonNode data, String credential) {
        if (credential.isEmpty()) {
            return data;
        }
        JsonNode copy = data.deepCopy();
        redactNode(copy, credential);
        return copy;
    }

    private static void redactNode(JsonNode node, String credential) {
        if (node instanceof ObjectNode object) {
            object.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (value.isTextual() && value.textValue().contains(credential)) {
                    object.put(entry.getKey(), value.textValue().replace(credential, "[REDACTED]"));
                } else {
                    redactNode(value, credential);
                }
            });
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                JsonNode value = node.get(index);
                if (value.isTextual() && value.textValue().contains(credential)) {
                    ((ArrayNode) node).set(index, JsonNodeFactory.instance.textNode("[REDACTED]"));
                } else {
                    redactNode(value, credential);
                }
            }
        }
    }

    private record ValidatedTarget(URI uri, boolean allowPrivate) {}

    public record ExternalApiResult(int status, JsonNode data) {}
}
