package org.agui.a2a.domain;

import java.util.Map;
import java.util.Objects;

/**
 * Domain value object representing a delegated A2A (Agent2Agent) specialist handoff.
 */
public record A2aHandoff(
        String taskId,
        String agentName,
        String skillId,
        String summary,
        Map<String, Object> metadata
) {
    public A2aHandoff {
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(agentName, "agentName must not be null");
        Objects.requireNonNull(skillId, "skillId must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
