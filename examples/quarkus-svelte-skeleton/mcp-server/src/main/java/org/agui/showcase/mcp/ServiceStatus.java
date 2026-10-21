package org.agui.showcase.mcp;

public record ServiceStatus(
    String name,
    String status,
    int replicas,
    double cpuPercent,
    double memoryMb
) {
    public ServiceStatus {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Service name must not be blank");
        }
        if (replicas < 1) {
            throw new IllegalArgumentException("Replicas must be >= 1");
        }
    }
}
