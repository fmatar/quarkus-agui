package org.agui.showcase.legacy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.guardrail.InputGuardrailException;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.rag.content.Content;
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
import org.agui.langchain4j.adapters.AgUiToolClassifier;

/**
 * Temporary Wave 1-2 compatibility bridge keeping the Nomad showcase green until Wave 3 cuts over
 * to {@code ag-ui-quarkus-langchain4j} and canonical {@code com.agui.community.core.event.Event}.
 */
@Deprecated(forRemoval = true)
@ApplicationScoped
public class LegacyQuarkusChatBridge {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final AgUiSseFormatter formatter = new AgUiSseFormatter();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgUiSseFormatter formatter() {
        return formatter;
    }

    public Multi<AgUiEvent<?>> adapt(String threadId, Multi<ChatEvent> chatEvents, LegacyRunOptions options) {
        Objects.requireNonNull(threadId, "threadId must not be null");
        Objects.requireNonNull(chatEvents, "chatEvents must not be null");
        LegacyRunOptions resolved = options == null ? LegacyRunOptions.defaults() : options;

        List<AgUiEvent<?>> initial = new ArrayList<>(2);
        initial.add(AgUiEvent.of(
                AgUiEventType.RUN_STARTED,
                threadId,
                Map.of("threadId", threadId, "status", "started", "llmMode", resolved.llmMode())));
        if (!resolved.initialStateSnapshot().isEmpty()) {
            initial.add(AgUiEvent.of(AgUiEventType.STATE_SNAPSHOT, threadId, resolved.initialStateSnapshot()));
        }

        Multi<AgUiEvent<?>> header = Multi.createFrom().iterable(initial);
        Multi<AgUiEvent<?>> body = chatEvents
                .onItem().transformToIterable(evt -> mapChatEvent(threadId, evt, resolved))
                .onFailure().recoverWithMulti(err -> Multi.createFrom().iterable(buildFailureEvents(threadId, err)));
        return Multi.createBy().concatenating().streams(header, body);
    }

    public Multi<String> adaptToSse(String threadId, Multi<ChatEvent> chatEvents, LegacyRunOptions options) {
        return adapt(threadId, chatEvents, options).map(formatter::formatSse);
    }

    private List<AgUiEvent<?>> mapChatEvent(String threadId, ChatEvent event, LegacyRunOptions options) {
        if (event instanceof ChatEvent.PartialThinkingEvent thinking) {
            String text = thinking.getText();
            return (text == null || text.isBlank())
                    ? List.of()
                    : List.of(AgUiEvent.of(AgUiEventType.STATUS, threadId, Map.of("phase", "reasoning", "message", text, "thought", text)));
        }
        if (event instanceof ChatEvent.ContentFetchedEvent rag) {
            List<Content> contents = rag.getContent();
            int count = contents == null ? 0 : contents.size();
            String stepId = "rag-" + UUID.randomUUID().toString().substring(0, 8);
            return List.of(
                    AgUiEvent.of(AgUiEventType.STEP_STARTED, threadId, Map.of(
                            "stepId", stepId, "protocol", "RAG", "callType", "rag:retrieval",
                            "agentName", "LangChain4j-RAG", "skillId", "cultural-rag-retrieval", "summary", "Retrieving context")),
                    AgUiEvent.of(AgUiEventType.STEP_FINISHED, threadId, Map.of(
                            "stepId", stepId, "protocol", "RAG", "callType", "rag:retrieval",
                            "agentName", "LangChain4j-RAG", "skillId", "cultural-rag-retrieval", "status", "OK",
                            "isError", false, "summary", "Retrieved " + count + " contextual segment(s)")));
        }
        if (event instanceof ChatEvent.BeforeToolExecutionEvent beforeTool) {
            return mapBeforeTool(threadId, beforeTool.getRequest(), options);
        }
        if (event instanceof ChatEvent.ToolExecutedEvent afterTool) {
            return mapToolExecution(threadId, afterTool.getExecution(), options);
        }
        if (event instanceof ChatEvent.PartialResponseEvent partial) {
            String chunk = partial.getChunk();
            return (chunk == null || chunk.isEmpty())
                    ? List.of()
                    : List.of(AgUiEvent.of(AgUiEventType.TEXT_DELTA, threadId, new TextDeltaPayload(chunk)));
        }
        if (event instanceof ChatEvent.AccumulatedResponseEvent accumulated) {
            List<AgUiEvent<?>> out = new ArrayList<>(3);
            if (accumulated.getMessage() != null && !accumulated.getMessage().isEmpty()) {
                out.add(AgUiEvent.of(AgUiEventType.TEXT_DELTA, threadId, new TextDeltaPayload(accumulated.getMessage())));
            }
            out.addAll(mapCompleted(threadId, options));
            return out;
        }
        if (event instanceof ChatEvent.ChatCompletedEvent) {
            return mapCompleted(threadId, options);
        }
        return List.of();
    }

    private List<AgUiEvent<?>> mapBeforeTool(String threadId, ToolExecutionRequest req, LegacyRunOptions options) {
        String callId = (req.id() == null || req.id().isBlank()) ? "call-" + UUID.randomUUID().toString().substring(0, 8) : req.id();
        String toolName = (req.name() == null || req.name().isBlank()) ? "mcp-tool" : req.name();
        Map<String, Object> args = parseJsonMapOrWrap(req.arguments(), "rawArguments");
        AgUiToolClassifier.ToolDescriptor desc = options.toolClassifier().classify(toolName, args);
        if (desc.stepMode()) {
            return List.of(AgUiEvent.of(AgUiEventType.STEP_STARTED, threadId, Map.of(
                    "stepId", callId, "protocol", "A2A", "callType", desc.callType(),
                    "agentName", desc.serverOrAgent(), "skillId", desc.skillOrLabel(),
                    "args", args, "summary", "Delegating " + desc.skillOrLabel())));
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

    private List<AgUiEvent<?>> mapToolExecution(String threadId, ToolExecution exec, LegacyRunOptions options) {
        ToolExecutionRequest req = exec.request();
        String callId = (req.id() == null || req.id().isBlank()) ? "call-" + UUID.randomUUID().toString().substring(0, 8) : req.id();
        String toolName = (req.name() == null || req.name().isBlank()) ? "mcp-tool" : req.name();
        Map<String, Object> args = parseJsonMapOrWrap(req.arguments(), "rawArguments");
        AgUiToolClassifier.ToolDescriptor desc = options.toolClassifier().classify(toolName, args);

        Map<String, Object> resultData = new LinkedHashMap<>(parseJsonMapOrWrap(exec.result(), "output"));
        boolean isError = exec.hasFailed() || Boolean.TRUE.equals(resultData.get("isError"))
                || (resultData.get("status") instanceof String st && "ERROR".equalsIgnoreCase(st));
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
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("stepId", callId);
            payload.put("protocol", "A2A");
            payload.put("callType", desc.callType());
            payload.put("agentName", desc.serverOrAgent());
            payload.put("skillId", desc.skillOrLabel());
            payload.put("status", isError ? "ERROR" : "OK");
            payload.put("isError", isError);
            String summary = resultData.get("summary") instanceof String s && !s.isBlank()
                    ? s : (isError ? errorDetail : "Completed " + desc.skillOrLabel());
            payload.put("summary", summary);
            if (isError && errorDetail != null) {
                payload.put("error", errorDetail);
            }
            payload.put("report", resultData);
            out.add(AgUiEvent.of(AgUiEventType.STEP_FINISHED, threadId, payload));
        } else {
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
            out.add(AgUiEvent.of(AgUiEventType.TOOL_RESULT, threadId, payload));
        }

        if (options.postToolStateDeltaProvider() != null) {
            Map<String, Object> delta = options.postToolStateDeltaProvider().apply(exec);
            if (delta != null && !delta.isEmpty()) {
                out.add(AgUiEvent.of(AgUiEventType.STATE_DELTA, threadId, delta));
            }
        }
        return out;
    }

    private List<AgUiEvent<?>> mapCompleted(String threadId, LegacyRunOptions options) {
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
        Throwable root = failure;
        while (root.getCause() != null && !(root instanceof InputGuardrailException) && !(root instanceof OutputGuardrailException)) {
            root = root.getCause();
        }
        String message = (root.getMessage() == null || root.getMessage().isBlank())
                ? root.getClass().getSimpleName() : root.getMessage();
        if (root instanceof InputGuardrailException || root instanceof OutputGuardrailException) {
            boolean isInput = root instanceof InputGuardrailException;
            String callType = isInput ? "guardrail:input" : "guardrail:output";
            String skillId = isInput ? "input-safety-guardrail" : "output-honesty-guardrail";
            String stepId = "guardrail-" + UUID.randomUUID().toString().substring(0, 8);
            return List.of(
                    AgUiEvent.of(AgUiEventType.STEP_STARTED, threadId, Map.of(
                            "stepId", stepId, "protocol", "GUARDRAIL", "callType", callType,
                            "agentName", "LangChain4j-Guardrail", "skillId", skillId, "summary", "Evaluating guardrail")),
                    AgUiEvent.of(AgUiEventType.STEP_FINISHED, threadId, Map.of(
                            "stepId", stepId, "protocol", "GUARDRAIL", "callType", callType,
                            "agentName", "LangChain4j-Guardrail", "skillId", skillId, "status", "ERROR",
                            "isError", true, "rule", "OWASP-LLM01", "error", message, "summary", message)),
                    AgUiEvent.of(AgUiEventType.TEXT_DELTA, threadId, new TextDeltaPayload("Request blocked by safety guardrail: " + message)),
                    AgUiEvent.of(AgUiEventType.RUN_FINISHED, threadId, Map.of(
                            "threadId", threadId, "status", "blocked_by_guardrail", "isError", true, "error", message)));
        }
        return List.of(
                AgUiEvent.of(AgUiEventType.STATUS, threadId, Map.of("phase", "error", "message", message)),
                AgUiEvent.of(AgUiEventType.TEXT_DELTA, threadId, new TextDeltaPayload("Upstream AI or tool service unavailable: " + message)),
                AgUiEvent.of(AgUiEventType.RUN_FINISHED, threadId, Map.of(
                        "threadId", threadId, "status", "failed", "isError", true, "error", message)));
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
}
