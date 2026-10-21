package org.agui.showcase.backend;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * OpsPilot Quarkus Orchestrator Service (:8080).
 * Implements Dual-Mode LangChain4j orchestration bridging MCP (:8081), A2A (:8082), and AG-UI SSE.
 */
@ApplicationScoped
public class OpsPilotOrchestratorService {

    private final McpGatewayClient mcpClient;
    private final A2aGatewayClient a2aClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    public OpsPilotOrchestratorService(McpGatewayClient mcpClient, A2aGatewayClient a2aClient) {
        this.mcpClient = mcpClient;
        this.a2aClient = a2aClient;
    }

    public Map<String, Object> getCatalog() {
        return Map.of(
                "orchestrator", "OpsPilot-Quarkus-Orchestrator",
                "llmMode", resolveMode(null),
                "mcpTools", mcpClient.listTools(),
                "a2aAgent", a2aClient.getAgentCard(),
                "clusterState", mcpClient.readClusterState());
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
            String svc = lower.contains("order-processor") ? "order-processor" : "payment-gateway";
            mcpCalls.add(mcpClient.callTool("scaleDeployment", Map.of("serviceName", svc, "replicas", replicas)));
        } else {
            mcpCalls.add(mcpClient.callTool("inspectClusterHealth", Map.of()));
        }

        if (lower.contains("security") || lower.contains("posture") || lower.contains("cve")) {
            a2aHandoffs.add(a2aClient.delegateTask("a2a-" + threadId, "assess-security-posture", prompt));
        } else if (lower.contains("remediat") || lower.contains("incident") || lower.contains("scale")) {
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
        AgentChatResponse chat = executeChat(request);
        String threadId = chat.threadId();
        List<String> frames = new ArrayList<>();

        frames.add(formatSseEvent(threadId, "RUN_STARTED", Map.of(
                "threadId", threadId,
                "status", "started",
                "llmMode", chat.llmMode())));

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

        String[] words = chat.reply().split(" ");
        StringBuilder chunk = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            chunk.append(words[i]).append(i < words.length - 1 ? " " : "");
            if ((i + 1) % 5 == 0 || i == words.length - 1) {
                frames.add(formatSseEvent(threadId, "TEXT_DELTA", Map.of("content", chunk.toString())));
                chunk.setLength(0);
            }
        }

        frames.add(formatSseEvent(threadId, "RUN_FINISHED", Map.of(
                "threadId", threadId,
                "status", "completed")));

        return Multi.createFrom().iterable(frames);
    }

    private String formatSseEvent(String threadId, String type, Map<String, Object> payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", "evt-" + UUID.randomUUID().toString().substring(0, 8));
        envelope.put("type", type);
        envelope.put("threadId", threadId);
        envelope.put("timestamp", System.currentTimeMillis());
        envelope.put("payload", payload);
        try {
            return "data: " + objectMapper.writeValueAsString(envelope) + "\n\n";
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize AG-UI event", e);
        }
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

    private int extractReplicas(String text, int fallback) {
        for (String token : text.split("\\s+")) {
            try {
                return Integer.parseInt(token.replaceAll("[^0-9]", ""));
            } catch (NumberFormatException ignored) {
                // continue scanning tokens
            }
        }
        return fallback;
    }
}
