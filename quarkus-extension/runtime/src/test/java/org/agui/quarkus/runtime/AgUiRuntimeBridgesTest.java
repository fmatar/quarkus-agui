package org.agui.quarkus.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.agui.core.domain.A2aHandoffPayload;
import org.agui.core.domain.AgUiEvent;
import org.agui.core.domain.AgUiEventType;
import org.agui.core.domain.McpToolMetadata;
import org.agui.core.domain.TextDeltaPayload;
import org.agui.core.domain.ToolCallPayload;
import org.agui.quarkus.runtime.adapters.AgUiA2aBridge;
import org.agui.quarkus.runtime.adapters.AgUiBridge;
import org.agui.quarkus.runtime.adapters.AgUiMcpBridge;
import org.junit.jupiter.api.Test;

class AgUiRuntimeBridgesTest {

    @Test
    void agUiBridgeStreamsTokensAndSseFramesViaMutinyMulti() {
        AgUiBridge bridge = new AgUiBridge();
        assertNotNull(bridge.formatter());

        List<AgUiEvent<?>> events = bridge.streamTokens("thread-rt-1", Arrays.asList("Hello ", "", null, "Quarkus!"))
                .collect().asList().await().indefinitely();
        assertEquals(4, events.size());
        assertEquals("RUN_STARTED", events.getFirst().type());
        assertEquals("TEXT_DELTA", events.get(1).type());
        assertEquals("RUN_FINISHED", events.getLast().type());

        List<String> frames = bridge.streamSseFrames(events).collect().asList().await().indefinitely();
        assertEquals(4, frames.size());
        assertTrue(frames.getFirst().startsWith("data: {"));
        assertTrue(frames.getFirst().endsWith("}\n\n"));

        AgUiEvent<TextDeltaPayload> single = AgUiEvent.of(
                AgUiEventType.TEXT_DELTA,
                "thread-rt-1",
                new TextDeltaPayload("token"));
        String json = bridge.toSseServerSentEventData(single);
        assertTrue(json.contains("\"content\":\"token\""));
    }

    @Test
    void agUiMcpBridgeEnrichesToolCallAndResultEvents() {
        AgUiMcpBridge mcpBridge = new AgUiMcpBridge();
        McpToolMetadata meta = new McpToolMetadata("quarkus-mcp-cluster-ops (:8081)", "scaleDeployment", "http-sse");

        List<AgUiEvent<?>> events = mcpBridge.recordToolExecution(
                "thread-mcp",
                "call-101",
                meta,
                Map.of("serviceName", "payment-gateway", "replicas", 4),
                Map.of("status", "SCALED"));
        assertEquals(2, events.size());
        assertEquals("TOOL_CALL", events.getFirst().type());
        ToolCallPayload callPayload = (ToolCallPayload) events.getFirst().payload();
        assertEquals("quarkus-mcp-cluster-ops (:8081)", callPayload.args().get("_mcpServer"));
        assertEquals(4, callPayload.args().get("replicas"));
        assertEquals("TOOL_RESULT", events.getLast().type());

        List<AgUiEvent<?>> nullArgsEvents = mcpBridge.recordToolExecution("thread-mcp", "call-102", meta, null, "OK");
        assertEquals(2, nullArgsEvents.size());
    }

    @Test
    void agUiA2aBridgeWrapsDelegationIntoStepStartedAndFinishedEvents() {
        AgUiA2aBridge a2aBridge = new AgUiA2aBridge();
        A2aHandoffPayload handoff = new A2aHandoffPayload(
                "task-202",
                "Sentinel-A2A-Auditor",
                "audit-clean-architecture",
                "Conforming",
                Map.of("violations", 0));

        List<AgUiEvent<?>> events = a2aBridge.recordDelegation("thread-a2a", handoff);
        assertEquals(2, events.size());
        assertEquals("STEP_STARTED", events.getFirst().type());
        assertEquals("STEP_FINISHED", events.getLast().type());
    }
}
