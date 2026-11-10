package org.agui.showcase.a2a;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

/**
 * MicroProfile SmallRye Readiness probe (`/q/health/ready`) for Nomad A2A Specialist (:8082).
 */
@Readiness
@ApplicationScoped
public class NomadA2aReadinessCheck implements HealthCheck {

    private final NomadTravelAdvisorService advisorService;

    @Inject
    public NomadA2aReadinessCheck(NomadTravelAdvisorService advisorService) {
        this.advisorService = advisorService;
    }

    @Override
    public HealthCheckResponse call() {
        Map<String, Object> report = advisorService.checkHealth();
        String overall = String.valueOf(report.getOrDefault("status", "DOWN"));
        HealthCheckResponseBuilder builder = HealthCheckResponse.named("nomad-a2a-upstreams")
                .withData("server", String.valueOf(report.getOrDefault("server", NomadTravelAdvisorService.AGENT_NAME)))
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
