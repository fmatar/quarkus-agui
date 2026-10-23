package org.agui.quarkus.websockets.deployment.adapters;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import org.agui.quarkus.websockets.runtime.adapters.AgUiWebSocketEndpoint;

/**
 * Build-time processor for {@code quarkus-ag-ui-websockets}.
 */
public class AgUiWebSocketsProcessor {

    public static final String FEATURE = "ag-ui-websockets";

    @BuildStep
    public FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public AdditionalBeanBuildItem registerWebSocketBeans() {
        return AdditionalBeanBuildItem.builder()
                .setUnremovable()
                .addBeanClasses(AgUiWebSocketEndpoint.class)
                .build();
    }
}
