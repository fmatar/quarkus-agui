package org.agui.quarkus.langchain4j.runtime.adapters;

import java.util.Map;
import java.util.Objects;

/**
 * Classifies LangChain4j tool executions into AG-UI tool calls ({@code TOOL_CALL}/{@code TOOL_RESULT})
 * or delegated protocol steps ({@code STEP_STARTED}/{@code STEP_FINISHED}).
 */
@FunctionalInterface
public interface AgUiToolClassifier {

    record ToolDescriptor(
            boolean stepMode,
            String callType,
            String serverOrAgent,
            String skillOrLabel,
            boolean requiresApproval
    ) {
        public ToolDescriptor {
            Objects.requireNonNull(callType, "callType must not be null");
            Objects.requireNonNull(serverOrAgent, "serverOrAgent must not be null");
            Objects.requireNonNull(skillOrLabel, "skillOrLabel must not be null");
        }

        public static ToolDescriptor mcpTool(String serverLabel, String toolName) {
            return new ToolDescriptor(false, "mcp:tool", serverLabel, toolName, false);
        }

        public static ToolDescriptor mcpSampling(String serverLabel, String toolName) {
            return new ToolDescriptor(false, "mcp:sampling", serverLabel, toolName, false);
        }

        public static ToolDescriptor a2aStep(String agentName, String skillId) {
            return new ToolDescriptor(true, "a2a:task", agentName, skillId, false);
        }

        public static ToolDescriptor uiSurface(String serverLabel, String toolName) {
            return new ToolDescriptor(false, "tool:ui", serverLabel, toolName, false);
        }

        public static ToolDescriptor hitlApproval(String serverLabel, String toolName) {
            return new ToolDescriptor(false, "hitl:approval", serverLabel, toolName, true);
        }
    }

    ToolDescriptor classify(String toolName, Map<String, Object> args);

    static AgUiToolClassifier defaultMcpClassifier(String serverLabel) {
        String resolved = (serverLabel == null || serverLabel.isBlank()) ? "quarkus-langchain4j-mcp" : serverLabel;
        return (toolName, args) -> ToolDescriptor.mcpTool(
                resolved,
                (toolName == null || toolName.isBlank()) ? "mcp-tool" : toolName);
    }
}
