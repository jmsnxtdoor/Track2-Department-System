package edu.department.portal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/external-api")
public class ExternalApiController {
    private final ExternalApiService externalApi;

    ExternalApiController(ExternalApiService externalApi) {
        this.externalApi = externalApi;
    }

    @PostMapping("/fetch")
    @ResponseStatus(HttpStatus.OK)
    public ExternalApiService.ExternalApiResult fetch(@Valid @RequestBody ExternalApiRequest request) {
        return externalApi.fetch(request.url(), request.authenticationType(), request.credential());
    }

    public record ExternalApiRequest(
            @NotBlank @Size(max = 2048) String url,
            @NotNull AuthenticationType authenticationType,
            @Size(max = 4096) String credential) {}

    public enum AuthenticationType {
        NONE,
        API_KEY,
        BEARER
    }
}
