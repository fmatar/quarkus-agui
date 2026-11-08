package org.agui.quarkus.sse.deployment.adapters;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import org.agui.quarkus.sse.runtime.adapters.AgUiSseBridge;

/**
 * Build-time processor for {@code quarkus-ag-ui-sse}.
 */
public class AgUiSseProcessor {

    public static final String FEATURE = "ag-ui-sse";

    @BuildStep
    public FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public AdditionalBeanBuildItem registerSseBeans() {
        return AdditionalBeanBuildItem.builder()
                .setUnremovable()
                .addBeanClasses(AgUiSseBridge.class)
                .build();
    }
}
