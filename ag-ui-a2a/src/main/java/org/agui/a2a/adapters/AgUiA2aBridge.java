package org.agui.a2a.adapters;

import com.agui.community.core.event.ActivitySnapshotEvent;
import com.agui.community.core.event.Event;
import com.agui.community.core.event.StepFinishedEvent;
import com.agui.community.core.event.StepStartedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.MessageEvent;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.client.TaskUpdateEvent;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TextPart;
import org.agui.a2a.domain.A2aHandoff;

/**
 * Framework-agnostic Java 17 bridge wrapping official A2A Java SDK specialist delegations,
 * tasks, artifacts, messages, and client events into canonical AG-UI {@link Event} records.
 */
@ApplicationScoped
public class AgUiA2aBridge {

    public List<Event> recordDelegation(A2aHandoff handoff) {
        Objects.requireNonNull(handoff, "handoff must not be null");
        return buildDelegationEvents(handoff, false, List.of());
    }

    public List<Event> fromA2aTask(String agentName, String skillId, Task task) {
        Objects.requireNonNull(agentName, "agentName must not be null");
        Objects.requireNonNull(skillId, "skillId must not be null");
        Objects.requireNonNull(task, "task must not be null");

        String summary = extractTaskSummary(task);
        boolean isError = isTerminalFailure(task.status().state());
        A2aHandoff handoff = new A2aHandoff(
                task.id(),
                agentName,
                skillId,
                summary,
                task.metadata() != null ? task.metadata() : Map.of());
        List<Artifact> artifacts = task.artifacts() == null ? List.of() : task.artifacts();
        return buildDelegationEvents(handoff, isError, artifacts);
    }

    public Event fromA2aClientEvent(String skillId, ClientEvent event, AgentCard agentCard) {
        Objects.requireNonNull(skillId, "skillId must not be null");
        Objects.requireNonNull(event, "event must not be null");

        String agentName = agentCard != null ? agentCard.name() : "A2A-Agent";
        String stepName = formatStepName(agentName, skillId);
        long now = System.currentTimeMillis();

        if (event instanceof MessageEvent messageEvent) {
            Message msg = messageEvent.getMessage();
            String stepId = msg.taskId() != null ? msg.taskId() : msg.messageId();
            return new StepFinishedEvent(
                    stepName,
                    now,
                    buildProvenance(stepId, agentName, skillId, extractMessageText(msg), false, Map.of()));
        }
        if (event instanceof TaskEvent taskEvent) {
            Task task = taskEvent.getTask();
            boolean isError = isTerminalFailure(task.status().state());
            Map<String, Object> meta = task.metadata() != null ? task.metadata() : Map.of();
            return new StepFinishedEvent(
                    stepName,
                    now,
                    buildProvenance(task.id(), agentName, skillId, extractTaskSummary(task), isError, meta));
        }
        TaskUpdateEvent updateEvent = (TaskUpdateEvent) event;
        Task task = updateEvent.getTask();
        return new StepStartedEvent(
                stepName,
                now,
                buildProvenance(task.id(), agentName, skillId, extractTaskSummary(task), false, Map.of()));
    }

    public String extractTaskSummary(Task task) {
        Objects.requireNonNull(task, "task must not be null");
        if (task.artifacts() != null && !task.artifacts().isEmpty()) {
            return extractArtifactsText(task.artifacts());
        }
        if (task.status().message() != null) {
            return extractMessageText(task.status().message());
        }
        return task.status().state().name();
    }

    public String extractMessageText(Message message) {
        if (message == null) {
            return "";
        }
        return extractPartsText(message.parts());
    }

    private static List<Event> buildDelegationEvents(
            A2aHandoff handoff,
            boolean isError,
            List<Artifact> artifacts) {
        String stepName = formatStepName(handoff.agentName(), handoff.skillId());
        long now = System.currentTimeMillis();
        List<Event> events = new ArrayList<>(3);
        events.add(new StepStartedEvent(
                stepName,
                now,
                buildProvenance(handoff.taskId(), handoff.agentName(), handoff.skillId(), "Delegated", false, handoff.metadata())));

        if (!artifacts.isEmpty()) {
            Map<String, Object> content = Map.of(
                    "taskId", handoff.taskId(),
                    "agentName", handoff.agentName(),
                    "skillId", handoff.skillId(),
                    "summary", handoff.summary(),
                    "artifactCount", artifacts.size());
            events.add(new ActivitySnapshotEvent(
                    handoff.taskId(),
                    "a2a:artifact",
                    content,
                    Boolean.TRUE,
                    now,
                    buildProvenance(handoff.taskId(), handoff.agentName(), handoff.skillId(), handoff.summary(), isError, handoff.metadata())));
        }

        events.add(new StepFinishedEvent(
                stepName,
                now,
                buildProvenance(handoff.taskId(), handoff.agentName(), handoff.skillId(), handoff.summary(), isError, handoff.metadata())));
        return List.copyOf(events);
    }

    private static Map<String, Object> buildProvenance(
            String stepId,
            String agentName,
            String skillId,
            String summary,
            boolean isError,
            Map<String, Object> metadata) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("stepId", stepId);
        raw.put("protocol", "A2A");
        raw.put("callType", "a2a:task");
        raw.put("agentName", agentName);
        raw.put("skillId", skillId);
        raw.put("status", isError ? "ERROR" : "OK");
        raw.put("isError", isError);
        raw.put("summary", summary);
        raw.put("metadata", metadata);
        return Map.copyOf(raw);
    }

    private static String formatStepName(String agentName, String skillId) {
        return "a2a:" + agentName + ":" + skillId;
    }

    private static boolean isTerminalFailure(TaskState state) {
        return state == TaskState.TASK_STATE_FAILED || state == TaskState.TASK_STATE_REJECTED;
    }

    private static String extractArtifactsText(List<Artifact> artifacts) {
        StringBuilder sb = new StringBuilder();
        for (Artifact artifact : artifacts) {
            sb.append(extractPartsText(artifact.parts()));
        }
        return sb.toString();
    }

    private static String extractPartsText(List<Part<?>> parts) {
        StringBuilder sb = new StringBuilder();
        for (Part<?> part : parts) {
            if (part instanceof TextPart textPart) {
                sb.append(textPart.text());
            }
        }
        return sb.toString();
    }
}
