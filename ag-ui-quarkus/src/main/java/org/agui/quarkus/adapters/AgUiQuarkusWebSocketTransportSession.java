package org.agui.quarkus.adapters;

import io.quarkus.websockets.next.WebSocketConnection;
import org.agui.core.adapters.AgUiWebSocketTransportSession;

/**
 * Quarkus WebSockets Next Transport Session adapter wrapping a {@link WebSocketConnection}
 * over the framework-agnostic {@link AgUiWebSocketTransportSession}.
 */
public class AgUiQuarkusWebSocketTransportSession extends AgUiWebSocketTransportSession {

    public AgUiQuarkusWebSocketTransportSession(String sessionId, WebSocketConnection connection) {
        super(
                sessionId,
                connection == null ? null : connection::sendTextAndAwait,
                connection == null ? null : () -> !connection.isClosed(),
                connection == null ? null : connection::closeAndAwait);
    }
}
