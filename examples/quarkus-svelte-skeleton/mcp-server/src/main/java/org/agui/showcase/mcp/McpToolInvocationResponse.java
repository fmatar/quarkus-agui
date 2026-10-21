package org.agui.showcase.mcp;

public record McpToolInvocationResponse(
    String serverName,
    String tool,
    Object result,
    boolean isError,
    long latencyMs
) {}
