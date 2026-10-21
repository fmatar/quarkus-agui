package org.agui.core.domain;

import java.util.Objects;

/**
 * System lifecycle or thinking state change payload.
 */
public record StatusPayload(
        String phase,
        String message
) {
    public StatusPayload {
        Objects.requireNonNull(phase, "phase must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }
}
