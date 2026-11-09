package org.agui.showcase.mcp;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class McpServerResourceTest {

    @Test
    void listToolsReturnsThreeRegisteredNomadMcpTools() {
        given()
            .when().get("/api/mcp/tools")
            .then()
            .statusCode(200)
            .body("serverName", equalTo("nomad-mcp-concierge"))
            .body("tools.size()", equalTo(3));
    }

    @Test
    void callToolEndpointExecutesFetchCityWeatherAndUpdateTripItinerary() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("tool", "fetchCityWeather", "arguments", Map.of("city", "Kyoto")))
            .when().post("/api/mcp/tools/call")
            .then()
            .statusCode(200)
            .body("isError", equalTo(false))
            .body("tool", equalTo("fetchCityWeather"))
            .body("result.city", equalTo("Kyoto"));

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("tool", "updateTripItinerary", "arguments", Map.of("city", "Lisbon", "stopTitle", "Palácio Belmonte", "nights", 4, "costUsd", 1760)))
            .when().post("/api/mcp/tools/call")
            .then()
            .statusCode(200)
            .body("isError", equalTo(false))
            .body("result.activeCity", equalTo("Lisbon"))
            .body("result.nights", equalTo(4));
    }

    @Test
    void nomadResourcesAndPromptsReturnSnapshots() {
        given()
            .when().get("/api/mcp/resources/itinerary")
            .then()
            .statusCode(200)
            .body("uri", equalTo("itinerary://active-trip"))
            .body("activeCity", notNullValue());

        given()
            .when().get("/api/mcp/resources/preferences")
            .then()
            .statusCode(200)
            .body("uri", equalTo("traveler://preferences"));

        given()
            .when().get("/api/mcp/prompts")
            .then()
            .statusCode(200)
            .body("prompts.size()", equalTo(2));

        given()
            .when().get("/api/mcp/resources/city/Beirut")
            .then()
            .statusCode(200)
            .body("city", equalTo("Beirut"))
            .body("uri", equalTo("itinerary://city/beirut/dossier"));

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("dietary", "Vegan", "homeCurrency", "EUR"))
            .when().post("/api/mcp/resources/preferences")
            .then()
            .statusCode(200)
            .body("dietary", equalTo("Vegan"))
            .body("homeCurrency", equalTo("EUR"));

        given()
            .queryParam("prefix", "Ky")
            .when().get("/api/mcp/prompts/complete")
            .then()
            .statusCode(200)
            .body("argument", equalTo("city"))
            .body("values[0]", equalTo("Kyoto"));

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("city", "Tokyo", "weatherCondition", "Clear Horizon", "genre", "jazz"))
            .when().post("/api/mcp/sampling")
            .then()
            .statusCode(200)
            .body("protocol", equalTo("mcp:sampling"))
            .body("city", equalTo("Tokyo"));
    }
}
