package org.agui.core.application;

import org.agui.core.domain.AgUiTransportType;

/**
 * Port interface implemented by transport adapters (SSE, WebSockets) to register their capabilities.
 */
public interface AgUiTransportBridge {

    AgUiTransportType transportType();

    boolean isEnabled();
}
