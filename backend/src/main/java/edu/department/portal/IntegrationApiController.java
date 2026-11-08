package edu.department.portal;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/integration/v1")
public class IntegrationApiController {
    private final IntegrationReadService integrationReadService;

    IntegrationApiController(IntegrationReadService integrationReadService) {
        this.integrationReadService = integrationReadService;
    }

    @Operation(summary = "Get integration service summary",
            description = "Returns non-personal service metadata. Requires the X-API-Key header.")
    @GetMapping("/summary")
    public IntegrationSummary summary() {
        return new IntegrationSummary(
                "Track 2 Department System",
                "NON_PERSONAL_SUMMARY",
                "Available",
                List.of("service-status", "available-data-categories"),
                Instant.now());
    }

    @Operation(summary = "Get approved aggregate data summary",
            description = "Returns aggregate student and attendance counts only; no personal records or financial details. Requires the X-API-Key header.")
    @GetMapping("/data-summary")
    public IntegrationDataSummary dataSummary() {
        return integrationReadService.dataSummary();
    }

    public record IntegrationSummary(
            String system,
            String dataClassification,
            String status,
            List<String> availableData,
            Instant generatedAt) {}
}
