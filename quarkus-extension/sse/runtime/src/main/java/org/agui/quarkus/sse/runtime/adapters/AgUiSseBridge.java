package org.agui.quarkus.sse.runtime.adapters;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.agui.quarkus.core.runtime.application.port.AgUiSessionRegistry;
import org.agui.quarkus.core.runtime.application.port.AgUiTransportBridge;
import org.agui.quarkus.core.runtime.domain.AgUiTransportType;

/**
 * SSE Transport Bridge providing factory methods for SSE sessions and registering them in the registry.
 */
@ApplicationScoped
public class AgUiSseBridge implements AgUiTransportBridge {

    private final AgUiSessionRegistry registry;

    @Inject
    public AgUiSseBridge(AgUiSessionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public AgUiTransportType transportType() {
        return AgUiTransportType.SSE;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    public AgUiSseTransportSession createSession(String sessionId) {
        AgUiSseTransportSession session = new AgUiSseTransportSession(sessionId);
        registry.register(session);
        return session;
    }
}
