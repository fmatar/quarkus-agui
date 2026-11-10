package org.agui.showcase.backend;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
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
 */
@WebSocket(path = "/api/agent/ws")
@ApplicationScoped
public class AgentWebSocketEndpoint {

    private final NomadConciergeOrchestratorService orchestrator;
    private final ObjectMapper objectMapper = new ObjectMapper();
    boolean asyncDispatch = true;

    @Inject
    public AgentWebSocketEndpoint(NomadConciergeOrchestratorService orchestrator) {
        this.orchestrator = orchestrator;
    }

    @OnOpen
    public void onOpen(WebSocketConnection connection) {
        Object dossier = orchestrator.getCatalog().get("tripDossierState");
        sendEventFrame(connection, "thread-nomad-1", "WS_CONNECTED", Map.of(
                "sessionId", connection.id(),
                "transport", "websocket-full-duplex",
                "tripDossierState", dossier,
                "clusterState", dossier,
                "memoryTurns", orchestrator.getMemoryTurnCount("thread-nomad-1")));
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
                    dispatchWork("agui-ws-hitl-" + threadId, () ->
                            orchestrator.approvePendingAction(approval)
                                    .onItem().invoke(frame -> sendRawSseFrameAsJson(connection, frame))
                                    .collect().last().await().indefinitely());
                }

                default -> {
                    String prompt = root.path("prompt").asText("");
                    String mode = root.hasNonNull("mode") ? root.path("mode").asText() : null;
                    Map<String, Object> state = root.has("state") && root.path("state").isObject()
                            ? objectMapper.convertValue(root.path("state"), new TypeReference<Map<String, Object>>() {})
                            : Map.of();
                    AgentRunRequest runRequest = new AgentRunRequest(threadId, prompt, state, mode);
                    dispatchWork("agui-ws-run-" + threadId, () ->
                            orchestrator.streamRun(runRequest)
                                    .onItem().invoke(frame -> sendRawSseFrameAsJson(connection, frame))
                                    .collect().last().await().indefinitely());
                }
            }
        } catch (Exception e) {
            sendEventFrame(connection, "thread-nomad-1", "RUN_FINISHED", Map.of(
                    "status", "failed",
                    "error", e.getMessage() == null ? "Invalid WebSocket frame" : e.getMessage()));
        }
    }

    private void dispatchWork(String threadName, Runnable task) {
        Runnable wrapped = () -> runWithRequestContext(task);
        if (asyncDispatch) {
            Thread.ofVirtual().name(threadName).start(wrapped);
        } else {
            wrapped.run();
        }
    }

    static void runWithRequestContext(Runnable task) {
        var container = Arc.container();
        if (container == null) {
            task.run();
            return;
        }
        ManagedContext requestContext = container.requestContext();
        if (requestContext == null || requestContext.isActive()) {
            task.run();
            return;
        }
        requestContext.activate();
        try {
            task.run();
        } finally {
            requestContext.terminate();
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
        String json = AgUiSseFormatter.unwrapSseData(orchestrator.formatter().formatSse(event));
        synchronized (connection) {
            if (connection.isOpen()) {
                connection.sendTextAndAwait(json.trim());
            }
        }
    }
}
