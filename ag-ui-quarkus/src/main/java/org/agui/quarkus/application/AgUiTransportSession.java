package org.agui.quarkus.application;

import org.agui.quarkus.domain.AgUiTransportType;

/**
 * Port abstraction representing an active AG-UI client transport session.
 */
public interface AgUiTransportSession {

    String sessionId();

    AgUiTransportType transportType();

    boolean isOpen();

    void sendJson(String json);

    void close();
}
