package org.agui.core.domain;

import java.util.Objects;

/**
 * Streamed chunk of LLM output text tokens.
 */
public record TextDeltaPayload(
        String content
) {
    public TextDeltaPayload {
        Objects.requireNonNull(content, "content must not be null");
    }
}
