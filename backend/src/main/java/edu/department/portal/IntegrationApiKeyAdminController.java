package edu.department.portal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/integration-api-keys")
class IntegrationApiKeyAdminController {
    private final IntegrationApiKeyService keys;

    IntegrationApiKeyAdminController(IntegrationApiKeyService keys) {
        this.keys = keys;
    }

    @GetMapping
    List<IntegrationApiKeyService.ApiKeyView> list() {
        return keys.list();
    }

    @PostMapping
    ResponseEntity<IntegrationApiKeyService.CreatedApiKey> create(
            @Valid @RequestBody CreateApiKeyRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(keys.create(request.name(), authentication.getName()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@PathVariable long id) {
        keys.revoke(id);
    }

    record CreateApiKeyRequest(@NotBlank @Size(max = 100) String name) {}
}
