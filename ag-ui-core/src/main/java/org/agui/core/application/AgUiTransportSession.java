package org.agui.core.application;

import org.agui.core.domain.AgUiEvent;
import org.agui.core.domain.AgUiTransportType;

/**
 * Port representing an active AG-UI transport connection (e.g., an SSE stream or WebSocket session).
 */
public interface AgUiTransportSession {

    String sessionId();

    AgUiTransportType transportType();

    boolean isOpen();

    void emit(AgUiEvent<?> event);

    void close();
}
