package org.agui.quarkus.langchain4j.runtime.adapters;

import dev.langchain4j.service.tool.ToolExecution;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Per-run adaptation options for {@link AgUiLangChain4jBridge}.
 */
public record AgUiRunOptions(
        String llmMode,
        Map<String, Object> initialStateSnapshot,
        AgUiToolClassifier toolClassifier,
        Function<ToolExecution, Map<String, Object>> postToolStateDeltaProvider,
        Supplier<Map<String, Object>> postRunStateDeltaProvider,
        Supplier<Map<String, Object>> runFinishedPayloadCustomizer
) {
    public AgUiRunOptions {
        llmMode = (llmMode == null || llmMode.isBlank()) ? "quarkus-langchain4j" : llmMode;
        initialStateSnapshot = initialStateSnapshot == null ? Map.of() : Map.copyOf(initialStateSnapshot);
        toolClassifier = toolClassifier == null
                ? AgUiToolClassifier.defaultMcpClassifier("quarkus-langchain4j-mcp")
                : toolClassifier;
    }

    public static AgUiRunOptions defaults() {
        return new AgUiRunOptions(
                "quarkus-langchain4j",
                Map.of(),
                AgUiToolClassifier.defaultMcpClassifier("quarkus-langchain4j-mcp"),
                null,
                null,
                null);
    }
}
