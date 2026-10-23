package org.agui.quarkus.sse.runtime.adapters;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import org.agui.core.adapters.AgUiSseFormatter;
import org.agui.core.domain.AgUiEvent;
import org.agui.quarkus.core.runtime.application.port.AgUiTransportSession;
import org.agui.quarkus.core.runtime.domain.AgUiTransportType;

/**
 * SSE Transport Session adapter that implements {@link AgUiTransportSession} backed by a reactive stream.
 */
public class AgUiSseTransportSession implements AgUiTransportSession {

    private final String sessionId;
    private final SubmissionPublisher<String> publisher;
    private final AgUiSseFormatter formatter;
    private volatile boolean open;

    public AgUiSseTransportSession(String sessionId) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
        this.publisher = new SubmissionPublisher<>();
        this.formatter = new AgUiSseFormatter();
        this.open = true;
    }

    @Override
    public String sessionId() {
        return sessionId;
    }

    @Override
    public AgUiTransportType transportType() {
        return AgUiTransportType.SSE;
    }

    @Override
    public boolean isOpen() {
        return open && !publisher.isClosed();
    }

    @Override
    public void emit(AgUiEvent<?> event) {
        if (!isOpen() || event == null) {
            return;
        }
        String sseFrame = formatter.formatSse(event);
        publisher.submit(sseFrame);
    }

    @Override
    public void close() {
        open = false;
        publisher.close();
    }

    public Multi<String> toMulti() {
        return Multi.createFrom().publisher(publisher);
    }
}
