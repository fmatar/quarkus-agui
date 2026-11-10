package org.agui.quarkus.adapters;

import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Objects;
import java.util.concurrent.Flow;
import org.agui.core.adapters.AgUiBridge;
import org.agui.core.adapters.AgUiSseFormatter;
import org.agui.core.adapters.AgUiSseTransportSession;
import org.agui.core.domain.AgUiEvent;

/**
 * Quarkus Reactive Bridge adapting {@link AgUiBridge} and {@link Flow.Publisher} streams into SmallRye Mutiny {@link Multi}.
 */
@ApplicationScoped
public class AgUiQuarkusBridge {

    private final AgUiBridge coreBridge;

    public AgUiQuarkusBridge() {
        this(new AgUiBridge());
    }

    @Inject
    public AgUiQuarkusBridge(AgUiBridge coreBridge) {
        this.coreBridge = Objects.requireNonNull(coreBridge, "coreBridge must not be null");
    }

    public Multi<AgUiEvent<?>> streamTokens(String threadId, Iterable<String> tokens) {
        return Multi.createFrom().iterable(coreBridge.buildTokenEvents(threadId, tokens));
    }

    public Multi<String> streamSseFrames(Iterable<AgUiEvent<?>> events) {
        return toMulti(coreBridge.streamSseFrames(events));
    }

    public Multi<String> toMulti(AgUiSseTransportSession session) {
        Objects.requireNonNull(session, "session must not be null");
        return toMulti(session.toPublisher());
    }

    public <T> Multi<T> toMulti(Flow.Publisher<T> publisher) {
        Objects.requireNonNull(publisher, "publisher must not be null");
        return Multi.createFrom().publisher(publisher);
    }

    public AgUiSseFormatter formatter() {
        return coreBridge.formatter();
    }
}
