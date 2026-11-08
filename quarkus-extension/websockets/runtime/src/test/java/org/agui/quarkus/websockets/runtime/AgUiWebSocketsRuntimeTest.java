package org.agui.quarkus.websockets.runtime;

import io.quarkus.websockets.next.WebSocketConnection;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.agui.core.domain.AgUiEvent;
import org.agui.core.domain.AgUiEventType;
import org.agui.core.domain.TextDeltaPayload;
import org.agui.quarkus.core.runtime.application.port.AgUiSessionRegistry;
import org.agui.quarkus.core.runtime.domain.AgUiTransportType;
import org.agui.quarkus.websockets.runtime.adapters.AgUiWebSocketEndpoint;
import org.agui.quarkus.websockets.runtime.adapters.AgUiWebSocketTransportSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgUiWebSocketsRuntimeTest {

    @Test
    @DisplayName("AgUiWebSocketTransportSession and AgUiWebSocketEndpoint handle full lifecycle")
    void testWebSocketSessionLifecycle() {
        AtomicBoolean closed = new AtomicBoolean(false);
        List<String> sentMessages = new ArrayList<>();

        WebSocketConnection connection = (WebSocketConnection) Proxy.newProxyInstance(
                WebSocketConnection.class.getClassLoader(),
                new Class<?>[] { WebSocketConnection.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "id" -> "ws-conn-1";
                    case "isClosed" -> closed.get();
                    case "sendTextAndAwait" -> {
                        sentMessages.add(String.valueOf(args[0]));
                        yield null;
                    }
                    case "closeAndAwait" -> {
                        closed.set(true);
                        yield null;
                    }
                    default -> null;
                });

        AgUiSessionRegistry registry = new AgUiSessionRegistry();
        AgUiWebSocketEndpoint endpoint = new AgUiWebSocketEndpoint(registry);
        assertEquals(AgUiTransportType.WEBSOCKET, endpoint.transportType());
        assertTrue(endpoint.isEnabled());

        endpoint.onOpen(connection);
        assertEquals(1, registry.activeCount());
        assertEquals(1, sentMessages.size());
        assertTrue(sentMessages.getFirst().contains("RUN_STARTED"));

        endpoint.onMessage("ping", connection);
        assertEquals(2, sentMessages.size());
        assertEquals("{\"type\":\"pong\"}", sentMessages.get(1));

        endpoint.onMessage("noop", connection);
        assertEquals(2, sentMessages.size());

        AgUiWebSocketTransportSession session = new AgUiWebSocketTransportSession("ws-conn-1", connection);
        assertEquals("ws-conn-1", session.sessionId());
        assertEquals(AgUiTransportType.WEBSOCKET, session.transportType());
        assertTrue(session.isOpen());

        session.emit(null);
        session.emit(AgUiEvent.of(AgUiEventType.TEXT_DELTA, "ws-conn-1", new TextDeltaPayload("ws-delta")));
        assertEquals(3, sentMessages.size());
        assertTrue(sentMessages.get(2).contains("ws-delta"));

        session.close();
        assertFalse(session.isOpen());
        session.emit(AgUiEvent.of(AgUiEventType.TEXT_DELTA, "ws-conn-1", new TextDeltaPayload("ignored")));
        assertEquals(3, sentMessages.size());

        endpoint.onClose(connection);
        assertEquals(0, registry.activeCount());

        endpoint.onOpen(connection);
        endpoint.onError(connection, new RuntimeException("simulated error"));
        assertEquals(0, registry.activeCount());

        AgUiWebSocketTransportSession nullConnSession = new AgUiWebSocketTransportSession("ws-null", null);
        assertFalse(nullConnSession.isOpen());
        nullConnSession.close();
    }
}
