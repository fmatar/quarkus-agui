package org.agui.showcase.backend;

import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Application-scoped {@link InMemoryChatMemoryStore} backing {@link NomadConciergeAiService}'s
 * {@code @MemoryId} sliding-window memory across HTTP SSE and WebSocket turns.
 */
@ApplicationScoped
public class NomadChatMemoryStore extends InMemoryChatMemoryStore {
}
