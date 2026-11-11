package org.agui.quarkus.adapters;

import com.agui.community.core.agent.Agent;
import com.agui.community.core.agent.RunAgentInput;
import com.agui.community.core.event.Event;
import com.agui.community.core.event.RunErrorEvent;
import com.agui.community.core.serialization.Serializer;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.Cancellable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.agui.quarkus.application.AgUiSessionRegistry;

/**
 * Reusable Quarkus WebSockets Next lifecycle and {@link RunAgentInput} execution helper.
 * Applications mount their own {@code @WebSocket(path = "...")} endpoint and delegate to this bean.
 */
@ApplicationScoped
public class AgUiWebSocketHandler {

    private static final Duration DEFAULT_STREAM_IDLE_TIMEOUT = Duration.ofSeconds(60);

    private final AgUiSessionRegistry registry;
    private final AgUiQuarkusBridge bridge;
    private final Serializer serializer;
    private final List<ActiveRun> activeRuns = new CopyOnWriteArrayList<>();
    private volatile Duration streamIdleTimeout = DEFAULT_STREAM_IDLE_TIMEOUT;

    private record ActiveRun(
            String connectionId,
            String threadId,
            AtomicBoolean cancelledByClient,
            CompletableFuture<Void> completion,
            AtomicReference<Cancellable> cancellableRef) {

        void cancel() {
            cancelledByClient.set(true);
            Cancellable cancellable = cancellableRef.getAndSet(null);
            if (cancellable != null) {
                cancellable.cancel();
            }
            completion.complete(null);
        }
    }

    public AgUiWebSocketHandler() {
        AgUiJacksonSerializer defaultSerializer = new AgUiJacksonSerializer();
        this.registry = new AgUiSessionRegistry();
        this.bridge = new AgUiQuarkusBridge(defaultSerializer);
        this.serializer = defaultSerializer;
    }

    @Inject
    public AgUiWebSocketHandler(
            AgUiSessionRegistry registry,
            AgUiQuarkusBridge bridge,
            Serializer serializer) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.bridge = Objects.requireNonNull(bridge, "bridge must not be null");
        this.serializer = Objects.requireNonNull(serializer, "serializer must not be null");
    }

    public AgUiQuarkusWebSocketTransportSession onOpen(WebSocketConnection connection) {
        Objects.requireNonNull(connection, "connection must not be null");
        AgUiQuarkusWebSocketTransportSession session =
                new AgUiQuarkusWebSocketTransportSession(connection.id(), connection);
        registry.register(session);
        return session;
    }

    public void onClose(WebSocketConnection connection) {
        if (connection != null) {
            cancelRun(connection, null);
            registry.unregister(connection.id());
        }
    }

    public void setStreamIdleTimeout(Duration timeout) {
        this.streamIdleTimeout = (timeout == null || timeout.isNegative() || timeout.isZero())
                ? DEFAULT_STREAM_IDLE_TIMEOUT
                : timeout;
    }

    public Duration streamIdleTimeout() {
        return streamIdleTimeout;
    }

    public boolean cancelRun(WebSocketConnection connection, String threadId) {
        if (connection == null) {
            return false;
        }
        String connId = connection.id();
        boolean cancelledAny = false;
        for (ActiveRun active : activeRuns) {
            boolean sameConn = Objects.equals(connId, active.connectionId());
            boolean sameThread = threadId == null || threadId.isBlank() || Objects.equals(threadId, active.threadId());
            if (sameConn && sameThread) {
                activeRuns.remove(active);
                active.cancel();
                cancelledAny = true;
            }
        }
        return cancelledAny;
    }

    public void onError(WebSocketConnection connection, Throwable error) {
        onClose(connection);
    }

    public boolean handleControlFrame(String rawMessage, WebSocketConnection connection) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return true;
        }
        String trimmed = rawMessage.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if ("ping".equals(lower) || lower.contains("\"type\":\"ping\"") || lower.contains("\"type\": \"ping\"")) {
            sendRawJson(connection, "{\"type\":\"pong\"}");
            return true;
        }
        return false;
    }

    public RunAgentInput parseInput(String rawMessage) {
        return serializer.deserialize(rawMessage, RunAgentInput.class);
    }

    public void sendEvent(WebSocketConnection connection, Event event) {
        if (event != null) {
            sendRawJson(connection, serializer.serialize(event));
        }
    }

    public void runAndSend(Agent agent, String rawMessage, WebSocketConnection connection) {
        Objects.requireNonNull(agent, "agent must not be null");
        if (handleControlFrame(rawMessage, connection)) {
            return;
        }
        try {
            RunAgentInput input = parseInput(rawMessage);
            Duration timeout = this.streamIdleTimeout;
            Multi<String> timedPayloads = bridge.runAsJsonPayloads(agent, input)
                    .ifNoItem()
                    .after(timeout)
                    .failWith(() -> new IllegalStateException(
                            "WebSocket agent stream timed out after " + timeout.toMillis() + "ms"));

            String connId = connection != null ? connection.id() : "";
            CompletableFuture<Void> done = new CompletableFuture<>();
            AtomicBoolean cancelledByClient = new AtomicBoolean(false);
            AtomicReference<Cancellable> cancellableRef = new AtomicReference<>();
            ActiveRun activeRun = new ActiveRun(connId, input.threadId(), cancelledByClient, done, cancellableRef);
            activeRuns.add(activeRun);
            try {
                Cancellable cancellable = timedPayloads.subscribe().with(
                        json -> {
                            if (!cancelledByClient.get()) {
                                sendRawJson(connection, json);
                            }
                        },
                        done::completeExceptionally,
                        () -> done.complete(null));
                cancellableRef.set(cancellable);
                if (cancelledByClient.get()) {
                    cancellable.cancel();
                }
                done.join();
            } catch (CompletionException ce) {
                if (!cancelledByClient.get()) {
                    Throwable cause = ce.getCause() != null ? ce.getCause() : ce;
                    if (cause instanceof Error err) {
                        throw err;
                    }
                    throw (cause instanceof RuntimeException re) ? re : new RuntimeException(cause);
                }
            } finally {
                activeRuns.remove(activeRun);
            }
        } catch (Exception failure) {
            String msg = (failure.getMessage() == null || failure.getMessage().isBlank())
                    ? failure.getClass().getSimpleName()
                    : failure.getMessage();
            sendEvent(connection, new RunErrorEvent(msg, "WS_EXECUTION_ERROR", System.currentTimeMillis(), null));
        }
    }

    private static void sendRawJson(WebSocketConnection connection, String json) {
        if (connection == null || connection.isClosed()) {
            return;
        }
        synchronized (connection) {
            if (!connection.isClosed()) {
                connection.sendTextAndAwait(json);
            }
        }
    }
}
