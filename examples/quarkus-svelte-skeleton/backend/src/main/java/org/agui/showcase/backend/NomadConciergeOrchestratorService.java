package org.agui.showcase.backend;

import com.agui.community.core.agent.Agent;
import com.agui.community.core.agent.RunAgentInput;
import com.agui.community.core.event.ActivitySnapshotEvent;
import com.agui.community.core.event.Event;
import com.agui.community.core.event.JsonPatchOperation;
import com.agui.community.core.event.RunErrorEvent;
import com.agui.community.core.event.RunFinishedEvent;
import com.agui.community.core.event.RunStartedEvent;
import com.agui.community.core.event.StateDeltaEvent;
import com.agui.community.core.event.StepFinishedEvent;
import com.agui.community.core.event.TextMessageContentEvent;
import com.agui.community.core.event.TextMessageEndEvent;
import com.agui.community.core.event.TextMessageStartEvent;
import com.agui.community.core.event.ToolCallArgsEvent;
import com.agui.community.core.event.ToolCallEndEvent;
import com.agui.community.core.event.ToolCallResultEvent;
import com.agui.community.core.event.ToolCallStartEvent;
import com.agui.community.core.interrupt.Interrupt;
import com.agui.community.core.interrupt.InterruptOutcome;
import com.agui.community.core.interrupt.Resume;
import com.agui.community.core.interrupt.ResumeStatus;
import com.agui.community.core.interrupt.SuccessOutcome;
import com.agui.community.core.message.Message;
import com.agui.community.core.message.Role;
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
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import org.agui.langchain4j.adapters.AgUiRunOptions;
import org.agui.langchain4j.adapters.AgUiToolClassifier;
import org.agui.langchain4j.domain.AgUiStatePatch;
import org.agui.quarkus.langchain4j.adapters.AgUiQuarkusLangChain4jBridge;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Idiomatic Quarkus LangChain4j + MCP + A2A + AG-UI Orchestrator ({@code :8080}).
 * Implements canonical {@link Agent} over {@link RunAgentInput}, RFC 6902 {@link StateDeltaEvent},
 * and native {@link InterruptOutcome} / {@link Resume} human-in-the-loop lifecycles.
 */
@ApplicationScoped
public class NomadConciergeOrchestratorService implements Agent {

    private static final String MCP_SERVER_LABEL = "nomad-mcp-concierge (:8081)";
    private static final Map<String, String> A2A_TOOL_SKILLS = Map.of(
            "verifyTravelAdvisory", "verify-travel-advisory",
            "convertAndAuditBudget", "convert-and-audit-budget",
            "curateNeighborhoodCadence", "curate-neighborhood-cadence");

    private final NomadConciergeAiService aiService;
    private final AgUiQuarkusLangChain4jBridge langChain4jBridge;
    private final McpClient nomadMcpClient;
    private final A2aGatewayClient a2aClient;
    private final TripDossierStore dossierStore;
    private final NomadBackendReadinessChecks readinessChecks;
    private final TravelHonestyOutputGuardrail outputGuardrail = new TravelHonestyOutputGuardrail();
    private final Map<String, AtomicInteger> memoryTurnCounts = new ConcurrentHashMap<>();

    @ConfigProperty(name = "quarkus.langchain4j.ollama.chat-model.model-id", defaultValue = "llama3.2:latest")
    String defaultOllamaModel = "llama3.2:latest";

    @Inject
    public NomadConciergeOrchestratorService(
            NomadConciergeAiService aiService,
            AgUiQuarkusLangChain4jBridge langChain4jBridge,
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

    @Override
    public Flow.Publisher<Event> run(RunAgentInput input) {
        return streamEvents(input);
    }

    public Multi<Event> streamEvents(RunAgentInput input) {
        if (hasResumeItems(input)) {
            return Multi.createFrom()
                    .deferred(() -> executeResumeEvents(input))
                    .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
        }
        return executeTurnEvents(input);
    }

    public Multi<String> streamSse(RunAgentInput input) {
        return langChain4jBridge.quarkusBridge().toSseFrames(streamEvents(input));
    }

    public Multi<String> streamJsonPayloads(RunAgentInput input) {
        return langChain4jBridge.quarkusBridge().toJsonPayloads(streamEvents(input));
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

    public AgentChatResponse executeChat(RunAgentInput input) {
        String threadId = normalizeThreadId(input == null ? null : input.threadId());
        String llmMode = resolveMode(extractRequestedMode(input));
        List<Event> events = streamEvents(input).collect().asList().await().indefinitely();
        return summarizeChatEvents(threadId, llmMode, events);
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

    private Multi<Event> executeTurnEvents(RunAgentInput input) {
        String threadId = normalizeThreadId(input == null ? null : input.threadId());
        String runId = normalizeRunId(input == null ? null : input.runId());
        String prompt = extractLatestPrompt(input);
        String llmMode = resolveMode(extractRequestedMode(input));

        dossierStore.beginTurn(threadId, prompt);
        int turns = incrementOrGetTurns(threadId, prompt);
        Map<String, Object> initialDossier = dossierStore.readTripDossierState();

        AgUiRunOptions options = AgUiRunOptions.builder()
                .runId(runId)
                .llmMode(llmMode)
                .initialStateSnapshot(Map.of(
                        "tripDossierState", initialDossier,
                        "clusterState", initialDossier,
                        "memoryTurns", turns))
                .toolClassifier(this::classifyToolExecution)
                .postToolStatePatchProvider(exec -> buildStatePatches(threadId, dossierStore.onToolExecuted(threadId, exec)))
                .postToolActivityProvider(exec -> dossierStore.drainPendingActivities(threadId))
                .postRunStatePatchProvider(() -> buildStatePatches(threadId, dossierStore.finalizeTurnDossier(threadId, a2aClient)))
                .postRunActivityProvider(() -> dossierStore.drainPendingActivities(threadId))
                .interruptSupplier(() -> buildInterrupts(threadId))
                .runResultSupplier(() -> buildRunResult(threadId))
                .build();

        return langChain4jBridge.adapt(threadId, buildGuardedStream(threadId, prompt), options);
    }

    private Multi<ChatEvent> buildGuardedStream(String threadId, String prompt) {
        return Multi.createFrom()
                .deferred(() -> aiService.chat(threadId, prompt))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .onItem().invoke(this::enforceOutputGuardrail);
    }

    private void enforceOutputGuardrail(ChatEvent event) {
        if (event instanceof ChatEvent.ChatCompletedEvent completed
                && completed.getChatResponse() != null
                && completed.getChatResponse().aiMessage() != null) {
            AiMessage aiMsg = completed.getChatResponse().aiMessage();
            if (outputGuardrail.validate(aiMsg).result() != GuardrailResult.Result.SUCCESS) {
                throw new OutputGuardrailException("Assistant response blocked by TravelHonestyOutputGuardrail");
            }
        }
    }

    private Multi<Event> executeResumeEvents(RunAgentInput input) {
        String threadId = normalizeThreadId(input.threadId());
        String runId = normalizeRunId(input.runId());
        String llmMode = resolveMode(extractRequestedMode(input));
        Resume resume = input.resume().getFirst();
        TripDossierStore.PendingHitlReservation pending = dossierStore.consumePendingApproval(threadId);

        if (resume == null || resume.status() != ResumeStatus.RESOLVED || pending == null) {
            return Multi.createFrom().iterable(buildDeclinedResumeEvents(threadId, runId, llmMode, resume));
        }
        return Multi.createFrom().iterable(buildApprovedResumeEvents(
                threadId,
                runId,
                llmMode,
                pending,
                extractOverrideMap(resume.payload())));
    }

    private List<Event> buildDeclinedResumeEvents(
            String threadId,
            String runId,
            String llmMode,
            Resume resume) {
        long now = System.currentTimeMillis();
        String reason = extractDeclineReason(resume);
        String msgId = "msg-" + shortId();
        Map<String, Object> clearPending = new LinkedHashMap<>();
        clearPending.put("pendingApproval", null);
        clearPending.put("memoryTurns", getMemoryTurnCount(threadId));

        return List.of(
                new RunStartedEvent(threadId, runId, null, null, now, Map.of("llmMode", llmMode)),
                new StateDeltaEvent(AgUiStatePatch.upsertRootFields(clearPending), now, null),
                new TextMessageStartEvent(msgId, Role.ASSISTANT, now, null),
                new TextMessageContentEvent(
                        msgId,
                        "Traveler declined sanctuary reservation hold (" + reason + "). Run cancelled safely.",
                        now,
                        null),
                new TextMessageEndEvent(msgId, now, null),
                new RunFinishedEvent(
                        threadId,
                        runId,
                        new SuccessOutcome(),
                        Map.of(
                                "threadId", threadId,
                                "status", "cancelled",
                                "reason", reason,
                                "memoryTurns", getMemoryTurnCount(threadId)),
                        now,
                        Map.of("llmMode", llmMode)));
    }

    private List<Event> buildApprovedResumeEvents(
            String threadId,
            String runId,
            String llmMode,
            TripDossierStore.PendingHitlReservation pending,
            Map<String, Object> overrides) {
        long now = System.currentTimeMillis();
        String city = stringOverride(overrides, "city", pending.city());
        String stopTitle = stringOverride(overrides, "stopTitle", pending.stopTitle());
        int nights = intOverride(overrides, "nights", pending.nights());
        double costUsd = doubleOverride(overrides, "costUsd", pending.costUsd());

        String callId = "hitl-exec-" + shortId();
        Map<String, Object> execArgs = Map.of(
                "city", city,
                "stopTitle", stopTitle,
                "nights", nights,
                "costUsd", costUsd);
        Map<String, Object> outcome = dossierStore.executeApprovedItineraryUpdate(city, stopTitle, nights, costUsd);
        boolean isError = Boolean.TRUE.equals(outcome.get("isError"));

        List<Event> events = new ArrayList<>(10);
        events.add(new RunStartedEvent(threadId, runId, null, null, now, Map.of("llmMode", llmMode)));
        appendResumeToolLifecycle(events, callId, execArgs, outcome, isError, now);

        Map<String, Object> dossier = dossierStore.readTripDossierState();
        Map<String, Object> deltaFields = new LinkedHashMap<>();
        deltaFields.put("tripDossierState", dossier);
        deltaFields.put("clusterState", dossier);
        deltaFields.put("memoryTurns", getMemoryTurnCount(threadId));
        deltaFields.put("pendingApproval", null);
        events.add(new StateDeltaEvent(AgUiStatePatch.upsertRootFields(deltaFields), now, null));
        if (!isError && dossier.get("reportSections") instanceof Map<?, ?> sections
                && sections.get("fxBudget") instanceof Map<?, ?> fxBudget) {
            String slug = city.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
            events.add(new ActivitySnapshotEvent(
                    "act-fx-budget-" + slug,
                    TripDossierStore.ACT_FX_BUDGET,
                    fxBudget,
                    true,
                    now,
                    Map.of("protocol", "AG-UI", "callType", "agui:activity", "activityType", TripDossierStore.ACT_FX_BUDGET, "city", city)));
        }

        String replyText = isError
                ? "Reservation commit failed on MCP server (:8081): " + outcome.getOrDefault("error", "Unavailable")
                : String.format(
                        Locale.ROOT,
                        "Confirmed **%s** in **%s** for **%d nights** ($%,.2f USD).",
                        stopTitle,
                        city,
                        nights,
                        costUsd);
        String msgId = "msg-" + shortId();
        events.add(new TextMessageStartEvent(msgId, Role.ASSISTANT, now, null));
        events.add(new TextMessageContentEvent(msgId, replyText, now, null));
        events.add(new TextMessageEndEvent(msgId, now, null));
        events.add(new RunFinishedEvent(
                threadId,
                runId,
                new SuccessOutcome(),
                Map.of(
                        "threadId", threadId,
                        "status", isError ? "failed" : "completed",
                        "isError", isError,
                        "memoryTurns", getMemoryTurnCount(threadId)),
                now,
                Map.of("llmMode", llmMode)));
        return List.copyOf(events);
    }

    private void appendResumeToolLifecycle(
            List<Event> events,
            String callId,
            Map<String, Object> execArgs,
            Map<String, Object> outcome,
            boolean isError,
            long now) {
        String argsJson = langChain4jBridge.quarkusBridge().serializer().serialize(execArgs);
        String resultJson = langChain4jBridge.quarkusBridge().serializer().serialize(outcome);

        events.add(new ToolCallStartEvent(callId, "updateTripItinerary", null, now, Map.of(
                "callId", callId,
                "tool", "updateTripItinerary",
                "protocol", "MCP",
                "callType", "mcp:tool",
                "server", MCP_SERVER_LABEL,
                "args", execArgs)));
        events.add(new ToolCallArgsEvent(callId, argsJson, now, null));
        events.add(new ToolCallEndEvent(callId, now, null));

        Map<String, Object> resultMeta = new LinkedHashMap<>();
        resultMeta.put("callId", callId);
        resultMeta.put("tool", "updateTripItinerary");
        resultMeta.put("protocol", "MCP");
        resultMeta.put("callType", "mcp:tool");
        resultMeta.put("server", MCP_SERVER_LABEL);
        resultMeta.put("status", isError ? "ERROR" : "OK");
        resultMeta.put("isError", isError);
        if (isError) {
            resultMeta.put("error", String.valueOf(outcome.getOrDefault("error", "MCP reservation update failed")));
        }
        resultMeta.put("result", outcome);
        events.add(new ToolCallResultEvent(
                "tool-msg-" + callId,
                callId,
                resultJson,
                Role.TOOL,
                now,
                Map.copyOf(resultMeta)));
    }

    private AgentChatResponse summarizeChatEvents(String threadId, String llmMode, List<Event> events) {
        StringBuilder reply = new StringBuilder();
        List<Map<String, Object>> mcpCalls = new ArrayList<>();
        List<Map<String, Object>> a2aHandoffs = new ArrayList<>();
        String status = "COMPLETED";

        for (Event event : events) {
            collectChatEventArtifact(event, reply, mcpCalls, a2aHandoffs);
            status = resolveChatEventStatus(event, status);
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

    private static void collectChatEventArtifact(
            Event event,
            StringBuilder reply,
            List<Map<String, Object>> mcpCalls,
            List<Map<String, Object>> a2aHandoffs) {
        if (event instanceof TextMessageContentEvent text) {
            reply.append(text.delta());
        } else if (event instanceof ToolCallResultEvent toolRes) {
            addIfMcpMetadata(mcpCalls, toolRes.rawEvent());
        } else if (event instanceof StepFinishedEvent stepFin) {
            addIfA2aMetadata(a2aHandoffs, stepFin.rawEvent());
        }
    }

    private static String resolveChatEventStatus(Event event, String currentStatus) {
        if (event instanceof RunFinishedEvent fin) {
            return resolveFinishedStatus(fin);
        }
        if (event instanceof RunErrorEvent err) {
            return err.code() != null ? err.code() : "ERROR";
        }
        return currentStatus;
    }

    private AgUiToolClassifier.ToolDescriptor classifyToolExecution(String toolName, Map<String, Object> args) {
        String a2aSkill = A2A_TOOL_SKILLS.get(toolName);
        if (a2aSkill != null) {
            return AgUiToolClassifier.ToolDescriptor.a2aStep("Nomad-A2A-Specialist", a2aSkill);
        }
        return switch (toolName) {
            case "sampleAcousticMood" -> AgUiToolClassifier.ToolDescriptor.mcpSampling(MCP_SERVER_LABEL, toolName);
            case "presentTravelChoices" -> AgUiToolClassifier.ToolDescriptor.uiSurface("Nomad-Concierge-Orchestrator", toolName);
            case "requestSanctuaryReservation" -> classifyReservationTool(toolName, args);
            default -> AgUiToolClassifier.ToolDescriptor.mcpTool(MCP_SERVER_LABEL, toolName);
        };
    }

    private static AgUiToolClassifier.ToolDescriptor classifyReservationTool(String toolName, Map<String, Object> args) {
        double cost = args != null && args.get("costUsd") instanceof Number n ? n.doubleValue() : 0.0;
        return cost >= TripDossierStore.HITL_THRESHOLD_USD
                ? AgUiToolClassifier.ToolDescriptor.hitlApproval("Nomad-Concierge-Orchestrator", toolName)
                : AgUiToolClassifier.ToolDescriptor.uiSurface("Nomad-Concierge-Orchestrator", toolName);
    }

    private List<JsonPatchOperation> buildStatePatches(String threadId, Map<String, Object> deltaFields) {
        if (deltaFields == null || deltaFields.isEmpty()) {
            return List.of();
        }
        Map<String, Object> enriched = new LinkedHashMap<>(deltaFields);
        enriched.put("memoryTurns", getMemoryTurnCount(threadId));
        return AgUiStatePatch.upsertRootFields(enriched);
    }

    private List<Interrupt> buildInterrupts(String threadId) {
        TripDossierStore.PendingHitlReservation pending = dossierStore.peekPendingApproval(threadId);
        if (pending == null) {
            return List.of();
        }
        Map<String, Object> meta = Map.of(
                "callId", pending.callId(),
                "toolName", "updateTripItinerary",
                "city", pending.city(),
                "stopTitle", pending.stopTitle(),
                "nights", pending.nights(),
                "costUsd", pending.costUsd());
        String message = String.format(
                Locale.ROOT,
                "Confirm sanctuary reservation hold for %s in %s (%d nights, $%,.2f USD)",
                pending.stopTitle(),
                pending.city(),
                pending.nights(),
                pending.costUsd());
        return List.of(new Interrupt(
                pending.callId(),
                "human_approval",
                message,
                pending.callId(),
                null,
                null,
                meta));
    }

    private Map<String, Object> buildRunResult(String threadId) {
        TripDossierStore.PendingHitlReservation pending = dossierStore.peekPendingApproval(threadId);
        return Map.of(
                "status", pending != null ? "interrupted" : "completed",
                "memoryTurns", getMemoryTurnCount(threadId));
    }

    private int incrementOrGetTurns(String threadId, String prompt) {
        return prompt.isBlank()
                ? getMemoryTurnCount(threadId)
                : memoryTurnCounts.computeIfAbsent(threadId, k -> new AtomicInteger()).addAndGet(2);
    }

    private static boolean hasResumeItems(RunAgentInput input) {
        return input != null && input.resume() != null && !input.resume().isEmpty();
    }

    private static String extractLatestPrompt(RunAgentInput input) {
        List<Message> messages = input == null ? null : input.messages();
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        String userPrompt = findLatestUserPrompt(messages);
        return userPrompt != null ? userPrompt : messageContentOrEmpty(messages.getLast());
    }

    private static String findLatestUserPrompt(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            String content = userMessageContent(messages.get(i));
            if (content != null) {
                return content;
            }
        }
        return null;
    }

    private static String userMessageContent(Message msg) {
        return (msg != null && msg.role() == Role.USER) ? msg.content() : null;
    }

    private static String messageContentOrEmpty(Message msg) {
        return (msg != null && msg.content() != null) ? msg.content() : "";
    }

    private static String extractRequestedMode(RunAgentInput input) {
        if (input != null && input.forwardedProps() instanceof Map<?, ?> props) {
            if (props.get("llmMode") instanceof String llmMode) {
                return llmMode;
            }
            if (props.get("mode") instanceof String mode) {
                return mode;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractOverrideMap(Object payload) {
        if (payload instanceof Map<?, ?> map) {
            if (map.get("modifiedArgs") instanceof Map<?, ?> nested) {
                return (Map<String, Object>) nested;
            }
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static String extractDeclineReason(Resume resume) {
        if (resume != null && resume.payload() instanceof Map<?, ?> map
                && map.get("reason") instanceof String reason && !reason.isBlank()) {
            return reason.trim();
        }
        return "Declined by traveler";
    }

    private static String stringOverride(Map<String, Object> overrides, String key, String fallback) {
        return overrides.get(key) instanceof String s && !s.isBlank() ? s.trim() : fallback;
    }

    private static int intOverride(Map<String, Object> overrides, String key, int fallback) {
        return overrides.get(key) instanceof Number n && n.intValue() > 0 ? n.intValue() : fallback;
    }

    private static double doubleOverride(Map<String, Object> overrides, String key, double fallback) {
        return overrides.get(key) instanceof Number n && n.doubleValue() > 0.0 ? n.doubleValue() : fallback;
    }

    @SuppressWarnings("unchecked")
    private static void addIfMcpMetadata(List<Map<String, Object>> mcpCalls, Object rawEvent) {
        if (rawEvent instanceof Map<?, ?> meta && String.valueOf(meta.get("callType")).startsWith("mcp:")) {
            mcpCalls.add((Map<String, Object>) meta);
        }
    }

    @SuppressWarnings("unchecked")
    private static void addIfA2aMetadata(List<Map<String, Object>> a2aHandoffs, Object rawEvent) {
        if (rawEvent instanceof Map<?, ?> meta && "A2A".equals(meta.get("protocol"))) {
            a2aHandoffs.add((Map<String, Object>) meta);
        }
    }

    private static String resolveFinishedStatus(RunFinishedEvent fin) {
        if (fin.outcome() instanceof InterruptOutcome) {
            return "INTERRUPTED";
        }
        if (fin.result() instanceof Map<?, ?> res && res.get("status") instanceof String s && !s.isBlank()) {
            return s.toUpperCase(Locale.ROOT);
        }
        return "COMPLETED";
    }

    private static String normalizeThreadId(String threadId) {
        return (threadId == null || threadId.isBlank()) ? "thread-nomad-1" : threadId.trim();
    }

    private static String normalizeRunId(String runId) {
        return (runId == null || runId.isBlank()) ? "run-" + shortId() : runId.trim();
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
