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
        AtomicBoolean streamConsumed = new AtomicBoolean(false);

        return subscriber -> {
            Objects.requireNonNull(subscriber, "subscriber must not be null");
            Object signalLock = new Object();
            AtomicBoolean started = new AtomicBoolean(false);
            AtomicBoolean terminated = new AtomicBoolean(false);

            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                    if (terminated.get()) {
                        return;
                    }
                    if (n <= 0) {
                        signalErrorOnce(
                                subscriber,
                                signalLock,
                                terminated,
                                new IllegalArgumentException("Reactive Streams §3.9: request n must be > 0, got " + n));
                        return;
                    }
                    if (!started.compareAndSet(false, true)) {
                        return;
                    }
                    if (!streamConsumed.compareAndSet(false, true)) {
                        signalErrorOnce(
                                subscriber,
                                signalLock,
                                terminated,
                                new IllegalStateException("TokenStream can only be subscribed to once"));
                        return;
                    }
                    startRunSession(threadId, tokenStream, resolved, subscriber, signalLock, terminated);
                }

                @Override
                public void cancel() {
                    terminated.set(true);
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
            Object signalLock,
            AtomicBoolean terminated) {
        AgUiLangChain4jRunSession session = newSession(threadId, options);
        emitAll(subscriber, signalLock, terminated, session.onStart());

        tokenStream.onPartialThinking(thinking -> {
            if (thinking != null) {
                emitAll(subscriber, signalLock, terminated, session.onPartialThinking(thinking.text()));
            }
        });
        tokenStream.onRetrieved(contents -> emitAll(subscriber, signalLock, terminated, session.onRetrieved(contents)));
        tokenStream.beforeToolExecution(before -> {
            if (before != null && before.request() != null) {
                emitAll(subscriber, signalLock, terminated, session.onBeforeTool(before.request()));
            }
        });
        tokenStream.onToolExecuted(exec -> {
            if (exec != null) {
                emitAll(subscriber, signalLock, terminated, session.onToolExecuted(exec));
            }
        });
        tokenStream.onPartialResponse(chunk -> emitAll(subscriber, signalLock, terminated, session.onPartialResponse(chunk)));
        tokenStream.onCompleteResponse(resp -> finishWithEvents(subscriber, signalLock, terminated, session.onComplete()));
        tokenStream.onError(failure -> finishWithEvents(subscriber, signalLock, terminated, session.onError(failure)));

        try {
            tokenStream.start();
        } catch (Exception failure) {
            finishWithEvents(subscriber, signalLock, terminated, session.onError(failure));
        }
    }

    private static void emitAll(
            Flow.Subscriber<? super Event> subscriber,
            Object signalLock,
            AtomicBoolean terminated,
            List<Event> events) {
        synchronized (signalLock) {
            for (Event event : events) {
                if (!terminated.get()) {
                    subscriber.onNext(event);
                }
            }
        }
    }

    private static void finishWithEvents(
            Flow.Subscriber<? super Event> subscriber,
            Object signalLock,
            AtomicBoolean terminated,
            List<Event> events) {
        synchronized (signalLock) {
            emitAll(subscriber, signalLock, terminated, events);
            if (!terminated.getAndSet(true)) {
                subscriber.onComplete();
            }
        }
    }

    private static void signalErrorOnce(
            Flow.Subscriber<? super Event> subscriber,
            Object signalLock,
            AtomicBoolean terminated,
            Throwable error) {
        synchronized (signalLock) {
            if (!terminated.getAndSet(true)) {
                subscriber.onError(error);
            }
        }
    }
}
