package org.agui.core.domain;

import java.util.Objects;

/**
 * Emits output or response payload after tool execution.
 */
public record ToolResultPayload(
        String callId,
        String tool,
        Object result
) {
    public ToolResultPayload {
        Objects.requireNonNull(callId, "callId must not be null");
        Objects.requireNonNull(tool, "tool must not be null");
    }
}
