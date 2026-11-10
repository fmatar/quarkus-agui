package org.agui.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agui.community.core.event.ActivitySnapshotEvent;
import com.agui.community.core.event.Event;
import com.agui.community.core.event.EventType;
import com.agui.community.core.event.StepFinishedEvent;
import com.agui.community.core.event.StepStartedEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.a2aproject.sdk.A2A;
import org.a2aproject.sdk.client.MessageEvent;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.client.TaskUpdateEvent;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.agui.a2a.adapters.AgUiA2aBridge;
import org.agui.a2a.domain.A2aHandoff;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AgUiA2aBridgeTest {

    @Test
    @DisplayName("A2aHandoff validates mandatory fields and copies metadata defensively")
    void handoffRecordValidatesAndDefensivelyCopies() {
        Map<String, Object> mutable = new HashMap<>();
        mutable.put("country", "Japan");
        A2aHandoff handoff = new A2aHandoff("task-1", "Nomad-A2A", "verify-advisory", "Verified", mutable);
        mutable.put("country", "Mutated");

        assertEquals("Japan", handoff.metadata().get("country"));
        assertThrows(UnsupportedOperationException.class, () -> handoff.metadata().put("x", 1));

        A2aHandoff nullMeta = new A2aHandoff("task-2", "Nomad-A2A", "verify-advisory", "Verified", null);
        assertTrue(nullMeta.metadata().isEmpty());

        assertThrows(NullPointerException.class, () -> new A2aHandoff(null, "a", "s", "sum", Map.of()));
        assertThrows(NullPointerException.class, () -> new A2aHandoff("t", null, "s", "sum", Map.of()));
        assertThrows(NullPointerException.class, () -> new A2aHandoff("t", "a", null, "sum", Map.of()));
        assertThrows(NullPointerException.class, () -> new A2aHandoff("t", "a", "s", null, Map.of()));
    }

    @Test
    @DisplayName("AgUiA2aBridge emits canonical StepStartedEvent, ActivitySnapshotEvent, and StepFinishedEvent with rawEvent provenance")
    @SuppressWarnings("unchecked")
    void agUiA2aBridgeCanonicalEventMapping() {
        AgUiA2aBridge bridge = new AgUiA2aBridge();

        assertThrows(NullPointerException.class, () -> bridge.recordDelegation(null));

        A2aHandoff handoff = new A2aHandoff(
                "task-202",
                "Nomad-A2A-Specialist",
                "verify-travel-advisory",
                "Destination advisory verified",
                Map.of("country", "Japan"));

        List<Event> delegationEvents = bridge.recordDelegation(handoff);
        assertEquals(2, delegationEvents.size());
        StepStartedEvent started = assertInstanceOf(StepStartedEvent.class, delegationEvents.get(0));
        StepFinishedEvent finished = assertInstanceOf(StepFinishedEvent.class, delegationEvents.get(1));

        assertEquals(EventType.STEP_STARTED, started.type());
        assertEquals("a2a:Nomad-A2A-Specialist:verify-travel-advisory", started.stepName());
        assertNotNull(started.timestamp());
        Map<String, Object> startMeta = (Map<String, Object>) started.rawEvent();
        assertEquals("A2A", startMeta.get("protocol"));
        assertEquals("a2a:task", startMeta.get("callType"));
        assertEquals("task-202", startMeta.get("stepId"));
        assertEquals("Delegated", startMeta.get("summary"));

        assertEquals(EventType.STEP_FINISHED, finished.type());
        Map<String, Object> finishMeta = (Map<String, Object>) finished.rawEvent();
        assertEquals("Destination advisory verified", finishMeta.get("summary"));
        assertEquals("OK", finishMeta.get("status"));
        assertFalse((Boolean) finishMeta.get("isError"));
        assertEquals(Map.of("country", "Japan"), finishMeta.get("metadata"));

        // Task with artifacts produces [StepStartedEvent, ActivitySnapshotEvent, StepFinishedEvent]
        Artifact artifact = Artifact.builder()
                .artifactId("art-1")
                .parts(List.of(new TextPart("Artifact summary text"), new DataPart(Map.of("score", 95))))
                .build();
        Task taskWithArtifact = Task.builder()
                .id("task-art-1")
                .contextId("ctx-1")
                .status(new TaskStatus(TaskState.TASK_STATE_COMPLETED))
                .artifacts(List.of(artifact))
                .metadata(Map.of("city", "Kyoto"))
                .build();

        List<Event> taskEvents = bridge.fromA2aTask("Nomad-A2A-Specialist", "verify-travel-advisory", taskWithArtifact);
        assertEquals(3, taskEvents.size());
        assertEquals(EventType.STEP_STARTED, taskEvents.get(0).type());
        ActivitySnapshotEvent activity = assertInstanceOf(ActivitySnapshotEvent.class, taskEvents.get(1));
        assertEquals(EventType.ACTIVITY_SNAPSHOT, activity.type());
        assertEquals("a2a:artifact", activity.activityType());
        assertEquals("task-art-1", activity.messageId());
        assertTrue(activity.replace());
        assertEquals(EventType.STEP_FINISHED, taskEvents.get(2).type());
        assertEquals("Artifact summary text", bridge.extractTaskSummary(taskWithArtifact));

        // Task without artifacts + message status
        Message agentMsg = A2A.toAgentMessage("Advisory verified");
        assertEquals("Advisory verified", bridge.extractMessageText(agentMsg));
        assertEquals("", bridge.extractMessageText(null));

        Task taskWorking = Task.builder()
                .id("task-work-1")
                .contextId("ctx-1")
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING, agentMsg, null))
                .build();
        List<Event> workingEvents = bridge.fromA2aTask("Nomad", "skill", taskWorking);
        assertEquals(2, workingEvents.size());
        assertEquals("Advisory verified", bridge.extractTaskSummary(taskWorking));

        // Failed task marks error in rawEvent
        Task failedTask = Task.builder()
                .id("task-fail-1")
                .contextId("ctx-1")
                .status(new TaskStatus(TaskState.TASK_STATE_FAILED))
                .build();
        assertEquals("TASK_STATE_FAILED", bridge.extractTaskSummary(failedTask));
        List<Event> failedEvents = bridge.fromA2aTask("Nomad", "skill", failedTask);
        Map<String, Object> failedMeta = (Map<String, Object>) failedEvents.get(1).rawEvent();
        assertTrue((Boolean) failedMeta.get("isError"));
        assertEquals("ERROR", failedMeta.get("status"));

        // Rejected task marks error in rawEvent
        Task rejectedTask = Task.builder()
                .id("task-rej-1")
                .contextId("ctx-1")
                .status(new TaskStatus(TaskState.TASK_STATE_REJECTED))
                .build();
        Map<String, Object> rejMeta = (Map<String, Object>) bridge.fromA2aTask("Nomad", "skill", rejectedTask).get(1).rawEvent();
        assertTrue((Boolean) rejMeta.get("isError"));

        // Client events: MessageEvent, TaskEvent, TaskUpdateEvent
        Event msgEvt = bridge.fromA2aClientEvent("verify", new MessageEvent(agentMsg), null);
        assertEquals(EventType.STEP_FINISHED, msgEvt.type());
        Map<String, Object> msgMeta = (Map<String, Object>) msgEvt.rawEvent();
        assertEquals("A2A-Agent", msgMeta.get("agentName"));

        Message msgWithTaskId = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .parts(List.of(new TextPart("Task-linked message"), new DataPart(Map.of("k", "v"))))
                .taskId("task-linked-1")
                .messageId("msg-1")
                .build();
        Event linkedEvt = bridge.fromA2aClientEvent("verify", new MessageEvent(msgWithTaskId), null);
        assertEquals("task-linked-1", ((Map<String, Object>) linkedEvt.rawEvent()).get("stepId"));

        Event taskClientEvt = bridge.fromA2aClientEvent("verify", new TaskEvent(taskWithArtifact), null);
        assertEquals(EventType.STEP_FINISHED, taskClientEvt.type());

        TaskUpdateEvent updateEvent = new TaskUpdateEvent(
                failedTask,
                new TaskStatusUpdateEvent("task-fail-1", new TaskStatus(TaskState.TASK_STATE_WORKING), "ctx-1", Map.of()));
        AgentCard card = AgentCard.builder()
                .name("Nomad-Specialist")
                .description("Travel specialist")
                .supportedInterfaces(List.of(new AgentInterface("JSONRPC", "http://localhost:8082")))
                .version("1.0.0")
                .capabilities(AgentCapabilities.builder().build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of())
                .build();
        Event updateStep = bridge.fromA2aClientEvent("verify", updateEvent, card);
        assertEquals(EventType.STEP_STARTED, updateStep.type());
        assertEquals("Nomad-Specialist", ((Map<String, Object>) updateStep.rawEvent()).get("agentName"));

        // Null checks
        assertThrows(NullPointerException.class, () -> bridge.fromA2aTask(null, "s", taskWorking));
        assertThrows(NullPointerException.class, () -> bridge.fromA2aTask("a", null, taskWorking));
        assertThrows(NullPointerException.class, () -> bridge.fromA2aTask("a", "s", null));
        assertThrows(NullPointerException.class, () -> bridge.fromA2aClientEvent(null, updateEvent, card));
        assertThrows(NullPointerException.class, () -> bridge.fromA2aClientEvent("s", null, card));
        assertThrows(NullPointerException.class, () -> bridge.extractTaskSummary(null));
        assertNotNull(bridge.getClass().getClassLoader().getResourceAsStream("META-INF/beans.xml"));
    }
}
