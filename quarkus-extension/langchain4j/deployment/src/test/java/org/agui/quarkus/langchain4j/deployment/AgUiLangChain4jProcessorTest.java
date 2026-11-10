package org.agui.quarkus.langchain4j.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import org.agui.quarkus.langchain4j.deployment.adapters.AgUiLangChain4jProcessor;
import org.agui.quarkus.langchain4j.runtime.adapters.AgUiLangChain4jBridge;
import org.junit.jupiter.api.Test;

class AgUiLangChain4jProcessorTest {

    @Test
    void registersFeatureAndUnremovableLangChain4jBridgeBean() {
        AgUiLangChain4jProcessor processor = new AgUiLangChain4jProcessor();
        FeatureBuildItem feature = processor.feature();
        assertEquals("ag-ui-langchain4j", feature.getName());

        AdditionalBeanBuildItem beans = processor.registerLangChain4jBeans();
        assertNotNull(beans);
        assertTrue(beans.getBeanClasses().contains(AgUiLangChain4jBridge.class.getName()));
        assertFalse(beans.isRemovable());
    }
}
