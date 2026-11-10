package org.agui.quarkus.adapters;

import io.quarkiverse.langchain4j.runtime.aiservice.ChatEvent;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Objects;
import org.agui.core.adapters.AgUiSseFormatter;
import org.agui.core.domain.AgUiEvent;
import org.agui.langchain4j.adapters.AgUiLangChain4jBridge;
import org.agui.langchain4j.adapters.AgUiRunOptions;

/**
 * Reactive Quarkus LangChain4j adapter converting {@link Multi}{@code <ChatEvent>} streams
 * into {@link Multi}{@code <AgUiEvent<?>>} by delegating event synthesis to the framework-agnostic
 * {@link AgUiLangChain4jBridge}.
 */
@ApplicationScoped
public class AgUiQuarkusLangChain4jBridge {

    private final AgUiLangChain4jBridge coreBridge;

    public AgUiQuarkusLangChain4jBridge() {
        this(new AgUiLangChain4jBridge());
    }

    @Inject
    public AgUiQuarkusLangChain4jBridge(AgUiLangChain4jBridge coreBridge) {
        this.coreBridge = Objects.requireNonNull(coreBridge, "coreBridge must not be null");
    }

    public Multi<AgUiEvent<?>> adapt(String threadId, Multi<ChatEvent> chatEvents) {
        return adapt(threadId, chatEvents, AgUiRunOptions.defaults());
    }

    public Multi<AgUiEvent<?>> adapt(String threadId, Multi<ChatEvent> chatEvents, AgUiRunOptions options) {
        AgUiLangChain4jBridge.validateInputs(threadId, chatEvents);
        AgUiRunOptions resolved = options == null ? AgUiRunOptions.defaults() : options;

        Multi<AgUiEvent<?>> headerEvents = Multi.createFrom().iterable(coreBridge.buildInitialEvents(threadId, resolved));
        Multi<AgUiEvent<?>> bodyEvents = chatEvents
                .onItem().transformToIterable(event -> mapChatEvent(threadId, event, resolved))
                .onFailure().recoverWithMulti(failure -> Multi.createFrom().iterable(coreBridge.buildFailureEvents(threadId, failure)));

        return Multi.createBy().concatenating().streams(headerEvents, bodyEvents);
    }

    public Multi<String> adaptToSse(String threadId, Multi<ChatEvent> chatEvents, AgUiRunOptions options) {
        return adapt(threadId, chatEvents, options).map(coreBridge.formatter()::formatSse);
    }

    public AgUiSseFormatter formatter() {
        return coreBridge.formatter();
    }

    private List<AgUiEvent<?>> mapChatEvent(String threadId, ChatEvent event, AgUiRunOptions options) {
        if (event instanceof ChatEvent.PartialThinkingEvent thinking) {
            return coreBridge.mapThinkingText(threadId, thinking.getText());
        }
        if (event instanceof ChatEvent.ContentFetchedEvent rag) {
            return coreBridge.mapRagContents(threadId, rag.getContent());
        }
        if (event instanceof ChatEvent.BeforeToolExecutionEvent beforeTool) {
            return coreBridge.mapBeforeToolRequest(threadId, beforeTool.getRequest(), options);
        }
        if (event instanceof ChatEvent.ToolExecutedEvent afterTool) {
            return coreBridge.mapToolExecution(threadId, afterTool.getExecution(), options);
        }
        if (event instanceof ChatEvent.PartialResponseEvent partial) {
            return coreBridge.mapPartialResponseChunk(threadId, partial.getChunk());
        }
        if (event instanceof ChatEvent.AccumulatedResponseEvent accumulated) {
            return coreBridge.mapAccumulatedResponse(threadId, accumulated.getMessage(), options);
        }
        if (event instanceof ChatEvent.ChatCompletedEvent) {
            return coreBridge.mapCompleted(threadId, options);
        }
        return List.of();
    }
}
