package org.agui.core.adapters;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Flow;
import org.agui.core.domain.AgUiEvent;
import org.agui.core.domain.AgUiEventType;
import org.agui.core.domain.RunFinishedPayload;
import org.agui.core.domain.RunStartedPayload;
import org.agui.core.domain.TextDeltaPayload;

/**
 * Framework-agnostic AG-UI Bridge adapting token streams and AG-UI events into {@link Flow.Publisher} streams.
 */
@ApplicationScoped
public class AgUiBridge {

    private final AgUiSseFormatter formatter = new AgUiSseFormatter();

    public List<AgUiEvent<?>> buildTokenEvents(String threadId, Iterable<String> tokens) {
        Objects.requireNonNull(threadId, "threadId must not be null");
        Objects.requireNonNull(tokens, "tokens must not be null");
        List<AgUiEvent<?>> events = new ArrayList<>();
        events.add(AgUiEvent.of(AgUiEventType.RUN_STARTED, threadId, new RunStartedPayload(threadId, "started")));
        for (String token : tokens) {
            if (token != null && !token.isEmpty()) {
                events.add(AgUiEvent.of(AgUiEventType.TEXT_DELTA, threadId, new TextDeltaPayload(token)));
            }
        }
        events.add(AgUiEvent.of(AgUiEventType.RUN_FINISHED, threadId, new RunFinishedPayload(threadId, "completed")));
        return events;
    }

    public Flow.Publisher<AgUiEvent<?>> streamTokens(String threadId, Iterable<String> tokens) {
        return AgUiFlowAdapter.fromIterable(buildTokenEvents(threadId, tokens));
    }

    public Flow.Publisher<String> streamSseFrames(Iterable<AgUiEvent<?>> events) {
        return AgUiFlowAdapter.toSsePublisher(events, formatter);
    }

    public String toSseServerSentEventData(AgUiEvent<?> event) {
        Objects.requireNonNull(event, "event must not be null");
        return formatter.toJson(event);
    }

    public AgUiSseFormatter formatter() {
        return formatter;
    }
}
