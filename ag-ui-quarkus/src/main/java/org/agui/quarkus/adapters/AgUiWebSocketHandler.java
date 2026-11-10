package org.agui.quarkus.adapters;

import com.agui.community.core.agent.Agent;
import com.agui.community.core.agent.RunAgentInput;
import com.agui.community.core.event.Event;
import com.agui.community.core.event.RunErrorEvent;
import com.agui.community.core.serialization.Serializer;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Locale;
import java.util.Objects;
import org.agui.quarkus.application.AgUiSessionRegistry;

/**
 * Reusable Quarkus WebSockets Next lifecycle and {@link RunAgentInput} execution helper.
 * Applications mount their own {@code @WebSocket(path = "...")} endpoint and delegate to this bean.
 */
@ApplicationScoped
public class AgUiWebSocketHandler {

    private final AgUiSessionRegistry registry;
    private final AgUiQuarkusBridge bridge;
    private final Serializer serializer;

    public AgUiWebSocketHandler() {
        AgUiJacksonSerializer defaultSerializer = new AgUiJacksonSerializer();
        this.registry = new AgUiSessionRegistry();
        this.bridge = new AgUiQuarkusBridge(defaultSerializer);
        this.serializer = defaultSerializer;
    }

    @Inject
    public AgUiWebSocketHandler(
            AgUiSessionRegistry registry,
            AgUiQuarkusBridge bridge,
            Serializer serializer) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.bridge = Objects.requireNonNull(bridge, "bridge must not be null");
        this.serializer = Objects.requireNonNull(serializer, "serializer must not be null");
    }

    public AgUiQuarkusWebSocketTransportSession onOpen(WebSocketConnection connection) {
        Objects.requireNonNull(connection, "connection must not be null");
        AgUiQuarkusWebSocketTransportSession session =
                new AgUiQuarkusWebSocketTransportSession(connection.id(), connection);
        registry.register(session);
        return session;
    }

    public void onClose(WebSocketConnection connection) {
        if (connection != null) {
            registry.unregister(connection.id());
        }
    }

    public void onError(WebSocketConnection connection, Throwable error) {
        onClose(connection);
    }

    public boolean handleControlFrame(String rawMessage, WebSocketConnection connection) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return true;
        }
        String trimmed = rawMessage.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if ("ping".equals(lower) || lower.contains("\"type\":\"ping\"") || lower.contains("\"type\": \"ping\"")) {
            sendRawJson(connection, "{\"type\":\"pong\"}");
            return true;
        }
        return false;
    }

    public RunAgentInput parseInput(String rawMessage) {
        return serializer.deserialize(rawMessage, RunAgentInput.class);
    }

    public void sendEvent(WebSocketConnection connection, Event event) {
        if (event != null) {
            sendRawJson(connection, serializer.serialize(event));
        }
    }

    public void runAndSend(Agent agent, String rawMessage, WebSocketConnection connection) {
        Objects.requireNonNull(agent, "agent must not be null");
        if (handleControlFrame(rawMessage, connection)) {
            return;
        }
        try {
            RunAgentInput input = parseInput(rawMessage);
            bridge.runAsJsonPayloads(agent, input)
                    .onItem().invoke(json -> sendRawJson(connection, json))
                    .collect().last().await().indefinitely();
        } catch (Exception failure) {
            String msg = (failure.getMessage() == null || failure.getMessage().isBlank())
                    ? failure.getClass().getSimpleName()
                    : failure.getMessage();
            sendEvent(connection, new RunErrorEvent(msg, "WS_EXECUTION_ERROR", System.currentTimeMillis(), null));
        }
    }

    private static void sendRawJson(WebSocketConnection connection, String json) {
        if (connection == null || connection.isClosed()) {
            return;
        }
        synchronized (connection) {
            if (!connection.isClosed()) {
                connection.sendTextAndAwait(json);
            }
        }
    }
}
