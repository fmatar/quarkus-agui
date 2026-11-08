package org.agui.showcase.backend;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP Gateway Client for OpsPilot & Nomad Concierge Orchestrator (:8080 -> :8081).
 * Exposes MCP tools (inspectClusterHealth, queryServiceLogs, scaleDeployment, fetchCityWeather,
 * discoverLocalCultureAndMedia, updateTripItinerary), resources, and prompt templates.
 */
@ApplicationScoped
public class McpGatewayClient {

    private final Map<String, Map<String, Object>> services = new ConcurrentHashMap<>();
    private final Map<String, Object> nomadTrip = new ConcurrentHashMap<>();

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

        nomadTrip.clear();
        nomadTrip.put("uri", "itinerary://active-trip");
        nomadTrip.put("activeCity", "Kyoto");
        nomadTrip.put("country", "Japan");
        nomadTrip.put("currency", "JPY");
        nomadTrip.put("exchangeRate", 151.40);
        nomadTrip.put("temperatureC", 18.4);
        nomadTrip.put("weatherCondition", "Clear Cedar & Autumn Mist");
        nomadTrip.put("nights", 4);
        nomadTrip.put("stopTitle", "Hoshinoya Arashiyama Pavilion");
        nomadTrip.put("budgetUsd", 3200.0);
        nomadTrip.put("allocatedUsd", 1680.0);
        nomadTrip.put("soundtrack", Map.of(
                "trackName", "Haru no Umi (Spring Sea — Koto & Shakuhachi)",
                "artistName", "Kyoto Koto Consort",
                "previewUrl", "https://www.w3schools.com/html/horse.mp3"));
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
                        "description", "Scales a deployment to the requested replica count."),
                Map.of(
                        "name", "fetchCityWeather",
                        "server", "nomad-mcp-concierge (:8081)",
                        "description", "Fetches live Open-Meteo weather forecast and temperature for a travel destination."),
                Map.of(
                        "name", "discoverLocalCultureAndMedia",
                        "server", "nomad-mcp-concierge (:8081)",
                        "description", "Queries Wikipedia REST API and Apple iTunes Search API for cultural landmarks and 30s local music previews."),
                Map.of(
                        "name", "updateTripItinerary",
                        "server", "nomad-mcp-concierge (:8081)",
                        "description", "Updates or reserves a cultural sanctuary or stop in the active trip itinerary."));
    }

    private static final List<String> CANONICAL_SERVICE_ORDER = List.of(
            "payment-gateway",
            "order-processor",
            "identity-provider");

    public Map<String, Object> readClusterState() {
        List<Map<String, Object>> ordered = new ArrayList<>();
        for (String svcName : CANONICAL_SERVICE_ORDER) {
            Map<String, Object> svc = services.get(svcName);
            if (svc != null) {
                ordered.add(svc);
            }
        }
        for (Map.Entry<String, Map<String, Object>> entry : services.entrySet()) {
            if (!CANONICAL_SERVICE_ORDER.contains(entry.getKey())) {
                ordered.add(entry.getValue());
            }
        }
        return Map.of(
                "uri", "cluster://topology",
                "cluster", "quarkus-prod-us-east-1",
                "services", List.copyOf(ordered),
                "nomadTrip", new LinkedHashMap<>(nomadTrip));
    }

    public Map<String, Object> readResource(String uri) {
        if ("traveler://preferences".equals(uri)) {
            return Map.of(
                    "uri", "traveler://preferences",
                    "style", "Architectural Minimalist & Slow Cultural Immersion",
                    "pace", "Unrushed (2 curated stops per day)",
                    "homeCurrency", "USD");
        }
        return new LinkedHashMap<>(nomadTrip);
    }

    public Map<String, Object> getPromptTemplate(String promptName, Map<String, Object> args) {
        String city = String.valueOf(args != null ? args.getOrDefault("city", "Kyoto") : "Kyoto");
        return Map.of(
                "promptName", promptName == null ? "weekend-cultural-escape" : promptName,
                "arguments", Map.of("city", city),
                "expandedPrompt", "Synthesize a 3-day architectural, culinary, and local music itinerary for " + city
                        + " with Open-Meteo telemetry, Wikipedia cultural context, and a 30s iTunes soundtrack preview.");
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
            case "fetchCityWeather" -> {
                String city = normalizeCity(String.valueOf(args.getOrDefault("city", "Kyoto")));
                double tempC = switch (city.toLowerCase(Locale.ROOT)) {
                    case "lisbon" -> 22.1;
                    case "reykjavik" -> 6.2;
                    default -> 18.4;
                };
                String condition = switch (city.toLowerCase(Locale.ROOT)) {
                    case "lisbon" -> "Atlantic Breeze & Golden Sun";
                    case "reykjavik" -> "Crisp Sub-Arctic Aurora Sky";
                    default -> "Clear Cedar & Autumn Mist";
                };
                nomadTrip.put("activeCity", city);
                nomadTrip.put("temperatureC", tempC);
                nomadTrip.put("weatherCondition", condition);
                yield Map.of(
                        "toolName", "fetchCityWeather",
                        "server", "nomad-mcp-concierge (:8081)",
                        "arguments", Map.of("city", city),
                        "result", Map.of(
                                "city", city,
                                "temperatureC", tempC,
                                "condition", condition,
                                "source", "api.open-meteo.com"));
            }
            case "discoverLocalCultureAndMedia" -> {
                String city = normalizeCity(String.valueOf(args.getOrDefault("destination", "Kyoto")));
                String genre = String.valueOf(args.getOrDefault("genre", "traditional"));
                Map<String, Object> culture = buildCultureResult(city, genre);
                nomadTrip.put("activeCity", city);
                nomadTrip.put("country", culture.get("country"));
                nomadTrip.put("currency", culture.get("currency"));
                nomadTrip.put("exchangeRate", culture.get("exchangeRate"));
                nomadTrip.put("soundtrack", culture.get("audioTrack"));
                yield Map.of(
                        "toolName", "discoverLocalCultureAndMedia",
                        "server", "nomad-mcp-concierge (:8081)",
                        "arguments", Map.of("destination", city, "genre", genre),
                        "result", culture);
            }
            case "updateTripItinerary" -> {
                String city = normalizeCity(String.valueOf(args.getOrDefault("city", "Kyoto")));
                String stopTitle = String.valueOf(args.getOrDefault("stopTitle", city + " Architectural Sanctuary"));
                int nights = args.get("nights") instanceof Number n ? n.intValue() : 4;
                double costUsd = args.get("costUsd") instanceof Number n ? n.doubleValue() : 1680.0;
                nomadTrip.put("activeCity", city);
                nomadTrip.put("stopTitle", stopTitle);
                nomadTrip.put("nights", nights);
                nomadTrip.put("allocatedUsd", costUsd);
                yield Map.of(
                        "toolName", "updateTripItinerary",
                        "server", "nomad-mcp-concierge (:8081)",
                        "arguments", Map.of("city", city, "stopTitle", stopTitle, "nights", nights, "costUsd", costUsd),
                        "result", new LinkedHashMap<>(nomadTrip));
            }
            default -> throw new IllegalArgumentException("Unsupported MCP tool: " + toolName);
        };
    }

    private Map<String, Object> buildCultureResult(String city, String genre) {
        return switch (city.toLowerCase(Locale.ROOT)) {
            case "lisbon" -> Map.of(
                    "destination", "Lisbon",
                    "country", "Portugal",
                    "currency", "EUR",
                    "exchangeRate", 0.92,
                    "genre", genre,
                    "culturalSummary", "Lisbon's hillside districts of Alfama and Chiado pair hand-painted azulejo tile facades and Miradouro terraces with intimate evening Fado sessions.",
                    "heroImageUrl", "https://images.unsplash.com/photo-1513735492246-483525079686?auto=format&fit=crop&w=1000&q=80",
                    "audioTrack", Map.of(
                            "trackName", "Canção do Mar (Acoustic Fado Session)",
                            "artistName", "Alfama Guitarra Ensemble",
                            "previewUrl", "https://www.w3schools.com/html/horse.mp3",
                            "durationSeconds", 30));
            case "reykjavik" -> Map.of(
                    "destination", "Reykjavik",
                    "country", "Iceland",
                    "currency", "ISK",
                    "exchangeRate", 137.80,
                    "genre", genre,
                    "culturalSummary", "Reykjavik contrasts basalt coastal geometry at Harpa Concert Hall and geothermal Sky Lagoons with Nordic neo-classical string arrangements.",
                    "heroImageUrl", "https://images.unsplash.com/photo-1504893524553-b855bce32c67?auto=format&fit=crop&w=1000&q=80",
                    "audioTrack", Map.of(
                            "trackName", "Hoppípolla — Nordic Strings",
                            "artistName", "Reykjavik Chamber Collective",
                            "previewUrl", "https://www.w3schools.com/html/horse.mp3",
                            "durationSeconds", 30));
            default -> Map.of(
                    "destination", "Kyoto",
                    "country", "Japan",
                    "currency", "JPY",
                    "exchangeRate", 151.40,
                    "genre", genre,
                    "culturalSummary", "Kyoto weaves centuries-old Zen moss gardens in Arashiyama, cedar machiya townhouses in Gion, and meditative koto and shakuhachi suites.",
                    "heroImageUrl", "https://images.unsplash.com/photo-1493976040374-85c8e12f0c0e?auto=format&fit=crop&w=1000&q=80",
                    "audioTrack", Map.of(
                            "trackName", "Haru no Umi (Spring Sea — Koto & Shakuhachi)",
                            "artistName", "Kyoto Koto Consort",
                            "previewUrl", "https://www.w3schools.com/html/horse.mp3",
                            "durationSeconds", 30));
        };
    }

    private String normalizeCity(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Kyoto";
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.contains("lisbon") || lower.contains("portugal")) {
            return "Lisbon";
        }
        if (lower.contains("reykjavik") || lower.contains("iceland")) {
            return "Reykjavik";
        }
        return "Kyoto";
    }
}
