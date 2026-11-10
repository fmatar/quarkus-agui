package org.agui.langchain4j.adapters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import org.agui.core.adapters.AgUiFlowAdapter;
import org.agui.core.domain.AgUiEvent;
import org.agui.core.domain.AgUiEventType;
import org.agui.core.domain.TextDeltaPayload;
import org.junit.jupiter.api.Test;

class AgUiLangChain4jBridgeTest {

    private final AgUiLangChain4jBridge bridge = new AgUiLangChain4jBridge();

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
    void rejectsNullOrBlankThreadIdAndNullStream() {
        TokenStream empty = new StubTokenStream(s -> s.completeHandler.accept(ChatResponse.builder().build()));
        assertThrows(NullPointerException.class, () -> bridge.adapt(null, empty));
        assertThrows(IllegalArgumentException.class, () -> bridge.adapt("   ", empty));
        assertThrows(NullPointerException.class, () -> bridge.adapt("thread-1", null));
    }

    @Test
    void adaptsUpstreamTokenStreamWithThinkingMcpToolA2aStepRagAndTextDeltas() {
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
            s.partialThinkingHandler.accept(new PartialThinking("Checking live Kyoto weather & advisory"));
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

        AgUiToolClassifier classifier = (toolName, args) -> {
            if ("verifyTravelAdvisory".equals(toolName)) {
                return AgUiToolClassifier.ToolDescriptor.a2aStep("Nomad-A2A-Specialist", "verify-travel-advisory");
            }
            return AgUiToolClassifier.ToolDescriptor.mcpTool("nomad-mcp-concierge (:8081)", toolName);
        };

        AgUiRunOptions options = new AgUiRunOptions(
                "ollama:llama3.2:latest",
                Map.of("tripDossierState", Map.of("activeCity", "Kyoto")),
                classifier,
                exec -> "fetchDestinationWeather".equals(exec.request().name())
                        ? Map.of("weatherUpdated", true)
                        : Map.of(),
                () -> Map.of("memoryTurns", 2),
                null);

        List<AgUiEvent<?>> events = AgUiFlowAdapter.collectAll(bridge.adapt("thread-kyoto", tokenStream, options));

        List<String> types = events.stream().map(AgUiEvent::type).toList();
        assertEquals(List.of(
                AgUiEventType.RUN_STARTED.name(),
                AgUiEventType.STATE_SNAPSHOT.name(),
                AgUiEventType.STATUS.name(),
                AgUiEventType.STEP_STARTED.name(),
                AgUiEventType.STEP_FINISHED.name(),
                AgUiEventType.TOOL_CALL.name(),
                AgUiEventType.TOOL_RESULT.name(),
                AgUiEventType.STATE_DELTA.name(),
                AgUiEventType.STEP_STARTED.name(),
                AgUiEventType.STEP_FINISHED.name(),
                AgUiEventType.TEXT_DELTA.name(),
                AgUiEventType.TEXT_DELTA.name(),
                AgUiEventType.STATE_DELTA.name(),
                AgUiEventType.RUN_FINISHED.name()), types);

        @SuppressWarnings("unchecked")
        Map<String, Object> toolCallPayload = (Map<String, Object>) events.get(5).payload();
        assertEquals("fetchDestinationWeather", toolCallPayload.get("tool"));
        assertEquals("mcp:tool", toolCallPayload.get("callType"));
        assertEquals(Map.of("city", "Kyoto"), toolCallPayload.get("args"));

        @SuppressWarnings("unchecked")
        Map<String, Object> toolResultPayload = (Map<String, Object>) events.get(6).payload();
        assertEquals(false, toolResultPayload.get("isError"));
        assertEquals("OK", toolResultPayload.get("status"));

        @SuppressWarnings("unchecked")
        Map<String, Object> a2aFinishedPayload = (Map<String, Object>) events.get(9).payload();
        assertEquals("verify-travel-advisory", a2aFinishedPayload.get("skillId"));
        assertEquals(false, a2aFinishedPayload.get("isError"));
        assertEquals("Destination advisory verified for Kyoto.", a2aFinishedPayload.get("summary"));

        TextDeltaPayload firstChunk = (TextDeltaPayload) events.get(10).payload();
        assertEquals("Kyoto is crisp ", firstChunk.content());
    }

    @Test
    void surfacesUpstreamMcpAndA2aToolErrorsHonestlyWithoutMasking() {
        ToolExecutionRequest mcpErrReq = ToolExecutionRequest.builder()
                .id("call-err-1")
                .name("fetchDestinationWeather")
                .arguments("{malformed-json")
                .build();
        ToolExecution mcpErrExec = toolExecution(
                mcpErrReq,
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
            s.beforeToolHandler.accept(beforeToolExecution(mcpErrReq));
            s.toolExecutedHandler.accept(mcpErrExec);
            s.beforeToolHandler.accept(beforeToolExecution(a2aErrReq));
            s.toolExecutedHandler.accept(a2aErrExec);
            s.completeHandler.accept(ChatResponse.builder().aiMessage(AiMessage.from("Service down.")).build());
        });

        AgUiRunOptions options = new AgUiRunOptions(
                null,
                null,
                (name, args) -> "verifyTravelAdvisory".equals(name)
                        ? AgUiToolClassifier.ToolDescriptor.a2aStep("Nomad-A2A-Specialist", "verify-travel-advisory")
                        : AgUiToolClassifier.ToolDescriptor.mcpTool("nomad-mcp", name),
                null,
                null,
                null);

        List<AgUiEvent<?>> events = AgUiFlowAdapter.collectAll(bridge.adapt("thread-err", stream, options));

        @SuppressWarnings("unchecked")
        Map<String, Object> mcpResPayload = (Map<String, Object>) events.get(2).payload();
        assertTrue((Boolean) mcpResPayload.get("isError"));
        assertEquals("ERROR", mcpResPayload.get("status"));
        assertEquals("Open-Meteo HTTP 503", mcpResPayload.get("error"));

        @SuppressWarnings("unchecked")
        Map<String, Object> a2aResPayload = (Map<String, Object>) events.get(4).payload();
        assertTrue((Boolean) a2aResPayload.get("isError"));
        assertEquals("ERROR", a2aResPayload.get("status"));
        assertEquals("Missing RESTCOUNTRIES_API_KEY", a2aResPayload.get("error"));
    }

    @Test
    void supportsHitlInterruptedCompletionAndSseFrameFormatting() {
        ToolExecutionRequest hitlReq = ToolExecutionRequest.builder()
                .id("hitl-1")
                .name("requestSanctuaryReservation")
                .arguments("{\"city\":\"Kyoto\",\"stopTitle\":\"Hoshinoya\",\"nights\":4,\"costUsd\":1840}")
                .build();
        ToolExecution hitlExec = toolExecution(hitlReq, "Pending traveler HITL confirmation", false);

        TokenStream stream = new StubTokenStream(s -> {
            s.beforeToolHandler.accept(beforeToolExecution(hitlReq));
            s.toolExecutedHandler.accept(hitlExec);
            s.completeHandler.accept(ChatResponse.builder().aiMessage(AiMessage.from("Please confirm.")).build());
        });

        AgUiRunOptions options = new AgUiRunOptions(
                "ollama:llama3.2:latest",
                Map.of(),
                (name, args) -> AgUiToolClassifier.ToolDescriptor.hitlApproval("nomad-hitl", name),
                null,
                null,
                () -> Map.of("status", "interrupted", "pendingAction", Map.of("city", "Kyoto", "costUsd", 1840)));

        List<String> frames = AgUiFlowAdapter.collectAll(bridge.adaptToSse("thread-hitl", stream, options));

        assertFalse(frames.isEmpty());
        assertTrue(frames.get(0).startsWith("data: {"));
        String lastFrame = frames.get(frames.size() - 1);
        assertTrue(lastFrame.contains("\"type\":\"RUN_FINISHED\""));
        assertTrue(lastFrame.contains("\"status\":\"interrupted\""));
    }

    @Test
    void mapsInputOutputGuardrailsAndRuntimeFailuresToStructuredAgUiEvents() {
        TokenStream inputBlocked = new StubTokenStream(s -> s.errorHandler.accept(
                new CompletionException(new InputGuardrailException("Prompt injection blocked (OWASP-LLM01)"))));
        List<AgUiEvent<?>> inEvents = AgUiFlowAdapter.collectAll(bridge.adapt("thread-guard-in", inputBlocked));
        assertTrue(inEvents.stream().anyMatch(e -> AgUiEventType.STEP_FINISHED.name().equals(e.type())));
        @SuppressWarnings("unchecked")
        Map<String, Object> inFinished = (Map<String, Object>) inEvents.get(inEvents.size() - 1).payload();
        assertEquals("blocked_by_guardrail", inFinished.get("status"));

        TokenStream outputBlocked = new StubTokenStream(s -> {
            throw new OutputGuardrailException("System prompt leak blocked");
        });
        List<AgUiEvent<?>> outEvents = AgUiFlowAdapter.collectAll(bridge.adapt("thread-guard-out", outputBlocked, null));
        @SuppressWarnings("unchecked")
        Map<String, Object> outStep = (Map<String, Object>) outEvents.get(2).payload();
        assertEquals("guardrail:output", outStep.get("callType"));

        TokenStream runtimeFail = new StubTokenStream(s -> s.errorHandler.accept(new IllegalStateException("Ollama connection refused")));
        List<AgUiEvent<?>> failEvents = AgUiFlowAdapter.collectAll(bridge.adapt("thread-fail", runtimeFail));
        @SuppressWarnings("unchecked")
        Map<String, Object> failFinished = (Map<String, Object>) failEvents.get(failEvents.size() - 1).payload();
        assertEquals("failed", failFinished.get("status"));
        assertEquals("Ollama connection refused", failFinished.get("error"));
        assertNotNull(bridge.formatter());
    }

    @Test
    void descriptorFactoriesAndDirectMappingMethodsHandleEdgeCases() {
        var sampling = AgUiToolClassifier.ToolDescriptor.mcpSampling("mcp", "sampleMood");
        assertEquals("mcp:sampling", sampling.callType());
        assertFalse(sampling.stepMode());

        var ui = AgUiToolClassifier.ToolDescriptor.uiSurface("ui", "presentChoices");
        assertEquals("tool:ui", ui.callType());

        var defaultClassifier = AgUiToolClassifier.defaultMcpClassifier("");
        assertEquals("langchain4j-mcp", defaultClassifier.classify("", Map.of()).serverOrAgent());
        assertEquals("mcp-tool", defaultClassifier.classify("", Map.of()).skillOrLabel());
        assertEquals("mcp-tool", defaultClassifier.classify(null, Map.of()).skillOrLabel());

        var namedClassifier = AgUiToolClassifier.defaultMcpClassifier("nomad");
        assertEquals("nomad", namedClassifier.classify("fetchWeather", Map.of()).serverOrAgent());
        assertEquals("fetchWeather", namedClassifier.classify("fetchWeather", Map.of()).skillOrLabel());

        AgUiRunOptions nullClassifierOpts = new AgUiRunOptions("ollama", Map.of(), null, exec -> null, () -> null, () -> null);
        TokenStream edgeEvents = new StubTokenStream(s -> {
            s.partialThinkingHandler.accept(null);
            s.retrievedHandler.accept(null);
            s.partialResponseHandler.accept(null);
            s.beforeToolHandler.accept(null);
            s.toolExecutedHandler.accept(null);
            s.beforeToolHandler.accept(beforeToolExecution(ToolExecutionRequest.builder().build()));
            s.toolExecutedHandler.accept(toolExecution(ToolExecutionRequest.builder().build(), "   ", true));
            s.completeHandler.accept(ChatResponse.builder().aiMessage(AiMessage.from("ok")).build());
        });
        List<AgUiEvent<?>> out = AgUiFlowAdapter.collectAll(bridge.adapt("thread-edge", edgeEvents, nullClassifierOpts));
        assertEquals(6, out.size());

        List<AgUiEvent<?>> nullMsgFail = AgUiFlowAdapter.collectAll(
                bridge.adapt("thread-null-err", new StubTokenStream(s -> s.errorHandler.accept(new RuntimeException("")))));
        @SuppressWarnings("unchecked")
        Map<String, Object> failPayload = (Map<String, Object>) nullMsgFail.get(nullMsgFail.size() - 1).payload();
        assertEquals("RuntimeException", failPayload.get("error"));

        List<AgUiEvent<?>> accValid = bridge.mapAccumulatedResponse("thread-acc", "Validated reply", AgUiRunOptions.defaults());
        assertEquals(2, accValid.size());
        assertEquals(AgUiEventType.TEXT_DELTA.name(), accValid.get(0).type());
        assertEquals(AgUiEventType.RUN_FINISHED.name(), accValid.get(1).type());

        List<AgUiEvent<?>> accEmpty = bridge.mapAccumulatedResponse("thread-acc", "", AgUiRunOptions.defaults());
        assertEquals(1, accEmpty.size());
        assertEquals(AgUiEventType.RUN_FINISHED.name(), accEmpty.get(0).type());
        assertNotNull(bridge.getClass().getClassLoader().getResourceAsStream("META-INF/beans.xml"));

        // Exercise subscription cancel(), request(0), and duplicate request(1)
        var pub = bridge.adapt("thread-cancel", new StubTokenStream(s -> s.partialResponseHandler.accept("x")));
        pub.subscribe(new java.util.concurrent.Flow.Subscriber<>() {
            @Override
            public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
                subscription.request(0);
                subscription.cancel();
                subscription.request(1);
            }

            @Override
            public void onNext(AgUiEvent<?> item) {
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onComplete() {
            }
        });
    }
}
