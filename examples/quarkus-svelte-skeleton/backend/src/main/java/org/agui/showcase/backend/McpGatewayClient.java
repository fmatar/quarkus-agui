package org.agui.showcase.backend;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP Gateway Client for OpsPilot Orchestrator (:8080 -> :8081).
 * Exposes MCP tools (inspectClusterHealth, queryServiceLogs, scaleDeployment) and cluster://topology.
 */
@ApplicationScoped
public class McpGatewayClient {

    private final Map<String, Map<String, Object>> services = new ConcurrentHashMap<>();

    public McpGatewayClient() {
        resetState();
    }

    public final void resetState() {
        services.clear();
        services.put("payment-gateway", new LinkedHashMap<>(Map.of(
                "serviceName", "payment-gateway",
                "replicas", 3,
                "cpuPercent", 78,
                "memoryMb", 420,
                "status", "DEGRADED",
                "region", "us-east-1")));
        services.put("order-processor", new LinkedHashMap<>(Map.of(
                "serviceName", "order-processor",
                "replicas", 2,
                "cpuPercent", 34,
                "memoryMb", 256,
                "status", "HEALTHY",
                "region", "us-east-1")));
        services.put("identity-provider", new LinkedHashMap<>(Map.of(
                "serviceName", "identity-provider",
                "replicas", 2,
                "cpuPercent", 22,
                "memoryMb", 192,
                "status", "HEALTHY",
                "region", "us-west-2")));
    }

    public List<Map<String, Object>> listTools() {
        return List.of(
                Map.of(
                        "name", "inspectClusterHealth",
                        "server", "quarkus-mcp-cluster-ops (:8081)",
                        "description", "Inspects live health, replica count, CPU, and memory across cluster services."),
                Map.of(
                        "name", "queryServiceLogs",
                        "server", "quarkus-mcp-cluster-ops (:8081)",
                        "description", "Queries recent structured log entries for a target microservice."),
                Map.of(
                        "name", "scaleDeployment",
                        "server", "quarkus-mcp-cluster-ops (:8081)",
                        "description", "Scales a deployment to the requested replica count."));
    }

    public Map<String, Object> readClusterState() {
        return Map.of(
                "uri", "cluster://topology",
                "cluster", "quarkus-prod-us-east-1",
                "services", List.copyOf(services.values()));
    }

    public Map<String, Object> callTool(String toolName, Map<String, Object> arguments) {
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        return switch (toolName) {
            case "inspectClusterHealth" -> Map.of(
                    "toolName", "inspectClusterHealth",
                    "server", "quarkus-mcp-cluster-ops (:8081)",
                    "arguments", args,
                    "result", readClusterState());
            case "queryServiceLogs" -> {
                String svc = String.valueOf(args.getOrDefault("serviceName", "payment-gateway"));
                yield Map.of(
                        "toolName", "queryServiceLogs",
                        "server", "quarkus-mcp-cluster-ops (:8081)",
                        "arguments", Map.of("serviceName", svc),
                        "result", List.of(
                                Map.of("level", "WARN", "service", svc, "message", "p99 latency spike (310ms) on /v1/charge"),
                                Map.of("level", "INFO", "service", svc, "message", "Connection pool utilization at 84%")));
            }
            case "scaleDeployment" -> {
                String svc = String.valueOf(args.getOrDefault("serviceName", "payment-gateway"));
                int replicas = args.get("replicas") instanceof Number n ? n.intValue() : 4;
                Map<String, Object> updated = new LinkedHashMap<>(services.getOrDefault(svc, Map.of()));
                updated.put("serviceName", svc);
                updated.put("replicas", replicas);
                updated.put("cpuPercent", Math.max(18, 78 / Math.max(1, replicas / 2)));
                updated.put("memoryMb", 256);
                updated.put("status", "HEALTHY");
                updated.put("region", updated.getOrDefault("region", "us-east-1"));
                services.put(svc, updated);
                yield Map.of(
                        "toolName", "scaleDeployment",
                        "server", "quarkus-mcp-cluster-ops (:8081)",
                        "arguments", Map.of("serviceName", svc, "replicas", replicas),
                        "result", updated);
            }
            default -> throw new IllegalArgumentException("Unsupported MCP tool: " + toolName);
        };
    }
}
