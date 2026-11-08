package org.agui.showcase.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class ClusterTelemetryService {

    public static final String SERVER_NAME = "cluster-telemetry-mcp";

    private final Map<String, ServiceStatus> services = new ConcurrentHashMap<>();
    private final List<LogEntry> logs = new ArrayList<>();
    private final Map<String, Object> activeTrip = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(1500))
        .build();

    String openMeteoBaseUrl = "https://api.open-meteo.com";
    String wikipediaBaseUrl = "https://en.wikipedia.org";
    String itunesBaseUrl = "https://itunes.apple.com";
    java.util.function.Function<String, String> httpFetcher = this::fetchUrlViaHttp;

    private String fetchUrlViaHttp(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(1500))
                .header("User-Agent", "Nomad-Quarkus-AGUI/1.0")
                .GET()
                .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            return res.statusCode() == 200 ? res.body() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

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

        activeTrip.clear();
        activeTrip.put("uri", "itinerary://active-trip");
        activeTrip.put("activeCity", "Kyoto");
        activeTrip.put("country", "Japan");
        activeTrip.put("currency", "JPY");
        activeTrip.put("nights", 4);
        activeTrip.put("stopTitle", "Hoshinoya Arashiyama Pavilion");
        activeTrip.put("budgetUsd", 3200.0);
        activeTrip.put("allocatedUsd", 1680.0);
    }

    @Tool(description = "Inspect cluster health, pod replicas, CPU, and memory across services")
    public Map<String, Object> inspectClusterHealth(
            @ToolArg(description = "Target AWS region", defaultValue = "us-east-1") String region) {
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

    @Tool(description = "Query recent structured logs by service name and severity")
    public List<LogEntry> queryServiceLogs(
            @ToolArg(description = "Microservice name", defaultValue = "payment-service") String serviceName,
            @ToolArg(description = "Log severity filter", defaultValue = "ALL") String severity,
            @ToolArg(description = "Maximum number of log entries", defaultValue = "5") int limit) {
        String targetService = (serviceName == null || serviceName.isBlank()) ? "payment-service" : serviceName.trim();
        String targetSeverity = (severity == null || severity.isBlank()) ? "ALL" : severity.trim().toUpperCase();
        int clampedLimit = Math.max(1, Math.min(limit, 50));

        return logs.stream()
            .filter(entry -> entry.serviceName().equalsIgnoreCase(targetService))
            .filter(entry -> "ALL".equals(targetSeverity) || entry.severity().equalsIgnoreCase(targetSeverity))
            .limit(clampedLimit)
            .toList();
    }

    @Tool(description = "Scale service deployment replica count between 1 and 20")
    public ServiceStatus scaleDeployment(
            @ToolArg(description = "Microservice name") String serviceName,
            @ToolArg(description = "Target replica count (1..20)") int targetReplicas) {
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

    @Resource(uri = "cluster://topology", description = "Live cluster topology and replica health snapshot", mimeType = "application/json")
    public TextResourceContents clusterTopologyResource() {
        return TextResourceContents.create("cluster://topology", getTopologySnapshot().toString());
    }

    @Resource(uri = "itinerary://active-trip", description = "Active Nomad multi-city cultural itinerary and budget state", mimeType = "application/json")
    public TextResourceContents activeTripResource() {
        return TextResourceContents.create("itinerary://active-trip", getActiveTripSnapshot().toString());
    }

    @Resource(uri = "traveler://preferences", description = "Traveler aesthetic, pace, and dietary preferences", mimeType = "application/json")
    public TextResourceContents travelerPreferencesResource() {
        return TextResourceContents.create("traveler://preferences", getTravelerPreferences().toString());
    }

    @Prompt(name = "weekend-cultural-escape", description = "Synthesize a 3-day architectural, culinary, and musical itinerary for a target city")
    public PromptMessage weekendCulturalEscapePrompt(
            @PromptArg(description = "Destination city", defaultValue = "Kyoto") String city) {
        String target = (city == null || city.isBlank()) ? "Kyoto" : city.trim();
        return PromptMessage.withUserRole(new TextContent(
            "Curate a 3-day architectural, culinary, and local music itinerary in " + target
            + " with live weather, currency conversion, and a 30s local soundtrack preview."));
    }

    @Prompt(name = "local-music-and-food-walk", description = "Curate an evening listening bar and neighborhood culinary walk")
    public PromptMessage localMusicAndFoodWalkPrompt(
            @PromptArg(description = "Destination city", defaultValue = "Lisbon") String city) {
        String target = (city == null || city.isBlank()) ? "Lisbon" : city.trim();
        return PromptMessage.withUserRole(new TextContent(
            "Design an intimate evening listening-bar and neighborhood culinary walk through " + target
            + " with playable audio preview and local budget breakdown."));
    }

    @Tool(description = "Fetch live Open-Meteo weather forecast and temperature for a travel destination")
    public Map<String, Object> fetchCityWeather(
            @ToolArg(description = "City name (e.g., Kyoto, Lisbon, Reykjavik)", defaultValue = "Kyoto") String city) {
        String resolvedCity = normalizeCity(city);
        double[] coords = switch (resolvedCity.toLowerCase(Locale.ROOT)) {
            case "lisbon" -> new double[]{38.7223, -9.1393, 22.1, 14.0};
            case "reykjavik" -> new double[]{64.1466, -21.9426, 6.2, 19.5};
            case "tokyo" -> new double[]{35.6762, 139.6503, 19.2, 11.0};
            case "paris" -> new double[]{48.8566, 2.3522, 16.8, 12.0};
            case "rome" -> new double[]{41.9028, 12.4964, 21.4, 9.5};
            case "barcelona" -> new double[]{41.3874, 2.1686, 22.5, 13.0};
            case "london" -> new double[]{51.5072, -0.1276, 14.6, 15.0};
            case "seoul" -> new double[]{37.5665, 126.9780, 17.5, 10.0};
            case "new york" -> new double[]{40.7128, -74.0060, 18.0, 14.5};
            case "marrakech" -> new double[]{31.6295, -7.9811, 26.4, 10.5};
            default -> new double[]{35.0116, 135.7681, 18.4, 8.5};
        };
        String defaultCondition = switch (resolvedCity.toLowerCase(Locale.ROOT)) {
            case "lisbon" -> "Atlantic Breeze & Golden Sun";
            case "reykjavik" -> "Crisp Sub-Arctic Aurora Sky";
            case "tokyo" -> "Clear Pacific Horizon";
            case "paris" -> "Soft Seine Daylight";
            case "rome" -> "Warm Mediterranean Sun";
            case "barcelona" -> "Balearic Coastal Breeze";
            case "marrakech" -> "Warm Atlas Foothill Sun";
            default -> "Clear Cedar & Autumn Mist";
        };
        double tempC = coords[2];
        double windKph = coords[3];
        String source = "open-meteo-fallback";
        try {
            String url = String.format(Locale.ROOT,
                "%s/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,wind_speed_10m",
                openMeteoBaseUrl, coords[0], coords[1]);
            String body = httpFetcher.apply(url);
            if (body != null) {
                JsonNode current = objectMapper.readTree(body).path("current");
                if (current.has("temperature_2m")) {
                    tempC = current.path("temperature_2m").asDouble(tempC);
                    windKph = current.path("wind_speed_10m").asDouble(windKph);
                    source = "api.open-meteo.com";
                }
            }
        } catch (Exception ignored) {
            // Deterministic offline/CI fallback
        }
        Map<String, Object> weather = new LinkedHashMap<>();
        weather.put("city", resolvedCity);
        weather.put("latitude", coords[0]);
        weather.put("longitude", coords[1]);
        weather.put("temperatureC", tempC);
        weather.put("windSpeedKph", windKph);
        weather.put("condition", defaultCondition);
        weather.put("source", source);
        return weather;
    }

    @Tool(description = "Discover local cultural landmarks via Wikipedia REST API and a 30s local music preview via Apple iTunes Search API")
    public Map<String, Object> discoverLocalCultureAndMedia(
            @ToolArg(description = "City or country name", defaultValue = "Kyoto") String destination,
            @ToolArg(description = "Music or cultural search query", defaultValue = "traditional") String genre) {
        String resolvedCity = normalizeCity(destination);
        String resolvedGenre = (genre == null || genre.isBlank()) ? "traditional" : genre.trim();

        String wikiExtract = switch (resolvedCity.toLowerCase(Locale.ROOT)) {
            case "lisbon" -> "Lisbon's hillside neighborhoods of Alfama and Chiado blend maritime azulejo tilework, historic tramlines, and intimate Fado houses.";
            case "reykjavik" -> "Reykjavik pairs geothermal coastal lagoons and modernist Nordic architecture at Harpa with an experimental neo-classical music scene.";
            default -> "Kyoto preserves over a millennium of Zen temple gardens, machiya timber townhouses in Gion, and contemplative koto and shakuhachi acoustics.";
        };
        String imageUrl = switch (resolvedCity.toLowerCase(Locale.ROOT)) {
            case "lisbon" -> "https://images.unsplash.com/photo-1513735492246-483525079686?auto=format&fit=crop&w=1000&q=80";
            case "reykjavik" -> "https://images.unsplash.com/photo-1504893524553-b855bce32c67?auto=format&fit=crop&w=1000&q=80";
            default -> "https://images.unsplash.com/photo-1493976040374-85c8e12f0c0e?auto=format&fit=crop&w=1000&q=80";
        };
        String trackName = switch (resolvedCity.toLowerCase(Locale.ROOT)) {
            case "lisbon" -> "Canção do Mar (Acoustic Fado Session)";
            case "reykjavik" -> "Hoppípolla — Nordic Strings";
            default -> "Haru no Umi (Spring Sea — Koto & Shakuhachi)";
        };
        String artistName = switch (resolvedCity.toLowerCase(Locale.ROOT)) {
            case "lisbon" -> "Alfama Guitarra Ensemble";
            case "reykjavik" -> "Reykjavik Chamber Collective";
            default -> "Kyoto Koto Consort";
        };
        String previewUrl = "https://www.w3schools.com/html/horse.mp3";
        String artworkUrl = imageUrl;

        try {
            String wikiUrl = wikipediaBaseUrl + "/api/rest_v1/page/summary/" + URLEncoder.encode(resolvedCity, StandardCharsets.UTF_8);
            String wikiBody = httpFetcher.apply(wikiUrl);
            if (wikiBody != null) {
                JsonNode root = objectMapper.readTree(wikiBody);
                if (root.hasNonNull("extract") && !root.path("extract").asText().isBlank()) {
                    wikiExtract = root.path("extract").asText();
                }
                if (root.path("thumbnail").hasNonNull("source")) {
                    imageUrl = root.path("thumbnail").path("source").asText();
                }
            }
        } catch (Exception ignored) {
        }

        try {
            String searchTerm = URLEncoder.encode(resolvedCity + " " + resolvedGenre, StandardCharsets.UTF_8);
            String itunesUrl = itunesBaseUrl + "/search?term=" + searchTerm + "&media=music&limit=1";
            String itunesBody = httpFetcher.apply(itunesUrl);
            if (itunesBody != null) {
                JsonNode results = objectMapper.readTree(itunesBody).path("results");
                if (results.isArray() && !results.isEmpty()) {
                    JsonNode first = results.get(0);
                    trackName = first.path("trackName").asText(trackName);
                    artistName = first.path("artistName").asText(artistName);
                    previewUrl = first.path("previewUrl").asText(previewUrl);
                    artworkUrl = first.path("artworkUrl100").asText(artworkUrl);
                }
            }
        } catch (Exception ignored) {
        }

        Map<String, Object> audioTrack = new LinkedHashMap<>();
        audioTrack.put("trackName", trackName);
        audioTrack.put("artistName", artistName);
        audioTrack.put("previewUrl", previewUrl);
        audioTrack.put("artworkUrl", artworkUrl);
        audioTrack.put("durationSeconds", 30);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("destination", resolvedCity);
        payload.put("genre", resolvedGenre);
        payload.put("culturalSummary", wikiExtract);
        payload.put("heroImageUrl", imageUrl);
        payload.put("audioTrack", audioTrack);
        payload.put("wikiSourceUrl", "https://en.wikipedia.org/wiki/" + URLEncoder.encode(resolvedCity, StandardCharsets.UTF_8));
        return payload;
    }

    @Tool(description = "Update or reserve a cultural stop in the active trip itinerary")
    public Map<String, Object> updateTripItinerary(
            @ToolArg(description = "Destination city") String city,
            @ToolArg(description = "Stop or boutique stay title") String stopTitle,
            @ToolArg(description = "Number of nights or duration", defaultValue = "3") int nights,
            @ToolArg(description = "Estimated cost in USD", defaultValue = "950") double costUsd) {
        String resolvedCity = normalizeCity(city);
        String resolvedStop = (stopTitle == null || stopTitle.isBlank())
            ? resolvedCity + " Architectural Sanctuary"
            : stopTitle.trim();
        int clampedNights = Math.max(1, Math.min(nights, 30));
        double safeCost = Math.max(0.0, costUsd);

        activeTrip.put("activeCity", resolvedCity);
        activeTrip.put("stopTitle", resolvedStop);
        activeTrip.put("nights", clampedNights);
        activeTrip.put("allocatedUsd", safeCost);
        activeTrip.put("updatedAt", Instant.now().toString());
        return getActiveTripSnapshot();
    }

    public Map<String, Object> getActiveTripSnapshot() {
        return new LinkedHashMap<>(activeTrip);
    }

    public Map<String, Object> getTravelerPreferences() {
        Map<String, Object> prefs = new LinkedHashMap<>();
        prefs.put("uri", "traveler://preferences");
        prefs.put("style", "Architectural Minimalist & Slow Cultural Immersion");
        prefs.put("pace", "Unrushed (2 curated stops per day)");
        prefs.put("interests", List.of("Craft Architecture", "Vinyl & Local Acoustics", "Seasonal Tasting Menus"));
        prefs.put("homeCurrency", "USD");
        return prefs;
    }

    public List<Map<String, Object>> listPrompts() {
        return List.of(
            Map.of(
                "name", "weekend-cultural-escape",
                "description", "Synthesize a 3-day architectural, culinary, and musical itinerary for a target city",
                "arguments", List.of("city")
            ),
            Map.of(
                "name", "local-music-and-food-walk",
                "description", "Curate an evening listening bar and neighborhood culinary walk",
                "arguments", List.of("city")
            )
        );
    }

    public Map<String, Object> renderPrompt(String name, String city) {
        String resolvedCity = normalizeCity(city);
        PromptMessage msg = "local-music-and-food-walk".equalsIgnoreCase(name)
            ? localMusicAndFoodWalkPrompt(resolvedCity)
            : weekendCulturalEscapePrompt(resolvedCity);
        String text = msg.content() instanceof TextContent tc ? tc.text() : msg.content().toString();
        return Map.of(
            "name", name == null ? "weekend-cultural-escape" : name,
            "city", resolvedCity,
            "prompt", text
        );
    }

    private String normalizeCity(String city) {
        if (city == null || city.isBlank()) {
            return "Kyoto";
        }
        String lower = city.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("lisbon") || lower.contains("portugal")) {
            return "Lisbon";
        }
        if (lower.contains("reykjavik") || lower.contains("iceland")) {
            return "Reykjavik";
        }
        if (lower.contains("kyoto") || lower.contains("japan")) {
            return "Kyoto";
        }
        return city.trim();
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
            ),
            Map.of(
                "name", "fetchCityWeather",
                "description", "Fetch live Open-Meteo weather forecast and temperature for a travel destination",
                "parameters", List.of("city")
            ),
            Map.of(
                "name", "discoverLocalCultureAndMedia",
                "description", "Discover local cultural landmarks via Wikipedia REST API and a 30s local music preview via Apple iTunes Search API",
                "parameters", List.of("destination", "genre")
            ),
            Map.of(
                "name", "updateTripItinerary",
                "description", "Update or reserve a cultural stop in the active trip itinerary",
                "parameters", List.of("city", "stopTitle", "nights", "costUsd")
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
                case "fetchCityWeather" -> fetchCityWeather(String.valueOf(args.getOrDefault("city", "Kyoto")));
                case "discoverLocalCultureAndMedia" -> discoverLocalCultureAndMedia(
                    String.valueOf(args.getOrDefault("destination", "Kyoto")),
                    String.valueOf(args.getOrDefault("genre", "traditional"))
                );
                case "updateTripItinerary" -> {
                    String city = String.valueOf(args.getOrDefault("city", "Kyoto"));
                    String stopTitle = String.valueOf(args.getOrDefault("stopTitle", "Hoshinoya Arashiyama"));
                    int nights = args.get("nights") instanceof Number n ? n.intValue() : 3;
                    double costUsd = args.get("costUsd") instanceof Number n ? n.doubleValue() : 950.0;
                    yield updateTripItinerary(city, stopTitle, nights, costUsd);
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
