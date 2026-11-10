package org.agui.core.adapters;

import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import org.agui.core.application.AgUiTransportSession;
import org.agui.core.domain.AgUiEvent;
import org.agui.core.domain.AgUiTransportType;

/**
 * Framework-agnostic SSE Transport Session adapter backed by a JDK {@link SubmissionPublisher}.
 */
public class AgUiSseTransportSession implements AgUiTransportSession {

    private final String sessionId;
    private final SubmissionPublisher<String> publisher;
    private final AgUiSseFormatter formatter;
    private volatile boolean open;

    public AgUiSseTransportSession(String sessionId) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
        this.publisher = new SubmissionPublisher<>(Runnable::run, Flow.defaultBufferSize());
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

    public Flow.Publisher<String> toPublisher() {
        return publisher;
    }
}
