package org.agui.quarkus.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import java.util.List;
import java.util.Map;
import org.agui.core.domain.AgUiEvent;
import org.agui.quarkus.deployment.adapters.AgUiProcessor;
import org.agui.quarkus.runtime.adapters.AgUiBridge;
import org.junit.jupiter.api.Test;

class AgUiProcessorTest {

    @Test
    void processorRegistersFeatureBeansGraalVmReflectionAndDevUiMetadata() {
        AgUiProcessor processor = new AgUiProcessor();

        FeatureBuildItem feature = processor.feature();
        assertEquals("ag-ui", feature.getName());

        AdditionalBeanBuildItem beans = processor.registerRuntimeBridges();
        assertNotNull(beans);
        assertTrue(beans.getBeanClasses().contains(AgUiBridge.class.getName()));

        ReflectiveClassBuildItem reflection = processor.registerGraalVmReflection();
        assertNotNull(reflection);
        assertTrue(reflection.isMethods());
        assertTrue(reflection.isFields());

        List<String> names = processor.getRegisteredReflectiveClassNames();
        assertEquals(13, names.size());
        assertTrue(names.contains(AgUiEvent.class.getName()));

        Map<String, Object> devUi = processor.buildDevUiInspectorMetadata();
        assertEquals("AG-UI Protocol Inspector", devUi.get("title"));
        assertEquals(13, devUi.get("reflectiveClassesCount"));
    }
}
