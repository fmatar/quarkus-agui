package org.agui.quarkus.langchain4j.adapters;

import com.agui.community.core.agent.Agent;
import com.agui.community.core.agent.RunAgentInput;
import com.agui.community.core.event.Event;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatEvent;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.agui.langchain4j.adapters.AgUiLangChain4jBridge;
import org.agui.langchain4j.adapters.AgUiLangChain4jRunSession;
import org.agui.langchain4j.adapters.AgUiRunOptions;
import org.agui.quarkus.adapters.AgUiQuarkusBridge;

/**
 * Reactive Quarkiverse LangChain4j adapter converting {@link Multi}{@code <ChatEvent>} streams
 * into canonical {@link Multi}{@code <Event>}, SSE, and JSON streams by delegating state-machine
 * synthesis to {@link AgUiLangChain4jRunSession}.
 */
@ApplicationScoped
public class AgUiQuarkusLangChain4jBridge {

    private final AgUiLangChain4jBridge langChain4jBridge;
    private final AgUiQuarkusBridge quarkusBridge;

    public AgUiQuarkusLangChain4jBridge() {
        this(new AgUiLangChain4jBridge(), new AgUiQuarkusBridge());
    }

    @Inject
    public AgUiQuarkusLangChain4jBridge(
            AgUiLangChain4jBridge langChain4jBridge,
            AgUiQuarkusBridge quarkusBridge) {
        this.langChain4jBridge = Objects.requireNonNull(langChain4jBridge, "langChain4jBridge must not be null");
        this.quarkusBridge = Objects.requireNonNull(quarkusBridge, "quarkusBridge must not be null");
    }

    public Multi<Event> adapt(String threadId, Multi<ChatEvent> chatEvents) {
        return adapt(threadId, chatEvents, AgUiRunOptions.defaults());
    }

    public Multi<Event> adapt(String threadId, Multi<ChatEvent> chatEvents, AgUiRunOptions options) {
        AgUiLangChain4jBridge.validateInputs(threadId, chatEvents);
        AgUiRunOptions resolved = options == null ? AgUiRunOptions.defaults() : options;

        return Multi.createFrom().deferred(() -> {
            AgUiLangChain4jRunSession session = langChain4jBridge.newSession(threadId, resolved);
            Multi<Event> startEvents = Multi.createFrom().iterable(session.onStart());
            Multi<Event> streamEvents = chatEvents
                    .onItem().transformToIterable(event -> mapChatEvent(session, event))
                    .onFailure().recoverWithMulti(failure -> Multi.createFrom().iterable(session.onError(failure)));
            Multi<Event> trailingCompletion = Multi.createFrom()
                    .deferred(() -> Multi.createFrom().iterable(session.onComplete()));
            return Multi.createBy().concatenating().streams(startEvents, streamEvents, trailingCompletion);
        });
    }

    public Multi<String> adaptToSse(String threadId, Multi<ChatEvent> chatEvents, AgUiRunOptions options) {
        return quarkusBridge.toSseFrames(adapt(threadId, chatEvents, options));
    }

    public Multi<String> adaptToJsonPayloads(String threadId, Multi<ChatEvent> chatEvents, AgUiRunOptions options) {
        return quarkusBridge.toJsonPayloads(adapt(threadId, chatEvents, options));
    }

    public Agent asAgent(
            BiFunction<RunAgentInput, AgUiRunOptions, Multi<ChatEvent>> chatFactory,
            Function<RunAgentInput, AgUiRunOptions> optionsFactory) {
        Objects.requireNonNull(chatFactory, "chatFactory must not be null");
        return input -> {
            Objects.requireNonNull(input, "input must not be null");
            AgUiRunOptions base = optionsFactory != null ? optionsFactory.apply(input) : AgUiRunOptions.defaults();
            AgUiRunOptions resolved = (base == null ? AgUiRunOptions.defaults() : base).withRunId(input.runId());
            return adapt(input.threadId(), chatFactory.apply(input, resolved), resolved);
        };
    }

    public AgUiLangChain4jBridge langChain4jBridge() {
        return langChain4jBridge;
    }

    public AgUiQuarkusBridge quarkusBridge() {
        return quarkusBridge;
    }

    private static List<Event> mapChatEvent(AgUiLangChain4jRunSession session, ChatEvent event) {
        if (event instanceof ChatEvent.PartialThinkingEvent thinking) {
            return session.onPartialThinking(thinking.getText());
        }
        if (event instanceof ChatEvent.ContentFetchedEvent rag) {
            return session.onRetrieved(rag.getContent());
        }
        if (event instanceof ChatEvent.BeforeToolExecutionEvent beforeTool) {
            return session.onBeforeTool(beforeTool.getRequest());
        }
        if (event instanceof ChatEvent.ToolExecutedEvent afterTool) {
            return session.onToolExecuted(afterTool.getExecution());
        }
        if (event instanceof ChatEvent.PartialResponseEvent partial) {
            return session.onPartialResponse(partial.getChunk());
        }
        if (event instanceof ChatEvent.AccumulatedResponseEvent accumulated) {
            return session.onAccumulatedResponse(accumulated.getMessage());
        }
        if (event instanceof ChatEvent.ChatCompletedEvent) {
            return session.onComplete();
        }
        return List.of();
    }
}
