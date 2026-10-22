package org.agui.quarkus.deployment.adapters;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.agui.core.domain.A2aHandoffPayload;
import org.agui.core.domain.AgUiEvent;
import org.agui.core.domain.AgUiEventType;
import org.agui.core.domain.McpToolMetadata;
import org.agui.core.domain.RunFinishedPayload;
import org.agui.core.domain.RunStartedPayload;
import org.agui.core.domain.StateDeltaPayload;
import org.agui.core.domain.StateSnapshotPayload;
import org.agui.core.domain.StatusPayload;
import org.agui.core.domain.StepPayload;
import org.agui.core.domain.TextDeltaPayload;
import org.agui.core.domain.ToolCallPayload;
import org.agui.core.domain.ToolResultPayload;
import org.agui.quarkus.runtime.adapters.AgUiA2aBridge;
import org.agui.quarkus.runtime.adapters.AgUiBridge;
import org.agui.quarkus.runtime.adapters.AgUiMcpBridge;

/**
 * Quarkus Build-Time Deployment Processor for {@code quarkus-ag-ui}.
 * Registers feature metadata, CDI runtime bridges, GraalVM 25 native reflection items, and Dev UI Inspector metadata.
 */
public class AgUiProcessor {

    public static final String FEATURE = "ag-ui";

    private static final Class<?>[] REFLECTIVE_PROTOCOL_CLASSES = new Class<?>[] {
            AgUiEvent.class,
            AgUiEventType.class,
            RunStartedPayload.class,
            TextDeltaPayload.class,
            ToolCallPayload.class,
            ToolResultPayload.class,
            StatusPayload.class,
            RunFinishedPayload.class,
            StepPayload.class,
            StateSnapshotPayload.class,
            StateDeltaPayload.class,
            McpToolMetadata.class,
            A2aHandoffPayload.class
    };

    @BuildStep
    public FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public AdditionalBeanBuildItem registerRuntimeBridges() {
        return AdditionalBeanBuildItem.builder()
                .setUnremovable()
                .addBeanClasses(
                        AgUiBridge.class,
                        AgUiMcpBridge.class,
                        AgUiA2aBridge.class)
                .build();
    }

    @BuildStep
    public ReflectiveClassBuildItem registerGraalVmReflection() {
        return ReflectiveClassBuildItem.builder(REFLECTIVE_PROTOCOL_CLASSES)
                .methods()
                .fields()
                .build();
    }

    public List<String> getRegisteredReflectiveClassNames() {
        return Arrays.stream(REFLECTIVE_PROTOCOL_CLASSES).map(Class::getName).toList();
    }

    public Map<String, Object> buildDevUiInspectorMetadata() {
        return Map.of(
                "feature", FEATURE,
                "title", "AG-UI Protocol Inspector",
                "protocols", List.of("AG-UI", "MCP", "A2A"),
                "supportedEvents", Arrays.stream(AgUiEventType.values()).map(Enum::name).toList(),
                "reflectiveClassesCount", REFLECTIVE_PROTOCOL_CLASSES.length);
    }
}
