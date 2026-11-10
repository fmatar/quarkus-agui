package org.agui.showcase.mcp;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

/**
 * MicroProfile SmallRye Readiness probe (`/q/health/ready`) for Nomad MCP Server (:8081).
 */
@Readiness
@ApplicationScoped
public class NomadMcpReadinessCheck implements HealthCheck {

    private final NomadMcpConciergeService conciergeService;

    @Inject
    public NomadMcpReadinessCheck(NomadMcpConciergeService conciergeService) {
        this.conciergeService = conciergeService;
    }

    @Override
    public HealthCheckResponse call() {
        Map<String, Object> report = conciergeService.checkHealth();
        String overall = String.valueOf(report.getOrDefault("status", "DOWN"));
        HealthCheckResponseBuilder builder = HealthCheckResponse.named("nomad-mcp-upstreams")
                .withData("server", String.valueOf(report.getOrDefault("server", NomadMcpConciergeService.SERVER_NAME)))
                .withData("overallStatus", overall);

        if (report.get("upstreams") instanceof List<?> upstreams) {
            for (Object item : upstreams) {
                if (item instanceof Map<?, ?> entry) {
                    String id = String.valueOf(entry.get("id"));
                    String status = String.valueOf(entry.get("status"));
                    builder.withData(id + ".status", status);
                    if (entry.get("error") instanceof String err && !err.isBlank()) {
                        builder.withData(id + ".error", err);
                    }
                }
            }
        }
        return builder.status("UP".equals(overall)).build();
    }
}
