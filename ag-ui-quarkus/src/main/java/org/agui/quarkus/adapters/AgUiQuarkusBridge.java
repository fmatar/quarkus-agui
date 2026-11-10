package org.agui.quarkus.adapters;

import com.agui.community.core.agent.Agent;
import com.agui.community.core.agent.RunAgentInput;
import com.agui.community.core.event.Event;
import com.agui.community.core.serialization.Serializer;
import com.agui.community.server.SseEventEncoder;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Objects;
import java.util.concurrent.Flow;

/**
 * Quarkus Reactive Bridge adapting canonical AG-UI {@link Agent} and {@link Flow.Publisher}
 * streams into SmallRye Mutiny {@link Multi} event, SSE, and JSON payload streams.
 */
@ApplicationScoped
public class AgUiQuarkusBridge {

    private final Serializer serializer;
    private final SseEventEncoder sseEncoder;

    public AgUiQuarkusBridge() {
        this(new AgUiJacksonSerializer());
    }

    @Inject
    public AgUiQuarkusBridge(Serializer serializer) {
        this.serializer = Objects.requireNonNull(serializer, "serializer must not be null");
        this.sseEncoder = new SseEventEncoder(this.serializer);
    }

    public Multi<Event> run(Agent agent, RunAgentInput input) {
        Objects.requireNonNull(agent, "agent must not be null");
        Objects.requireNonNull(input, "input must not be null");
        return toMulti(agent.run(input));
    }

    public Multi<String> runAsSse(Agent agent, RunAgentInput input) {
        return toSseFrames(run(agent, input));
    }

    public Multi<String> runAsJsonPayloads(Agent agent, RunAgentInput input) {
        return toJsonPayloads(run(agent, input));
    }

    public Multi<String> toSseFrames(Multi<Event> events) {
        Objects.requireNonNull(events, "events must not be null");
        return events.map(sseEncoder::encode);
    }

    public Multi<String> toJsonPayloads(Multi<Event> events) {
        Objects.requireNonNull(events, "events must not be null");
        return events.map(serializer::serialize);
    }

    public <T> Multi<T> toMulti(Flow.Publisher<T> publisher) {
        Objects.requireNonNull(publisher, "publisher must not be null");
        return Multi.createFrom().publisher(publisher);
    }

    public Serializer serializer() {
        return serializer;
    }

    public SseEventEncoder sseEncoder() {
        return sseEncoder;
    }

    public static String unwrapSseData(String rawFrame) {
        if (rawFrame == null) {
            return null;
        }
        String trimmed = rawFrame.trim();
        if (trimmed.isEmpty() || trimmed.startsWith(":")) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean foundData = false;
        for (String line : trimmed.split("\n")) {
            String stripped = line.trim();
            if (stripped.startsWith("data:")) {
                if (foundData) {
                    sb.append('\n');
                }
                sb.append(stripped.substring(5).trim());
                foundData = true;
            }
        }
        return foundData ? sb.toString() : trimmed;
    }
}
