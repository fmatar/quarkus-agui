package org.agui.core.domain;

import java.util.Map;
import java.util.Objects;

/**
 * Signals agent intent to execute a specific tool with arguments.
 */
public record ToolCallPayload(
        String callId,
        String tool,
        Map<String, Object> args
) {
    public ToolCallPayload {
        Objects.requireNonNull(callId, "callId must not be null");
        Objects.requireNonNull(tool, "tool must not be null");
        args = args == null ? Map.of() : Map.copyOf(args);
    }
}
