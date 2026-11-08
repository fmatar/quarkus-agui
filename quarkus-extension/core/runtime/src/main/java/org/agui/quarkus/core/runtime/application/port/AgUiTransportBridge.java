package org.agui.quarkus.core.runtime.application.port;

import org.agui.quarkus.core.runtime.domain.AgUiTransportType;

/**
 * Port contract implemented by transport extensions (SSE, WebSockets) to register transport adapters.
 */
public interface AgUiTransportBridge {

    AgUiTransportType transportType();

    boolean isEnabled();
}
