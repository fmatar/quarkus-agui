package org.agui.showcase.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClusterTelemetryServiceTest {

    private ClusterTelemetryService service;

    @BeforeEach
    void setUp() {
        service = new ClusterTelemetryService();
        service.resetState();
    }

    @Test
    void inspectClusterHealthReturnsRegionAndThreeServices() {
        Map<String, Object> health = service.inspectClusterHealth("us-west-2");
        assertEquals("us-west-2", health.get("region"));
        assertEquals("DEGRADED", health.get("overallStatus"));
        @SuppressWarnings("unchecked")
        List<ServiceStatus> services = (List<ServiceStatus>) health.get("services");
        assertEquals(3, services.size());
    }

    @Test
    void inspectClusterHealthDefaultsBlankRegionToUsEast1() {
        Map<String, Object> health = service.inspectClusterHealth("   ");
        assertEquals("us-east-1", health.get("region"));
    }

    @Test
    void queryServiceLogsFiltersByServiceAndSeverityAndRespectsLimit() {
        List<LogEntry> errorLogs = service.queryServiceLogs("payment-service", "ERROR", 5);
        assertFalse(errorLogs.isEmpty());
        assertTrue(errorLogs.size() <= 5);
        for (LogEntry entry : errorLogs) {
            assertEquals("payment-service", entry.serviceName());
            assertEquals("ERROR", entry.severity());
        }

        List<LogEntry> limited = service.queryServiceLogs("payment-service", "ALL", 1);
        assertEquals(1, limited.size());
    }

    @Test
    void scaleDeploymentUpdatesReplicaCountAndCpuUtilization() {
        ServiceStatus updated = service.scaleDeployment("payment-service", 6);
        assertEquals("payment-service", updated.name());
        assertEquals(6, updated.replicas());
        assertEquals("HEALTHY", updated.status());
        assertTrue(updated.cpuPercent() < 80.0);
    }

    @Test
    void scaleDeploymentRejectsOutOfBoundsReplicasOrUnknownService() {
        assertThrows(IllegalArgumentException.class, () -> service.scaleDeployment("payment-service", 0));
        assertThrows(IllegalArgumentException.class, () -> service.scaleDeployment("payment-service", 21));
        assertThrows(IllegalArgumentException.class, () -> service.scaleDeployment("", 3));
    }

    @Test
    void dispatchToolExecutesSupportedToolsAndHandlesUnknownTool() {
        McpToolInvocationResponse healthRes = service.dispatchTool(
            new McpToolInvocationRequest("inspectClusterHealth", Map.of("region", "eu-central-1"))
        );
        assertFalse(healthRes.isError());
        assertEquals("cluster-telemetry-mcp", healthRes.serverName());
        assertEquals("inspectClusterHealth", healthRes.tool());
        assertNotNull(healthRes.result());

        McpToolInvocationResponse logsRes = service.dispatchTool(
            new McpToolInvocationRequest("queryServiceLogs", Map.of("serviceName", "payment-service", "severity", "ERROR", "limit", 2))
        );
        assertFalse(logsRes.isError());

        McpToolInvocationResponse scaleRes = service.dispatchTool(
            new McpToolInvocationRequest("scaleDeployment", Map.of("serviceName", "payment-service", "targetReplicas", 4))
        );
        assertFalse(scaleRes.isError());

        McpToolInvocationResponse unknownRes = service.dispatchTool(
            new McpToolInvocationRequest("nonExistentTool", Map.of())
        );
        assertTrue(unknownRes.isError());

        var resourceContents = service.clusterTopologyResource();
        assertEquals("cluster://topology", resourceContents.uri());
        assertTrue(resourceContents.text().contains("payment-service"));
    }

    @Test
    void nomadWeatherCultureAndItineraryToolsAndResourcesWorkDeterministicallyAndViaLiveHttp() {
        // 1. Default httpFetcher fallback path when upstream is unreachable
        service.openMeteoBaseUrl = "http://127.0.0.1:1";
        service.wikipediaBaseUrl = "http://127.0.0.1:1";
        service.itunesBaseUrl = "http://127.0.0.1:1";

        Map<String, Object> lisbonWeather = service.fetchCityWeather("Lisbon");
        assertEquals("Lisbon", lisbonWeather.get("city"));
        assertNotNull(lisbonWeather.get("condition"));

        Map<String, Object> reykjavikCulture = service.discoverLocalCultureAndMedia("Reykjavik", "ambient");
        assertEquals("Reykjavik", reykjavikCulture.get("destination"));
        assertNotNull(reykjavikCulture.get("audioTrack"));

        // 2. Live JSON response parsing path via httpFetcher (including Open-Meteo geocoding for unseeded city)
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

        assertFalse(service.dispatchTool(new McpToolInvocationRequest("fetchCityWeather", Map.of("city", "Kyoto"))).isError());
        assertFalse(service.dispatchTool(new McpToolInvocationRequest("discoverLocalCultureAndMedia", Map.of("destination", "Lisbon", "genre", "fado"))).isError());
        assertFalse(service.dispatchTool(new McpToolInvocationRequest("updateTripItinerary", Map.of("city", "Reykjavik", "stopTitle", "Harpa Concert Hall", "nights", 3, "costUsd", 1100))).isError());

        assertEquals("itinerary://active-trip", service.activeTripResource().uri());
        assertEquals("traveler://preferences", service.travelerPreferencesResource().uri());
        assertEquals(2, service.listPrompts().size());
        assertNotNull(service.weekendCulturalEscapePrompt("Kyoto"));
        assertNotNull(service.localMusicAndFoodWalkPrompt("Lisbon"));
        assertTrue(service.renderPrompt("weekend-cultural-escape", "Kyoto").get("prompt").toString().contains("Kyoto"));
        assertTrue(service.renderPrompt("local-music-and-food-walk", "Lisbon").get("prompt").toString().contains("Lisbon"));
    }
}
