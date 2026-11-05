package edu.department.portal;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;

interface ExternalApiTransport {
    ExternalApiResponse get(URI target, ExternalApiController.AuthenticationType authenticationType, String credential);

    record ExternalApiResponse(int status, JsonNode data) {}
}
