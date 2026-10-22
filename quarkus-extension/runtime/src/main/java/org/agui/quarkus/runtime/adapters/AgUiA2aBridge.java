package org.agui.quarkus.runtime.adapters;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
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
import org.a2aproject.sdk.spec.TextPart;
import org.agui.core.domain.A2aHandoffPayload;
import org.agui.core.domain.AgUiEvent;
import org.agui.core.domain.AgUiEventType;
import org.agui.core.domain.StepPayload;

/**
 * Quarkus Runtime Bridge wrapping official A2A Java SDK ({@code org.a2aproject.sdk}) specialist delegations,
 * {@link Task}s, {@link Message}s, and {@link ClientEvent}s into AG-UI {@code STEP_STARTED} and {@code STEP_FINISHED} events.
 */
@ApplicationScoped
public class AgUiA2aBridge {

    public List<AgUiEvent<?>> recordDelegation(String threadId, A2aHandoffPayload handoff) {
        Objects.requireNonNull(threadId, "threadId must not be null");
        Objects.requireNonNull(handoff, "handoff must not be null");

        AgUiEvent<StepPayload> started = AgUiEvent.of(
                AgUiEventType.STEP_STARTED,
                threadId,
                new StepPayload(handoff.taskId(), "A2A", handoff.agentName(), handoff.skillId(), "Delegated"));

        AgUiEvent<StepPayload> finished = AgUiEvent.of(
                AgUiEventType.STEP_FINISHED,
                threadId,
                new StepPayload(handoff.taskId(), "A2A", handoff.agentName(), handoff.skillId(), handoff.summary()));

        return List.of(started, finished);
    }

    public List<AgUiEvent<?>> fromA2aTask(String threadId, String agentName, String skillId, Task task) {
        Objects.requireNonNull(threadId, "threadId must not be null");
        Objects.requireNonNull(agentName, "agentName must not be null");
        Objects.requireNonNull(skillId, "skillId must not be null");
        Objects.requireNonNull(task, "task must not be null");

        String summary = extractTaskSummary(task);
        return recordDelegation(
                threadId,
                new A2aHandoffPayload(task.id(), agentName, skillId, summary, task.metadata() != null ? task.metadata() : java.util.Map.of()));
    }

    public AgUiEvent<StepPayload> fromA2aClientEvent(String threadId, String skillId, ClientEvent event, AgentCard agentCard) {
        Objects.requireNonNull(threadId, "threadId must not be null");
        Objects.requireNonNull(skillId, "skillId must not be null");
        Objects.requireNonNull(event, "event must not be null");

        String agentName = agentCard != null ? agentCard.name() : "A2A-Agent";
        if (event instanceof MessageEvent messageEvent) {
            Message msg = messageEvent.getMessage();
            String stepId = msg.taskId() != null ? msg.taskId() : msg.messageId();
            return AgUiEvent.of(
                    AgUiEventType.STEP_FINISHED,
                    threadId,
                    new StepPayload(stepId, "A2A", agentName, skillId, extractMessageText(msg)));
        }
        if (event instanceof TaskEvent taskEvent) {
            Task task = taskEvent.getTask();
            return AgUiEvent.of(
                    AgUiEventType.STEP_FINISHED,
                    threadId,
                    new StepPayload(task.id(), "A2A", agentName, skillId, extractTaskSummary(task)));
        }
        TaskUpdateEvent updateEvent = (TaskUpdateEvent) event;
        Task task = updateEvent.getTask();
        return AgUiEvent.of(
                AgUiEventType.STEP_STARTED,
                threadId,
                new StepPayload(task.id(), "A2A", agentName, skillId, extractTaskSummary(task)));
    }

    public String extractTaskSummary(Task task) {
        Objects.requireNonNull(task, "task must not be null");
        if (task.artifacts() != null && !task.artifacts().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Artifact artifact : task.artifacts()) {
                for (Part<?> part : artifact.parts()) {
                    if (part instanceof TextPart textPart) {
                        sb.append(textPart.text());
                    }
                }
            }
            return sb.toString();
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
        StringBuilder sb = new StringBuilder();
        for (Part<?> part : message.parts()) {
            if (part instanceof TextPart textPart) {
                sb.append(textPart.text());
            }
        }
        return sb.toString();
    }
}
