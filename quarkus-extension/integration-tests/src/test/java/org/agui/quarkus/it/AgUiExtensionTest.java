package org.agui.quarkus.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import org.agui.quarkus.it.adapters.AgUiIntegrationResource;
import org.agui.quarkus.runtime.adapters.AgUiA2aBridge;
import org.agui.quarkus.runtime.adapters.AgUiBridge;
import org.agui.quarkus.runtime.adapters.AgUiMcpBridge;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AgUiExtensionTest {

    @Test
    void extensionAutoConfiguresBridgesAndStreamsAgUiSseEvents() {
        given()
                .queryParam("threadId", "thread-ext-1")
                .when()
                .get("/api/it/agui/stream")
                .then()
                .statusCode(200)
                .contentType(containsString("text/event-stream"))
                .body(containsString("thread-ext-1"))
                .body(containsString("TOOL_CALL"))
                .body(containsString("STEP_FINISHED"))
                .body(containsString("TEXT_DELTA"))
                .body(containsString("RUN_FINISHED"));

        AgUiIntegrationResource direct = new AgUiIntegrationResource(
                new AgUiBridge(),
                new AgUiMcpBridge(),
                new AgUiA2aBridge());
        List<String> blankFrames = direct.stream("").collect().asList().await().indefinitely();
        assertTrue(blankFrames.getFirst().contains("thread-it"));

        List<String> nullFrames = direct.stream(null).collect().asList().await().indefinitely();
        assertTrue(nullFrames.getFirst().contains("thread-it"));
    }
}
