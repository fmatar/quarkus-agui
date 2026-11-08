package org.agui.quarkus.sse.runtime;

import java.util.ArrayList;
import java.util.List;
import org.agui.core.domain.AgUiEvent;
import org.agui.core.domain.AgUiEventType;
import org.agui.core.domain.TextDeltaPayload;
import org.agui.quarkus.core.runtime.application.port.AgUiSessionRegistry;
import org.agui.quarkus.core.runtime.domain.AgUiTransportType;
import org.agui.quarkus.sse.runtime.adapters.AgUiSseBridge;
import org.agui.quarkus.sse.runtime.adapters.AgUiSseTransportSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgUiSseRuntimeTest {

    @Test
    @DisplayName("AgUiSseTransportSession creates reactive stream, emits SSE frames to subscribers, and closes cleanly")
    void testSseSessionLifecycle() {
        AgUiSessionRegistry registry = new AgUiSessionRegistry();
        AgUiSseBridge bridge = new AgUiSseBridge(registry);
        assertEquals(AgUiTransportType.SSE, bridge.transportType());
        assertTrue(bridge.isEnabled());

        AgUiSseTransportSession session = bridge.createSession("sse-sess-1");
        assertEquals("sse-sess-1", session.sessionId());
        assertEquals(AgUiTransportType.SSE, session.transportType());
        assertTrue(session.isOpen());
        assertEquals(1, registry.activeCount());

        List<String> receivedFrames = new ArrayList<>();
        session.toMulti().subscribe().with(receivedFrames::add);

        AgUiEvent<TextDeltaPayload> event = AgUiEvent.of(AgUiEventType.TEXT_DELTA, "th-1", new TextDeltaPayload("hello SSE"));
        session.emit(null);
        session.emit(event);
        session.close();
        session.emit(event);

        assertFalse(session.isOpen());
        assertEquals(1, receivedFrames.size());
        assertTrue(receivedFrames.getFirst().startsWith("data: {"));
        assertTrue(receivedFrames.getFirst().contains("hello SSE"));

        registry.unregister("sse-sess-1");
        assertEquals(0, registry.activeCount());
    }
}
