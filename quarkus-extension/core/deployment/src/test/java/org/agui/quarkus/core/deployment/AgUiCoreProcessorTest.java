package org.agui.quarkus.core.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import org.agui.quarkus.core.deployment.adapters.AgUiCoreProcessor;
import org.agui.quarkus.core.runtime.adapters.AgUiA2aBridge;
import org.agui.quarkus.core.runtime.adapters.AgUiBridge;
import org.agui.quarkus.core.runtime.adapters.AgUiMcpBridge;
import org.agui.quarkus.core.runtime.application.port.AgUiSessionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgUiCoreProcessorTest {

    @Test
    @DisplayName("Processor exposes ag-ui-core feature identifier")
    void testFeatureIdentifier() {
        AgUiCoreProcessor processor = new AgUiCoreProcessor();
        FeatureBuildItem item = processor.feature();
        assertEquals("ag-ui-core", item.getName());
    }

    @Test
    @DisplayName("Processor registers all CDI beans")
    void testRegisterRuntimeBridges() {
        AgUiCoreProcessor processor = new AgUiCoreProcessor();
        AdditionalBeanBuildItem item = processor.registerRuntimeBridges();
        assertNotNull(item);
        assertTrue(item.isRemovable() == false || !item.getBeanClasses().isEmpty());
        assertTrue(item.getBeanClasses().contains(AgUiBridge.class.getName()));
        assertTrue(item.getBeanClasses().contains(AgUiMcpBridge.class.getName()));
        assertTrue(item.getBeanClasses().contains(AgUiA2aBridge.class.getName()));
        assertTrue(item.getBeanClasses().contains(AgUiSessionRegistry.class.getName()));
    }

    @Test
    @DisplayName("Processor registers domain protocol classes for GraalVM reflection")
    void testRegisterReflectiveProtocolModels() {
        AgUiCoreProcessor processor = new AgUiCoreProcessor();
        ReflectiveClassBuildItem item = processor.registerReflectiveProtocolModels();
        assertNotNull(item);
        assertTrue(item.getClassNames().size() >= 13);
    }
}
