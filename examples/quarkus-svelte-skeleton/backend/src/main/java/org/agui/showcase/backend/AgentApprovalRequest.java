package org.agui.showcase.backend;

import java.util.Map;

/**
 * Inbound Human-in-the-Loop (HITL) approval or rejection payload, supporting optional parameter overrides.
 */
public record AgentApprovalRequest(
        String threadId,
        boolean approved,
        String reason,
        Map<String, Object> modifiedArgs
) {
    public AgentApprovalRequest(String threadId, boolean approved, String reason) {
        this(threadId, approved, reason, null);
    }
}
