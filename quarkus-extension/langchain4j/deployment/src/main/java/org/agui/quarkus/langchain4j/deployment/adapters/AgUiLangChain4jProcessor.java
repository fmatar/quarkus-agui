package org.agui.quarkus.langchain4j.deployment.adapters;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import org.agui.quarkus.langchain4j.runtime.adapters.AgUiLangChain4jBridge;

/**
 * Build-time processor for {@code quarkus-ag-ui-langchain4j}.
 */
public class AgUiLangChain4jProcessor {

    public static final String FEATURE = "ag-ui-langchain4j";

    @BuildStep
    public FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public AdditionalBeanBuildItem registerLangChain4jBeans() {
        return AdditionalBeanBuildItem.builder()
                .setUnremovable()
                .addBeanClasses(AgUiLangChain4jBridge.class)
                .build();
    }
}
