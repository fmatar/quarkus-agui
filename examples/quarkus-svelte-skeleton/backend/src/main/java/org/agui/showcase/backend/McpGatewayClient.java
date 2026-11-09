package org.agui.showcase.backend;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpGetPromptResult;
import dev.langchain4j.mcp.client.McpReadResourceResult;
import dev.langchain4j.mcp.client.McpResourceContents;
import dev.langchain4j.mcp.client.McpTextResourceContents;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.service.tool.ToolExecutionResult;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Native {@code quarkus-langchain4j-mcp} Gateway Client (:8080 -> :8081/mcp) for the Nomad Travel Concierge.
 * Uses {@link DefaultMcpClient} over {@link StreamableHttpMcpTransport} for MCP tools, resources, prompts,
 * and protocol-level {@link McpClient#checkHealth()} pings without fake fallbacks or assumed defaults.
 */
@ApplicationScoped
public class McpGatewayClient implements AutoCloseable {

    private static final Set<String> SUPPORTED_TOOLS = Set.of(
            "fetchCityWeather", "discoverLocalCultureAndMedia", "updateTripItinerary");

    private static final List<String> CURATED_CITIES = List.of(
            "Beirut", "Kyoto", "Lisbon", "Reykjavik", "Tokyo", "Oaxaca", "Paris", "Rome",
            "Barcelona", "Marrakech", "Copenhagen", "Vienna", "Berlin", "Seoul", "Istanbul",
            "Buenos Aires", "Amman", "Cairo", "Athens", "Rio de Janeiro");

    private static final Map<String, String> CITY_SANCTUARIES = Map.of(
            "kyoto", "Hoshinoya Arashiyama Pavilion",
            "lisbon", "Palácio Belmonte Alfama Suite",
            "reykjavik", "The Retreat at Blue Lagoon Moss Villa",
            "tokyo", "Aman Tokyo Otemachi Sanctuary",
            "paris", "Cour des Vosges Marais Residence",
            "beirut", "Albergo Achrafieh Heritage Residence",
            "oaxaca", "Casa Oaxaca Courtyard Sanctuary");

    private final Map<String, Object> nomadTrip = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<String, Object> travelerPreferences = Collections.synchronizedMap(new LinkedHashMap<>());
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(1800))
            .build();

    @ConfigProperty(name = "agui.mcp.base-url", defaultValue = "http://127.0.0.1:8081")
    String mcpBaseUrl = "http://127.0.0.1:8081";

    private volatile McpClient activeMcpClient;
    private volatile String activeMcpEndpoint;

    public McpGatewayClient() {
        resetState();
    }

    public final void resetState() {
        synchronized (nomadTrip) {
            nomadTrip.clear();
            nomadTrip.put("uri", "itinerary://active-trip");
            nomadTrip.put("activeCity", null);
            nomadTrip.put("country", null);
            nomadTrip.put("currency", null);
            nomadTrip.put("exchangeRate", null);
            nomadTrip.put("temperatureC", null);
            nomadTrip.put("weatherCondition", null);
            nomadTrip.put("nights", null);
            nomadTrip.put("stopTitle", null);
            nomadTrip.put("budgetUsd", null);
            nomadTrip.put("allocatedUsd", null);
            nomadTrip.put("homeCurrency", null);
            nomadTrip.put("soundtrack", null);
        }

        synchronized (travelerPreferences) {
            travelerPreferences.clear();
            travelerPreferences.put("uri", "traveler://preferences");
            travelerPreferences.put("travelerName", null);
            travelerPreferences.put("style", null);
            travelerPreferences.put("dietary", null);
            travelerPreferences.put("musicTaste", null);
            travelerPreferences.put("pace", null);
            travelerPreferences.put("homeCurrency", null);
        }
    }

    synchronized McpClient getOrCreateMcpClient() {
        String targetEndpoint = mcpBaseUrl + "/mcp";
        if (activeMcpClient != null && targetEndpoint.equals(activeMcpEndpoint)) {
            return activeMcpClient;
        }
        closeQuietly(activeMcpClient);
        StreamableHttpMcpTransport transport = StreamableHttpMcpTransport.builder()
                .url(targetEndpoint)
                .timeout(Duration.ofSeconds(4))
                .build();
        activeMcpClient = DefaultMcpClient.builder()
                .key("nomad-mcp-concierge")
                .protocolVersion("2025-03-26")
                .initializationTimeout(Duration.ofSeconds(3))
                .transport(transport)
                .toolExecutionTimeout(Duration.ofSeconds(5))
                .resourcesTimeout(Duration.ofSeconds(4))
                .promptsTimeout(Duration.ofSeconds(4))
                .pingTimeout(Duration.ofSeconds(3))
                .reconnectInterval(Duration.ofSeconds(30))
                .build();
        activeMcpEndpoint = targetEndpoint;
        return activeMcpClient;
    }

    @Override
    @PreDestroy
    public synchronized void close() {
        closeQuietly(activeMcpClient);
        activeMcpClient = null;
        activeMcpEndpoint = null;
    }

    private static void closeQuietly(McpClient client) {
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
    }

    public List<Map<String, Object>> listTools() {
        return List.of(
                Map.of(
                        "name", "fetchCityWeather",
                        "server", "nomad-mcp-concierge (:8081)",
                        "description", "Fetches live Open-Meteo weather forecast and temperature for an explicit destination city."),
                Map.of(
                        "name", "discoverLocalCultureAndMedia",
                        "server", "nomad-mcp-concierge (:8081)",
                        "description", "Queries Wikipedia REST API and Apple iTunes Search API for cultural landmarks and 30s local music previews."),
                Map.of(
                        "name", "updateTripItinerary",
                        "server", "nomad-mcp-concierge (:8081)",
                        "description", "Updates or reserves a cultural stop in the active trip itinerary with explicit city, nights, and cost."));
    }

    public Map<String, Object> readTripDossierState() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("uri", "itinerary://active-trip");
        synchronized (nomadTrip) {
            out.put("nomadTrip", new LinkedHashMap<>(nomadTrip));
        }
        out.put("preferences", readResource("traveler://preferences"));
        return out;
    }

    public Map<String, Object> readClusterState() {
        return readTripDossierState();
    }

    public void updateActiveTripFields(Map<String, Object> fields) {
        if (fields == null) {
            return;
        }
        synchronized (nomadTrip) {
            for (Map.Entry<String, Object> entry : fields.entrySet()) {
                if (entry.getKey() != null) {
                    nomadTrip.put(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    public Map<String, Object> readResource(String uri) {
        if ("traveler://preferences".equals(uri)) {
            synchronized (travelerPreferences) {
                return new LinkedHashMap<>(travelerPreferences);
            }
        }
        if (uri != null && uri.startsWith("itinerary://city/")) {
            return readRemoteMcpResource(uri);
        }
        synchronized (nomadTrip) {
            return new LinkedHashMap<>(nomadTrip);
        }
    }

    private Map<String, Object> readRemoteMcpResource(String uri) {
        try {
            McpReadResourceResult res = getOrCreateMcpClient().readResource(uri);
            if (res != null && res.contents() != null) {
                for (McpResourceContents content : res.contents()) {
                    if (content instanceof McpTextResourceContents textContent && textContent.text() != null) {
                        Map<String, Object> parsed = objectMapper.readValue(
                                textContent.text(), new TypeReference<Map<String, Object>>() {});
                        parsed.putIfAbsent("uri", uri);
                        return parsed;
                    }
                }
            }
            return Map.of("uri", uri, "status", "ERROR", "isError", true, "error", "Empty MCP resource payload for " + uri);
        } catch (Exception ex) {
            return Map.of(
                    "uri", uri,
                    "status", "ERROR",
                    "isError", true,
                    "error", "MCP Server (:8081/mcp) resource unavailable: " + ex.getMessage());
        }
    }

    public Map<String, Object> updateTravelerPreferences(Map<String, Object> updates) {
        if (updates != null) {
            synchronized (travelerPreferences) {
                for (Map.Entry<String, Object> entry : updates.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null && !String.valueOf(entry.getValue()).isBlank()) {
                        travelerPreferences.put(entry.getKey(), entry.getValue());
                    }
                }
                travelerPreferences.put("uri", "traveler://preferences");
            }
            if (updates.get("homeCurrency") instanceof String hc && !hc.isBlank()) {
                nomadTrip.put("homeCurrency", hc.trim().toUpperCase(Locale.ROOT));
            }
            syncRemotePreferences(updates);
        }
        return readResource("traveler://preferences");
    }

    public Map<String, Object> updateTripParameters(Integer nights, String homeCurrency) {
        if (nights != null && nights > 0) {
            int clamped = Math.min(30, nights);
            nomadTrip.put("nights", clamped);
        }
        if (homeCurrency != null && !homeCurrency.isBlank()) {
            String normalizedCurrency = homeCurrency.trim().toUpperCase(Locale.ROOT);
            nomadTrip.put("homeCurrency", normalizedCurrency);
            travelerPreferences.put("homeCurrency", normalizedCurrency);
        }
        return readTripDossierState();
    }

    public Map<String, Object> sampleAcousticMood(String city, String weatherCondition, String genre) {
        if (city == null || city.isBlank()) {
            return Map.of(
                    "status", "ERROR",
                    "isError", true,
                    "error", "Required argument 'city' missing for MCP acoustic mood sampling.");
        }
        String resolvedCity = normalizeCity(city);
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("city", resolvedCity);
            if (weatherCondition != null && !weatherCondition.isBlank()) {
                payload.put("weatherCondition", weatherCondition);
            }
            if (genre != null && !genre.isBlank()) {
                payload.put("genre", genre);
            }
            HttpRequest req = HttpRequest.newBuilder(URI.create(mcpBaseUrl + "/api/mcp/sampling"))
                    .timeout(Duration.ofMillis(2000))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 200 && res.statusCode() < 300 && res.body() != null) {
                return objectMapper.readValue(res.body(), new TypeReference<Map<String, Object>>() {});
            }
            return Map.of(
                    "status", "ERROR",
                    "isError", true,
                    "error", "MCP Sampling endpoint returned HTTP " + res.statusCode());
        } catch (Exception ex) {
            return Map.of(
                    "status", "ERROR",
                    "isError", true,
                    "error", "MCP Sampling service (:8081) unavailable: " + ex.getMessage());
        }
    }

    public List<String> completePromptArgument(String prefix) {
        String normalized = prefix == null ? "" : prefix.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return CURATED_CITIES.subList(0, 8);
        }
        List<String> matches = CURATED_CITIES.stream()
                .filter(c -> c.toLowerCase(Locale.ROOT).startsWith(normalized)
                        || c.toLowerCase(Locale.ROOT).contains(normalized))
                .limit(8)
                .toList();
        return matches.isEmpty() ? List.of(normalizeCity(prefix)) : matches;
    }

    public Map<String, Object> getPromptTemplate(String promptName, Map<String, Object> args) {
        String resolvedName = (promptName == null || promptName.isBlank()) ? "weekend-cultural-escape" : promptName.trim();
        String city = args != null && args.get("city") != null ? String.valueOf(args.get("city")).trim() : "";
        if (city.isEmpty()) {
            return Map.of(
                    "promptName", resolvedName,
                    "status", "ERROR",
                    "isError", true,
                    "error", "Required prompt argument 'city' must be supplied (no default assumed).");
        }
        try {
            McpGetPromptResult promptResult = getOrCreateMcpClient().getPrompt(resolvedName, Map.of("city", city));
            String expanded = promptResult != null && promptResult.messages() != null && !promptResult.messages().isEmpty()
                    ? String.valueOf(promptResult.messages().getFirst().content())
                    : "Curate a travel itinerary in " + city;
            return Map.of(
                    "promptName", resolvedName,
                    "status", "OK",
                    "arguments", Map.of("city", city),
                    "expandedPrompt", expanded);
        } catch (Exception ex) {
            return Map.of(
                    "promptName", resolvedName,
                    "status", "ERROR",
                    "isError", true,
                    "arguments", Map.of("city", city),
                    "error", "MCP Prompt service (:8081/mcp) unavailable: " + ex.getMessage());
        }
    }

    public Map<String, Object> callTool(String toolName, Map<String, Object> arguments) {
        if (toolName == null || !SUPPORTED_TOOLS.contains(toolName)) {
            throw new IllegalArgumentException("Unsupported MCP tool: " + toolName);
        }
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        try {
            String argsJson = objectMapper.writeValueAsString(args);
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name(toolName)
                    .arguments(argsJson)
                    .build();
            ToolExecutionResult execResult = getOrCreateMcpClient().executeTool(request);
            Map<String, Object> parsedResult = parseToolResultText(execResult.resultText());
            boolean isError = execResult.isError()
                    || Boolean.TRUE.equals(parsedResult.get("isError"))
                    || "ERROR".equalsIgnoreCase(String.valueOf(parsedResult.get("status")));

            if (!isError) {
                applySuccessfulToolSideEffects(toolName, parsedResult);
            }

            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("toolName", toolName);
            envelope.put("server", "nomad-mcp-concierge (:8081)");
            envelope.put("status", isError ? "ERROR" : "OK");
            envelope.put("isError", isError);
            envelope.put("arguments", args);
            envelope.put("result", parsedResult);
            if (isError) {
                envelope.put("error", parsedResult.getOrDefault("error", "MCP tool execution failed: " + toolName));
            }
            return envelope;
        } catch (Exception ex) {
            String errMsg = "MCP Server (:8081/mcp) unavailable while calling " + toolName + ": " + ex.getMessage();
            Map<String, Object> errPayload = Map.of(
                    "status", "ERROR",
                    "isError", true,
                    "serviceAvailable", false,
                    "upstream", "nomad-mcp-concierge (:8081/mcp)",
                    "error", errMsg);
            return Map.of(
                    "toolName", toolName,
                    "server", "nomad-mcp-concierge (:8081)",
                    "status", "ERROR",
                    "isError", true,
                    "error", errMsg,
                    "arguments", args,
                    "result", errPayload);
        }
    }

    private void applySuccessfulToolSideEffects(String toolName, Map<String, Object> result) {
        switch (toolName) {
            case "fetchCityWeather" -> {
                String city = stringVal(result.get("city"));
                String country = stringVal(result.get("country"));
                if (city != null) {
                    nomadTrip.put("activeCity", city);
                    nomadTrip.putIfAbsent("stopTitle", defaultSanctuaryForCity(city));
                }
                if (country != null) {
                    nomadTrip.put("country", country);
                    A2aGatewayClient.registerCityCountry(city, country);
                    nomadTrip.put("currency", A2aGatewayClient.resolveCurrencyForCountry(country));
                }
                if (result.get("temperatureC") != null) {
                    nomadTrip.put("temperatureC", result.get("temperatureC"));
                }
                if (result.get("condition") != null) {
                    nomadTrip.put("weatherCondition", result.get("condition"));
                }
            }
            case "discoverLocalCultureAndMedia" -> {
                String dest = stringVal(result.get("destination"));
                if (dest != null) {
                    nomadTrip.put("activeCity", dest);
                }
                if (result.get("audioTrack") != null) {
                    nomadTrip.put("soundtrack", result.get("audioTrack"));
                }
                if (result.get("heroImageUrl") != null) {
                    nomadTrip.put("heroImageUrl", result.get("heroImageUrl"));
                }
                if (result.get("culturalSummary") != null) {
                    nomadTrip.put("culturalSummary", result.get("culturalSummary"));
                }
            }
            case "updateTripItinerary" -> {
                if (result.get("activeCity") != null) {
                    nomadTrip.put("activeCity", result.get("activeCity"));
                }
                if (result.get("stopTitle") != null) {
                    nomadTrip.put("stopTitle", result.get("stopTitle"));
                }
                if (result.get("nights") != null) {
                    nomadTrip.put("nights", result.get("nights"));
                }
                if (result.get("allocatedUsd") != null) {
                    nomadTrip.put("allocatedUsd", result.get("allocatedUsd"));
                }
            }
            default -> {
            }
        }
    }

    private Map<String, Object> parseToolResultText(String text) {
        if (text == null || text.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode node = objectMapper.readTree(text);
            if (node.isObject()) {
                return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {});
            }
            return Map.of("message", text);
        } catch (Exception ignored) {
            return Map.of("status", "ERROR", "isError", true, "error", text);
        }
    }

    public List<Map<String, Object>> checkMcpHealth() {
        List<Map<String, Object>> entries = new ArrayList<>();
        long pingStart = System.nanoTime();
        boolean mcpPingOk = false;
        String pingErr = null;
        try {
            getOrCreateMcpClient().checkHealth();
            mcpPingOk = true;
        } catch (Exception ex) {
            pingErr = ex.getMessage();
        }
        long pingMs = Math.max(1L, (System.nanoTime() - pingStart) / 1_000_000L);
        Map<String, Object> mcpNode = new LinkedHashMap<>();
        mcpNode.put("id", "mcp-server");
        mcpNode.put("name", "Quarkus MCP Server (:8081/mcp)");
        mcpNode.put("category", "protocol");
        mcpNode.put("status", mcpPingOk ? "UP" : "DOWN");
        mcpNode.put("latencyMs", pingMs);
        if (!mcpPingOk && pingErr != null) {
            mcpNode.put("error", pingErr);
        }
        entries.add(mcpNode);

        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(mcpBaseUrl + "/api/mcp/health"))
                    .timeout(Duration.ofMillis(2500))
                    .GET()
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 200 && res.statusCode() < 300 && res.body() != null) {
                JsonNode root = objectMapper.readTree(res.body());
                JsonNode upstreams = root.path("upstreams");
                if (upstreams.isArray()) {
                    for (JsonNode u : upstreams) {
                        Map<String, Object> item = objectMapper.convertValue(u, new TypeReference<Map<String, Object>>() {});
                        item.put("category", "upstream-api");
                        item.put("via", "mcp-server (:8081)");
                        entries.add(item);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return entries;
    }

    private void syncRemotePreferences(Map<String, Object> updates) {
        try {
            String bodyJson = objectMapper.writeValueAsString(updates);
            HttpRequest req = HttpRequest.newBuilder(URI.create(mcpBaseUrl + "/api/mcp/resources/preferences"))
                    .timeout(Duration.ofMillis(1000))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();
            httpClient.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
        }
    }

    static String normalizeCity(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String trimmed = raw.trim();
        return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
    }

    static String defaultSanctuaryForCity(String city) {
        if (city == null || city.isBlank()) {
            return null;
        }
        return CITY_SANCTUARIES.getOrDefault(
                city.trim().toLowerCase(Locale.ROOT),
                city.trim() + " Architectural Sanctuary");
    }

    private static String stringVal(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }
}
