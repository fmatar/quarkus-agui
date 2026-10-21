package org.agui.core.adapters;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import org.agui.core.application.AgUiEventSerializer;
import org.agui.core.domain.AgUiEvent;

/**
 * Zero-dependency {@link Flow.Publisher} reactive stream adapter for AG-UI events (Level 2 Adapter).
 */
public final class AgUiFlowAdapter {

    private AgUiFlowAdapter() {
    }

    public static <T> Flow.Publisher<T> fromIterable(Iterable<T> items) {
        Objects.requireNonNull(items, "items must not be null");
        return subscriber -> {
            Objects.requireNonNull(subscriber, "subscriber must not be null");
            AtomicBoolean cancelled = new AtomicBoolean(false);
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                    if (cancelled.get()) {
                        return;
                    }
                    if (n <= 0) {
                        cancelled.set(true);
                        subscriber.onError(new IllegalArgumentException("request n must be > 0"));
                        return;
                    }
                    for (T item : items) {
                        if (!cancelled.get()) {
                            subscriber.onNext(item);
                        }
                    }
                    if (!cancelled.getAndSet(true)) {
                        subscriber.onComplete();
                    }
                }

                @Override
                public void cancel() {
                    cancelled.set(true);
                }
            });
        };
    }

    public static Flow.Publisher<String> toSsePublisher(
            Iterable<AgUiEvent<?>> events,
            AgUiEventSerializer serializer) {
        Objects.requireNonNull(events, "events must not be null");
        Objects.requireNonNull(serializer, "serializer must not be null");
        List<String> frames = new ArrayList<>();
        for (AgUiEvent<?> event : events) {
            frames.add("data: " + serializer.toJson(event) + "\n\n");
        }
        return fromIterable(frames);
    }

    public static <T> List<T> collectAll(Flow.Publisher<T> publisher) {
        Objects.requireNonNull(publisher, "publisher must not be null");
        List<T> collected = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(T item) {
                collected.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
                throw new IllegalStateException("Publisher failed", throwable);
            }

            @Override
            public void onComplete() {
                completed.set(true);
            }
        });
        if (!completed.get()) {
            throw new IllegalStateException("Publisher did not complete");
        }
        return collected;
    }
}
