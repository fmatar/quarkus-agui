package org.agui.showcase.mcp;

public record LogEntry(
    String timestamp,
    String serviceName,
    String severity,
    String message
) {}
