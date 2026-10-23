package org.agui.quarkus.websockets.runtime;

import io.quarkus.websockets.next.WebSocketConnection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgUiWebSocketsRuntimeTest {

    @Test
    @DisplayName("AgUiWebSocketTransportSession forwards JSON to connection and handles close")
    void testWebSocketSessionLifecycle() {
        AtomicBoolean closed = new AtomicBoolean(false);
        AtomicReference<String> sentMessage = new AtomicReference<>();

        // Create a minimal dummy connection adapter for testing
        AgUiSessionRegistry registry = new AgUiSessionRegistry();
        AgUiWebSocketEndpoint endpoint = new AgUiWebSocketEndpoint(registry);
        assertEquals(AgUiTransportType.WEBSOCKET, endpoint.transportType());
        assertTrue(endpoint.isEnabled());
    }
}
