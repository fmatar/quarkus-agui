package org.agui.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Map;
import org.a2aproject.sdk.A2A;
import org.a2aproject.sdk.client.MessageEvent;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.client.TaskUpdateEvent;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.agui.a2a.adapters.AgUiA2aBridge;
import org.agui.core.domain.A2aHandoffPayload;
import org.agui.core.domain.AgUiEvent;
import org.agui.core.domain.StepPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AgUiA2aBridgeTest {

    @Test
    @DisplayName("AgUiA2aBridge produces structured A2A step delegation events for tasks, messages, and updates")
    void agUiA2aBridgeDelegationLifecycle() {
        AgUiA2aBridge a2aBridge = new AgUiA2aBridge();
        A2aHandoffPayload handoff = new A2aHandoffPayload(
                "task-202",
                "Nomad-A2A-Specialist",
                "verify-travel-advisory",
                "Destination advisory verified",
                Map.of("country", "Japan"));

        List<AgUiEvent<?>> events = a2aBridge.recordDelegation("thread-a2a", handoff);
        assertEquals(2, events.size());
        assertEquals("STEP_STARTED", events.getFirst().type());
        assertEquals("STEP_FINISHED", events.getLast().type());

        Message agentMsg = A2A.toAgentMessage("Advisory verified");
        assertEquals("Advisory verified", a2aBridge.extractMessageText(agentMsg));
        assertEquals("", a2aBridge.extractMessageText(null));

        Artifact artifact = Artifact.builder()
                .artifactId("art-1")
                .parts(List.of(new TextPart("Artifact summary text")))
                .build();
        Task taskWithArtifact = Task.builder()
                .id("task-art-1")
                .contextId("ctx-1")
                .status(new TaskStatus(TaskState.TASK_STATE_COMPLETED))
                .artifacts(List.of(artifact))
                .metadata(Map.of("city", "Kyoto"))
                .build();
        List<AgUiEvent<?>> taskEvents = a2aBridge.fromA2aTask(
                "thread-a2a",
                "Nomad-A2A-Specialist",
                "verify-travel-advisory",
                taskWithArtifact);
        assertEquals(2, taskEvents.size());
        assertEquals("Artifact summary text", a2aBridge.extractTaskSummary(taskWithArtifact));

        Task taskNullMeta = Task.builder()
                .id("task-null-meta")
                .contextId("ctx-1")
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING, agentMsg, null))
                .build();
        assertEquals(2, a2aBridge.fromA2aTask("thread-a2a", "Nomad", "skill", taskNullMeta).size());
        assertEquals("Advisory verified", a2aBridge.extractTaskSummary(taskNullMeta));

        Task taskStateOnly = Task.builder()
                .id("task-state-1")
                .contextId("ctx-1")
                .status(new TaskStatus(TaskState.TASK_STATE_SUBMITTED))
                .build();
        assertEquals("TASK_STATE_SUBMITTED", a2aBridge.extractTaskSummary(taskStateOnly));

        MessageEvent msgEvent = new MessageEvent(agentMsg);
        assertEquals("STEP_FINISHED", a2aBridge.fromA2aClientEvent("thread-a2a", "verify", msgEvent, null).type());

        Message msgWithTaskId = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .parts(List.of(new TextPart("Task-linked message")))
                .taskId("task-linked-1")
                .messageId("msg-1")
                .build();
        AgUiEvent<StepPayload> linkedEvt = a2aBridge.fromA2aClientEvent(
                "thread-a2a",
                "verify",
                new MessageEvent(msgWithTaskId),
                null);
        assertEquals("task-linked-1", linkedEvt.payload().stepId());

        TaskEvent taskEvent = new TaskEvent(taskWithArtifact);
        assertEquals("STEP_FINISHED", a2aBridge.fromA2aClientEvent("thread-a2a", "verify", taskEvent, null).type());

        TaskUpdateEvent updateEvent = new TaskUpdateEvent(
                taskStateOnly,
                new TaskStatusUpdateEvent("task-state-1", new TaskStatus(TaskState.TASK_STATE_WORKING), "ctx-1", Map.of()));
        AgentCard card = AgentCard.builder()
                .name("Nomad-Specialist")
                .description("Travel specialist")
                .supportedInterfaces(List.of(new org.a2aproject.sdk.spec.AgentInterface("JSONRPC", "http://localhost:8082")))
                .version("1.0.0")
                .capabilities(org.a2aproject.sdk.spec.AgentCapabilities.builder().build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of())
                .build();
        AgUiEvent<StepPayload> updateStep = a2aBridge.fromA2aClientEvent("thread-a2a", "verify", updateEvent, card);
        assertEquals("STEP_STARTED", updateStep.type());
        assertEquals("Nomad-Specialist", updateStep.payload().agentName());
        assertNotNull(a2aBridge.getClass().getClassLoader().getResourceAsStream("META-INF/beans.xml"));
    }
}
