package org.agui.core.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable AG-UI Event Envelope (Level 0 Domain Core).
 *
 * @param <T> Typed event payload record or map
 */
public record AgUiEvent<T>(
        String id,
        String type,
        String threadId,
        long timestamp,
        T payload
) {
    public AgUiEvent {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(threadId, "threadId must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        if (threadId.isBlank()) {
            throw new IllegalArgumentException("threadId must not be blank");
        }
    }

    public static <T> AgUiEvent<T> of(AgUiEventType eventType, String threadId, T payload) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        return new AgUiEvent<>(
                "evt-" + UUID.randomUUID().toString().substring(0, 8),
                eventType.name(),
                threadId,
                System.currentTimeMillis(),
                payload);
    }

    public static <T> AgUiEvent<T> ofType(String type, String threadId, T payload) {
        return new AgUiEvent<>(
                "evt-" + UUID.randomUUID().toString().substring(0, 8),
                type,
                threadId,
                System.currentTimeMillis(),
                payload);
    }
}
