package org.agui.showcase.a2a;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class A2aAgentResourceTest {

    @Test
    void wellKnownAgentCardJsonReturnsOfficialA2aCard() {
        given()
            .when().get("/.well-known/agent-card.json")
            .then()
            .statusCode(200)
            .body("name", equalTo("Nomad-A2A-Specialist"))
            .body("capabilities.streaming", equalTo(true))
            .body("skills.size()", equalTo(3));
    }
}
