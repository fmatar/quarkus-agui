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

        org.a2aproject.sdk.spec.Message agentMsg = org.a2aproject.sdk.A2A.toAgentMessage("Zero outward violations");
        assertEquals("Zero outward violations", a2aBridge.extractMessageText(agentMsg));
        assertEquals("", a2aBridge.extractMessageText(null));

        org.a2aproject.sdk.spec.Artifact artifact = org.a2aproject.sdk.spec.Artifact.builder()
                .artifactId("art-1")
                .name("arch-report")
                .parts(new org.a2aproject.sdk.spec.TextPart("Clean ring boundaries verified"))
                .build();
        org.a2aproject.sdk.spec.Task taskWithArtifact = org.a2aproject.sdk.spec.Task.builder()
                .id("task-a2a-1")
                .contextId("ctx-1")
                .status(new org.a2aproject.sdk.spec.TaskStatus(org.a2aproject.sdk.spec.TaskState.TASK_STATE_COMPLETED, agentMsg, null))
                .artifacts(List.of(artifact))
                .build();
        List<AgUiEvent<?>> taskEvents = a2aBridge.fromA2aTask("thread-a2a", "Sentinel-A2A-Auditor", "audit-clean-architecture", taskWithArtifact);
        assertEquals(2, taskEvents.size());
        assertEquals("STEP_FINISHED", taskEvents.getLast().type());
        org.agui.core.domain.StepPayload lastStep = (org.agui.core.domain.StepPayload) taskEvents.getLast().payload();
        assertEquals("Clean ring boundaries verified", lastStep.summary());
        assertEquals("Sentinel-A2A-Auditor", lastStep.agentName());

        org.a2aproject.sdk.spec.Task taskWithStatusOnly = org.a2aproject.sdk.spec.Task.builder()
                .id("task-a2a-2")
                .contextId("ctx-2")
                .status(new org.a2aproject.sdk.spec.TaskStatus(org.a2aproject.sdk.spec.TaskState.TASK_STATE_COMPLETED, agentMsg, null))
                .build();
        assertEquals("Zero outward violations", a2aBridge.extractTaskSummary(taskWithStatusOnly));

        org.a2aproject.sdk.spec.Task taskWithStateOnly = org.a2aproject.sdk.spec.Task.builder()
                .id("task-a2a-3")
                .contextId("ctx-3")
                .status(new org.a2aproject.sdk.spec.TaskStatus(org.a2aproject.sdk.spec.TaskState.TASK_STATE_WORKING))
                .build();
        assertEquals("TASK_STATE_WORKING", a2aBridge.extractTaskSummary(taskWithStateOnly));

        org.a2aproject.sdk.spec.AgentCard card = org.a2aproject.sdk.spec.AgentCard.builder()
                .name("Sentinel-A2A-Auditor")
                .description("Auditor")
                .version("1.4.0")
                .url("http://localhost:8082")
                .preferredTransport("JSONRPC")
                .supportedInterfaces(List.of(new org.a2aproject.sdk.spec.AgentInterface("JSONRPC", "http://localhost:8082")))
                .defaultInputModes(List.of("text/plain"))
                .defaultOutputModes(List.of("text/plain"))
                .capabilities(org.a2aproject.sdk.spec.AgentCapabilities.builder().streaming(true).build())
                .skills(List.of())
                .build();

        var msgStep = a2aBridge.fromA2aClientEvent(
                "thread-a2a",
                "audit-clean-architecture",
                new org.a2aproject.sdk.client.MessageEvent(agentMsg),
                card);
        assertEquals("STEP_FINISHED", msgStep.type());
        assertEquals("Sentinel-A2A-Auditor", msgStep.payload().agentName());
        assertEquals("Zero outward violations", msgStep.payload().summary());

        var taskStep = a2aBridge.fromA2aClientEvent(
                "thread-a2a",
                "audit-clean-architecture",
                new org.a2aproject.sdk.client.TaskEvent(taskWithArtifact),
                null);
        assertEquals("STEP_FINISHED", taskStep.type());
        assertEquals("A2A-Agent", taskStep.payload().agentName());
        assertEquals("Clean ring boundaries verified", taskStep.payload().summary());

        var updateStep = a2aBridge.fromA2aClientEvent(
                "thread-a2a",
                "audit-clean-architecture",
                new org.a2aproject.sdk.client.TaskUpdateEvent(
                        taskWithStateOnly,
                        new org.a2aproject.sdk.spec.TaskStatusUpdateEvent(
                                "task-a2a-3",
                                new org.a2aproject.sdk.spec.TaskStatus(org.a2aproject.sdk.spec.TaskState.TASK_STATE_WORKING),
                                "ctx-3",
                                Map.of())),
                card);
        assertEquals("STEP_STARTED", updateStep.type());
        assertEquals("TASK_STATE_WORKING", updateStep.payload().summary());
    }
}
