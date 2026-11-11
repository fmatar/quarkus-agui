package org.agui.showcase.backend;

import com.agui.community.core.event.RunErrorEvent;
import com.agui.community.core.event.RunFinishedEvent;
import com.agui.community.core.event.StateDeltaEvent;
import com.agui.community.core.interrupt.SuccessOutcome;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnError;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Locale;
import java.util.Map;
import org.agui.langchain4j.domain.AgUiStatePatch;
import org.agui.quarkus.adapters.AgUiWebSocketHandler;

/**
 * Full-Duplex Quarkus WebSockets Next Endpoint ({@code /api/agent/ws}) delegating canonical
 * AG-UI {@code RunAgentInput} execution and session lifecycle to {@link AgUiWebSocketHandler}.
 */
@WebSocket(path = "/api/agent/ws")
@ApplicationScoped
public class AgentWebSocketEndpoint {

    private final NomadConciergeOrchestratorService orchestrator;
    private final AgUiWebSocketHandler wsHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();
    boolean asyncDispatch = true;

    public AgentWebSocketEndpoint(NomadConciergeOrchestratorService orchestrator) {
        this(orchestrator, new AgUiWebSocketHandler());
    }

    @Inject
    public AgentWebSocketEndpoint(
            NomadConciergeOrchestratorService orchestrator,
            AgUiWebSocketHandler wsHandler) {
        this.orchestrator = orchestrator;
        this.wsHandler = wsHandler;
    }

    @OnOpen
    public void onOpen(WebSocketConnection connection) {
        wsHandler.onOpen(connection);
        Object dossier = orchestrator.getCatalog().get("tripDossierState");
        sendControlJson(connection, Map.of(
                "type", "WS_CONNECTED",
                "sessionId", connection.id(),
                "transport", "websocket-full-duplex",
                "tripDossierState", dossier,
                "clusterState", dossier,
                "memoryTurns", orchestrator.getMemoryTurnCount("thread-nomad-1")));
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {
        wsHandler.onClose(connection);
    }

    @OnError
    public void onError(WebSocketConnection connection, Throwable error) {
        wsHandler.onError(connection, error);
    }

    @OnTextMessage
    public void onMessage(String rawMessage, WebSocketConnection connection) {
        if (wsHandler.handleControlFrame(rawMessage, connection)) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(rawMessage.trim());
            String commandType = root.path("type").asText("").toUpperCase(Locale.ROOT);
            String threadId = root.path("threadId").asText("thread-nomad-1");
            routeParsedFrame(rawMessage, connection, root, commandType, threadId);
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "Invalid WebSocket frame" : e.getMessage();
            wsHandler.sendEvent(connection, new RunErrorEvent(
                    msg,
                    "WS_FRAME_ERROR",
                    System.currentTimeMillis(),
                    Map.of("status", "failed")));
        }
    }

    private void routeParsedFrame(
            String rawMessage,
            WebSocketConnection connection,
            JsonNode root,
            String commandType,
            String threadId) {
        switch (commandType) {
            case "CANCEL_RUN" -> handleCancelCommand(connection, threadId);
            case "UPDATE_TRIP_PARAMS" -> handleUpdateParamsCommand(connection, threadId, root);
            case "RESET_MEMORY" -> handleResetMemoryCommand(connection, threadId);
            default -> dispatchWork(
                    "agui-ws-run-" + threadId,
                    () -> wsHandler.runAndSend(orchestrator, rawMessage, connection));
        }
    }

    private void handleCancelCommand(WebSocketConnection connection, String threadId) {
        wsHandler.cancelRun(connection, threadId);
        orchestrator.cancelRun(threadId);
        wsHandler.sendEvent(connection, new RunFinishedEvent(
                threadId,
                "run-cancelled",
                new SuccessOutcome(),
                Map.of("threadId", threadId, "status", "cancelled"),
                System.currentTimeMillis(),
                null));
    }

    private void handleUpdateParamsCommand(WebSocketConnection connection, String threadId, JsonNode root) {
        Integer nights = root.hasNonNull("nights") ? root.path("nights").asInt() : null;
        String homeCurrency = root.hasNonNull("homeCurrency") ? root.path("homeCurrency").asText() : null;
        Map<String, Object> delta = orchestrator.updateTripParams(threadId, nights, homeCurrency);
        wsHandler.sendEvent(connection, new StateDeltaEvent(
                AgUiStatePatch.upsertRootFields(delta),
                System.currentTimeMillis(),
                delta));
    }

    private void handleResetMemoryCommand(WebSocketConnection connection, String threadId) {
        Map<String, Object> resetDelta = orchestrator.resetMemory(threadId);
        wsHandler.sendEvent(connection, new StateDeltaEvent(
                AgUiStatePatch.upsertRootFields(resetDelta),
                System.currentTimeMillis(),
                resetDelta));
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
        runWithManagedContext(container == null ? null : container.requestContext(), task);
    }

    static void runWithManagedContext(ManagedContext requestContext, Runnable task) {
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

    void sendControlJson(WebSocketConnection connection, Map<String, Object> payload) {
        if (connection == null || connection.isClosed()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            synchronized (connection) {
                if (!connection.isClosed()) {
                    connection.sendTextAndAwait(json);
                }
            }
        } catch (Exception ignored) {
            // Non-serializable control payloads are ignored
        }
    }
}
