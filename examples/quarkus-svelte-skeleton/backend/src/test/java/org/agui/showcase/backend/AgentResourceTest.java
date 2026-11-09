package org.agui.showcase.backend;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import io.quarkus.test.junit.QuarkusTest;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AgentResourceTest {

    @Test
    void catalogEndpointReturnsTriProtocolCapabilities() {
        given()
                .when()
                .get("/api/agent/catalog")
                .then()
                .statusCode(200)
                .body("llmMode", equalTo("deterministic"))
                .body("mcpTools.size()", equalTo(3))
                .body("a2aAgent.name", equalTo("Nomad-A2A-Specialist"));
    }

    @Test
    void chatEndpointExecutesTriProtocolWorkflow() {
        given()
                .contentType("application/json")
                .body(Map.of(
                        "threadId", "thread-http-1",
                        "prompt", "Curate a 4-night architectural and koto music escape in Kyoto",
                        "state", Map.of("city", "Kyoto"),
                        "mode", "deterministic"))
                .when()
                .post("/api/agent/chat")
                .then()
                .statusCode(200)
                .body("threadId", equalTo("thread-http-1"))
                .body("status", equalTo("COMPLETED"))
                .body("mcpCalls.size()", greaterThanOrEqualTo(1))
                .body("a2aHandoffs.size()", greaterThanOrEqualTo(1));
    }

    @Test
    void runSseEndpointStreamsAgUiFrames() {
        given()
                .contentType("application/json")
                .body(Map.of(
                        "threadId", "thread-sse-http",
                        "prompt", "Explore Lisbon Fado and azulejo architecture",
                        "state", Map.of(),
                        "mode", "deterministic"))
                .when()
                .post("/api/agent/run")
                .then()
                .statusCode(200)
                .contentType(containsString("text/event-stream"))
                .body(containsString("RUN_STARTED"))
                .body(containsString("STATE_SNAPSHOT"))
                .body(containsString("TOOL_CALL"))
                .body(containsString("STATE_DELTA"))
                .body(containsString("TEXT_DELTA"))
                .body(containsString("RUN_FINISHED"));
    }

    @Test
    void approveEndpointResumesInterruptedHitlRun() {
        given()
                .contentType("application/json")
                .body(Map.of(
                        "threadId", "thread-hitl-http",
                        "prompt", "Reserve Hoshinoya Arashiyama Pavilion in Kyoto for 4 nights ($1840)",
                        "state", Map.of(),
                        "mode", "deterministic"))
                .when()
                .post("/api/agent/run")
                .then()
                .statusCode(200)
                .body(containsString("interrupted"));

        given()
                .contentType("application/json")
                .body(Map.of(
                        "threadId", "thread-hitl-http",
                        "approved", true,
                        "reason", "Confirmed by Traveler"))
                .when()
                .post("/api/agent/approve")
                .then()
                .statusCode(200)
                .contentType(containsString("text/event-stream"))
                .body(containsString("TOOL_RESULT"))
                .body(containsString("STATE_DELTA"))
                .body(containsString("completed"));

        given()
                .contentType("application/json")
                .body(Map.of("threadId", "thread-http-1", "nights", 5, "homeCurrency", "EUR"))
                .when()
                .post("/api/agent/params")
                .then()
                .statusCode(200)
                .body("status", equalTo("params_updated"));

        given()
                .contentType("application/json")
                .body(Map.of("threadId", "thread-http-1"))
                .when()
                .post("/api/agent/memory/reset")
                .then()
                .statusCode(200)
                .body("status", equalTo("memory_reset"))
                .body("memoryTurns", equalTo(0));

        given()
                .contentType("application/json")
                .body(Map.of("threadId", "thread-http-1"))
                .when()
                .post("/api/agent/cancel")
                .then()
                .statusCode(200)
                .body("status", equalTo("cancelled"));

        given()
                .queryParam("prefix", "Li")
                .when()
                .get("/api/agent/complete")
                .then()
                .statusCode(200)
                .body("argument", equalTo("city"));
    }
}
