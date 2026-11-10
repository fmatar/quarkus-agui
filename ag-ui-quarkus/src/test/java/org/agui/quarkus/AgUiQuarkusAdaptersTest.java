package org.agui.quarkus;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.InputGuardrailException;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatEvent;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.mutiny.Multi;
import org.agui.core.adapters.AgUiSseTransportSession;
import org.agui.core.application.AgUiSessionRegistry;
import org.agui.core.domain.AgUiEvent;
import org.agui.core.domain.AgUiEventType;
import org.agui.core.domain.AgUiTransportType;
import org.agui.core.domain.TextDeltaPayload;
import org.agui.langchain4j.adapters.AgUiRunOptions;
import org.agui.langchain4j.adapters.AgUiToolClassifier;
import org.agui.quarkus.adapters.AgUiQuarkusBridge;
import org.agui.quarkus.adapters.AgUiQuarkusLangChain4jBridge;
import org.agui.quarkus.adapters.AgUiQuarkusWebSocketTransportSession;
import org.agui.quarkus.adapters.AgUiWebSocketEndpoint;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class AgUiQuarkusAdaptersTest {

    @Test
    void quarkusBridgeConvertsCoreStreamsAndSseSessionsToMutinyMulti() {
        AgUiQuarkusBridge bridge = new AgUiQuarkusBridge();
        assertNotNull(bridge.formatter());

        List<AgUiEvent<?>> events = bridge.streamTokens("thread-q-1", Arrays.asList("Hello ", "", null, "Mutiny!"))
                .collect().asList().await().indefinitely();
        assertEquals(4, events.size());

        List<String> frames = bridge.streamSseFrames(events).collect().asList().await().indefinitely();
        assertEquals(4, frames.size());
        assertTrue(frames.getFirst().startsWith("data: {"));

        AgUiSseTransportSession sseSession = new AgUiSseTransportSession("sse-q-1");
        List<String> sseReceived = new ArrayList<>();
        bridge.toMulti(sseSession).subscribe().with(sseReceived::add);
        sseSession.emit(AgUiEvent.of(AgUiEventType.TEXT_DELTA, "thread-q-1", new TextDeltaPayload("live-sse")));
        sseSession.close();
        assertEquals(1, sseReceived.size());
        assertTrue(sseReceived.getFirst().contains("live-sse"));
    }

    @Test
    void quarkusWebSocketEndpointAndSessionHandleFullLifecycle() {
        AtomicBoolean closed = new AtomicBoolean(false);
        List<String> sentMessages = new ArrayList<>();

        WebSocketConnection connection = (WebSocketConnection) Proxy.newProxyInstance(
                WebSocketConnection.class.getClassLoader(),
                new Class<?>[]{WebSocketConnection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "id" -> "ws-conn-1";
                    case "isClosed" -> closed.get();
                    case "sendTextAndAwait" -> {
                        sentMessages.add(String.valueOf(args[0]));
                        yield null;
                    }
                    case "closeAndAwait" -> {
                        closed.set(true);
                        yield null;
                    }
                    default -> null;
                });

        AgUiSessionRegistry registry = new AgUiSessionRegistry();
        AgUiWebSocketEndpoint endpoint = new AgUiWebSocketEndpoint(registry);
        assertEquals(AgUiTransportType.WEBSOCKET, endpoint.transportType());
        assertTrue(endpoint.isEnabled());

        endpoint.onOpen(connection);
        assertEquals(1, registry.activeCount());
        assertEquals(1, sentMessages.size());
        assertTrue(sentMessages.getFirst().contains("RUN_STARTED"));

        endpoint.onMessage("ping", connection);
        assertEquals(2, sentMessages.size());
        assertEquals("{\"type\":\"pong\"}", sentMessages.get(1));

        endpoint.onMessage("noop", connection);
        assertEquals(2, sentMessages.size());

        AgUiQuarkusWebSocketTransportSession session = new AgUiQuarkusWebSocketTransportSession("ws-conn-1", connection);
        assertEquals("ws-conn-1", session.sessionId());
        assertEquals(AgUiTransportType.WEBSOCKET, session.transportType());
        assertTrue(session.isOpen());

        session.emit(null);
        session.emit(AgUiEvent.of(AgUiEventType.TEXT_DELTA, "ws-conn-1", new TextDeltaPayload("ws-delta")));
        assertEquals(3, sentMessages.size());
        assertTrue(sentMessages.get(2).contains("ws-delta"));

        session.close();
        assertFalse(session.isOpen());
        session.emit(AgUiEvent.of(AgUiEventType.TEXT_DELTA, "ws-conn-1", new TextDeltaPayload("ignored")));
        assertEquals(3, sentMessages.size());

        endpoint.onClose(connection);
        assertEquals(0, registry.activeCount());

        endpoint.onOpen(connection);
        endpoint.onError(connection, new RuntimeException("simulated error"));
        assertEquals(0, registry.activeCount());

        AgUiQuarkusWebSocketTransportSession nullConnSession = new AgUiQuarkusWebSocketTransportSession("ws-null", null);
        assertFalse(nullConnSession.isOpen());
        nullConnSession.close();
    }

    @Test
    void quarkusLangChain4jBridgeAdaptsChatEventMultiStreamsAndFailures() {
        AgUiQuarkusLangChain4jBridge bridge = new AgUiQuarkusLangChain4jBridge();
        assertNotNull(bridge.formatter());

        Multi<ChatEvent> empty = Multi.createFrom().empty();
        assertThrows(NullPointerException.class, () -> bridge.adapt(null, empty));
        assertThrows(IllegalArgumentException.class, () -> bridge.adapt("   ", empty));
        assertThrows(NullPointerException.class, () -> bridge.adapt("thread-1", null));

        ToolExecutionRequest req = ToolExecutionRequest.builder()
                .id("call-1")
                .name("fetchCityWeather")
                .arguments("{\"city\":\"Kyoto\"}")
                .build();
        ToolExecution exec = ToolExecution.builder()
                .invocationContext(InvocationContext.builder().invocationId(UUID.randomUUID()).timestamp(Instant.now()).build())
                .request(req)
                .result(ToolExecutionResult.builder().isError(false).resultText("{\"status\":\"OK\"}").build())
                .build();
        ChatResponse finalResp = ChatResponse.builder().aiMessage(AiMessage.from("18C")).build();

        Multi<ChatEvent> stream = Multi.createFrom().items(
                new ChatEvent.PartialThinkingEvent("Checking"),
                new ChatEvent.ContentFetchedEvent(List.of(Content.from("Note"))),
                new ChatEvent.BeforeToolExecutionEvent(req),
                new ChatEvent.ToolExecutedEvent(exec),
                new ChatEvent.IntermediateResponseEvent(finalResp),
                new ChatEvent.PartialResponseEvent("18C"),
                new ChatEvent.AccumulatedResponseEvent("Accumulated", null),
                new ChatEvent.ChatCompletedEvent(finalResp));

        AgUiRunOptions options = new AgUiRunOptions(
                "ollama:llama3.2:latest",
                Map.of("city", "Kyoto"),
                AgUiToolClassifier.defaultMcpClassifier("nomad"),
                null,
                null,
                null);

        List<AgUiEvent<?>> adaptedEvents = bridge.adapt("thread-q-lc", stream, options)
                .collect().asList().await().indefinitely();
        assertEquals(List.of(
                        AgUiEventType.RUN_STARTED.name(),
                        AgUiEventType.STATE_SNAPSHOT.name(),
                        AgUiEventType.STATUS.name(),
                        AgUiEventType.STEP_STARTED.name(),
                        AgUiEventType.STEP_FINISHED.name(),
                        AgUiEventType.TOOL_CALL.name(),
                        AgUiEventType.TOOL_RESULT.name(),
                        AgUiEventType.TEXT_DELTA.name(),
                        AgUiEventType.TEXT_DELTA.name(),
                        AgUiEventType.RUN_FINISHED.name(),
                        AgUiEventType.RUN_FINISHED.name()),
                adaptedEvents.stream().map(AgUiEvent::type).toList());

        List<String> frames = bridge.adaptToSse("thread-q-lc", stream, options)
                .collect().asList().await().indefinitely();
        assertEquals(11, frames.size());
        assertTrue(frames.getFirst().contains("RUN_STARTED"));
        assertTrue(frames.getLast().contains("RUN_FINISHED"));

        Multi<ChatEvent> failStream = Multi.createFrom().failure(new InputGuardrailException("Blocked"));
        List<AgUiEvent<?>> failEvents = bridge.adapt("thread-q-fail", failStream)
                .collect().asList().await().indefinitely();
        assertEquals(AgUiEventType.RUN_FINISHED.name(), failEvents.getLast().type());
        List<AgUiEvent<?>> nullOptEvents = bridge.adapt("thread-q-null", Multi.createFrom().empty(), null)
                .collect().asList().await().indefinitely();
        assertEquals(1, nullOptEvents.size());
        assertNotNull(bridge.getClass().getClassLoader().getResourceAsStream("META-INF/beans.xml"));
    }
}
