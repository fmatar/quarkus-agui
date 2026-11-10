package org.agui.showcase.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.mcp.client.McpClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

/**
 * MicroProfile / SmallRye Health {@code @Readiness} probes for the Nomad Backend Orchestrator ({@code :8080}).
 * Complements {@code quarkus-langchain4j-mcp}'s built-in {@code McpClientHealthCheck} with upstream readiness
 * probes for {@code mcp-server} ({@code :8081/q/health/ready}), {@code a2a-server} ({@code :8082/q/health/ready}),
 * and local Ollama ({@code /api/tags}).
 */
@ApplicationScoped
public class NomadBackendReadinessChecks {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(1800))
            .build();

    @ConfigProperty(name = "agui.mcp.health-url", defaultValue = "http://127.0.0.1:8081/q/health/ready")
    String mcpHealthUrl = "http://127.0.0.1:8081/q/health/ready";

    @ConfigProperty(name = "agui.a2a.base-url", defaultValue = "http://127.0.0.1:8082")
    String a2aBaseUrl = "http://127.0.0.1:8082";

    @ConfigProperty(name = "quarkus.langchain4j.ollama.base-url", defaultValue = "http://127.0.0.1:11434")
    String ollamaBaseUrl = "http://127.0.0.1:11434";

    @ConfigProperty(name = "quarkus.langchain4j.ollama.chat-model.model-id", defaultValue = "llama3.2:latest")
    String defaultOllamaModel = "llama3.2:latest";

    HealthCheckResponse probeMcpUpstreams() {
        return probeSmallRyeEndpoint("nomad-mcp-upstreams", mcpHealthUrl);
    }

    HealthCheckResponse probeA2aSpecialist() {
        return probeSmallRyeEndpoint("nomad-a2a-specialist", a2aBaseUrl + "/q/health/ready");
    }

    HealthCheckResponse probeSmallRyeEndpoint(String checkName, String url) {
        HealthCheckResponseBuilder builder = HealthCheckResponse.named(checkName);
        builder.withData("endpoint", url);
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(2500))
                    .GET()
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            boolean up = res.statusCode() >= 200 && res.statusCode() < 300;
            if (res.body() != null && !res.body().isBlank()) {
                JsonNode root = objectMapper.readTree(res.body());
                if (root.has("status") && !"UP".equalsIgnoreCase(root.path("status").asText("DOWN"))) {
                    up = false;
                }
                JsonNode checks = root.path("checks");
                if (checks.isArray()) {
                    for (JsonNode check : checks) {
                        JsonNode data = check.path("data");
                        if (data.isObject()) {
                            for (Map.Entry<String, JsonNode> entry : data.properties()) {
                                builder.withData(entry.getKey(), entry.getValue().asText());
                            }
                        }
                    }
                }
            } else if (!up) {
                builder.withData("error", "HTTP " + res.statusCode());
            }
            return builder.status(up).build();
        } catch (Exception ex) {
            return builder
                    .down()
                    .withData("error", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage())
                    .build();
        }
    }

    HealthCheckResponse probeOllamaEndpoint() {
        String url = ollamaBaseUrl + "/api/tags";
        HealthCheckResponseBuilder builder = HealthCheckResponse.named("ollama-chat-model")
                .withData("endpoint", url)
                .withData("model", defaultOllamaModel);
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(2000))
                    .GET()
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 200 && res.statusCode() < 300) {
                return builder.up().build();
            }
            return builder.down().withData("error", "HTTP " + res.statusCode()).build();
        } catch (Exception ex) {
            return builder
                    .down()
                    .withData("error", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage())
                    .build();
        }
    }

    public Map<String, Object> checkAllServices(McpClient nomadMcpClient) {
        List<Map<String, Object>> services = new ArrayList<>();

        try {
            nomadMcpClient.checkHealth();
            services.add(Map.of(
                    "id", "mcp-client-nomad",
                    "name", "Quarkus LangChain4j MCP Client (nomad)",
                    "category", "protocol",
                    "status", "UP"));
        } catch (Exception ex) {
            services.add(Map.of(
                    "id", "mcp-client-nomad",
                    "name", "Quarkus LangChain4j MCP Client (nomad)",
                    "category", "protocol",
                    "status", "DOWN",
                    "error", ex.getMessage() == null ? "MCP ping failed" : ex.getMessage()));
        }

        appendHealthResponseEntries(services, probeSmallRyeEndpoint("nomad-mcp-upstreams", mcpHealthUrl), "Quarkus MCP Server (:8081)");
        appendHealthResponseEntries(services, probeSmallRyeEndpoint("nomad-a2a-specialist", a2aBaseUrl + "/q/health/ready"), "Quarkus A2A Specialist (:8082)");
        appendHealthResponseEntries(services, probeOllamaEndpoint(), "Ollama Local LLM (" + defaultOllamaModel + ")");

        long upCount = services.stream().filter(s -> "UP".equals(s.get("status"))).count();
        String overall = upCount == services.size() ? "UP" : (upCount == 0 ? "DOWN" : "DEGRADED");

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("orchestrator", "Nomad-Concierge-Orchestrator");
        report.put("status", overall);
        report.put("checkedAt", Instant.now().toString());
        report.put("services", services);
        return report;
    }

    private static void appendHealthResponseEntries(
            List<Map<String, Object>> services,
            HealthCheckResponse response,
            String displayName) {
        boolean isUp = response.getStatus() == HealthCheckResponse.Status.UP;
        Map<String, Object> primary = new LinkedHashMap<>();
        primary.put("id", response.getName());
        primary.put("name", displayName);
        primary.put("category", "protocol");
        primary.put("status", isUp ? "UP" : "DOWN");
        response.getData().ifPresent(data -> {
            if (data.get("error") != null) {
                primary.put("error", String.valueOf(data.get("error")));
            }
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                String k = entry.getKey();
                if (k.endsWith(".status")) {
                    String prefix = k.substring(0, k.length() - ".status".length());
                    String subStatus = String.valueOf(entry.getValue());
                    Map<String, Object> sub = new LinkedHashMap<>();
                    sub.put("id", prefix);
                    sub.put("name", prefix);
                    sub.put("category", "upstream-api");
                    sub.put("status", subStatus);
                    Object err = data.get(prefix + ".error");
                    if (err != null) {
                        sub.put("error", String.valueOf(err));
                    }
                    services.add(sub);
                }
            }
        });
        services.addFirst(primary);
    }

    @Readiness
    @ApplicationScoped
    public static class McpUpstreamsReadinessCheck implements HealthCheck {

        private final NomadBackendReadinessChecks parent;

        McpUpstreamsReadinessCheck() {
            this.parent = null;
        }

        @Inject
        public McpUpstreamsReadinessCheck(NomadBackendReadinessChecks parent) {
            this.parent = parent;
        }

        @Override
        public HealthCheckResponse call() {
            return parent.probeMcpUpstreams();
        }
    }

    @Readiness
    @ApplicationScoped
    public static class A2aSpecialistReadinessCheck implements HealthCheck {

        private final NomadBackendReadinessChecks parent;

        A2aSpecialistReadinessCheck() {
            this.parent = null;
        }

        @Inject
        public A2aSpecialistReadinessCheck(NomadBackendReadinessChecks parent) {
            this.parent = parent;
        }

        @Override
        public HealthCheckResponse call() {
            return parent.probeA2aSpecialist();
        }
    }

    @Readiness
    @ApplicationScoped
    public static class OllamaModelReadinessCheck implements HealthCheck {

        private final NomadBackendReadinessChecks parent;

        OllamaModelReadinessCheck() {
            this.parent = null;
        }

        @Inject
        public OllamaModelReadinessCheck(NomadBackendReadinessChecks parent) {
            this.parent = parent;
        }

        @Override
        public HealthCheckResponse call() {
            return parent.probeOllamaEndpoint();
        }
    }
}
