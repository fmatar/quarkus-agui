package org.agui.quarkus.langchain4j.runtime.adapters;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.guardrail.InputGuardrailException;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.service.tool.ToolExecution;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatEvent;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.agui.core.adapters.AgUiSseFormatter;
import org.agui.core.domain.AgUiEvent;
import org.agui.core.domain.AgUiEventType;
import org.agui.core.domain.TextDeltaPayload;

/**
 * Reactive Quarkus LangChain4j adapter converting {@link Multi}{@code <ChatEvent>} streams
 * into {@link Multi}{@code <AgUiEvent<?>>} and SSE wire frames with honest upstream error surfacing.
 */
@ApplicationScoped
public class AgUiLangChain4jBridge {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final AgUiSseFormatter formatter = new AgUiSseFormatter();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Multi<AgUiEvent<?>> adapt(String threadId, Multi<ChatEvent> chatEvents) {
        return adapt(threadId, chatEvents, AgUiRunOptions.defaults());
    }

    public Multi<AgUiEvent<?>> adapt(String threadId, Multi<ChatEvent> chatEvents, AgUiRunOptions options) {
        validateInputs(threadId, chatEvents);
        AgUiRunOptions resolved = options == null ? AgUiRunOptions.defaults() : options;

        Multi<AgUiEvent<?>> headerEvents = Multi.createFrom().iterable(buildInitialEvents(threadId, resolved));
        Multi<AgUiEvent<?>> bodyEvents = chatEvents
                .onItem().transformToIterable(event -> mapChatEvent(threadId, event, resolved))
                .onFailure().recoverWithMulti(failure -> Multi.createFrom().iterable(buildFailureEvents(threadId, failure)));

        return Multi.createBy().concatenating().streams(headerEvents, bodyEvents);
    }

    public Multi<String> adaptToSse(String threadId, Multi<ChatEvent> chatEvents, AgUiRunOptions options) {
        return adapt(threadId, chatEvents, options).map(formatter::formatSse);
    }

    public AgUiSseFormatter formatter() {
        return formatter;
    }

    private static void validateInputs(String threadId, Multi<ChatEvent> chatEvents) {
        Objects.requireNonNull(threadId, "threadId must not be null");
        Objects.requireNonNull(chatEvents, "chatEvents must not be null");
        if (threadId.isBlank()) {
            throw new IllegalArgumentException("threadId must not be blank");
        }
    }

    private List<AgUiEvent<?>> buildInitialEvents(String threadId, AgUiRunOptions options) {
        List<AgUiEvent<?>> initial = new ArrayList<>(2);
        initial.add(AgUiEvent.of(
                AgUiEventType.RUN_STARTED,
                threadId,
                Map.of("threadId", threadId, "status", "started", "llmMode", options.llmMode())));
        if (!options.initialStateSnapshot().isEmpty()) {
            initial.add(AgUiEvent.of(AgUiEventType.STATE_SNAPSHOT, threadId, options.initialStateSnapshot()));
        }
        return initial;
    }

    private List<AgUiEvent<?>> mapChatEvent(String threadId, ChatEvent event, AgUiRunOptions options) {
        if (event instanceof ChatEvent.PartialThinkingEvent thinking) {
            return mapThinkingEvent(threadId, thinking);
        }
        if (event instanceof ChatEvent.ContentFetchedEvent rag) {
            return mapRagEvent(threadId, rag);
        }
        if (event instanceof ChatEvent.BeforeToolExecutionEvent beforeTool) {
            return mapBeforeToolEvent(threadId, beforeTool.getRequest(), options);
        }
        if (event instanceof ChatEvent.ToolExecutedEvent afterTool) {
            return mapToolExecutedEvent(threadId, afterTool.getExecution(), options);
        }
        if (event instanceof ChatEvent.PartialResponseEvent partial) {
            return mapPartialResponseEvent(threadId, partial);
        }
        if (event instanceof ChatEvent.AccumulatedResponseEvent accumulated) {
            return mapAccumulatedResponseEvent(threadId, accumulated, options);
        }
        if (event instanceof ChatEvent.ChatCompletedEvent) {
            return mapCompletedEvent(threadId, options);
        }
        return List.of();
    }

    private List<AgUiEvent<?>> mapThinkingEvent(String threadId, ChatEvent.PartialThinkingEvent thinking) {
        String text = thinking.getText();
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return List.of(AgUiEvent.of(
                AgUiEventType.STATUS,
                threadId,
                Map.of("phase", "reasoning", "message", text, "thought", text)));
    }

    private List<AgUiEvent<?>> mapRagEvent(String threadId, ChatEvent.ContentFetchedEvent rag) {
        int count = rag.getContent() == null ? 0 : rag.getContent().size();
        String stepId = "rag-" + UUID.randomUUID().toString().substring(0, 8);
        String summary = "Retrieved " + count + " contextual segment(s)";
        return List.of(
                AgUiEvent.of(AgUiEventType.STEP_STARTED, threadId, Map.of(
                        "stepId", stepId,
                        "protocol", "RAG",
                        "callType", "rag:retrieval",
                        "agentName", "LangChain4j-RAG",
                        "skillId", "cultural-rag-retrieval",
                        "summary", "Retrieving context")),
                AgUiEvent.of(AgUiEventType.STEP_FINISHED, threadId, Map.of(
                        "stepId", stepId,
                        "protocol", "RAG",
                        "callType", "rag:retrieval",
                        "agentName", "LangChain4j-RAG",
                        "skillId", "cultural-rag-retrieval",
                        "status", "OK",
                        "isError", false,
                        "summary", summary)));
    }

    private List<AgUiEvent<?>> mapBeforeToolEvent(
            String threadId,
            ToolExecutionRequest req,
            AgUiRunOptions options) {
        String callId = resolveCallId(req.id());
        String toolName = resolveToolName(req.name());
        Map<String, Object> args = parseJsonMapOrWrap(req.arguments(), "rawArguments");
        AgUiToolClassifier.ToolDescriptor desc = options.toolClassifier().classify(toolName, args);

        if (desc.stepMode()) {
            return List.of(AgUiEvent.of(AgUiEventType.STEP_STARTED, threadId, Map.of(
                    "stepId", callId,
                    "protocol", "A2A",
                    "callType", desc.callType(),
                    "agentName", desc.serverOrAgent(),
                    "skillId", desc.skillOrLabel(),
                    "args", args,
                    "summary", "Delegating " + desc.skillOrLabel())));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("callId", callId);
        payload.put("tool", toolName);
        payload.put("callType", desc.callType());
        payload.put("server", desc.serverOrAgent());
        payload.put("requiresApproval", desc.requiresApproval());
        payload.put("args", args);
        return List.of(AgUiEvent.of(AgUiEventType.TOOL_CALL, threadId, payload));
    }

    private List<AgUiEvent<?>> mapToolExecutedEvent(
            String threadId,
            ToolExecution exec,
            AgUiRunOptions options) {
        ToolExecutionRequest req = exec.request();
        String callId = resolveCallId(req.id());
        String toolName = resolveToolName(req.name());
        Map<String, Object> args = parseJsonMapOrWrap(req.arguments(), "rawArguments");
        AgUiToolClassifier.ToolDescriptor desc = options.toolClassifier().classify(toolName, args);

        Map<String, Object> resultData = new LinkedHashMap<>(parseJsonMapOrWrap(exec.result(), "output"));
        boolean isError = exec.hasFailed() || hasErrorFlag(resultData);
        if (isError) {
            resultData.put("status", "ERROR");
            resultData.put("isError", true);
        } else {
            resultData.putIfAbsent("status", "OK");
            resultData.putIfAbsent("isError", false);
        }
        String errorDetail = isError ? extractErrorMessage(resultData, exec.result()) : null;

        List<AgUiEvent<?>> out = new ArrayList<>(2);
        if (desc.stepMode()) {
            out.add(buildStepFinishedEvent(threadId, callId, desc, resultData, isError, errorDetail));
        } else {
            out.add(buildToolResultEvent(threadId, callId, toolName, desc, resultData, isError, errorDetail));
        }

        if (options.postToolStateDeltaProvider() != null) {
            Map<String, Object> delta = options.postToolStateDeltaProvider().apply(exec);
            if (delta != null && !delta.isEmpty()) {
                out.add(AgUiEvent.of(AgUiEventType.STATE_DELTA, threadId, delta));
            }
        }
        return out;
    }

    private AgUiEvent<?> buildStepFinishedEvent(
            String threadId,
            String stepId,
            AgUiToolClassifier.ToolDescriptor desc,
            Map<String, Object> report,
            boolean isError,
            String errorDetail) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stepId", stepId);
        payload.put("protocol", "A2A");
        payload.put("callType", desc.callType());
        payload.put("agentName", desc.serverOrAgent());
        payload.put("skillId", desc.skillOrLabel());
        payload.put("status", isError ? "ERROR" : "OK");
        payload.put("isError", isError);
        String summary = report.get("summary") instanceof String s && !s.isBlank()
                ? s
                : (isError ? errorDetail : "Completed " + desc.skillOrLabel());
        payload.put("summary", summary);
        if (isError && errorDetail != null) {
            payload.put("error", errorDetail);
        }
        payload.put("report", report);
        return AgUiEvent.of(AgUiEventType.STEP_FINISHED, threadId, payload);
    }

    private AgUiEvent<?> buildToolResultEvent(
            String threadId,
            String callId,
            String toolName,
            AgUiToolClassifier.ToolDescriptor desc,
            Map<String, Object> resultData,
            boolean isError,
            String errorDetail) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("callId", callId);
        payload.put("tool", toolName);
        payload.put("callType", desc.callType());
        payload.put("status", isError ? "ERROR" : "OK");
        payload.put("isError", isError);
        if (isError && errorDetail != null) {
            payload.put("error", errorDetail);
        }
        payload.put("result", resultData);
        return AgUiEvent.of(AgUiEventType.TOOL_RESULT, threadId, payload);
    }

    private List<AgUiEvent<?>> mapPartialResponseEvent(String threadId, ChatEvent.PartialResponseEvent partial) {
        String chunk = partial.getChunk();
        if (chunk == null || chunk.isEmpty()) {
            return List.of();
        }
        return List.of(AgUiEvent.of(AgUiEventType.TEXT_DELTA, threadId, new TextDeltaPayload(chunk)));
    }

    private List<AgUiEvent<?>> mapAccumulatedResponseEvent(
            String threadId,
            ChatEvent.AccumulatedResponseEvent accumulated,
            AgUiRunOptions options) {
        List<AgUiEvent<?>> out = new ArrayList<>(3);
        String msg = accumulated.getMessage();
        if (msg != null && !msg.isEmpty()) {
            out.add(AgUiEvent.of(AgUiEventType.TEXT_DELTA, threadId, new TextDeltaPayload(msg)));
        }
        out.addAll(mapCompletedEvent(threadId, options));
        return out;
    }

    private List<AgUiEvent<?>> mapCompletedEvent(String threadId, AgUiRunOptions options) {
        List<AgUiEvent<?>> out = new ArrayList<>(2);
        if (options.postRunStateDeltaProvider() != null) {
            Map<String, Object> delta = options.postRunStateDeltaProvider().get();
            if (delta != null && !delta.isEmpty()) {
                out.add(AgUiEvent.of(AgUiEventType.STATE_DELTA, threadId, delta));
            }
        }
        Map<String, Object> finishedPayload = new LinkedHashMap<>();
        finishedPayload.put("threadId", threadId);
        finishedPayload.put("status", "completed");
        finishedPayload.put("llmMode", options.llmMode());
        if (options.runFinishedPayloadCustomizer() != null) {
            Map<String, Object> custom = options.runFinishedPayloadCustomizer().get();
            if (custom != null) {
                finishedPayload.putAll(custom);
            }
        }
        out.add(AgUiEvent.of(AgUiEventType.RUN_FINISHED, threadId, finishedPayload));
        return out;
    }

    private List<AgUiEvent<?>> buildFailureEvents(String threadId, Throwable failure) {
        Throwable root = unwrapCause(failure);
        String message = (root.getMessage() == null || root.getMessage().isBlank())
                ? root.getClass().getSimpleName()
                : root.getMessage();

        if (root instanceof InputGuardrailException || root instanceof OutputGuardrailException) {
            boolean isInput = root instanceof InputGuardrailException;
            String callType = isInput ? "guardrail:input" : "guardrail:output";
            String skillId = isInput ? "input-safety-guardrail" : "output-honesty-guardrail";
            String stepId = "guardrail-" + UUID.randomUUID().toString().substring(0, 8);
            return List.of(
                    AgUiEvent.of(AgUiEventType.STEP_STARTED, threadId, Map.of(
                            "stepId", stepId,
                            "protocol", "GUARDRAIL",
                            "callType", callType,
                            "agentName", "LangChain4j-Guardrail",
                            "skillId", skillId,
                            "summary", "Evaluating guardrail")),
                    AgUiEvent.of(AgUiEventType.STEP_FINISHED, threadId, Map.of(
                            "stepId", stepId,
                            "protocol", "GUARDRAIL",
                            "callType", callType,
                            "agentName", "LangChain4j-Guardrail",
                            "skillId", skillId,
                            "status", "ERROR",
                            "isError", true,
                            "rule", "OWASP-LLM01",
                            "error", message,
                            "summary", message)),
                    AgUiEvent.of(AgUiEventType.TEXT_DELTA, threadId, new TextDeltaPayload(
                            "Request blocked by safety guardrail: " + message)),
                    AgUiEvent.of(AgUiEventType.RUN_FINISHED, threadId, Map.of(
                            "threadId", threadId,
                            "status", "blocked_by_guardrail",
                            "isError", true,
                            "error", message)));
        }

        return List.of(
                AgUiEvent.of(AgUiEventType.STATUS, threadId, Map.of(
                        "phase", "error",
                        "message", message)),
                AgUiEvent.of(AgUiEventType.TEXT_DELTA, threadId, new TextDeltaPayload(
                        "Upstream AI or tool service unavailable: " + message)),
                AgUiEvent.of(AgUiEventType.RUN_FINISHED, threadId, Map.of(
                        "threadId", threadId,
                        "status", "failed",
                        "isError", true,
                        "error", message)));
    }

    private Map<String, Object> parseJsonMapOrWrap(String raw, String fallbackKey) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("{")) {
            try {
                return objectMapper.readValue(trimmed, MAP_TYPE);
            } catch (Exception ignored) {
                return Map.of(fallbackKey, raw);
            }
        }
        return Map.of(fallbackKey, raw);
    }

    private static boolean hasErrorFlag(Map<String, Object> map) {
        if (Boolean.TRUE.equals(map.get("isError"))) {
            return true;
        }
        return map.get("status") instanceof String status && "ERROR".equalsIgnoreCase(status);
    }

    private static String extractErrorMessage(Map<String, Object> map, String rawFallback) {
        if (map.get("error") instanceof String err && !err.isBlank()) {
            return err;
        }
        if (map.get("message") instanceof String msg && !msg.isBlank()) {
            return msg;
        }
        if (rawFallback != null && !rawFallback.isBlank()) {
            return rawFallback;
        }
        return "Tool execution failed";
    }

    private static String resolveCallId(String id) {
        return (id == null || id.isBlank()) ? "call-" + UUID.randomUUID().toString().substring(0, 8) : id;
    }

    private static String resolveToolName(String name) {
        return (name == null || name.isBlank()) ? "mcp-tool" : name;
    }

    private static Throwable unwrapCause(Throwable throwable) {
        Throwable curr = throwable;
        while (curr.getCause() != null && !(curr instanceof InputGuardrailException) && !(curr instanceof OutputGuardrailException)) {
            curr = curr.getCause();
        }
        return curr;
    }
}
