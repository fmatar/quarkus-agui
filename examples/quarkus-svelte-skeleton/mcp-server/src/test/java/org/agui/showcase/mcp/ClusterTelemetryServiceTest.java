package org.agui.showcase.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClusterTelemetryServiceTest {

    private ClusterTelemetryService service;

    @BeforeEach
    void setUp() {
        service = new ClusterTelemetryService();
        service.resetState();
    }

    @Test
    void dispatchToolExecutesNomadToolsAndHandlesUnknownTool() {
        McpToolInvocationResponse weatherRes = service.dispatchTool(
            new McpToolInvocationRequest("fetchCityWeather", Map.of("city", "Kyoto"))
        );
        assertFalse(weatherRes.isError());
        assertEquals("nomad-mcp-concierge", weatherRes.serverName());
        assertEquals("fetchCityWeather", weatherRes.tool());
        assertNotNull(weatherRes.result());

        McpToolInvocationResponse cultureRes = service.dispatchTool(
            new McpToolInvocationRequest("discoverLocalCultureAndMedia", Map.of("destination", "Lisbon", "genre", "fado"))
        );
        assertFalse(cultureRes.isError());

        McpToolInvocationResponse itineraryRes = service.dispatchTool(
            new McpToolInvocationRequest("updateTripItinerary", Map.of("city", "Reykjavik", "stopTitle", "Harpa Concert Hall", "nights", 3, "costUsd", 1100))
        );
        assertFalse(itineraryRes.isError());

        McpToolInvocationResponse unknownRes = service.dispatchTool(
            new McpToolInvocationRequest("nonExistentTool", Map.of())
        );
        assertTrue(unknownRes.isError());
    }

    @Test
    void nomadWeatherCultureAndItineraryToolsAndResourcesWorkDeterministicallyAndViaLiveHttp() {
        service.openMeteoBaseUrl = "http://127.0.0.1:1";
        service.openMeteoGeoBaseUrl = "http://127.0.0.1:1";
        service.wikipediaBaseUrl = "http://127.0.0.1:1";
        service.itunesBaseUrl = "http://127.0.0.1:1";

        Map<String, Object> lisbonWeather = service.fetchCityWeather("Lisbon");
        assertEquals("Lisbon", lisbonWeather.get("city"));
        assertNotNull(lisbonWeather.get("condition"));

        Map<String, Object> reykjavikCulture = service.discoverLocalCultureAndMedia("Reykjavik", "ambient");
        assertEquals("Reykjavik", reykjavikCulture.get("destination"));
        assertNotNull(reykjavikCulture.get("audioTrack"));

        Map<String, Object> beirutWeather = service.fetchCityWeather("Beirut");
        assertEquals("Beirut", beirutWeather.get("city"));
        assertEquals("Lebanon", beirutWeather.get("country"));

        for (String city : List.of(
                "Beirut", "Amman", "Dubai", "Cairo", "Athens", "Rio de Janeiro",
                "Tokyo", "Paris", "Rome", "Barcelona", "Marrakech", "Oaxaca",
                "Buenos Aires", "Vienna", "Berlin", "Copenhagen", "Seoul", "Istanbul", "Zurich")) {
            Map<String, Object> c = service.discoverLocalCultureAndMedia(city, "traditional");
            assertEquals(city, c.get("destination"));
        }

        service.httpFetcher = url -> {
            if (url.contains("/v1/search")) {
                return "{\"results\":[{\"name\":\"Oaxaca\",\"country\":\"Mexico\",\"latitude\":17.0732,\"longitude\":-96.7266}]}";
            }
            if (url.contains("/v1/forecast")) {
                return "{\"current\":{\"temperature_2m\":24.8,\"wind_speed_10m\":9.4,\"weather_code\":2}}";
            }
            if (url.contains("/api/rest_v1/page/summary/")) {
                return "{\"title\":\"Kyoto\",\"extract\":\"Kyoto is the cultural heart of Japan.\",\"originalimage\":{\"source\":\"https://upload.wikimedia.org/kyoto-hd.jpg\"},\"thumbnail\":{\"source\":\"https://upload.wikimedia.org/kyoto.jpg\"}}";
            }
            if (url.contains("/search")) {
                return "{\"resultCount\":1,\"results\":[{\"trackName\":\"Sakura Koto\",\"artistName\":\"Kyoto Ensemble\",\"previewUrl\":\"https://audio-ssl.itunes.apple.com/sakura.m4a\",\"artworkUrl100\":\"https://is1-ssl.mzstatic.com/100x100bb.jpg\"}]}";
            }
            return null;
        };

        Map<String, Object> geocodedWeather = service.fetchCityWeather("Oaxaca");
        assertEquals("Oaxaca", geocodedWeather.get("city"));
        assertEquals("Mexico", geocodedWeather.get("country"));
        assertEquals(24.8, geocodedWeather.get("temperatureC"));
        assertEquals("Partly Clouded Horizon", geocodedWeather.get("condition"));

        Map<String, Object> liveWeather = service.fetchCityWeather("Kyoto");
        assertEquals("Kyoto", liveWeather.get("city"));
        assertEquals(24.8, liveWeather.get("temperatureC"));
        assertEquals("api.open-meteo.com", liveWeather.get("source"));

        Map<String, Object> liveCulture = service.discoverLocalCultureAndMedia("Kyoto", "koto");
        assertEquals("Kyoto", liveCulture.get("destination"));
        assertEquals("Sakura Koto", ((Map<?, ?>) liveCulture.get("audioTrack")).get("trackName"));

        Map<String, Object> updatedTrip = service.updateTripItinerary("Kyoto", "Hoshinoya Arashiyama Ryokan", 4, 1840.0);
        assertEquals("Kyoto", updatedTrip.get("activeCity"));
        assertEquals(4, updatedTrip.get("nights"));

        Map<String, Object> defaultStopTrip = service.updateTripItinerary("", "", 0, -50.0);
        assertEquals("Kyoto", defaultStopTrip.get("activeCity"));
        assertEquals(1, defaultStopTrip.get("nights"));

        assertEquals("itinerary://active-trip", service.activeTripResource().uri());
        assertEquals("traveler://preferences", service.travelerPreferencesResource().uri());
        assertEquals("itinerary://city/lisbon/dossier", service.cityDossierResourceTemplate("Lisbon").uri());
        assertEquals("itinerary://city/beirut/dossier", service.cityDossierResourceTemplate("Beirut").uri());
        assertEquals("itinerary://city/tokyo/dossier", service.cityDossierResourceTemplate("Tokyo").uri());
        assertEquals("itinerary://city/oaxaca/dossier", service.cityDossierResourceTemplate("Oaxaca").uri());
        assertEquals("itinerary://city/reykjavik/dossier", service.cityDossierResourceTemplate("Reykjavik").uri());
        assertFalse(service.completeWeekendEscapeCity("Be").values().isEmpty());
        assertFalse(service.completeMusicWalkCity("").values().isEmpty());
        assertFalse(service.completeCityArgument("ZzzUnknownCity").isEmpty());

        Map<String, Object> updatedPrefs = service.updateTravelerPreferences(Map.of(
                "dietary", "Vegetarian",
                "homeCurrency", "EUR"));
        assertEquals("Vegetarian", updatedPrefs.get("dietary"));
        assertEquals("EUR", updatedPrefs.get("homeCurrency"));

        Map<String, Object> sampled = service.sampleAcousticMoodProfile("Lisbon", "Golden Sun", "fado");
        assertEquals("mcp:sampling", sampled.get("protocol"));
        assertEquals("Lisbon", sampled.get("city"));

        assertEquals(2, service.listPrompts().size());
        assertEquals(3, service.listTools().size());
        assertNotNull(service.weekendCulturalEscapePrompt("Kyoto"));
        assertNotNull(service.localMusicAndFoodWalkPrompt("Lisbon"));
        assertTrue(service.renderPrompt("weekend-cultural-escape", "Kyoto").get("prompt").toString().contains("Kyoto"));
        assertTrue(service.renderPrompt("local-music-and-food-walk", "Lisbon").get("prompt").toString().contains("Lisbon"));
    }
}
