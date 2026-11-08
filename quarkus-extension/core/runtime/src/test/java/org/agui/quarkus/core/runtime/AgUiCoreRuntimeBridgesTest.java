package org.agui.quarkus.core.runtime;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.agui.core.domain.A2aHandoffPayload;
import org.agui.core.domain.AgUiEvent;
import org.agui.core.domain.AgUiEventType;
import org.agui.core.domain.McpToolMetadata;
import org.agui.core.domain.TextDeltaPayload;
import org.agui.core.domain.ToolCallPayload;
import org.agui.quarkus.core.runtime.adapters.AgUiA2aBridge;
import org.agui.quarkus.core.runtime.adapters.AgUiBridge;
import org.agui.quarkus.core.runtime.adapters.AgUiMcpBridge;
import org.agui.quarkus.core.runtime.application.port.AgUiSessionRegistry;
import org.agui.quarkus.core.runtime.application.port.AgUiTransportSession;
import org.agui.quarkus.core.runtime.domain.AgUiTransportType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgUiCoreRuntimeBridgesTest {

    @Test
    @DisplayName("AgUiBridge correctly forms Mutiny stream and formats SSE data payload")
    void agUiBridgeTokenStreaming() {
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
    @DisplayName("AgUiMcpBridge produces structured ToolCall and ToolResult events")
    void agUiMcpBridgeToolLifecycle() {
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
    @DisplayName("AgUiA2aBridge produces structured A2A step delegation events")
    void agUiA2aBridgeDelegationLifecycle() {
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

        org.a2aproject.sdk.spec.Message agentMsg = org.a2aproject.sdk.A2A.toAgentMessage("Zero outward violations");
        assertEquals("Zero outward violations", a2aBridge.extractMessageText(agentMsg));
        assertEquals("", a2aBridge.extractMessageText(null));

        org.a2aproject.sdk.spec.Artifact artifact = org.a2aproject.sdk.spec.Artifact.builder()
                .artifactId("art-1")
                .parts(List.of(new org.a2aproject.sdk.spec.TextPart("Artifact summary text")))
                .build();
        org.a2aproject.sdk.spec.Task taskWithArtifact = org.a2aproject.sdk.spec.Task.builder()
                .id("task-art-1")
                .contextId("ctx-1")
                .status(new org.a2aproject.sdk.spec.TaskStatus(org.a2aproject.sdk.spec.TaskState.TASK_STATE_COMPLETED))
                .artifacts(List.of(artifact))
                .metadata(Map.of("ring", "domain"))
                .build();
        List<AgUiEvent<?>> taskEvents = a2aBridge.fromA2aTask("thread-a2a", "Sentinel-A2A-Auditor", "audit-clean-architecture", taskWithArtifact);
        assertEquals(2, taskEvents.size());
        assertEquals("Artifact summary text", a2aBridge.extractTaskSummary(taskWithArtifact));

        org.a2aproject.sdk.spec.Task taskWithStatusMsg = org.a2aproject.sdk.spec.Task.builder()
                .id("task-msg-1")
                .contextId("ctx-1")
                .status(new org.a2aproject.sdk.spec.TaskStatus(org.a2aproject.sdk.spec.TaskState.TASK_STATE_WORKING, agentMsg, null))
                .build();
        assertEquals("Zero outward violations", a2aBridge.extractTaskSummary(taskWithStatusMsg));

        org.a2aproject.sdk.spec.Task taskStateOnly = org.a2aproject.sdk.spec.Task.builder()
                .id("task-state-1")
                .contextId("ctx-1")
                .status(new org.a2aproject.sdk.spec.TaskStatus(org.a2aproject.sdk.spec.TaskState.TASK_STATE_SUBMITTED))
                .build();
        assertEquals("TASK_STATE_SUBMITTED", a2aBridge.extractTaskSummary(taskStateOnly));

        org.a2aproject.sdk.client.MessageEvent msgEvent = new org.a2aproject.sdk.client.MessageEvent(agentMsg);
        assertEquals("STEP_FINISHED", a2aBridge.fromA2aClientEvent("thread-a2a", "audit", msgEvent, null).type());

        org.a2aproject.sdk.client.TaskEvent taskEvent = new org.a2aproject.sdk.client.TaskEvent(taskWithArtifact);
        assertEquals("STEP_FINISHED", a2aBridge.fromA2aClientEvent("thread-a2a", "audit", taskEvent, null).type());
    }

    @Test
    @DisplayName("AgUiSessionRegistry registers, broadcasts, retrieves, and unregisters sessions")
    void agUiSessionRegistryLifecycle() {
        AgUiSessionRegistry registry = new AgUiSessionRegistry();
        assertEquals(0, registry.activeCount());
        registry.register(null);
        registry.unregister(null);
        assertFalse(registry.find(null).isPresent());

        java.util.concurrent.atomic.AtomicInteger wsEmits = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger sseEmits = new java.util.concurrent.atomic.AtomicInteger();

        AgUiTransportSession wsSession = new AgUiTransportSession() {
            private boolean open = true;
            @Override
            public String sessionId() { return "sess-ws"; }
            @Override
            public AgUiTransportType transportType() { return AgUiTransportType.WEBSOCKET; }
            @Override
            public boolean isOpen() { return open; }
            @Override
            public void emit(AgUiEvent<?> event) { wsEmits.incrementAndGet(); }
            @Override
            public void close() { open = false; }
        };

        AgUiTransportSession sseSession = new AgUiTransportSession() {
            private boolean open = true;
            @Override
            public String sessionId() { return "sess-sse"; }
            @Override
            public AgUiTransportType transportType() { return AgUiTransportType.SSE; }
            @Override
            public boolean isOpen() { return open; }
            @Override
            public void emit(AgUiEvent<?> event) { sseEmits.incrementAndGet(); }
            @Override
            public void close() { open = false; }
        };

        registry.register(wsSession);
        registry.register(sseSession);
        assertEquals(2, registry.activeCount());
        assertEquals(2, registry.allSessions().size());
        assertTrue(registry.find("sess-ws").isPresent());

        AgUiEvent<TextDeltaPayload> evt = AgUiEvent.of(AgUiEventType.TEXT_DELTA, "th-1", new TextDeltaPayload("broadcast"));
        registry.broadcast(null);
        registry.broadcast(evt);
        assertEquals(1, wsEmits.get());
        assertEquals(1, sseEmits.get());

        registry.broadcastToTransport(null, evt);
        registry.broadcastToTransport(AgUiTransportType.SSE, null);
        registry.broadcastToTransport(AgUiTransportType.SSE, evt);
        assertEquals(1, wsEmits.get());
        assertEquals(2, sseEmits.get());

        wsSession.close();
        registry.broadcast(evt);
        assertEquals(1, wsEmits.get());
        assertEquals(3, sseEmits.get());

        registry.unregister("sess-ws");
        registry.unregister("sess-sse");
        assertEquals(0, registry.activeCount());
        assertFalse(registry.find("sess-ws").isPresent());
    }
}
