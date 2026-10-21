package org.agui.core.domain;

import java.util.Objects;

/**
 * MCP (Model Context Protocol) tool provenance metadata.
 */
public record McpToolMetadata(
        String serverName,
        String toolName,
        String transport
) {
    public McpToolMetadata {
        Objects.requireNonNull(serverName, "serverName must not be null");
        Objects.requireNonNull(toolName, "toolName must not be null");
        Objects.requireNonNull(transport, "transport must not be null");
    }
}
