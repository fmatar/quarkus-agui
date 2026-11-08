package org.agui.quarkus.websockets.runtime.adapters;

import io.quarkus.websockets.next.WebSocketConnection;
import java.util.Objects;
import org.agui.core.adapters.AgUiSseFormatter;
import org.agui.core.domain.AgUiEvent;
import org.agui.quarkus.core.runtime.application.port.AgUiTransportSession;
import org.agui.quarkus.core.runtime.domain.AgUiTransportType;

/**
 * WebSocket Transport Session adapter wrapping a {@link WebSocketConnection}.
 */
public class AgUiWebSocketTransportSession implements AgUiTransportSession {

    private final String sessionId;
    private final WebSocketConnection connection;
    private final AgUiSseFormatter jsonSerializer;

    public AgUiWebSocketTransportSession(String sessionId, WebSocketConnection connection) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
        this.connection = connection;
        this.jsonSerializer = new AgUiSseFormatter();
    }

    @Override
    public String sessionId() {
        return sessionId;
    }

    @Override
    public AgUiTransportType transportType() {
        return AgUiTransportType.WEBSOCKET;
    }

    @Override
    public boolean isOpen() {
        return connection != null && !connection.isClosed();
    }

    @Override
    public void emit(AgUiEvent<?> event) {
        if (!isOpen() || event == null) {
            return;
        }
        String json = jsonSerializer.toJson(event);
        connection.sendTextAndAwait(json);
    }

    @Override
    public void close() {
        if (isOpen()) {
            connection.closeAndAwait();
        }
    }
}
