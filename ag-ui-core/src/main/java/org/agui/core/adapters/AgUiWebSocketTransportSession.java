package org.agui.core.adapters;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.agui.core.application.AgUiTransportSession;
import org.agui.core.domain.AgUiEvent;
import org.agui.core.domain.AgUiTransportType;

/**
 * Framework-agnostic WebSocket Transport Session backed by functional callbacks, allowing any
 * WebSocket implementation (Quarkus, Jakarta WebSocket, Spring, Netty, Vert.x) to plug into {@code AgUiSessionRegistry}.
 */
public class AgUiWebSocketTransportSession implements AgUiTransportSession {

    private final String sessionId;
    private final Consumer<String> textSender;
    private final BooleanSupplier openCheck;
    private final Runnable closeHandler;
    private final AgUiSseFormatter jsonSerializer;

    public AgUiWebSocketTransportSession(
            String sessionId,
            Consumer<String> textSender,
            BooleanSupplier openCheck,
            Runnable closeHandler) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
        this.textSender = textSender;
        this.openCheck = openCheck;
        this.closeHandler = closeHandler;
        this.jsonSerializer = new AgUiSseFormatter();
    }

    @Override
    public String sessionId() {
        return sessionId;
    }

    @Override
    public AgUiTransportType transportType() {
        return AgUiTransportType.WEBSOCKET;
    }

    @Override
    public boolean isOpen() {
        return textSender != null && openCheck != null && openCheck.getAsBoolean();
    }

    @Override
    public void emit(AgUiEvent<?> event) {
        if (!isOpen() || event == null) {
            return;
        }
        String json = jsonSerializer.toJson(event);
        textSender.accept(json);
    }

    @Override
    public void close() {
        if (isOpen() && closeHandler != null) {
            closeHandler.run();
        }
    }
}
