package org.agui.core.domain;

import java.util.Map;

/**
 * Shared state snapshot synchronization payload (STATE_SNAPSHOT).
 */
public record StateSnapshotPayload(
        Map<String, Object> state
) {
    public StateSnapshotPayload {
        state = state == null ? Map.of() : Map.copyOf(state);
    }
}
