package org.agui.quarkus.runtime.adapters;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Objects;
import org.agui.core.domain.A2aHandoffPayload;
import org.agui.core.domain.AgUiEvent;
import org.agui.core.domain.AgUiEventType;
import org.agui.core.domain.StepPayload;

/**
 * Quarkus Runtime Bridge wrapping A2A (Agent-to-Agent) specialist delegations into AG-UI STEP_STARTED and STEP_FINISHED events.
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
}
