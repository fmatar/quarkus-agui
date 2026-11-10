package org.agui.langchain4j.adapters;

import com.agui.community.core.event.Event;
import com.agui.community.core.event.JsonPatchOperation;
import com.agui.community.core.event.ReasoningEndEvent;
import com.agui.community.core.event.ReasoningMessageContentEvent;
import com.agui.community.core.event.ReasoningMessageEndEvent;
import com.agui.community.core.event.ReasoningMessageStartEvent;
import com.agui.community.core.event.ReasoningStartEvent;
import com.agui.community.core.event.RunErrorEvent;
import com.agui.community.core.event.RunFinishedEvent;
import com.agui.community.core.event.RunStartedEvent;
import com.agui.community.core.event.StateDeltaEvent;
import com.agui.community.core.event.StateSnapshotEvent;
import com.agui.community.core.event.StepFinishedEvent;
import com.agui.community.core.event.StepStartedEvent;
import com.agui.community.core.event.TextMessageContentEvent;
import com.agui.community.core.event.TextMessageEndEvent;
import com.agui.community.core.event.TextMessageStartEvent;
import com.agui.community.core.event.ToolCallArgsEvent;
import com.agui.community.core.event.ToolCallEndEvent;
import com.agui.community.core.event.ToolCallResultEvent;
import com.agui.community.core.event.ToolCallStartEvent;
import com.agui.community.core.interrupt.Interrupt;
import com.agui.community.core.interrupt.InterruptOutcome;
import com.agui.community.core.interrupt.RunOutcome;
import com.agui.community.core.interrupt.SuccessOutcome;
import com.agui.community.core.message.Role;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.guardrail.InputGuardrailException;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.tool.ToolExecution;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Stateful per-run state machine translating LangChain4j streaming callbacks into
 * well-formed canonical AG-UI {@link Event} lifecycles.
 */
public final class AgUiLangChain4jRunSession {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final String threadId;
    private final AgUiRunOptions options;
    private final ObjectMapper objectMapper;
    private final Deque<String> pendingGeneratedCallIds = new ArrayDeque<>();

    private String activeReasoningId;
    private String activeTextMessageId;
    private boolean streamedAnyText;
    private boolean terminated;

    AgUiLangChain4jRunSession(String threadId, AgUiRunOptions options, ObjectMapper objectMapper) {
        this.threadId = Objects.requireNonNull(threadId, "threadId must not be null");
        this.options = options == null ? AgUiRunOptions.defaults() : options;
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public synchronized List<Event> onStart() {
        long now = System.currentTimeMillis();
        List<Event> out = new ArrayList<>(2);
        out.add(new RunStartedEvent(
                threadId,
                options.runId(),
                null,
                null,
                now,
                Map.of("llmMode", options.llmMode())));
        if (!options.initialStateSnapshot().isEmpty()) {
            out.add(new StateSnapshotEvent(options.initialStateSnapshot(), now, null));
        }
        return List.copyOf(out);
    }

    public synchronized List<Event> onPartialThinking(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        List<Event> out = new ArrayList<>(3);
        closeTextInto(out, now);
        if (activeReasoningId == null) {
            activeReasoningId = "reasoning-" + shortId();
            out.add(new ReasoningStartEvent(activeReasoningId, now, null));
            out.add(new ReasoningMessageStartEvent(activeReasoningId, Role.REASONING, now, null));
        }
        out.add(new ReasoningMessageContentEvent(activeReasoningId, text, now, null));
        return List.copyOf(out);
    }

    public synchronized List<Event> onRetrieved(List<Content> contents) {
        long now = System.currentTimeMillis();
        int count = contents == null ? 0 : contents.size();
        String stepId = "rag-" + shortId();
        String stepName = "rag:cultural-rag-retrieval";
        List<Event> out = new ArrayList<>(4);
        closeOpenStreamsInto(out, now);
        out.add(new StepStartedEvent(stepName, now, Map.of(
                "stepId", stepId,
                "protocol", "RAG",
                "callType", "rag:retrieval",
                "agentName", "LangChain4j-RAG",
                "skillId", "cultural-rag-retrieval",
                "summary", "Retrieving context")));
        out.add(new StepFinishedEvent(stepName, now, Map.of(
                "stepId", stepId,
                "protocol", "RAG",
                "callType", "rag:retrieval",
                "agentName", "LangChain4j-RAG",
                "skillId", "cultural-rag-retrieval",
                "status", "OK",
                "isError", false,
                "summary", "Retrieved " + count + " contextual segment(s)")));
        return List.copyOf(out);
    }

    public synchronized List<Event> onBeforeTool(ToolExecutionRequest req) {
        long now = System.currentTimeMillis();
        List<Event> out = new ArrayList<>(5);
        closeOpenStreamsInto(out, now);

        String callId = acquireBeforeCallId(req.id());
        String toolName = resolveToolName(req.name());
        Map<String, Object> args = parseJsonMapOrWrap(req.arguments(), "rawArguments");
        AgUiToolClassifier.ToolDescriptor desc = options.toolClassifier().classify(toolName, args);

        if (desc.stepMode()) {
            String stepName = "a2a:" + desc.serverOrAgent() + ":" + desc.skillOrLabel();
            out.add(new StepStartedEvent(stepName, now, Map.of(
                    "stepId", callId,
                    "protocol", "A2A",
                    "callType", desc.callType(),
                    "agentName", desc.serverOrAgent(),
                    "skillId", desc.skillOrLabel(),
                    "args", args,
                    "summary", "Delegating " + desc.skillOrLabel())));
            return List.copyOf(out);
        }

        Map<String, Object> startMeta = new LinkedHashMap<>();
        startMeta.put("callId", callId);
        startMeta.put("tool", toolName);
        startMeta.put("protocol", resolveProtocolLabel(desc.callType()));
        startMeta.put("callType", desc.callType());
        startMeta.put("server", desc.serverOrAgent());
        startMeta.put("requiresApproval", desc.requiresApproval());
        startMeta.put("args", args);

        String argsJson = (req.arguments() == null || req.arguments().isBlank()) ? "{}" : req.arguments();
        out.add(new ToolCallStartEvent(callId, toolName, null, now, Map.copyOf(startMeta)));
        out.add(new ToolCallArgsEvent(callId, argsJson, now, null));
        out.add(new ToolCallEndEvent(callId, now, null));
        return List.copyOf(out);
    }

    public synchronized List<Event> onToolExecuted(ToolExecution exec) {
        long now = System.currentTimeMillis();
        List<Event> out = new ArrayList<>(4);
        closeOpenStreamsInto(out, now);

        ToolExecutionRequest req = exec.request();
        String callId = acquireAfterCallId(req.id());
        String toolName = resolveToolName(req.name());
        Map<String, Object> args = parseJsonMapOrWrap(req.arguments(), "rawArguments");
        AgUiToolClassifier.ToolDescriptor desc = options.toolClassifier().classify(toolName, args);

        Map<String, Object> resultData = normalizeResultMap(exec);
        boolean isError = Boolean.TRUE.equals(resultData.get("isError"));
        String errorDetail = isError ? extractErrorMessage(resultData, exec.result()) : null;

        if (desc.stepMode()) {
            out.add(buildStepFinishedEvent(callId, desc, resultData, isError, errorDetail, now));
        } else {
            out.add(buildToolResultEvent(callId, toolName, desc, resultData, isError, errorDetail, now));
        }
        appendStateDeltaIfPresent(out, options.postToolStatePatchProvider() == null
                ? null
                : options.postToolStatePatchProvider().apply(exec), now);
        return List.copyOf(out);
    }

    public synchronized List<Event> onPartialResponse(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        List<Event> out = new ArrayList<>(4);
        closeReasoningInto(out, now);
        if (activeTextMessageId == null) {
            activeTextMessageId = "msg-" + shortId();
            out.add(new TextMessageStartEvent(activeTextMessageId, Role.ASSISTANT, now, null));
        }
        streamedAnyText = true;
        out.add(new TextMessageContentEvent(activeTextMessageId, chunk, now, null));
        return List.copyOf(out);
    }

    public synchronized List<Event> onAccumulatedResponse(String message) {
        if (streamedAnyText || message == null || message.isEmpty()) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        List<Event> out = new ArrayList<>(5);
        out.addAll(onPartialResponse(message));
        closeTextInto(out, now);
        return List.copyOf(out);
    }

    public synchronized List<Event> onComplete() {
        if (terminated) {
            return List.of();
        }
        terminated = true;
        long now = System.currentTimeMillis();
        List<Event> out = new ArrayList<>(5);
        closeOpenStreamsInto(out, now);
        appendStateDeltaIfPresent(out, options.postRunStatePatchProvider() == null
                ? null
                : options.postRunStatePatchProvider().get(), now);

        RunOutcome outcome = resolveRunOutcome();
        Object result = options.runResultSupplier() != null ? options.runResultSupplier().get() : null;
        out.add(new RunFinishedEvent(
                threadId,
                options.runId(),
                outcome,
                result,
                now,
                Map.of("llmMode", options.llmMode())));
        return List.copyOf(out);
    }

    public synchronized List<Event> onError(Throwable failure) {
        if (terminated) {
            return List.of();
        }
        terminated = true;
        long now = System.currentTimeMillis();
        List<Event> out = new ArrayList<>(6);
        closeOpenStreamsInto(out, now);

        Throwable root = unwrapCause(failure);
        String message = (root.getMessage() == null || root.getMessage().isBlank())
                ? root.getClass().getSimpleName()
                : root.getMessage();

        if (root instanceof InputGuardrailException || root instanceof OutputGuardrailException) {
            appendGuardrailEvents(out, root instanceof InputGuardrailException, message, now);
            return List.copyOf(out);
        }

        out.add(new RunErrorEvent(
                message,
                "UPSTREAM_ERROR",
                now,
                Map.of("exception", root.getClass().getSimpleName())));
        return List.copyOf(out);
    }

    private void appendGuardrailEvents(List<Event> out, boolean isInput, String message, long now) {
        String callType = isInput ? "guardrail:input" : "guardrail:output";
        String skillId = isInput ? "input-safety-guardrail" : "output-honesty-guardrail";
        String stepId = "guardrail-" + shortId();
        String stepName = "guardrail:" + skillId;
        out.add(new StepStartedEvent(stepName, now, Map.of(
                "stepId", stepId,
                "protocol", "GUARDRAIL",
                "callType", callType,
                "agentName", "LangChain4j-Guardrail",
                "skillId", skillId,
                "summary", "Evaluating guardrail")));
        out.add(new StepFinishedEvent(stepName, now, Map.of(
                "stepId", stepId,
                "protocol", "GUARDRAIL",
                "callType", callType,
                "agentName", "LangChain4j-Guardrail",
                "skillId", skillId,
                "status", "ERROR",
                "isError", true,
                "rule", "OWASP-LLM01",
                "error", message,
                "summary", message)));
        out.add(new RunErrorEvent(message, "GUARDRAIL_BLOCKED", now, Map.of(
                "protocol", "GUARDRAIL",
                "callType", callType,
                "skillId", skillId,
                "rule", "OWASP-LLM01")));
    }

    private RunOutcome resolveRunOutcome() {
        if (options.interruptSupplier() != null) {
            List<Interrupt> interrupts = options.interruptSupplier().get();
            if (interrupts != null && !interrupts.isEmpty()) {
                return new InterruptOutcome(List.copyOf(interrupts));
            }
        }
        return new SuccessOutcome();
    }

    private void closeOpenStreamsInto(List<Event> out, long now) {
        closeReasoningInto(out, now);
        closeTextInto(out, now);
    }

    private void closeReasoningInto(List<Event> out, long now) {
        if (activeReasoningId != null) {
            out.add(new ReasoningMessageEndEvent(activeReasoningId, now, null));
            out.add(new ReasoningEndEvent(activeReasoningId, now, null));
            activeReasoningId = null;
        }
    }

    private void closeTextInto(List<Event> out, long now) {
        if (activeTextMessageId != null) {
            out.add(new TextMessageEndEvent(activeTextMessageId, now, null));
            activeTextMessageId = null;
        }
    }

    private static void appendStateDeltaIfPresent(List<Event> out, List<JsonPatchOperation> patches, long now) {
        if (patches != null && !patches.isEmpty()) {
            out.add(new StateDeltaEvent(List.copyOf(patches), now, null));
        }
    }

    private String acquireBeforeCallId(String rawId) {
        if (rawId != null && !rawId.isBlank()) {
            return rawId;
        }
        String generated = "call-" + shortId();
        pendingGeneratedCallIds.addLast(generated);
        return generated;
    }

    private String acquireAfterCallId(String rawId) {
        if (rawId != null && !rawId.isBlank()) {
            return rawId;
        }
        String pending = pendingGeneratedCallIds.pollFirst();
        return pending != null ? pending : "call-" + shortId();
    }

    private Map<String, Object> normalizeResultMap(ToolExecution exec) {
        Map<String, Object> resultData = new LinkedHashMap<>(parseJsonMapOrWrap(exec.result(), "output"));
        boolean isError = exec.hasFailed() || hasErrorFlag(resultData);
        if (isError) {
            resultData.put("status", "ERROR");
            resultData.put("isError", true);
        } else {
            resultData.putIfAbsent("status", "OK");
            resultData.putIfAbsent("isError", false);
        }
        return resultData;
    }

    private Event buildStepFinishedEvent(
            String stepId,
            AgUiToolClassifier.ToolDescriptor desc,
            Map<String, Object> report,
            boolean isError,
            String errorDetail,
            long now) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("stepId", stepId);
        meta.put("protocol", "A2A");
        meta.put("callType", desc.callType());
        meta.put("agentName", desc.serverOrAgent());
        meta.put("skillId", desc.skillOrLabel());
        meta.put("status", isError ? "ERROR" : "OK");
        meta.put("isError", isError);
        String summary = report.get("summary") instanceof String s && !s.isBlank()
                ? s
                : (isError ? errorDetail : "Completed " + desc.skillOrLabel());
        meta.put("summary", summary);
        if (isError && errorDetail != null) {
            meta.put("error", errorDetail);
        }
        meta.put("report", report);
        String stepName = "a2a:" + desc.serverOrAgent() + ":" + desc.skillOrLabel();
        return new StepFinishedEvent(stepName, now, Map.copyOf(meta));
    }

    private Event buildToolResultEvent(
            String callId,
            String toolName,
            AgUiToolClassifier.ToolDescriptor desc,
            Map<String, Object> resultData,
            boolean isError,
            String errorDetail,
            long now) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("callId", callId);
        meta.put("tool", toolName);
        meta.put("protocol", resolveProtocolLabel(desc.callType()));
        meta.put("callType", desc.callType());
        meta.put("server", desc.serverOrAgent());
        meta.put("status", isError ? "ERROR" : "OK");
        meta.put("isError", isError);
        if (isError && errorDetail != null) {
            meta.put("error", errorDetail);
        }
        meta.put("result", resultData);
        return new ToolCallResultEvent(
                "tool-msg-" + callId,
                callId,
                serializeJson(resultData),
                Role.TOOL,
                now,
                Map.copyOf(meta));
    }

    private String serializeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
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

    private static String resolveProtocolLabel(String callType) {
        if (callType.startsWith("mcp:")) {
            return "MCP";
        }
        if (callType.startsWith("hitl:")) {
            return "HITL";
        }
        return "AG-UI";
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

    private static String resolveToolName(String name) {
        return (name == null || name.isBlank()) ? "mcp-tool" : name;
    }

    private static Throwable unwrapCause(Throwable throwable) {
        Throwable curr = throwable;
        while (curr.getCause() != null
                && !(curr instanceof InputGuardrailException)
                && !(curr instanceof OutputGuardrailException)) {
            curr = curr.getCause();
        }
        return curr;
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
