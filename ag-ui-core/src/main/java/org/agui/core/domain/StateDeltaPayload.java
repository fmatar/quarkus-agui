package org.agui.core.domain;

import java.util.Map;

/**
 * Incremental shared state delta payload (STATE_DELTA).
 */
public record StateDeltaPayload(
        Map<String, Object> delta
) {
    public StateDeltaPayload {
        delta = delta == null ? Map.of() : Map.copyOf(delta);
    }
}
