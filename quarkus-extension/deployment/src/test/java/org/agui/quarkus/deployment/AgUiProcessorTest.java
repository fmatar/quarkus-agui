package org.agui.quarkus.deployment;

import io.quarkus.deployment.builditem.FeatureBuildItem;
import org.agui.quarkus.deployment.adapters.AgUiProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgUiProcessorTest {

    @Test
    @DisplayName("Starter processor exposes umbrella ag-ui feature identifier")
    void testFeatureIdentifier() {
        AgUiProcessor processor = new AgUiProcessor();
        FeatureBuildItem item = processor.feature();
        assertEquals("ag-ui", item.getName());
    }
}
