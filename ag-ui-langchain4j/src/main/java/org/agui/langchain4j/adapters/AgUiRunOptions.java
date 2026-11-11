package org.agui.langchain4j.adapters;

import com.agui.community.core.event.ActivitySnapshotEvent;
import com.agui.community.core.event.JsonPatchOperation;
import com.agui.community.core.interrupt.Interrupt;
import dev.langchain4j.service.tool.ToolExecution;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Per-run adaptation options for {@link AgUiLangChain4jBridge}.
 */
public record AgUiRunOptions(
        String runId,
        String llmMode,
        Map<String, Object> initialStateSnapshot,
        AgUiToolClassifier toolClassifier,
        Function<ToolExecution, List<JsonPatchOperation>> postToolStatePatchProvider,
        Supplier<List<JsonPatchOperation>> postRunStatePatchProvider,
        Function<ToolExecution, List<ActivitySnapshotEvent>> postToolActivityProvider,
        Supplier<List<ActivitySnapshotEvent>> postRunActivityProvider,
        Supplier<List<Interrupt>> interruptSupplier,
        Supplier<Object> runResultSupplier
) {
    public AgUiRunOptions {
        runId = (runId == null || runId.isBlank()) ? "run-" + UUID.randomUUID().toString().substring(0, 8) : runId;
        llmMode = (llmMode == null || llmMode.isBlank()) ? "langchain4j" : llmMode;
        initialStateSnapshot = initialStateSnapshot == null ? Map.of() : Map.copyOf(initialStateSnapshot);
        toolClassifier = toolClassifier == null
                ? AgUiToolClassifier.defaultMcpClassifier("langchain4j-mcp")
                : toolClassifier;
    }

    public AgUiRunOptions(
            String runId,
            String llmMode,
            Map<String, Object> initialStateSnapshot,
            AgUiToolClassifier toolClassifier,
            Function<ToolExecution, List<JsonPatchOperation>> postToolStatePatchProvider,
            Supplier<List<JsonPatchOperation>> postRunStatePatchProvider,
            Supplier<List<Interrupt>> interruptSupplier,
            Supplier<Object> runResultSupplier) {
        this(
                runId,
                llmMode,
                initialStateSnapshot,
                toolClassifier,
                postToolStatePatchProvider,
                postRunStatePatchProvider,
                null,
                null,
                interruptSupplier,
                runResultSupplier);
    }

    public static AgUiRunOptions defaults() {
        return builder().build();
    }

    public AgUiRunOptions withRunId(String overrideRunId) {
        if (overrideRunId == null || overrideRunId.isBlank()) {
            return this;
        }
        return new AgUiRunOptions(
                overrideRunId,
                llmMode,
                initialStateSnapshot,
                toolClassifier,
                postToolStatePatchProvider,
                postRunStatePatchProvider,
                postToolActivityProvider,
                postRunActivityProvider,
                interruptSupplier,
                runResultSupplier);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String runId;
        private String llmMode;
        private Map<String, Object> initialStateSnapshot;
        private AgUiToolClassifier toolClassifier;
        private Function<ToolExecution, List<JsonPatchOperation>> postToolStatePatchProvider;
        private Supplier<List<JsonPatchOperation>> postRunStatePatchProvider;
        private Function<ToolExecution, List<ActivitySnapshotEvent>> postToolActivityProvider;
        private Supplier<List<ActivitySnapshotEvent>> postRunActivityProvider;
        private Supplier<List<Interrupt>> interruptSupplier;
        private Supplier<Object> runResultSupplier;

        private Builder() {
        }

        public Builder runId(String runId) {
            this.runId = runId;
            return this;
        }

        public Builder llmMode(String llmMode) {
            this.llmMode = llmMode;
            return this;
        }

        public Builder initialStateSnapshot(Map<String, Object> initialStateSnapshot) {
            this.initialStateSnapshot = initialStateSnapshot;
            return this;
        }

        public Builder toolClassifier(AgUiToolClassifier toolClassifier) {
            this.toolClassifier = toolClassifier;
            return this;
        }

        public Builder postToolStatePatchProvider(Function<ToolExecution, List<JsonPatchOperation>> provider) {
            this.postToolStatePatchProvider = provider;
            return this;
        }

        public Builder postRunStatePatchProvider(Supplier<List<JsonPatchOperation>> provider) {
            this.postRunStatePatchProvider = provider;
            return this;
        }

        public Builder postToolActivityProvider(Function<ToolExecution, List<ActivitySnapshotEvent>> provider) {
            this.postToolActivityProvider = provider;
            return this;
        }

        public Builder postRunActivityProvider(Supplier<List<ActivitySnapshotEvent>> provider) {
            this.postRunActivityProvider = provider;
            return this;
        }

        public Builder interruptSupplier(Supplier<List<Interrupt>> interruptSupplier) {
            this.interruptSupplier = interruptSupplier;
            return this;
        }

        public Builder runResultSupplier(Supplier<Object> runResultSupplier) {
            this.runResultSupplier = runResultSupplier;
            return this;
        }

        public AgUiRunOptions build() {
            return new AgUiRunOptions(
                    runId,
                    llmMode,
                    initialStateSnapshot,
                    toolClassifier,
                    postToolStatePatchProvider,
                    postRunStatePatchProvider,
                    postToolActivityProvider,
                    postRunActivityProvider,
                    interruptSupplier,
                    runResultSupplier);
        }
    }
}
