package org.agui.showcase.mcp;

import io.quarkiverse.mcp.server.ToolResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NomadMcpConciergeServiceTest {

    private NomadMcpConciergeService service;

    @BeforeEach
    void setUp() {
        service = new NomadMcpConciergeService();
        service.resetState();
    }

    @Test
    void initialStateHasZeroAssumedDefaults() {
        Map<String, Object> trip = service.getActiveTripSnapshot();
        assertEquals("itinerary://active-trip", trip.get("uri"));
        assertNull(trip.get("activeCity"));
        assertNull(trip.get("nights"));
        assertNull(trip.get("budgetUsd"));
        assertNull(trip.get("homeCurrency"));
        assertNull(trip.get("soundtrack"));

        Map<String, Object> prefs = service.getTravelerPreferences();
        assertEquals("traveler://preferences", prefs.get("uri"));
        assertNull(prefs.get("style"));
        assertNull(prefs.get("dietary"));
        assertNull(prefs.get("musicTaste"));
        assertNull(prefs.get("homeCurrency"));
        assertNull(prefs.get("travelerName"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void toolsRejectMissingRequiredArgumentsInsteadOfAssumingKyotoOrDefaults() {
        ToolResponse missingCityWeather = service.fetchCityWeather("");
        assertTrue(missingCityWeather.isError());
        Map<String, Object> errWeather = (Map<String, Object>) missingCityWeather.structuredContent();
        assertEquals("ERROR", errWeather.get("status"));
        assertEquals("MISSING_REQUIRED_ARGUMENTS", errWeather.get("code"));

        ToolResponse missingDestCulture = service.discoverLocalCultureAndMedia("   ", null);
        assertTrue(missingDestCulture.isError());
        Map<String, Object> errCulture = (Map<String, Object>) missingDestCulture.structuredContent();
        assertEquals("MISSING_REQUIRED_ARGUMENTS", errCulture.get("code"));

        ToolResponse missingNightsItinerary = service.updateTripItinerary("Paris", "Cour des Vosges", null, 1400.0);
        assertTrue(missingNightsItinerary.isError());

        ToolResponse missingCostItinerary = service.updateTripItinerary("Paris", "Cour des Vosges", 5, null);
        assertTrue(missingCostItinerary.isError());

        McpToolInvocationResponse dispatchMissing = service.dispatchTool(
                new McpToolInvocationRequest("fetchCityWeather", Map.of()));
        assertTrue(dispatchMissing.isError());

        McpToolInvocationResponse dispatchUnknown = service.dispatchTool(
                new McpToolInvocationRequest("nonExistentTool", Map.of()));
        assertTrue(dispatchUnknown.isError());
    }

    @Test
    @SuppressWarnings("unchecked")
    void upstreamFailuresSurfaceStructuredErrorsInsteadOfFakeFallbacks() {
        service.httpProbe = url -> new NomadMcpConciergeService.UpstreamHttpResult(
                503, null, "Connection refused to upstream", 12L);

        ToolResponse failedWeather = service.fetchCityWeather("Lisbon");
        assertTrue(failedWeather.isError());
        Map<String, Object> weatherErr = (Map<String, Object>) failedWeather.structuredContent();
        assertEquals("ERROR", weatherErr.get("status"));
        assertEquals(false, weatherErr.get("serviceAvailable"));
        assertEquals("api.open-meteo.com", weatherErr.get("upstream"));
        assertNull(service.getActiveTripSnapshot().get("temperatureC"));

        ToolResponse failedCulture = service.discoverLocalCultureAndMedia("Lisbon", "fado");
        assertTrue(failedCulture.isError());
        Map<String, Object> cultureErr = (Map<String, Object>) failedCulture.structuredContent();
        assertEquals("ERROR", cultureErr.get("status"));
        assertEquals("ERROR", cultureErr.get("wikipediaStatus"));
        assertEquals("ERROR", cultureErr.get("itunesStatus"));

        Map<String, Object> degradedHealth = service.checkHealth();
        assertEquals("DOWN", degradedHealth.get("status"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void liveUpstreamResponsesPopulateWeatherCultureItineraryResourcesAndHealth() {
        service.httpProbe = url -> {
            if (url.contains("/v1/search")) {
                return new NomadMcpConciergeService.UpstreamHttpResult(
                        200,
                        "{\"results\":[{\"name\":\"Oaxaca\",\"country\":\"Mexico\",\"latitude\":17.0732,\"longitude\":-96.7266}]}",
                        null,
                        18L);
            }
            if (url.contains("/v1/forecast")) {
                return new NomadMcpConciergeService.UpstreamHttpResult(
                        200,
                        "{\"current\":{\"temperature_2m\":24.8,\"wind_speed_10m\":9.4,\"weather_code\":2}}",
                        null,
                        22L);
            }
            if (url.contains("/api/rest_v1/page/summary/")) {
                return new NomadMcpConciergeService.UpstreamHttpResult(
                        200,
                        "{\"title\":\"Paris\",\"extract\":\"Paris is renowned for Haussmannian boulevards and modernist museums.\",\"originalimage\":{\"source\":\"https://upload.wikimedia.org/paris-hd.jpg\"}}",
                        null,
                        25L);
            }
            if (url.contains("/search")) {
                return new NomadMcpConciergeService.UpstreamHttpResult(
                        200,
                        "{\"resultCount\":1,\"results\":[{\"trackName\":\"Gymnopédie No. 1\",\"artistName\":\"Erik Satie\",\"previewUrl\":\"https://audio-ssl.itunes.apple.com/satie.m4a\",\"artworkUrl100\":\"https://is1-ssl.mzstatic.com/100x100bb.jpg\"}]}",
                        null,
                        30L);
            }
            return new NomadMcpConciergeService.UpstreamHttpResult(404, null, "Not found", 5L);
        };

        ToolResponse geocodedWeather = service.fetchCityWeather("Oaxaca");
        assertFalse(geocodedWeather.isError());
        Map<String, Object> oaxacaData = (Map<String, Object>) geocodedWeather.structuredContent();
        assertEquals("Oaxaca", oaxacaData.get("city"));
        assertEquals("Mexico", oaxacaData.get("country"));
        assertEquals(24.8, oaxacaData.get("temperatureC"));
        assertEquals("Partly Clouded Horizon", oaxacaData.get("condition"));

        ToolResponse parisCulture = service.discoverLocalCultureAndMedia("Paris", "jazz");
        assertFalse(parisCulture.isError());
        Map<String, Object> parisData = (Map<String, Object>) parisCulture.structuredContent();
        assertEquals("Paris", parisData.get("destination"));
        assertEquals("Gymnopédie No. 1", ((Map<?, ?>) parisData.get("audioTrack")).get("trackName"));

        ToolResponse updatedItinerary = service.updateTripItinerary("Paris", "Cour des Vosges", 5, 2100.0);
        assertFalse(updatedItinerary.isError());
        Map<String, Object> tripSnap = (Map<String, Object>) updatedItinerary.structuredContent();
        assertEquals("Paris", tripSnap.get("activeCity"));
        assertEquals(5, tripSnap.get("nights"));
        assertEquals(2100.0, tripSnap.get("allocatedUsd"));

        assertEquals("itinerary://active-trip", service.activeTripResource().uri());
        assertEquals("traveler://preferences", service.travelerPreferencesResource().uri());
        assertEquals("itinerary://city/paris/dossier", service.cityDossierResourceTemplate("Paris").uri());
        assertFalse(service.completeWeekendEscapeCity("Pa").values().isEmpty());
        assertFalse(service.completeMusicWalkCity("").values().isEmpty());

        Map<String, Object> prefs = service.updateTravelerPreferences(Map.of(
                "travelerName", "Fady",
                "dietary", "Vegetarian",
                "homeCurrency", "EUR"));
        assertEquals("Fady", prefs.get("travelerName"));
        assertEquals("EUR", prefs.get("homeCurrency"));

        Map<String, Object> health = service.checkHealth();
        assertEquals("UP", health.get("status"));
        assertNotNull(health.get("upstreams"));
    }
}
