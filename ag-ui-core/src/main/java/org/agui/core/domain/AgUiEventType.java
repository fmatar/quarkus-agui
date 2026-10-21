package org.agui.core.domain;

/**
 * Standard AG-UI Protocol Event Types (Core 6 + Step & State Extensions).
 */
public enum AgUiEventType {
    RUN_STARTED,
    STATUS,
    TEXT_DELTA,
    TOOL_CALL,
    TOOL_RESULT,
    RUN_FINISHED,
    STEP_STARTED,
    STEP_FINISHED,
    STATE_SNAPSHOT,
    STATE_DELTA
}
