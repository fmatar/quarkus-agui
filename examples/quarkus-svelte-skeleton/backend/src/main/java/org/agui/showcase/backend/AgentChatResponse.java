package org.agui.showcase.backend;

import java.util.List;
import java.util.Map;

/**
 * Baseline synchronous response for Wave 1 orchestration verification.
 */
public record AgentChatResponse(
        String threadId,
        String status,
        String llmMode,
        List<Map<String, Object>> mcpCalls,
        List<Map<String, Object>> a2aHandoffs,
        Map<String, Object> clusterState,
        String reply
) {
}
