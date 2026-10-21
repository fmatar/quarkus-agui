package org.agui.core.domain;

import java.util.Map;
import java.util.Objects;

/**
 * A2A (Agent-to-Agent) specialist delegation payload.
 */
public record A2aHandoffPayload(
        String taskId,
        String agentName,
        String skillId,
        String summary,
        Map<String, Object> report
) {
    public A2aHandoffPayload {
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(agentName, "agentName must not be null");
        Objects.requireNonNull(skillId, "skillId must not be null");
        report = report == null ? Map.of() : Map.copyOf(report);
    }
}
