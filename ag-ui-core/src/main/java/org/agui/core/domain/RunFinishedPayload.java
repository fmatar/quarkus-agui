package org.agui.core.domain;

import java.util.Objects;

/**
 * Marks run termination with status (completed, failed, interrupted, cancelled).
 */
public record RunFinishedPayload(
        String threadId,
        String status
) {
    public RunFinishedPayload {
        Objects.requireNonNull(threadId, "threadId must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
