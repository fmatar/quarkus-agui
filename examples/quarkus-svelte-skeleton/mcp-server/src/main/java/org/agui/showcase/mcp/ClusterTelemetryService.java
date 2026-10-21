package org.agui.showcase.mcp;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class ClusterTelemetryService {

    public static final String SERVER_NAME = "cluster-telemetry-mcp";

    private final Map<String, ServiceStatus> services = new ConcurrentHashMap<>();
    private final List<LogEntry> logs = new ArrayList<>();

    public ClusterTelemetryService() {
        resetState();
    }

    @PostConstruct
    public final void resetState() {
        services.clear();
        services.put("api-gateway", new ServiceStatus("api-gateway", "HEALTHY", 3, 42.5, 310.0));
        services.put("payment-service", new ServiceStatus("payment-service", "DEGRADED", 2, 89.4, 720.0));
        services.put("auth-service", new ServiceStatus("auth-service", "HEALTHY", 3, 35.0, 260.0));

        logs.clear();
        logs.add(new LogEntry("2026-10-21T19:00:01Z", "payment-service", "ERROR", "Connection pool exhausted: p99 latency exceeded 1450ms"));
        logs.add(new LogEntry("2026-10-21T19:00:05Z", "payment-service", "ERROR", "Retry budget depleted on upstream settlement gateway"));
        logs.add(new LogEntry("2026-10-21T19:00:09Z", "payment-service", "WARN", "CPU throttling detected on replica payment-service-pod-2 (89.4%)"));
        logs.add(new LogEntry("2026-10-21T19:00:12Z", "api-gateway", "INFO", "SSE stream routing healthy across 3 active replicas"));
        logs.add(new LogEntry("2026-10-21T19:00:15Z", "auth-service", "INFO", "OIDC token verification cache hit ratio at 98.2%"));
    }

    public Map<String, Object> inspectClusterHealth(String region) {
        String resolvedRegion = (region == null || region.isBlank()) ? "us-east-1" : region.trim();
        List<ServiceStatus> ordered = List.of(
            services.get("api-gateway"),
            services.get("payment-service"),
            services.get("auth-service")
        );
        boolean anyDegraded = ordered.stream().anyMatch(s -> !"HEALTHY".equals(s.status()));
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("region", resolvedRegion);
        snapshot.put("overallStatus", anyDegraded ? "DEGRADED" : "HEALTHY");
        snapshot.put("services", ordered);
        snapshot.put("updatedAt", Instant.now().toString());
        return snapshot;
    }

    public List<LogEntry> queryServiceLogs(String serviceName, String severity, int limit) {
        String targetService = (serviceName == null || serviceName.isBlank()) ? "payment-service" : serviceName.trim();
        String targetSeverity = (severity == null || severity.isBlank()) ? "ALL" : severity.trim().toUpperCase();
        int clampedLimit = Math.max(1, Math.min(limit, 50));

        return logs.stream()
            .filter(entry -> entry.serviceName().equalsIgnoreCase(targetService))
            .filter(entry -> "ALL".equals(targetSeverity) || entry.severity().equalsIgnoreCase(targetSeverity))
            .limit(clampedLimit)
            .toList();
    }

    public ServiceStatus scaleDeployment(String serviceName, int targetReplicas) {
        if (serviceName == null || serviceName.isBlank() || !services.containsKey(serviceName.trim())) {
            throw new IllegalArgumentException("Unknown service: " + serviceName);
        }
        if (targetReplicas < 1 || targetReplicas > 20) {
            throw new IllegalArgumentException("targetReplicas must be between 1 and 20");
        }
        String key = serviceName.trim();
        ServiceStatus current = services.get(key);
        double scaledCpu = Math.round(Math.max(18.0, (current.cpuPercent() * current.replicas()) / targetReplicas) * 10.0) / 10.0;
        String newStatus = scaledCpu < 75.0 ? "HEALTHY" : "DEGRADED";
        ServiceStatus updated = new ServiceStatus(key, newStatus, targetReplicas, scaledCpu, current.memoryMb());
        services.put(key, updated);
        return updated;
    }

    public Map<String, Object> getTopologySnapshot() {
        Map<String, Object> res = new LinkedHashMap<>(inspectClusterHealth("us-east-1"));
        res.put("uri", "cluster://topology");
        return res;
    }

    public List<Map<String, Object>> listTools() {
        return List.of(
            Map.of(
                "name", "inspectClusterHealth",
                "description", "Inspect cluster health, pod replicas, CPU, and memory across services",
                "parameters", List.of("region")
            ),
            Map.of(
                "name", "queryServiceLogs",
                "description", "Query recent structured logs by service name and severity",
                "parameters", List.of("serviceName", "severity", "limit")
            ),
            Map.of(
                "name", "scaleDeployment",
                "description", "Scale service deployment replica count between 1 and 20",
                "parameters", List.of("serviceName", "targetReplicas")
            )
        );
    }

    public McpToolInvocationResponse dispatchTool(McpToolInvocationRequest request) {
        long startNs = System.nanoTime();
        String toolName = (request == null || request.tool() == null) ? "" : request.tool().trim();
        Map<String, Object> args = (request == null || request.arguments() == null) ? Map.of() : request.arguments();

        try {
            Object output = switch (toolName) {
                case "inspectClusterHealth" -> inspectClusterHealth(String.valueOf(args.getOrDefault("region", "us-east-1")));
                case "queryServiceLogs" -> {
                    String svc = String.valueOf(args.getOrDefault("serviceName", "payment-service"));
                    String sev = String.valueOf(args.getOrDefault("severity", "ERROR"));
                    int lim = args.get("limit") instanceof Number n ? n.intValue() : 5;
                    yield queryServiceLogs(svc, sev, lim);
                }
                case "scaleDeployment" -> {
                    String svc = String.valueOf(args.getOrDefault("serviceName", "payment-service"));
                    int replicas = args.get("targetReplicas") instanceof Number n ? n.intValue() : 4;
                    yield scaleDeployment(svc, replicas);
                }
                default -> throw new IllegalArgumentException("Unsupported MCP tool: " + toolName);
            };
            long elapsedMs = Math.max(1L, (System.nanoTime() - startNs) / 1_000_000L);
            return new McpToolInvocationResponse(SERVER_NAME, toolName, output, false, elapsedMs);
        } catch (RuntimeException ex) {
            long elapsedMs = Math.max(1L, (System.nanoTime() - startNs) / 1_000_000L);
            return new McpToolInvocationResponse(SERVER_NAME, toolName, Map.of("error", ex.getMessage()), true, elapsedMs);
        }
    }
}
