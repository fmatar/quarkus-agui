package org.agui.quarkus.core.deployment.adapters;

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
import org.agui.quarkus.core.runtime.adapters.AgUiA2aBridge;
import org.agui.quarkus.core.runtime.adapters.AgUiBridge;
import org.agui.quarkus.core.runtime.adapters.AgUiMcpBridge;
import org.agui.quarkus.core.runtime.application.port.AgUiSessionRegistry;
import org.agui.quarkus.core.runtime.domain.AgUiTransportType;

/**
 * Quarkus Build-Time Deployment Processor for {@code quarkus-ag-ui-core}.
 */
public class AgUiCoreProcessor {

    public static final String FEATURE = "ag-ui-core";

    private static final Class<?>[] REFLECTIVE_PROTOCOL_CLASSES = new Class<?>[] {
            AgUiEvent.class,
            AgUiEventType.class,
            AgUiTransportType.class,
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
                        AgUiA2aBridge.class,
                        AgUiSessionRegistry.class
                )
                .build();
    }

    @BuildStep
    public ReflectiveClassBuildItem registerReflectiveProtocolModels() {
        String[] classNames = Arrays.stream(REFLECTIVE_PROTOCOL_CLASSES)
                .map(Class::getName)
                .toArray(String[]::new);

        return ReflectiveClassBuildItem.builder(classNames)
                .methods(true)
                .fields(true)
                .serialization(true)
                .build();
    }
}
