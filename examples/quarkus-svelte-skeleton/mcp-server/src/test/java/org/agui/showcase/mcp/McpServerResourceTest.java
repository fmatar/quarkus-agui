package org.agui.showcase.mcp;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class McpServerResourceTest {

    @Inject
    NomadMcpConciergeService conciergeService;

    @BeforeEach
    void setUp() {
        conciergeService.resetState();
        conciergeService.setHttpProbe(url -> {
            if (url.contains("/v1/forecast")) {
                return new NomadMcpConciergeService.UpstreamHttpResult(
                        200,
                        "{\"current\":{\"temperature_2m\":19.5,\"wind_speed_10m\":8.2,\"weather_code\":1}}",
                        null,
                        15L);
            }
            if (url.contains("/api/rest_v1/page/summary/")) {
                return new NomadMcpConciergeService.UpstreamHttpResult(
                        200,
                        "{\"title\":\"Lisbon\",\"extract\":\"Lisbon overlooks the Atlantic and Tagus estuary.\",\"originalimage\":{\"source\":\"https://upload.wikimedia.org/lisbon.jpg\"}}",
                        null,
                        18L);
            }
            if (url.contains("/search")) {
                return new NomadMcpConciergeService.UpstreamHttpResult(
                        200,
                        "{\"resultCount\":1,\"results\":[{\"trackName\":\"Canção do Mar\",\"artistName\":\"Amália Rodrigues\",\"previewUrl\":\"https://audio-ssl.itunes.apple.com/fado.m4a\",\"artworkUrl100\":\"https://is1-ssl.mzstatic.com/100x100bb.jpg\"}]}",
                        null,
                        20L);
            }
            return new NomadMcpConciergeService.UpstreamHttpResult(200, "{}", null, 10L);
        });
    }

    @Test
    void listToolsAndHealthEndpointsReturnStatusAndRegisteredTools() {
        given()
                .when().get("/api/mcp/tools")
                .then()
                .statusCode(200)
                .body("serverName", equalTo("nomad-mcp-concierge"))
                .body("tools", hasSize(3));

        given()
                .when().get("/api/mcp/health")
                .then()
                .statusCode(200)
                .body("server", equalTo("nomad-mcp-concierge"))
                .body("status", equalTo("UP"))
                .body("upstreams", notNullValue());
    }

    @Test
    void nativeQuarkusMcpStreamableHttpEndpointExposesInitializeAndTools() {
        given()
                .contentType(ContentType.JSON)
                .accept("application/json, text/event-stream")
                .body(Map.of(
                        "jsonrpc", "2.0",
                        "id", 1,
                        "method", "initialize",
                        "params", Map.of(
                                "protocolVersion", "2025-03-26",
                                "capabilities", Map.of(),
                                "clientInfo", Map.of("name", "test-client", "version", "1.0.0"))))
                .when().post("/mcp")
                .then()
                .statusCode(200);
    }

    @Test
    void callToolEndpointRejectsMissingArgumentsAndExecutesValidRequests() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("tool", "fetchCityWeather", "arguments", Map.of()))
                .when().post("/api/mcp/tools/call")
                .then()
                .statusCode(200)
                .body("isError", equalTo(true));

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("tool", "fetchCityWeather", "arguments", Map.of("city", "Lisbon")))
                .when().post("/api/mcp/tools/call")
                .then()
                .statusCode(200)
                .body("isError", equalTo(false))
                .body("result.city", equalTo("Lisbon"))
                .body("result.temperatureC", equalTo(19.5f));

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("tool", "updateTripItinerary", "arguments", Map.of(
                        "city", "Lisbon",
                        "stopTitle", "Palácio Belmonte",
                        "nights", 4,
                        "costUsd", 1760)))
                .when().post("/api/mcp/tools/call")
                .then()
                .statusCode(200)
                .body("isError", equalTo(false))
                .body("result.activeCity", equalTo("Lisbon"))
                .body("result.nights", equalTo(4));
    }

    @Test
    void nomadResourcesStartWithoutAssumedDefaultsAndAcceptPreferenceUpdates() {
        given()
                .when().get("/api/mcp/resources/itinerary")
                .then()
                .statusCode(200)
                .body("uri", equalTo("itinerary://active-trip"))
                .body("activeCity", nullValue())
                .body("nights", nullValue());

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("travelerName", "Fady", "dietary", "Vegan", "homeCurrency", "EUR"))
                .when().post("/api/mcp/resources/preferences")
                .then()
                .statusCode(200)
                .body("travelerName", equalTo("Fady"))
                .body("dietary", equalTo("Vegan"))
                .body("homeCurrency", equalTo("EUR"));

        given()
                .when().get("/api/mcp/resources/preferences")
                .then()
                .statusCode(200)
                .body("travelerName", equalTo("Fady"));

        given()
                .when().get("/api/mcp/resources/city/Lisbon")
                .then()
                .statusCode(200)
                .body("city", equalTo("Lisbon"))
                .body("weatherStatus", equalTo("OK"));

        given()
                .when().get("/api/mcp/prompts")
                .then()
                .statusCode(200)
                .body("prompts", hasSize(2));

        given()
                .queryParam("prefix", "Li")
                .when().get("/api/mcp/prompts/complete")
                .then()
                .statusCode(200)
                .body("argument", equalTo("city"));

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("city", "Lisbon", "weatherCondition", "Clear Sky", "genre", "fado"))
                .when().post("/api/mcp/sampling")
                .then()
                .statusCode(200)
                .body("status", equalTo("OK"))
                .body("city", equalTo("Lisbon"));
    }
}
