package org.agui.quarkus.langchain4j.adapters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agui.community.core.agent.Agent;
import com.agui.community.core.agent.RunAgentInput;
import com.agui.community.core.event.Event;
import com.agui.community.core.event.EventType;
import com.agui.community.core.event.RunErrorEvent;
import com.agui.community.core.event.RunFinishedEvent;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.InputGuardrailException;
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
import org.agui.langchain4j.adapters.AgUiRunOptions;
import org.agui.langchain4j.adapters.AgUiToolClassifier;
import org.junit.jupiter.api.Test;

class AgUiQuarkusLangChain4jBridgeTest {

    @Test
    void adaptsQuarkiverseChatEventStreamsToCanonicalEventsSseJsonAndAgent() {
        AgUiQuarkusLangChain4jBridge bridge = new AgUiQuarkusLangChain4jBridge();
        assertNotNull(bridge.quarkusBridge());
        assertNotNull(bridge.langChain4jBridge());

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
                new ChatEvent.AccumulatedResponseEvent("18C", null),
                new ChatEvent.ChatCompletedEvent(finalResp));

        AgUiRunOptions options = AgUiRunOptions.builder()
                .runId("run-qlc-1")
                .llmMode("ollama:llama3.2:latest")
                .initialStateSnapshot(Map.of("city", "Kyoto"))
                .toolClassifier(AgUiToolClassifier.defaultMcpClassifier("nomad"))
                .build();

        List<Event> events = bridge.adapt("thread-q-lc", stream, options)
                .collect().asList().await().indefinitely();
        assertEquals(List.of(
                        EventType.RUN_STARTED,
                        EventType.STATE_SNAPSHOT,
                        EventType.REASONING_START,
                        EventType.REASONING_MESSAGE_START,
                        EventType.REASONING_MESSAGE_CONTENT,
                        EventType.REASONING_MESSAGE_END,
                        EventType.REASONING_END,
                        EventType.STEP_STARTED,
                        EventType.STEP_FINISHED,
                        EventType.TOOL_CALL_START,
                        EventType.TOOL_CALL_ARGS,
                        EventType.TOOL_CALL_END,
                        EventType.TOOL_CALL_RESULT,
                        EventType.TEXT_MESSAGE_START,
                        EventType.TEXT_MESSAGE_CONTENT,
                        EventType.TEXT_MESSAGE_END,
                        EventType.RUN_FINISHED),
                events.stream().map(Event::type).toList());

        // Re-subscribing to SSE & JSON payload streams produces independent sessions without duplicate RUN_FINISHED
        List<String> sseFrames = bridge.adaptToSse("thread-q-lc", stream, options)
                .collect().asList().await().indefinitely();
        assertEquals(17, sseFrames.size());
        assertTrue(sseFrames.get(0).startsWith("data: {"));
        assertTrue(sseFrames.get(sseFrames.size() - 1).contains("RUN_FINISHED"));

        List<String> jsonPayloads = bridge.adaptToJsonPayloads("thread-q-lc", stream, options)
                .collect().asList().await().indefinitely();
        assertEquals(17, jsonPayloads.size());
        assertTrue(jsonPayloads.get(0).startsWith("{"));

        // Accumulated-only stream without ChatCompletedEvent still closes with RUN_FINISHED
        Multi<ChatEvent> accOnly = Multi.createFrom().item(new ChatEvent.AccumulatedResponseEvent("Full text", null));
        List<Event> accEvents = bridge.adapt("thread-acc-only", accOnly)
                .collect().asList().await().indefinitely();
        assertEquals(List.of(
                        EventType.RUN_STARTED,
                        EventType.TEXT_MESSAGE_START,
                        EventType.TEXT_MESSAGE_CONTENT,
                        EventType.TEXT_MESSAGE_END,
                        EventType.RUN_FINISHED),
                accEvents.stream().map(Event::type).toList());

        // Guardrail failure stream produces canonical RunErrorEvent
        Multi<ChatEvent> failStream = Multi.createFrom().failure(new InputGuardrailException("Blocked"));
        List<Event> failEvents = bridge.adapt("thread-q-fail", failStream, null)
                .collect().asList().await().indefinitely();
        RunErrorEvent errEvt = assertInstanceOf(RunErrorEvent.class, failEvents.get(failEvents.size() - 1));
        assertEquals("GUARDRAIL_BLOCKED", errEvt.code());

        // asAgent adapter
        Agent agent = bridge.asAgent((in, opts) -> accOnly, in -> options);
        RunAgentInput input = new RunAgentInput("thread-agent-1", "run-override-1", List.of(), List.of());
        List<Event> agentEvents = Multi.createFrom().publisher(agent.run(input))
                .collect().asList().await().indefinitely();
        RunFinishedEvent agentFin = assertInstanceOf(RunFinishedEvent.class, agentEvents.get(agentEvents.size() - 1));
        assertEquals("run-override-1", agentFin.runId());

        Agent defaultAgent = bridge.asAgent((in, opts) -> Multi.createFrom().empty(), null);
        List<Event> defEvents = Multi.createFrom().publisher(defaultAgent.run(new RunAgentInput("t-def", "", List.of(), List.of())))
                .collect().asList().await().indefinitely();
        assertEquals(2, defEvents.size());

        assertNotNull(bridge.getClass().getClassLoader().getResourceAsStream("META-INF/beans.xml"));
    }
}
