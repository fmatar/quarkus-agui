package org.agui.showcase.backend;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.GuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.mcp.client.McpClient;
import io.quarkiverse.langchain4j.ChatMemoryRemover;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.agui.core.adapters.AgUiSseFormatter;
import org.agui.core.domain.AgUiEvent;
import org.agui.core.domain.AgUiEventType;
import org.agui.core.domain.TextDeltaPayload;
import org.agui.quarkus.langchain4j.runtime.adapters.AgUiLangChain4jBridge;
import org.agui.quarkus.langchain4j.runtime.adapters.AgUiRunOptions;
import org.agui.quarkus.langchain4j.runtime.adapters.AgUiToolClassifier;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Idiomatic Quarkus LangChain4j + MCP + A2A + AG-UI Orchestrator ({@code :8080}).
 * Delegates conversational reasoning and MCP tool execution to {@link NomadConciergeAiService}
 * (`@RegisterAiService` + `@McpToolBox("nomad")`) and adapts {@code Multi<ChatEvent>} streams
 * via {@link AgUiLangChain4jBridge}.
 */
@ApplicationScoped
public class NomadConciergeOrchestratorService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final NomadConciergeAiService aiService;
    private final AgUiLangChain4jBridge langChain4jBridge;
    private final McpClient nomadMcpClient;
    private final A2aGatewayClient a2aClient;
    private final TripDossierStore dossierStore;
    private final NomadBackendReadinessChecks readinessChecks;
    private final TravelHonestyOutputGuardrail outputGuardrail = new TravelHonestyOutputGuardrail();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, AtomicInteger> memoryTurnCounts = new ConcurrentHashMap<>();

    @ConfigProperty(name = "quarkus.langchain4j.ollama.chat-model.model-id", defaultValue = "llama3.2:latest")
    String defaultOllamaModel = "llama3.2:latest";

    @Inject
    public NomadConciergeOrchestratorService(
            NomadConciergeAiService aiService,
            AgUiLangChain4jBridge langChain4jBridge,
            @McpClientName("nomad") McpClient nomadMcpClient,
            A2aGatewayClient a2aClient,
            TripDossierStore dossierStore,
            NomadBackendReadinessChecks readinessChecks) {
        this.aiService = aiService;
        this.langChain4jBridge = langChain4jBridge;
        this.nomadMcpClient = nomadMcpClient;
        this.a2aClient = a2aClient;
        this.dossierStore = dossierStore;
        this.readinessChecks = readinessChecks;
    }

    public AgUiSseFormatter formatter() {
        return langChain4jBridge.formatter();
    }

    public int getMemoryTurnCount(String threadId) {
        AtomicInteger counter = memoryTurnCounts.get(normalizeThreadId(threadId));
        return counter == null ? 0 : counter.get();
    }

    public Map<String, Object> getCatalog() {
        String activeMode = resolveMode(null);
        Map<String, Object> dossier = dossierStore.readTripDossierState();
        Map<String, Object> catalog = new LinkedHashMap<>();
        catalog.put("orchestrator", "Nomad-Concierge-Orchestrator");
        catalog.put("llmMode", activeMode);
        catalog.put("supportedModes", List.of(activeMode));
        catalog.put("mcpTools", dossierStore.listMcpTools());
        catalog.put("a2aAgent", a2aClient.getAgentCard());
        catalog.put("tripDossierState", dossier);
        catalog.put("clusterState", dossier);
        return catalog;
    }

    public Map<String, Object> checkSystemHealth() {
        return readinessChecks.checkAllServices(nomadMcpClient);
    }

    public Multi<String> streamRun(AgentRunRequest request) {
        String threadId = normalizeThreadId(request == null ? null : request.threadId());
        String prompt = (request == null || request.prompt() == null) ? "" : request.prompt();
        String llmMode = resolveMode(request == null ? null : request.mode());

        dossierStore.beginTurn(threadId);
        int turns = prompt.isBlank()
                ? getMemoryTurnCount(threadId)
                : memoryTurnCounts.computeIfAbsent(threadId, k -> new AtomicInteger()).addAndGet(2);

        Map<String, Object> initialDossier = dossierStore.readTripDossierState();
        AgUiRunOptions options = new AgUiRunOptions(
                llmMode,
                Map.of(
                        "tripDossierState", initialDossier,
                        "clusterState", initialDossier,
                        "memoryTurns", turns),
                this::classifyToolExecution,
                exec -> dossierStore.onToolExecuted(threadId, exec),
                null,
                () -> buildRunFinishedMetadata(threadId));

        Multi<ChatEvent> guardedStream = Multi.createFrom()
                .deferred(() -> aiService.chat(threadId, prompt))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .onItem().invoke(event -> {
                    if (event instanceof ChatEvent.ChatCompletedEvent completed
                            && completed.getChatResponse() != null
                            && completed.getChatResponse().aiMessage() != null) {
                        AiMessage aiMsg = completed.getChatResponse().aiMessage();
                        if (outputGuardrail.validate(aiMsg).result() != GuardrailResult.Result.SUCCESS) {
                            throw new OutputGuardrailException("Assistant response blocked by TravelHonestyOutputGuardrail");
                        }
                    }
                });

        return langChain4jBridge.adaptToSse(threadId, guardedStream, options);
    }

    @SuppressWarnings("unchecked")
    public AgentChatResponse executeChat(AgentRunRequest request) {
        String threadId = normalizeThreadId(request == null ? null : request.threadId());
        String llmMode = resolveMode(request == null ? null : request.mode());
        List<String> frames = streamRun(request).collect().asList().await().indefinitely();

        StringBuilder reply = new StringBuilder();
        List<Map<String, Object>> mcpCalls = new ArrayList<>();
        List<Map<String, Object>> a2aHandoffs = new ArrayList<>();
        String status = "COMPLETED";

        for (String rawFrame : frames) {
            String json = AgUiSseFormatter.unwrapSseData(rawFrame);
            if (json == null || json.isBlank()) {
                continue;
            }
            try {
                Map<String, Object> event = objectMapper.readValue(json, MAP_TYPE);
                String type = String.valueOf(event.getOrDefault("type", ""));
                Map<String, Object> payload = event.get("payload") instanceof Map<?, ?> p
                        ? (Map<String, Object>) p
                        : Map.of();
                switch (type) {
                    case "TEXT_DELTA" -> {
                        if (payload.get("content") instanceof String chunk) {
                            reply.append(chunk);
                        }
                    }
                    case "TOOL_RESULT" -> {
                        if (String.valueOf(payload.getOrDefault("callType", "")).startsWith("mcp:")) {
                            mcpCalls.add(payload);
                        }
                    }
                    case "STEP_FINISHED" -> {
                        if ("A2A".equals(payload.get("protocol"))) {
                            a2aHandoffs.add(payload);
                        }
                    }
                    case "RUN_FINISHED" -> {
                        if (payload.get("status") instanceof String s && !s.isBlank()) {
                            status = s.toUpperCase(Locale.ROOT);
                        }
                    }
                    default -> {
                    }
                }
            } catch (Exception ignored) {
                // Ignore non-JSON comments if any
            }
        }

        return new AgentChatResponse(
                threadId,
                status,
                llmMode,
                mcpCalls,
                a2aHandoffs,
                dossierStore.readTripDossierState(),
                reply.toString());
    }

    public Multi<String> approvePendingAction(AgentApprovalRequest approval) {
        return Multi.createFrom()
                .deferred(() -> executeApprovePendingAction(approval))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private Multi<String> executeApprovePendingAction(AgentApprovalRequest approval) {
        String threadId = normalizeThreadId(approval == null ? null : approval.threadId());
        TripDossierStore.PendingHitlReservation pending = dossierStore.consumePendingApproval(threadId);
        List<AgUiEvent<?>> events = new ArrayList<>();
        events.add(AgUiEvent.of(AgUiEventType.RUN_STARTED, threadId, Map.of(
                "threadId", threadId,
                "status", "started",
                "llmMode", resolveMode(null))));

        if (approval == null || !approval.approved() || pending == null) {
            String reason = (approval != null && approval.reason() != null && !approval.reason().isBlank())
                    ? approval.reason()
                    : "Declined by traveler";
            events.add(AgUiEvent.of(
                    AgUiEventType.TEXT_DELTA,
                    threadId,
                    new TextDeltaPayload("Traveler declined sanctuary reservation hold (" + reason + "). Run cancelled safely.")));
            events.add(AgUiEvent.of(AgUiEventType.RUN_FINISHED, threadId, Map.of(
                    "threadId", threadId,
                    "status", "cancelled",
                    "reason", reason,
                    "memoryTurns", getMemoryTurnCount(threadId))));
            return Multi.createFrom().iterable(events).map(langChain4jBridge.formatter()::formatSse);
        }

        Map<String, Object> overrides = approval.modifiedArgs() == null ? Map.of() : approval.modifiedArgs();
        String city = overrides.get("city") instanceof String c && !c.isBlank() ? c.trim() : pending.city();
        String stopTitle = overrides.get("stopTitle") instanceof String s && !s.isBlank() ? s.trim() : pending.stopTitle();
        int nights = overrides.get("nights") instanceof Number n && n.intValue() > 0 ? n.intValue() : pending.nights();
        double costUsd = overrides.get("costUsd") instanceof Number c && c.doubleValue() > 0 ? c.doubleValue() : pending.costUsd();

        String callId = "hitl-exec-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> execArgs = Map.of("city", city, "stopTitle", stopTitle, "nights", nights, "costUsd", costUsd);
        events.add(AgUiEvent.of(AgUiEventType.TOOL_CALL, threadId, Map.of(
                "callId", callId,
                "tool", "updateTripItinerary",
                "callType", "mcp:tool",
                "server", "nomad-mcp-concierge (:8081)",
                "args", execArgs)));

        Map<String, Object> outcome = dossierStore.executeApprovedItineraryUpdate(city, stopTitle, nights, costUsd);
        boolean isError = Boolean.TRUE.equals(outcome.get("isError"));
        Map<String, Object> toolResultPayload = new LinkedHashMap<>();
        toolResultPayload.put("callId", callId);
        toolResultPayload.put("tool", "updateTripItinerary");
        toolResultPayload.put("callType", "mcp:tool");
        toolResultPayload.put("status", isError ? "ERROR" : "OK");
        toolResultPayload.put("isError", isError);
        if (isError) {
            toolResultPayload.put("error", String.valueOf(outcome.getOrDefault("error", "MCP reservation update failed")));
        }
        toolResultPayload.put("result", outcome);
        events.add(AgUiEvent.of(AgUiEventType.TOOL_RESULT, threadId, toolResultPayload));

        Map<String, Object> dossier = dossierStore.readTripDossierState();
        events.add(AgUiEvent.of(AgUiEventType.STATE_DELTA, threadId, Map.of(
                "tripDossierState", dossier,
                "clusterState", dossier,
                "memoryTurns", getMemoryTurnCount(threadId))));

        String replyText = isError
                ? "Reservation commit failed on MCP server (:8081): " + outcome.getOrDefault("error", "Unavailable")
                : String.format(Locale.ROOT, "Confirmed **%s** in **%s** for **%d nights** ($%,.2f USD).", stopTitle, city, nights, costUsd);
        events.add(AgUiEvent.of(AgUiEventType.TEXT_DELTA, threadId, new TextDeltaPayload(replyText)));
        events.add(AgUiEvent.of(AgUiEventType.RUN_FINISHED, threadId, Map.of(
                "threadId", threadId,
                "status", isError ? "failed" : "completed",
                "isError", isError,
                "memoryTurns", getMemoryTurnCount(threadId))));

        return Multi.createFrom().iterable(events).map(langChain4jBridge.formatter()::formatSse);
    }

    public Map<String, Object> updateTripParams(String threadId, Integer nights, String homeCurrency) {
        String resolvedThread = normalizeThreadId(threadId);
        Map<String, Object> dossier = dossierStore.updateTripParameters(nights, homeCurrency);
        return Map.of(
                "threadId", resolvedThread,
                "status", "params_updated",
                "tripDossierState", dossier,
                "clusterState", dossier,
                "memoryTurns", getMemoryTurnCount(resolvedThread));
    }

    public Map<String, Object> resetMemory(String threadId) {
        String resolvedThread = normalizeThreadId(threadId);
        try {
            ChatMemoryRemover.remove(aiService, resolvedThread);
        } catch (RuntimeException ignored) {
            // Non-Quarkus test doubles do not implement QuarkusAiServiceContext
        }
        memoryTurnCounts.remove(resolvedThread);
        Map<String, Object> dossier = dossierStore.resetAll(resolvedThread);
        return Map.of(
                "threadId", resolvedThread,
                "status", "memory_reset",
                "memoryTurns", 0,
                "tripDossierState", dossier,
                "clusterState", dossier);
    }

    public void cancelRun(String threadId) {
        dossierStore.consumePendingApproval(normalizeThreadId(threadId));
    }

    public List<String> completeCityPrefix(String prefix) {
        return dossierStore.completeCityPrefix(prefix);
    }

    String resolveMode(String requestedMode) {
        if (requestedMode == null || requestedMode.isBlank()) {
            return "ollama:" + defaultOllamaModel;
        }
        String trimmed = requestedMode.trim();
        if ("deterministic".equalsIgnoreCase(trimmed)
                || "ollama".equalsIgnoreCase(trimmed)
                || "ollama:".equalsIgnoreCase(trimmed)) {
            return "ollama:" + defaultOllamaModel;
        }
        return trimmed;
    }

    private AgUiToolClassifier.ToolDescriptor classifyToolExecution(String toolName, Map<String, Object> args) {
        return switch (toolName) {
            case "verifyTravelAdvisory" -> AgUiToolClassifier.ToolDescriptor.a2aStep(
                    "Nomad-A2A-Specialist", "verify-travel-advisory");
            case "convertAndAuditBudget" -> AgUiToolClassifier.ToolDescriptor.a2aStep(
                    "Nomad-A2A-Specialist", "convert-and-audit-budget");
            case "curateNeighborhoodCadence" -> AgUiToolClassifier.ToolDescriptor.a2aStep(
                    "Nomad-A2A-Specialist", "curate-neighborhood-cadence");
            case "sampleAcousticMood" -> AgUiToolClassifier.ToolDescriptor.mcpSampling(
                    "nomad-mcp-concierge (:8081)", toolName);
            case "presentTravelChoices" -> AgUiToolClassifier.ToolDescriptor.uiSurface(
                    "Nomad-Concierge-Orchestrator", toolName);
            case "requestSanctuaryReservation" -> {
                double cost = args != null && args.get("costUsd") instanceof Number n ? n.doubleValue() : 0.0;
                yield cost >= TripDossierStore.HITL_THRESHOLD_USD
                        ? AgUiToolClassifier.ToolDescriptor.hitlApproval("Nomad-Concierge-Orchestrator", toolName)
                        : AgUiToolClassifier.ToolDescriptor.uiSurface("Nomad-Concierge-Orchestrator", toolName);
            }
            default -> AgUiToolClassifier.ToolDescriptor.mcpTool("nomad-mcp-concierge (:8081)", toolName);
        };
    }

    private Map<String, Object> buildRunFinishedMetadata(String threadId) {
        TripDossierStore.PendingHitlReservation pending = dossierStore.peekPendingApproval(threadId);
        int turns = getMemoryTurnCount(threadId);
        if (pending != null) {
            return Map.of(
                    "status", "interrupted",
                    "approvalRequired", true,
                    "memoryTurns", turns,
                    "pendingAction", Map.of(
                            "callId", pending.callId(),
                            "toolName", "updateTripItinerary",
                            "city", pending.city(),
                            "stopTitle", pending.stopTitle(),
                            "nights", pending.nights(),
                            "costUsd", pending.costUsd()));
        }
        return Map.of("memoryTurns", turns);
    }

    private static String normalizeThreadId(String threadId) {
        return (threadId == null || threadId.isBlank()) ? "thread-nomad-1" : threadId.trim();
    }
}
