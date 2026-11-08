package org.agui.showcase.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.agui.core.domain.AgUiEvent;
import org.agui.quarkus.core.runtime.adapters.AgUiA2aBridge;
import org.agui.quarkus.core.runtime.adapters.AgUiBridge;
import org.agui.quarkus.core.runtime.adapters.AgUiMcpBridge;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Nomad Travel & Local Culture Concierge Quarkus Orchestrator Service (:8080).
 * Implements the full AG-UI Protocol (Core 6 + MCP/A2A + STATE_SNAPSHOT/STATE_DELTA + HITL sanctuary approvals)
 * with Dual-Mode execution: live local Ollama LLM streaming (Java 25 Virtual Threads) and deterministic CI mode.
 */
@ApplicationScoped
public class OpsPilotOrchestratorService {

    private static final double HITL_SANCTUARY_THRESHOLD_USD = 1500.0;
    private static final String DEFAULT_OLLAMA_MODEL = "llama3.2:latest";

    private final McpGatewayClient mcpClient;
    private final A2aGatewayClient a2aClient;
    private final AgUiBridge agUiBridge;
    private final AgUiMcpBridge agUiMcpBridge;
    private final AgUiA2aBridge agUiA2aBridge;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Map<String, Object>> pendingApprovals = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @ConfigProperty(name = "agui.ollama.base-url", defaultValue = "http://127.0.0.1:11434")
    String ollamaBaseUrl = "http://127.0.0.1:11434";

    public OpsPilotOrchestratorService(McpGatewayClient mcpClient, A2aGatewayClient a2aClient) {
        this(mcpClient, a2aClient, new AgUiBridge(), new AgUiMcpBridge(), new AgUiA2aBridge());
    }

    @Inject
    public OpsPilotOrchestratorService(
            McpGatewayClient mcpClient,
            A2aGatewayClient a2aClient,
            AgUiBridge agUiBridge,
            AgUiMcpBridge agUiMcpBridge,
            AgUiA2aBridge agUiA2aBridge) {
        this.mcpClient = mcpClient;
        this.a2aClient = a2aClient;
        this.agUiBridge = agUiBridge;
        this.agUiMcpBridge = agUiMcpBridge;
        this.agUiA2aBridge = agUiA2aBridge;
    }

    public Map<String, Object> getCatalog() {
        return Map.of(
                "orchestrator", "Nomad-Concierge-Orchestrator",
                "extension", "org.agui:quarkus-ag-ui",
                "llmMode", resolveMode(null),
                "defaultOllamaModel", DEFAULT_OLLAMA_MODEL,
                "mcpTools", mcpClient.listTools(),
                "a2aAgent", a2aClient.getAgentCard(),
                "clusterState", mcpClient.readClusterState());
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

    public AgentChatResponse executeChat(AgentRunRequest request) {
        String threadId = resolveThreadId(request);
        String prompt = request != null && request.prompt() != null && !request.prompt().isBlank()
                ? request.prompt()
                : "Curate a 4-night cultural and musical escape in Kyoto";
        String lower = prompt.toLowerCase(Locale.ROOT);
        String mode = resolveMode(request != null ? request.mode() : null);
        String city = detectNomadCity(prompt, request);
        String genre = detectMusicGenre(lower);

        List<Map<String, Object>> mcpCalls = new ArrayList<>();
        mcpCalls.add(mcpClient.callTool("fetchCityWeather", Map.of("city", city)));
        mcpCalls.add(mcpClient.callTool("discoverLocalCultureAndMedia", Map.of("destination", city, "genre", genre)));

        if (lower.contains("itinerary") || lower.contains("update")) {
            int nights = extractNights(lower, 4);
            mcpCalls.add(mcpClient.callTool("updateTripItinerary", Map.of(
                    "city", city,
                    "stopTitle", McpGatewayClient.defaultSanctuaryForCity(city),
                    "nights", nights,
                    "costUsd", 1480.0)));
        }

        List<Map<String, Object>> a2aHandoffs = new ArrayList<>();
        a2aHandoffs.add(a2aClient.delegateTask("a2a-fx-" + threadId, "convert-and-audit-budget", prompt));
        a2aHandoffs.add(a2aClient.delegateTask("a2a-adv-" + threadId, "verify-travel-advisory", prompt));
        if (lower.contains("cadence") || lower.contains("walk") || lower.contains("neighborhood")) {
            a2aHandoffs.add(a2aClient.delegateTask("a2a-cad-" + threadId, "curate-neighborhood-cadence", prompt));
        }

        Map<String, Object> handoff = a2aHandoffs.getFirst();
        String reply = buildNomadConciergeMarkdownReply(city, prompt, handoff);

        return new AgentChatResponse(
                threadId,
                "COMPLETED",
                mode,
                mcpCalls,
                a2aHandoffs,
                mcpClient.readClusterState(),
                reply);
    }

    public Multi<String> streamRun(AgentRunRequest request) {
        String mode = resolveMode(request != null ? request.mode() : null);
        if (isLiveLlmMode(mode)) {
            return Multi.createFrom().emitter(emitter ->
                    Thread.ofVirtual().name("agui-ollama-stream").start(() -> streamLiveOllamaRun(request, mode, emitter)));
        }
        return Multi.createFrom().iterable(buildDeterministicFrames(request, mode));
    }

    private List<String> buildDeterministicFrames(AgentRunRequest request, String mode) {
        String threadId = resolveThreadId(request);
        String prompt = request != null && request.prompt() != null && !request.prompt().isBlank()
                ? request.prompt()
                : "Curate a 4-night cultural and musical escape in Kyoto";
        String lower = prompt.toLowerCase(Locale.ROOT);
        List<String> frames = new ArrayList<>();

        frames.add(formatSseEvent(threadId, "RUN_STARTED", Map.of(
                "threadId", threadId,
                "status", "started",
                "llmMode", mode)));

        frames.add(formatSseEvent(threadId, "STATE_SNAPSHOT", Map.of(
                "clusterState", mcpClient.readClusterState(),
                "clientState", request != null && request.state() != null ? request.state() : Map.of())));

        if (checkAndRecordHitlInterrupt(threadId, prompt, lower, frames)) {
            return frames;
        }

        String city = detectNomadCity(prompt, request);
        String promptTemplateName = lower.contains("walk") || lower.contains("music")
                ? "local-music-and-food-walk"
                : "weekend-cultural-escape";

        frames.add(formatSseEvent(threadId, "STATUS", Map.of(
                "phase", "reasoning",
                "durationMs", 1240,
                "thought", "Synthesizing " + city + " cultural dossier: expanding MCP prompt template, reading traveler://preferences, querying Open-Meteo & iTunes 30s audio preview (:8081), and delegating Frankfurter FX & REST Countries audit to Nomad-A2A-Specialist (:8082).",
                "message", "Reasoning across AG-UI + MCP (:8081) + A2A (:8082) protocols")));

        String promptCallId = "call-prompt-" + UUID.randomUUID().toString().substring(0, 6);
        Map<String, Object> promptTpl = mcpClient.getPromptTemplate(promptTemplateName, Map.of("city", city));
        frames.add(formatSseEvent(threadId, "TOOL_CALL", Map.of(
                "callId", promptCallId,
                "tool", promptTemplateName,
                "protocol", "MCP",
                "callType", "mcp:prompt",
                "server", "nomad-mcp-concierge (:8081)",
                "args", Map.of("city", city))));
        frames.add(formatSseEvent(threadId, "TOOL_RESULT", Map.of(
                "callId", promptCallId,
                "tool", promptTemplateName,
                "protocol", "MCP",
                "callType", "mcp:prompt",
                "result", promptTpl)));

        String resCallId = "call-res-" + UUID.randomUUID().toString().substring(0, 6);
        Map<String, Object> prefs = mcpClient.readResource("traveler://preferences");
        frames.add(formatSseEvent(threadId, "TOOL_CALL", Map.of(
                "callId", resCallId,
                "tool", "traveler://preferences",
                "protocol", "MCP",
                "callType", "mcp:resource",
                "server", "nomad-mcp-concierge (:8081)",
                "args", Map.of("uri", "traveler://preferences"))));
        frames.add(formatSseEvent(threadId, "TOOL_RESULT", Map.of(
                "callId", resCallId,
                "tool", "traveler://preferences",
                "protocol", "MCP",
                "callType", "mcp:resource",
                "result", prefs)));

        AgentChatResponse chat = executeChat(request);

        frames.add(formatSseEvent(threadId, "STATUS", Map.of(
                "phase", "executing_mcp_tool",
                "message", "Querying live Open-Meteo weather, Wikipedia cultural dossier & Apple iTunes 30s audio preview for " + city)));

        for (Map<String, Object> call : chat.mcpCalls()) {
            String callId = "call-" + UUID.randomUUID().toString().substring(0, 8);
            frames.add(formatSseEvent(threadId, "TOOL_CALL", Map.of(
                    "callId", callId,
                    "tool", call.get("toolName"),
                    "protocol", "MCP",
                    "callType", "mcp:tool",
                    "server", call.get("server"),
                    "args", call.get("arguments"))));
            frames.add(formatSseEvent(threadId, "TOOL_RESULT", Map.of(
                    "callId", callId,
                    "tool", call.get("toolName"),
                    "protocol", "MCP",
                    "callType", "mcp:tool",
                    "result", call.get("result"))));
        }

        String fnCallId = "call-fn-" + UUID.randomUUID().toString().substring(0, 6);
        frames.add(formatSseEvent(threadId, "TOOL_CALL", Map.of(
                "callId", fnCallId,
                "tool", "calculateWalkingCorridor",
                "protocol", "LOCAL",
                "callType", "tool:function",
                "server", "nomad-orchestrator (:8080)",
                "args", Map.of("city", city, "stops", 3))));
        frames.add(formatSseEvent(threadId, "TOOL_RESULT", Map.of(
                "callId", fnCallId,
                "tool", "calculateWalkingCorridor",
                "protocol", "LOCAL",
                "callType", "tool:function",
                "result", Map.of("distanceKm", 4.2, "walkingMinutes", 52, "scenicRating", "9.8/10"))));

        boolean wantsChoices = isChoiceElicitationRequest(prompt);
        String uiCallId = "call-ui-" + UUID.randomUUID().toString().substring(0, 6);
        frames.add(formatSseEvent(threadId, "TOOL_CALL", Map.of(
                "callId", uiCallId,
                "tool", "renderInteractiveConciergeDeck",
                "protocol", "AG-UI",
                "callType", "tool:ui",
                "server", "agui-surface-renderer",
                "args", Map.of("city", city, "components", wantsChoices ? List.of("MediaGallery", "StructuredResponseBlock") : List.of("MediaGallery")))));
        frames.add(formatSseEvent(threadId, "TOOL_RESULT", Map.of(
                "callId", uiCallId,
                "tool", "renderInteractiveConciergeDeck",
                "protocol", "AG-UI",
                "callType", "tool:ui",
                "result", Map.of("rendered", true, "city", city))));

        String discStepId = "a2a-disc-" + UUID.randomUUID().toString().substring(0, 6);
        frames.add(formatSseEvent(threadId, "STEP_STARTED", Map.of(
                "stepId", discStepId,
                "protocol", "A2A",
                "callType", "a2a:discovery",
                "agentName", "Nomad-A2A-Specialist",
                "skillId", "/.well-known/agent-card.json")));
        frames.add(formatSseEvent(threadId, "STEP_FINISHED", Map.of(
                "stepId", discStepId,
                "protocol", "A2A",
                "callType", "a2a:discovery",
                "agentName", "Nomad-A2A-Specialist",
                "skillId", "/.well-known/agent-card.json",
                "summary", "Discovered A2A AgentCard v1.4.0 at :8082 (3 travel specialist skills verified)",
                "report", a2aClient.getAgentCard())));

        for (Map<String, Object> handoff : chat.a2aHandoffs()) {
            frames.add(formatSseEvent(threadId, "STEP_STARTED", Map.of(
                    "stepId", handoff.get("taskId"),
                    "protocol", "A2A",
                    "callType", "a2a:task",
                    "agentName", handoff.get("agentName"),
                    "skillId", handoff.get("skillId"))));
            frames.add(formatSseEvent(threadId, "STEP_FINISHED", Map.of(
                    "stepId", handoff.get("taskId"),
                    "protocol", "A2A",
                    "callType", "a2a:task",
                    "agentName", handoff.get("agentName"),
                    "skillId", handoff.get("skillId"),
                    "summary", handoff.get("summary"),
                    "report", handoff.get("report"))));

            String artStepId = "a2a-art-" + UUID.randomUUID().toString().substring(0, 6);
            frames.add(formatSseEvent(threadId, "STEP_STARTED", Map.of(
                    "stepId", artStepId,
                    "protocol", "A2A",
                    "callType", "a2a:artifact",
                    "agentName", handoff.get("agentName"),
                    "skillId", "artifact:" + handoff.get("skillId"))));
            frames.add(formatSseEvent(threadId, "STEP_FINISHED", Map.of(
                    "stepId", artStepId,
                    "protocol", "A2A",
                    "callType", "a2a:artifact",
                    "agentName", handoff.get("agentName"),
                    "skillId", "artifact:" + handoff.get("skillId"),
                    "summary", "Signed A2A Dossier Artifact delivered to orchestrator",
                    "report", handoff.get("report"))));
        }

        Map<String, Object> stateDelta = new LinkedHashMap<>();
        stateDelta.put("clusterState", chat.clusterState());
        stateDelta.put("lastTool", chat.mcpCalls().getFirst().get("toolName"));
        stateDelta.put("lastA2aSkill", chat.a2aHandoffs().getFirst().get("skillId"));
        stateDelta.put("media", buildTurnMedia(city));
        if (wantsChoices) {
            stateDelta.put("interaction", buildTurnInteraction(city, lower));
        }
        stateDelta.put("sources", buildTurnSources(city));
        stateDelta.put("followUps", buildTurnFollowUps(city));

        frames.add(formatSseEvent(threadId, "STATE_DELTA", stateDelta));

        appendTextDeltaFrames(frames, threadId, chat.reply());

        frames.add(formatSseEvent(threadId, "RUN_FINISHED", Map.of(
                "threadId", threadId,
                "status", "completed")));

        return frames;
    }

    private void streamLiveOllamaRun(AgentRunRequest request, String mode, MultiEmitter<? super String> emitter) {
        String threadId = resolveThreadId(request);
        String prompt = request != null && request.prompt() != null && !request.prompt().isBlank()
                ? request.prompt()
                : "Curate a 4-night cultural and musical escape in Kyoto";
        String lower = prompt.toLowerCase(Locale.ROOT);
        String ollamaModel = extractOllamaModel(mode);

        try {
            emitter.emit(formatSseEvent(threadId, "RUN_STARTED", Map.of(
                    "threadId", threadId,
                    "status", "started",
                    "llmMode", "ollama:" + ollamaModel)));

            emitter.emit(formatSseEvent(threadId, "STATE_SNAPSHOT", Map.of(
                    "clusterState", mcpClient.readClusterState(),
                    "clientState", request != null && request.state() != null ? request.state() : Map.of())));

            List<String> hitlFrames = new ArrayList<>();
            if (checkAndRecordHitlInterrupt(threadId, prompt, lower, hitlFrames)) {
                hitlFrames.forEach(emitter::emit);
                emitter.complete();
                return;
            }

            StringBuilder toolContext = new StringBuilder();
            String city = detectNomadCity(prompt, request);
            String genre = detectMusicGenre(lower);
            String promptTemplateName = lower.contains("walk") || lower.contains("music")
                    ? "local-music-and-food-walk"
                    : "weekend-cultural-escape";

            emitter.emit(formatSseEvent(threadId, "STATUS", Map.of(
                    "phase", "reasoning",
                    "durationMs", 1180,
                    "thought", "Synthesizing " + city + " cultural dossier with " + ollamaModel
                            + ": expanding MCP prompt template, reading traveler://preferences, querying Open-Meteo & iTunes 30s audio preview (:8081), and delegating Frankfurter FX & REST Countries audit to Nomad-A2A-Specialist (:8082).",
                    "message", "Reasoning across AG-UI + MCP (:8081) + A2A (:8082) with " + ollamaModel)));

            String promptCallId = "call-prompt-" + UUID.randomUUID().toString().substring(0, 6);
            Map<String, Object> promptTpl = mcpClient.getPromptTemplate(promptTemplateName, Map.of("city", city));
            emitter.emit(formatSseEvent(threadId, "TOOL_CALL", Map.of(
                    "callId", promptCallId,
                    "tool", promptTemplateName,
                    "protocol", "MCP",
                    "callType", "mcp:prompt",
                    "server", "nomad-mcp-concierge (:8081)",
                    "args", Map.of("city", city))));
            emitter.emit(formatSseEvent(threadId, "TOOL_RESULT", Map.of(
                    "callId", promptCallId,
                    "tool", promptTemplateName,
                    "protocol", "MCP",
                    "callType", "mcp:prompt",
                    "result", promptTpl)));
            toolContext.append("\n[MCP Prompt `").append(promptTemplateName).append("`]: ")
                    .append(promptTpl.getOrDefault("expandedPrompt", ""));

            String resCallId = "call-res-" + UUID.randomUUID().toString().substring(0, 6);
            Map<String, Object> prefs = mcpClient.readResource("traveler://preferences");
            emitter.emit(formatSseEvent(threadId, "TOOL_CALL", Map.of(
                    "callId", resCallId,
                    "tool", "traveler://preferences",
                    "protocol", "MCP",
                    "callType", "mcp:resource",
                    "server", "nomad-mcp-concierge (:8081)",
                    "args", Map.of("uri", "traveler://preferences"))));
            emitter.emit(formatSseEvent(threadId, "TOOL_RESULT", Map.of(
                    "callId", resCallId,
                    "tool", "traveler://preferences",
                    "protocol", "MCP",
                    "callType", "mcp:resource",
                    "result", prefs)));
            toolContext.append("\n[MCP Resource `traveler://preferences`]: ")
                    .append(objectMapper.writeValueAsString(prefs));

            emitter.emit(formatSseEvent(threadId, "STATUS", Map.of(
                    "phase", "executing_mcp_tool",
                    "message", "Querying live Open-Meteo weather, Wikipedia cultural dossier & Apple iTunes 30s audio preview for " + city)));

            for (Map<String, Object> call : List.of(
                    mcpClient.callTool("fetchCityWeather", Map.of("city", city)),
                    mcpClient.callTool("discoverLocalCultureAndMedia", Map.of("destination", city, "genre", genre)))) {
                String callId = "call-" + UUID.randomUUID().toString().substring(0, 8);
                emitter.emit(formatSseEvent(threadId, "TOOL_CALL", Map.of(
                        "callId", callId,
                        "tool", call.get("toolName"),
                        "protocol", "MCP",
                        "callType", "mcp:tool",
                        "server", call.get("server"),
                        "args", call.get("arguments"))));
                emitter.emit(formatSseEvent(threadId, "TOOL_RESULT", Map.of(
                        "callId", callId,
                        "tool", call.get("toolName"),
                        "protocol", "MCP",
                        "callType", "mcp:tool",
                        "result", call.get("result"))));
                toolContext.append("\n[MCP `").append(call.get("toolName")).append("`]: ")
                        .append(objectMapper.writeValueAsString(call.get("result")));
            }

            String fnCallId = "call-fn-" + UUID.randomUUID().toString().substring(0, 6);
            emitter.emit(formatSseEvent(threadId, "TOOL_CALL", Map.of(
                    "callId", fnCallId,
                    "tool", "calculateWalkingCorridor",
                    "protocol", "LOCAL",
                    "callType", "tool:function",
                    "server", "nomad-orchestrator (:8080)",
                    "args", Map.of("city", city, "stops", 3))));
            emitter.emit(formatSseEvent(threadId, "TOOL_RESULT", Map.of(
                    "callId", fnCallId,
                    "tool", "calculateWalkingCorridor",
                    "protocol", "LOCAL",
                    "callType", "tool:function",
                    "result", Map.of("distanceKm", 4.2, "walkingMinutes", 52, "scenicRating", "9.8/10"))));

            boolean wantsChoices = isChoiceElicitationRequest(prompt);
            String uiCallId = "call-ui-" + UUID.randomUUID().toString().substring(0, 6);
            emitter.emit(formatSseEvent(threadId, "TOOL_CALL", Map.of(
                    "callId", uiCallId,
                    "tool", "renderInteractiveConciergeDeck",
                    "protocol", "AG-UI",
                    "callType", "tool:ui",
                    "server", "agui-surface-renderer",
                    "args", Map.of("city", city, "components", wantsChoices ? List.of("MediaGallery", "StructuredResponseBlock") : List.of("MediaGallery")))));
            emitter.emit(formatSseEvent(threadId, "TOOL_RESULT", Map.of(
                    "callId", uiCallId,
                    "tool", "renderInteractiveConciergeDeck",
                    "protocol", "AG-UI",
                    "callType", "tool:ui",
                    "result", Map.of("rendered", true, "city", city))));

            String discStepId = "a2a-disc-" + UUID.randomUUID().toString().substring(0, 6);
            emitter.emit(formatSseEvent(threadId, "STEP_STARTED", Map.of(
                    "stepId", discStepId,
                    "protocol", "A2A",
                    "callType", "a2a:discovery",
                    "agentName", "Nomad-A2A-Specialist",
                    "skillId", "/.well-known/agent-card.json")));
            emitter.emit(formatSseEvent(threadId, "STEP_FINISHED", Map.of(
                    "stepId", discStepId,
                    "protocol", "A2A",
                    "callType", "a2a:discovery",
                    "agentName", "Nomad-A2A-Specialist",
                    "skillId", "/.well-known/agent-card.json",
                    "summary", "Discovered A2A AgentCard v1.4.0 at :8082 (3 travel specialist skills verified)",
                    "report", a2aClient.getAgentCard())));

            List<String> a2aSkills = new ArrayList<>(List.of("convert-and-audit-budget", "verify-travel-advisory"));
            if (lower.contains("walk") || lower.contains("cadence") || lower.contains("evening")) {
                a2aSkills.add("curate-neighborhood-cadence");
            }
            for (String skillId : a2aSkills) {
                String stepId = "a2a-" + skillId.substring(0, 3) + "-" + UUID.randomUUID().toString().substring(0, 6);
                emitter.emit(formatSseEvent(threadId, "STEP_STARTED", Map.of(
                        "stepId", stepId,
                        "protocol", "A2A",
                        "callType", "a2a:task",
                        "agentName", "Nomad-A2A-Specialist",
                        "skillId", skillId)));
                Map<String, Object> handoff = a2aClient.delegateTask(stepId, skillId, prompt);
                emitter.emit(formatSseEvent(threadId, "STEP_FINISHED", Map.of(
                        "stepId", stepId,
                        "protocol", "A2A",
                        "callType", "a2a:task",
                        "agentName", handoff.get("agentName"),
                        "skillId", skillId,
                        "summary", handoff.get("summary"),
                        "report", handoff.get("report"))));

                String artStepId = "a2a-art-" + UUID.randomUUID().toString().substring(0, 6);
                emitter.emit(formatSseEvent(threadId, "STEP_STARTED", Map.of(
                        "stepId", artStepId,
                        "protocol", "A2A",
                        "callType", "a2a:artifact",
                        "agentName", handoff.get("agentName"),
                        "skillId", "artifact:" + skillId)));
                emitter.emit(formatSseEvent(threadId, "STEP_FINISHED", Map.of(
                        "stepId", artStepId,
                        "protocol", "A2A",
                        "callType", "a2a:artifact",
                        "agentName", handoff.get("agentName"),
                        "skillId", "artifact:" + skillId,
                        "summary", "Signed A2A Dossier Artifact delivered to orchestrator",
                        "report", handoff.get("report"))));

                toolContext.append("\n[A2A `").append(skillId).append("`]: ")
                        .append(handoff.get("summary"));
            }

            Map<String, Object> stateDelta = new LinkedHashMap<>();
            stateDelta.put("clusterState", mcpClient.readClusterState());
            stateDelta.put("lastTool", "discoverLocalCultureAndMedia");
            stateDelta.put("lastA2aSkill", "convert-and-audit-budget");
            stateDelta.put("media", buildTurnMedia(city));
            if (wantsChoices) {
                stateDelta.put("interaction", generateOllamaInteractiveChoices(ollamaModel, city, prompt, lower));
            }
            stateDelta.put("sources", buildTurnSources(city));
            stateDelta.put("followUps", buildTurnFollowUps(city));
            emitter.emit(formatSseEvent(threadId, "STATE_DELTA", stateDelta));

            emitter.emit(formatSseEvent(threadId, "STATUS", Map.of(
                    "phase", "streaming_llm_tokens",
                    "message", "Streaming live tokens from Ollama (" + ollamaModel + ")")));

            boolean streamedFromOllama = streamTokensFromOllama(threadId, ollamaModel, prompt, toolContext.toString(), emitter);
            if (!streamedFromOllama) {
                AgentChatResponse fallback = executeChat(request);
                List<String> fallbackFrames = new ArrayList<>();
                appendTextDeltaFrames(fallbackFrames, threadId, fallback.reply());
                fallbackFrames.forEach(emitter::emit);
            }

            emitter.emit(formatSseEvent(threadId, "RUN_FINISHED", Map.of(
                    "threadId", threadId,
                    "status", "completed",
                    "llmMode", "ollama:" + ollamaModel)));
            emitter.complete();
        } catch (Exception e) {
            emitter.emit(formatSseEvent(threadId, "TEXT_DELTA", Map.of(
                    "content", "Error communicating with orchestrator: " + e.getMessage())));
            emitter.emit(formatSseEvent(threadId, "RUN_FINISHED", Map.of(
                    "threadId", threadId,
                    "status", "failed")));
            emitter.complete();
        }
    }

    private boolean streamTokensFromOllama(
            String threadId,
            String model,
            String userPrompt,
            String toolContext,
            MultiEmitter<? super String> emitter) {
        try {
            boolean hasInteractiveCard = isChoiceElicitationRequest(userPrompt);
            String systemPrompt = "You are Nomad, an authoritative, refined AI Travel, Architecture, Weather, FX & Local Culture Concierge. "
                    + "Ground your response in the live Open-Meteo weather, Wikipedia cultural summary, Apple iTunes 30-second local music preview, "
                    + "Frankfurter FX conversion, and REST Countries travel advisory provided in the context. "
                    + "Format your response with a clean Markdown heading and concise bullet points covering atmospheric architecture, local listening/music culture, and practical weather/currency insights."
                    + (hasInteractiveCard
                            ? " An interactive AG-UI clickable selection card is rendered directly below your message—invite the traveler to click their preferred option(s) in the card below rather than typing a number."
                            : "");

            String augmentedUserPrompt = toolContext.isEmpty()
                    ? userPrompt
                    : userPrompt + "\n\nLive API Telemetry & Specialist Dossier:" + toolContext
                            + "\nSynthesize these live findings into an atmospheric, practical response.";

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("stream", true);
            payload.put("think", false);
            payload.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", augmentedUserPrompt)));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaBaseUrl + "/api/chat"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<InputStream> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                return false;
            }

            boolean emittedAny = false;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    JsonNode node = objectMapper.readTree(line);
                    JsonNode contentNode = node.path("message").path("content");
                    if (!contentNode.isMissingNode() && !contentNode.asText().isEmpty()) {
                        emitter.emit(formatSseEvent(threadId, "TEXT_DELTA", Map.of(
                                "content", contentNode.asText())));
                        emittedAny = true;
                    }
                }
            }
            return emittedAny;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean checkAndRecordHitlInterrupt(String threadId, String prompt, String lower, List<String> frames) {
        if (lower.startsWith("/hitl") || lower.contains("reserve") || lower.contains("book ")) {
            String city = detectNomadCity(prompt, null);
            String stopTitle = McpGatewayClient.defaultSanctuaryForCity(city);
            int nights = extractNights(lower, 4);
            double costUsd = switch (city.toLowerCase(Locale.ROOT)) {
                case "lisbon" -> 1760.0;
                case "reykjavik" -> 2240.0;
                default -> 1840.0;
            };
            if (costUsd > HITL_SANCTUARY_THRESHOLD_USD) {
                String callId = "call-hitl-" + UUID.randomUUID().toString().substring(0, 6);
                Map<String, Object> pendingAction = new LinkedHashMap<>();
                pendingAction.put("callId", callId);
                pendingAction.put("toolName", "updateTripItinerary");
                pendingAction.put("city", city);
                pendingAction.put("stopTitle", stopTitle);
                pendingAction.put("nights", nights);
                pendingAction.put("costUsd", costUsd);
                pendingAction.put("prompt", prompt);
                pendingApprovals.put(threadId, pendingAction);

                frames.add(formatSseEvent(threadId, "STATUS", Map.of(
                        "phase", "awaiting_human_approval",
                        "message", String.format(Locale.ROOT,
                                "HITL Gate: Holding %s in %s (%d nights, $%.0f USD > $%.0f limit) requires traveler authorization",
                                stopTitle, city, nights, costUsd, HITL_SANCTUARY_THRESHOLD_USD))));
                frames.add(formatSseEvent(threadId, "TOOL_CALL", Map.of(
                        "callId", callId,
                        "tool", "updateTripItinerary",
                        "protocol", "MCP",
                        "callType", "hitl:approval",
                        "server", "nomad-mcp-concierge (:8081)",
                        "args", Map.of("city", city, "stopTitle", stopTitle, "nights", nights, "costUsd", costUsd),
                        "requiresApproval", true)));
                frames.add(formatSseEvent(threadId, "RUN_FINISHED", Map.of(
                        "threadId", threadId,
                        "status", "interrupted",
                        "approvalRequired", true,
                        "pendingAction", pendingAction)));
                return true;
            }
        }
        return false;
    }

    public Multi<String> approvePendingAction(AgentApprovalRequest approval) {
        String threadId = approval != null && approval.threadId() != null ? approval.threadId() : "thread-default";
        Map<String, Object> pending = pendingApprovals.remove(threadId);
        List<String> frames = new ArrayList<>();

        frames.add(formatSseEvent(threadId, "RUN_STARTED", Map.of(
                "threadId", threadId,
                "status", "resumed_from_hitl")));

        if (approval == null || !approval.approved() || pending == null) {
            frames.add(formatSseEvent(threadId, "STATUS", Map.of(
                    "phase", "hitl_rejected",
                    "message", "Traveler declined pending sanctuary reservation hold")));
            frames.add(formatSseEvent(threadId, "RUN_FINISHED", Map.of(
                    "threadId", threadId,
                    "status", "cancelled")));
            return Multi.createFrom().iterable(frames);
        }

        String callId = String.valueOf(pending.getOrDefault("callId", "call-hitl"));
        Map<String, Object> mods = approval.modifiedArgs() != null ? approval.modifiedArgs() : Map.of();

        String city = String.valueOf(mods.getOrDefault("city", pending.getOrDefault("city", "Kyoto")));
        String stopTitle = String.valueOf(mods.getOrDefault("stopTitle", pending.getOrDefault("stopTitle", McpGatewayClient.defaultSanctuaryForCity(city))));
        int nights = mods.get("nights") instanceof Number n ? n.intValue()
                : (pending.get("nights") instanceof Number pn ? pn.intValue() : 4);
        double costUsd = mods.get("costUsd") instanceof Number n ? n.doubleValue()
                : (pending.get("costUsd") instanceof Number pn ? pn.doubleValue() : 1840.0);

        Map<String, Object> result = mcpClient.callTool("updateTripItinerary", Map.of(
                "city", city,
                "stopTitle", stopTitle,
                "nights", nights,
                "costUsd", costUsd));
        Map<String, Object> handoff = a2aClient.delegateTask(
                "a2a-" + threadId,
                "convert-and-audit-budget",
                "Confirm reservation budget for " + city);

        frames.add(formatSseEvent(threadId, "TOOL_RESULT", Map.of(
                "callId", callId,
                "tool", "updateTripItinerary",
                "protocol", "MCP",
                "callType", "mcp:tool",
                "approvedByOperator", true,
                "result", result.get("result"))));
        frames.add(formatSseEvent(threadId, "STEP_STARTED", Map.of(
                "stepId", handoff.get("taskId"),
                "protocol", "A2A",
                "callType", "a2a:task",
                "agentName", handoff.get("agentName"),
                "skillId", handoff.get("skillId"))));
        frames.add(formatSseEvent(threadId, "STEP_FINISHED", Map.of(
                "stepId", handoff.get("taskId"),
                "protocol", "A2A",
                "callType", "a2a:task",
                "agentName", handoff.get("agentName"),
                "skillId", handoff.get("skillId"),
                "summary", handoff.get("summary"),
                "report", handoff.get("report"))));
        frames.add(formatSseEvent(threadId, "STATE_DELTA", Map.of(
                "clusterState", mcpClient.readClusterState(),
                "hitlApproved", true,
                "reason", approval.reason() == null ? "Confirmed by Traveler" : approval.reason(),
                "followUps", buildTurnFollowUps(city))));

        String reply = String.format(Locale.ROOT,
                "Reservation confirmed (%s). Locked **%s** in **%s** for **%d nights** ($%.0f USD) via MCP (`:8081`) and audited FX settlement with `%s` (`:8082`).",
                approval.reason() == null ? "Traveler Approved" : approval.reason(),
                stopTitle,
                city,
                nights,
                costUsd,
                handoff.get("agentName"));
        appendTextDeltaFrames(frames, threadId, reply);
        frames.add(formatSseEvent(threadId, "RUN_FINISHED", Map.of(
                "threadId", threadId,
                "status", "completed")));
        return Multi.createFrom().iterable(frames);
    }

    private String detectNomadCity(String prompt, AgentRunRequest request) {
        String raw = prompt == null ? "" : prompt.replaceFirst("^/[a-z]+\\s*", "").trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.contains("lisbon") || lower.contains("portugal") || lower.contains("fado") || lower.contains("alfama")) {
            return "Lisbon";
        }
        if (lower.contains("reykjavik") || lower.contains("iceland") || lower.contains("aurora") || lower.contains("harpa")) {
            return "Reykjavik";
        }
        if (lower.contains("kyoto") || lower.contains("koto") || lower.contains("arashiyama") || lower.contains("gion")) {
            return "Kyoto";
        }
        for (String candidate : List.of(
                "Beirut", "Amman", "Dubai", "Abu Dhabi", "Doha", "Riyadh", "Muscat", "Kuwait City", "Manama",
                "Jerusalem", "Tel Aviv", "Tunis", "Algiers", "Casablanca", "Marrakech", "Cairo", "Alexandria",
                "Nairobi", "Cape Town", "Johannesburg", "Lagos", "Accra", "Addis Ababa", "Dakar",
                "Tokyo", "Osaka", "Paris", "Rome", "Milan", "Florence", "Venice", "Barcelona", "Madrid", "Seville",
                "London", "Edinburgh", "Dublin", "Berlin", "Munich", "Vienna", "Zurich", "Geneva", "Amsterdam",
                "Brussels", "Copenhagen", "Stockholm", "Oslo", "Helsinki", "Prague", "Budapest", "Warsaw",
                "Dubrovnik", "Split", "Athens", "Santorini", "Istanbul", "Cappadocia", "Tbilisi", "Yerevan", "Baku",
                "Seoul", "Busan", "Bangkok", "Chiang Mai", "Singapore", "Taipei", "Hong Kong", "Shanghai", "Beijing",
                "Hanoi", "Ho Chi Minh City", "Kuala Lumpur", "Jakarta", "Bali", "Manila", "Mumbai", "New Delhi",
                "Jaipur", "Kathmandu", "Colombo", "Sydney", "Melbourne", "Auckland", "Queenstown",
                "New York", "San Francisco", "Chicago", "Los Angeles", "New Orleans", "Miami", "Seattle", "Boston",
                "Toronto", "Vancouver", "Montreal", "Oaxaca", "Mexico City", "Havana", "Bogota", "Medellin",
                "Cartagena", "Lima", "Cusco", "Santiago", "Valparaiso", "Buenos Aires", "Montevideo",
                "Rio de Janeiro", "Sao Paulo")) {
            if (lower.contains(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
        Matcher m = Pattern.compile(
                "\\b(?:in|to|for|visit|explore|around|about)\\s+([A-Z][a-zA-Z]+(?:\\s+[A-Z][a-zA-Z]+)?)\\b")
                .matcher(raw);
        while (m.find()) {
            String candidate = m.group(1).trim();
            String candLower = candidate.toLowerCase(Locale.ROOT);
            if (!List.of("the", "this", "that", "my", "our", "your", "local", "weekend", "evening", "morning", "night").contains(candLower)) {
                return candidate;
            }
        }
        if (request != null && request.state() != null) {
            if (request.state().get("city") instanceof String explicitCity && !explicitCity.isBlank()) {
                return explicitCity.trim();
            }
            if (request.state().get("nomadTrip") instanceof Map<?, ?> trip
                    && trip.get("activeCity") instanceof String activeCity
                    && !activeCity.isBlank()) {
                return activeCity.trim();
            }
        }
        Map<String, Object> currentTrip = activeNomadTripState();
        if (currentTrip.get("activeCity") instanceof String currentCity && !currentCity.isBlank()) {
            return currentCity.trim();
        }
        return "Kyoto";
    }

    private String detectMusicGenre(String lower) {
        if (lower.contains("fado")) {
            return "fado";
        }
        if (lower.contains("jazz") || lower.contains("vinyl") || lower.contains("kissaten")) {
            return "jazz";
        }
        if (lower.contains("ambient") || lower.contains("nordic") || lower.contains("strings")) {
            return "ambient";
        }
        return "traditional";
    }

    private int extractNights(String text, int fallback) {
        if (text == null) {
            return fallback;
        }
        Matcher m = Pattern.compile("\\b(\\d+)\\s*[- ]?nights?\\b", Pattern.CASE_INSENSITIVE).matcher(text);
        if (m.find()) {
            try {
                int val = Integer.parseInt(m.group(1));
                if (val > 0 && val <= 30) {
                    return val;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> activeNomadTripState() {
        Map<String, Object> state = mcpClient.readClusterState();
        if (state != null && state.get("nomadTrip") instanceof Map<?, ?> trip) {
            return (Map<String, Object>) trip;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private String buildNomadConciergeMarkdownReply(String city, String userPrompt, Map<String, Object> handoff) {
        Map<String, Object> trip = activeNomadTripState();
        Object tempObj = trip.getOrDefault("temperatureC", 18.4);
        String condition = String.valueOf(trip.getOrDefault("weatherCondition", "Clear Cedar & Autumn Mist"));
        String country = String.valueOf(trip.getOrDefault("country", A2aGatewayClient.resolveCountryForCity(city)));
        String currency = String.valueOf(trip.getOrDefault("currency", A2aGatewayClient.resolveCurrencyForCountry(country)));
        String summaryExtract = trip.get("culturalSummary") instanceof String s && !s.isBlank() ? s : null;
        String trackTitle = "Haru no Umi (Spring Sea — Koto & Shakuhachi)";
        String artistName = "Kyoto Koto Consort";
        if (trip.get("soundtrack") instanceof Map<?, ?> st) {
            trackTitle = String.valueOf(((Map<String, Object>) st).getOrDefault("trackName", trackTitle));
            artistName = String.valueOf(((Map<String, Object>) st).getOrDefault("artistName", artistName));
        }

        String cleanedPrompt = userPrompt == null ? "" : userPrompt.replaceFirst("^/[a-z]+\\s*", "").trim();
        String lowerPrompt = cleanedPrompt.toLowerCase(Locale.ROOT);

        String header = switch (city.toLowerCase(Locale.ROOT)) {
            case "lisbon" -> "### Lisbon, Portugal — Atlantic Light, Azulejos & Evening Fado";
            case "reykjavik" -> "### Reykjavik, Iceland — Basalt Geometry, Geothermal Steam & Nordic Strings";
            case "kyoto" -> "### Kyoto, Japan — Cedar Machiya, Moss Gardens & Koto Acoustics";
            case "tokyo" -> "### Tokyo, Japan — Modernist Timber, Vinyl Kissaten & Late-Night Culinary Craft";
            default -> "### " + city + ", " + country + " — Architectural, Cultural & Acoustic Dossier";
        };

        StringBuilder md = new StringBuilder(header).append("\n\n");
        if (summaryExtract != null) {
            md.append(summaryExtract).append("\n\n");
        }

        if (lowerPrompt.contains("music") || lowerPrompt.contains("soundtrack") || lowerPrompt.contains("listen") || lowerPrompt.contains("vinyl") || lowerPrompt.contains("song")) {
            md.append("#### Acoustic & Listening Curation\n");
            md.append("- **Featured 30s Apple iTunes Preview:** *").append(trackTitle).append("* by **").append(artistName).append("** (playable in the media card below)\n");
            md.append("- **Evening Listening Atmosphere:** Pair an intimate analog listening session in **").append(city)
                    .append("** with current outdoor conditions (`").append(tempObj).append("°C`, ").append(condition).append(").\n");
            md.append("- **Budget & Currency Note:** ").append(handoff.get("summary")).append("\n\n");
        } else if (lowerPrompt.contains("weather") || lowerPrompt.contains("pack") || lowerPrompt.contains("forecast") || lowerPrompt.contains("temperature")) {
            md.append("#### Live Meteorological & Packing Briefing\n");
            md.append("- **Current Open-Meteo Telemetry:** `").append(tempObj).append("°C` — **").append(condition).append("** in ").append(city).append("\n");
            md.append("- **Recommended Layers:** Breathable architectural layers suited for `").append(tempObj)
                    .append("°C` walking corridors between morning sanctuary visits and evening acoustic venues.\n");
            md.append("- **Local Soundtrack:** *").append(trackTitle).append("* — ").append(artistName).append("\n\n");
        } else {
            md.append("- **Live Open-Meteo Weather:** `").append(tempObj).append("°C` — ").append(condition).append("\n");
            md.append("- **Local Soundtrack (30s Preview):** *").append(trackTitle).append("* — **").append(artistName).append("**\n");
            md.append("- **Currency & FX Settlement (").append(currency).append("):** ").append(handoff.get("summary")).append("\n\n");
        }

        md.append("Sample the live 30-second audio preview below or select a curated ").append(city).append(" experience sequence to refine your itinerary.");
        return md.toString();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildTurnMedia(String city) {
        Map<String, Object> trip = activeNomadTripState();
        String fallbackHeroUrl = switch (city.toLowerCase(Locale.ROOT)) {
            case "lisbon" -> "https://images.unsplash.com/photo-1513735492246-483525079686?auto=format&fit=crop&w=1000&q=80";
            case "reykjavik" -> "https://images.unsplash.com/photo-1504893524553-b855bce32c67?auto=format&fit=crop&w=1000&q=80";
            default -> "https://images.unsplash.com/photo-1493976040374-85c8e12f0c0e?auto=format&fit=crop&w=1000&q=80";
        };
        String heroUrl = trip.get("heroImageUrl") instanceof String h && !h.isBlank() ? h : fallbackHeroUrl;
        String trackTitle = switch (city.toLowerCase(Locale.ROOT)) {
            case "lisbon" -> "Canção do Mar (Acoustic Fado Session)";
            case "reykjavik" -> "Hoppípolla — Nordic Strings";
            default -> "Haru no Umi (Spring Sea — Koto & Shakuhachi)";
        };
        String artist = switch (city.toLowerCase(Locale.ROOT)) {
            case "lisbon" -> "Alfama Guitarra Ensemble";
            case "reykjavik" -> "Reykjavik Chamber Collective";
            default -> "Kyoto Koto Consort";
        };
        String previewUrl = "https://www.w3schools.com/html/horse.mp3";
        String artworkUrl = heroUrl;
        if (trip.get("soundtrack") instanceof Map<?, ?> st) {
            Map<String, Object> track = (Map<String, Object>) st;
            trackTitle = String.valueOf(track.getOrDefault("trackName", trackTitle));
            artist = String.valueOf(track.getOrDefault("artistName", artist));
            previewUrl = String.valueOf(track.getOrDefault("previewUrl", previewUrl));
            artworkUrl = String.valueOf(track.getOrDefault("artworkUrl", heroUrl));
        }
        return List.of(
                Map.of(
                        "type", "image",
                        "title", city + " — Architectural & Cultural Landmark",
                        "subtitle", "Wikipedia REST API • Live Destination Photography",
                        "url", heroUrl),
                Map.of(
                        "type", "audio",
                        "title", trackTitle,
                        "subtitle", artist + " • Apple iTunes 30s Preview",
                        "url", previewUrl,
                        "artworkUrl", artworkUrl,
                        "durationSeconds", 30));
    }

    static boolean isChoiceElicitationRequest(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return false;
        }
        String trimmed = prompt.trim();
        if (trimmed.startsWith("Selected itinerary preference:")
                || trimmed.startsWith("Confirmed multi-select itinerary preferences:")) {
            return false;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return lower.startsWith("/choices")
                || lower.contains("option")
                || lower.contains("choice")
                || lower.contains("pick")
                || lower.contains("choose")
                || lower.contains("select")
                || lower.contains("compare")
                || lower.contains("alternative")
                || lower.contains("versus")
                || lower.contains(" vs ");
    }

    static boolean isMultiSelectRequest(String lower) {
        if (lower == null) {
            return false;
        }
        return lower.contains("multi")
                || lower.contains("multiple")
                || lower.contains("several")
                || lower.contains("combine")
                || lower.contains("checkbox")
                || lower.contains("all that apply")
                || lower.contains("more than one");
    }

    static int extractRequestedOptionCount(String lower) {
        if (lower == null) {
            return 3;
        }
        Matcher digitMatcher = Pattern.compile("\\b([2-6])\\s+(?:[a-zA-Z-]+\\s+){0,3}?(?:options?|choices?|alternatives?|stops?|experiences?|ideas?|spots?|venues?)\\b")
                .matcher(lower);
        if (digitMatcher.find()) {
            return Integer.parseInt(digitMatcher.group(1));
        }
        if (lower.contains("two ") || lower.contains("couple of ")) {
            return 2;
        }
        if (lower.contains("four ")) {
            return 4;
        }
        if (lower.contains("five ")) {
            return 5;
        }
        if (lower.contains("six ")) {
            return 6;
        }
        return 3;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> generateOllamaInteractiveChoices(
            String model,
            String city,
            String userPrompt,
            String lower) {
        Map<String, Object> fallback = buildTurnInteraction(city, lower);
        int count = extractRequestedOptionCount(lower);
        String requestedMode = isMultiSelectRequest(lower) ? "multi_select" : "single_select";
        String country = A2aGatewayClient.resolveCountryForCity(city);
        String currency = A2aGatewayClient.resolveCurrencyForCountry(country);

        try {
            StringBuilder slots = new StringBuilder();
            for (int i = 1; i <= count; i++) {
                if (i > 1) {
                    slots.append(", ");
                }
                slots.append("{\"id\": \"option-").append(i)
                        .append("\", \"label\": \"Distinct ").append(city).append(" Experience ").append(i)
                        .append("\", \"description\": \"1-sentence vivid cultural, architectural or musical summary\", \"badge\": \"Timing • ")
                        .append(currency).append("\"}");
            }
            String systemPrompt = "You are a JSON generator for an interactive AG-UI travel choice component. "
                    + "Respond ONLY with a valid JSON object containing EXACTLY " + count + " items in the options array: "
                    + "{\"prompt\": \"string\", \"mode\": \"" + requestedMode + "\", \"options\": [" + slots + "]}. "
                    + "Every option MUST be a real, distinct neighborhood, venue, landmark, or cultural experience in " + city + ", " + country + ".";

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("stream", false);
            payload.put("think", false);
            payload.put("format", "json");
            payload.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", "Generate exactly " + count + " distinct selectable options in " + city + " for: " + userPrompt)));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaBaseUrl + "/api/chat"))
                    .timeout(Duration.ofSeconds(12))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null) {
                JsonNode root = objectMapper.readTree(resp.body());
                String content = root.path("message").path("content").asText("");
                if (!content.isBlank()) {
                    JsonNode json = objectMapper.readTree(content);
                    JsonNode optsNode = json.path("options");
                    if (optsNode.isArray() && !optsNode.isEmpty()) {
                        List<Map<String, Object>> parsedOptions = new ArrayList<>();
                        int idx = 1;
                        for (JsonNode item : optsNode) {
                            String label = item.path("label").asText("").trim();
                            if (label.isEmpty()) {
                                continue;
                            }
                            String id = item.path("id").asText("opt-" + idx).trim();
                            String desc = item.path("description").asText("Curated local experience in " + city).trim();
                            String badge = item.path("badge").asText("Curated • " + currency).trim();
                            parsedOptions.add(Map.of(
                                    "id", id.isEmpty() ? "opt-" + idx : id + "-" + idx,
                                    "label", label,
                                    "description", desc,
                                    "badge", badge));
                            idx++;
                            if (parsedOptions.size() >= count) {
                                break;
                            }
                        }
                        if (!parsedOptions.isEmpty()) {
                            if (parsedOptions.size() < count && fallback.get("options") instanceof List<?> fallbackOpts) {
                                for (Object fbObj : fallbackOpts) {
                                    if (parsedOptions.size() >= count) {
                                        break;
                                    }
                                    if (fbObj instanceof Map<?, ?> fbMap) {
                                        parsedOptions.add((Map<String, Object>) fbMap);
                                    }
                                }
                            }
                            String promptHeading = json.path("prompt").asText(
                                    requestedMode.equals("multi_select")
                                            ? "Select multiple " + city + " experiences to combine:"
                                            : "Choose your preferred " + city + " option:").trim();
                            return Map.of(
                                    "id", "choice-" + city.toLowerCase(Locale.ROOT) + "-" + UUID.randomUUID().toString().substring(0, 4),
                                    "mode", requestedMode,
                                    "prompt", promptHeading,
                                    "options", parsedOptions);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private Map<String, Object> buildTurnInteraction(String city, String lower) {
        String mode = isMultiSelectRequest(lower) ? "multi_select" : "single_select";
        int count = extractRequestedOptionCount(lower);
        String currency = A2aGatewayClient.resolveCurrencyForCountry(A2aGatewayClient.resolveCountryForCity(city));
        String slug = city.toLowerCase(Locale.ROOT).replace(" ", "-");
        List<Map<String, Object>> allOptions = switch (city.toLowerCase(Locale.ROOT)) {
            case "lisbon" -> List.of(
                    Map.of("id", "alfama-fado", "label", "Alfama Historic Fado & Miradouro Walk", "description", "Intimate acoustic Fado tavern + Santa Luzia tile terrace", "badge", "Evening • €140"),
                    Map.of("id", "chiado-atelier", "label", "Chiado Architectural & Azulejo Atelier", "description", "Private ceramic conservation studio + Belém riverside", "badge", "Morning • €190"),
                    Map.of("id", "sintra-coastal", "label", "Cabo da Roca & Sintra Pine Sanctuary", "description", "Coastal cliff walk + modernist vineyard tasting", "badge", "Full Day • €260"),
                    Map.of("id", "principe-real-vinyl", "label", "Príncipe Real Vinyl & Petiscos Tasting", "description", "Hi-fi listening bar + natural Portuguese wine cellar", "badge", "Night • €125"),
                    Map.of("id", "maat-tejo", "label", "MAAT Riverfront Pavilion & Pastéis Masterclass", "description", "Contemporary riverside architecture + traditional bakery kitchen", "badge", "Afternoon • €155"),
                    Map.of("id", "mouraria-guitarra", "label", "Mouraria Portuguese Guitar Luthier Workshop", "description", "Meet master 12-string guitarra makers + courtyard recital", "badge", "Twilight • €175"));
            case "reykjavik" -> List.of(
                    Map.of("id", "harpa-acoustics", "label", "Harpa Basalt Hall & Nordic Strings Rehearsal", "description", "Private acoustic walk-through + harbor listening lounge", "badge", "Evening • ISK"),
                    Map.of("id", "sky-lagoon", "label", "Geothermal Cliffside Ritual & Turf Architecture", "description", "Seven-step oceanfront thermal soak + slow dining", "badge", "Afternoon • ISK"),
                    Map.of("id", "snaefellsnes", "label", "Snæfellsnes Volcanic Fjord Expedition", "description", "Black church at Búðir + glacier ridge photography", "badge", "Full Day • ISK"),
                    Map.of("id", "hallgrimskirkja-organ", "label", "Hallgrímskirkja Basalt Vault & Pipe Organ Study", "description", "Expressionist concrete tower + acoustic organ session", "badge", "Morning • ISK"),
                    Map.of("id", "marshall-house", "label", "Grandi Harbor Contemporary Art & Slow Nordic Table", "description", "Converted herring factory galleries + Arctic char tasting", "badge", "Evening • ISK"),
                    Map.of("id", "thingvellir-rift", "label", "Þingvellir Tectonic Rift & Moss Canyon Walk", "description", "Sub-Arctic canyon acoustics + geothermal bakery tasting", "badge", "Day • ISK"));
            case "kyoto" -> List.of(
                    Map.of("id", "arashiyama-dawn", "label", "Arashiyama Bamboo & Katsura Imperial Villa", "description", "Early-morning moss garden architectural walk + matcha pavilion", "badge", "Dawn • ¥24,000"),
                    Map.of("id", "gion-koto", "label", "Gion Machiya Private Koto & Kaiseki Evening", "description", "11-course seasonal Kaiseki with live Koto performance", "badge", "Evening • ¥38,000"),
                    Map.of("id", "higashiyama-ceramics", "label", "Higashiyama Raku Kiln & Vinyl Listening Bar", "description", "Private ceramic studio visit + analog jazz kissaten", "badge", "Late Night • ¥19,000"),
                    Map.of("id", "nanzenji-aqueduct", "label", "Nanzen-ji Zen Garden & Meiji Brick Aqueduct", "description", "Sub-temple dry landscape study + Tofu sanctuary lunch", "badge", "Morning • ¥16,000"),
                    Map.of("id", "fushimi-sake", "label", "Fushimi Timber Brewery & Shakuhachi Recital", "description", "Spring-water sake cellar tasting + bamboo flute acoustics", "badge", "Afternoon • ¥21,000"),
                    Map.of("id", "pontocho-vinyl", "label", "Kamo Riverbank & Analog Kissaten Sanctuary", "description", "Rare Japanese jazz pressings + hand-pouredネルドリップ coffee", "badge", "Night • ¥14,000"));
            case "tokyo" -> List.of(
                    Map.of("id", "aoyama-architecture", "label", "Omotesando & Aoyama Modernist Architecture Walk", "description", "Nezu Museum bamboo courtyard + Tadao Ando concrete galleries", "badge", "Morning • ¥18,000"),
                    Map.of("id", "shibuya-kissaten", "label", "Shibuya & Daikanyama Vinyl Kissaten Circuit", "description", "Hi-fi tube-amplifier jazz listening bars + artisanal coffee", "badge", "Evening • ¥22,000"),
                    Map.of("id", "ginza-omakase", "label", "Ginza Counter Omakase & Tsukiji Craft Study", "description", "Intimate 10-seat seasonal Edomae tasting + sake pairing", "badge", "Night • ¥42,000"),
                    Map.of("id", "yanaka-timber", "label", "Yanaka Pre-War Timber Alleys & Atelier Walk", "description", "Historic craft workshops + quiet temple courtyard tea", "badge", "Afternoon • ¥15,000"),
                    Map.of("id", "shimokitazawa-indie", "label", "Shimokitazawa Independent Record & Izakaya Walk", "description", "Crate-digging vinyl boutiques + charcoal robata dining", "badge", "Late Night • ¥19,000"),
                    Map.of("id", "kiyosumi-gardens", "label", "Kiyosumi Landscape Stones & Roastery Study", "description", "Strolling pond garden + single-origin coffee lab", "badge", "Morning • ¥12,000"));
            default -> List.of(
                    Map.of("id", slug + "-arch", "label", city + " Architectural Landmarks & Courtyards", "description", "Private curator-led walk through historic and modernist districts in " + city, "badge", "Morning • " + currency),
                    Map.of("id", slug + "-music", "label", city + " Evening Acoustic & Vinyl Listening Session", "description", "Intimate neighborhood performance and local culinary tasting in " + city, "badge", "Evening • " + currency),
                    Map.of("id", slug + "-atelier", "label", city + " Artisan Craft & Slow Dining Sanctuary", "description", "Studio visit with local makers followed by seasonal chef's table", "badge", "Full Day • " + currency),
                    Map.of("id", slug + "-culinary", "label", city + " Heritage Market & Regional Tasting Walk", "description", "Guided culinary tasting across traditional spice and artisan stalls in " + city, "badge", "Afternoon • " + currency),
                    Map.of("id", slug + "-rooftop", "label", city + " Twilight Rooftop & Live Ensemble", "description", "Sunset architectural panorama paired with regional acoustic music in " + city, "badge", "Twilight • " + currency),
                    Map.of("id", slug + "-galleries", "label", city + " Contemporary Galleries & Courtyard Café", "description", "Curated design museums and historic coffee house conversation in " + city, "badge", "Midday • " + currency));
        };
        List<Map<String, Object>> options = allOptions.subList(0, Math.min(count, allOptions.size()));
        String promptHeading = mode.equals("multi_select")
                ? "Select multiple " + city + " experiences to combine:"
                : "Choose your preferred " + city + " option:";
        return Map.of(
                "id", "choice-" + slug,
                "mode", mode,
                "prompt", promptHeading,
                "options", options);
    }

    private List<Map<String, Object>> buildTurnSources(String city) {
        return List.of(
                Map.of("title", "Open-Meteo Weather API (" + city + ")", "url", "https://api.open-meteo.com", "protocol", "mcp:tool"),
                Map.of("title", "Wikipedia REST API v1 (" + city + ")", "url", "https://en.wikipedia.org/wiki/" + city.replace(" ", "_"), "protocol", "mcp:tool"),
                Map.of("title", "Apple iTunes 30s Audio Preview", "url", "https://itunes.apple.com", "protocol", "mcp:tool"),
                Map.of("title", "Frankfurter ECB FX Reference", "url", "https://api.frankfurter.dev", "protocol", "a2a:task"),
                Map.of("title", "REST Countries Advisory v3.1", "url", "https://restcountries.com", "protocol", "a2a:artifact"));
    }

    private List<String> buildTurnFollowUps(String city) {
        return List.of(
                "Give me 2 random evening options in " + city + " to pick from",
                "Create 4 culinary and music experiences in " + city + " so I can select multiple",
                "What should I pack for the current weather in " + city + "?",
                "Reserve " + city + " Sanctuary Suite for 4 nights");
    }

    private void appendTextDeltaFrames(List<String> frames, String threadId, String text) {
        String[] words = text.split(" ");
        StringBuilder chunk = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            chunk.append(words[i]).append(i < words.length - 1 ? " " : "");
            if ((i + 1) % 5 == 0 || i == words.length - 1) {
                frames.add(formatSseEvent(threadId, "TEXT_DELTA", Map.of("content", chunk.toString())));
                chunk.setLength(0);
            }
        }
    }

    private String formatSseEvent(String threadId, String type, Map<String, Object> payload) {
        AgUiEvent<Map<String, Object>> event = AgUiEvent.ofType(type, threadId, payload);
        return agUiBridge.formatter().formatSse(event);
    }

    private String resolveThreadId(AgentRunRequest request) {
        if (request == null || request.threadId() == null || request.threadId().isBlank()) {
            return "thread-default";
        }
        return request.threadId();
    }

    private String resolveMode(String requestedMode) {
        if (requestedMode != null && !requestedMode.isBlank()) {
            return requestedMode;
        }
        return "deterministic";
    }

    private boolean isLiveLlmMode(String mode) {
        if (mode == null) {
            return false;
        }
        String normalized = mode.toLowerCase(Locale.ROOT);
        return normalized.startsWith("ollama") || normalized.equals("live");
    }

    private String extractOllamaModel(String mode) {
        if (mode != null && mode.contains(":")) {
            String afterFirstColon = mode.substring(mode.indexOf(':') + 1).trim();
            if (!afterFirstColon.isEmpty()) {
                return afterFirstColon;
            }
        }
        return DEFAULT_OLLAMA_MODEL;
    }
}
