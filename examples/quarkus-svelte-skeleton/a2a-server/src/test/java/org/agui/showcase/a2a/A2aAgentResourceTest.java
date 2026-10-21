package org.agui.showcase.a2a;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class A2aAgentResourceTest {

    @Test
    void wellKnownAgentJsonReturnsAgentCard() {
        given()
            .when().get("/.well-known/agent.json")
            .then()
            .statusCode(200)
            .body("name", equalTo("Sentinel-A2A-Auditor"))
            .body("capabilities.streaming", equalTo(true))
            .body("skills.size()", equalTo(3));
    }

    @Test
    void sendTaskExecutesSpecialistSkillAndReturnsArtifacts() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of(
                "taskId", "a2a-task-1",
                "skillId", "audit-clean-architecture",
                "prompt", "Audit concentric dependency rings",
                "context", Map.of("serviceName", "payment-service")
            ))
            .when().post("/api/a2a/tasks/send")
            .then()
            .statusCode(200)
            .body("taskId", equalTo("a2a-task-1"))
            .body("agentName", equalTo("Sentinel-A2A-Auditor"))
            .body("status", equalTo("COMPLETED"))
            .body("artifacts.size()", greaterThanOrEqualTo(1))
            .body("summary", notNullValue());
    }
}
