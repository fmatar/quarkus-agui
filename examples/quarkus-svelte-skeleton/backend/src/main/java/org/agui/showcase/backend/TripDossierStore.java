package org.agui.showcase.backend;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpGetPromptResult;
import dev.langchain4j.mcp.client.McpPromptMessage;
import dev.langchain4j.mcp.client.McpReadResourceResult;
import dev.langchain4j.mcp.client.McpTextContent;
import dev.langchain4j.mcp.client.McpTextResourceContents;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reactive Trip Dossier state store and Quarkus-managed {@link McpClient} facade (`@McpClientName("nomad")`).
 * Synchronizes live MCP/A2A tool results into AG-UI {@code STATE_DELTA} payloads without fabricating defaults.
 */
@ApplicationScoped
public class TripDossierStore {

    public static final double HITL_THRESHOLD_USD = 1500.0;

    public record PendingHitlReservation(
            String callId,
            String threadId,
            String city,
            String stopTitle,
            int nights,
            double costUsd
    ) {}

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private static final List<String> COMPLETION_CITIES = List.of(
            "Beirut", "Kyoto", "Lisbon", "Reykjavik", "Paris", "Oaxaca", "Tokyo", "Zurich",
            "Barcelona", "Rome", "London", "Marrakech", "Istanbul", "Seoul", "Buenos Aires");

    private final McpClient nomadMcpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Object stateLock = new Object();

    private final Map<String, Object> nomadTrip = new LinkedHashMap<>();
    private final Map<String, Object> preferences = new LinkedHashMap<>();
    private final Map<String, PendingHitlReservation> pendingApprovals = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> turnInteractions = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> turnMedia = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> turnSources = new ConcurrentHashMap<>();

    @Inject
    public TripDossierStore(@McpClientName("nomad") McpClient nomadMcpClient) {
        this.nomadMcpClient = nomadMcpClient;
        resetDossierMapsLocked();
    }

    public void beginTurn(String threadId) {
        String key = normalizeThreadId(threadId);
        turnInteractions.remove(key);
        turnMedia.remove(key);
        turnSources.remove(key);
    }

    public Map<String, Object> readTripDossierState() {
        synchronized (stateLock) {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("uri", "itinerary://active-trip");
            snapshot.put("nomadTrip", new LinkedHashMap<>(nomadTrip));
            snapshot.put("preferences", new LinkedHashMap<>(preferences));
            return snapshot;
        }
    }

    public Map<String, Object> updateTripParameters(Integer nights, String homeCurrency) {
        synchronized (stateLock) {
            if (nights != null) {
                int clamped = Math.max(1, Math.min(30, nights));
                nomadTrip.put("nights", clamped);
            }
            if (homeCurrency != null && !homeCurrency.isBlank()) {
                String code = homeCurrency.trim().toUpperCase(Locale.ROOT);
                nomadTrip.put("homeCurrency", code);
                preferences.put("homeCurrency", code);
            }
            return readTripDossierState();
        }
    }

    public Map<String, Object> resetAll(String threadId) {
        String key = normalizeThreadId(threadId);
        pendingApprovals.remove(key);
        beginTurn(key);
        synchronized (stateLock) {
            resetDossierMapsLocked();
        }
        try {
            ToolExecutionRequest resetReq = ToolExecutionRequest.builder()
                    .id("mcp-reset-" + UUID.randomUUID().toString().substring(0, 8))
                    .name("resetNomadState")
                    .arguments("{}")
                    .build();
            nomadMcpClient.executeTool(resetReq);
        } catch (Exception ignored) {
            // Remote MCP server may be unreachable during local unit resets
        }
        return readTripDossierState();
    }

    public List<Map<String, Object>> listMcpTools() {
        try {
            List<ToolSpecification> specs = nomadMcpClient.listTools();
            if (specs == null) {
                return List.of();
            }
            List<Map<String, Object>> tools = new ArrayList<>(specs.size());
            for (ToolSpecification spec : specs) {
                tools.add(Map.of(
                        "name", spec.name(),
                        "description", spec.description() == null ? "" : spec.description(),
                        "server", "nomad-mcp-concierge (:8081)"));
            }
            return tools;
        } catch (Exception ex) {
            return List.of();
        }
    }

    public Map<String, Object> readMcpResource(String uri) {
        try {
            McpReadResourceResult result = nomadMcpClient.readResource(uri);
            if (result == null || result.contents() == null || result.contents().isEmpty()) {
                return Map.of("status", "ERROR", "isError", true, "uri", String.valueOf(uri), "error", "Empty MCP resource");
            }
            if (result.contents().getFirst() instanceof McpTextResourceContents textContent) {
                Map<String, Object> parsed = new LinkedHashMap<>(parseJsonOrEmpty(textContent.text()));
                parsed.putIfAbsent("uri", uri);
                parsed.put("status", "OK");
                parsed.put("isError", false);
                return parsed;
            }
            return Map.of("status", "ERROR", "isError", true, "uri", String.valueOf(uri), "error", "Unsupported MCP resource content");
        } catch (Exception ex) {
            return Map.of(
                    "status", "ERROR",
                    "isError", true,
                    "uri", String.valueOf(uri),
                    "error", ex.getMessage() == null ? "MCP readResource failed" : ex.getMessage());
        }
    }

    public Map<String, Object> getMcpPrompt(String promptName, String city) {
        if (city == null || city.isBlank()) {
            return Map.of("status", "ERROR", "isError", true, "error", "Missing required city parameter");
        }
        try {
            McpGetPromptResult result = nomadMcpClient.getPrompt(promptName, Map.of("city", city.trim()));
            String expanded = "";
            if (result != null && result.messages() != null && !result.messages().isEmpty()) {
                McpPromptMessage first = result.messages().getFirst();
                if (first.content() instanceof McpTextContent text) {
                    expanded = text.text();
                }
            }
            return Map.of(
                    "status", "OK",
                    "isError", false,
                    "promptName", promptName,
                    "city", city.trim(),
                    "description", result != null && result.description() != null ? result.description() : "",
                    "expandedPrompt", expanded == null ? "" : expanded);
        } catch (Exception ex) {
            return Map.of(
                    "status", "ERROR",
                    "isError", true,
                    "error", ex.getMessage() == null ? "MCP getPrompt failed" : ex.getMessage());
        }
    }

    public List<String> completeCityPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return COMPLETION_CITIES.subList(0, 8);
        }
        String norm = prefix.trim().toLowerCase(Locale.ROOT);
        return COMPLETION_CITIES.stream()
                .filter(city -> city.toLowerCase(Locale.ROOT).startsWith(norm))
                .limit(8)
                .toList();
    }

    public void recordInteraction(String threadId, Map<String, Object> interaction) {
        if (interaction != null) {
            turnInteractions.put(normalizeThreadId(threadId), interaction);
        }
    }

    public PendingHitlReservation stagePendingApproval(
            String threadId,
            String callId,
            String city,
            String stopTitle,
            int nights,
            double costUsd) {
        String key = normalizeThreadId(threadId);
        PendingHitlReservation staged = new PendingHitlReservation(callId, key, city, stopTitle, nights, costUsd);
        pendingApprovals.put(key, staged);
        return staged;
    }

    public PendingHitlReservation peekPendingApproval(String threadId) {
        return pendingApprovals.get(normalizeThreadId(threadId));
    }

    public PendingHitlReservation consumePendingApproval(String threadId) {
        return pendingApprovals.remove(normalizeThreadId(threadId));
    }

    public Map<String, Object> executeApprovedItineraryUpdate(
            String city,
            String stopTitle,
            int nights,
            double costUsd) {
        try {
            String argsJson = objectMapper.writeValueAsString(Map.of(
                    "city", city,
                    "stopTitle", stopTitle,
                    "nights", nights,
                    "costUsd", costUsd));
            ToolExecutionRequest req = ToolExecutionRequest.builder()
                    .id("mcp-itin-" + UUID.randomUUID().toString().substring(0, 8))
                    .name("updateTripItinerary")
                    .arguments(argsJson)
                    .build();
            ToolExecutionResult execResult = nomadMcpClient.executeTool(req);
            Map<String, Object> parsed = new LinkedHashMap<>(parseJsonOrEmpty(execResult.resultText()));
            boolean isError = execResult.isError()
                    || Boolean.TRUE.equals(parsed.get("isError"))
                    || "ERROR".equalsIgnoreCase(String.valueOf(parsed.get("status")));
            if (isError) {
                parsed.put("status", "ERROR");
                parsed.put("isError", true);
                parsed.putIfAbsent("error", "MCP updateTripItinerary failed");
                return parsed;
            }
            synchronized (stateLock) {
                nomadTrip.put("activeCity", parsed.getOrDefault("activeCity", city));
                nomadTrip.put("stopTitle", parsed.getOrDefault("stopTitle", stopTitle));
                nomadTrip.put("nights", parsed.getOrDefault("nights", nights));
                nomadTrip.put("allocatedUsd", parsed.getOrDefault("allocatedUsd", costUsd));
            }
            parsed.put("status", "OK");
            parsed.put("isError", false);
            return parsed;
        } catch (Exception ex) {
            return Map.of(
                    "status", "ERROR",
                    "isError", true,
                    "error", ex.getMessage() == null ? "MCP updateTripItinerary failed" : ex.getMessage());
        }
    }

    public Map<String, Object> onToolExecuted(String threadId, ToolExecution exec) {
        String key = normalizeThreadId(threadId);
        String toolName = exec.request().name() == null ? "" : exec.request().name();
        Map<String, Object> args = parseJsonOrEmpty(exec.request().arguments());
        Map<String, Object> result = parseJsonOrEmpty(exec.result());
        boolean isError = exec.hasFailed()
                || Boolean.TRUE.equals(result.get("isError"))
                || "ERROR".equalsIgnoreCase(String.valueOf(result.get("status")));

        switch (toolName) {
            case "saveTravelerPreferences" -> applyPreferencesResult(args, result, isError);
            case "fetchCityWeather" -> applyWeatherResult(key, args, result, isError);
            case "discoverLocalCultureAndMedia" -> applyCultureResult(key, args, result, isError);
            case "sampleAcousticMood" -> applyAcousticMoodResult(result, isError);
            case "updateTripItinerary", "requestSanctuaryReservation" -> applyReservationResult(result, isError);
            case "resetNomadState" -> {
                synchronized (stateLock) {
                    resetDossierMapsLocked();
                }
            }
            case "verifyTravelAdvisory" -> applyAdvisoryResult(key, args, result, isError);
            case "convertAndAuditBudget" -> applyBudgetResult(key, args, result, isError);
            case "curateNeighborhoodCadence" -> {
                if (!isError && args.get("city") instanceof String city && !city.isBlank()) {
                    synchronized (stateLock) {
                        nomadTrip.putIfAbsent("activeCity", city.trim());
                    }
                }
            }
            default -> {
                // Other UI or custom tools require no dossier mutation
            }
        }

        Map<String, Object> dossier = readTripDossierState();
        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("tripDossierState", dossier);
        delta.put("clusterState", dossier);

        List<Map<String, Object>> media = turnMedia.get(key);
        if (media != null && !media.isEmpty()) {
            delta.put("media", List.copyOf(media));
        }
        List<Map<String, Object>> sources = turnSources.get(key);
        if (sources != null && !sources.isEmpty()) {
            delta.put("sources", List.copyOf(sources));
        }
        Map<String, Object> interaction = turnInteractions.get(key);
        if (interaction != null) {
            delta.put("interaction", interaction);
        }
        PendingHitlReservation pending = pendingApprovals.get(key);
        if (pending != null) {
            delta.put("pendingApproval", Map.of(
                    "callId", pending.callId(),
                    "toolName", "updateTripItinerary",
                    "city", pending.city(),
                    "stopTitle", pending.stopTitle(),
                    "nights", pending.nights(),
                    "costUsd", pending.costUsd()));
        }
        return delta;
    }

    private void applyPreferencesResult(Map<String, Object> args, Map<String, Object> result, boolean isError) {
        if (isError) {
            return;
        }
        synchronized (stateLock) {
            for (String field : List.of("travelerName", "style", "dietary", "musicTaste", "pace", "homeCurrency")) {
                Object val = result.get(field) != null ? result.get(field) : args.get(field);
                if (val instanceof String s && !s.isBlank()) {
                    preferences.put(field, s.trim());
                    if ("homeCurrency".equals(field)) {
                        nomadTrip.put("homeCurrency", s.trim().toUpperCase(Locale.ROOT));
                    }
                }
            }
        }
    }

    private void applyWeatherResult(String threadKey, Map<String, Object> args, Map<String, Object> result, boolean isError) {
        String city = stringOrDefault(result.get("city"), stringOrDefault(args.get("city"), "Destination"));
        appendSource(threadKey, Map.of(
                "title", "Open-Meteo Forecast — " + city,
                "url", "https://api.open-meteo.com/v1/forecast",
                "status", isError ? "error" : "verified",
                "error", isError ? stringOrDefault(result.get("error"), "Weather API unavailable") : ""));
        if (isError) {
            return;
        }
        String country = stringOrDefault(result.get("country"), null);
        if (country != null) {
            A2aGatewayClient.registerCityCountry(city, country);
        }
        synchronized (stateLock) {
            nomadTrip.put("activeCity", city);
            if (country != null) {
                nomadTrip.put("country", country);
            }
            if (result.get("temperatureC") instanceof Number temp) {
                nomadTrip.put("temperatureC", temp.doubleValue());
            }
            if (result.get("condition") instanceof String cond && !cond.isBlank()) {
                nomadTrip.put("weatherCondition", cond);
            }
        }
    }

    private void applyCultureResult(String threadKey, Map<String, Object> args, Map<String, Object> result, boolean isError) {
        String city = stringOrDefault(result.get("destination"), stringOrDefault(args.get("destination"), "Destination"));
        appendSource(threadKey, Map.of(
                "title", "Wikipedia Cultural Summary — " + city,
                "url", "https://en.wikipedia.org/wiki/" + URLEncoder.encode(city.replace(' ', '_'), StandardCharsets.UTF_8),
                "status", isError ? "error" : "verified",
                "error", isError ? stringOrDefault(result.get("error"), "Culture API unavailable") : ""));
        if (isError) {
            return;
        }
        List<Map<String, Object>> mediaItems = new ArrayList<>();
        if (result.get("heroImageUrl") instanceof String imgUrl && !imgUrl.isBlank()) {
            mediaItems.add(Map.of(
                    "type", "image",
                    "title", city + " Cultural & Architectural Plate",
                    "subtitle", "Wikipedia REST API v1 • Verified Landmark Photography",
                    "url", imgUrl));
        }
        String trackTitle = null;
        if (result.get("audioTrack") instanceof Map<?, ?> track
                && track.get("previewUrl") instanceof String previewUrl
                && !previewUrl.isBlank()) {
            trackTitle = track.get("trackName") != null ? String.valueOf(track.get("trackName")) : city + " Acoustic Session";
            String artistName = track.get("artistName") != null ? String.valueOf(track.get("artistName")) : "Regional Artist";
            mediaItems.add(Map.of(
                    "type", "audio",
                    "title", trackTitle,
                    "subtitle", artistName,
                    "url", previewUrl,
                    "durationSeconds", 30));
        }
        if (!mediaItems.isEmpty()) {
            turnMedia.put(threadKey, mediaItems);
        }
        synchronized (stateLock) {
            nomadTrip.putIfAbsent("activeCity", city);
            if (trackTitle != null) {
                nomadTrip.put("soundtrack", trackTitle);
            }
        }
    }

    private void applyAcousticMoodResult(Map<String, Object> result, boolean isError) {
        if (!isError && result.get("sampledMood") instanceof String mood && !mood.isBlank()) {
            synchronized (stateLock) {
                nomadTrip.put("soundtrack", mood);
            }
        }
    }

    private void applyReservationResult(Map<String, Object> result, boolean isError) {
        if (isError || "AWAITING_APPROVAL".equalsIgnoreCase(String.valueOf(result.get("status")))) {
            return;
        }
        synchronized (stateLock) {
            if (result.get("activeCity") instanceof String c && !c.isBlank()) {
                nomadTrip.put("activeCity", c);
            }
            if (result.get("stopTitle") instanceof String s && !s.isBlank()) {
                nomadTrip.put("stopTitle", s);
            }
            if (result.get("nights") instanceof Number n) {
                nomadTrip.put("nights", n.intValue());
            }
            if (result.get("allocatedUsd") instanceof Number cost) {
                nomadTrip.put("allocatedUsd", cost.doubleValue());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void applyAdvisoryResult(String threadKey, Map<String, Object> args, Map<String, Object> result, boolean isError) {
        Map<String, Object> report = result.get("report") instanceof Map<?, ?> r ? (Map<String, Object>) r : result;
        String city = stringOrDefault(report.get("city"), stringOrDefault(args.get("city"), "Destination"));
        String country = stringOrDefault(report.get("country"), A2aGatewayClient.resolveCountryForCity(city));
        String errorMsg = stringOrDefault(result.get("error"), stringOrDefault(report.get("error"), "Travel Advisory API unavailable"));
        appendSource(threadKey, Map.of(
                "title", "REST Countries v5 Travel Advisory — " + country,
                "url", "https://api.restcountries.com/countries/v5/names.common/"
                        + URLEncoder.encode(country.isBlank() ? city : country, StandardCharsets.UTF_8),
                "status", isError ? "error" : "verified",
                "error", isError ? errorMsg : ""));
        if (isError) {
            return;
        }
        synchronized (stateLock) {
            if (!city.isBlank()) {
                nomadTrip.put("activeCity", city);
            }
            if (!country.isBlank()) {
                nomadTrip.put("country", country);
            }
            String currency = stringOrDefault(report.get("currency"), stringOrDefault(report.get("currencyCode"), null));
            if (currency != null) {
                nomadTrip.put("currency", currency);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void applyBudgetResult(String threadKey, Map<String, Object> args, Map<String, Object> result, boolean isError) {
        Map<String, Object> report = result.get("report") instanceof Map<?, ?> r ? (Map<String, Object>) r : result;
        appendSource(threadKey, Map.of(
                "title", "Frankfurter ECB FX Reference",
                "url", "https://api.frankfurter.dev/v1/latest",
                "status", isError ? "error" : "verified",
                "error", isError ? stringOrDefault(result.get("error"), "FX API unavailable") : ""));
        if (isError) {
            return;
        }
        synchronized (stateLock) {
            String city = stringOrDefault(report.get("city"), stringOrDefault(args.get("city"), null));
            if (city != null) {
                nomadTrip.put("activeCity", city);
            }
            if (report.get("targetCurrency") instanceof String tc && !tc.isBlank()) {
                nomadTrip.put("currency", tc);
            }
            if (report.get("baseCurrency") instanceof String bc && !bc.isBlank()) {
                nomadTrip.put("homeCurrency", bc);
            }
            if (report.get("exchangeRate") instanceof Number rate) {
                nomadTrip.put("exchangeRate", rate.doubleValue());
            }
            if (args.get("budgetAmount") instanceof Number budget) {
                nomadTrip.put("budgetUsd", budget.doubleValue());
            }
        }
    }

    private void appendSource(String threadKey, Map<String, Object> source) {
        turnSources.compute(threadKey, (k, existing) -> {
            List<Map<String, Object>> next = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
            next.add(source);
            return next;
        });
    }

    private void resetDossierMapsLocked() {
        nomadTrip.clear();
        nomadTrip.put("uri", "itinerary://active-trip");

        preferences.clear();
        preferences.put("uri", "traveler://preferences");
    }

    private Map<String, Object> parseJsonOrEmpty(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        String trimmed = raw.trim();
        if (!trimmed.startsWith("{")) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(trimmed, MAP_TYPE);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static String stringOrDefault(Object candidate, String fallback) {
        return candidate instanceof String s && !s.isBlank() ? s.trim() : fallback;
    }

    private static String normalizeThreadId(String threadId) {
        return (threadId == null || threadId.isBlank()) ? "thread-nomad-1" : threadId.trim();
    }
}
