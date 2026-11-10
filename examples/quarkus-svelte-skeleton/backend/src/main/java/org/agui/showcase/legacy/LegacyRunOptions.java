package org.agui.showcase.legacy;

import dev.langchain4j.service.tool.ToolExecution;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import org.agui.langchain4j.adapters.AgUiToolClassifier;

/**
 * Temporary Wave 1-2 compatibility options for the Nomad showcase prior to Wave 3 canonical cutover.
 */
@Deprecated(forRemoval = true)
public record LegacyRunOptions(
        String llmMode,
        Map<String, Object> initialStateSnapshot,
        AgUiToolClassifier toolClassifier,
        Function<ToolExecution, Map<String, Object>> postToolStateDeltaProvider,
        Supplier<Map<String, Object>> postRunStateDeltaProvider,
        Supplier<Map<String, Object>> runFinishedPayloadCustomizer
) {
    public LegacyRunOptions {
        llmMode = (llmMode == null || llmMode.isBlank()) ? "langchain4j" : llmMode;
        initialStateSnapshot = initialStateSnapshot == null ? Map.of() : Map.copyOf(initialStateSnapshot);
        toolClassifier = toolClassifier == null
                ? AgUiToolClassifier.defaultMcpClassifier("langchain4j-mcp")
                : toolClassifier;
    }

    public static LegacyRunOptions defaults() {
        return new LegacyRunOptions("langchain4j", Map.of(), null, null, null, null);
    }
}
