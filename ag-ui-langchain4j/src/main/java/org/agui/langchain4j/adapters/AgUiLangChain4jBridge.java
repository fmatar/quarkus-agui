package org.agui.langchain4j.adapters;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.guardrail.InputGuardrailException;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import org.agui.core.adapters.AgUiSseFormatter;
import org.agui.core.domain.AgUiEvent;
import org.agui.core.domain.AgUiEventType;
import org.agui.core.domain.TextDeltaPayload;

/**
 * Framework-agnostic LangChain4j adapter converting upstream {@link TokenStream} and tool/RAG/guardrail
 * lifecycle events into {@link Flow.Publisher}{@code <AgUiEvent<?>>} and SSE wire frames with honest error surfacing.
 */
@ApplicationScoped
public class AgUiLangChain4jBridge {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final AgUiSseFormatter formatter = new AgUiSseFormatter();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Flow.Publisher<AgUiEvent<?>> adapt(String threadId, TokenStream tokenStream) {
        return adapt(threadId, tokenStream, AgUiRunOptions.defaults());
    }

    public Flow.Publisher<AgUiEvent<?>> adapt(String threadId, TokenStream tokenStream, AgUiRunOptions options) {
        validateInputs(threadId, tokenStream);
        AgUiRunOptions resolved = options == null ? AgUiRunOptions.defaults() : options;

        return subscriber -> {
            Objects.requireNonNull(subscriber, "subscriber must not be null");
            AtomicBoolean started = new AtomicBoolean(false);
            AtomicBoolean cancelled = new AtomicBoolean(false);

            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                    if (cancelled.get() || n <= 0 || !started.compareAndSet(false, true)) {
                        return;
                    }
                    emitAll(subscriber, cancelled, buildInitialEvents(threadId, resolved));
                    tokenStream.onPartialThinking(thinking -> {
                        if (thinking != null) {
                            emitAll(subscriber, cancelled, mapThinkingText(threadId, thinking.text()));
                        }
                    });
                    tokenStream.onRetrieved(contents -> emitAll(subscriber, cancelled, mapRagContents(threadId, contents)));
                    tokenStream.beforeToolExecution(before -> {
                        if (before != null && before.request() != null) {
                            emitAll(subscriber, cancelled, mapBeforeToolRequest(threadId, before.request(), resolved));
                        }
                    });
                    tokenStream.onToolExecuted(exec -> {
                        if (exec != null) {
                            emitAll(subscriber, cancelled, mapToolExecution(threadId, exec, resolved));
                        }
                    });
                    tokenStream.onPartialResponse(chunk -> emitAll(subscriber, cancelled, mapPartialResponseChunk(threadId, chunk)));
                    tokenStream.onCompleteResponse(resp -> {
                        emitAll(subscriber, cancelled, mapCompleted(threadId, resolved));
                        if (!cancelled.getAndSet(true)) {
                            subscriber.onComplete();
                        }
                    });
                    tokenStream.onError(failure -> {
                        emitAll(subscriber, cancelled, buildFailureEvents(threadId, failure));
                        if (!cancelled.getAndSet(true)) {
                            subscriber.onComplete();
                        }
                    });
                    try {
                        tokenStream.start();
                    } catch (Throwable failure) {
                        emitAll(subscriber, cancelled, buildFailureEvents(threadId, failure));
                        if (!cancelled.getAndSet(true)) {
                            subscriber.onComplete();
                        }
                    }
                }

                @Override
                public void cancel() {
                    cancelled.set(true);
                }
            });
        };
    }

    public Flow.Publisher<String> adaptToSse(String threadId, TokenStream tokenStream, AgUiRunOptions options) {
        Flow.Publisher<AgUiEvent<?>> eventPub = adapt(threadId, tokenStream, options);
        return subscriber -> eventPub.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscriber.onSubscribe(subscription);
            }

            @Override
            public void onNext(AgUiEvent<?> item) {
                subscriber.onNext(formatter.formatSse(item));
            }

            @Override
            public void onError(Throwable throwable) {
                subscriber.onError(throwable);
            }

            @Override
            public void onComplete() {
                subscriber.onComplete();
            }
        });
    }

    public AgUiSseFormatter formatter() {
        return formatter;
    }

    public static void validateInputs(String threadId, Object stream) {
        Objects.requireNonNull(threadId, "threadId must not be null");
        Objects.requireNonNull(stream, "stream must not be null");
        if (threadId.isBlank()) {
            throw new IllegalArgumentException("threadId must not be blank");
        }
    }

    public List<AgUiEvent<?>> buildInitialEvents(String threadId, AgUiRunOptions options) {
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

    public List<AgUiEvent<?>> mapThinkingText(String threadId, String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return List.of(AgUiEvent.of(
                AgUiEventType.STATUS,
                threadId,
                Map.of("phase", "reasoning", "message", text, "thought", text)));
    }

    public List<AgUiEvent<?>> mapRagContents(String threadId, List<Content> contents) {
        int count = contents == null ? 0 : contents.size();
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

    public List<AgUiEvent<?>> mapBeforeToolRequest(
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

    public List<AgUiEvent<?>> mapToolExecution(
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

    public List<AgUiEvent<?>> mapPartialResponseChunk(String threadId, String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return List.of();
        }
        return List.of(AgUiEvent.of(AgUiEventType.TEXT_DELTA, threadId, new TextDeltaPayload(chunk)));
    }

    public List<AgUiEvent<?>> mapAccumulatedResponse(
            String threadId,
            String message,
            AgUiRunOptions options) {
        List<AgUiEvent<?>> out = new ArrayList<>(3);
        if (message != null && !message.isEmpty()) {
            out.add(AgUiEvent.of(AgUiEventType.TEXT_DELTA, threadId, new TextDeltaPayload(message)));
        }
        out.addAll(mapCompleted(threadId, options));
        return out;
    }

    public List<AgUiEvent<?>> mapCompleted(String threadId, AgUiRunOptions options) {
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

    public List<AgUiEvent<?>> buildFailureEvents(String threadId, Throwable failure) {
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

    private static void emitAll(
            Flow.Subscriber<? super AgUiEvent<?>> subscriber,
            AtomicBoolean cancelled,
            List<AgUiEvent<?>> events) {
        for (AgUiEvent<?> event : events) {
            if (!cancelled.get()) {
                subscriber.onNext(event);
            }
        }
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
