package org.agui.core.domain;

import java.util.Objects;

/**
 * Emitted when an AG-UI agent session or run initializes.
 */
public record RunStartedPayload(
        String threadId,
        String status
) {
    public RunStartedPayload {
        Objects.requireNonNull(threadId, "threadId must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
