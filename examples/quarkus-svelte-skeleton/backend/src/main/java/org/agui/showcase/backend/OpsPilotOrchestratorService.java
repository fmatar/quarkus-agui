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
import org.agui.quarkus.runtime.adapters.AgUiA2aBridge;
import org.agui.quarkus.runtime.adapters.AgUiBridge;
import org.agui.quarkus.runtime.adapters.AgUiMcpBridge;
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
        } else {
            mcpCalls.add(mcpClient.callTool("inspectClusterHealth", Map.of()));
        }

        if (containsAny(lower, List.of("security", "posture", "cve"))) {
            a2aHandoffs.add(a2aClient.delegateTask("a2a-" + threadId, "assess-security-posture", prompt));
        } else if (containsAny(lower, List.of("remediat", "incident", "scale"))) {
            a2aHandoffs.add(a2aClient.delegateTask("a2a-" + threadId, "plan-incident-remediation", prompt));
        } else {
            a2aHandoffs.add(a2aClient.delegateTask("a2a-" + threadId, "audit-clean-architecture", prompt));
        }

        Map<String, Object> toolCall = mcpCalls.getFirst();
        Map<String, Object> handoff = a2aHandoffs.getFirst();
        String reply = String.format(
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
                    "server", call.get("server"),
                    "args", call.get("arguments"))));
            frames.add(formatSseEvent(threadId, "TOOL_RESULT", Map.of(
                    "callId", callId,
                    "tool", call.get("toolName"),
                    "protocol", "MCP",
                    "result", call.get("result"))));
        }

        for (Map<String, Object> handoff : chat.a2aHandoffs()) {
            frames.add(formatSseEvent(threadId, "STEP_STARTED", Map.of(
                    "stepId", handoff.get("taskId"),
                    "protocol", "A2A",
                    "agentName", handoff.get("agentName"),
                    "skillId", handoff.get("skillId"))));
            frames.add(formatSseEvent(threadId, "STEP_FINISHED", Map.of(
                    "stepId", handoff.get("taskId"),
                    "protocol", "A2A",
                    "agentName", handoff.get("agentName"),
                    "skillId", handoff.get("skillId"),
                    "summary", handoff.get("summary"),
                    "report", handoff.get("report"))));
        }

        frames.add(formatSseEvent(threadId, "STATE_DELTA", Map.of(
                "clusterState", chat.clusterState(),
                "lastTool", chat.mcpCalls().getFirst().get("toolName"),
                "lastA2aSkill", chat.a2aHandoffs().getFirst().get("skillId"))));

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
                    "message", "Operator rejected high-replica scale action")));
            frames.add(formatSseEvent(threadId, "RUN_FINISHED", Map.of(
                    "threadId", threadId,
                    "status", "cancelled")));
            return Multi.createFrom().iterable(frames);
        }

        String svc = String.valueOf(pending.getOrDefault("serviceName", "payment-gateway"));
        int replicas = pending.get("replicas") instanceof Number n ? n.intValue() : 8;
        String callId = String.valueOf(pending.getOrDefault("callId", "call-hitl"));

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
                "approvedByOperator", true,
                "result", result.get("result"))));

        frames.add(formatSseEvent(threadId, "STEP_STARTED", Map.of(
                "stepId", handoff.get("taskId"),
                "protocol", "A2A",
                "agentName", handoff.get("agentName"),
                "skillId", handoff.get("skillId"))));
        frames.add(formatSseEvent(threadId, "STEP_FINISHED", Map.of(
                "stepId", handoff.get("taskId"),
                "protocol", "A2A",
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
