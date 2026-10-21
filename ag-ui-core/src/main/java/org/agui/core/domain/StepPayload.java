package org.agui.core.domain;

import java.util.Objects;

/**
 * Represents a structured multi-step or A2A sub-agent delegation lifecycle step.
 */
public record StepPayload(
        String stepId,
        String protocol,
        String agentName,
        String skillId,
        String summary
) {
    public StepPayload {
        Objects.requireNonNull(stepId, "stepId must not be null");
        Objects.requireNonNull(protocol, "protocol must not be null");
        Objects.requireNonNull(agentName, "agentName must not be null");
        Objects.requireNonNull(skillId, "skillId must not be null");
    }
}
