package org.agui.core.application;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.agui.core.domain.AgUiEvent;
import org.agui.core.domain.AgUiTransportType;

/**
 * Transport-agnostic session registry managing active client connections across SSE and WebSockets.
 */
@ApplicationScoped
public class AgUiSessionRegistry {

    private final Map<String, AgUiTransportSession> sessions = new ConcurrentHashMap<>();

    public void register(AgUiTransportSession session) {
        if (session != null && session.sessionId() != null) {
            sessions.put(session.sessionId(), session);
        }
    }

    public void unregister(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }

    public Optional<AgUiTransportSession> find(String sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public Collection<AgUiTransportSession> allSessions() {
        return sessions.values();
    }

    public int activeCount() {
        return sessions.size();
    }

    public void broadcast(AgUiEvent<?> event) {
        if (event == null) {
            return;
        }
        for (AgUiTransportSession session : sessions.values()) {
            if (session.isOpen()) {
                session.emit(event);
            }
        }
    }

    public void broadcastToTransport(AgUiTransportType transportType, AgUiEvent<?> event) {
        if (transportType == null || event == null) {
            return;
        }
        for (AgUiTransportSession session : sessions.values()) {
            if (session.transportType() == transportType && session.isOpen()) {
                session.emit(event);
            }
        }
    }
}
