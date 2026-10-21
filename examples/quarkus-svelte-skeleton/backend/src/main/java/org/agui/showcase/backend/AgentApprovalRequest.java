package org.agui.showcase.backend;

/**
 * Inbound Human-in-the-Loop (HITL) approval or rejection payload.
 */
public record AgentApprovalRequest(
        String threadId,
        boolean approved,
        String reason
) {
}
