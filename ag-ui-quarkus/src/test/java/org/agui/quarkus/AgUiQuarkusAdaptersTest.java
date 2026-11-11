package org.agui.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agui.community.core.agent.Agent;
import com.agui.community.core.agent.Context;
import com.agui.community.core.agent.RunAgentInput;
import com.agui.community.core.event.Event;
import com.agui.community.core.event.EventType;
import com.agui.community.core.event.JsonPatchOperation;
import com.agui.community.core.event.RunFinishedEvent;
import com.agui.community.core.event.RunStartedEvent;
import com.agui.community.core.event.StateDeltaEvent;
import com.agui.community.core.event.TextMessageContentEvent;
import com.agui.community.core.event.TextMessageEndEvent;
import com.agui.community.core.event.TextMessageStartEvent;
import com.agui.community.core.interrupt.Interrupt;
import com.agui.community.core.interrupt.InterruptOutcome;
import com.agui.community.core.interrupt.OutcomeType;
import com.agui.community.core.interrupt.Resume;
import com.agui.community.core.interrupt.ResumeStatus;
import com.agui.community.core.interrupt.SuccessOutcome;
import com.agui.community.core.message.AssistantMessage;
import com.agui.community.core.message.FunctionCall;
import com.agui.community.core.message.Message;
import com.agui.community.core.message.Role;
import com.agui.community.core.message.SystemMessage;
import com.agui.community.core.message.ToolCall;
import com.agui.community.core.message.UserMessage;
import com.agui.community.core.serialization.SerializationException;
import com.agui.community.core.tool.Tool;
import com.agui.community.core.tool.ToolParameters;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.mutiny.Multi;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.agui.quarkus.adapters.AgUiJacksonSerializer;
import org.agui.quarkus.adapters.AgUiObjectMapperCustomizer;
import org.agui.quarkus.adapters.AgUiQuarkusBridge;
import org.agui.quarkus.adapters.AgUiQuarkusWebSocketTransportSession;
import org.agui.quarkus.adapters.AgUiWebSocketHandler;
import org.agui.quarkus.application.AgUiSessionRegistry;
import org.agui.quarkus.domain.AgUiTransportType;
import org.junit.jupiter.api.Test;

class AgUiQuarkusAdaptersTest {

    @Test
    void jacksonSerializerAndCustomizerRoundTripEventsMessagesOutcomesAndRunAgentInput() {
        ObjectMapper quarkusMapper = new ObjectMapper();
        new AgUiObjectMapperCustomizer().customize(quarkusMapper);
        AgUiJacksonSerializer serializer = new AgUiJacksonSerializer(quarkusMapper);

        // Polymorphic Event round-trip: StateDeltaEvent & RunFinishedEvent with InterruptOutcome & SuccessOutcome
        StateDeltaEvent deltaEvent = new StateDeltaEvent(
                List.of(new JsonPatchOperation("add", "/city", "Kyoto")),
                1700000000L,
                Map.of("protocol", "AG-UI"));
        String deltaJson = serializer.serialize(deltaEvent);
        assertTrue(deltaJson.contains("\"type\":\"STATE_DELTA\""));
        assertFalse(deltaJson.contains("\"from\":null"));
        StateDeltaEvent parsedDelta = assertInstanceOf(StateDeltaEvent.class, serializer.deserialize(deltaJson, Event.class));
        assertEquals(EventType.STATE_DELTA, parsedDelta.type());
        assertEquals("/city", parsedDelta.delta().get(0).path());

        Interrupt interrupt = new Interrupt("int-1", "hitl:approval", "Approve?", "call-1", null, null, Map.of("costUsd", 1840));
        RunFinishedEvent intFin = new RunFinishedEvent("t-1", "r-1", new InterruptOutcome(List.of(interrupt)), null, 1L, null);
        String intFinJson = serializer.serialize(intFin);
        assertTrue(intFinJson.contains("\"type\":\"interrupt\""));
        RunFinishedEvent parsedIntFin = assertInstanceOf(RunFinishedEvent.class, serializer.deserialize(intFinJson, Event.class));
        InterruptOutcome parsedOutcome = assertInstanceOf(InterruptOutcome.class, parsedIntFin.outcome());
        assertEquals(OutcomeType.INTERRUPT, parsedOutcome.type());
        assertEquals("int-1", parsedOutcome.interrupts().get(0).id());

        RunFinishedEvent okFin = new RunFinishedEvent("t-1", "r-2", new SuccessOutcome(), Map.of("ok", true), 2L, null);
        String okFinJson = serializer.serialize(okFin);
        assertTrue(okFinJson.contains("\"type\":\"success\""));
        RunFinishedEvent parsedOkFin = assertInstanceOf(RunFinishedEvent.class, serializer.deserialize(okFinJson, Event.class));
        assertInstanceOf(SuccessOutcome.class, parsedOkFin.outcome());

        // Polymorphic Message + ToolCall ("type":"function") + RunAgentInput + Resume round-trip
        AssistantMessage assistantWithTool = new AssistantMessage(
                "m-2",
                null,
                null,
                List.of(new ToolCall("tc-1", new FunctionCall("fetchWeather", "{\"city\":\"Kyoto\"}"))));
        RunAgentInput input = new RunAgentInput(
                "thread-1",
                "run-1",
                Map.of("nights", 5),
                List.of(
                        new SystemMessage("m-0", "You are Nomad"),
                        new UserMessage("m-1", "Plan Kyoto"),
                        assistantWithTool),
                List.of(new Tool("fetchWeather", "Weather", new ToolParameters(Map.of(), List.of()))),
                List.of(new Context("locale", "en-US")),
                Map.of("llmMode", "ollama:llama3.2:latest"),
                List.of(new Resume("int-1", ResumeStatus.RESOLVED, Map.of("approved", true))));

        String inputJson = serializer.serialize(input);
        assertTrue(inputJson.contains("\"role\":\"user\""));
        assertTrue(inputJson.contains("\"type\":\"function\""));
        assertTrue(inputJson.contains("\"status\":\"resolved\""));

        RunAgentInput parsedInput = serializer.deserialize(inputJson, RunAgentInput.class);
        assertEquals("thread-1", parsedInput.threadId());
        assertEquals(3, parsedInput.messages().size());
        assertEquals(Role.USER, parsedInput.messages().get(1).role());
        AssistantMessage parsedAssistant = assertInstanceOf(AssistantMessage.class, parsedInput.messages().get(2));
        assertEquals("function", parsedAssistant.toolCalls().get(0).type());
        assertEquals(ResumeStatus.RESOLVED, parsedInput.resume().get(0).status());

        List<Message> msgList = serializer.deserializeList(
                "[{\"id\":\"u1\",\"role\":\"user\",\"content\":\"hi\"}]", Message.class);
        assertEquals(1, msgList.size());
        assertEquals(Role.USER, msgList.get(0).role());

        // Error handling
        assertThrows(SerializationException.class, () -> serializer.deserialize("{broken", Event.class));
        assertThrows(SerializationException.class, () -> serializer.deserializeList("[{broken", Event.class));
        Object unserializable = new Object() {
            @SuppressWarnings("unused")
            public String getBoom() {
                throw new IllegalStateException("boom");
            }
        };
        assertThrows(SerializationException.class, () -> serializer.serialize(unserializable));
    }

    @Test
    void quarkusBridgeRunsAgentsAndEncodesSseAndJsonPayloads() {
        AgUiQuarkusBridge bridge = new AgUiQuarkusBridge();
        assertNotNull(bridge.serializer());
        assertNotNull(bridge.sseEncoder());

        Agent echoAgent = input -> Multi.createFrom().items(
                new RunStartedEvent(input.threadId(), input.runId()),
                new TextMessageStartEvent("m-1", Role.ASSISTANT),
                new TextMessageContentEvent("m-1", "Hello Quarkus!"),
                new TextMessageEndEvent("m-1"),
                new RunFinishedEvent(input.threadId(), input.runId(), new SuccessOutcome(), null, null, null));

        RunAgentInput input = new RunAgentInput("t-q-1", "r-q-1", List.of(), List.of());

        List<Event> events = bridge.run(echoAgent, input).collect().asList().await().indefinitely();
        assertEquals(5, events.size());

        List<String> sseFrames = bridge.runAsSse(echoAgent, input).collect().asList().await().indefinitely();
        assertEquals(5, sseFrames.size());
        assertTrue(sseFrames.get(0).startsWith("data: {"));
        assertTrue(sseFrames.get(1).contains("\"role\":\"assistant\""));
        String unwrapped = AgUiQuarkusBridge.unwrapSseData(sseFrames.get(2));
        assertTrue(unwrapped.contains("Hello Quarkus!"));

        List<String> jsonPayloads = bridge.runAsJsonPayloads(echoAgent, input).collect().asList().await().indefinitely();
        assertEquals(5, jsonPayloads.size());
        assertTrue(jsonPayloads.get(0).startsWith("{"));

        assertNull(AgUiQuarkusBridge.unwrapSseData(null));
        assertEquals("", AgUiQuarkusBridge.unwrapSseData(": keepalive\n\n"));
        assertEquals("{\"a\":1}", AgUiQuarkusBridge.unwrapSseData("{\"a\":1}"));
        assertEquals("line1\nline2", AgUiQuarkusBridge.unwrapSseData("event: msg\ndata: line1\ndata: line2\n\n"));
    }

    @Test
    void webSocketHandlerAndSessionRegistryManageFullDuplexLifecycle() {
        assertNotNull(new AgUiWebSocketHandler());
        AtomicBoolean closed = new AtomicBoolean(false);
        List<String> sentFrames = new ArrayList<>();

        WebSocketConnection connection = (WebSocketConnection) Proxy.newProxyInstance(
                WebSocketConnection.class.getClassLoader(),
                new Class<?>[]{WebSocketConnection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "id" -> "ws-conn-1";
                    case "isClosed" -> closed.get();
                    case "isOpen" -> !closed.get();
                    case "sendTextAndAwait" -> {
                        sentFrames.add(String.valueOf(args[0]));
                        yield null;
                    }
                    case "closeAndAwait" -> {
                        closed.set(true);
                        yield null;
                    }
                    default -> null;
                });

        AgUiSessionRegistry registry = new AgUiSessionRegistry();
        AgUiJacksonSerializer serializer = new AgUiJacksonSerializer();
        AgUiQuarkusBridge bridge = new AgUiQuarkusBridge(serializer);
        AgUiWebSocketHandler handler = new AgUiWebSocketHandler(registry, bridge, serializer);

        AgUiQuarkusWebSocketTransportSession session = handler.onOpen(connection);
        assertEquals("ws-conn-1", session.sessionId());
        assertEquals(AgUiTransportType.WEBSOCKET, session.transportType());
        assertEquals(1, registry.activeCount());
        assertTrue(registry.find("ws-conn-1").isPresent());

        assertTrue(handler.handleControlFrame("ping", connection));
        assertTrue(handler.handleControlFrame("{\"type\":\"ping\"}", connection));
        assertTrue(handler.handleControlFrame("   ", connection));
        assertFalse(handler.handleControlFrame("{\"threadId\":\"t1\",\"runId\":\"r1\",\"messages\":[],\"tools\":[]}", connection));
        assertEquals(2, sentFrames.size());
        assertEquals("{\"type\":\"pong\"}", sentFrames.get(0));

        Agent wsAgent = input -> Multi.createFrom().items(
                new RunStartedEvent(input.threadId(), input.runId()),
                new RunFinishedEvent(input.threadId(), input.runId(), new SuccessOutcome(), null, null, null));

        handler.runAndSend(wsAgent, "{\"threadId\":\"t1\",\"runId\":\"r1\",\"messages\":[],\"tools\":[]}", connection);
        assertEquals(4, sentFrames.size());
        assertTrue(sentFrames.get(2).contains("RUN_STARTED"));
        assertTrue(sentFrames.get(3).contains("RUN_FINISHED"));

        // Malformed JSON emits RUN_ERROR frame cleanly
        handler.runAndSend(wsAgent, "{invalid-json", connection);
        assertEquals(5, sentFrames.size());
        assertTrue(sentFrames.get(4).contains("RUN_ERROR"));

        // Broadcast across active sessions
        registry.broadcastJson("{\"type\":\"CUSTOM\",\"name\":\"pingback\",\"value\":{}}");
        assertEquals(6, sentFrames.size());

        session.close();
        assertFalse(session.isOpen());
        session.sendJson("{\"ignored\":true}");
        assertEquals(6, sentFrames.size());

        handler.onClose(connection);
        assertEquals(0, registry.activeCount());

        handler.onOpen(connection);
        handler.onError(connection, new RuntimeException("ws error"));
        assertEquals(0, registry.activeCount());
        int frameCountBeforeNullGuards = sentFrames.size();
        handler.onClose(null);
        handler.sendEvent(connection, null);
        handler.sendEvent(null, new RunStartedEvent("t", "r"));
        assertEquals(frameCountBeforeNullGuards, sentFrames.size());
        assertTrue(handler.handleControlFrame(null, connection));
        assertTrue(handler.handleControlFrame("{\"type\": \"ping\"}", connection));
        registry.unregister(null);
        assertTrue(registry.find(null).isEmpty());

        AgUiQuarkusWebSocketTransportSession nullSession = new AgUiQuarkusWebSocketTransportSession("null-id", null);
        assertFalse(nullSession.isOpen());
        int frameCountBeforeNullSession = sentFrames.size();
        nullSession.sendJson(null);
        nullSession.sendJson("x");
        nullSession.close();
        assertEquals(frameCountBeforeNullSession, sentFrames.size());

        Agent fatalWsAgent = in -> {
            throw new AssertionError("fatal-ws-jvm-error");
        };
        assertThrows(AssertionError.class, () -> handler.runAndSend(
                fatalWsAgent,
                "{\"threadId\":\"t1\",\"runId\":\"r1\",\"messages\":[],\"tools\":[]}",
                connection));

        assertNotNull(handler.getClass().getClassLoader().getResourceAsStream("META-INF/beans.xml"));
        assertNotNull(handler.getClass().getClassLoader()
                .getResourceAsStream("META-INF/native-image/org.agui/ag-ui-quarkus/reflect-config.json"));
    }

    @Test
    void webSocketHandlerCancelsActiveInFlightSubscriptionsAndEnforcesStreamIdleTimeout() throws Exception {
        List<String> frames = java.util.Collections.synchronizedList(new ArrayList<>());
        WebSocketConnection connection = (WebSocketConnection) Proxy.newProxyInstance(
                WebSocketConnection.class.getClassLoader(),
                new Class<?>[]{WebSocketConnection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "id" -> "ws-cancel-conn";
                    case "isClosed" -> false;
                    case "isOpen" -> true;
                    case "sendTextAndAwait" -> {
                        frames.add(String.valueOf(args[0]));
                        yield null;
                    }
                    default -> null;
                });

        AgUiWebSocketHandler handler = new AgUiWebSocketHandler();
        handler.onOpen(connection);

        java.util.concurrent.CountDownLatch startedLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch finishedLatch = new java.util.concurrent.CountDownLatch(1);
        AtomicBoolean subscriptionCancelled = new AtomicBoolean(false);

        Agent hangingAgent = input -> Multi.createBy().concatenating().streams(
                Multi.createFrom().item((Event) new RunStartedEvent(input.threadId(), input.runId()))
                        .onItem().invoke(startedLatch::countDown),
                Multi.createFrom().<Event>nothing()
                        .onCancellation().invoke(() -> subscriptionCancelled.set(true)));

        Thread worker = new Thread(() -> {
            try {
                handler.runAndSend(
                        hangingAgent,
                        "{\"threadId\":\"t-hang\",\"runId\":\"r-hang\",\"messages\":[],\"tools\":[]}",
                        connection);
            } finally {
                finishedLatch.countDown();
            }
        });
        worker.start();

        assertTrue(startedLatch.await(2, java.util.concurrent.TimeUnit.SECONDS));
        assertFalse(handler.cancelRun(null, "t-hang"));
        assertFalse(handler.cancelRun(connection, "unrelated-thread"));
        assertTrue(handler.cancelRun(connection, "t-hang"));
        assertTrue(finishedLatch.await(2, java.util.concurrent.TimeUnit.SECONDS));
        worker.join(1000);
        assertTrue(subscriptionCancelled.get(), "Cancelling run must cancel upstream Multi subscription");
        assertFalse(frames.stream().anyMatch(f -> f.contains("RUN_ERROR")),
                "Explicit client cancellation must not emit spurious RUN_ERROR: " + frames);

        // Stream idle timeout aborts stalled upstream deterministically and surfaces RUN_ERROR
        frames.clear();
        handler.setStreamIdleTimeout(null);
        assertEquals(java.time.Duration.ofSeconds(60), handler.streamIdleTimeout());
        handler.setStreamIdleTimeout(java.time.Duration.ZERO);
        assertEquals(java.time.Duration.ofSeconds(60), handler.streamIdleTimeout());
        handler.setStreamIdleTimeout(java.time.Duration.ofMillis(80));
        assertEquals(java.time.Duration.ofMillis(80), handler.streamIdleTimeout());
        Agent stalledAgent = input -> Multi.createFrom().nothing();
        handler.runAndSend(
                stalledAgent,
                "{\"threadId\":\"t-stall\",\"runId\":\"r-stall\",\"messages\":[],\"tools\":[]}",
                connection);
        assertTrue(frames.stream().anyMatch(f -> f.contains("RUN_ERROR")),
                "Stalled upstream stream must emit RUN_ERROR after idle timeout: " + frames);
    }
}
