package org.agui.quarkus.websockets.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import org.agui.quarkus.websockets.deployment.adapters.AgUiWebSocketsProcessor;
import org.agui.quarkus.websockets.runtime.adapters.AgUiWebSocketEndpoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgUiWebSocketsProcessorTest {

    @Test
    @DisplayName("AgUiWebSocketsProcessor exposes ag-ui-websockets feature identifier and registers AgUiWebSocketEndpoint")
    void testProcessor() {
        AgUiWebSocketsProcessor processor = new AgUiWebSocketsProcessor();
        FeatureBuildItem feature = processor.feature();
        assertEquals("ag-ui-websockets", feature.getName());

        AdditionalBeanBuildItem beanBuildItem = processor.registerWebSocketBeans();
        assertNotNull(beanBuildItem);
        assertTrue(beanBuildItem.getBeanClasses().contains(AgUiWebSocketEndpoint.class.getName()));
    }
}
