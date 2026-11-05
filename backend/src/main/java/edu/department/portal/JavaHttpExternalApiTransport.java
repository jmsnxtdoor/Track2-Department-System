package edu.department.portal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.nio.ByteBuffer;

@Component
final class JavaHttpExternalApiTransport implements ExternalApiTransport {
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final ObjectMapper objectMapper;

    JavaHttpExternalApiTransport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ExternalApiResponse get(
            URI target, ExternalApiController.AuthenticationType authenticationType, String credential) {
        verifyResolvedDestination(target);
        HttpRequest.Builder request = HttpRequest.newBuilder(target)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET();
        if (authenticationType == ExternalApiController.AuthenticationType.API_KEY) {
            request.header("X-API-Key", credential);
        } else if (authenticationType == ExternalApiController.AuthenticationType.BEARER) {
            request.header("Authorization", "Bearer " + credential);
        }

        try {
            HttpResponse<byte[]> response = CLIENT.send(
                    request.build(), information -> new LimitedBodySubscriber());
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (!contentType.matches("(?i).*(application/json|application/[^;]+\\+json).*")) {
                throw new ExternalApiFailure("The external API returned a non-JSON response.");
            }
            JsonNode json = objectMapper.readTree(response.body());
            if (json == null) {
                throw new ExternalApiFailure("The external API returned an empty JSON response.");
            }
            return new ExternalApiResponse(response.statusCode(), json);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ExternalApiFailure("The external API request was interrupted.", interrupted);
        } catch (IOException failure) {
            if (containsOversizedResponse(failure)) {
                throw new ExternalApiFailure("The external API response exceeds the 1 MB limit.");
            }
            if (failure instanceof java.net.http.HttpTimeoutException) {
                throw new ExternalApiFailure("The external API request timed out.");
            }
            throw new ExternalApiFailure("The external API could not be reached or returned invalid JSON.", failure);
        }
    }

    private static boolean containsOversizedResponse(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof OversizedResponseException) {
                return true;
            }
        }
        return false;
    }

    private static void verifyResolvedDestination(URI target) {
        if ("http".equalsIgnoreCase(target.getScheme())) {
            return;
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(target.getHost());
            if (addresses.length == 0 || Arrays.stream(addresses).anyMatch(JavaHttpExternalApiTransport::isNonPublic)) {
                throw new ExternalApiFailure("The configured external API host resolves to a private or reserved address.");
            }
        } catch (IOException failure) {
            throw new ExternalApiFailure("The configured external API host could not be resolved.", failure);
        }
    }

    private static boolean isNonPublic(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        if (bytes.length == 16) {
            return (bytes[0] & 0xfe) == 0xfc;
        }
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        int third = bytes[2] & 0xff;
        return first == 0 || first == 10 || first == 127
                || first == 100 && second >= 64 && second <= 127
                || first == 169 && second == 254
                || first == 172 && second >= 16 && second <= 31
                || first == 192 && (second == 0 || second == 168)
                || first == 198 && (second == 18 || second == 19 || second == 51 && third == 100)
                || first == 203 && second == 0 && third == 113
                || first >= 224;
    }

    static final class ExternalApiFailure extends RuntimeException {
        ExternalApiFailure(String message) {
            super(message);
        }

        ExternalApiFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream content = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        private int size;

        @Override
        public java.util.concurrent.CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                int nextSize = buffer.remaining();
                if (size + nextSize > MAX_RESPONSE_BYTES) {
                    subscription.cancel();
                    body.completeExceptionally(new OversizedResponseException());
                    return;
                }
                byte[] bytes = new byte[nextSize];
                buffer.get(bytes);
                content.writeBytes(bytes);
                size += nextSize;
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable failure) {
            body.completeExceptionally(failure);
        }

        @Override
        public void onComplete() {
            body.complete(content.toByteArray());
        }
    }

    private static final class OversizedResponseException extends RuntimeException {}
}
