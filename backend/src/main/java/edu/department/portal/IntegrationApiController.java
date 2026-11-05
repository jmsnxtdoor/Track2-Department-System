package edu.department.portal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/integration/v1")
public class IntegrationApiController {
    @GetMapping("/summary")
    public IntegrationSummary summary() {
        return new IntegrationSummary(
                "Track 2 Department System",
                "NON_PERSONAL_SUMMARY",
                "Available",
                List.of("service-status", "available-data-categories"),
                Instant.now());
    }

    public record IntegrationSummary(
            String system,
            String dataClassification,
            String status,
            List<String> availableData,
            Instant generatedAt) {}
}
