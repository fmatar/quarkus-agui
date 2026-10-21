package org.agui.showcase.mcp;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class McpServerResourceTest {

    @Test
    void listToolsReturnsThreeRegisteredMcpTools() {
        given()
            .when().get("/api/mcp/tools")
            .then()
            .statusCode(200)
            .body("serverName", equalTo("cluster-telemetry-mcp"))
            .body("tools.size()", equalTo(3));
    }

    @Test
    void callToolEndpointExecutesInspectClusterHealthAndScaleDeployment() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("tool", "inspectClusterHealth", "arguments", Map.of("region", "us-east-1")))
            .when().post("/api/mcp/tools/call")
            .then()
            .statusCode(200)
            .body("isError", equalTo(false))
            .body("tool", equalTo("inspectClusterHealth"))
            .body("result.region", equalTo("us-east-1"));

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("tool", "scaleDeployment", "arguments", Map.of("serviceName", "payment-service", "targetReplicas", 5)))
            .when().post("/api/mcp/tools/call")
            .then()
            .statusCode(200)
            .body("isError", equalTo(false))
            .body("result.replicas", equalTo(5));
    }

    @Test
    void topologyResourceReturnsClusterSnapshot() {
        given()
            .when().get("/api/mcp/resources/topology")
            .then()
            .statusCode(200)
            .body("uri", equalTo("cluster://topology"))
            .body("services.size()", greaterThanOrEqualTo(3))
            .body("updatedAt", notNullValue());
    }
}
