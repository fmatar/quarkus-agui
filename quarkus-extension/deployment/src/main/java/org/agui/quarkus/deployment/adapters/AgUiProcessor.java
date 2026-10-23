package org.agui.quarkus.deployment.adapters;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

/**
 * Build-time deployment processor for umbrella starter {@code quarkus-ag-ui}.
 */
public class AgUiProcessor {

    public static final String FEATURE = "ag-ui";

    @BuildStep
    public FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }
}
