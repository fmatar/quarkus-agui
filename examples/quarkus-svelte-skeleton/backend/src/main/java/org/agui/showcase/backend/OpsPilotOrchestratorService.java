package org.agui.showcase.backend;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.agui.core.domain.AgUiEvent;
import org.agui.quarkus.core.runtime.adapters.AgUiA2aBridge;
import org.agui.quarkus.core.runtime.adapters.AgUiBridge;
import org.agui.quarkus.core.runtime.adapters.AgUiMcpBridge;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * OpsPilot Quarkus Orchestrator Service (:8080).
 * Implements full in-place AG-UI Protocol (Core 6 + MCP/A2A + STATE_SNAPSHOT/STATE_DELTA + HITL approvals)
 * with Dual-Mode execution: live local Ollama LLM streaming (Java 25 Virtual Threads) and deterministic CI mode.
 */
@ApplicationScoped
public class OpsPilotOrchestratorService {

    private static final int HITL_REPLICA_THRESHOLD = 5;
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
                "orchestrator", "OpsPilot-Quarkus-Orchestrator",
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
        String prompt = request != null && request.prompt() != null ? request.prompt() : "Inspect cluster health";
        String lower = prompt.toLowerCase(Locale.ROOT);
        String mode = resolveMode(request != null ? request.mode() : null);

        List<Map<String, Object>> mcpCalls = new ArrayList<>();
        List<Map<String, Object>> a2aHandoffs = new ArrayList<>();

        if (lower.contains("log")) {
            mcpCalls.add(mcpClient.callTool("queryServiceLogs", Map.of("serviceName", "payment-gateway")));
        } else if (lower.contains("scale")) {
            int replicas = extractReplicas(lower, 4);
            String svc = "payment-gateway";
            if (lower.contains("order-processor") || lower.contains("order")) {
                svc = "order-processor";
            } else if (lower.contains("identity-provider") || lower.contains("identity")) {
                svc = "identity-provider";
            } else if (lower.contains("payment-service") || lower.contains("payment")) {
                svc = "payment-gateway";
            }
            mcpCalls.add(mcpClient.callTool("scaleDeployment", Map.of("serviceName", svc, "replicas", replicas)));
        } else if (isNomadPrompt(lower)) {
            String city = detectNomadCity(lower);
            mcpCalls.add(mcpClient.callTool("fetchCityWeather", Map.of("city", city)));
            mcpCalls.add(mcpClient.callTool("discoverLocalCultureAndMedia", Map.of("destination", city, "genre", "traditional")));
        } else {
            mcpCalls.add(mcpClient.callTool("inspectClusterHealth", Map.of()));
        }

        if (containsAny(lower, List.of("security", "posture", "cve"))) {
            a2aHandoffs.add(a2aClient.delegateTask("a2a-" + threadId, "assess-security-posture", prompt));
        } else if (containsAny(lower, List.of("remediat", "incident", "scale"))) {
            a2aHandoffs.add(a2aClient.delegateTask("a2a-" + threadId, "plan-incident-remediation", prompt));
        } else if (isNomadPrompt(lower)) {
            a2aHandoffs.add(a2aClient.delegateTask("a2a-" + threadId, "convert-and-audit-budget", prompt));
            a2aHandoffs.add(a2aClient.delegateTask("a2a-" + threadId, "verify-travel-advisory", prompt));
        } else {
            a2aHandoffs.add(a2aClient.delegateTask("a2a-" + threadId, "audit-clean-architecture", prompt));
        }

        Map<String, Object> toolCall = mcpCalls.getFirst();
        Map<String, Object> handoff = a2aHandoffs.getFirst();
        String reply = isNomadPrompt(lower)
                ? buildNomadConciergeMarkdownReply(detectNomadCity(lower), handoff)
                : String.format(
                        "OpsPilot executed MCP tool `%s` on quarkus-mcp-cluster-ops (:8081) and delegated `%s` to %s (:8082). Verdict: %s",
                        toolCall.get("toolName"),
                        handoff.get("skillId"),
                        handoff.get("agentName"),
                        handoff.get("summary"));

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
        String prompt = request != null && request.prompt() != null ? request.prompt() : "Inspect cluster health";
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

        String city = detectNomadCity(lower);
        boolean nomadFlow = isNomadPrompt(lower);

        frames.add(formatSseEvent(threadId, "STATUS", Map.of(
                "phase", "reasoning",
                "durationMs", 1240,
                "thought", nomadFlow
                        ? "Synthesizing " + city + " cultural dossier: expanding MCP prompt template, reading traveler://preferences, querying Open-Meteo & iTunes 30s audio preview (:8081), and delegating Frankfurter FX & REST Countries audit to Sentinel-A2A (:8082)."
                        : "Evaluating cluster telemetry via MCP (:8081) and delegating architectural/security verification to A2A Specialist (:8082).",
                "message", "Reasoning across AG-UI + MCP (:8081) + A2A (:8082) protocols")));

        if (nomadFlow) {
            String promptCallId = "call-prompt-" + UUID.randomUUID().toString().substring(0, 6);
            Map<String, Object> promptTpl = mcpClient.getPromptTemplate("weekend-cultural-escape", Map.of("city", city));
            frames.add(formatSseEvent(threadId, "TOOL_CALL", Map.of(
                    "callId", promptCallId,
                    "tool", "weekend-cultural-escape",
                    "protocol", "MCP",
                    "callType", "mcp:prompt",
                    "server", "nomad-mcp-concierge (:8081)",
                    "args", Map.of("city", city))));
            frames.add(formatSseEvent(threadId, "TOOL_RESULT", Map.of(
                    "callId", promptCallId,
                    "tool", "weekend-cultural-escape",
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
        }

        AgentChatResponse chat = executeChat(request);

        frames.add(formatSseEvent(threadId, "STATUS", Map.of(
                "phase", "executing_mcp_tool",
                "message", "Invoking MCP tool on quarkus-mcp-cluster-ops (:8081)")));

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

        if (nomadFlow) {
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

            String uiCallId = "call-ui-" + UUID.randomUUID().toString().substring(0, 6);
            frames.add(formatSseEvent(threadId, "TOOL_CALL", Map.of(
                    "callId", uiCallId,
                    "tool", "renderInteractiveConciergeDeck",
                    "protocol", "AG-UI",
                    "callType", "tool:ui",
                    "server", "agui-surface-renderer",
                    "args", Map.of("city", city, "components", List.of("MediaGallery", "StructuredResponseBlock")))));
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
                    "agentName", "Sentinel-A2A-Auditor",
                    "skillId", "/.well-known/agent-card.json")));
            frames.add(formatSseEvent(threadId, "STEP_FINISHED", Map.of(
                    "stepId", discStepId,
                    "protocol", "A2A",
                    "callType", "a2a:discovery",
                    "agentName", "Sentinel-A2A-Auditor",
                    "skillId", "/.well-known/agent-card.json",
                    "summary", "Discovered A2A AgentCard v1.4.0 at :8082 (5 specialist skills verified)",
                    "report", a2aClient.getAgentCard())));
        }

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

            if (nomadFlow) {
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
        }

        Map<String, Object> stateDelta = new LinkedHashMap<>();
        stateDelta.put("clusterState", chat.clusterState());
        stateDelta.put("lastTool", chat.mcpCalls().getFirst().get("toolName"));
        stateDelta.put("lastA2aSkill", chat.a2aHandoffs().getFirst().get("skillId"));
        stateDelta.put("media", buildTurnMedia(city));
        stateDelta.put("interaction", buildTurnInteraction(city, lower));
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
        String prompt = request != null && request.prompt() != null ? request.prompt() : "Inspect cluster health";
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
            boolean shouldInvokeMcp = containsAny(lower, List.of("health", "cluster", "status", "service", "log", "scale", "replica"));
            boolean shouldInvokeA2a = containsAny(lower, List.of("audit", "arch", "ring", "security", "posture", "cve", "remediat", "incident", "scale"));

            if (shouldInvokeMcp) {
                String toolName = "inspectClusterHealth";
                Map<String, Object> args = Map.of();
                if (lower.contains("log")) {
                    toolName = "queryServiceLogs";
                    args = Map.of("serviceName", lower.contains("order") ? "order-processor" : "payment-gateway");
                } else if (lower.contains("scale")) {
                    toolName = "scaleDeployment";
                    int replicas = extractReplicas(lower, 4);
                    String svc = "payment-gateway";
                    if (lower.contains("order-processor") || lower.contains("order")) {
                        svc = "order-processor";
                    } else if (lower.contains("identity-provider") || lower.contains("identity")) {
                        svc = "identity-provider";
                    } else if (lower.contains("payment-service") || lower.contains("payment")) {
                        svc = "payment-gateway";
                    }
                    args = Map.of("serviceName", svc, "replicas", replicas);
                }

                String callId = "call-" + UUID.randomUUID().toString().substring(0, 8);
                emitter.emit(formatSseEvent(threadId, "STATUS", Map.of(
                        "phase", "executing_mcp_tool",
                        "message", "Invoking MCP tool `" + toolName + "` on quarkus-mcp-cluster-ops (:8081)")));
                emitter.emit(formatSseEvent(threadId, "TOOL_CALL", Map.of(
                        "callId", callId,
                        "tool", toolName,
                        "protocol", "MCP",
                        "server", "quarkus-mcp-cluster-ops (:8081)",
                        "args", args)));

                Map<String, Object> callResult = mcpClient.callTool(toolName, args);
                emitter.emit(formatSseEvent(threadId, "TOOL_RESULT", Map.of(
                        "callId", callId,
                        "tool", toolName,
                        "protocol", "MCP",
                        "result", callResult.get("result"))));
                emitter.emit(formatSseEvent(threadId, "STATE_DELTA", Map.of(
                        "clusterState", mcpClient.readClusterState(),
                        "lastTool", toolName)));
                toolContext.append("\n[MCP Tool Executed (`").append(toolName).append("` on :8081)]: ")
                        .append(objectMapper.writeValueAsString(callResult.get("result")));
            }

            if (shouldInvokeA2a) {
                String skillId = "audit-clean-architecture";
                if (containsAny(lower, List.of("security", "posture", "cve"))) {
                    skillId = "assess-security-posture";
                } else if (containsAny(lower, List.of("remediat", "incident", "scale"))) {
                    skillId = "plan-incident-remediation";
                }
                emitter.emit(formatSseEvent(threadId, "STATUS", Map.of(
                        "phase", "delegating_a2a_task",
                        "message", "Delegating `" + skillId + "` to Sentinel-A2A-Auditor (:8082)")));
                String stepId = "a2a-" + UUID.randomUUID().toString().substring(0, 8);
                emitter.emit(formatSseEvent(threadId, "STEP_STARTED", Map.of(
                        "stepId", stepId,
                        "protocol", "A2A",
                        "agentName", "Sentinel-A2A-Auditor",
                        "skillId", skillId)));
                Map<String, Object> handoff = a2aClient.delegateTask(stepId, skillId, prompt);
                emitter.emit(formatSseEvent(threadId, "STEP_FINISHED", Map.of(
                        "stepId", stepId,
                        "protocol", "A2A",
                        "agentName", handoff.get("agentName"),
                        "skillId", skillId,
                        "summary", handoff.get("summary"),
                        "report", handoff.get("report"))));
                toolContext.append("\n[A2A Specialist (`Sentinel-A2A-Auditor` :8082, skill `").append(skillId).append("`)]: ")
                        .append(handoff.get("summary")).append(" | Report: ")
                        .append(objectMapper.writeValueAsString(handoff.get("report")));
            }

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
            String systemPrompt = "You are OpsPilot, a concise Quarkus SRE & Clean Architecture AI Agent speaking the AG-UI protocol. "
                    + "You orchestrate an MCP Server on port 8081 (tools: inspectClusterHealth, queryServiceLogs, scaleDeployment) "
                    + "and an A2A Specialist Agent on port 8082 (Sentinel-A2A-Auditor: audit-clean-architecture, assess-security-posture, plan-incident-remediation). "
                    + "Current cluster state: " + objectMapper.writeValueAsString(mcpClient.readClusterState()) + ". "
                    + "Keep responses clear, direct, and under 4 sentences.";

            String augmentedUserPrompt = toolContext.isEmpty()
                    ? userPrompt
                    : userPrompt + "\n\nLive Telemetry & Agent Results:" + toolContext
                            + "\nSummarize the findings and answer the operator concisely.";

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
            String city = detectNomadCity(lower);
            String stopTitle = switch (city.toLowerCase(Locale.ROOT)) {
                case "lisbon" -> "Palácio Belmonte Alfama Suite";
                case "reykjavik" -> "The Retreat at Blue Lagoon Moss Villa";
                default -> "Hoshinoya Arashiyama Riverside Pavilion";
            };
            int nights = 4;
            double costUsd = switch (city.toLowerCase(Locale.ROOT)) {
                case "lisbon" -> 1760.0;
                case "reykjavik" -> 2240.0;
                default -> 1840.0;
            };
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
                            "HITL Gate: Holding %s in %s (%d nights, $%.0f USD > $1,500 limit) requires traveler authorization",
                            stopTitle, city, nights, costUsd))));
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
        if (lower.contains("scale")) {
            int requestedReplicas = extractReplicas(lower, 4);
            String svc = "payment-gateway";
            if (lower.contains("order-processor") || lower.contains("order")) {
                svc = "order-processor";
            } else if (lower.contains("identity-provider") || lower.contains("identity")) {
                svc = "identity-provider";
            } else if (lower.contains("payment-service") || lower.contains("payment")) {
                svc = "payment-gateway";
            }
            if (requestedReplicas > HITL_REPLICA_THRESHOLD) {
                String callId = "call-hitl-" + UUID.randomUUID().toString().substring(0, 6);
                Map<String, Object> pendingAction = Map.of(
                        "callId", callId,
                        "toolName", "scaleDeployment",
                        "serviceName", svc,
                        "replicas", requestedReplicas,
                        "prompt", prompt);
                pendingApprovals.put(threadId, pendingAction);

                frames.add(formatSseEvent(threadId, "STATUS", Map.of(
                        "phase", "awaiting_human_approval",
                        "message", String.format("HITL Gate: Scaling %s to %d replicas (>%d) requires operator approval",
                                svc, requestedReplicas, HITL_REPLICA_THRESHOLD))));
                frames.add(formatSseEvent(threadId, "TOOL_CALL", Map.of(
                        "callId", callId,
                        "tool", "scaleDeployment",
                        "protocol", "MCP",
                        "callType", "hitl:approval",
                        "server", "quarkus-mcp-cluster-ops (:8081)",
                        "args", Map.of("serviceName", svc, "replicas", requestedReplicas),
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
                    "message", "Operator rejected pending HITL action")));
            frames.add(formatSseEvent(threadId, "RUN_FINISHED", Map.of(
                    "threadId", threadId,
                    "status", "cancelled")));
            return Multi.createFrom().iterable(frames);
        }

        String toolName = String.valueOf(pending.getOrDefault("toolName", "scaleDeployment"));
        String callId = String.valueOf(pending.getOrDefault("callId", "call-hitl"));
        Map<String, Object> mods = approval.modifiedArgs() != null ? approval.modifiedArgs() : Map.of();

        if ("updateTripItinerary".equals(toolName)) {
            String city = String.valueOf(mods.getOrDefault("city", pending.getOrDefault("city", "Kyoto")));
            String stopTitle = String.valueOf(mods.getOrDefault("stopTitle", pending.getOrDefault("stopTitle", "Hoshinoya Arashiyama")));
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

        String svc = String.valueOf(pending.getOrDefault("serviceName", "payment-gateway"));
        int replicas = pending.get("replicas") instanceof Number n ? n.intValue() : 8;

        Map<String, Object> result = mcpClient.callTool("scaleDeployment", Map.of(
                "serviceName", svc,
                "replicas", replicas));
        Map<String, Object> handoff = a2aClient.delegateTask(
                "a2a-" + threadId,
                "plan-incident-remediation",
                String.valueOf(pending.getOrDefault("prompt", "Approved scale")));

        frames.add(formatSseEvent(threadId, "TOOL_RESULT", Map.of(
                "callId", callId,
                "tool", "scaleDeployment",
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
                "reason", approval.reason() == null ? "Approved" : approval.reason())));

        String reply = String.format(
                "HITL Approval confirmed (%s). Scaled `%s` to %d replicas via MCP (:8081) and verified remediation with %s (:8082).",
                approval.reason() == null ? "Operator" : approval.reason(),
                svc,
                replicas,
                handoff.get("agentName"));
        appendTextDeltaFrames(frames, threadId, reply);

        frames.add(formatSseEvent(threadId, "RUN_FINISHED", Map.of(
                "threadId", threadId,
                "status", "completed")));

        return Multi.createFrom().iterable(frames);
    }

    private boolean isNomadPrompt(String lower) {
        return containsAny(lower, List.of(
                "/kyoto", "/lisbon", "/reykjavik", "/media", "/choices", "/mcp", "/a2a", "/hitl",
                "kyoto", "lisbon", "reykjavik", "travel", "trip", "itinerary", "weather", "music", "culture", "concierge", "currency", "fado", "aurora"));
    }

    private String detectNomadCity(String lower) {
        if (lower.contains("lisbon") || lower.contains("portugal") || lower.contains("fado")) {
            return "Lisbon";
        }
        if (lower.contains("reykjavik") || lower.contains("iceland") || lower.contains("aurora")) {
            return "Reykjavik";
        }
        return "Kyoto";
    }

    private String buildNomadConciergeMarkdownReply(String city, Map<String, Object> handoff) {
        return switch (city.toLowerCase(Locale.ROOT)) {
            case "lisbon" -> "### Lisbon — Atlantic Light, Azulejos & Evening Fado\n\n"
                    + "I have synchronized your **Lisbon Cultural Dossier** via `nomad-mcp-concierge (:8081)` and verified EUR currency bounds with `Sentinel-A2A-Auditor (:8082)`.\n\n"
                    + "- **Live Open-Meteo Forecast:** `22.1°C` — Atlantic Breeze & Golden Sun\n"
                    + "- **Soundtrack Preview (iTunes 30s):** *Canção do Mar (Acoustic Fado Session)* — Alfama Guitarra Ensemble\n"
                    + "- **A2A FX & Budget Verdict:** " + handoff.get("summary") + "\n\n"
                    + "Select your preferred neighborhood cadence below or preview the 30-second acoustic Fado recording inline.";
            case "reykjavik" -> "### Reykjavik — Basalt Geometry, Geothermal Steam & Nordic Strings\n\n"
                    + "I have assembled your **Reykjavik Sub-Arctic Dossier** via `nomad-mcp-concierge (:8081)` and audited ISK exchange rates with `Sentinel-A2A-Auditor (:8082)`.\n\n"
                    + "- **Live Open-Meteo Forecast:** `6.2°C` — Crisp Sub-Arctic Aurora Sky\n"
                    + "- **Soundtrack Preview (iTunes 30s):** *Hoppípolla — Nordic Strings* — Reykjavik Chamber Collective\n"
                    + "- **A2A FX & Budget Verdict:** " + handoff.get("summary") + "\n\n"
                    + "Listen to the 30-second chamber strings preview below and choose your evening geothermal and architectural sequence.";
            default -> "### Kyoto — Cedar Machiya, Moss Gardens & Koto Acoustics\n\n"
                    + "I have curated your **Kyoto Architectural & Cultural Dossier** via `nomad-mcp-concierge (:8081)` and audited JPY conversion with `Sentinel-A2A-Auditor (:8082)`.\n\n"
                    + "- **Live Open-Meteo Forecast:** `18.4°C` — Clear Cedar & Autumn Mist\n"
                    + "- **Soundtrack Preview (iTunes 30s):** *Haru no Umi (Spring Sea — Koto & Shakuhachi)* — Kyoto Koto Consort\n"
                    + "- **A2A FX & Budget Verdict:** " + handoff.get("summary") + "\n\n"
                    + "Use the inline player below to sample the 30-second Koto recording or tailor your neighborhood focus.";
        };
    }

    private List<Map<String, Object>> buildTurnMedia(String city) {
        String heroUrl = switch (city.toLowerCase(Locale.ROOT)) {
            case "lisbon" -> "https://images.unsplash.com/photo-1513735492246-483525079686?auto=format&fit=crop&w=1000&q=80";
            case "reykjavik" -> "https://images.unsplash.com/photo-1504893524553-b855bce32c67?auto=format&fit=crop&w=1000&q=80";
            default -> "https://images.unsplash.com/photo-1493976040374-85c8e12f0c0e?auto=format&fit=crop&w=1000&q=80";
        };
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
        return List.of(
                Map.of(
                        "type", "image",
                        "title", city + " Architectural & Cultural Panorama",
                        "subtitle", "Wikipedia REST API v1 • Curated Landmark Frame",
                        "url", heroUrl),
                Map.of(
                        "type", "audio",
                        "title", trackTitle,
                        "subtitle", artist + " • Apple iTunes 30s Preview",
                        "url", "https://www.w3schools.com/html/horse.mp3",
                        "artworkUrl", heroUrl,
                        "durationSeconds", 30),
                Map.of(
                        "type", "video",
                        "title", city + " Golden Hour Aerial Study",
                        "subtitle", "1080p Field Reel • Slow Architecture",
                        "url", "https://www.w3schools.com/html/mov_bbb.mp4",
                        "posterUrl", heroUrl),
                Map.of(
                        "type", "document",
                        "title", "Nomad-" + city + "-Concierge-Folio.pdf",
                        "subtitle", "Signed A2A & MCP Itinerary Dossier • 4 Pages",
                        "url", "https://en.wikipedia.org/wiki/" + city));
    }

    private Map<String, Object> buildTurnInteraction(String city, String lower) {
        String mode = lower.contains("multi") ? "multi_select" : "single_select";
        List<Map<String, Object>> options = switch (city.toLowerCase(Locale.ROOT)) {
            case "lisbon" -> List.of(
                    Map.of("id", "alfama-fado", "label", "Alfama Historic Fado & Miradouro Walk", "description", "Intimate acoustic Fado tavern + Santa Luzia tile terrace", "badge", "Evening • €140"),
                    Map.of("id", "chiado-atelier", "label", "Chiado Architectural & Azulejo Atelier", "description", "Private ceramic conservation studio + Belém riverside", "badge", "Morning • €190"),
                    Map.of("id", "sintra-coastal", "label", "Cabo da Roca & Sintra Pine Sanctuary", "description", "Coastal cliff walk + modernist vineyard tasting", "badge", "Full Day • €260"));
            case "reykjavik" -> List.of(
                    Map.of("id", "harpa-acoustics", "label", "Harpa Basalt Hall & Nordic Strings Rehearsal", "description", "Private acoustic walk-through + harbor listening lounge", "badge", "Evening • $210"),
                    Map.of("id", "sky-lagoon", "label", "Geothermal Cliffside Ritual & Turf Architecture", "description", "Seven-step oceanfront thermal soak + slow dining", "badge", "Afternoon • $280"),
                    Map.of("id", "snaefellsnes", "label", "Snæfellsnes Volcanic Fjord Expedition", "description", "Black church at Búðir + glacier ridge photography", "badge", "Full Day • $390"));
            default -> List.of(
                    Map.of("id", "arashiyama-dawn", "label", "Arashiyama Bamboo & Katsura Imperial Villa", "description", "Early-morning moss garden architectural walk + matcha pavilion", "badge", "Dawn • ¥24,000"),
                    Map.of("id", "gion-koto", "label", "Gion Machiya Private Koto & Kaiseki Evening", "description", "11-course seasonal Kaiseki with live Koto performance", "badge", "Evening • ¥38,000"),
                    Map.of("id", "higashiyama-ceramics", "label", "Higashiyama Raku Kiln & Vinyl Listening Bar", "description", "Private ceramic studio visit + analog jazz kissaten", "badge", "Late Night • ¥19,000"));
        };
        List<Map<String, Object>> actions = List.of(
                Map.of("id", "act-hitl-book", "label", "Hold Sanctuary Suite ($1,840 — HITL Gate)", "prompt", "/hitl Reserve " + city + " Sanctuary Suite for 4 nights ($1840)", "variant", "primary"),
                Map.of("id", "act-fx-audit", "label", "Re-run Frankfurter FX Audit (:8082)", "prompt", "/a2a Convert and audit " + city + " budget", "variant", "secondary"));
        return Map.of(
                "id", "choice-" + city.toLowerCase(Locale.ROOT),
                "mode", mode,
                "prompt", "Curate your preferred " + city + " cultural sequence:",
                "options", options,
                "actions", actions);
    }

    private List<Map<String, Object>> buildTurnSources(String city) {
        return List.of(
                Map.of("title", "Open-Meteo Weather API (" + city + ")", "url", "https://api.open-meteo.com", "protocol", "mcp:tool"),
                Map.of("title", "Wikipedia REST API v1 (" + city + ")", "url", "https://en.wikipedia.org/wiki/" + city, "protocol", "mcp:tool"),
                Map.of("title", "Apple iTunes 30s Audio Preview", "url", "https://itunes.apple.com", "protocol", "mcp:tool"),
                Map.of("title", "Frankfurter ECB FX Reference", "url", "https://api.frankfurter.dev", "protocol", "a2a:task"),
                Map.of("title", "REST Countries Advisory v3.1", "url", "https://restcountries.com", "protocol", "a2a:artifact"));
    }

    private List<String> buildTurnFollowUps(String city) {
        return List.of(
                "/kyoto Curate a 4-night Kyoto architectural & Koto escape",
                "/lisbon Plan an evening Fado & azulejo walk in Lisbon",
                "/reykjavik Design an aurora & geothermal itinerary in Reykjavik",
                "/hitl Reserve " + city + " Sanctuary Pavilion ($1,840 — HITL Gate)",
                "/choices Compare multi-select neighborhood experiences in " + city);
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

    private boolean containsAny(String text, List<String> keywords) {
        return keywords.stream().anyMatch(text::contains);
    }

    private int extractReplicas(String text, int fallback) {
        if (text == null) {
            return fallback;
        }
        // Match patterns like "to 8 replicas", "to 8", "8 replicas"
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b(?:to\\s+)?(\\d+)\\s*(?:replicas?|pods?)?\\b", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text);
        int candidate = fallback;
        while (m.find()) {
            try {
                int val = Integer.parseInt(m.group(1));
                if (val > 0 && val <= 50) {
                    candidate = val;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return candidate;
    }
}
