package org.agui.showcase.mcp;

import java.util.Map;

public record McpToolInvocationRequest(
    String tool,
    Map<String, Object> arguments
) {}
