package org.agui.quarkus.runtime.adapters;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.agui.core.domain.AgUiEvent;
import org.agui.core.domain.AgUiEventType;
import org.agui.core.domain.McpToolMetadata;
import org.agui.core.domain.ToolCallPayload;
import org.agui.core.domain.ToolResultPayload;

/**
 * Quarkus Runtime Bridge enriching MCP (Model Context Protocol) tool calls into AG-UI events.
 */
@ApplicationScoped
public class AgUiMcpBridge {

    public List<AgUiEvent<?>> recordToolExecution(
            String threadId,
            String callId,
            McpToolMetadata metadata,
            Map<String, Object> args,
            Object result) {
        Objects.requireNonNull(threadId, "threadId must not be null");
        Objects.requireNonNull(callId, "callId must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");

        Map<String, Object> enrichedArgs = new LinkedHashMap<>();
        if (args != null) {
            enrichedArgs.putAll(args);
        }
        enrichedArgs.put("_mcpServer", metadata.serverName());
        enrichedArgs.put("_mcpTransport", metadata.transport());

        AgUiEvent<ToolCallPayload> callEvent = AgUiEvent.of(
                AgUiEventType.TOOL_CALL,
                threadId,
                new ToolCallPayload(callId, metadata.toolName(), enrichedArgs));

        AgUiEvent<ToolResultPayload> resultEvent = AgUiEvent.of(
                AgUiEventType.TOOL_RESULT,
                threadId,
                new ToolResultPayload(callId, metadata.toolName(), result));

        return List.of(callEvent, resultEvent);
    }
}
