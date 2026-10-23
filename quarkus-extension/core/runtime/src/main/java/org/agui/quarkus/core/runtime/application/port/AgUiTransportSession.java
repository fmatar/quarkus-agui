package org.agui.quarkus.core.runtime.application.port;

import org.agui.core.domain.AgUiEvent;
import org.agui.quarkus.core.runtime.domain.AgUiTransportType;

/**
 * Port representing an active transport session (SSE connection, WebSocket session, etc.).
 */
public interface AgUiTransportSession {

    String sessionId();

    AgUiTransportType transportType();

    boolean isOpen();

    void emit(AgUiEvent<?> event);

    void close();
}
