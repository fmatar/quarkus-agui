package org.agui.langchain4j.adapters;

import com.agui.community.core.event.Event;
import com.agui.community.core.event.ToolCallArgsEvent;
import com.agui.community.core.event.ToolCallEndEvent;
import com.agui.community.core.event.ToolCallResultEvent;
import com.agui.community.core.event.ToolCallStartEvent;
import com.agui.community.core.message.Role;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.agui.langchain4j.domain.McpToolMetadata;

/**
 * Framework-agnostic bridge converting MCP (Model Context Protocol) tool executions
 * into canonical AG-UI {@code TOOL_CALL_*} {@link Event} sequences with {@code rawEvent} provenance.
 */
@ApplicationScoped
public class AgUiMcpBridge {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Event> recordToolExecution(
            String callId,
            String parentMessageId,
            McpToolMetadata metadata,
            Map<String, Object> args,
            Object result) {
        Objects.requireNonNull(callId, "callId must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");

        long now = System.currentTimeMillis();
        Map<String, Object> safeArgs = args == null ? Map.of() : Map.copyOf(args);
        Map<String, Object> startMeta = new LinkedHashMap<>();
        startMeta.put("callId", callId);
        startMeta.put("tool", metadata.toolName());
        startMeta.put("protocol", "MCP");
        startMeta.put("callType", "mcp:tool");
        startMeta.put("server", metadata.serverName());
        startMeta.put("transport", metadata.transport());
        startMeta.put("args", safeArgs);

        Map<String, Object> resultMeta = new LinkedHashMap<>(startMeta);
        resultMeta.put("status", "OK");
        resultMeta.put("isError", false);
        resultMeta.put("result", result);

        return List.of(
                new ToolCallStartEvent(callId, metadata.toolName(), parentMessageId, now, Map.copyOf(startMeta)),
                new ToolCallArgsEvent(callId, toJsonString(safeArgs), now, null),
                new ToolCallEndEvent(callId, now, null),
                new ToolCallResultEvent("tool-msg-" + callId, callId, toJsonString(result), Role.TOOL, now, Map.copyOf(resultMeta)));
    }

    private String toJsonString(Object value) {
        if (value instanceof String str) {
            return str;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }
}
