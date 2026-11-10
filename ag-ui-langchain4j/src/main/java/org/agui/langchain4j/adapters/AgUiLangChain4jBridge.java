package org.agui.langchain4j.adapters;

import com.agui.community.core.agent.Agent;
import com.agui.community.core.agent.RunAgentInput;
import com.agui.community.core.event.Event;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.service.TokenStream;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Framework-agnostic Java 17 LangChain4j adapter converting upstream {@link TokenStream}
 * lifecycles into canonical AG-UI {@link Flow.Publisher}{@code <Event>} streams and {@link Agent} instances.
 */
@ApplicationScoped
public class AgUiLangChain4jBridge {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Flow.Publisher<Event> adapt(String threadId, TokenStream tokenStream) {
        return adapt(threadId, tokenStream, AgUiRunOptions.defaults());
    }

    public Flow.Publisher<Event> adapt(String threadId, TokenStream tokenStream, AgUiRunOptions options) {
        validateInputs(threadId, tokenStream);
        AgUiRunOptions resolved = options == null ? AgUiRunOptions.defaults() : options;

        return subscriber -> {
            Objects.requireNonNull(subscriber, "subscriber must not be null");
            AtomicBoolean started = new AtomicBoolean(false);
            AtomicBoolean cancelled = new AtomicBoolean(false);

            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                    if (cancelled.get() || n <= 0 || !started.compareAndSet(false, true)) {
                        return;
                    }
                    startRunSession(threadId, tokenStream, resolved, subscriber, cancelled);
                }

                @Override
                public void cancel() {
                    cancelled.set(true);
                }
            });
        };
    }

    public Agent asAgent(
            BiFunction<RunAgentInput, AgUiRunOptions, TokenStream> streamFactory,
            Function<RunAgentInput, AgUiRunOptions> optionsFactory) {
        Objects.requireNonNull(streamFactory, "streamFactory must not be null");
        return input -> {
            Objects.requireNonNull(input, "input must not be null");
            AgUiRunOptions base = optionsFactory != null ? optionsFactory.apply(input) : AgUiRunOptions.defaults();
            AgUiRunOptions resolved = (base == null ? AgUiRunOptions.defaults() : base).withRunId(input.runId());
            TokenStream stream = streamFactory.apply(input, resolved);
            return adapt(input.threadId(), stream, resolved);
        };
    }

    public AgUiLangChain4jRunSession newSession(String threadId, AgUiRunOptions options) {
        Objects.requireNonNull(threadId, "threadId must not be null");
        if (threadId.isBlank()) {
            throw new IllegalArgumentException("threadId must not be blank");
        }
        return new AgUiLangChain4jRunSession(threadId, options, objectMapper);
    }

    public static void validateInputs(String threadId, Object stream) {
        Objects.requireNonNull(threadId, "threadId must not be null");
        Objects.requireNonNull(stream, "stream must not be null");
        if (threadId.isBlank()) {
            throw new IllegalArgumentException("threadId must not be blank");
        }
    }

    private void startRunSession(
            String threadId,
            TokenStream tokenStream,
            AgUiRunOptions options,
            Flow.Subscriber<? super Event> subscriber,
            AtomicBoolean cancelled) {
        AgUiLangChain4jRunSession session = newSession(threadId, options);
        emitAll(subscriber, cancelled, session.onStart());

        tokenStream.onPartialThinking(thinking -> {
            if (thinking != null) {
                emitAll(subscriber, cancelled, session.onPartialThinking(thinking.text()));
            }
        });
        tokenStream.onRetrieved(contents -> emitAll(subscriber, cancelled, session.onRetrieved(contents)));
        tokenStream.beforeToolExecution(before -> {
            if (before != null && before.request() != null) {
                emitAll(subscriber, cancelled, session.onBeforeTool(before.request()));
            }
        });
        tokenStream.onToolExecuted(exec -> {
            if (exec != null) {
                emitAll(subscriber, cancelled, session.onToolExecuted(exec));
            }
        });
        tokenStream.onPartialResponse(chunk -> emitAll(subscriber, cancelled, session.onPartialResponse(chunk)));
        tokenStream.onCompleteResponse(resp -> {
            emitAll(subscriber, cancelled, session.onComplete());
            completeOnce(subscriber, cancelled);
        });
        tokenStream.onError(failure -> {
            emitAll(subscriber, cancelled, session.onError(failure));
            completeOnce(subscriber, cancelled);
        });

        try {
            tokenStream.start();
        } catch (Throwable failure) {
            emitAll(subscriber, cancelled, session.onError(failure));
            completeOnce(subscriber, cancelled);
        }
    }

    private static void emitAll(
            Flow.Subscriber<? super Event> subscriber,
            AtomicBoolean cancelled,
            List<Event> events) {
        for (Event event : events) {
            if (!cancelled.get()) {
                subscriber.onNext(event);
            }
        }
    }

    private static void completeOnce(Flow.Subscriber<? super Event> subscriber, AtomicBoolean cancelled) {
        if (!cancelled.getAndSet(true)) {
            subscriber.onComplete();
        }
    }
}
