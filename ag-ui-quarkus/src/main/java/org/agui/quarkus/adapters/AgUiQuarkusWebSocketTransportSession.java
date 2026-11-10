package org.agui.quarkus.adapters;

import io.quarkus.websockets.next.WebSocketConnection;
import java.util.Objects;
import org.agui.quarkus.application.AgUiTransportSession;
import org.agui.quarkus.domain.AgUiTransportType;

/**
 * Quarkus WebSockets Next transport session implementing {@link AgUiTransportSession}.
 */
public class AgUiQuarkusWebSocketTransportSession implements AgUiTransportSession {

    private final String sessionId;
    private final WebSocketConnection connection;

    public AgUiQuarkusWebSocketTransportSession(String sessionId, WebSocketConnection connection) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
        this.connection = connection;
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
    public void sendJson(String json) {
        if (json == null || !isOpen()) {
            return;
        }
        synchronized (connection) {
            if (!connection.isClosed()) {
                connection.sendTextAndAwait(json);
            }
        }
    }

    @Override
    public void close() {
        if (isOpen()) {
            connection.closeAndAwait();
        }
    }
}
