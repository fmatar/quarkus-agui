package org.agui.quarkus.websockets.runtime.adapters;

import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnError;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.agui.core.domain.AgUiEvent;
import org.agui.core.domain.AgUiEventType;
import org.agui.core.domain.RunStartedPayload;
import org.agui.quarkus.core.runtime.application.port.AgUiSessionRegistry;
import org.agui.quarkus.core.runtime.application.port.AgUiTransportBridge;
import org.agui.quarkus.core.runtime.domain.AgUiTransportType;

/**
 * WebSocket Next endpoint providing full-duplex communication for AG-UI.
 */
@WebSocket(path = "/ag-ui/ws")
@ApplicationScoped
public class AgUiWebSocketEndpoint implements AgUiTransportBridge {

    private final AgUiSessionRegistry registry;

    @Inject
    public AgUiWebSocketEndpoint(AgUiSessionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public AgUiTransportType transportType() {
        return AgUiTransportType.WEBSOCKET;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @OnOpen
    public void onOpen(WebSocketConnection connection) {
        AgUiWebSocketTransportSession session = new AgUiWebSocketTransportSession(connection.id(), connection);
        registry.register(session);
        // Send initial acknowledgment
        AgUiEvent<RunStartedPayload> welcomeEvent = AgUiEvent.of(
                AgUiEventType.RUN_STARTED,
                connection.id(),
                new RunStartedPayload(connection.id(), "connected")
        );
        session.emit(welcomeEvent);
    }

    @OnTextMessage
    public void onMessage(String message, WebSocketConnection connection) {
        // Can be forwarded to an agent executor / orchestrator
        if ("ping".equalsIgnoreCase(message.trim())) {
            connection.sendTextAndAwait("{\"type\":\"pong\"}");
        }
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {
        registry.unregister(connection.id());
    }

    @OnError
    public void onError(WebSocketConnection connection, Throwable throwable) {
        registry.unregister(connection.id());
    }
}
