package org.agui.showcase.a2a;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class A2aAgentResourceTest {

    @Inject
    NomadTravelAdvisorService advisorService;

    @BeforeEach
    void setUp() {
        advisorService.setHttpProbe(url -> {
            if (url.contains("frankfurter")) {
                return new NomadTravelAdvisorService.UpstreamHttpResult(
                        200, "{\"amount\":1.0,\"base\":\"USD\",\"rates\":{\"EUR\":0.92}}", null, 18L);
            }
            return new NomadTravelAdvisorService.UpstreamHttpResult(
                    400,
                    "{\"success\":false,\"data\":null,\"errors\":[{\"message\":\"This API version has been deprecated. Please visit https://restcountries.com/docs/countries/legacy-api-deprecation to migrate to our new version (v5).\"}]}",
                    "HTTP 400 from restcountries.com",
                    33L);
        });
    }

    @Test
    void wellKnownAgentCardAndHealthEndpointExposeDegradedRestCountriesStatus() throws Exception {
        given()
            .when().get("/.well-known/agent-card.json")
            .then()
            .statusCode(200)
            .body("name", equalTo("Nomad-A2A-Specialist"))
            .body("capabilities.streaming", equalTo(true))
            .body("skills.size()", equalTo(3));

        given()
            .when().get("/api/a2a/health")
            .then()
            .statusCode(200)
            .body("server", equalTo("Nomad-A2A-Specialist"))
            .body("status", equalTo("DEGRADED"))
            .body("healthyCount", equalTo(1))
            .body("totalCount", equalTo(2))
            .body("upstreams[0].status", equalTo("UP"))
            .body("upstreams[1].status", equalTo("DOWN"))
            .body("upstreams[1].error", containsString("This API version has been deprecated"));

        given()
            .contentType("application/json")
            .body(java.util.Map.of(
                    "skillId", "verify-travel-advisory",
                    "prompt", "Verify travel advisory for Paris",
                    "context", java.util.Map.of("city", "Paris")))
            .when().post("/api/a2a/audit")
            .then()
            .statusCode(200)
            .body("data.status", equalTo("ERROR"))
            .body("data.isError", equalTo(true))
            .body("summary", containsString("This API version has been deprecated"));

        // Exercise official A2A JSON-RPC 2.0 AgentExecutor + DataPart extraction at POST /
        org.a2aproject.sdk.spec.Message userMessage = org.a2aproject.sdk.spec.Message.builder()
                .role(org.a2aproject.sdk.spec.Message.Role.ROLE_USER)
                .messageId("msg-1")
                .parts(java.util.List.of(
                        new org.a2aproject.sdk.spec.TextPart("[verify-travel-advisory] Verify travel advisory for Paris"),
                        new org.a2aproject.sdk.spec.DataPart(java.util.Map.of("city", "Paris", "skillId", "verify-travel-advisory"))))
                .build();
        org.a2aproject.sdk.jsonrpc.common.wrappers.SendMessageRequest rpcReq =
                org.a2aproject.sdk.jsonrpc.common.wrappers.SendMessageRequest.builder()
                        .id("rpc-1")
                        .params(org.a2aproject.sdk.spec.MessageSendParams.builder().message(userMessage).build())
                        .build();
        String jsonRpcRequest = org.a2aproject.sdk.jsonrpc.common.json.JsonUtil.toJson(rpcReq);

        given()
            .contentType("application/json")
            .header("A2A-Version", "1.0")
            .body(jsonRpcRequest)
            .when().post("/")
            .then()
            .statusCode(200)
            .body(containsString("Travel Advisory Service Unavailable"));
    }
}
