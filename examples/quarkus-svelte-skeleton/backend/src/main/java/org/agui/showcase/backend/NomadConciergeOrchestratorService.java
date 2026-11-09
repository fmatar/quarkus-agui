package org.agui.showcase.backend;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.agui.core.domain.AgUiEvent;
import org.agui.quarkus.core.runtime.adapters.AgUiA2aBridge;
import org.agui.quarkus.core.runtime.adapters.AgUiBridge;
import org.agui.quarkus.core.runtime.adapters.AgUiMcpBridge;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Nomad Travel & Local Culture Concierge Orchestrator (:8080).
 * Coordinates intent-driven conversational travel chat (Ollama-first), zero-default interactive
 * slot-filling, native {@code quarkus-langchain4j-mcp} tool/resource/prompt execution (:8081/mcp),
 * A2A specialist delegation (:8082), HITL reservation gates, and live multi-service healthchecks.
 */
@ApplicationScoped
public class NomadConciergeOrchestratorService {

    public static final String ORCHESTRATOR_NAME = "Nomad-Concierge-Orchestrator";
    private static final double HITL_THRESHOLD_USD = 1500.0;

    private static final List<String> INJECTION_PATTERNS = List.of(
            "ignore previous instructions",
            "ignore all instructions",
            "reveal system prompt",
            "drop table",
            "rm -rf /");

    private static final Map<String, Integer> WORD_NUMBERS = Map.of(
            "one", 1, "two", 2, "three", 3, "four", 4, "five", 5,
            "six", 6, "seven", 7, "eight", 8, "ten", 10);

    private static final List<String> FOCUS_KEYWORDS = List.of(
            "architecture", "architectural", "brutalist", "modernist", "machiya", "azulejo", "basalt",
            "culinary", "food", "gastronomy", "mezze", "street food", "dining", "wine", "tea",
            "music", "jazz", "fado", "koto", "vinyl", "oud", "acoustic", "tango",
            "history", "historical", "heritage", "museums", "art", "galleries",
            "nature", "coastal", "aurora", "geothermal", "hiking", "relaxation", "cultural");

    public enum TravelIntent {
        GENERAL_CHAT_OR_PROFILE,
        WEATHER_CHECK,
        TRAVEL_ADVISORY,
        CURRENCY_AND_BUDGET,
        CULTURE_AND_MUSIC,
        CHOICE_CURATION,
        HITL_RESERVATION,
        TRIP_PLANNING
    }

    public record TripSlotState(
            String city,
            Integer nights,
            Double budget,
            String homeCurrency,
            String travelFocus) {

        public static TripSlotState empty() {
            return new TripSlotState(null, null, null, null, null);
        }

        public boolean hasCity() {
            return city != null && !city.isBlank();
        }

        public boolean hasDuration() {
            return nights != null && nights > 0;
        }

        public boolean hasBudget() {
            return budget != null && budget > 0 && homeCurrency != null && !homeCurrency.isBlank();
        }

        public boolean hasFocus() {
            return travelFocus != null && !travelFocus.isBlank();
        }

        public List<String> missingTripPlanningSlots() {
            List<String> missing = new ArrayList<>();
            if (!hasCity()) {
                missing.add("destination");
            }
            if (!hasDuration()) {
                missing.add("duration");
            }
            if (!hasBudget()) {
                missing.add("budget");
            }
            if (!hasFocus()) {
                missing.add("travelFocus");
            }
            return missing;
        }

        public boolean isCompleteForTripPlanning() {
            return missingTripPlanningSlots().isEmpty();
        }

        public TripSlotState merge(TripSlotState newer) {
            if (newer == null) {
                return this;
            }
            return new TripSlotState(
                    newer.hasCity() ? newer.city() : this.city,
                    newer.hasDuration() ? newer.nights() : this.nights,
                    (newer.budget() != null && newer.budget() > 0) ? newer.budget() : this.budget,
                    (newer.homeCurrency() != null && !newer.homeCurrency().isBlank()) ? newer.homeCurrency() : this.homeCurrency,
                    newer.hasFocus() ? newer.travelFocus() : this.travelFocus);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("city", city);
            map.put("nights", nights);
            map.put("budget", budget);
            map.put("homeCurrency", homeCurrency);
            map.put("travelFocus", travelFocus);
            return map;
        }
    }

    private record PendingHitlReservation(
            String threadId,
            String city,
            String stopTitle,
            int nights,
            double costUsd,
            String llmMode) {}

    public record MemoryTurn(String role, String content) {}

    private final McpGatewayClient mcpClient;
    private final A2aGatewayClient a2aClient;
    private final AgUiBridge agUiBridge = new AgUiBridge();
    private final AgUiMcpBridge agUiMcpBridge = new AgUiMcpBridge();
    private final AgUiA2aBridge agUiA2aBridge = new AgUiA2aBridge();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private final Map<String, TripSlotState> threadSlots = new ConcurrentHashMap<>();
    private final Map<String, Boolean> threadAwaitingSlots = new ConcurrentHashMap<>();
    private final Map<String, PendingHitlReservation> pendingApprovals = new ConcurrentHashMap<>();
    private final Map<String, List<MemoryTurn>> conversationMemory = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> activeRunCancellations = new ConcurrentHashMap<>();

    @ConfigProperty(name = "agui.ollama.base-url", defaultValue = "http://127.0.0.1:11434")
    String ollamaBaseUrl = "http://127.0.0.1:11434";

    @ConfigProperty(name = "agui.ollama.model", defaultValue = "llama3.2:latest")
    String defaultOllamaModel = "llama3.2:latest";

    @Inject
    public NomadConciergeOrchestratorService(McpGatewayClient mcpClient, A2aGatewayClient a2aClient) {
        this.mcpClient = mcpClient;
        this.a2aClient = a2aClient;
    }

    public AgUiBridge agUiBridge() {
        return agUiBridge;
    }

    public AgUiMcpBridge agUiMcpBridge() {
        return agUiMcpBridge;
    }

    public AgUiA2aBridge agUiA2aBridge() {
        return agUiA2aBridge;
    }

    public Map<String, Object> getCatalog() {
        Map<String, Object> dossier = mcpClient.readTripDossierState();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("orchestrator", ORCHESTRATOR_NAME);
        out.put("extension", "org.agui:quarkus-ag-ui");
        out.put("llmMode", "ollama:" + defaultOllamaModel);
        out.put("defaultOllamaModel", defaultOllamaModel);
        out.put("supportedModes", List.of("ollama:" + defaultOllamaModel, "deterministic"));
        out.put("mcpTools", mcpClient.listTools());
        out.put("a2aAgent", a2aClient.getAgentCard());
        out.put("tripDossierState", dossier);
        out.put("clusterState", dossier);
        return out;
    }

    public Map<String, Object> checkSystemHealth() {
        List<Map<String, Object>> services = new ArrayList<>();
        services.addAll(mcpClient.checkMcpHealth());
        services.addAll(a2aClient.checkA2aHealth());
        services.add(checkOllamaHealth());

        long upCount = services.stream().filter(s -> "UP".equals(s.get("status"))).count();
        long downCount = services.size() - upCount;
        String overall = downCount == 0 ? "UP" : (upCount == 0 ? "DOWN" : "DEGRADED");

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("orchestrator", ORCHESTRATOR_NAME);
        report.put("status", overall);
        report.put("healthyCount", upCount);
        report.put("downCount", downCount);
        report.put("totalCount", services.size());
        report.put("services", services);
        return report;
    }

    private Map<String, Object> checkOllamaHealth() {
        long startNs = System.nanoTime();
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(ollamaBaseUrl + "/api/tags"))
                    .timeout(Duration.ofMillis(2000))
                    .GET()
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            long ms = Math.max(1L, (System.nanoTime() - startNs) / 1_000_000L);
            if (res.statusCode() >= 200 && res.statusCode() < 300) {
                return Map.of(
                        "id", "ollama-llm",
                        "name", "Ollama Travel Brain (" + defaultOllamaModel + ")",
                        "category", "llm",
                        "status", "UP",
                        "latencyMs", ms);
            }
            return Map.of(
                    "id", "ollama-llm",
                    "name", "Ollama Travel Brain (" + defaultOllamaModel + ")",
                    "category", "llm",
                    "status", "DOWN",
                    "latencyMs", ms,
                    "error", "HTTP " + res.statusCode() + " from Ollama (:11434)");
        } catch (Exception ex) {
            long ms = Math.max(1L, (System.nanoTime() - startNs) / 1_000_000L);
            return Map.of(
                    "id", "ollama-llm",
                    "name", "Ollama Travel Brain (" + defaultOllamaModel + ")",
                    "category", "llm",
                    "status", "DOWN",
                    "latencyMs", ms,
                    "error", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }

    public List<MemoryTurn> getConversationMemory(String threadId) {
        String key = normalizeThreadId(threadId);
        return List.copyOf(conversationMemory.getOrDefault(key, List.of()));
    }

    public Map<String, Object> updateTripParams(String threadId, Integer nights, String homeCurrency) {
        String resolvedThread = normalizeThreadId(threadId);
        Map<String, Object> dossier = mcpClient.updateTripParameters(nights, homeCurrency);
        TripSlotState prev = threadSlots.getOrDefault(resolvedThread, TripSlotState.empty());
        TripSlotState updated = new TripSlotState(
                prev.city(),
                nights != null && nights > 0 ? nights : prev.nights(),
                prev.budget(),
                (homeCurrency != null && !homeCurrency.isBlank())
                        ? homeCurrency.trim().toUpperCase(Locale.ROOT)
                        : prev.homeCurrency(),
                prev.travelFocus());
        threadSlots.put(resolvedThread, updated);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("threadId", resolvedThread);
        out.put("status", "params_updated");
        out.put("tripSlots", updated.toMap());
        out.put("tripDossierState", dossier);
        out.put("clusterState", dossier);
        return out;
    }

    public Map<String, Object> resetMemory(String threadId) {
        String resolvedThread = normalizeThreadId(threadId);
        conversationMemory.remove(resolvedThread);
        threadSlots.remove(resolvedThread);
        threadAwaitingSlots.remove(resolvedThread);
        pendingApprovals.remove(resolvedThread);
        mcpClient.resetState();
        Map<String, Object> dossier = mcpClient.readTripDossierState();
        return Map.of(
                "threadId", resolvedThread,
                "status", "memory_reset",
                "memoryTurns", 0,
                "tripDossierState", dossier,
                "clusterState", dossier);
    }

    public void cancelRun(String threadId) {
        AtomicBoolean flag = activeRunCancellations.get(normalizeThreadId(threadId));
        if (flag != null) {
            flag.set(true);
        }
    }

    public List<String> completeCityPrefix(String prefix) {
        return mcpClient.completePromptArgument(prefix);
    }

    public AgentChatResponse executeChat(AgentRunRequest request) {
        String threadId = request == null || request.threadId() == null || request.threadId().isBlank()
                ? "thread-default"
                : request.threadId().trim();
        String prompt = request == null || request.prompt() == null ? "" : request.prompt().trim();
        String mode = resolveMode(request == null ? null : request.mode());
        Map<String, Object> state = request == null || request.state() == null ? Map.of() : request.state();

        List<Map<String, Object>> mcpCalls = new ArrayList<>();
        List<Map<String, Object>> a2aHandoffs = new ArrayList<>();
        StringBuilder replyBuilder = new StringBuilder();

        List<String> frames = streamRun(new AgentRunRequest(threadId, prompt, state, mode))
                .collect().asList().await().indefinitely();
        for (String rawFrame : frames) {
            String json = AgentResource.unwrapSseDataPayload(rawFrame);
            if (json == null || json.isBlank()) {
                continue;
            }
            try {
                JsonNode node = objectMapper.readTree(json);
                String type = node.path("type").asText("");
                JsonNode payload = node.path("payload");
                if ("TEXT_DELTA".equals(type)) {
                    replyBuilder.append(payload.path("content").asText(payload.path("delta").asText("")));
                } else if ("TOOL_RESULT".equals(type)) {
                    String callType = payload.path("callType").asText("");
                    if ("mcp:tool".equals(callType)) {
                        mcpCalls.add(objectMapper.convertValue(payload, new TypeReference<Map<String, Object>>() {}));
                    }
                } else if ("STEP_FINISHED".equals(type)) {
                    String callType = payload.path("callType").asText("");
                    if ("a2a:task".equals(callType)) {
                        a2aHandoffs.add(objectMapper.convertValue(payload, new TypeReference<Map<String, Object>>() {}));
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return new AgentChatResponse(
                threadId,
                "COMPLETED",
                mode,
                mcpCalls,
                a2aHandoffs,
                mcpClient.readTripDossierState(),
                replyBuilder.toString().trim());
    }

    public Multi<String> streamRun(AgentRunRequest request) {
        return Multi.createFrom().<String>emitter(emitter -> {
            String threadId = normalizeThreadId(request == null ? null : request.threadId());
            String prompt = request == null || request.prompt() == null ? "" : request.prompt().trim();
            String llmMode = resolveMode(request == null ? null : request.mode());
            Map<String, Object> reqState = request == null || request.state() == null ? Map.of() : request.state();

            AtomicBoolean cancelled = new AtomicBoolean(false);
            activeRunCancellations.put(threadId, cancelled);
            try {
                executeStreamingTurn(emitter, threadId, prompt, reqState, llmMode, cancelled);
            } catch (Exception ex) {
                emitFrame(emitter, threadId, "RUN_FINISHED", Map.of(
                        "threadId", threadId,
                        "status", "failed",
                        "error", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
            } finally {
                activeRunCancellations.remove(threadId, cancelled);
                emitter.complete();
            }
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private void executeStreamingTurn(
            MultiEmitter<? super String> emitter,
            String threadId,
            String prompt,
            Map<String, Object> reqState,
            String llmMode,
            AtomicBoolean cancelled) {

        emitFrame(emitter, threadId, "RUN_STARTED", Map.of(
                "threadId", threadId,
                "llmMode", llmMode,
                "orchestrator", ORCHESTRATOR_NAME));

        Map<String, Object> initialDossier = mcpClient.readTripDossierState();
        emitFrame(emitter, threadId, "STATE_SNAPSHOT", Map.of(
                "tripDossierState", initialDossier,
                "clusterState", initialDossier));

        // 1. OWASP-LLM01 Input Guardrail
        if (isBlockedByInputGuardrail(prompt)) {
            emitGuardrailRejection(emitter, threadId, prompt);
            return;
        }

        // 2. Extract Traveler Profile preferences (name, dietary, style, music, currency)
        extractAndApplyTravelerPreferences(prompt);

        // 3. Merge Trip Slots across multi-turn thread memory (zero assumed defaults!)
        TripSlotState previousSlots = threadSlots.getOrDefault(threadId, TripSlotState.empty());
        boolean wasAwaitingSlots = Boolean.TRUE.equals(threadAwaitingSlots.get(threadId));
        TripSlotState turnSlots = extractSlotsFromTurn(prompt, reqState);
        TripSlotState mergedSlots = previousSlots.merge(turnSlots);
        threadSlots.put(threadId, mergedSlots);

        if (mergedSlots.hasCity()) {
            String country = A2aGatewayClient.resolveCountryForCity(mergedSlots.city());
            Map<String, Object> partial = new LinkedHashMap<>();
            partial.put("activeCity", mergedSlots.city());
            if (!country.isBlank()) {
                partial.put("country", country);
            }
            if (mergedSlots.hasDuration()) {
                partial.put("nights", mergedSlots.nights());
            }
            if (mergedSlots.budget() != null) {
                partial.put("budgetUsd", mergedSlots.budget());
            }
            if (mergedSlots.homeCurrency() != null) {
                partial.put("homeCurrency", mergedSlots.homeCurrency());
            }
            mcpClient.updateActiveTripFields(partial);
        }

        // 4. Classify Travel Intent
        TravelIntent intent = classifyIntent(prompt, mergedSlots, wasAwaitingSlots);
        appendMemoryTurn(threadId, "user", prompt);

        emitReasoningStep(emitter, threadId, intent, mergedSlots);
        if (cancelled.get()) {
            emitFrame(emitter, threadId, "RUN_FINISHED", Map.of("threadId", threadId, "status", "cancelled"));
            return;
        }

        switch (intent) {
            case GENERAL_CHAT_OR_PROFILE -> handleGeneralChatTurn(emitter, threadId, prompt, mergedSlots, llmMode);
            case HITL_RESERVATION -> handleHitlReservationTurn(emitter, threadId, prompt, mergedSlots, llmMode);
            case CHOICE_CURATION -> handleChoiceCurationTurn(emitter, threadId, prompt, mergedSlots, llmMode);
            case WEATHER_CHECK -> handleWeatherOnlyTurn(emitter, threadId, prompt, mergedSlots, llmMode);
            case TRAVEL_ADVISORY -> handleAdvisoryOnlyTurn(emitter, threadId, prompt, mergedSlots, llmMode);
            case CURRENCY_AND_BUDGET -> handleCurrencyOnlyTurn(emitter, threadId, prompt, mergedSlots, llmMode);
            case CULTURE_AND_MUSIC -> handleCultureOnlyTurn(emitter, threadId, prompt, mergedSlots, llmMode);
            case TRIP_PLANNING -> handleTripPlanningTurn(emitter, threadId, prompt, mergedSlots, llmMode);
        }
    }

    private void handleGeneralChatTurn(
            MultiEmitter<? super String> emitter,
            String threadId,
            String prompt,
            TripSlotState slots,
            String llmMode) {
        Map<String, Object> prefs = mcpClient.readResource("traveler://preferences");
        String travelerName = prefs.get("travelerName") instanceof String n && !n.isBlank() ? n : null;

        Map<String, Object> delta = new LinkedHashMap<>();
        Map<String, Object> dossier = mcpClient.readTripDossierState();
        delta.put("tripDossierState", dossier);
        delta.put("clusterState", dossier);
        if (!slots.hasCity()) {
            delta.put("awaitingDestination", true);
            delta.put("interaction", buildWelcomeCityPickerInteraction(travelerName));
        }
        delta.put("followUps", List.of(
                "I want a trip in Paris",
                "What's the weather in Beirut?",
                "Play local Fado music from Lisbon"));
        emitFrame(emitter, threadId, "STATE_DELTA", delta);

        String fallbackReply = (travelerName != null ? "Welcome, **" + travelerName + "**! " : "Welcome to **Nomad Concierge**! ")
                + "I am your dedicated travel & local culture concierge. Tell me which **destination city** you would like to explore—or ask about live weather, local music, currency exchange, or travel advisories anywhere in the world.";
        String systemPrompt = "You are Nomad, a warm, knowledgeable travel and local culture chatbot. "
                + "Do NOT assume any default destination city (never mention Kyoto unless asked). "
                + (travelerName != null ? "Greet " + travelerName + " by name, acknowledge any preferences shared, and " : "")
                + "ask which city or travel experience they would like to explore next in 2-3 concise sentences.";
        String finalText = streamAssistantText(emitter, threadId, llmMode, systemPrompt, prompt, fallbackReply);
        appendMemoryTurn(threadId, "assistant", finalText);
        emitCompletedRun(emitter, threadId, llmMode);
    }

    private void handleTripPlanningTurn(
            MultiEmitter<? super String> emitter,
            String threadId,
            String prompt,
            TripSlotState slots,
            String llmMode) {
        List<String> missing = slots.missingTripPlanningSlots();
        if (!missing.isEmpty()) {
            threadAwaitingSlots.put(threadId, true);
            emitMissingTripSlotsTurn(emitter, threadId, prompt, slots, missing, llmMode);
            return;
        }
        threadAwaitingSlots.put(threadId, false);
        executeCompleteTripPlan(emitter, threadId, prompt, slots, llmMode);
    }

    private void emitMissingTripSlotsTurn(
            MultiEmitter<? super String> emitter,
            String threadId,
            String prompt,
            TripSlotState slots,
            List<String> missing,
            String llmMode) {
        String cityLabel = slots.hasCity() ? slots.city() : "your trip";
        Map<String, Object> dossier = mcpClient.readTripDossierState();

        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("awaitingTripSlots", true);
        delta.put("missingSlots", missing);
        delta.put("tripSlots", slots.toMap());
        delta.put("tripDossierState", dossier);
        delta.put("clusterState", dossier);
        delta.put("interaction", buildSlotElicitationInteraction(slots, missing));
        delta.put("followUps", buildSlotFollowUpSuggestions(slots));
        emitFrame(emitter, threadId, "STATE_DELTA", delta);

        List<String> questions = new ArrayList<>();
        if (missing.contains("destination")) {
            questions.add("1. **Destination City** — Which city or region would you like to visit?");
        }
        if (missing.contains("duration")) {
            questions.add((questions.size() + 1) + ". **Duration** — How many **nights or days** are you planning?");
        }
        if (missing.contains("budget")) {
            questions.add((questions.size() + 1) + ". **Budget & Currency** — What is your approximate total budget and home currency (e.g., `$2,200 USD` or `€2,000 EUR`)?");
        }
        if (missing.contains("travelFocus")) {
            questions.add((questions.size() + 1) + ". **Travel Focus** — What interests should anchor the itinerary (e.g., *architecture, local food, jazz/vinyl, museums, nature*)?");
        }

        String deterministicAsk = "I would love to curate **" + cityLabel + "** without assuming any defaults. "
                + "Before I query live weather, currency rates, and cultural venues, please share:\n\n"
                + String.join("\n", questions);

        String systemPrompt = "You are Nomad, a precise travel concierge chatbot. The traveler wants to plan a trip"
                + (slots.hasCity() ? " to " + slots.city() : "")
                + ", and we NEVER assume default days, budgets, or interests. "
                + "Known parameters so far: " + slots.toMap() + ". "
                + "Politely and concisely ask the traveler ONLY for these missing required details before building the itinerary: "
                + String.join(", ", missing) + ". Do not invent weather, prices, or a 3-day itinerary yet.";

        String reply = streamAssistantText(emitter, threadId, llmMode, systemPrompt, prompt, deterministicAsk);
        appendMemoryTurn(threadId, "assistant", reply);
        emitCompletedRun(emitter, threadId, llmMode);
    }

    private void executeCompleteTripPlan(
            MultiEmitter<? super String> emitter,
            String threadId,
            String prompt,
            TripSlotState slots,
            String llmMode) {
        String city = slots.city();
        int nights = slots.nights();
        double budget = slots.budget();
        String homeCurrency = slots.homeCurrency();
        String focus = slots.travelFocus();

        // 1. Native MCP Prompt + Resource read (:8081/mcp)
        Map<String, Object> promptTpl = mcpClient.getPromptTemplate("weekend-cultural-escape", Map.of("city", city));
        emitProtocolStep(emitter, threadId, "mcp:prompt", "weekend-cultural-escape", promptTpl);

        Map<String, Object> prefsRes = mcpClient.readResource("traveler://preferences");
        emitProtocolStep(emitter, threadId, "mcp:resource", "traveler://preferences", prefsRes);

        // 2. Native MCP Tools (:8081/mcp)
        Map<String, Object> weatherCall = emitMcpToolCall(emitter, threadId, "fetchCityWeather", Map.of("city", city));
        Map<String, Object> cultureCall = emitMcpToolCall(
                emitter, threadId, "discoverLocalCultureAndMedia", Map.of("destination", city, "genre", focus));
        String stopTitle = McpGatewayClient.defaultSanctuaryForCity(city);
        double allocated = Math.round(budget * 0.52 * 100.0) / 100.0;
        emitMcpToolCall(emitter, threadId, "updateTripItinerary", Map.of(
                "city", city,
                "stopTitle", stopTitle,
                "nights", nights,
                "costUsd", allocated));

        String condition = extractNestedString(weatherCall, "result", "condition");
        Map<String, Object> samplingRes = mcpClient.sampleAcousticMood(city, condition, focus);
        emitProtocolStep(emitter, threadId, "tool:function", "sampleAcousticMoodProfile", samplingRes);

        // 3. Official A2A Specialist Delegation (:8082)
        emitProtocolStep(emitter, threadId, "a2a:discovery", "Nomad-A2A-Specialist", a2aClient.getAgentCard());
        Map<String, Object> fxHandoff = emitA2aSkillCall(
                emitter,
                threadId,
                "convert-and-audit-budget",
                "Convert " + budget + " " + homeCurrency + " budget for " + city,
                Map.of("city", city, "budget", budget, "budgetUsd", budget, "baseCurrency", homeCurrency));
        Map<String, Object> advisoryHandoff = emitA2aSkillCall(
                emitter,
                threadId,
                "verify-travel-advisory",
                "Verify travel advisory for " + city,
                Map.of("city", city));
        Map<String, Object> cadenceHandoff = emitA2aSkillCall(
                emitter,
                threadId,
                "curate-neighborhood-cadence",
                "Curate " + nights + "-night " + focus + " cadence for " + city,
                Map.of("city", city, "nights", nights, "focus", focus));
        emitProtocolStep(emitter, threadId, "a2a:artifact", "neighborhood-cadence-" + city.toLowerCase(Locale.ROOT), cadenceHandoff);

        // Update active trip dossier with real FX if available
        Map<String, Object> tripUpdates = new LinkedHashMap<>();
        tripUpdates.put("nights", nights);
        tripUpdates.put("budgetUsd", budget);
        tripUpdates.put("homeCurrency", homeCurrency);
        if (!Boolean.TRUE.equals(fxHandoff.get("isError")) && fxHandoff.get("report") instanceof Map<?, ?> fxReport) {
            if (fxReport.get("targetCurrency") != null) {
                tripUpdates.put("currency", fxReport.get("targetCurrency"));
            }
            if (fxReport.get("exchangeRate") != null) {
                tripUpdates.put("exchangeRate", fxReport.get("exchangeRate"));
            }
        }
        mcpClient.updateActiveTripFields(tripUpdates);

        // 4. Emit Media, Sources (marking failed APIs as error!), and Updated Trip Dossier State
        emitProtocolStep(emitter, threadId, "tool:ui", "renderTripDossierMedia", Map.of("city", city, "nights", nights));
        Map<String, Object> finalDossier = mcpClient.readTripDossierState();
        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("awaitingTripSlots", false);
        delta.put("missingSlots", List.of());
        delta.put("tripSlots", slots.toMap());
        delta.put("tripDossierState", finalDossier);
        delta.put("clusterState", finalDossier);
        List<Map<String, Object>> media = buildMediaPayload(city, cultureCall);
        if (!media.isEmpty()) {
            delta.put("media", media);
        }
        delta.put("sources", buildVerifiedAndFailedSources(city, weatherCall, cultureCall, fxHandoff, advisoryHandoff));
        delta.put("followUps", List.of(
                "Give me two evening options in " + city + " to pick from",
                "Reserve " + stopTitle + " in " + city + " for " + nights + " nights ($1840)",
                "What's the weather in " + city + " right now?"));
        emitFrame(emitter, threadId, "STATE_DELTA", delta);

        // 5. Stream Grounded Travel Concierge Response (honestly surfacing any upstream errors!)
        List<String> errorWarnings = collectUpstreamWarnings(weatherCall, cultureCall, fxHandoff, advisoryHandoff);
        String deterministicReply = formatDeterministicTripSummary(slots, weatherCall, cultureCall, fxHandoff, errorWarnings);
        String systemPrompt = buildTripSynthesisSystemPrompt(slots, weatherCall, cultureCall, fxHandoff, errorWarnings);
        String streamed = streamAssistantText(emitter, threadId, llmMode, systemPrompt, prompt, deterministicReply);
        if (!errorWarnings.isEmpty() && !streamed.contains("Unavailable")) {
            String warningSuffix = "\n\n> " + String.join("\n> ", errorWarnings);
            emitTextDelta(emitter, threadId, warningSuffix);
            streamed = streamed + warningSuffix;
        }
        appendMemoryTurn(threadId, "assistant", streamed);
        emitCompletedRun(emitter, threadId, llmMode);
    }

    private void handleWeatherOnlyTurn(
            MultiEmitter<? super String> emitter,
            String threadId,
            String prompt,
            TripSlotState slots,
            String llmMode) {
        if (!slots.hasCity()) {
            emitAskForCityOnly(emitter, threadId, prompt, "check live Open-Meteo weather", llmMode);
            return;
        }
        String city = slots.city();
        Map<String, Object> weatherCall = emitMcpToolCall(emitter, threadId, "fetchCityWeather", Map.of("city", city));
        Map<String, Object> dossier = mcpClient.readTripDossierState();
        emitFrame(emitter, threadId, "STATE_DELTA", Map.of(
                "tripDossierState", dossier,
                "clusterState", dossier,
                "followUps", List.of(
                        "I want a trip in " + city,
                        "Check the travel advisory for " + city,
                        "Play local music from " + city)));

        boolean isErr = Boolean.TRUE.equals(weatherCall.get("isError"));
        String fallback = isErr
                ? "⚠️ **Weather Service Unavailable**: " + weatherCall.get("error")
                : String.format(
                        Locale.ROOT,
                        "### Meteorological Brief — %s\nLive Open-Meteo telemetry reports **%s°C** (%s).",
                        city,
                        extractNestedString(weatherCall, "result", "temperatureC"),
                        extractNestedString(weatherCall, "result", "condition"));
        String sys = "You are Nomad, a concise travel concierge. Answer the user's weather question for " + city
                + " using ONLY this live tool output (if status is ERROR, tell the user the weather service is unavailable): "
                + weatherCall;
        String text = streamAssistantText(emitter, threadId, llmMode, sys, prompt, fallback);
        appendMemoryTurn(threadId, "assistant", text);
        emitCompletedRun(emitter, threadId, llmMode);
    }

    private void handleAdvisoryOnlyTurn(
            MultiEmitter<? super String> emitter,
            String threadId,
            String prompt,
            TripSlotState slots,
            String llmMode) {
        if (!slots.hasCity()) {
            emitAskForCityOnly(emitter, threadId, prompt, "verify the destination travel advisory", llmMode);
            return;
        }
        String city = slots.city();
        Map<String, Object> advisoryHandoff = emitA2aSkillCall(
                emitter, threadId, "verify-travel-advisory", "Verify travel advisory for " + city, Map.of("city", city));
        boolean isErr = Boolean.TRUE.equals(advisoryHandoff.get("isError"));
        String country = A2aGatewayClient.resolveCountryForCity(city);
        emitFrame(emitter, threadId, "STATE_DELTA", Map.of(
                "sources", List.of(Map.of(
                        "title", "REST Countries Advisory — " + country,
                        "url", "https://restcountries.com/v3.1/name/" + URLEncoder.encode(country, StandardCharsets.UTF_8),
                        "status", isErr ? "error" : "verified",
                        "error", isErr ? String.valueOf(advisoryHandoff.getOrDefault("error", "Service unavailable")) : ""))));

        String fallback = isErr
                ? "⚠️ **" + advisoryHandoff.getOrDefault("error", advisoryHandoff.get("summary")) + "**"
                : String.valueOf(advisoryHandoff.get("summary"));
        String sys = "You are Nomad. Report the exact travel advisory status for " + city + ". "
                + "IMPORTANT: If status is ERROR, tell the user clearly that the Travel Advisory service (restcountries.com) is currently not working and state the reason—do NOT fabricate an advisory: "
                + advisoryHandoff;
        String text = streamAssistantText(emitter, threadId, llmMode, sys, prompt, fallback);
        if (isErr && !text.contains("Unavailable") && !text.contains("deprecated")) {
            String suffix = "\n\n> ⚠️ " + advisoryHandoff.getOrDefault("error", "Travel Advisory Service Unavailable");
            emitTextDelta(emitter, threadId, suffix);
            text = text + suffix;
        }
        appendMemoryTurn(threadId, "assistant", text);
        emitCompletedRun(emitter, threadId, llmMode);
    }

    private void handleCurrencyOnlyTurn(
            MultiEmitter<? super String> emitter,
            String threadId,
            String prompt,
            TripSlotState slots,
            String llmMode) {
        if (!slots.hasCity() || !slots.hasBudget()) {
            String ask = "To run a live **Frankfurter ECB currency & budget check**, please specify the **destination city**, **amount**, and **currency** (for example: *\"Convert $2,000 USD for Tokyo\"*).";
            emitTextDelta(emitter, threadId, ask);
            emitCompletedRun(emitter, threadId, llmMode);
            return;
        }
        Map<String, Object> fxHandoff = emitA2aSkillCall(
                emitter,
                threadId,
                "convert-and-audit-budget",
                prompt,
                Map.of(
                        "city", slots.city(),
                        "budget", slots.budget(),
                        "budgetUsd", slots.budget(),
                        "baseCurrency", slots.homeCurrency()));
        boolean isErr = Boolean.TRUE.equals(fxHandoff.get("isError"));
        String fallback = isErr
                ? "⚠️ **Currency Conversion Service Unavailable**: " + fxHandoff.get("error")
                : String.valueOf(fxHandoff.get("summary"));
        String sys = "You are Nomad. Report this exact Frankfurter FX conversion result (or surface the error if status is ERROR): " + fxHandoff;
        String text = streamAssistantText(emitter, threadId, llmMode, sys, prompt, fallback);
        appendMemoryTurn(threadId, "assistant", text);
        emitCompletedRun(emitter, threadId, llmMode);
    }

    private void handleCultureOnlyTurn(
            MultiEmitter<? super String> emitter,
            String threadId,
            String prompt,
            TripSlotState slots,
            String llmMode) {
        if (!slots.hasCity()) {
            emitAskForCityOnly(emitter, threadId, prompt, "discover local music & cultural landmarks", llmMode);
            return;
        }
        String city = slots.city();
        String genre = slots.hasFocus() ? slots.travelFocus() : "regional acoustic";
        Map<String, Object> cultureCall = emitMcpToolCall(
                emitter, threadId, "discoverLocalCultureAndMedia", Map.of("destination", city, "genre", genre));
        mcpClient.sampleAcousticMood(city, null, genre);
        Map<String, Object> dossier = mcpClient.readTripDossierState();
        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("tripDossierState", dossier);
        delta.put("clusterState", dossier);
        List<Map<String, Object>> media = buildMediaPayload(city, cultureCall);
        if (!media.isEmpty()) {
            delta.put("media", media);
        }
        emitFrame(emitter, threadId, "STATE_DELTA", delta);

        boolean isErr = Boolean.TRUE.equals(cultureCall.get("isError"));
        String fallback = isErr
                ? "⚠️ **Cultural & Media Service Unavailable**: " + cultureCall.get("error")
                : "### Cultural & Acoustic Spotlight — " + city + "\n"
                        + extractNestedString(cultureCall, "result", "culturalSummary");
        String sys = "You are Nomad. Highlight the cultural landmarks and playable 30s music track for " + city
                + " based on this tool result (or report the service error if status is ERROR): " + cultureCall;
        String text = streamAssistantText(emitter, threadId, llmMode, sys, prompt, fallback);
        appendMemoryTurn(threadId, "assistant", text);
        emitCompletedRun(emitter, threadId, llmMode);
    }

    private void handleChoiceCurationTurn(
            MultiEmitter<? super String> emitter,
            String threadId,
            String prompt,
            TripSlotState slots,
            String llmMode) {
        if (!slots.hasCity()) {
            emitAskForCityOnly(emitter, threadId, prompt, "curate neighborhood options to choose from", llmMode);
            return;
        }
        String city = slots.city();
        boolean multi = prompt.toLowerCase(Locale.ROOT).contains("multi");
        int count = extractRequestedOptionCount(prompt);
        Map<String, Object> interaction = generateChoiceInteraction(city, multi, count, prompt, llmMode);
        emitFrame(emitter, threadId, "STATE_DELTA", Map.of("interaction", interaction));
        String msg = "Here are **" + count + " curated options in " + city + "**—select your preference below:";
        emitTextDelta(emitter, threadId, msg);
        appendMemoryTurn(threadId, "assistant", msg);
        emitCompletedRun(emitter, threadId, llmMode);
    }

    private void handleHitlReservationTurn(
            MultiEmitter<? super String> emitter,
            String threadId,
            String prompt,
            TripSlotState slots,
            String llmMode) {
        if (!slots.hasCity() || !slots.hasDuration()) {
            String ask = "Before holding a reservation, please confirm the **destination city**, **number of nights**, and **estimated total cost** (e.g., *\"Reserve Cour des Vosges in Paris for 4 nights ($1,840)\"*).";
            emitTextDelta(emitter, threadId, ask);
            emitCompletedRun(emitter, threadId, llmMode);
            return;
        }
        String city = slots.city();
        int nights = slots.nights();
        double costUsd = slots.budget() != null ? slots.budget() : 1840.0;
        String stopTitle = extractStopTitle(prompt, city);

        if (costUsd >= HITL_THRESHOLD_USD || prompt.toLowerCase(Locale.ROOT).startsWith("/hitl")) {
            String callId = "hitl-" + UUID.randomUUID().toString().substring(0, 8);
            PendingHitlReservation pending = new PendingHitlReservation(threadId, city, stopTitle, nights, costUsd, llmMode);
            pendingApprovals.put(threadId, pending);

            Map<String, Object> approvalArgs = Map.of(
                    "city", city,
                    "stopTitle", stopTitle,
                    "nights", nights,
                    "costUsd", costUsd);
            Map<String, Object> approvalPayload = new LinkedHashMap<>();
            approvalPayload.put("callId", callId);
            approvalPayload.put("callType", "hitl:approval");
            approvalPayload.put("tool", "updateTripItinerary");
            approvalPayload.put("toolName", "updateTripItinerary");
            approvalPayload.put("requiresApproval", true);
            approvalPayload.put("args", approvalArgs);
            approvalPayload.put("city", city);
            approvalPayload.put("stopTitle", stopTitle);
            approvalPayload.put("nights", nights);
            approvalPayload.put("costUsd", costUsd);
            approvalPayload.put("thresholdUsd", HITL_THRESHOLD_USD);
            emitFrame(emitter, threadId, "TOOL_CALL", approvalPayload);
            emitFrame(emitter, threadId, "STATE_DELTA", Map.of("pendingApproval", approvalPayload));
            emitTextDelta(emitter, threadId, String.format(
                    Locale.ROOT,
                    "Hold paused for **%s** (%s, %d nights • **$%,.0f USD** exceeds the $1,500 HITL threshold). Please approve or adjust the parameters to confirm.",
                    stopTitle, city, nights, costUsd));
            emitFrame(emitter, threadId, "RUN_FINISHED", Map.of(
                    "threadId", threadId,
                    "status", "interrupted",
                    "approvalRequired", true,
                    "pendingAction", approvalPayload));
            return;
        }

        emitMcpToolCall(emitter, threadId, "updateTripItinerary", Map.of(
                "city", city, "stopTitle", stopTitle, "nights", nights, "costUsd", costUsd));
        Map<String, Object> dossier = mcpClient.readTripDossierState();
        emitFrame(emitter, threadId, "STATE_DELTA", Map.of("tripDossierState", dossier, "clusterState", dossier));
        emitTextDelta(emitter, threadId, "Confirmed itinerary stop **" + stopTitle + "** in " + city + ".");
        emitCompletedRun(emitter, threadId, llmMode);
    }

    public Multi<String> approvePendingAction(AgentApprovalRequest approval) {
        return Multi.createFrom().<String>emitter(emitter -> {
            String threadId = approval == null ? "thread-default" : normalizeThreadId(approval.threadId());
            PendingHitlReservation pending = pendingApprovals.remove(threadId);
            if (approval == null || !approval.approved() || pending == null) {
                emitFrame(emitter, threadId, "STATE_DELTA", Map.of("pendingApproval", Map.of()));
                emitFrame(emitter, threadId, "RUN_FINISHED", Map.of(
                        "threadId", threadId,
                        "status", "cancelled",
                        "reason", approval == null || approval.reason() == null ? "Declined by traveler" : approval.reason()));
                emitter.complete();
                return;
            }

            Map<String, Object> mod = approval.modifiedArgs() == null ? Map.of() : approval.modifiedArgs();
            String city = mod.get("city") instanceof String c && !c.isBlank() ? c : pending.city();
            String stopTitle = mod.get("stopTitle") instanceof String s && !s.isBlank() ? s : pending.stopTitle();
            int nights = mod.get("nights") instanceof Number n ? n.intValue() : pending.nights();
            double costUsd = mod.get("costUsd") instanceof Number n ? n.doubleValue() : pending.costUsd();

            emitMcpToolCall(emitter, threadId, "updateTripItinerary", Map.of(
                    "city", city, "stopTitle", stopTitle, "nights", nights, "costUsd", costUsd));
            Map<String, Object> dossier = mcpClient.readTripDossierState();
            emitFrame(emitter, threadId, "STATE_DELTA", Map.of(
                    "pendingApproval", Map.of(),
                    "tripDossierState", dossier,
                    "clusterState", dossier));
            emitTextDelta(emitter, threadId, String.format(
                    Locale.ROOT,
                    "Reservation approved: **%s** in **%s** confirmed for **%d nights** ($%,.0f USD).",
                    stopTitle, city, nights, costUsd));
            emitFrame(emitter, threadId, "RUN_FINISHED", Map.of("threadId", threadId, "status", "completed"));
            emitter.complete();
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    // --- Pure Domain Intent & Slot Extraction Helpers ---

    static TravelIntent classifyIntent(String prompt, TripSlotState slots, boolean wasAwaitingSlots) {
        String raw = prompt == null ? "" : prompt.trim();
        String lower = raw.toLowerCase(Locale.ROOT);

        if (lower.startsWith("/hitl") || ((lower.contains("reserve ") || lower.contains("book ")) && slots.hasCity())) {
            return TravelIntent.HITL_RESERVATION;
        }
        if (lower.startsWith("/choices")
                || lower.contains("pick from")
                || lower.contains("select multiple")
                || (lower.contains("options") && slots.hasCity())) {
            return TravelIntent.CHOICE_CURATION;
        }
        if (wasAwaitingSlots && (slots.hasDuration() || slots.hasBudget() || slots.hasFocus())) {
            return TravelIntent.TRIP_PLANNING;
        }

        boolean mentionsTrip = lower.contains("trip")
                || lower.contains("itinerary")
                || lower.contains("plan ")
                || lower.contains("curate")
                || lower.contains("escape")
                || lower.contains("weekend in")
                || lower.contains("visit ")
                || lower.contains("explore ")
                || lower.startsWith("/kyoto")
                || lower.startsWith("/lisbon")
                || lower.startsWith("/reykjavik");

        if (!mentionsTrip) {
            if (lower.contains("advisory") || lower.contains("visa") || lower.contains("entry requirement")) {
                return TravelIntent.TRAVEL_ADVISORY;
            }
            if (lower.contains("weather") || lower.contains("temperature") || lower.contains("forecast") || lower.contains("pack for")) {
                return TravelIntent.WEATHER_CHECK;
            }
            if (lower.contains("convert ") || lower.contains("exchange rate") || lower.contains("fx ")) {
                return TravelIntent.CURRENCY_AND_BUDGET;
            }
            if (lower.contains("play ") || lower.contains("soundtrack") || lower.contains("listen") || lower.contains("music from")) {
                return TravelIntent.CULTURE_AND_MUSIC;
            }
        }

        if (mentionsTrip || (slots.hasCity() && (slots.hasDuration() || slots.hasBudget() || slots.hasFocus()))) {
            return TravelIntent.TRIP_PLANNING;
        }
        return TravelIntent.GENERAL_CHAT_OR_PROFILE;
    }

    TripSlotState extractSlotsFromTurn(String prompt, Map<String, Object> reqState) {
        String city = A2aGatewayClient.resolveNomadCityFromPrompt(prompt);
        if ((city == null || city.isBlank()) && reqState != null && reqState.get("city") instanceof String sc && !sc.isBlank()) {
            city = McpGatewayClient.normalizeCity(sc);
        }
        Integer nights = extractDurationNights(prompt, reqState);
        BudgetPair budgetPair = extractBudgetAndCurrency(prompt, reqState);
        String focus = extractTravelFocus(prompt);
        return new TripSlotState(city, nights, budgetPair.amount(), budgetPair.currency(), focus);
    }

    private record BudgetPair(Double amount, String currency) {}

    private Integer extractDurationNights(String prompt, Map<String, Object> reqState) {
        if (prompt != null && !prompt.isBlank()) {
            String lower = prompt.toLowerCase(Locale.ROOT);
            Matcher digitMatch = Pattern.compile("\\b(\\d{1,2})\\s*[- ]?(?:night|nights|day|days)\\b").matcher(lower);
            if (digitMatch.find()) {
                return Integer.parseInt(digitMatch.group(1));
            }
            for (Map.Entry<String, Integer> entry : WORD_NUMBERS.entrySet()) {
                if (Pattern.compile("\\b" + entry.getKey() + "\\s*[- ]?(?:night|nights|day|days)\\b").matcher(lower).find()) {
                    return entry.getValue();
                }
            }
            if (lower.contains("a week") || lower.contains("one week")) {
                return 7;
            }
            if (lower.contains("weekend")) {
                return 2;
            }
        }
        if (reqState != null && reqState.get("nights") instanceof Number n && n.intValue() > 0) {
            return n.intValue();
        }
        return null;
    }

    private BudgetPair extractBudgetAndCurrency(String prompt, Map<String, Object> reqState) {
        if (prompt != null && !prompt.isBlank()) {
            Matcher symBefore = Pattern.compile("([$€£¥])\\s*([0-9]{1,7}(?:,[0-9]{3})*(?:\\.[0-9]+)?)\\s*([A-Za-z]{3})?").matcher(prompt);
            if (symBefore.find()) {
                double val = Double.parseDouble(symBefore.group(2).replace(",", ""));
                String explicitCode = symBefore.group(3);
                String code = (explicitCode != null && !explicitCode.isBlank())
                        ? explicitCode.toUpperCase(Locale.ROOT)
                        : symbolToCurrency(symBefore.group(1));
                return new BudgetPair(val, code);
            }
            Matcher codeAfter = Pattern.compile("\\b([0-9]{1,7}(?:,[0-9]{3})*(?:\\.[0-9]+)?)\\s*(USD|EUR|GBP|JPY|CHF|CAD|AUD|ISK|MXN|LBP)\\b", Pattern.CASE_INSENSITIVE)
                    .matcher(prompt);
            if (codeAfter.find()) {
                double val = Double.parseDouble(codeAfter.group(1).replace(",", ""));
                return new BudgetPair(val, codeAfter.group(2).toUpperCase(Locale.ROOT));
            }
            Matcher codeBefore = Pattern.compile("\\b(USD|EUR|GBP|JPY|CHF|CAD|AUD|ISK|MXN|LBP)\\s*([0-9]{1,7}(?:,[0-9]{3})*(?:\\.[0-9]+)?)\\b", Pattern.CASE_INSENSITIVE)
                    .matcher(prompt);
            if (codeBefore.find()) {
                double val = Double.parseDouble(codeBefore.group(2).replace(",", ""));
                return new BudgetPair(val, codeBefore.group(1).toUpperCase(Locale.ROOT));
            }
        }
        if (reqState != null && reqState.get("budget") instanceof Number b && reqState.get("homeCurrency") instanceof String hc) {
            return new BudgetPair(b.doubleValue(), hc.trim().toUpperCase(Locale.ROOT));
        }
        return new BudgetPair(null, null);
    }

    private static String symbolToCurrency(String symbol) {
        return switch (symbol) {
            case "€" -> "EUR";
            case "£" -> "GBP";
            case "¥" -> "JPY";
            default -> "USD";
        };
    }

    private String extractTravelFocus(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return null;
        }
        String lower = prompt.toLowerCase(Locale.ROOT);
        Set<String> matched = new LinkedHashSet<>();
        for (String kw : FOCUS_KEYWORDS) {
            if (lower.contains(kw)) {
                matched.add(kw);
            }
        }
        return matched.isEmpty() ? null : String.join(", ", matched);
    }

    public void extractAndApplyTravelerPreferences(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return;
        }
        Map<String, Object> updates = new LinkedHashMap<>();
        Matcher nameMatcher = Pattern.compile("(?:my name is|i am|i'm)\\s+([A-Z][a-zA-Z]{1,24})\\b", Pattern.CASE_INSENSITIVE).matcher(prompt);
        if (nameMatcher.find()) {
            String candidate = nameMatcher.group(1);
            if (!Set.of("vegan", "vegetarian", "planning", "looking", "interested", "going", "traveling", "visiting")
                    .contains(candidate.toLowerCase(Locale.ROOT))) {
                updates.put("travelerName", Character.toUpperCase(candidate.charAt(0)) + candidate.substring(1));
            }
        }
        String lower = prompt.toLowerCase(Locale.ROOT);
        if (lower.contains("vegan")) {
            updates.put("dietary", "Plant-Based Vegan Terroir");
        } else if (lower.contains("vegetarian")) {
            updates.put("dietary", "Seasonal Vegetarian");
        } else if (lower.contains("halal")) {
            updates.put("dietary", "Halal Local Cuisine");
        } else if (lower.contains("gluten-free")) {
            updates.put("dietary", "Gluten-Free Terroir");
        }
        if (lower.contains("brutalist") || lower.contains("tadao ando") || lower.contains("concrete")) {
            updates.put("style", "Modernist & Brutalist Architecture");
        } else if (lower.contains("machiya") || lower.contains("timber")) {
            updates.put("style", "Traditional Timber & Craft");
        } else if (lower.contains("azulejo")) {
            updates.put("style", "Historic Tilework & Courtyards");
        }
        if (lower.contains("vinyl") || lower.contains("jazz")) {
            updates.put("musicTaste", "Analog Vinyl & Jazz Listening Bars");
        } else if (lower.contains("fado")) {
            updates.put("musicTaste", "Acoustic Fado & Guitarra");
        } else if (lower.contains("oud")) {
            updates.put("musicTaste", "Levantine Oud & Chamber");
        }
        if (lower.contains("unrushed") || lower.contains("slow")) {
            updates.put("pace", "Unrushed (2 curated stops per day)");
        } else if (lower.contains("packed")) {
            updates.put("pace", "High-Energy Explorer");
        }
        for (String code : List.of("EUR", "GBP", "CHF", "USD", "CAD", "JPY")) {
            if (Pattern.compile("\\b" + code + "\\b").matcher(prompt).find()) {
                updates.put("homeCurrency", code);
                break;
            }
        }
        if (!updates.isEmpty()) {
            mcpClient.updateTravelerPreferences(updates);
        }
    }

    public static int extractRequestedOptionCount(String prompt) {
        if (prompt == null) {
            return 3;
        }
        String lower = prompt.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Integer> e : WORD_NUMBERS.entrySet()) {
            if (lower.contains(e.getKey() + " ")) {
                return Math.min(6, Math.max(2, e.getValue()));
            }
        }
        Matcher m = Pattern.compile("\\b([2-6])\\b").matcher(lower);
        return m.find() ? Integer.parseInt(m.group(1)) : 3;
    }

    // --- Protocol Frame Emission & Ollama Streaming ---

    private Map<String, Object> emitMcpToolCall(
            MultiEmitter<? super String> emitter,
            String threadId,
            String toolName,
            Map<String, Object> args) {
        String callId = "mcp-" + UUID.randomUUID().toString().substring(0, 8);
        emitFrame(emitter, threadId, "TOOL_CALL", Map.of(
                "callId", callId,
                "toolCallId", callId,
                "protocol", "MCP",
                "callType", "mcp:tool",
                "server", "nomad-mcp-concierge (:8081)",
                "tool", toolName,
                "toolName", toolName,
                "args", args,
                "arguments", args));

        Map<String, Object> outcome = mcpClient.callTool(toolName, args);
        boolean isErr = Boolean.TRUE.equals(outcome.get("isError"));
        Map<String, Object> resultFrame = new LinkedHashMap<>(outcome);
        resultFrame.put("callId", callId);
        resultFrame.put("toolCallId", callId);
        resultFrame.put("tool", toolName);
        resultFrame.put("toolName", toolName);
        resultFrame.put("callType", "mcp:tool");
        resultFrame.put("status", isErr ? "ERROR" : "COMPLETED");
        resultFrame.put("isError", isErr);
        emitFrame(emitter, threadId, "TOOL_RESULT", resultFrame);
        return outcome;
    }

    private Map<String, Object> emitA2aSkillCall(
            MultiEmitter<? super String> emitter,
            String threadId,
            String skillId,
            String taskPrompt,
            Map<String, Object> ctx) {
        String taskId = "a2a-" + UUID.randomUUID().toString().substring(0, 8);
        emitFrame(emitter, threadId, "STEP_STARTED", Map.of(
                "stepId", taskId,
                "protocol", "A2A",
                "callType", "a2a:task",
                "skillId", skillId,
                "agentName", "Nomad-A2A-Specialist"));

        Map<String, Object> handoff = a2aClient.delegateTask(taskId, skillId, taskPrompt, ctx);
        boolean isErr = Boolean.TRUE.equals(handoff.get("isError"));
        Map<String, Object> resultFrame = new LinkedHashMap<>(handoff);
        resultFrame.put("stepId", taskId);
        resultFrame.put("protocol", "A2A");
        resultFrame.put("callType", "a2a:task");
        resultFrame.put("skillId", skillId);
        resultFrame.put("agentName", "Nomad-A2A-Specialist");
        resultFrame.put("status", isErr ? "ERROR" : "COMPLETED");
        resultFrame.put("isError", isErr);
        emitFrame(emitter, threadId, "STEP_FINISHED", resultFrame);
        return handoff;
    }

    private void emitProtocolStep(
            MultiEmitter<? super String> emitter,
            String threadId,
            String callType,
            String label,
            Map<String, Object> result) {
        String stepId = "step-" + UUID.randomUUID().toString().substring(0, 8);
        boolean isErr = Boolean.TRUE.equals(result.get("isError")) || "ERROR".equals(result.get("status"));
        String agentLabel = callType.startsWith("mcp:") ? "nomad-mcp-concierge" : "Nomad-Concierge-Orchestrator";
        emitFrame(emitter, threadId, "STEP_STARTED", Map.of(
                "stepId", stepId, "callType", callType, "skillId", label, "label", label, "agentName", agentLabel));
        Map<String, Object> finished = new LinkedHashMap<>();
        finished.put("stepId", stepId);
        finished.put("callType", callType);
        finished.put("skillId", label);
        finished.put("label", label);
        finished.put("agentName", agentLabel);
        finished.put("status", isErr ? "ERROR" : "COMPLETED");
        finished.put("isError", isErr);
        finished.put("summary", isErr
                ? String.valueOf(result.getOrDefault("error", label + " failed"))
                : String.valueOf(result.getOrDefault("expandedPrompt", result.getOrDefault("sampledMood", label + " verified"))));
        finished.put("report", result);
        finished.put("result", result);
        if (isErr && result.get("error") != null) {
            finished.put("error", result.get("error"));
        }
        emitFrame(emitter, threadId, "STEP_FINISHED", finished);
    }

    private void emitReasoningStep(
            MultiEmitter<? super String> emitter,
            String threadId,
            TravelIntent intent,
            TripSlotState slots) {
        emitFrame(emitter, threadId, "STATUS", Map.of(
                "phase", "reasoning",
                "durationMs", 420,
                "intent", intent.name(),
                "tripSlots", slots.toMap(),
                "thought", "Classified travel intent as " + intent.name() + " with active slots " + slots.toMap(),
                "message", "Routing travel intent (" + intent.name() + ")..."));
    }

    private String streamAssistantText(
            MultiEmitter<? super String> emitter,
            String threadId,
            String llmMode,
            String systemPrompt,
            String userPrompt,
            String fallbackText) {
        if (!llmMode.startsWith("ollama")) {
            emitTextDelta(emitter, threadId, fallbackText);
            return fallbackText;
        }
        String modelName = llmMode.contains(":") && llmMode.length() > "ollama:".length()
                ? llmMode.substring("ollama:".length())
                : defaultOllamaModel;
        StringBuilder accumulated = new StringBuilder();
        try {
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            for (MemoryTurn turn : getConversationMemory(threadId)) {
                messages.add(Map.of("role", turn.role(), "content", turn.content()));
            }
            if (messages.size() == 1) {
                messages.add(Map.of("role", "user", "content", userPrompt));
            }
            String reqJson = objectMapper.writeValueAsString(Map.of(
                    "model", modelName,
                    "stream", true,
                    "messages", messages));
            HttpRequest req = HttpRequest.newBuilder(URI.create(ollamaBaseUrl + "/api/chat"))
                    .timeout(Duration.ofSeconds(25))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(reqJson))
                    .build();
            HttpResponse< java.io.InputStream > res = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (res.statusCode() >= 200 && res.statusCode() < 300) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(res.body(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isBlank()) {
                            continue;
                        }
                        JsonNode chunk = objectMapper.readTree(line);
                        String token = chunk.path("message").path("content").asText("");
                        if (!token.isEmpty()) {
                            accumulated.append(token);
                            emitTextDelta(emitter, threadId, token);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        if (accumulated.isEmpty()) {
            emitTextDelta(emitter, threadId, fallbackText);
            return fallbackText;
        }
        return accumulated.toString();
    }

    private Map<String, Object> generateChoiceInteraction(
            String city, boolean multi, int count, String prompt, String llmMode) {
        if (llmMode.startsWith("ollama")) {
            try {
                String modelName = llmMode.contains(":") && llmMode.length() > 7 ? llmMode.substring(7) : defaultOllamaModel;
                String reqJson = objectMapper.writeValueAsString(Map.of(
                        "model", modelName,
                        "stream", false,
                        "format", "json",
                        "messages", List.of(
                                Map.of("role", "system", "content", "Return JSON {\"prompt\":\"...\",\"mode\":\"" + (multi ? "multi_select" : "single_select") + "\",\"options\":[{\"id\":\"...\",\"label\":\"...\",\"description\":\"...\",\"badge\":\"...\"}]} with " + count + " options in " + city + "."),
                                Map.of("role", "user", "content", prompt))));
                HttpRequest req = HttpRequest.newBuilder(URI.create(ollamaBaseUrl + "/api/chat"))
                        .timeout(Duration.ofSeconds(12))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(reqJson))
                        .build();
                HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 200 && res.body() != null) {
                    String content = objectMapper.readTree(res.body()).path("message").path("content").asText("");
                    if (!content.isBlank()) {
                        Map<String, Object> parsed = objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {});
                        parsed.putIfAbsent("id", "choice-" + city.toLowerCase(Locale.ROOT));
                        return parsed;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        List<Map<String, Object>> options = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            options.add(Map.of(
                    "id", city.toLowerCase(Locale.ROOT) + "-opt-" + i,
                    "label", city + " Curated Experience #" + i,
                    "description", "Neighborhood architectural & local acoustic stop in " + city,
                    "badge", "Stop " + i));
        }
        return Map.of(
                "id", "choice-" + city.toLowerCase(Locale.ROOT),
                "mode", multi ? "multi_select" : "single_select",
                "prompt", "Choose your preferred experience in " + city + ":",
                "options", options);
    }

    private Map<String, Object> buildSlotElicitationInteraction(TripSlotState slots, List<String> missing) {
        String city = slots.hasCity() ? slots.city() : "your destination";
        List<Map<String, Object>> quickTemplates = List.of(
                Map.of(
                        "id", "slot-preset-weekend",
                        "label", "3 nights • $1,800 USD • Architecture & Local Food",
                        "description", "Complete all required parameters for a 3-night architectural & culinary stay in " + city,
                        "badge", "3 Nights • USD",
                        "prompt", (slots.hasCity() ? "Plan a 3-night architecture and culinary trip in " + city + " with $1,800 USD" : "Plan a 3-night architecture and culinary trip in Lisbon with $1,800 USD")),
                Map.of(
                        "id", "slot-preset-immersive",
                        "label", "5 nights • €2,500 EUR • Architecture, Museums & Jazz",
                        "description", "Complete all required parameters for an unrushed 5-night cultural & music trip in " + city,
                        "badge", "5 Nights • EUR",
                        "prompt", (slots.hasCity() ? "5 nights, €2,500 EUR, architecture, museums and jazz in " + city : "Plan a 5-night architecture and jazz trip in Paris with €2,500 EUR")),
                Map.of(
                        "id", "slot-preset-extended",
                        "label", "7 nights • $3,800 USD • Heritage, Nature & Vinyl",
                        "description", "Full-week immersion with neighborhood walks and evening listening sessions in " + city,
                        "badge", "7 Nights • USD",
                        "prompt", (slots.hasCity() ? "7 nights, $3,800 USD, heritage, nature and vinyl music in " + city : "Plan a 7-night heritage and music trip in Kyoto with $3,800 USD")));
        return Map.of(
                "id", "trip-slot-elicitation",
                "mode", "single_select",
                "missingSlots", missing,
                "currentSlots", slots.toMap(),
                "prompt", "Required Trip Details Needed for " + city + " (reply in chat with your own numbers or pick a template):",
                "options", quickTemplates);
    }

    private List<String> buildSlotFollowUpSuggestions(TripSlotState slots) {
        String inCity = slots.hasCity() ? " in " + slots.city() : " in Paris";
        return List.of(
                "4 nights, $2,200 USD, architecture and local food" + inCity,
                "5 nights, €2,500 EUR, museums and jazz" + inCity,
                "2 nights, £900 GBP, historic neighborhoods and music" + inCity);
    }

    private Map<String, Object> buildWelcomeCityPickerInteraction(String travelerName) {
        String greeting = travelerName != null ? travelerName + ", which city would you like to explore?" : "Choose a destination to start planning (or type any city worldwide):";
        return Map.of(
                "id", "welcome-city-picker",
                "mode", "single_select",
                "prompt", greeting,
                "options", List.of(
                        Map.of("id", "dest-beirut", "label", "Beirut, Lebanon", "description", "Mediterranean courtyards, Sursock arches & Fairuz nostalgia", "badge", "Levant", "prompt", "I want to plan a trip to Beirut, Lebanon"),
                        Map.of("id", "dest-paris", "label", "Paris, France", "description", "Marais arcades, modernist ateliers & Left Bank jazz", "badge", "Europe", "prompt", "I want to plan a trip to Paris, France"),
                        Map.of("id", "dest-lisbon", "label", "Lisbon, Portugal", "description", "Alfama azulejo terraces & twilight Fado sessions", "badge", "Atlantic", "prompt", "I want to plan a trip to Lisbon, Portugal"),
                        Map.of("id", "dest-kyoto", "label", "Kyoto, Japan", "description", "Cedar machiya joinery, dry-landscape gardens & koto", "badge", "East Asia", "prompt", "I want to plan a trip to Kyoto, Japan")));
    }

    private void emitAskForCityOnly(
            MultiEmitter<? super String> emitter,
            String threadId,
            String prompt,
            String actionLabel,
            String llmMode) {
        emitFrame(emitter, threadId, "STATE_DELTA", Map.of(
                "awaitingDestination", true,
                "interaction", buildWelcomeCityPickerInteraction(null)));
        String ask = "Which **destination city** would you like me to " + actionLabel + " for?";
        String sys = "Ask the user concisely which destination city they want to " + actionLabel + " for. Do not assume any default city.";
        String text = streamAssistantText(emitter, threadId, llmMode, sys, prompt, ask);
        appendMemoryTurn(threadId, "assistant", text);
        emitCompletedRun(emitter, threadId, llmMode);
    }

    private List<Map<String, Object>> buildMediaPayload(String city, Map<String, Object> cultureCall) {
        if (Boolean.TRUE.equals(cultureCall.get("isError"))) {
            return List.of();
        }
        if (!(cultureCall.get("result") instanceof Map<?, ?> res)) {
            return List.of();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        if (res.get("heroImageUrl") instanceof String imgUrl && !imgUrl.isBlank()) {
            items.add(Map.of(
                    "type", "image",
                    "title", city + " Cultural & Architectural Plate",
                    "subtitle", "Wikipedia REST API v1 • Verified Landmark Photography",
                    "url", imgUrl));
        }
        if (res.get("audioTrack") instanceof Map<?, ?> track
                && track.get("previewUrl") instanceof String previewUrl
                && !previewUrl.isBlank()) {
            String trackName = track.get("trackName") != null ? String.valueOf(track.get("trackName")) : city + " Acoustic Session";
            String artistName = track.get("artistName") != null ? String.valueOf(track.get("artistName")) : "Regional Artist";
            items.add(Map.of(
                    "type", "audio",
                    "title", trackName,
                    "subtitle", artistName,
                    "url", previewUrl,
                    "durationSeconds", 30));
        }
        return items;
    }

    private List<Map<String, Object>> buildVerifiedAndFailedSources(
            String city,
            Map<String, Object> weatherCall,
            Map<String, Object> cultureCall,
            Map<String, Object> fxHandoff,
            Map<String, Object> advisoryHandoff) {
        List<Map<String, Object>> sources = new ArrayList<>();
        boolean weatherErr = Boolean.TRUE.equals(weatherCall.get("isError"));
        sources.add(Map.of(
                "title", "Open-Meteo Forecast — " + city,
                "url", "https://api.open-meteo.com/v1/forecast",
                "status", weatherErr ? "error" : "verified",
                "error", weatherErr ? String.valueOf(weatherCall.getOrDefault("error", "Weather API unavailable")) : ""));

        boolean cultureErr = Boolean.TRUE.equals(cultureCall.get("isError"));
        sources.add(Map.of(
                "title", "Wikipedia Cultural Summary — " + city,
                "url", "https://en.wikipedia.org/wiki/" + URLEncoder.encode(city.replace(' ', '_'), StandardCharsets.UTF_8),
                "status", cultureErr ? "error" : "verified",
                "error", cultureErr ? String.valueOf(cultureCall.getOrDefault("error", "Wikipedia unavailable")) : ""));

        boolean fxErr = Boolean.TRUE.equals(fxHandoff.get("isError"));
        sources.add(Map.of(
                "title", "Frankfurter ECB FX Reference",
                "url", "https://api.frankfurter.dev/v1/latest",
                "status", fxErr ? "error" : "verified",
                "error", fxErr ? String.valueOf(fxHandoff.getOrDefault("error", "FX API unavailable")) : ""));

        boolean advErr = Boolean.TRUE.equals(advisoryHandoff.get("isError"));
        String country = A2aGatewayClient.resolveCountryForCity(city);
        sources.add(Map.of(
                "title", "REST Countries Travel Advisory — " + country,
                "url", "https://restcountries.com/v3.1/name/" + URLEncoder.encode(country, StandardCharsets.UTF_8),
                "status", advErr ? "error" : "verified",
                "error", advErr ? String.valueOf(advisoryHandoff.getOrDefault("error", "Travel Advisory API unavailable")) : ""));
        return sources;
    }

    private List<String> collectUpstreamWarnings(
            Map<String, Object> weatherCall,
            Map<String, Object> cultureCall,
            Map<String, Object> fxHandoff,
            Map<String, Object> advisoryHandoff) {
        List<String> warnings = new ArrayList<>();
        if (Boolean.TRUE.equals(weatherCall.get("isError"))) {
            warnings.add("⚠️ **Weather Service Unavailable**: " + weatherCall.get("error"));
        }
        if (Boolean.TRUE.equals(cultureCall.get("isError"))) {
            warnings.add("⚠️ **Culture/Media Service Unavailable**: " + cultureCall.get("error"));
        }
        if (Boolean.TRUE.equals(fxHandoff.get("isError"))) {
            warnings.add("⚠️ **Currency Service Unavailable**: " + fxHandoff.get("error"));
        }
        if (Boolean.TRUE.equals(advisoryHandoff.get("isError"))) {
            warnings.add("⚠️ **" + advisoryHandoff.getOrDefault("error", "Travel Advisory Service Unavailable (restcountries.com)") + "**");
        }
        return warnings;
    }

    private String formatDeterministicTripSummary(
            TripSlotState slots,
            Map<String, Object> weatherCall,
            Map<String, Object> cultureCall,
            Map<String, Object> fxHandoff,
            List<String> warnings) {
        StringBuilder sb = new StringBuilder();
        sb.append("### Curated ").append(slots.nights()).append("-Night ")
                .append(slots.city()).append(" Dossier\n\n");
        sb.append("- **Focus**: ").append(slots.travelFocus()).append("\n");
        sb.append("- **Budget**: ").append(String.format(Locale.ROOT, "%,.0f %s", slots.budget(), slots.homeCurrency()));
        if (!Boolean.TRUE.equals(fxHandoff.get("isError"))) {
            sb.append(" — ").append(fxHandoff.get("summary"));
        }
        sb.append("\n");
        if (!Boolean.TRUE.equals(weatherCall.get("isError"))) {
            sb.append("- **Live Weather**: ")
                    .append(extractNestedString(weatherCall, "result", "temperatureC"))
                    .append("°C (")
                    .append(extractNestedString(weatherCall, "result", "condition"))
                    .append(")\n");
        }
        if (!Boolean.TRUE.equals(cultureCall.get("isError"))) {
            sb.append("\n").append(extractNestedString(cultureCall, "result", "culturalSummary")).append("\n");
        }
        if (!warnings.isEmpty()) {
            sb.append("\n").append(String.join("\n", warnings));
        }
        return sb.toString().trim();
    }

    private String buildTripSynthesisSystemPrompt(
            TripSlotState slots,
            Map<String, Object> weatherCall,
            Map<String, Object> cultureCall,
            Map<String, Object> fxHandoff,
            List<String> warnings) {
        return "You are Nomad, an editorial travel & local culture concierge chatbot. "
                + "Synthesize a concise, vivid " + slots.nights() + "-night itinerary for " + slots.city()
                + " tailored to focus=[" + slots.travelFocus() + "] and budget=[" + slots.budget() + " " + slots.homeCurrency() + "]. "
                + "Ground your answer strictly in these live tool results: "
                + "weather=" + weatherCall.get("result") + ", "
                + "fx=" + fxHandoff.get("summary") + ", "
                + "culture=" + cultureCall.get("result") + ". "
                + (warnings.isEmpty()
                        ? ""
                        : "CRITICAL HONESTY RULE: Explicitly warn the user about any failed upstream service in your reply (never claim advisories or failed APIs are CLEAR): " + warnings);
    }

    private boolean isBlockedByInputGuardrail(String prompt) {
        if (prompt == null) {
            return false;
        }
        String lower = prompt.toLowerCase(Locale.ROOT);
        return INJECTION_PATTERNS.stream().anyMatch(lower::contains);
    }

    private void emitGuardrailRejection(MultiEmitter<? super String> emitter, String threadId, String prompt) {
        Map<String, Object> guardPayload = Map.of(
                "stepId", "guard-" + UUID.randomUUID().toString().substring(0, 8),
                "callType", "guardrail:input",
                "skillId", "OWASP-LLM01 Input Guardrail",
                "agentName", "TravelSafetyAndInjectionGuardrail",
                "rule", "OWASP-LLM01",
                "status", "ERROR",
                "isError", true,
                "summary", "Blocked prompt-injection pattern (OWASP-LLM01)",
                "rejectedExcerpt", prompt.substring(0, Math.min(80, prompt.length())));
        emitFrame(emitter, threadId, "STEP_STARTED", guardPayload);
        emitFrame(emitter, threadId, "STEP_FINISHED", guardPayload);
        emitTextDelta(emitter, threadId, "Request blocked by **OWASP-LLM01 Input Guardrail**: I can only assist with travel planning, weather, currency, and cultural discovery.");
        emitFrame(emitter, threadId, "RUN_FINISHED", Map.of(
                "threadId", threadId,
                "status", "blocked_by_guardrail",
                "memoryTurns", getConversationMemory(threadId).size()));
    }

    private void emitCompletedRun(MultiEmitter<? super String> emitter, String threadId, String llmMode) {
        emitFrame(emitter, threadId, "RUN_FINISHED", Map.of(
                "threadId", threadId,
                "status", "completed",
                "llmMode", llmMode,
                "memoryTurns", getConversationMemory(threadId).size()));
    }

    private void emitTextDelta(MultiEmitter<? super String> emitter, String threadId, String chunk) {
        emitFrame(emitter, threadId, "TEXT_DELTA", Map.of("content", chunk, "delta", chunk));
    }

    private void emitFrame(MultiEmitter<? super String> emitter, String threadId, String type, Map<String, Object> payload) {
        AgUiEvent<Map<String, Object>> event = AgUiEvent.ofType(type, threadId, payload);
        emitter.emit(agUiBridge.formatter().formatSse(event));
    }

    private void appendMemoryTurn(String threadId, String role, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        List<MemoryTurn> turns = conversationMemory.computeIfAbsent(
                normalizeThreadId(threadId), k -> Collections.synchronizedList(new ArrayList<>()));
        turns.add(new MemoryTurn(role, content));
        while (turns.size() > 16) {
            turns.removeFirst();
        }
    }

    String resolveMode(String requestedMode) {
        if (requestedMode == null || requestedMode.isBlank()) {
            return "ollama:" + defaultOllamaModel;
        }
        String trimmed = requestedMode.trim();
        if ("deterministic".equalsIgnoreCase(trimmed)) {
            return "deterministic";
        }
        if ("ollama".equalsIgnoreCase(trimmed) || "ollama:".equalsIgnoreCase(trimmed)) {
            return "ollama:" + defaultOllamaModel;
        }
        return trimmed;
    }

    private static String normalizeThreadId(String threadId) {
        return (threadId == null || threadId.isBlank()) ? "thread-nomad-1" : threadId.trim();
    }

    private static String extractStopTitle(String prompt, String city) {
        if (prompt != null) {
            Matcher m = Pattern.compile("(?:reserve|book)\\s+(.+?)\\s+(?:in|for)\\s+", Pattern.CASE_INSENSITIVE).matcher(prompt);
            if (m.find()) {
                return m.group(1).trim();
            }
        }
        return McpGatewayClient.defaultSanctuaryForCity(city);
    }

    private static String extractNestedString(Map<String, Object> map, String outerKey, String innerKey) {
        if (map != null && map.get(outerKey) instanceof Map<?, ?> inner && inner.get(innerKey) != null) {
            return String.valueOf(inner.get(innerKey));
        }
        return "";
    }
}
