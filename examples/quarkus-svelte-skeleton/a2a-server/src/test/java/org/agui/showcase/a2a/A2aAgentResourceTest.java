package org.agui.showcase.a2a;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.net.URL;
import java.time.Duration;
import java.util.Map;
import org.agui.a2a.adapters.AgUiA2aClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class A2aAgentResourceTest {

    @Inject
    NomadTravelAdvisorService advisorService;

    @TestHTTPResource("/")
    URL serverUrl;

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
    void wellKnownAgentCardHealthAndOfficialA2aClientSdkWorkEndToEnd() throws Exception {
        given()
            .when().get("/.well-known/agent-card.json")
            .then()
            .statusCode(200)
            .body("name", equalTo("Nomad-A2A-Specialist"))
            .body("version", equalTo("1.5.0"))
            .body("capabilities.streaming", equalTo(true))
            .body("skills.size()", equalTo(3));

        given()
            .when().get("/q/health/ready")
            .then()
            .statusCode(503)
            .body("status", equalTo("DOWN"))
            .body("checks[0].name", equalTo("nomad-a2a-upstreams"))
            .body("checks[0].data.'restcountries.status'", equalTo("DOWN"));

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

        // Verify legacy custom /api/a2a/audit side-door has been removed
        given()
            .contentType("application/json")
            .body(Map.of("skillId", "verify-travel-advisory", "prompt", "Verify Paris"))
            .when().post("/api/a2a/audit")
            .then()
            .statusCode(404);

        // Exercise official A2A 1.5.0.Final client (A2ACardResolver + Client.builder + JSONRPCTransport)
        try (AgUiA2aClient client = AgUiA2aClient.builder(serverUrl.toString())
                .streaming(false)
                .requestTimeout(Duration.ofSeconds(5))
                .build()) {
            assertEquals("1.5.0", client.resolveAgentCard().version());

            AgUiA2aClient.DelegationExecution fxExec = client.delegate(
                    "req-fx-1",
                    "convert-and-audit-budget",
                    "Convert 2000 USD for Paris",
                    Map.of("city", "Paris", "country", "France", "budgetUsd", 2000.0, "baseCurrency", "USD", "targetCurrency", "EUR"),
                    Map.of());
            assertFalse(fxExec.result().isError());
            assertEquals(0.92, fxExec.result().data().get("exchangeRate"));

            AgUiA2aClient.DelegationExecution advExec = client.delegate(
                    "req-adv-1",
                    "verify-travel-advisory",
                    "Verify travel advisory for Paris",
                    Map.of("city", "Paris", "country", "France"),
                    Map.of());
            assertTrue(advExec.result().isError());
            assertTrue(advExec.result().summary().contains("This API version has been deprecated"));
        }
    }
}
