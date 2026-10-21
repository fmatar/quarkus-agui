package org.agui.showcase.backend;

import java.util.Map;

/**
 * Inbound AG-UI / Orchestrator prompt request.
 */
public record AgentRunRequest(
        String threadId,
        String prompt,
        Map<String, Object> state,
        String mode
) {
}
