package org.agui.a2a.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable domain value object representing the outcome of an official A2A specialist delegation.
 */
public record A2aDelegationResult(
        String taskId,
        String agentName,
        String skillId,
        String status,
        boolean isError,
        String summary,
        Map<String, Object> data
) {
    public A2aDelegationResult {
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(agentName, "agentName must not be null");
        Objects.requireNonNull(skillId, "skillId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
        data = data == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }
}
