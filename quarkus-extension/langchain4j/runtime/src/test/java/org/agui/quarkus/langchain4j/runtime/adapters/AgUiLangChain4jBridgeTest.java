package org.agui.quarkus.langchain4j.runtime.adapters;

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
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatEvent;
import io.smallrye.mutiny.Multi;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.agui.core.domain.AgUiEvent;
import org.agui.core.domain.AgUiEventType;
import org.agui.core.domain.TextDeltaPayload;
import org.junit.jupiter.api.Test;

class AgUiLangChain4jBridgeTest {

    private final AgUiLangChain4jBridge bridge = new AgUiLangChain4jBridge();

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

    @Test
    void rejectsNullOrBlankThreadIdAndNullStream() {
        Multi<ChatEvent> empty = Multi.createFrom().empty();
        assertThrows(NullPointerException.class, () -> bridge.adapt(null, empty));
        assertThrows(IllegalArgumentException.class, () -> bridge.adapt("   ", empty));
        assertThrows(NullPointerException.class, () -> bridge.adapt("thread-1", null));
    }

    @Test
    void adaptsFullStreamingTurnWithThinkingMcpToolA2aStepRagAndTextDeltas() {
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

        Multi<ChatEvent> chatStream = Multi.createFrom().items(
                new ChatEvent.PartialThinkingEvent("   "),
                new ChatEvent.PartialThinkingEvent("Checking live Kyoto weather & advisory"),
                new ChatEvent.ContentFetchedEvent(List.of(Content.from("Machiya cedar joinery context"))),
                new ChatEvent.BeforeToolExecutionEvent(mcpReq),
                new ChatEvent.ToolExecutedEvent(mcpExec),
                new ChatEvent.BeforeToolExecutionEvent(a2aReq),
                new ChatEvent.ToolExecutedEvent(a2aExec),
                new ChatEvent.IntermediateResponseEvent(finalResponse),
                new ChatEvent.PartialResponseEvent(""),
                new ChatEvent.PartialResponseEvent("Kyoto is crisp "),
                new ChatEvent.PartialResponseEvent("at 18.4°C."),
                new ChatEvent.ChatCompletedEvent(finalResponse));

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

        List<AgUiEvent<?>> events = bridge.adapt("thread-kyoto", chatStream, options)
                .collect().asList().await().indefinitely();

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

        Multi<ChatEvent> stream = Multi.createFrom().items(
                new ChatEvent.BeforeToolExecutionEvent(mcpErrReq),
                new ChatEvent.ToolExecutedEvent(mcpErrExec),
                new ChatEvent.BeforeToolExecutionEvent(a2aErrReq),
                new ChatEvent.ToolExecutedEvent(a2aErrExec),
                new ChatEvent.ChatCompletedEvent(ChatResponse.builder().aiMessage(AiMessage.from("Service down.")).build()));

        AgUiRunOptions options = new AgUiRunOptions(
                null,
                null,
                (name, args) -> "verifyTravelAdvisory".equals(name)
                        ? AgUiToolClassifier.ToolDescriptor.a2aStep("Nomad-A2A-Specialist", "verify-travel-advisory")
                        : AgUiToolClassifier.ToolDescriptor.mcpTool("nomad-mcp", name),
                null,
                null,
                null);

        List<AgUiEvent<?>> events = bridge.adapt("thread-err", stream, options)
                .collect().asList().await().indefinitely();

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

        Multi<ChatEvent> stream = Multi.createFrom().items(
                new ChatEvent.BeforeToolExecutionEvent(hitlReq),
                new ChatEvent.ToolExecutedEvent(hitlExec),
                new ChatEvent.ChatCompletedEvent(ChatResponse.builder().aiMessage(AiMessage.from("Please confirm.")).build()));

        AgUiRunOptions options = new AgUiRunOptions(
                "ollama:llama3.2:latest",
                Map.of(),
                (name, args) -> AgUiToolClassifier.ToolDescriptor.hitlApproval("nomad-hitl", name),
                null,
                null,
                () -> Map.of("status", "interrupted", "pendingAction", Map.of("city", "Kyoto", "costUsd", 1840)));

        List<String> frames = bridge.adaptToSse("thread-hitl", stream, options)
                .collect().asList().await().indefinitely();

        assertFalse(frames.isEmpty());
        assertTrue(frames.get(0).startsWith("data: {"));
        String lastFrame = frames.get(frames.size() - 1);
        assertTrue(lastFrame.contains("\"type\":\"RUN_FINISHED\""));
        assertTrue(lastFrame.contains("\"status\":\"interrupted\""));
    }

    @Test
    void mapsInputOutputGuardrailsAndRuntimeFailuresToStructuredAgUiEvents() {
        Multi<ChatEvent> inputBlocked = Multi.createFrom().failure(
                new CompletionException(new InputGuardrailException("Prompt injection blocked (OWASP-LLM01)")));
        List<AgUiEvent<?>> inEvents = bridge.adapt("thread-guard-in", inputBlocked)
                .collect().asList().await().indefinitely();
        assertTrue(inEvents.stream().anyMatch(e -> AgUiEventType.STEP_FINISHED.name().equals(e.type())));
        @SuppressWarnings("unchecked")
        Map<String, Object> inFinished = (Map<String, Object>) inEvents.get(inEvents.size() - 1).payload();
        assertEquals("blocked_by_guardrail", inFinished.get("status"));

        Multi<ChatEvent> outputBlocked = Multi.createFrom().failure(
                new OutputGuardrailException("System prompt leak blocked"));
        List<AgUiEvent<?>> outEvents = bridge.adapt("thread-guard-out", outputBlocked, null)
                .collect().asList().await().indefinitely();
        @SuppressWarnings("unchecked")
        Map<String, Object> outStep = (Map<String, Object>) outEvents.get(2).payload();
        assertEquals("guardrail:output", outStep.get("callType"));

        Multi<ChatEvent> runtimeFail = Multi.createFrom().failure(new IllegalStateException("Ollama connection refused"));
        List<AgUiEvent<?>> failEvents = bridge.adapt("thread-fail", runtimeFail)
                .collect().asList().await().indefinitely();
        @SuppressWarnings("unchecked")
        Map<String, Object> failFinished = (Map<String, Object>) failEvents.get(failEvents.size() - 1).payload();
        assertEquals("failed", failFinished.get("status"));
        assertEquals("Ollama connection refused", failFinished.get("error"));
        assertNotNull(bridge.formatter());
    }

    @Test
    void descriptorFactoriesCreateExpectedProtocolMetadata() {
        var sampling = AgUiToolClassifier.ToolDescriptor.mcpSampling("mcp", "sampleMood");
        assertEquals("mcp:sampling", sampling.callType());
        assertFalse(sampling.stepMode());

        var ui = AgUiToolClassifier.ToolDescriptor.uiSurface("ui", "presentChoices");
        assertEquals("tool:ui", ui.callType());

        var defaultClassifier = AgUiToolClassifier.defaultMcpClassifier("");
        assertEquals("quarkus-langchain4j-mcp", defaultClassifier.classify("", Map.of()).serverOrAgent());
        assertEquals("mcp-tool", defaultClassifier.classify("", Map.of()).skillOrLabel());
        assertEquals("mcp-tool", defaultClassifier.classify(null, Map.of()).skillOrLabel());

        var namedClassifier = AgUiToolClassifier.defaultMcpClassifier("nomad");
        assertEquals("nomad", namedClassifier.classify("fetchWeather", Map.of()).serverOrAgent());
        assertEquals("fetchWeather", namedClassifier.classify("fetchWeather", Map.of()).skillOrLabel());

        AgUiRunOptions nullClassifierOpts = new AgUiRunOptions("ollama", Map.of(), null, exec -> null, () -> null, () -> null);
        Multi<ChatEvent> edgeEvents = Multi.createFrom().items(
                new ChatEvent.PartialThinkingEvent(null),
                new ChatEvent.ContentFetchedEvent(null),
                new ChatEvent.PartialResponseEvent(null),
                new ChatEvent.BeforeToolExecutionEvent(ToolExecutionRequest.builder().build()),
                new ChatEvent.ToolExecutedEvent(toolExecution(
                        ToolExecutionRequest.builder().build(),
                        "   ",
                        true)),
                new ChatEvent.ChatCompletedEvent(ChatResponse.builder().aiMessage(AiMessage.from("ok")).build()));
        List<AgUiEvent<?>> out = bridge.adapt("thread-edge", edgeEvents, nullClassifierOpts)
                .collect().asList().await().indefinitely();
        assertEquals(6, out.size());

        List<AgUiEvent<?>> nullMsgFail = bridge.adapt("thread-null-err", Multi.createFrom().failure(new RuntimeException("")))
                .collect().asList().await().indefinitely();
        @SuppressWarnings("unchecked")
        Map<String, Object> failPayload = (Map<String, Object>) nullMsgFail.get(nullMsgFail.size() - 1).payload();
        assertEquals("RuntimeException", failPayload.get("error"));

        List<AgUiEvent<?>> accEvents = bridge.adapt(
                        "thread-acc",
                        Multi.createFrom().items(
                                new ChatEvent.AccumulatedResponseEvent("Validated reply", null),
                                new ChatEvent.AccumulatedResponseEvent("", null)))
                .collect().asList().await().indefinitely();
        assertTrue(accEvents.stream().anyMatch(e -> AgUiEventType.TEXT_DELTA.name().equals(e.type())));
        assertTrue(accEvents.stream().anyMatch(e -> AgUiEventType.RUN_FINISHED.name().equals(e.type())));
    }
}
