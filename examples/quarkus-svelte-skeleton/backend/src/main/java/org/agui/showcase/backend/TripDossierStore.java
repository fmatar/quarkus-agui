package org.agui.showcase.backend;

import com.agui.community.core.event.ActivitySnapshotEvent;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reactive Trip Dossier state store and Quarkus-managed {@link McpClient} facade (`@McpClientName("nomad")`).
 * Synchronizes live MCP/A2A tool results into AG-UI {@code STATE_DELTA} and canonical {@code ACTIVITY_SNAPSHOT}
 * payloads without fabricating defaults.
 */
@ApplicationScoped
public class TripDossierStore {

    public static final double HITL_THRESHOLD_USD = 1500.0;
    public static final String ACT_WEATHER = "agui:weather";
    public static final String ACT_MAP_PLACES = "agui:map-places";
    public static final String ACT_SECURITY_ADVISORY = "agui:security-advisory";
    public static final String ACT_FX_BUDGET = "agui:fx-budget";
    public static final String ACT_CULTURE_AUDIO = "agui:culture-audio";

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
            "Barcelona", "Rome", "London", "Marrakech", "Istanbul", "Seoul", "Buenos Aires",
            "Cairo", "Athens", "Vienna", "Berlin", "Copenhagen", "Amman", "Rio de Janeiro", "New York");

    private static final Pattern NIGHTS_PATTERN = Pattern.compile(
            "(?i)\\b(\\d{1,2})\\s*[- ]?\\s*(?:night|nights|day|days)\\b");
    private static final Pattern SYMBOL_BUDGET_PATTERN = Pattern.compile(
            "(?i)(?<![\\d])(\\$|€|£)[ \\t]*([\\d,]+(?:\\.\\d+)?)[ \\t]*(USD|EUR|GBP|CHF|euros?|dollars?|pounds?|sterling|francs?)?\\b");
    private static final Pattern TRAILING_SYMBOL_BUDGET_PATTERN = Pattern.compile(
            "(?i)\\b([\\d,]+(?:\\.\\d+)?)[ \\t]*(\\$|€|£)(?:[ \\t]*(USD|EUR|GBP|CHF|euros?|dollars?|pounds?|sterling|francs?)\\b)?");
    private static final Pattern CODE_BUDGET_PATTERN = Pattern.compile(
            "(?i)\\b([\\d,]+(?:\\.\\d+)?)[ \\t]*(USD|EUR|GBP|CHF|JPY|CAD|AUD|ISK|MXN|LBP|MAD|euros?|dollars?|pounds?|sterling|francs?|yen)\\b");
    private static final Pattern MEDIA_OR_DETAILS_PATTERN = Pattern.compile(
            "(?i)\\b(music|song|audio|listen|soundtrack|fado|koto|oud|culture|cultural|media|photo|picture|image|landmark|details)\\b");
    private static final Pattern RESERVATION_INTENT_PATTERN = Pattern.compile(
            "(?i)\\b(reserve|book|hold)\\b");

    record ParsedBudget(double amount, String currency) {}

    private final McpClient nomadMcpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Object stateLock = new Object();

    private final Map<String, Object> nomadTrip = new LinkedHashMap<>();
    private final Map<String, Object> preferences = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> reportSections = new LinkedHashMap<>();
    private final Map<String, PendingHitlReservation> pendingApprovals = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> turnInteractions = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> turnMedia = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> turnSources = new ConcurrentHashMap<>();
    private final Map<String, Map<String, ActivitySnapshotEvent>> pendingTurnActivities = new ConcurrentHashMap<>();
    private final Map<String, String> turnPrompts = new ConcurrentHashMap<>();
    private final Map<String, String> lastRequestedCities = new ConcurrentHashMap<>();

    @Inject
    public TripDossierStore(@McpClientName("nomad") McpClient nomadMcpClient) {
        this.nomadMcpClient = nomadMcpClient;
        resetDossierMapsLocked();
    }

    public void beginTurn(String threadId) {
        beginTurn(threadId, "");
    }

    public void beginTurn(String threadId, String prompt) {
        String key = normalizeThreadId(threadId);
        turnInteractions.remove(key);
        turnMedia.remove(key);
        turnSources.remove(key);
        pendingTurnActivities.remove(key);
        turnPrompts.put(key, prompt == null ? "" : prompt.trim());
    }

    public List<ActivitySnapshotEvent> drainPendingActivities(String threadId) {
        String key = normalizeThreadId(threadId);
        Map<String, ActivitySnapshotEvent> queued = pendingTurnActivities.remove(key);
        if (queued == null || queued.isEmpty()) {
            return List.of();
        }
        synchronized (queued) {
            return List.copyOf(queued.values());
        }
    }

    public Map<String, Object> readTripDossierState() {
        synchronized (stateLock) {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("uri", "itinerary://active-trip");
            snapshot.put("nomadTrip", new LinkedHashMap<>(nomadTrip));
            snapshot.put("preferences", new LinkedHashMap<>(preferences));
            snapshot.put("reportSections", copyReportSectionsLocked());
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
            if (nomadTrip.get("activeCity") instanceof String activeCity && !activeCity.isBlank()
                    && (reportSections.containsKey("fxBudget") || nomadTrip.get("budgetUsd") != null)) {
                reportSections.put("fxBudget", buildFxBudgetSectionLocked(activeCity, false, null));
            }
            return readTripDossierState();
        }
    }

    public Map<String, Object> resetAll(String threadId) {
        String key = normalizeThreadId(threadId);
        pendingApprovals.remove(key);
        lastRequestedCities.remove(key);
        turnPrompts.remove(key);
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
            String resolvedCity = normalizeCityName(stringOrDefault(parsed.get("activeCity"), city));
            synchronized (stateLock) {
                switchActiveCityLocked(resolvedCity);
                nomadTrip.put("stopTitle", parsed.getOrDefault("stopTitle", stopTitle));
                nomadTrip.put("nights", parsed.getOrDefault("nights", nights));
                nomadTrip.put("allocatedUsd", parsed.getOrDefault("allocatedUsd", costUsd));
                reportSections.put("fxBudget", buildFxBudgetSectionLocked(resolvedCity, false, null));
                reportSections.put("mapPlaces", buildMapPlacesSectionLocked(resolvedCity));
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

    @SuppressWarnings("unchecked")
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
            case "sampleAcousticMood" -> applyAcousticMoodResult(key, args, result, isError, false);
            case "updateTripItinerary", "requestSanctuaryReservation" -> {
                applyReservationResult(key, args, result, isError);
                String reservedCity = stringOrDefault(result.get("activeCity"), stringOrDefault(args.get("city"), null));
                if (!isError && !"AWAITING_APPROVAL".equalsIgnoreCase(String.valueOf(result.get("status")))) {
                    ensureTurnCultureMedia(key, reservedCity, "");
                }
            }
            case "resetNomadState" -> {
                synchronized (stateLock) {
                    resetDossierMapsLocked();
                }
            }
            case "verifyTravelAdvisory" -> applyAdvisoryResult(key, args, result, isError);
            case "convertAndAuditBudget" -> {
                applyBudgetResult(key, args, result, isError);
                if (!isError) {
                    Map<String, Object> report = result.get("report") instanceof Map<?, ?> r
                            ? (Map<String, Object>) r
                            : result;
                    String fxCity = stringOrDefault(report.get("city"), stringOrDefault(args.get("city"), null));
                    ensureTurnCultureMedia(key, fxCity, "");
                }
            }
            case "curateNeighborhoodCadence" -> {
                applyCadenceResult(key, args, result, isError);
                if (!isError && args.get("city") instanceof String city && !city.isBlank()) {
                    ensureTurnCultureMedia(key, normalizeCityName(city), stringOrDefault(args.get("focus"), ""));
                }
            }
            default -> {
                // Other UI or custom tools require no dossier mutation
            }
        }

        return buildCurrentStateDelta(key);
    }

    public Map<String, Object> finalizeTurnDossier(String threadId, A2aGatewayClient a2aClient) {
        String key = normalizeThreadId(threadId);
        if (pendingApprovals.containsKey(key)) {
            return Map.of();
        }
        String prompt = turnPrompts.getOrDefault(key, "");
        if (prompt.isBlank()) {
            return Map.of();
        }

        String explicitCity = extractCityFromPrompt(prompt);
        Integer explicitNights = extractNightsFromPrompt(prompt);
        ParsedBudget explicitBudget = extractBudgetFromPrompt(prompt);
        boolean reservationIntent = RESERVATION_INTENT_PATTERN.matcher(prompt).find();
        boolean mediaOrDetailsIntent = MEDIA_OR_DETAILS_PATTERN.matcher(prompt).find();

        if (explicitCity != null && explicitNights == null && explicitBudget == null && !reservationIntent) {
            lastRequestedCities.put(key, explicitCity);
            if (mediaOrDetailsIntent) {
                List<Map<String, Object>> existingMedia = turnMedia.get(key);
                if (existingMedia == null || existingMedia.isEmpty()) {
                    ensureTurnCultureMedia(key, explicitCity, "");
                }
                List<Map<String, Object>> updatedMedia = turnMedia.get(key);
                if (updatedMedia != null && !updatedMedia.isEmpty()) {
                    return buildCurrentStateDelta(key);
                }
            }
            return Map.of();
        }

        if (!reservationIntent && explicitNights != null && explicitBudget != null) {
            String targetCity = explicitCity != null ? explicitCity : lastRequestedCities.get(key);
            if (targetCity == null) {
                synchronized (stateLock) {
                    targetCity = stringOrDefault(nomadTrip.get("activeCity"), null);
                }
            }
            if (targetCity != null) {
                String resolvedCity = normalizeCityName(targetCity);
                lastRequestedCities.put(key, resolvedCity);
                String country = A2aGatewayClient.resolveCountryForCity(resolvedCity);
                String localCurrency = A2aGatewayClient.resolveCurrencyForCountry(country);
                double allocated = Math.round(explicitBudget.amount() * 0.65 * 100.0) / 100.0;

                boolean needsWeather;
                boolean needsFx;
                boolean needsCadence;
                boolean needsAdvisory;
                boolean needsAcoustic;
                synchronized (stateLock) {
                    switchActiveCityLocked(resolvedCity);
                    if (!country.isBlank()) {
                        nomadTrip.putIfAbsent("country", country);
                    }
                    if (!localCurrency.isBlank()) {
                        nomadTrip.putIfAbsent("currency", localCurrency);
                    }
                    nomadTrip.put("nights", explicitNights);
                    nomadTrip.put("budgetUsd", explicitBudget.amount());
                    nomadTrip.put("homeCurrency", explicitBudget.currency());
                    preferences.put("homeCurrency", explicitBudget.currency());
                    nomadTrip.putIfAbsent("allocatedUsd", allocated);
                    nomadTrip.putIfAbsent("stopTitle", resolvedCity + " Cultural & Architectural Itinerary");
                    needsWeather = !hasSectionForCityLocked("weather", resolvedCity) || nomadTrip.get("temperatureC") == null;
                    needsFx = (!hasSectionForCityLocked("fxBudget", resolvedCity) || nomadTrip.get("exchangeRate") == null) && a2aClient != null;
                    needsCadence = !(nomadTrip.get("places") instanceof List<?> p && !p.isEmpty()) && a2aClient != null;
                    needsAdvisory = (!hasSectionForCityLocked("securityAdvisory", resolvedCity)
                            || Boolean.TRUE.equals(reportSections.get("securityAdvisory").get("isError"))) && a2aClient != null;
                    needsAcoustic = nomadTrip.get("acousticMood") == null;
                }

                List<Runnable> tasks = new ArrayList<>();
                tasks.add(() -> ensureTurnCultureMedia(key, resolvedCity, ""));
                if (needsWeather) {
                    tasks.add(() -> ensureTurnWeather(key, resolvedCity));
                }
                if (needsAcoustic) {
                    tasks.add(() -> ensureTurnAcousticMood(key, resolvedCity));
                }
                if (needsCadence) {
                    tasks.add(() -> ensureTurnNeighborhoodPlaces(key, resolvedCity, a2aClient));
                }
                if (needsAdvisory) {
                    tasks.add(() -> ensureTurnAdvisory(key, resolvedCity, a2aClient));
                }
                if (needsFx) {
                    tasks.add(() -> ensureTurnBudget(key, resolvedCity, explicitBudget.amount(), explicitBudget.currency(), a2aClient));
                }
                try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                    List<CompletableFuture<Void>> futures = tasks.stream()
                            .map(task -> CompletableFuture.runAsync(task, executor)
                                    .orTimeout(4, TimeUnit.SECONDS)
                                    .exceptionally(ex -> null))
                            .toList();
                    CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
                }
                ensureCompleteSectionsLocked(key, resolvedCity);
                return buildCurrentStateDelta(key);
            }
        }
        return Map.of();
    }

    private boolean hasSectionForCityLocked(String sectionKey, String expectedCity) {
        Map<String, Object> sec = reportSections.get(sectionKey);
        if (sec == null || sec.isEmpty()) {
            return false;
        }
        String secCity = stringOrDefault(sec.get("city"), "");
        return expectedCity != null && secCity.equalsIgnoreCase(expectedCity);
    }

    private void ensureCompleteSectionsLocked(String threadKey, String resolvedCity) {
        Map<String, Object> weatherSection = null;
        Map<String, Object> securitySection = null;
        Map<String, Object> mapSection;
        Map<String, Object> fxSection;
        Map<String, Object> cultureSection;
        synchronized (stateLock) {
            if (hasSectionForCityLocked("weather", resolvedCity)) {
                weatherSection = new LinkedHashMap<>(reportSections.get("weather"));
            }
            if (hasSectionForCityLocked("securityAdvisory", resolvedCity)) {
                securitySection = new LinkedHashMap<>(reportSections.get("securityAdvisory"));
            }
            mapSection = buildMapPlacesSectionLocked(resolvedCity);
            reportSections.put("mapPlaces", mapSection);
            fxSection = buildFxBudgetSectionLocked(resolvedCity, false, null);
            reportSections.put("fxBudget", fxSection);
            cultureSection = buildCultureAudioSectionLocked(resolvedCity, false, null);
            reportSections.put("cultureAudio", cultureSection);
        }
        if (weatherSection != null) {
            queueActivity(threadKey, ACT_WEATHER, resolvedCity, weatherSection);
        }
        queueActivity(threadKey, ACT_MAP_PLACES, resolvedCity, mapSection);
        if (securitySection != null) {
            queueActivity(threadKey, ACT_SECURITY_ADVISORY, resolvedCity, securitySection);
        }
        queueActivity(threadKey, ACT_FX_BUDGET, resolvedCity, fxSection);
        queueActivity(threadKey, ACT_CULTURE_AUDIO, resolvedCity, cultureSection);
    }

    private Map<String, Object> buildCurrentStateDelta(String key) {
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
                    String trimmed = s.trim();
                    if ("travelerName".equals(field) && isKnownCityName(trimmed)) {
                        switchActiveCityLocked(normalizeCityName(trimmed));
                        continue;
                    }
                    String normalized = "homeCurrency".equals(field) ? trimmed.toUpperCase(Locale.ROOT) : trimmed;
                    preferences.put(field, normalized);
                    if ("homeCurrency".equals(field)) {
                        nomadTrip.put("homeCurrency", normalized);
                    }
                }
            }
        }
    }

    private void applyWeatherResult(String threadKey, Map<String, Object> args, Map<String, Object> result, boolean isError) {
        String city = normalizeCityName(stringOrDefault(result.get("city"), stringOrDefault(args.get("city"), "Destination")));
        String errText = stringOrDefault(result.get("error"), "Weather API unavailable");
        appendSource(threadKey, Map.of(
                "title", "Open-Meteo Forecast — " + city,
                "url", "https://api.open-meteo.com/v1/forecast",
                "status", isError ? "error" : "verified",
                "error", isError ? errText : ""));
        if (isError) {
            Map<String, Object> errSection = new LinkedHashMap<>();
            errSection.put("status", "ERROR");
            errSection.put("isError", true);
            errSection.put("city", city);
            errSection.put("error", errText);
            errSection.put("source", "api.open-meteo.com");
            synchronized (stateLock) {
                reportSections.put("weather", errSection);
            }
            queueActivity(threadKey, ACT_WEATHER, city, errSection);
            return;
        }
        String country = stringOrDefault(result.get("country"), A2aGatewayClient.resolveCountryForCity(city));
        if (!country.isBlank()) {
            A2aGatewayClient.registerCityCountry(city, country);
        }
        Map<String, Object> weatherSection;
        Map<String, Object> mapSection;
        synchronized (stateLock) {
            switchActiveCityLocked(city);
            if (!country.isBlank()) {
                nomadTrip.put("country", country);
            }
            if (result.get("temperatureC") instanceof Number temp) {
                nomadTrip.put("temperatureC", temp.doubleValue());
            }
            if (result.get("windSpeedKph") instanceof Number wind) {
                nomadTrip.put("windSpeedKph", wind.doubleValue());
            }
            if (result.get("latitude") instanceof Number lat) {
                nomadTrip.put("latitude", lat.doubleValue());
            }
            if (result.get("longitude") instanceof Number lon) {
                nomadTrip.put("longitude", lon.doubleValue());
            }
            if (result.get("condition") instanceof String cond && !cond.isBlank()) {
                nomadTrip.put("weatherCondition", cond);
            }
            weatherSection = new LinkedHashMap<>();
            weatherSection.put("status", "OK");
            weatherSection.put("isError", false);
            weatherSection.put("city", city);
            weatherSection.put("country", country);
            weatherSection.put("temperatureC", nomadTrip.get("temperatureC"));
            weatherSection.put("windSpeedKph", nomadTrip.get("windSpeedKph"));
            weatherSection.put("condition", nomadTrip.get("weatherCondition"));
            weatherSection.put("latitude", nomadTrip.get("latitude"));
            weatherSection.put("longitude", nomadTrip.get("longitude"));
            weatherSection.put("source", stringOrDefault(result.get("source"), "api.open-meteo.com"));
            reportSections.put("weather", weatherSection);

            mapSection = buildMapPlacesSectionLocked(city);
            reportSections.put("mapPlaces", mapSection);
        }
        queueActivity(threadKey, ACT_WEATHER, city, weatherSection);
        queueActivity(threadKey, ACT_MAP_PLACES, city, mapSection);
    }

    private void applyCultureResult(String threadKey, Map<String, Object> args, Map<String, Object> result, boolean isError) {
        String argDest = stringOrDefault(args.get("destination"), stringOrDefault(args.get("city"), "Destination"));
        String city = normalizeCityName(stringOrDefault(result.get("destination"), argDest));
        String wikiUrl = stringOrDefault(
                result.get("wikiSourceUrl"),
                "https://en.wikipedia.org/wiki/" + URLEncoder.encode(city.replace(' ', '_'), StandardCharsets.UTF_8));
        String errText = stringOrDefault(result.get("error"), "Culture API unavailable");
        appendSource(threadKey, Map.of(
                "title", "Wikipedia Cultural Summary — " + city,
                "url", wikiUrl,
                "status", isError ? "error" : "verified",
                "error", isError ? errText : ""));
        if (isError) {
            Map<String, Object> errSection = null;
            synchronized (stateLock) {
                String currentActive = stringOrDefault(nomadTrip.get("activeCity"), null);
                boolean missingDestinationCall = "Destination".equals(city);
                boolean keepExistingHealthy = (missingDestinationCall && currentActive != null && hasSectionForCityLocked("cultureAudio", currentActive))
                        || (hasSectionForCityLocked("cultureAudio", city)
                                && reportSections.get("cultureAudio") instanceof Map<?, ?> existing
                                && !Boolean.TRUE.equals(existing.get("isError")));
                if (!keepExistingHealthy) {
                    errSection = buildCultureAudioSectionLocked(city, true, errText);
                    reportSections.put("cultureAudio", errSection);
                }
            }
            if (errSection != null) {
                queueActivity(threadKey, ACT_CULTURE_AUDIO, city, errSection);
            }
            return;
        }
        List<Map<String, Object>> mediaItems = new ArrayList<>();
        String heroImage = null;
        if (result.get("heroImageUrl") instanceof String imgUrl && !imgUrl.isBlank()) {
            heroImage = imgUrl.trim();
            mediaItems.add(Map.of(
                    "type", "image",
                    "title", city + " Cultural & Architectural Plate",
                    "subtitle", "Wikipedia REST API v1 • Verified Landmark Photography",
                    "url", heroImage));
        }
        Map<String, Object> soundtrackEntry = null;
        if (result.get("audioTrack") instanceof Map<?, ?> track
                && track.get("previewUrl") instanceof String previewUrl
                && !previewUrl.isBlank()) {
            String trackTitle = track.get("trackName") != null ? String.valueOf(track.get("trackName")) : city + " Acoustic Session";
            String artistName = track.get("artistName") != null ? String.valueOf(track.get("artistName")) : "Regional Artist";
            mediaItems.add(Map.of(
                    "type", "audio",
                    "title", trackTitle,
                    "subtitle", artistName,
                    "url", previewUrl,
                    "durationSeconds", 30));
            soundtrackEntry = Map.of(
                    "trackName", trackTitle,
                    "artistName", artistName,
                    "previewUrl", previewUrl,
                    "durationSeconds", 30);
        }
        if (!mediaItems.isEmpty()) {
            turnMedia.put(threadKey, mediaItems);
        }
        Map<String, Object> cultureSection;
        synchronized (stateLock) {
            switchActiveCityLocked(city);
            nomadTrip.put("wikiSourceUrl", wikiUrl);
            if (result.get("culturalSummary") instanceof String summary && !summary.isBlank()) {
                nomadTrip.put("culturalSummary", summary.trim());
            }
            if (heroImage != null) {
                nomadTrip.put("heroImageUrl", heroImage);
            }
            if (soundtrackEntry != null) {
                nomadTrip.put("soundtrack", soundtrackEntry);
            }
            cultureSection = buildCultureAudioSectionLocked(city, false, null);
            reportSections.put("cultureAudio", cultureSection);
            if (reportSections.containsKey("mapPlaces")) {
                reportSections.put("mapPlaces", buildMapPlacesSectionLocked(city));
            }
        }
        queueActivity(threadKey, ACT_CULTURE_AUDIO, city, cultureSection);
    }

    private void ensureTurnCultureMedia(String threadKey, String city, String genre) {
        if (city == null || city.isBlank()) {
            return;
        }
        String resolvedCity = normalizeCityName(city);
        List<Map<String, Object>> existingMedia = turnMedia.get(threadKey);
        if (existingMedia != null && !existingMedia.isEmpty()) {
            return;
        }
        try {
            String argsJson = objectMapper.writeValueAsString(Map.of(
                    "destination", resolvedCity,
                    "genre", genre == null ? "" : genre.trim()));
            ToolExecutionRequest req = ToolExecutionRequest.builder()
                    .id("mcp-cult-" + UUID.randomUUID().toString().substring(0, 8))
                    .name("discoverLocalCultureAndMedia")
                    .arguments(argsJson)
                    .build();
            ToolExecutionResult execResult = nomadMcpClient.executeTool(req);
            if (execResult == null || execResult.isError()) {
                return;
            }
            Map<String, Object> parsed = parseJsonOrEmpty(execResult.resultText());
            boolean isErr = Boolean.TRUE.equals(parsed.get("isError"))
                    || "ERROR".equalsIgnoreCase(String.valueOf(parsed.get("status")));
            if (!isErr && (parsed.containsKey("heroImageUrl") || parsed.containsKey("audioTrack") || parsed.containsKey("culturalSummary"))) {
                applyCultureResult(threadKey, Map.of("destination", resolvedCity), parsed, false);
            }
        } catch (Exception ignored) {
            // Optional media enrichment is best-effort when MCP client is unavailable
        }
    }

    private void ensureTurnWeather(String threadKey, String city) {
        if (city == null || city.isBlank()) {
            return;
        }
        String resolvedCity = normalizeCityName(city);
        try {
            String argsJson = objectMapper.writeValueAsString(Map.of("city", resolvedCity));
            ToolExecutionRequest req = ToolExecutionRequest.builder()
                    .id("mcp-weath-" + UUID.randomUUID().toString().substring(0, 8))
                    .name("fetchCityWeather")
                    .arguments(argsJson)
                    .build();
            ToolExecutionResult execResult = nomadMcpClient.executeTool(req);
            if (execResult == null) {
                return;
            }
            Map<String, Object> parsed = parseJsonOrEmpty(execResult.resultText());
            boolean isErr = execResult.isError()
                    || Boolean.TRUE.equals(parsed.get("isError"))
                    || "ERROR".equalsIgnoreCase(String.valueOf(parsed.get("status")));
            if (!isErr && parsed.get("temperatureC") instanceof Number) {
                applyWeatherResult(threadKey, Map.of("city", resolvedCity), parsed, false);
            } else if (isErr) {
                applyWeatherResult(threadKey, Map.of("city", resolvedCity), parsed, true);
            }
        } catch (Exception ignored) {
            // Best-effort weather enrichment when MCP client is unavailable
        }
    }

    private void ensureTurnAcousticMood(String threadKey, String city) {
        if (city == null || city.isBlank()) {
            return;
        }
        String resolvedCity = normalizeCityName(city);
        String cond;
        synchronized (stateLock) {
            cond = stringOrDefault(nomadTrip.get("weatherCondition"), "");
        }
        try {
            String argsJson = objectMapper.writeValueAsString(Map.of("city", resolvedCity, "weatherCondition", cond));
            ToolExecutionRequest req = ToolExecutionRequest.builder()
                    .id("mcp-mood-" + UUID.randomUUID().toString().substring(0, 8))
                    .name("sampleAcousticMood")
                    .arguments(argsJson)
                    .build();
            ToolExecutionResult execResult = nomadMcpClient.executeTool(req);
            if (execResult == null || execResult.isError()) {
                return;
            }
            Map<String, Object> parsed = parseJsonOrEmpty(execResult.resultText());
            if (!Boolean.TRUE.equals(parsed.get("isError")) && parsed.containsKey("sampledMood")) {
                applyAcousticMoodResult(threadKey, Map.of("city", resolvedCity), parsed, false, true);
            }
        } catch (Exception ignored) {
            // Best-effort acoustic mood sampling
        }
    }

    private void ensureTurnNeighborhoodPlaces(String threadKey, String city, A2aGatewayClient a2aClient) {
        if (city == null || city.isBlank() || a2aClient == null) {
            return;
        }
        String resolvedCity = normalizeCityName(city);
        try {
            Map<String, Object> handoff = a2aClient.delegateTask(
                    "a2a-places-" + UUID.randomUUID().toString().substring(0, 8),
                    "curate-neighborhood-cadence",
                    "Curate neighborhood places for " + resolvedCity,
                    Map.of("city", resolvedCity));
            boolean isErr = Boolean.TRUE.equals(handoff.get("isError"))
                    || "ERROR".equalsIgnoreCase(String.valueOf(handoff.get("status")));
            applyCadenceResult(threadKey, Map.of("city", resolvedCity), handoff, isErr);
        } catch (Exception ignored) {
            // Best-effort A2A cadence curation
        }
    }

    private void ensureTurnAdvisory(String threadKey, String city, A2aGatewayClient a2aClient) {
        if (city == null || city.isBlank() || a2aClient == null) {
            return;
        }
        String resolvedCity = normalizeCityName(city);
        try {
            Map<String, Object> handoff = a2aClient.delegateTask(
                    "a2a-adv-" + UUID.randomUUID().toString().substring(0, 8),
                    "verify-travel-advisory",
                    "Verify travel advisory for " + resolvedCity,
                    Map.of("city", resolvedCity));
            boolean isErr = Boolean.TRUE.equals(handoff.get("isError"))
                    || "ERROR".equalsIgnoreCase(String.valueOf(handoff.get("status")));
            applyAdvisoryResult(threadKey, Map.of("city", resolvedCity), handoff, isErr);
        } catch (Exception ignored) {
            // Best-effort A2A advisory verification
        }
    }

    private void ensureTurnBudget(
            String threadKey,
            String city,
            double budgetAmount,
            String baseCurrency,
            A2aGatewayClient a2aClient) {
        if (city == null || city.isBlank() || budgetAmount <= 0.0 || a2aClient == null) {
            return;
        }
        String resolvedCity = normalizeCityName(city);
        String normBase = (baseCurrency == null || baseCurrency.isBlank()) ? "USD" : baseCurrency.trim().toUpperCase(Locale.ROOT);
        String country = A2aGatewayClient.resolveCountryForCity(resolvedCity);
        String targetCurrency = A2aGatewayClient.resolveCurrencyForCountry(country);
        try {
            Map<String, Object> handoff = a2aClient.delegateTask(
                    "a2a-fx-" + UUID.randomUUID().toString().substring(0, 8),
                    "convert-and-audit-budget",
                    "Convert " + budgetAmount + " " + normBase + " for " + resolvedCity,
                    Map.of(
                            "city", resolvedCity,
                            "budgetUsd", budgetAmount,
                            "baseCurrency", normBase,
                            "targetCurrency", targetCurrency));
            boolean isErr = Boolean.TRUE.equals(handoff.get("isError"))
                    || "ERROR".equalsIgnoreCase(String.valueOf(handoff.get("status")));
            applyBudgetResult(
                    threadKey,
                    Map.of("city", resolvedCity, "budgetAmount", budgetAmount, "baseCurrency", normBase),
                    handoff,
                    isErr);
        } catch (Exception ignored) {
            // Best-effort A2A FX enrichment
        }
    }

    private void applyAcousticMoodResult(
            String threadKey,
            Map<String, Object> args,
            Map<String, Object> result,
            boolean isError,
            boolean preserveExistingSoundtrack) {
        if (!isError && result.get("sampledMood") instanceof String mood && !mood.isBlank()) {
            String window = stringOrDefault(result.get("recommendedListeningWindow"), "Sampled Acoustic Mood");
            String city = normalizeCityName(stringOrDefault(result.get("city"), stringOrDefault(args.get("city"), "Destination")));
            Map<String, Object> cultureSection;
            synchronized (stateLock) {
                switchActiveCityLocked(city);
                nomadTrip.put("acousticMood", mood.trim());
                nomadTrip.put("listeningWindow", window);
                Object existingSoundtrack = nomadTrip.get("soundtrack");
                if (existingSoundtrack == null) {
                    nomadTrip.put("soundtrack", Map.of(
                            "trackName", mood.trim(),
                            "artistName", window));
                } else if (!preserveExistingSoundtrack) {
                    Map<String, Object> updatedSoundtrack = new LinkedHashMap<>();
                    if (existingSoundtrack instanceof Map<?, ?> prev) {
                        for (Map.Entry<?, ?> entry : prev.entrySet()) {
                            updatedSoundtrack.put(String.valueOf(entry.getKey()), entry.getValue());
                        }
                    }
                    updatedSoundtrack.put("trackName", mood.trim());
                    updatedSoundtrack.put("artistName", window);
                    nomadTrip.put("soundtrack", updatedSoundtrack);
                }
                cultureSection = buildCultureAudioSectionLocked(city, false, null);
                reportSections.put("cultureAudio", cultureSection);
            }
            queueActivity(threadKey, ACT_CULTURE_AUDIO, city, cultureSection);
        }
    }

    private void applyReservationResult(String threadKey, Map<String, Object> args, Map<String, Object> result, boolean isError) {
        if (isError || "AWAITING_APPROVAL".equalsIgnoreCase(String.valueOf(result.get("status")))) {
            return;
        }
        String city = normalizeCityName(stringOrDefault(result.get("activeCity"), stringOrDefault(args.get("city"), "Destination")));
        Map<String, Object> fxSection;
        Map<String, Object> mapSection;
        synchronized (stateLock) {
            if (!city.isBlank() && !"Destination".equals(city)) {
                switchActiveCityLocked(city);
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
            fxSection = buildFxBudgetSectionLocked(city, false, null);
            reportSections.put("fxBudget", fxSection);
            mapSection = buildMapPlacesSectionLocked(city);
            reportSections.put("mapPlaces", mapSection);
        }
        queueActivity(threadKey, ACT_FX_BUDGET, city, fxSection);
        queueActivity(threadKey, ACT_MAP_PLACES, city, mapSection);
    }

    @SuppressWarnings("unchecked")
    private void applyCadenceResult(String threadKey, Map<String, Object> args, Map<String, Object> result, boolean isError) {
        Map<String, Object> report = result.get("report") instanceof Map<?, ?> r ? (Map<String, Object>) r : result;
        String city = normalizeCityName(stringOrDefault(report.get("city"), stringOrDefault(args.get("city"), "Destination")));
        if (isError) {
            return;
        }
        Map<String, Object> mapSection;
        synchronized (stateLock) {
            switchActiveCityLocked(city);
            String country = stringOrDefault(report.get("country"), A2aGatewayClient.resolveCountryForCity(city));
            if (!country.isBlank()) {
                nomadTrip.putIfAbsent("country", country);
            }
            if (report.get("focus") instanceof String focus && !focus.isBlank()) {
                nomadTrip.put("cadenceFocus", focus.trim());
            }
            if (report.get("pace") instanceof String pace && !pace.isBlank()) {
                nomadTrip.put("cadencePace", pace.trim());
            }
            if (report.get("stops") instanceof List<?> rawStops && !rawStops.isEmpty()) {
                List<String> stops = rawStops.stream().map(String::valueOf).toList();
                nomadTrip.put("places", stops);
            }
            mapSection = buildMapPlacesSectionLocked(city);
            reportSections.put("mapPlaces", mapSection);
        }
        queueActivity(threadKey, ACT_MAP_PLACES, city, mapSection);
    }

    @SuppressWarnings("unchecked")
    private void applyAdvisoryResult(String threadKey, Map<String, Object> args, Map<String, Object> result, boolean isError) {
        Map<String, Object> report = result.get("report") instanceof Map<?, ?> r ? (Map<String, Object>) r : result;
        String rawCityVal = stringOrDefault(report.get("city"), stringOrDefault(args.get("city"), "Destination"));
        String city = normalizeCityName(rawCityVal);
        String rawCountry = stringOrDefault(report.get("country"), "");
        String country = (rawCountry.isBlank() || rawCountry.equalsIgnoreCase(rawCityVal))
                ? A2aGatewayClient.resolveCountryForCity(rawCityVal)
                : rawCountry;
        String errorMsg = stringOrDefault(result.get("error"), stringOrDefault(report.get("error"), "Travel Advisory API unavailable"));
        appendSource(threadKey, Map.of(
                "title", "REST Countries v5 Travel Advisory — " + (country.isBlank() ? city : country),
                "url", "https://api.restcountries.com/countries/v5/names.common/"
                        + URLEncoder.encode(country.isBlank() ? city : country, StandardCharsets.UTF_8),
                "status", isError ? "error" : "verified",
                "error", isError ? errorMsg : ""));

        String currency = stringOrDefault(
                report.get("currency"),
                stringOrDefault(report.get("currencyCode"), A2aGatewayClient.resolveCurrencyForCountry(country)));
        List<String> highlights = report.get("safetyHighlights") instanceof List<?> rawList && !rawList.isEmpty()
                ? rawList.stream().map(String::valueOf).toList()
                : List.of(
                        "Confirm passport validity (6+ months) & consular entry requirements for " + (country.isBlank() ? city : country) + ".",
                        "Verify municipal transit & settlement currency (" + currency + ").",
                        "Nomad HITL financial safeguard active: sanctuary holds >= $1,500 USD require explicit traveler authorization.");

        Map<String, Object> securitySection = new LinkedHashMap<>();
        securitySection.put("status", isError ? "DEGRADED" : "VERIFIED");
        securitySection.put("isError", isError);
        securitySection.put("city", city);
        securitySection.put("country", country);
        securitySection.put("capital", stringOrDefault(report.get("capital"), isError ? null : city));
        securitySection.put("region", stringOrDefault(report.get("region"), null));
        securitySection.put("currencyCode", currency);
        securitySection.put("timezone", stringOrDefault(report.get("timezone"), null));
        securitySection.put("advisoryLevel", stringOrDefault(
                report.get("advisoryLevel"),
                isError ? "UNVERIFIED_UPSTREAM" : "STANDARD_PRECAUTIONS"));
        securitySection.put("advisoryStatus", stringOrDefault(
                report.get("advisoryStatus"),
                isError ? "Upstream Advisory Unverified (" + errorMsg + ")" : "VERIFIED — Country Metadata & Timezone Confirmed"));
        securitySection.put("emergencyNumbers", stringOrDefault(
                report.get("emergencyNumbers"),
                "112 Universal GSM • Local Municipal Dispatch"));
        securitySection.put("hitlThresholdUsd", HITL_THRESHOLD_USD);
        securitySection.put("safetyHighlights", highlights);
        securitySection.put("source", stringOrDefault(report.get("source"), "api.restcountries.com"));
        if (isError) {
            securitySection.put("error", errorMsg);
        }

        synchronized (stateLock) {
            String existingActiveCity = stringOrDefault(nomadTrip.get("activeCity"), null);
            if (!city.isBlank() && !"Destination".equals(city)
                    && (!isError || existingActiveCity == null || existingActiveCity.equalsIgnoreCase(city))) {
                switchActiveCityLocked(city);
                if (!country.isBlank()) {
                    nomadTrip.put("country", country);
                }
            }
            if (!isError) {
                nomadTrip.put("currency", currency);
            }
            nomadTrip.put("securityAdvisory", securitySection);
            reportSections.put("securityAdvisory", securitySection);
        }
        queueActivity(threadKey, ACT_SECURITY_ADVISORY, city, securitySection);
    }

    @SuppressWarnings("unchecked")
    private void applyBudgetResult(String threadKey, Map<String, Object> args, Map<String, Object> result, boolean isError) {
        Map<String, Object> report = result.get("report") instanceof Map<?, ?> r ? (Map<String, Object>) r : result;
        String sourceHost = stringOrDefault(report.get("source"), "api.frankfurter.dev");
        boolean isOpenErApi = "open.er-api.com".equalsIgnoreCase(sourceHost);
        String errText = stringOrDefault(result.get("error"), stringOrDefault(report.get("error"), "FX API unavailable"));
        appendSource(threadKey, Map.of(
                "title", isOpenErApi ? "ExchangeRate-API ISO 4217 FX Reference" : "Frankfurter ECB FX Reference",
                "url", isOpenErApi ? "https://open.er-api.com/v6/latest/USD" : "https://api.frankfurter.dev/v1/latest",
                "status", isError ? "error" : "verified",
                "error", isError ? errText : ""));

        String rawCityVal = stringOrDefault(report.get("city"), stringOrDefault(args.get("city"), "Destination"));
        String city = normalizeCityName(rawCityVal);
        if (isError) {
            Map<String, Object> errSection;
            synchronized (stateLock) {
                errSection = buildFxBudgetSectionLocked(city, true, errText);
                reportSections.put("fxBudget", errSection);
            }
            queueActivity(threadKey, ACT_FX_BUDGET, city, errSection);
            return;
        }
        String turnPrompt = turnPrompts.getOrDefault(threadKey, "");
        boolean nonReservationTurn = !turnPrompt.isBlank() && !RESERVATION_INTENT_PATTERN.matcher(turnPrompt).find();
        ParsedBudget promptBudget = nonReservationTurn ? extractBudgetFromPrompt(turnPrompt) : null;
        Integer promptNights = nonReservationTurn ? extractNightsFromPrompt(turnPrompt) : null;

        Map<String, Object> fxSection;
        synchronized (stateLock) {
            if (!city.isBlank() && !"Destination".equals(city)) {
                switchActiveCityLocked(city);
            }
            String rawCountry = stringOrDefault(report.get("country"), "");
            String resolvedCountry = (rawCountry.isBlank() || rawCountry.equalsIgnoreCase(rawCityVal))
                    ? A2aGatewayClient.resolveCountryForCity(rawCityVal)
                    : rawCountry;
            if (!resolvedCountry.isBlank()) {
                nomadTrip.put("country", resolvedCountry);
            }
            if (promptNights != null) {
                nomadTrip.put("nights", promptNights);
            }
            String targetCurrency = stringOrDefault(
                    report.get("targetCurrency"),
                    stringOrDefault(nomadTrip.get("currency"), A2aGatewayClient.resolveCurrencyForCountry(resolvedCountry)));
            if (!targetCurrency.isBlank()) {
                nomadTrip.put("currency", targetCurrency);
            }
            nomadTrip.put("fxSource", sourceHost);
            if (promptBudget != null) {
                String groundedBase = promptBudget.currency();
                double groundedAmount = promptBudget.amount();
                nomadTrip.put("homeCurrency", groundedBase);
                preferences.put("homeCurrency", groundedBase);
                nomadTrip.put("budgetUsd", groundedAmount);
                nomadTrip.put("allocatedUsd", Math.round(groundedAmount * 0.65 * 100.0) / 100.0);
                if (groundedBase.equalsIgnoreCase(targetCurrency)) {
                    nomadTrip.put("exchangeRate", 1.0);
                    nomadTrip.put("convertedTotal", groundedAmount);
                } else if (groundedBase.equalsIgnoreCase(stringOrDefault(report.get("baseCurrency"), ""))
                        && report.get("exchangeRate") instanceof Number r) {
                    double rateVal = r.doubleValue();
                    nomadTrip.put("exchangeRate", rateVal);
                    nomadTrip.put("convertedTotal", Math.round(groundedAmount * rateVal * 100.0) / 100.0);
                } else {
                    nomadTrip.remove("exchangeRate");
                    nomadTrip.remove("convertedTotal");
                }
            } else {
                if (report.get("baseCurrency") instanceof String bc && !bc.isBlank()) {
                    nomadTrip.put("homeCurrency", bc);
                }
                if (report.get("exchangeRate") instanceof Number rate) {
                    nomadTrip.put("exchangeRate", rate.doubleValue());
                }
                if (report.get("convertedTotal") instanceof Number conv) {
                    nomadTrip.put("convertedTotal", conv.doubleValue());
                }
                Object budgetObj = args.get("budgetAmount") != null ? args.get("budgetAmount") : report.get("budgetUsd");
                if (budgetObj instanceof Number budget && budget.doubleValue() > 0.0) {
                    nomadTrip.put("budgetUsd", budget.doubleValue());
                    nomadTrip.putIfAbsent("allocatedUsd", Math.round(budget.doubleValue() * 0.65 * 100.0) / 100.0);
                }
            }
            fxSection = buildFxBudgetSectionLocked(city, false, null);
            reportSections.put("fxBudget", fxSection);
        }
        queueActivity(threadKey, ACT_FX_BUDGET, city, fxSection);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildMapPlacesSectionLocked(String fallbackCity) {
        String city = stringOrDefault(nomadTrip.get("activeCity"), fallbackCity);
        String country = stringOrDefault(nomadTrip.get("country"), A2aGatewayClient.resolveCountryForCity(city));
        String wikiUrl = stringOrDefault(
                nomadTrip.get("wikiSourceUrl"),
                city == null || city.isBlank()
                        ? null
                        : "https://en.wikipedia.org/wiki/" + URLEncoder.encode(city.replace(' ', '_'), StandardCharsets.UTF_8));
        List<String> stops = nomadTrip.get("places") instanceof List<?> raw && !raw.isEmpty()
                ? raw.stream().map(String::valueOf).toList()
                : (nomadTrip.get("stopTitle") instanceof String st && !st.isBlank()
                        ? List.of(st)
                        : List.of(city + " Historic Center & Cultural District"));

        Map<String, Object> mapSection = new LinkedHashMap<>();
        mapSection.put("status", "OK");
        mapSection.put("isError", false);
        mapSection.put("city", city);
        mapSection.put("country", country);
        mapSection.put("latitude", nomadTrip.get("latitude"));
        mapSection.put("longitude", nomadTrip.get("longitude"));
        mapSection.put("stopTitle", nomadTrip.get("stopTitle"));
        mapSection.put("focus", stringOrDefault(nomadTrip.get("cadenceFocus"), "architecture, local craft & evening acoustics"));
        mapSection.put("pace", stringOrDefault(nomadTrip.get("cadencePace"), "Unrushed curated walk"));
        mapSection.put("stops", stops);
        mapSection.put("wikiSourceUrl", wikiUrl);
        return mapSection;
    }

    private Map<String, Object> buildFxBudgetSectionLocked(String fallbackCity, boolean isError, String error) {
        String city = stringOrDefault(nomadTrip.get("activeCity"), fallbackCity);
        String country = stringOrDefault(nomadTrip.get("country"), A2aGatewayClient.resolveCountryForCity(city));
        String baseCurrency = stringOrDefault(nomadTrip.get("homeCurrency"), "USD");
        String targetCurrency = stringOrDefault(nomadTrip.get("currency"), A2aGatewayClient.resolveCurrencyForCountry(country));
        Double budget = nomadTrip.get("budgetUsd") instanceof Number b ? b.doubleValue() : null;
        Double allocated = nomadTrip.get("allocatedUsd") instanceof Number a ? a.doubleValue() : null;
        Double rate = nomadTrip.get("exchangeRate") instanceof Number r ? r.doubleValue() : null;
        Double converted = nomadTrip.get("convertedTotal") instanceof Number c
                ? c.doubleValue()
                : (budget != null && rate != null ? Math.round(budget * rate * 100.0) / 100.0 : null);

        Map<String, Object> fxSection = new LinkedHashMap<>();
        fxSection.put("status", isError ? "ERROR" : "OK");
        fxSection.put("isError", isError);
        fxSection.put("city", city);
        fxSection.put("country", country);
        fxSection.put("nights", nomadTrip.get("nights"));
        fxSection.put("stopTitle", nomadTrip.get("stopTitle"));
        fxSection.put("baseCurrency", baseCurrency);
        fxSection.put("targetCurrency", targetCurrency);
        fxSection.put("exchangeRate", rate);
        fxSection.put("budgetUsd", budget);
        fxSection.put("allocatedUsd", allocated);
        fxSection.put("convertedTotal", converted);
        fxSection.put("hitlThresholdUsd", HITL_THRESHOLD_USD);
        fxSection.put("source", stringOrDefault(nomadTrip.get("fxSource"), "api.frankfurter.dev"));
        if (isError && error != null) {
            fxSection.put("error", error);
        }
        return fxSection;
    }

    private Map<String, Object> buildCultureAudioSectionLocked(String fallbackCity, boolean isError, String error) {
        String city = stringOrDefault(nomadTrip.get("activeCity"), fallbackCity);
        String country = stringOrDefault(nomadTrip.get("country"), A2aGatewayClient.resolveCountryForCity(city));
        Map<String, Object> cultureSection = new LinkedHashMap<>();
        cultureSection.put("status", isError ? "ERROR" : "OK");
        cultureSection.put("isError", isError);
        cultureSection.put("city", city);
        cultureSection.put("country", country);
        cultureSection.put("culturalSummary", nomadTrip.get("culturalSummary"));
        cultureSection.put("heroImageUrl", nomadTrip.get("heroImageUrl"));
        cultureSection.put("wikiSourceUrl", nomadTrip.get("wikiSourceUrl"));
        cultureSection.put("soundtrack", nomadTrip.get("soundtrack"));
        cultureSection.put("acousticMood", nomadTrip.get("acousticMood"));
        cultureSection.put("listeningWindow", nomadTrip.get("listeningWindow"));
        if (isError && error != null) {
            cultureSection.put("error", error);
        }
        return cultureSection;
    }

    private void queueActivity(String threadKey, String activityType, String city, Map<String, Object> content) {
        String slug = (city == null || city.isBlank() ? "trip" : city.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-"));
        String msgId = "act-" + activityType.replace("agui:", "") + "-" + slug;
        long now = System.currentTimeMillis();
        ActivitySnapshotEvent event = new ActivitySnapshotEvent(
                msgId,
                activityType,
                new LinkedHashMap<>(content),
                true,
                now,
                Map.of(
                        "protocol", "AG-UI",
                        "callType", "agui:activity",
                        "activityType", activityType,
                        "city", city == null ? "" : city));
        pendingTurnActivities.compute(threadKey, (k, existing) -> {
            Map<String, ActivitySnapshotEvent> map = existing == null ? new LinkedHashMap<>() : existing;
            synchronized (map) {
                map.put(activityType, event);
            }
            return map;
        });
    }

    private Map<String, Object> copyReportSectionsLocked() {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : reportSections.entrySet()) {
            copy.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
        return copy;
    }

    private void switchActiveCityLocked(String newCity) {
        if (newCity == null || newCity.isBlank()) {
            return;
        }
        String normalized = normalizeCityName(newCity);
        String current = stringOrDefault(nomadTrip.get("activeCity"), null);
        if (current != null && !current.equalsIgnoreCase(normalized)) {
            nomadTrip.remove("country");
            nomadTrip.remove("currency");
            nomadTrip.remove("exchangeRate");
            nomadTrip.remove("convertedTotal");
            nomadTrip.remove("fxSource");
            nomadTrip.remove("temperatureC");
            nomadTrip.remove("windSpeedKph");
            nomadTrip.remove("latitude");
            nomadTrip.remove("longitude");
            nomadTrip.remove("weatherCondition");
            nomadTrip.remove("stopTitle");
            nomadTrip.remove("allocatedUsd");
            nomadTrip.remove("heroImageUrl");
            nomadTrip.remove("culturalSummary");
            nomadTrip.remove("wikiSourceUrl");
            nomadTrip.remove("soundtrack");
            nomadTrip.remove("acousticMood");
            nomadTrip.remove("listeningWindow");
            nomadTrip.remove("places");
            nomadTrip.remove("cadenceFocus");
            nomadTrip.remove("cadencePace");
            nomadTrip.remove("securityAdvisory");
            reportSections.clear();
        }
        nomadTrip.put("activeCity", normalized);
    }

    static String extractCityFromPrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return null;
        }
        String lower = prompt.toLowerCase(Locale.ROOT);
        for (String city : COMPLETION_CITIES) {
            String cLower = city.toLowerCase(Locale.ROOT);
            int idx = lower.indexOf(cLower);
            if (idx >= 0) {
                boolean leftOk = idx == 0 || !Character.isLetter(lower.charAt(idx - 1));
                int endIdx = idx + cLower.length();
                boolean rightOk = endIdx >= lower.length() || !Character.isLetter(lower.charAt(endIdx));
                if (leftOk && rightOk) {
                    return city;
                }
            }
        }
        return null;
    }

    static Integer extractNightsFromPrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return null;
        }
        Matcher matcher = NIGHTS_PATTERN.matcher(prompt);
        if (matcher.find()) {
            try {
                int parsed = Integer.parseInt(matcher.group(1));
                if (parsed > 0) {
                    return Math.min(30, parsed);
                }
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    static ParsedBudget extractBudgetFromPrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return null;
        }
        Matcher symMatcher = SYMBOL_BUDGET_PATTERN.matcher(prompt);
        if (symMatcher.find()) {
            String symbol = symMatcher.group(1);
            String numberStr = symMatcher.group(2).replace(",", "");
            String explicitCode = symMatcher.group(3);
            try {
                double amount = Double.parseDouble(numberStr);
                if (amount > 0.0) {
                    String currency = explicitCode != null && !explicitCode.isBlank()
                            ? normalizeCurrencyToken(explicitCode)
                            : ("€".equals(symbol) ? "EUR" : ("£".equals(symbol) ? "GBP" : "USD"));
                    return new ParsedBudget(amount, currency);
                }
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        Matcher trailingSymMatcher = TRAILING_SYMBOL_BUDGET_PATTERN.matcher(prompt);
        if (trailingSymMatcher.find()) {
            String numberStr = trailingSymMatcher.group(1).replace(",", "");
            String symbol = trailingSymMatcher.group(2);
            String explicitCode = trailingSymMatcher.group(3);
            try {
                double amount = Double.parseDouble(numberStr);
                if (amount > 0.0) {
                    String currency = explicitCode != null && !explicitCode.isBlank()
                            ? normalizeCurrencyToken(explicitCode)
                            : ("€".equals(symbol) ? "EUR" : ("£".equals(symbol) ? "GBP" : "USD"));
                    return new ParsedBudget(amount, currency);
                }
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        Matcher codeMatcher = CODE_BUDGET_PATTERN.matcher(prompt);
        if (codeMatcher.find()) {
            String numberStr = codeMatcher.group(1).replace(",", "");
            String code = normalizeCurrencyToken(codeMatcher.group(2));
            try {
                double amount = Double.parseDouble(numberStr);
                if (amount > 0.0) {
                    return new ParsedBudget(amount, code);
                }
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    static String normalizeCurrencyToken(String raw) {
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "euro", "euros" -> "EUR";
            case "dollar", "dollars" -> "USD";
            case "pound", "pounds", "sterling" -> "GBP";
            case "franc", "francs" -> "CHF";
            case "yen" -> "JPY";
            default -> raw.trim().toUpperCase(Locale.ROOT);
        };
    }

    static boolean isKnownCityName(String raw) {
        String norm = normalizeCityName(raw);
        if (norm.isEmpty()) {
            return false;
        }
        return COMPLETION_CITIES.stream().anyMatch(c -> c.equalsIgnoreCase(norm));
    }

    static String normalizeCityName(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim().replaceAll("\\s+", " ");
        int commaIdx = trimmed.indexOf(',');
        if (commaIdx > 0) {
            trimmed = trimmed.substring(0, commaIdx).trim();
        }
        if (trimmed.isEmpty()) {
            return "";
        }
        for (String city : COMPLETION_CITIES) {
            if (city.equalsIgnoreCase(trimmed)) {
                return city;
            }
        }
        String stripped = A2aGatewayClient.stripTrailingCountry(trimmed);
        if (!stripped.isEmpty()) {
            for (String city : COMPLETION_CITIES) {
                if (city.equalsIgnoreCase(stripped)) {
                    return city;
                }
            }
            return Character.toUpperCase(stripped.charAt(0)) + stripped.substring(1);
        }
        return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
    }

    private void appendSource(String threadKey, Map<String, Object> source) {
        String incomingTitle = stringOrDefault(source.get("title"), "");
        turnSources.compute(threadKey, (k, existing) -> {
            List<Map<String, Object>> next = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
            if (!incomingTitle.isEmpty()) {
                for (int i = 0; i < next.size(); i++) {
                    if (incomingTitle.equals(stringOrDefault(next.get(i).get("title"), ""))) {
                        boolean prevVerified = "verified".equals(next.get(i).get("status"));
                        boolean nextError = "error".equals(source.get("status"));
                        if (!(prevVerified && nextError)) {
                            next.set(i, source);
                        }
                        return next;
                    }
                }
            }
            next.add(source);
            return next;
        });
    }

    private void resetDossierMapsLocked() {
        nomadTrip.clear();
        nomadTrip.put("uri", "itinerary://active-trip");

        preferences.clear();
        preferences.put("uri", "traveler://preferences");

        reportSections.clear();
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
