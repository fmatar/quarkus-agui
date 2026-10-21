package org.agui.showcase.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClusterTelemetryServiceTest {

    private ClusterTelemetryService service;

    @BeforeEach
    void setUp() {
        service = new ClusterTelemetryService();
        service.resetState();
    }

    @Test
    void inspectClusterHealthReturnsRegionAndThreeServices() {
        Map<String, Object> health = service.inspectClusterHealth("us-west-2");
        assertEquals("us-west-2", health.get("region"));
        assertEquals("DEGRADED", health.get("overallStatus"));
        @SuppressWarnings("unchecked")
        List<ServiceStatus> services = (List<ServiceStatus>) health.get("services");
        assertEquals(3, services.size());
    }

    @Test
    void inspectClusterHealthDefaultsBlankRegionToUsEast1() {
        Map<String, Object> health = service.inspectClusterHealth("   ");
        assertEquals("us-east-1", health.get("region"));
    }

    @Test
    void queryServiceLogsFiltersByServiceAndSeverityAndRespectsLimit() {
        List<LogEntry> errorLogs = service.queryServiceLogs("payment-service", "ERROR", 5);
        assertFalse(errorLogs.isEmpty());
        assertTrue(errorLogs.size() <= 5);
        for (LogEntry entry : errorLogs) {
            assertEquals("payment-service", entry.serviceName());
            assertEquals("ERROR", entry.severity());
        }

        List<LogEntry> limited = service.queryServiceLogs("payment-service", "ALL", 1);
        assertEquals(1, limited.size());
    }

    @Test
    void scaleDeploymentUpdatesReplicaCountAndCpuUtilization() {
        ServiceStatus updated = service.scaleDeployment("payment-service", 6);
        assertEquals("payment-service", updated.name());
        assertEquals(6, updated.replicas());
        assertEquals("HEALTHY", updated.status());
        assertTrue(updated.cpuPercent() < 80.0);
    }

    @Test
    void scaleDeploymentRejectsOutOfBoundsReplicasOrUnknownService() {
        assertThrows(IllegalArgumentException.class, () -> service.scaleDeployment("payment-service", 0));
        assertThrows(IllegalArgumentException.class, () -> service.scaleDeployment("payment-service", 21));
        assertThrows(IllegalArgumentException.class, () -> service.scaleDeployment("", 3));
    }

    @Test
    void dispatchToolExecutesSupportedToolsAndHandlesUnknownTool() {
        McpToolInvocationResponse healthRes = service.dispatchTool(
            new McpToolInvocationRequest("inspectClusterHealth", Map.of("region", "eu-central-1"))
        );
        assertFalse(healthRes.isError());
        assertEquals("cluster-telemetry-mcp", healthRes.serverName());
        assertEquals("inspectClusterHealth", healthRes.tool());
        assertNotNull(healthRes.result());

        McpToolInvocationResponse logsRes = service.dispatchTool(
            new McpToolInvocationRequest("queryServiceLogs", Map.of("serviceName", "payment-service", "severity", "ERROR", "limit", 2))
        );
        assertFalse(logsRes.isError());

        McpToolInvocationResponse scaleRes = service.dispatchTool(
            new McpToolInvocationRequest("scaleDeployment", Map.of("serviceName", "payment-service", "targetReplicas", 4))
        );
        assertFalse(scaleRes.isError());

        McpToolInvocationResponse unknownRes = service.dispatchTool(
            new McpToolInvocationRequest("nonExistentTool", Map.of())
        );
        assertTrue(unknownRes.isError());
    }
}
