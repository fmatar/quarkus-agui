package org.agui.showcase.backend;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import org.agui.core.adapters.AgUiSseFormatter;
import org.agui.core.domain.AgUiEvent;

/**
 * Full-Duplex Quarkus WebSockets Next Endpoint (`/api/agent/ws`) for the Nomad Concierge Studio (:8080).
 * Supports bidirectional AG-UI envelopes:
 * - `RUN_AGENT`: Streams AG-UI protocol frames over the persistent WebSocket
 * - `APPROVE_HITL`: Resumes or rejects a pending HITL sanctuary hold
 * - `CANCEL_RUN`: Immediately aborts an active streaming turn on the server
 * - `UPDATE_TRIP_PARAMS`: Instant `<50ms` Trip Dossier nights & homeCurrency state update (`STATE_DELTA`)
 * - `RESET_MEMORY`: Clears sliding-window conversational memory and resets traveler preferences
 */
@WebSocket(path = "/api/agent/ws")
@ApplicationScoped
public class AgentWebSocketEndpoint {

    private final OpsPilotOrchestratorService orchestrator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    public AgentWebSocketEndpoint(OpsPilotOrchestratorService orchestrator) {
        this.orchestrator = orchestrator;
    }

    @OnOpen
    public void onOpen(WebSocketConnection connection) {
        sendEventFrame(connection, "thread-nomad-1", "WS_CONNECTED", Map.of(
                "sessionId", connection.id(),
                "transport", "websocket-full-duplex",
                "clusterState", orchestrator.getCatalog().get("clusterState"),
                "memoryTurns", orchestrator.getConversationMemory("thread-nomad-1").size()));
    }

    @OnTextMessage
    public void onMessage(String rawMessage, WebSocketConnection connection) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return;
        }
        String trimmed = rawMessage.trim();
        if ("ping".equalsIgnoreCase(trimmed)) {
            connection.sendTextAndAwait("{\"type\":\"pong\"}");
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(trimmed);
            String commandType = root.path("type").asText("RUN_AGENT").toUpperCase();
            String threadId = root.path("threadId").asText("thread-nomad-1");

            switch (commandType) {
                case "PING" -> connection.sendTextAndAwait("{\"type\":\"pong\"}");

                case "CANCEL_RUN" -> {
                    orchestrator.cancelRun(threadId);
                    sendEventFrame(connection, threadId, "RUN_FINISHED", Map.of(
                            "threadId", threadId,
                            "status", "cancelled"));
                }

                case "UPDATE_TRIP_PARAMS" -> {
                    Integer nights = root.hasNonNull("nights") ? root.path("nights").asInt() : null;
                    String homeCurrency = root.hasNonNull("homeCurrency") ? root.path("homeCurrency").asText() : null;
                    Map<String, Object> delta = orchestrator.updateTripParams(threadId, nights, homeCurrency);
                    sendEventFrame(connection, threadId, "STATE_DELTA", delta);
                }

                case "RESET_MEMORY" -> {
                    Map<String, Object> resetDelta = orchestrator.resetMemory(threadId);
                    sendEventFrame(connection, threadId, "STATE_DELTA", resetDelta);
                }

                case "APPROVE_HITL" -> {
                    boolean approved = root.path("approved").asBoolean(false);
                    String reason = root.path("reason").asText(approved ? "Approved via WebSocket" : "Declined via WebSocket");
                    Map<String, Object> modifiedArgs = root.has("modifiedArgs") && root.path("modifiedArgs").isObject()
                            ? objectMapper.convertValue(root.path("modifiedArgs"), new TypeReference<Map<String, Object>>() {})
                            : null;
                    AgentApprovalRequest approval = new AgentApprovalRequest(threadId, approved, reason, modifiedArgs);
                    Thread.ofVirtual().name("agui-ws-hitl-" + threadId).start(() ->
                            orchestrator.approvePendingAction(approval)
                                    .subscribe().with(frame -> sendRawSseFrameAsJson(connection, frame)));
                }

                default -> {
                    String prompt = root.path("prompt").asText("");
                    String mode = root.path("mode").asText("ollama:llama3.2:latest");
                    Map<String, Object> state = root.has("state") && root.path("state").isObject()
                            ? objectMapper.convertValue(root.path("state"), new TypeReference<Map<String, Object>>() {})
                            : Map.of();
                    AgentRunRequest runRequest = new AgentRunRequest(threadId, prompt, state, mode);
                    Thread.ofVirtual().name("agui-ws-run-" + threadId).start(() ->
                            orchestrator.streamRun(runRequest)
                                    .subscribe().with(frame -> sendRawSseFrameAsJson(connection, frame)));
                }
            }
        } catch (Exception e) {
            sendEventFrame(connection, "thread-nomad-1", "RUN_FINISHED", Map.of(
                    "status", "failed",
                    "error", e.getMessage() == null ? "Invalid WebSocket frame" : e.getMessage()));
        }
    }

    void sendRawSseFrameAsJson(WebSocketConnection connection, String rawSseFrame) {
        if (connection == null || !connection.isOpen()) {
            return;
        }
        String unwrapped = AgUiSseFormatter.unwrapSseData(rawSseFrame);
        if (unwrapped != null && !unwrapped.isBlank()) {
            synchronized (connection) {
                if (connection.isOpen()) {
                    connection.sendTextAndAwait(unwrapped.trim());
                }
            }
        }
    }

    void sendEventFrame(WebSocketConnection connection, String threadId, String type, Map<String, Object> payload) {
        if (connection == null || !connection.isOpen()) {
            return;
        }
        AgUiEvent<Map<String, Object>> event = AgUiEvent.ofType(type, threadId, payload);
        String json = AgUiSseFormatter.unwrapSseData(orchestrator.agUiBridge().formatter().formatSse(event));
        synchronized (connection) {
            if (connection.isOpen()) {
                connection.sendTextAndAwait(json.trim());
            }
        }
    }
}
