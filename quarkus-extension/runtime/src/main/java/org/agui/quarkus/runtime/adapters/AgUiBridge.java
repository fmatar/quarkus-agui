package org.agui.quarkus.runtime.adapters;

import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.agui.core.adapters.AgUiSseFormatter;
import org.agui.core.domain.AgUiEvent;
import org.agui.core.domain.AgUiEventType;
import org.agui.core.domain.RunFinishedPayload;
import org.agui.core.domain.RunStartedPayload;
import org.agui.core.domain.TextDeltaPayload;

/**
 * Quarkus Runtime Bridge adapting token streams and AG-UI events into SmallRye Mutiny {@link Multi} streams.
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

    public Multi<AgUiEvent<?>> streamTokens(String threadId, Iterable<String> tokens) {
        return Multi.createFrom().iterable(buildTokenEvents(threadId, tokens));
    }

    public Multi<String> streamSseFrames(Iterable<AgUiEvent<?>> events) {
        Objects.requireNonNull(events, "events must not be null");
        List<String> frames = new ArrayList<>();
        for (AgUiEvent<?> event : events) {
            frames.add(formatter.formatSse(event));
        }
        return Multi.createFrom().iterable(frames);
    }

    public String toSseServerSentEventData(AgUiEvent<?> event) {
        Objects.requireNonNull(event, "event must not be null");
        return formatter.toJson(event);
    }

    public AgUiSseFormatter formatter() {
        return formatter;
    }
}
