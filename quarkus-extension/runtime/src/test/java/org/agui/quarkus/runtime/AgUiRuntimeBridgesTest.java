package org.agui.quarkus.runtime;

import org.agui.quarkus.core.runtime.adapters.AgUiA2aBridge;
import org.agui.quarkus.core.runtime.adapters.AgUiBridge;
import org.agui.quarkus.core.runtime.adapters.AgUiMcpBridge;
import org.agui.quarkus.core.runtime.application.port.AgUiSessionRegistry;
import org.agui.quarkus.sse.runtime.adapters.AgUiSseBridge;
import org.agui.quarkus.websockets.runtime.adapters.AgUiWebSocketEndpoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class AgUiStarterBridgesTest {

    @Test
    @DisplayName("Starter module resolves all core, SSE, and WebSocket adapters on classpath")
    void starterResolvesAllTransports() {
        AgUiSessionRegistry registry = new AgUiSessionRegistry();
        AgUiBridge bridge = new AgUiBridge();
        AgUiMcpBridge mcpBridge = new AgUiMcpBridge();
        AgUiA2aBridge a2aBridge = new AgUiA2aBridge();
        AgUiSseBridge sseBridge = new AgUiSseBridge(registry);
        AgUiWebSocketEndpoint wsEndpoint = new AgUiWebSocketEndpoint(registry);

        assertNotNull(bridge);
        assertNotNull(mcpBridge);
        assertNotNull(a2aBridge);
        assertNotNull(sseBridge);
        assertNotNull(wsEndpoint);
    }
}
