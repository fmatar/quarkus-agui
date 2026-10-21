package org.agui.core.application;

import java.util.List;
import java.util.Objects;
import org.agui.core.domain.AgUiEvent;

/**
 * Application Layer Session Runner (Level 1) formatting AG-UI events through an {@link AgUiEventSerializer} port.
 */
public class AgUiSessionRunner {

    private final AgUiEventSerializer serializer;

    public AgUiSessionRunner(AgUiEventSerializer serializer) {
        this.serializer = Objects.requireNonNull(serializer, "serializer must not be null");
    }

    public String formatSseFrame(AgUiEvent<?> event) {
        Objects.requireNonNull(event, "event must not be null");
        return "data: " + serializer.toJson(event) + "\n\n";
    }

    public List<String> formatSseStream(List<AgUiEvent<?>> events) {
        Objects.requireNonNull(events, "events must not be null");
        return events.stream().map(this::formatSseFrame).toList();
    }
}
