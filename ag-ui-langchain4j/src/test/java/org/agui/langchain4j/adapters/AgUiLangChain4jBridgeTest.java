package org.agui.langchain4j.adapters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agui.community.core.agent.Agent;
import com.agui.community.core.agent.RunAgentInput;
import com.agui.community.core.event.Event;
import com.agui.community.core.event.EventType;
import com.agui.community.core.event.JsonPatchOperation;
import com.agui.community.core.event.RunErrorEvent;
import com.agui.community.core.event.RunFinishedEvent;
import com.agui.community.core.event.StateDeltaEvent;
import com.agui.community.core.event.StepFinishedEvent;
import com.agui.community.core.event.TextMessageContentEvent;
import com.agui.community.core.event.ToolCallArgsEvent;
import com.agui.community.core.event.ToolCallResultEvent;
import com.agui.community.core.event.ToolCallStartEvent;
import com.agui.community.core.interrupt.Interrupt;
import com.agui.community.core.interrupt.InterruptOutcome;
import com.agui.community.core.interrupt.OutcomeType;
import com.agui.community.core.interrupt.SuccessOutcome;
import com.agui.community.core.message.Role;
import com.agui.community.core.message.UserMessage;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.InputGuardrailException;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import org.agui.langchain4j.domain.AgUiStatePatch;
import org.agui.langchain4j.domain.McpToolMetadata;
import org.junit.jupiter.api.Test;

class AgUiLangChain4jBridgeTest {

    private final AgUiLangChain4jBridge bridge = new AgUiLangChain4jBridge();

    private static List<Event> collectAll(Flow.Publisher<Event> publisher) {
        List<Event> collected = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(Event item) {
                collected.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
                errors.add(throwable);
            }

            @Override
            public void onComplete() {
            }
        });
        if (!errors.isEmpty()) {
            throw new RuntimeException(errors.get(0));
        }
        return collected;
    }

    private static BeforeToolExecution beforeToolExecution(ToolExecutionRequest req) {
        return BeforeToolExecution.builder()
                .invocationContext(InvocationContext.builder()
                        .invocationId(UUID.randomUUID())
                        .timestamp(Instant.now())
                        .build())
                .request(req)
                .build();
    }

    private static ToolExecution toolExecution(ToolExecutionRequest req, String text, boolean isError) {
        return ToolExecution.builder()
                .invocationContext(InvocationContext.builder()
                        .invocationId(UUID.randomUUID())
                        .timestamp(Instant.now())
                        .build())
                .request(req)
                .result(ToolExecutionResult.builder()
                        .isError(isError)
                        .resultText(text)
                        .build())
                .build();
    }

    private static final class StubTokenStream implements TokenStream {
        private final Consumer<StubTokenStream> scenario;
        Consumer<String> partialResponseHandler = s -> {};
        Consumer<PartialThinking> partialThinkingHandler = t -> {};
        Consumer<List<Content>> retrievedHandler = c -> {};
        Consumer<BeforeToolExecution> beforeToolHandler = b -> {};
        Consumer<ToolExecution> toolExecutedHandler = e -> {};
        Consumer<ChatResponse> completeHandler = r -> {};
        Consumer<Throwable> errorHandler = e -> {};

        StubTokenStream(Consumer<StubTokenStream> scenario) {
            this.scenario = scenario;
        }

        @Override
        public TokenStream onPartialResponse(Consumer<String> handler) {
            this.partialResponseHandler = handler;
            return this;
        }

        @Override
        public TokenStream onPartialThinking(Consumer<PartialThinking> handler) {
            this.partialThinkingHandler = handler;
            return this;
        }

        @Override
        public TokenStream onRetrieved(Consumer<List<Content>> handler) {
            this.retrievedHandler = handler;
            return this;
        }

        @Override
        public TokenStream onIntermediateResponse(Consumer<ChatResponse> handler) {
            return this;
        }

        @Override
        public TokenStream beforeToolExecution(Consumer<BeforeToolExecution> handler) {
            this.beforeToolHandler = handler;
            return this;
        }

        @Override
        public TokenStream onToolExecuted(Consumer<ToolExecution> handler) {
            this.toolExecutedHandler = handler;
            return this;
        }

        @Override
        public TokenStream onCompleteResponse(Consumer<ChatResponse> handler) {
            this.completeHandler = handler;
            return this;
        }

        @Override
        public TokenStream onError(Consumer<Throwable> handler) {
            this.errorHandler = handler;
            return this;
        }

        @Override
        public TokenStream ignoreErrors() {
            return this;
        }

        @Override
        public void start() {
            scenario.accept(this);
        }
    }

    @Test
    void statePatchAndMcpBridgeFollowRfc6902AndCanonicalToolEvents() {
        assertEquals("/tripDossierState", AgUiStatePatch.toPointer("tripDossierState"));
        assertEquals("/nested/field", AgUiStatePatch.toPointer("/nested/field"));
        assertEquals("/a~0b~1c", AgUiStatePatch.toPointer("a~b/c"));
        assertThrows(IllegalArgumentException.class, () -> AgUiStatePatch.toPointer(""));
        assertThrows(NullPointerException.class, () -> AgUiStatePatch.toPointer(null));

        JsonPatchOperation addOp = AgUiStatePatch.add("memoryTurns", 4);
        assertEquals("add", addOp.op());
        assertEquals("/memoryTurns", addOp.path());
        assertEquals(4, addOp.value());

        JsonPatchOperation repOp = AgUiStatePatch.replace("/city", "Kyoto");
        assertEquals("replace", repOp.op());
        assertEquals("/city", repOp.path());

        JsonPatchOperation remOp = AgUiStatePatch.remove("temp");
        assertEquals("remove", remOp.op());
        assertEquals("/temp", remOp.path());

        assertTrue(AgUiStatePatch.upsertRootFields(null).isEmpty());
        assertTrue(AgUiStatePatch.upsertRootFields(Map.of()).isEmpty());

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("city", "Kyoto");
        fields.put("nights", 5);
        List<JsonPatchOperation> batch = AgUiStatePatch.upsertRootFields(fields);
        assertEquals(2, batch.size());
        assertEquals("add", batch.get(0).op());
        assertEquals("/city", batch.get(0).path());

        McpToolMetadata meta = new McpToolMetadata("nomad-mcp (:8081)", "fetchDestinationWeather", "streamable-http");
        assertThrows(NullPointerException.class, () -> new McpToolMetadata(null, "t", "http"));
        assertThrows(NullPointerException.class, () -> new McpToolMetadata("s", null, "http"));
        assertThrows(NullPointerException.class, () -> new McpToolMetadata("s", "t", null));

        AgUiMcpBridge mcpBridge = new AgUiMcpBridge();
        List<Event> mcpEvents = mcpBridge.recordToolExecution(
                "call-mcp-1", "msg-parent-1", meta, Map.of("city", "Kyoto"), Map.of("tempC", 18.4));
        assertEquals(List.of(
                        EventType.TOOL_CALL_START,
                        EventType.TOOL_CALL_ARGS,
                        EventType.TOOL_CALL_END,
                        EventType.TOOL_CALL_RESULT),
                mcpEvents.stream().map(Event::type).toList());
        ToolCallStartEvent startEvt = assertInstanceOf(ToolCallStartEvent.class, mcpEvents.get(0));
        assertEquals("call-mcp-1", startEvt.toolCallId());
        assertEquals("msg-parent-1", startEvt.parentMessageId());
        ToolCallArgsEvent argsEvt = assertInstanceOf(ToolCallArgsEvent.class, mcpEvents.get(1));
        assertTrue(argsEvt.delta().contains("Kyoto"));
        ToolCallResultEvent resEvt = assertInstanceOf(ToolCallResultEvent.class, mcpEvents.get(3));
        assertEquals(Role.TOOL, resEvt.role());
        assertTrue(resEvt.content().contains("18.4"));

        List<Event> stringResEvents = mcpBridge.recordToolExecution("call-mcp-2", null, meta, null, "raw-text");
        ToolCallResultEvent strRes = assertInstanceOf(ToolCallResultEvent.class, stringResEvents.get(3));
        assertEquals("raw-text", strRes.content());

        assertThrows(NullPointerException.class, () -> mcpBridge.recordToolExecution(null, null, meta, Map.of(), "ok"));
        assertThrows(NullPointerException.class, () -> mcpBridge.recordToolExecution("c", null, null, Map.of(), "ok"));
    }

    @Test
    void rejectsNullOrBlankThreadIdAndNullStream() {
        TokenStream empty = new StubTokenStream(s -> s.completeHandler.accept(ChatResponse.builder().build()));
        assertThrows(NullPointerException.class, () -> bridge.adapt(null, empty));
        assertThrows(IllegalArgumentException.class, () -> bridge.adapt("   ", empty));
        assertThrows(NullPointerException.class, () -> bridge.adapt("thread-1", null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void adaptsTokenStreamIntoCanonicalReasoningStepsToolCallsJsonPatchesAndTextMessages() {
        ToolExecutionRequest mcpReq = ToolExecutionRequest.builder()
                .id("call-w-1")
                .name("fetchDestinationWeather")
                .arguments("{\"city\":\"Kyoto\"}")
                .build();
        ToolExecution mcpExec = toolExecution(
                mcpReq,
                "{\"status\":\"OK\",\"isError\":false,\"city\":\"Kyoto\",\"temperatureC\":18.4}",
                false);

        ToolExecutionRequest a2aReq = ToolExecutionRequest.builder()
                .id("call-adv-1")
                .name("verifyTravelAdvisory")
                .arguments("{\"city\":\"Kyoto\"}")
                .build();
        ToolExecution a2aExec = toolExecution(
                a2aReq,
                "{\"status\":\"OK\",\"isError\":false,\"country\":\"Japan\",\"summary\":\"Destination advisory verified for Kyoto.\"}",
                false);

        ChatResponse finalResponse = ChatResponse.builder()
                .aiMessage(AiMessage.from("Kyoto is crisp at 18.4°C."))
                .build();

        TokenStream tokenStream = new StubTokenStream(s -> {
            s.partialThinkingHandler.accept(new PartialThinking("   "));
            s.partialThinkingHandler.accept(new PartialThinking("Checking live Kyoto "));
            s.partialThinkingHandler.accept(new PartialThinking("weather & advisory"));
            s.retrievedHandler.accept(List.of(Content.from("Machiya cedar joinery context")));
            s.beforeToolHandler.accept(beforeToolExecution(mcpReq));
            s.toolExecutedHandler.accept(mcpExec);
            s.beforeToolHandler.accept(beforeToolExecution(a2aReq));
            s.toolExecutedHandler.accept(a2aExec);
            s.partialResponseHandler.accept("");
            s.partialResponseHandler.accept("Kyoto is crisp ");
            s.partialResponseHandler.accept("at 18.4°C.");
            s.completeHandler.accept(finalResponse);
        });

        AgUiToolClassifier classifier = (toolName, args) -> "verifyTravelAdvisory".equals(toolName)
                ? AgUiToolClassifier.ToolDescriptor.a2aStep("Nomad-A2A-Specialist", "verify-travel-advisory")
                : AgUiToolClassifier.ToolDescriptor.mcpTool("nomad-mcp-concierge (:8081)", toolName);

        AgUiRunOptions options = AgUiRunOptions.builder()
                .runId("run-kyoto-1")
                .llmMode("ollama:llama3.2:latest")
                .initialStateSnapshot(Map.of("tripDossierState", Map.of("activeCity", "Kyoto")))
                .toolClassifier(classifier)
                .postToolStatePatchProvider(exec -> "fetchDestinationWeather".equals(exec.request().name())
                        ? List.of(AgUiStatePatch.add("weatherUpdated", true))
                        : List.of())
                .postRunStatePatchProvider(() -> List.of(AgUiStatePatch.add("memoryTurns", 2)))
                .runResultSupplier(() -> Map.of("completed", true))
                .build();

        List<Event> events = collectAll(bridge.adapt("thread-kyoto", tokenStream, options));
        List<EventType> types = events.stream().map(Event::type).toList();

        assertEquals(List.of(
                EventType.RUN_STARTED,
                EventType.STATE_SNAPSHOT,
                EventType.REASONING_START,
                EventType.REASONING_MESSAGE_START,
                EventType.REASONING_MESSAGE_CONTENT,
                EventType.REASONING_MESSAGE_CONTENT,
                EventType.REASONING_MESSAGE_END,
                EventType.REASONING_END,
                EventType.STEP_STARTED,
                EventType.STEP_FINISHED,
                EventType.TOOL_CALL_START,
                EventType.TOOL_CALL_ARGS,
                EventType.TOOL_CALL_END,
                EventType.TOOL_CALL_RESULT,
                EventType.STATE_DELTA,
                EventType.STEP_STARTED,
                EventType.STEP_FINISHED,
                EventType.TEXT_MESSAGE_START,
                EventType.TEXT_MESSAGE_CONTENT,
                EventType.TEXT_MESSAGE_CONTENT,
                EventType.TEXT_MESSAGE_END,
                EventType.STATE_DELTA,
                EventType.RUN_FINISHED), types);

        ToolCallStartEvent toolStart = assertInstanceOf(ToolCallStartEvent.class, events.get(10));
        assertEquals("fetchDestinationWeather", toolStart.toolCallName());
        Map<String, Object> startMeta = (Map<String, Object>) toolStart.rawEvent();
        assertEquals("mcp:tool", startMeta.get("callType"));
        assertEquals("MCP", startMeta.get("protocol"));
        assertEquals(Map.of("city", "Kyoto"), startMeta.get("args"));

        ToolCallResultEvent toolRes = assertInstanceOf(ToolCallResultEvent.class, events.get(13));
        Map<String, Object> resMeta = (Map<String, Object>) toolRes.rawEvent();
        assertEquals(false, resMeta.get("isError"));
        assertEquals("OK", resMeta.get("status"));

        StateDeltaEvent postToolDelta = assertInstanceOf(StateDeltaEvent.class, events.get(14));
        assertEquals("/weatherUpdated", postToolDelta.delta().get(0).path());

        StepFinishedEvent a2aFin = assertInstanceOf(StepFinishedEvent.class, events.get(16));
        Map<String, Object> a2aMeta = (Map<String, Object>) a2aFin.rawEvent();
        assertEquals("verify-travel-advisory", a2aMeta.get("skillId"));
        assertEquals("Destination advisory verified for Kyoto.", a2aMeta.get("summary"));

        TextMessageContentEvent firstChunk = assertInstanceOf(TextMessageContentEvent.class, events.get(18));
        assertEquals("Kyoto is crisp ", firstChunk.delta());

        RunFinishedEvent finished = assertInstanceOf(RunFinishedEvent.class, events.get(22));
        assertEquals("run-kyoto-1", finished.runId());
        assertInstanceOf(SuccessOutcome.class, finished.outcome());
        assertEquals(OutcomeType.SUCCESS, finished.outcome().type());
        assertEquals(Map.of("completed", true), finished.result());
    }

    @Test
    @SuppressWarnings("unchecked")
    void correlatesGeneratedToolCallIdsAndSurfacesHitlInterruptsAndToolErrors() {
        // Null IDs on Ollama tool calls must still correlate start -> args -> end -> result!
        ToolExecutionRequest nullIdReq = ToolExecutionRequest.builder()
                .name("requestSanctuaryReservation")
                .arguments("{malformed-json")
                .build();
        ToolExecution nullIdExec = toolExecution(
                nullIdReq,
                "{\"status\":\"ERROR\",\"isError\":true,\"error\":\"Open-Meteo HTTP 503\"}",
                true);

        ToolExecutionRequest a2aErrReq = ToolExecutionRequest.builder()
                .name("verifyTravelAdvisory")
                .arguments("")
                .build();
        ToolExecution a2aErrExec = toolExecution(
                a2aErrReq,
                "{\"status\":\"ERROR\",\"isError\":false,\"message\":\"Missing RESTCOUNTRIES_API_KEY\"}",
                false);

        TokenStream stream = new StubTokenStream(s -> {
            s.partialResponseHandler.accept("Checking reservation...");
            s.beforeToolHandler.accept(beforeToolExecution(nullIdReq));
            s.toolExecutedHandler.accept(nullIdExec);
            s.beforeToolHandler.accept(beforeToolExecution(a2aErrReq));
            s.toolExecutedHandler.accept(a2aErrExec);
            s.completeHandler.accept(ChatResponse.builder().aiMessage(AiMessage.from("Confirm?")).build());
        });

        Interrupt hitlInterrupt = new Interrupt(
                "int-kyoto-1",
                "hitl:approval",
                "Approve Hoshinoya Kyoto ($1,840 USD)?",
                "call-hitl-1",
                null,
                null,
                Map.of("city", "Kyoto", "costUsd", 1840));

        AgUiRunOptions options = AgUiRunOptions.builder()
                .toolClassifier((name, args) -> "verifyTravelAdvisory".equals(name)
                        ? AgUiToolClassifier.ToolDescriptor.a2aStep("Nomad-A2A-Specialist", "verify-travel-advisory")
                        : AgUiToolClassifier.ToolDescriptor.hitlApproval("Nomad-Concierge", name))
                .interruptSupplier(() -> List.of(hitlInterrupt))
                .build();

        Agent agent = bridge.asAgent((input, opts) -> stream, input -> options);
        RunAgentInput input = new RunAgentInput(
                "thread-hitl",
                "run-hitl-1",
                List.of(new UserMessage("u1", "Book Hoshinoya Kyoto")),
                List.of());

        List<Event> events = collectAll(agent.run(input));
        ToolCallStartEvent startEvt = events.stream()
                .filter(ToolCallStartEvent.class::isInstance)
                .map(ToolCallStartEvent.class::cast)
                .findFirst()
                .orElseThrow();
        ToolCallResultEvent resEvt = events.stream()
                .filter(ToolCallResultEvent.class::isInstance)
                .map(ToolCallResultEvent.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(startEvt.toolCallId(), resEvt.toolCallId());

        Map<String, Object> mcpErrMeta = (Map<String, Object>) resEvt.rawEvent();
        assertTrue((Boolean) mcpErrMeta.get("isError"));
        assertEquals("Open-Meteo HTTP 503", mcpErrMeta.get("error"));
        assertEquals("HITL", mcpErrMeta.get("protocol"));

        StepFinishedEvent a2aErrStep = events.stream()
                .filter(StepFinishedEvent.class::isInstance)
                .map(StepFinishedEvent.class::cast)
                .findFirst()
                .orElseThrow();
        Map<String, Object> a2aErrMeta = (Map<String, Object>) a2aErrStep.rawEvent();
        assertTrue((Boolean) a2aErrMeta.get("isError"));
        assertEquals("Missing RESTCOUNTRIES_API_KEY", a2aErrMeta.get("error"));

        RunFinishedEvent fin = assertInstanceOf(RunFinishedEvent.class, events.get(events.size() - 1));
        InterruptOutcome outcome = assertInstanceOf(InterruptOutcome.class, fin.outcome());
        assertEquals(OutcomeType.INTERRUPT, outcome.type());
        assertEquals(1, outcome.interrupts().size());
        assertEquals("int-kyoto-1", outcome.interrupts().get(0).id());
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsGuardrailAndRuntimeFailuresToCanonicalRunErrorEventsAndClosesOpenStreams() {
        TokenStream inputBlocked = new StubTokenStream(s -> {
            s.partialThinkingHandler.accept(new PartialThinking("Evaluating..."));
            s.errorHandler.accept(new CompletionException(new InputGuardrailException("Prompt injection blocked")));
        });
        List<Event> inEvents = collectAll(bridge.adapt("thread-guard-in", inputBlocked));
        assertTrue(inEvents.stream().anyMatch(e -> e.type() == EventType.REASONING_END));
        assertTrue(inEvents.stream().anyMatch(e -> e.type() == EventType.STEP_FINISHED));
        RunErrorEvent inErr = assertInstanceOf(RunErrorEvent.class, inEvents.get(inEvents.size() - 1));
        assertEquals("GUARDRAIL_BLOCKED", inErr.code());
        assertEquals("guardrail:input", ((Map<String, Object>) inErr.rawEvent()).get("callType"));

        TokenStream outputBlocked = new StubTokenStream(s -> {
            s.partialResponseHandler.accept("Partial leak");
            throw new OutputGuardrailException("System prompt leak blocked");
        });
        List<Event> outEvents = collectAll(bridge.adapt("thread-guard-out", outputBlocked, null));
        assertTrue(outEvents.stream().anyMatch(e -> e.type() == EventType.TEXT_MESSAGE_END));
        RunErrorEvent outErr = assertInstanceOf(RunErrorEvent.class, outEvents.get(outEvents.size() - 1));
        assertEquals("GUARDRAIL_BLOCKED", outErr.code());
        assertEquals("guardrail:output", ((Map<String, Object>) outErr.rawEvent()).get("callType"));

        TokenStream runtimeFail = new StubTokenStream(s -> s.errorHandler.accept(new IllegalStateException("")));
        List<Event> failEvents = collectAll(bridge.adapt("thread-fail", runtimeFail));
        RunErrorEvent runErr = assertInstanceOf(RunErrorEvent.class, failEvents.get(failEvents.size() - 1));
        assertEquals("UPSTREAM_ERROR", runErr.code());
        assertEquals("IllegalStateException", runErr.message());
    }

    @Test
    void sessionEdgeCasesAccumulatedIdempotencyAndSubscriptionCancellation() {
        assertEquals("mcp:sampling", AgUiToolClassifier.ToolDescriptor.mcpSampling("mcp", "s").callType());
        assertEquals("tool:ui", AgUiToolClassifier.ToolDescriptor.uiSurface("ui", "p").callType());
        var def = AgUiToolClassifier.defaultMcpClassifier("");
        assertEquals("langchain4j-mcp", def.classify(null, Map.of()).serverOrAgent());
        assertEquals("mcp-tool", def.classify("  ", Map.of()).skillOrLabel());

        AgUiLangChain4jRunSession session = bridge.newSession("thread-sess", AgUiRunOptions.defaults());
        assertEquals(1, session.onStart().size());
        assertTrue(session.onAccumulatedResponse("").isEmpty());
        List<Event> accEvents = session.onAccumulatedResponse("Full reply");
        assertEquals(List.of(
                        EventType.TEXT_MESSAGE_START,
                        EventType.TEXT_MESSAGE_CONTENT,
                        EventType.TEXT_MESSAGE_END),
                accEvents.stream().map(Event::type).toList());
        // Subsequent accumulated response after text was emitted should not duplicate text
        assertTrue(session.onAccumulatedResponse("Duplicate").isEmpty());
        assertEquals(1, session.onComplete().size());
        // Duplicate complete or error after termination must be no-op
        assertTrue(session.onComplete().isEmpty());
        assertTrue(session.onError(new RuntimeException("late")).isEmpty());

        // Standalone onToolExecuted without preceding onBeforeTool + null callbacks + UI tool protocol
        ToolExecution standaloneExec = toolExecution(ToolExecutionRequest.builder().build(), "  ", true);
        AgUiLangChain4jRunSession standaloneSession = bridge.newSession("thread-st", AgUiRunOptions.builder()
                .toolClassifier((n, a) -> AgUiToolClassifier.ToolDescriptor.uiSurface("ui", n))
                .build());
        assertEquals(1, standaloneSession.onToolExecuted(standaloneExec).size());
        ToolExecution rawTextErr = toolExecution(ToolExecutionRequest.builder().name("ui").build(), "Plain failure", true);
        assertEquals(1, standaloneSession.onToolExecuted(rawTextErr).size());
        assertThrows(NullPointerException.class, () -> bridge.newSession(null, null));
        assertThrows(IllegalArgumentException.class, () -> bridge.newSession("  ", null));

        TokenStream nullCallbacksStream = new StubTokenStream(s -> {
            s.partialThinkingHandler.accept(null);
            s.retrievedHandler.accept(null);
            s.beforeToolHandler.accept(null);
            s.toolExecutedHandler.accept(null);
            s.completeHandler.accept(ChatResponse.builder().build());
        });
        Agent defaultOptsAgent = bridge.asAgent((in, opts) -> nullCallbacksStream, null);
        List<Event> nullCbEvents = collectAll(defaultOptsAgent.run(new RunAgentInput("thread-ncb", "", List.of(), List.of())));
        assertEquals(4, nullCbEvents.size());

        // Subscription cancel and invalid request(0)
        var pub = bridge.adapt("thread-cancel", new StubTokenStream(s -> s.partialResponseHandler.accept("x")));
        pub.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(0);
                subscription.cancel();
                subscription.request(1);
            }

            @Override
            public void onNext(Event item) {
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onComplete() {
            }
        });
        assertNotNull(bridge.getClass().getClassLoader().getResourceAsStream("META-INF/beans.xml"));
    }
}
