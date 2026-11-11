package org.agui.a2a.adapters;

import com.agui.community.core.event.Event;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.MessageEvent;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.client.TaskUpdateEvent;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.http.A2ACardResolver;
import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.client.http.JdkA2AHttpClient;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.spec.A2AClientError;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TextPart;
import org.agui.a2a.domain.A2aDelegationResult;
import org.agui.a2a.domain.A2aHandoff;

/**
 * Reusable Java 17 A2A 1.5.0.Final client wrapper bridging {@link A2ACardResolver},
 * {@link Client#builder(AgentCard)}, and {@link JSONRPCTransport} into canonical
 * {@link A2aDelegationResult} and AG-UI {@link Event} records.
 */
public final class AgUiA2aClient implements AutoCloseable {

    private static final List<String> DEFAULT_OUTPUT_MODES =
            List.of("text/plain", "application/json", "text");
    private static final AtomicInteger THREAD_SEQ = new AtomicInteger(1);

    private record Session(AgentCard card, Client client) {}

    private final String baseUrl;
    private final boolean streaming;
    private final Duration connectTimeout;
    private final Duration requestTimeout;
    private final A2AHttpClient httpClient;
    private final boolean ownsHttpClient;
    private final Map<String, String> authHeaders;
    private final AgUiA2aBridge bridge;
    private final ExecutorService ioExecutor;

    private final Object lock = new Object();
    private volatile Session cachedSession;

    private AgUiA2aClient(Builder builder) {
        this.baseUrl = normalizeBaseUrl(builder.baseUrl);
        this.streaming = builder.streaming;
        this.connectTimeout = builder.connectTimeout;
        this.requestTimeout = builder.requestTimeout;
        this.ownsHttpClient = builder.httpClient == null || builder.closeHttpClientOnShutdown;
        this.httpClient = builder.httpClient != null
                ? builder.httpClient
                : new JdkA2AHttpClient(HttpClient.newBuilder()
                        .connectTimeout(this.connectTimeout)
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build());
        this.authHeaders = Map.copyOf(builder.authHeaders);
        this.bridge = builder.bridge != null ? builder.bridge : new AgUiA2aBridge();
        this.ioExecutor = Executors.newCachedThreadPool(runnable -> {
            Thread t = new Thread(runnable, "agui-a2a-io-" + THREAD_SEQ.getAndIncrement());
            t.setDaemon(true);
            return t;
        });
    }

    public static Builder builder(String baseUrl) {
        return new Builder(baseUrl);
    }

    public Duration connectTimeout() {
        return connectTimeout;
    }

    public Duration requestTimeout() {
        return requestTimeout;
    }

    public record DelegationExecution(
            AgentCard agentCard,
            A2aDelegationResult result,
            List<Event> agUiEvents
    ) {
        public DelegationExecution {
            Objects.requireNonNull(result, "result must not be null");
            agUiEvents = agUiEvents == null ? List.of() : List.copyOf(agUiEvents);
        }
    }

    /**
     * Resolves (and caches) the remote {@link AgentCard} from {@code /.well-known/agent-card.json}
     * via the official {@link A2ACardResolver}, bounded by {@code requestTimeout}, and rebinds
     * container/proxy advertised URLs onto {@code baseUrl}.
     */
    public AgentCard resolveAgentCard() throws A2AClientError, A2AClientException {
        try {
            return resolveSessionBounded().card();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new A2AClientException("AgentCard resolution interrupted", ie);
        } catch (TimeoutException te) {
            throw new A2AClientException(
                    "AgentCard resolution timed out after " + requestTimeout.toMillis() + "ms", te);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause() != null ? ee.getCause() : ee;
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new A2AClientException("AgentCard resolution failed: " + cause.getMessage(), cause);
        }
    }

    /**
     * Sends a structured specialist delegation over the official A2A Java SDK {@link Client}
     * (JSON-RPC 2.0 {@code SendMessage} or SSE {@code SendStreamingMessage}) and maps the outcome
     * to {@link A2aDelegationResult} + canonical AG-UI {@link Event} records.
     */
    public DelegationExecution delegate(
            String requestId,
            String skillId,
            String prompt,
            Map<String, Object> context,
            Map<String, String> callHeaders) {
        Objects.requireNonNull(skillId, "skillId must not be null");
        Objects.requireNonNull(prompt, "prompt must not be null");
        String effectiveId = (requestId == null || requestId.isBlank())
                ? UUID.randomUUID().toString()
                : requestId;

        if (Thread.currentThread().isInterrupted()) {
            return fromFailure(null, effectiveId, skillId, "A2A delegation interrupted");
        }

        AgentCard card = null;
        CompletableFuture<?> dispatchFuture = null;
        try {
            long deadlineNanos = System.nanoTime() + requestTimeout.toNanos();
            Session session = resolveSessionBounded();
            card = session.card();
            Client client = session.client();
            boolean effectiveStreaming = streaming
                    && card.capabilities() != null
                    && Boolean.TRUE.equals(card.capabilities().streaming());

            Map<String, Object> payload = new LinkedHashMap<>();
            if (context != null) {
                payload.putAll(context);
            }
            payload.putIfAbsent("skillId", skillId);
            payload.putIfAbsent("requestId", effectiveId);

            Message userMessage = Message.builder()
                    .role(Message.Role.ROLE_USER)
                    .messageId(effectiveId)
                    .parts(List.of(
                            new TextPart("[" + skillId + "] " + prompt),
                            new DataPart(payload)))
                    .metadata(Map.of("skillId", skillId, "requestId", effectiveId))
                    .build();

            Map<String, String> mergedHeaders = new LinkedHashMap<>(authHeaders);
            if (callHeaders != null) {
                mergedHeaders.putAll(callHeaders);
            }
            ClientCallContext callContext = new ClientCallContext(
                    Map.of("skillId", skillId, "requestId", effectiveId),
                    mergedHeaders);

            CompletableFuture<Object> outcomeFuture = new CompletableFuture<>();
            BiConsumer<ClientEvent, AgentCard> eventConsumer = (event, evtCard) -> {
                if (event instanceof MessageEvent msgEvt) {
                    outcomeFuture.complete(msgEvt.getMessage());
                } else if (event instanceof TaskEvent taskEvt) {
                    Task task = taskEvt.getTask();
                    if (!effectiveStreaming || isSettledState(task.status().state())) {
                        outcomeFuture.complete(task);
                    }
                } else if (event instanceof TaskUpdateEvent updateEvt) {
                    Task task = updateEvt.getTask();
                    if (isSettledState(task.status().state())) {
                        outcomeFuture.complete(task);
                    }
                }
            };
            Consumer<Throwable> errorHandler = outcomeFuture::completeExceptionally;

            if (effectiveStreaming) {
                client.sendMessage(userMessage, List.of(eventConsumer), errorHandler, callContext);
            } else {
                dispatchFuture = CompletableFuture.runAsync(() -> {
                    try {
                        client.sendMessage(userMessage, List.of(eventConsumer), errorHandler, callContext);
                    } catch (Throwable t) {
                        outcomeFuture.completeExceptionally(t);
                    }
                }, ioExecutor);
            }

            long remainingMs = Math.max(1L, TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime()));
            Object settled = outcomeFuture.get(remainingMs, TimeUnit.MILLISECONDS);
            if (settled instanceof Message replyMsg) {
                return fromDirectMessage(card, effectiveId, skillId, replyMsg);
            }
            return fromCompletedTask(card, skillId, (Task) settled);
        } catch (InterruptedException ie) {
            cancelFutureQuietly(dispatchFuture);
            Thread.currentThread().interrupt();
            return fromFailure(card, effectiveId, skillId, "A2A delegation interrupted: " + ie.getMessage());
        } catch (TimeoutException te) {
            cancelFutureQuietly(dispatchFuture);
            return fromFailure(
                    card,
                    effectiveId,
                    skillId,
                    "A2A delegation timed out after " + requestTimeout.toMillis() + "ms");
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause() != null ? ee.getCause() : ee;
            invalidate();
            return fromFailure(card, effectiveId, skillId, "A2A delegation failed: " + cause.getMessage());
        } catch (RuntimeException ex) {
            invalidate();
            return fromFailure(card, effectiveId, skillId, "A2A client error: " + ex.getMessage());
        }
    }

    public boolean isCached() {
        return cachedSession != null;
    }

    public void invalidate() {
        Session previous;
        synchronized (lock) {
            previous = cachedSession;
            cachedSession = null;
        }
        if (previous != null) {
            previous.client().close();
        }
    }

    @Override
    public void close() {
        invalidate();
        ioExecutor.shutdownNow();
        if (ownsHttpClient && httpClient instanceof AutoCloseable closeableHttp) {
            try {
                closeableHttp.close();
            } catch (Exception ignored) {
                // Best-effort resource cleanup
            }
        }
    }

    private Session resolveSessionBounded()
            throws InterruptedException, ExecutionException, TimeoutException {
        Session existing = cachedSession;
        if (existing != null) {
            return existing;
        }
        return runBounded(() -> {
            synchronized (lock) {
                if (cachedSession == null) {
                    AgentCard rawCard = A2ACardResolver.builder()
                            .baseUrl(baseUrl)
                            .httpClient(httpClient)
                            .authHeaders(authHeaders)
                            .build()
                            .getAgentCard();
                    AgentCard rebound = rebindCardEndpoints(rawCard, baseUrl);
                    ClientConfig clientConfig = ClientConfig.builder()
                            .setStreaming(streaming)
                            .setAcceptedOutputModes(DEFAULT_OUTPUT_MODES)
                            .build();
                    Client client = Client.builder(rebound)
                            .clientConfig(clientConfig)
                            .withTransport(
                                    JSONRPCTransport.class,
                                    new JSONRPCTransportConfigBuilder().httpClient(httpClient))
                            .build();
                    cachedSession = new Session(rebound, client);
                }
                return cachedSession;
            }
        });
    }

    private <T> T runBounded(Callable<T> task)
            throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<T> future = new CompletableFuture<>();
        java.util.concurrent.Future<?> submitted = ioExecutor.submit(() -> {
            try {
                future.complete(task.call());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        try {
            return future.get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException | InterruptedException ex) {
            submitted.cancel(true);
            throw ex;
        }
    }

    private static void cancelFutureQuietly(CompletableFuture<?> future) {
        if (future != null) {
            future.cancel(true);
        }
    }

    private DelegationExecution fromCompletedTask(AgentCard card, String skillId, Task task) {
        String agentName = card.name();
        String summary = bridge.extractTaskSummary(task);
        Map<String, Object> data = bridge.extractTaskData(task);
        boolean isError = AgUiA2aBridge.isTerminalFailure(task.status().state())
                || AgUiA2aBridge.isPayloadError(data);
        String status = isError ? "ERROR" : normalizeTaskStatus(task.status().state());
        A2aDelegationResult result = new A2aDelegationResult(
                task.id(), agentName, skillId, status, isError, summary, data);
        List<Event> events = bridge.fromA2aTask(agentName, skillId, task);
        return new DelegationExecution(card, result, events);
    }

    private DelegationExecution fromDirectMessage(
            AgentCard card, String fallbackTaskId, String skillId, Message message) {
        String agentName = card.name();
        String taskId = message.taskId() != null ? message.taskId()
                : (message.messageId() != null ? message.messageId() : fallbackTaskId);
        String summary = bridge.extractMessageText(message);
        Map<String, Object> data = bridge.extractMessageData(message);
        boolean isError = AgUiA2aBridge.isPayloadError(data);
        String status = isError ? "ERROR" : "COMPLETED";
        A2aDelegationResult result = new A2aDelegationResult(
                taskId, agentName, skillId, status, isError, summary, data);
        List<Event> events = List.of(
                bridge.fromA2aClientEvent(skillId, new MessageEvent(message), card));
        return new DelegationExecution(card, result, events);
    }

    private DelegationExecution fromFailure(
            AgentCard card, String taskId, String skillId, String errorMessage) {
        String agentName = card != null ? card.name() : "A2A-Agent";
        Map<String, Object> errData = new LinkedHashMap<>();
        errData.put("status", "ERROR");
        errData.put("isError", true);
        errData.put("error", errorMessage);
        A2aDelegationResult result = new A2aDelegationResult(
                taskId, agentName, skillId, "ERROR", true, errorMessage, errData);
        A2aHandoff handoff = new A2aHandoff(taskId, agentName, skillId, errorMessage, Map.of("error", errorMessage));
        List<Event> events = bridge.recordDelegation(handoff);
        return new DelegationExecution(card, result, events);
    }

    private static boolean isSettledState(TaskState state) {
        return state.isFinal() || state.isInterrupted();
    }

    static String normalizeTaskStatus(TaskState state) {
        String raw = state.name();
        return raw.startsWith("TASK_STATE_") ? raw.substring("TASK_STATE_".length()) : raw;
    }

    public static AgentCard rebindCardEndpoints(AgentCard card, String targetBaseUrl) {
        String normalizedBase = normalizeBaseUrl(targetBaseUrl);
        String reboundCardUrl = rebindSingleUrl(card.url(), normalizedBase);
        List<AgentInterface> interfaces = card.supportedInterfaces() != null
                ? card.supportedInterfaces()
                : List.of();
        List<AgentInterface> reboundInterfaces = new ArrayList<>(interfaces.size());
        for (AgentInterface iface : interfaces) {
            reboundInterfaces.add(new AgentInterface(
                    iface.protocolBinding(),
                    rebindSingleUrl(iface.url(), normalizedBase),
                    iface.tenant(),
                    iface.protocolVersion()));
        }
        return AgentCard.builder(card)
                .url(reboundCardUrl)
                .supportedInterfaces(Collections.unmodifiableList(reboundInterfaces))
                .build();
    }

    /**
     * Safely rebinds an advertised endpoint URL onto {@code targetBaseUrl}'s origin and base path,
     * preventing host/port prefix-confusion SSRF, credential/userInfo spoofing, non-HTTP(S) schemes,
     * and protocol-relative {@code //authority} path injection.
     */
    public static String rebindSingleUrl(String advertisedUrl, String targetBaseUrl) {
        if (advertisedUrl == null || advertisedUrl.isBlank()) {
            return targetBaseUrl;
        }
        URI advUri;
        try {
            advUri = URI.create(advertisedUrl.trim());
        } catch (IllegalArgumentException ex) {
            return targetBaseUrl;
        }
        if (!isValidHttpUri(advUri)) {
            return targetBaseUrl;
        }
        String advPath = sanitizePath(advUri.getRawPath());
        if (advPath.isEmpty()) {
            return targetBaseUrl;
        }
        String basePath = sanitizePath(URI.create(targetBaseUrl).getRawPath());
        if (!basePath.isEmpty() && (advPath.equals(basePath) || advPath.startsWith(basePath + "/"))) {
            String suffix = advPath.substring(basePath.length());
            return suffix.isEmpty() ? targetBaseUrl : targetBaseUrl + suffix;
        }
        return targetBaseUrl + advPath;
    }

    public static String normalizeBaseUrl(String raw) {
        Objects.requireNonNull(raw, "baseUrl must not be null");
        String trimmed = raw.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        URI parsed = URI.create(trimmed);
        if (!isValidHttpUri(parsed)) {
            throw new IllegalArgumentException("baseUrl must be a valid http(s) URL without userInfo: " + raw);
        }
        return trimmed;
    }

    private static boolean isValidHttpUri(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null) {
            return false;
        }
        String lower = scheme.toLowerCase(Locale.ROOT);
        if (!"http".equals(lower) && !"https".equals(lower)) {
            return false;
        }
        return uri.getRawUserInfo() == null && uri.getHost() != null && !uri.getHost().isBlank();
    }

    private static String sanitizePath(String rawPath) {
        if (rawPath == null || rawPath.isEmpty() || "/".equals(rawPath)) {
            return "";
        }
        int start = 0;
        while (start < rawPath.length() && rawPath.charAt(start) == '/') {
            start++;
        }
        if (start >= rawPath.length()) {
            return "";
        }
        String normalized = "/" + rawPath.substring(start);
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private static Duration requirePositiveDuration(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    public static final class Builder {
        private final String baseUrl;
        private boolean streaming;
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration requestTimeout = Duration.ofSeconds(15);
        private A2AHttpClient httpClient;
        private boolean closeHttpClientOnShutdown;
        private Map<String, String> authHeaders = Map.of();
        private AgUiA2aBridge bridge;

        private Builder(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public Builder streaming(boolean streaming) {
            this.streaming = streaming;
            return this;
        }

        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = requirePositiveDuration(connectTimeout, "connectTimeout");
            return this;
        }

        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requirePositiveDuration(requestTimeout, "requestTimeout");
            return this;
        }

        public Builder httpClient(A2AHttpClient httpClient) {
            return httpClient(httpClient, false);
        }

        public Builder httpClient(A2AHttpClient httpClient, boolean closeOnShutdown) {
            this.httpClient = httpClient;
            this.closeHttpClientOnShutdown = closeOnShutdown;
            return this;
        }

        public Builder authHeaders(Map<String, String> authHeaders) {
            this.authHeaders = authHeaders == null ? Map.of() : Map.copyOf(authHeaders);
            return this;
        }

        public Builder bridge(AgUiA2aBridge bridge) {
            this.bridge = bridge;
            return this;
        }

        public AgUiA2aClient build() {
            return new AgUiA2aClient(this);
        }
    }
}
