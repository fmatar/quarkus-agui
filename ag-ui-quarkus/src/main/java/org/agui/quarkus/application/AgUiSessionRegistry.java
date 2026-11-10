package org.agui.quarkus.application;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry tracking active {@link AgUiTransportSession} connections.
 */
@ApplicationScoped
public class AgUiSessionRegistry {

    private final Map<String, AgUiTransportSession> sessions = new ConcurrentHashMap<>();

    public void register(AgUiTransportSession session) {
        Objects.requireNonNull(session, "session must not be null");
        sessions.put(session.sessionId(), session);
    }

    public void unregister(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }

    public Optional<AgUiTransportSession> find(String sessionId) {
        return sessionId == null ? Optional.empty() : Optional.ofNullable(sessions.get(sessionId));
    }

    public int activeCount() {
        return sessions.size();
    }

    public void broadcastJson(String json) {
        Objects.requireNonNull(json, "json must not be null");
        for (AgUiTransportSession session : sessions.values()) {
            if (session.isOpen()) {
                session.sendJson(json);
            }
        }
    }
}
