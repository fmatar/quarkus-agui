package org.agui.quarkus.sse.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import org.agui.quarkus.sse.deployment.adapters.AgUiSseProcessor;
import org.agui.quarkus.sse.runtime.adapters.AgUiSseBridge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgUiSseProcessorTest {

    @Test
    @DisplayName("AgUiSseProcessor exposes ag-ui-sse feature identifier and registers AgUiSseBridge")
    void testProcessor() {
        AgUiSseProcessor processor = new AgUiSseProcessor();
        FeatureBuildItem feature = processor.feature();
        assertEquals("ag-ui-sse", feature.getName());

        AdditionalBeanBuildItem beanBuildItem = processor.registerSseBeans();
        assertNotNull(beanBuildItem);
        assertTrue(beanBuildItem.getBeanClasses().contains(AgUiSseBridge.class.getName()));
    }
}
