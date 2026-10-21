package org.agui.showcase.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpsPilotOrchestratorServiceTest {

    private OpsPilotOrchestratorService service;

    @BeforeEach
    void setUp() {
        McpGatewayClient mcpClient = new McpGatewayClient();
        A2aGatewayClient a2aClient = new A2aGatewayClient();
        service = new OpsPilotOrchestratorService(mcpClient, a2aClient);
    }

    @Test
    void catalogReturnsMcpToolsA2aAgentCardAndClusterState() {
        Map<String, Object> catalog = service.getCatalog();
        assertEquals("deterministic", catalog.get("llmMode"));
        List<?> mcpTools = (List<?>) catalog.get("mcpTools");
        assertNotNull(mcpTools);
        assertEquals(3, mcpTools.size());

        Map<?, ?> a2aAgent = (Map<?, ?>) catalog.get("a2aAgent");
        assertNotNull(a2aAgent);
        assertEquals("Sentinel-A2A-Auditor", a2aAgent.get("name"));

        Map<?, ?> clusterState = (Map<?, ?>) catalog.get("clusterState");
        assertNotNull(clusterState);
        assertEquals("quarkus-prod-us-east-1", clusterState.get("cluster"));
    }

    @Test
    void executeChatRunsMcpHealthAndA2aArchitectureAudit() {
        AgentRunRequest req = new AgentRunRequest(
                "thread-1",
                "Inspect cluster health and audit clean architecture",
                Map.of("environment", "prod"),
                "deterministic");

        AgentChatResponse res = service.executeChat(req);
        assertEquals("thread-1", res.threadId());
        assertEquals("COMPLETED", res.status());
        assertFalse(res.mcpCalls().isEmpty());
        assertEquals("inspectClusterHealth", res.mcpCalls().getFirst().get("toolName"));
        assertFalse(res.a2aHandoffs().isEmpty());
        assertEquals("audit-clean-architecture", res.a2aHandoffs().getFirst().get("skillId"));
        assertTrue(res.reply().contains("Sentinel-A2A-Auditor"));
    }

    @Test
    void executeChatHandlesLogQueryAndSecurityAudit() {
        AgentRunRequest req = new AgentRunRequest(
                "thread-logs",
                "Query service logs for payment-gateway and assess security posture",
                Map.of(),
                "deterministic");

        AgentChatResponse res = service.executeChat(req);
        assertEquals("COMPLETED", res.status());
        assertTrue(res.mcpCalls().stream().anyMatch(c -> "queryServiceLogs".equals(c.get("toolName"))));
        assertTrue(res.a2aHandoffs().stream().anyMatch(h -> "assess-security-posture".equals(h.get("skillId"))));
    }

    @Test
    void executeChatScalesDeploymentWithinSafeThreshold() {
        AgentRunRequest req = new AgentRunRequest(
                "thread-scale",
                "Scale deployment order-processor to 4 replicas and plan incident remediation",
                Map.of(),
                "deterministic");

        AgentChatResponse res = service.executeChat(req);
        assertEquals("COMPLETED", res.status());
        assertTrue(res.mcpCalls().stream().anyMatch(c -> "scaleDeployment".equals(c.get("toolName"))));
        assertTrue(res.a2aHandoffs().stream().anyMatch(h -> "plan-incident-remediation".equals(h.get("skillId"))));
    }

    @Test
    void streamRunEmitsFormattedSseFramesInDeterministicOrder() {
        AgentRunRequest req = new AgentRunRequest(
                "thread-sse-1",
                "Inspect cluster health and audit architecture",
                Map.of(),
                "deterministic");

        List<String> frames = service.streamRun(req).collect().asList().await().indefinitely();
        assertFalse(frames.isEmpty());
        assertTrue(frames.getFirst().startsWith("data: "));
        assertTrue(frames.getFirst().endsWith("\n\n"));
        assertTrue(frames.getFirst().contains("\"type\":\"RUN_STARTED\""));
        assertTrue(frames.stream().anyMatch(f -> f.contains("\"type\":\"TOOL_CALL\"")));
        assertTrue(frames.stream().anyMatch(f -> f.contains("\"type\":\"TOOL_RESULT\"")));
        assertTrue(frames.stream().anyMatch(f -> f.contains("\"type\":\"TEXT_DELTA\"")));
        assertTrue(frames.getLast().contains("\"type\":\"RUN_FINISHED\""));
    }
}
