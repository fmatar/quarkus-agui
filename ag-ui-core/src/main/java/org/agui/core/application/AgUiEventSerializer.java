package org.agui.core.application;

import org.agui.core.domain.AgUiEvent;

/**
 * Outbound Serialization Port (Level 1 Application Layer) for AG-UI Event Envelopes.
 */
@FunctionalInterface
public interface AgUiEventSerializer {

    /**
     * Serializes an immutable {@link AgUiEvent} into a compact JSON string.
     */
    String toJson(AgUiEvent<?> event);
}
