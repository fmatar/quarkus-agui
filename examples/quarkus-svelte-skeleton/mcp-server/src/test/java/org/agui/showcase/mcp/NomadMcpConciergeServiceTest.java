package org.agui.showcase.mcp;

import com.sun.net.httpserver.HttpServer;
import io.quarkiverse.mcp.server.ToolResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NomadMcpConciergeServiceTest {

    private NomadMcpConciergeService service;
    private McpServerResource resource;
    private NomadMcpReadinessCheck readinessCheck;

    @BeforeEach
    void setUp() {
        service = new NomadMcpConciergeService();
        service.init();
        resource = new McpServerResource(service);
        readinessCheck = new NomadMcpReadinessCheck(service);
    }

    @Test
    void initialStateAndResetClearStaleEntriesWithZeroAssumedDefaults() {
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

        service.updateTripItinerary("Paris", "Cour des Vosges", 3, 900.0);
        service.updateTravelerPreferences(Map.of("travelerName", "Fady", "customTrait", "Architecture"));
        assertEquals("Paris", service.getActiveTripSnapshot().get("activeCity"));
        assertEquals("Architecture", service.getTravelerPreferences().get("customTrait"));

        service.resetState();
        assertNull(service.getActiveTripSnapshot().get("activeCity"));
        assertFalse(service.getTravelerPreferences().containsKey("customTrait"));
    }

    @Test
    void upstreamHttpResultBoundariesAndLiveHttpProbeWorkDeterministically() throws Exception {
        assertFalse(new NomadMcpConciergeService.UpstreamHttpResult(199, "{}", null, 1L).isOk());
        assertTrue(new NomadMcpConciergeService.UpstreamHttpResult(200, "{}", null, 1L).isOk());
        assertTrue(new NomadMcpConciergeService.UpstreamHttpResult(299, "{}", "", 1L).isOk());
        assertFalse(new NomadMcpConciergeService.UpstreamHttpResult(300, "{}", null, 1L).isOk());
        assertFalse(new NomadMcpConciergeService.UpstreamHttpResult(200, null, null, 1L).isOk());
        assertFalse(new NomadMcpConciergeService.UpstreamHttpResult(200, "{}", "err", 1L).isOk());

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", exchange -> {
            byte[] bytes = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.createContext("/fail", exchange -> {
            byte[] bytes = "upstream error".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(502, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        int port = server.getAddress().getPort();
        try {
            service.setHttpProbe(null);
            NomadMcpConciergeService.UpstreamHttpResult ok =
                    service.probeUrlViaHttp("http://127.0.0.1:" + port + "/ok");
            assertTrue(ok.isOk());
            assertEquals(200, ok.statusCode());
            assertEquals("{\"ok\":true}", ok.body());
            assertTrue(ok.latencyMs() >= 1L);

            NomadMcpConciergeService.UpstreamHttpResult bad =
                    service.probeUrlViaHttp("http://127.0.0.1:" + port + "/fail");
            assertFalse(bad.isOk());
            assertEquals(502, bad.statusCode());
            assertTrue(bad.error().contains("HTTP 502"));
        } finally {
            server.stop(0);
        }

        NomadMcpConciergeService.UpstreamHttpResult refused =
                service.probeUrlViaHttp("http://127.0.0.1:" + port + "/closed");
        assertFalse(refused.isOk());
        assertEquals(0, refused.statusCode());
        assertNotNull(refused.error());
    }

    @Test
    @SuppressWarnings("unchecked")
    void toolsRejectMissingRequiredArgumentsAndValidateBoundaries() {
        assertTrue(service.fetchCityWeather(null).isError());
        ToolResponse missingCityWeather = service.fetchCityWeather("  ");
        assertTrue(missingCityWeather.isError());
        Map<String, Object> errWeather = (Map<String, Object>) missingCityWeather.structuredContent();
        assertEquals("ERROR", errWeather.get("status"));
        assertEquals("MISSING_REQUIRED_ARGUMENTS", errWeather.get("code"));

        assertTrue(service.discoverLocalCultureAndMedia(null, null).isError());
        ToolResponse missingDestCulture = service.discoverLocalCultureAndMedia("   ", null);
        assertTrue(missingDestCulture.isError());
        Map<String, Object> errCulture = (Map<String, Object>) missingDestCulture.structuredContent();
        assertEquals("MISSING_REQUIRED_ARGUMENTS", errCulture.get("code"));

        assertTrue(service.updateTripItinerary(null, "Hotel", 2, 100.0).isError());
        assertTrue(service.updateTripItinerary(" ", "Hotel", 2, 100.0).isError());
        assertTrue(service.updateTripItinerary("Paris", null, 2, 100.0).isError());
        assertTrue(service.updateTripItinerary("Paris", "  ", 2, 100.0).isError());
        assertTrue(service.updateTripItinerary("Paris", "Cour des Vosges", null, 1400.0).isError());
        assertTrue(service.updateTripItinerary("Paris", "Cour des Vosges", 0, 1400.0).isError());
        assertTrue(service.updateTripItinerary("Paris", "Cour des Vosges", -1, 1400.0).isError());
        assertTrue(service.updateTripItinerary("Paris", "Cour des Vosges", 5, null).isError());
        assertTrue(service.updateTripItinerary("Paris", "Cour des Vosges", 5, -0.01).isError());

        // Exact zero costUsd and 1 night and >30 nights clamping boundaries
        ToolResponse minValid = service.updateTripItinerary("Paris", " Studio ", 1, 0.0);
        assertFalse(minValid.isError());
        Map<String, Object> minSnap = (Map<String, Object>) minValid.structuredContent();
        assertEquals(1, minSnap.get("nights"));
        assertEquals(0.0, minSnap.get("allocatedUsd"));
        assertEquals("Studio", minSnap.get("stopTitle"));

        ToolResponse clamped = service.updateTripItinerary("Paris", "Long Stay", 45, 5000.0);
        assertFalse(clamped.isError());
        assertEquals(30, ((Map<String, Object>) clamped.structuredContent()).get("nights"));

        assertTrue(service.dispatchTool(null).isError());
        assertTrue(service.dispatchTool(new McpToolInvocationRequest(null, null)).isError());
        assertTrue(service.dispatchTool(new McpToolInvocationRequest("fetchCityWeather", Map.of())).isError());
        assertTrue(service.dispatchTool(new McpToolInvocationRequest("nonExistentTool", Map.of())).isError());

        McpToolInvocationResponse badNumericStrings = service.dispatchTool(
                new McpToolInvocationRequest("updateTripItinerary", Map.of(
                        "city", "Paris",
                        "stopTitle", "Hotel",
                        "nights", "not-a-number",
                        "costUsd", "invalid")));
        assertTrue(badNumericStrings.isError());
        assertTrue(badNumericStrings.latencyMs() >= 1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void upstreamFailuresAndPartialCultureDegradationSurfaceStructuredErrors() {
        service.setHttpProbe(url -> new NomadMcpConciergeService.UpstreamHttpResult(
                503, null, null, 12L));

        ToolResponse unknownCityWeather = service.fetchCityWeather("AtlantisUnknown");
        assertTrue(unknownCityWeather.isError());

        ToolResponse failedWeather = service.fetchCityWeather("Lisbon");
        assertTrue(failedWeather.isError());
        Map<String, Object> weatherErr = (Map<String, Object>) failedWeather.structuredContent();
        assertEquals("ERROR", weatherErr.get("status"));
        assertEquals(false, weatherErr.get("serviceAvailable"));
        assertEquals("api.open-meteo.com", weatherErr.get("upstream"));
        assertTrue(String.valueOf(weatherErr.get("error")).contains("HTTP 503"));
        assertNull(service.getActiveTripSnapshot().get("temperatureC"));

        ToolResponse failedCulture = service.discoverLocalCultureAndMedia("Lisbon", "fado");
        assertTrue(failedCulture.isError());
        Map<String, Object> cultureErr = (Map<String, Object>) failedCulture.structuredContent();
        assertEquals("ERROR", cultureErr.get("status"));
        assertEquals("ERROR", cultureErr.get("wikipediaStatus"));
        assertEquals("ERROR", cultureErr.get("itunesStatus"));

        assertEquals("DOWN", service.checkHealth().get("status"));

        // Invalid JSON / missing temperature_2m in Open-Meteo
        service.setHttpProbe(url -> new NomadMcpConciergeService.UpstreamHttpResult(200, "{\"current\":{}}", null, 5L));
        assertTrue(service.fetchCityWeather("Lisbon").isError());
        service.setHttpProbe(url -> new NomadMcpConciergeService.UpstreamHttpResult(200, "{broken-json", null, 5L));
        assertTrue(service.fetchCityWeather("Lisbon").isError());
        assertTrue(service.fetchCityWeather("UnknownGeoJson").isError());

        // Partial culture: Wikipedia OK (via thumbnail fallback) + iTunes empty results
        service.setHttpProbe(url -> {
            if (url.contains("wikipedia")) {
                return new NomadMcpConciergeService.UpstreamHttpResult(
                        200,
                        "{\"extract\":\"Zurich architectural guildhalls.\",\"thumbnail\":{\"source\":\"https://upload.wikimedia.org/thumb.jpg\"}}",
                        null,
                        10L);
            }
            return new NomadMcpConciergeService.UpstreamHttpResult(200, "{\"results\":[{\"previewUrl\":\"\"}]}", null, 10L);
        });
        ToolResponse wikiOnly = service.discoverLocalCultureAndMedia("Zurich", "");
        assertFalse(wikiOnly.isError());
        Map<String, Object> wikiOnlyData = (Map<String, Object>) wikiOnly.structuredContent();
        assertEquals("PARTIAL", wikiOnlyData.get("status"));
        assertEquals("Zurich acoustic", wikiOnlyData.get("genre"));
        assertEquals("OK", wikiOnlyData.get("wikipediaStatus"));
        assertEquals("ERROR", wikiOnlyData.get("itunesStatus"));
        assertEquals("https://upload.wikimedia.org/thumb.jpg", wikiOnlyData.get("heroImageUrl"));
        assertNotNull(wikiOnlyData.get("itunesError"));

        // Partial culture: Wikipedia blank extract + iTunes OK with custom artist query + DEGRADED health
        service.setHttpProbe(url -> {
            if (url.contains("wikipedia")) {
                return new NomadMcpConciergeService.UpstreamHttpResult(200, "{\"extract\":\"   \"}", null, 10L);
            }
            if (url.contains("itunes")) {
                return new NomadMcpConciergeService.UpstreamHttpResult(
                        200,
                        "{\"results\":[{\"trackName\":\"Blue in Green\",\"artistName\":\"Miles Davis\",\"previewUrl\":\"https://itunes.example/miles.m4a\",\"artworkUrl100\":\"https://img/100x100bb.jpg\"}]}",
                        null,
                        10L);
            }
            return new NomadMcpConciergeService.UpstreamHttpResult(503, null, "Open-Meteo down", 10L);
        });
        ToolResponse itunesOnly = service.discoverLocalCultureAndMedia("Zurich", "Miles Davis Quartet");
        assertFalse(itunesOnly.isError());
        Map<String, Object> itunesOnlyData = (Map<String, Object>) itunesOnly.structuredContent();
        assertEquals("PARTIAL", itunesOnlyData.get("status"));
        assertEquals("Miles Davis Quartet", itunesOnlyData.get("genre"));
        assertEquals("ERROR", itunesOnlyData.get("wikipediaStatus"));
        assertEquals("OK", itunesOnlyData.get("itunesStatus"));
        assertNotNull(itunesOnlyData.get("wikipediaError"));
        assertEquals("DEGRADED", service.checkHealth().get("status"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void liveUpstreamResponsesAndMcpServerResourceEndpointsWorkEndToEnd() {
        service.setHttpProbe(url -> {
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
                        "{\"title\":\"Paris\",\"extract\":\"Paris is renowned for Haussmannian boulevards.\",\"originalimage\":{\"source\":\"https://upload.wikimedia.org/paris-hd.jpg\"}}",
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
        });

        ToolResponse geocodedWeather = service.fetchCityWeather("Oaxaca");
        assertFalse(geocodedWeather.isError());
        Map<String, Object> oaxacaData = (Map<String, Object>) geocodedWeather.structuredContent();
        assertEquals("Oaxaca", oaxacaData.get("city"));
        assertEquals("Mexico", oaxacaData.get("country"));
        assertEquals(24.8, oaxacaData.get("temperatureC"));
        assertEquals("Partly Clouded Horizon", oaxacaData.get("condition"));

        McpToolInvocationResponse cultureDispatch = resource.callTool(
                new McpToolInvocationRequest("discoverLocalCultureAndMedia", Map.of("destination", "Paris", "genre", "jazz")));
        assertFalse(cultureDispatch.isError());
        Map<String, Object> parisData = (Map<String, Object>) cultureDispatch.result();
        assertEquals("Erik Satie piano", parisData.get("genre"));
        assertEquals("Gymnopédie No. 1", ((Map<?, ?>) parisData.get("audioTrack")).get("trackName"));

        // Destination with country suffix ("Beirut, Lebanon") and multi-word travel focus ("architecture and oud music")
        ToolResponse beirutWithCountry = service.discoverLocalCultureAndMedia("Beirut, Lebanon", "architecture and oud music");
        assertFalse(beirutWithCountry.isError());
        Map<String, Object> beirutCulture = (Map<String, Object>) beirutWithCountry.structuredContent();
        assertEquals("Beirut", beirutCulture.get("destination"));
        assertEquals("Fairuz", beirutCulture.get("genre"));
        assertNotNull(beirutCulture.get("heroImageUrl"));
        assertNotNull(beirutCulture.get("audioTrack"));

        // Custom genre returning 0 iTunes tracks automatically falls back to city curated regional music query
        service.setHttpProbe(url -> {
            if (url.contains("/api/rest_v1/page/summary/Kyoto")) {
                return new NomadMcpConciergeService.UpstreamHttpResult(
                        200,
                        "{\"title\":\"Kyoto\",\"extract\":\"Kyoto wooden machiya architecture.\",\"originalimage\":{\"source\":\"https://upload.wikimedia.org/kyoto.jpg\"}}",
                        null,
                        15L);
            }
            if (url.contains("term=ObscureNonexistentGenre")) {
                return new NomadMcpConciergeService.UpstreamHttpResult(200, "{\"resultCount\":0,\"results\":[]}", null, 15L);
            }
            if (url.contains("term=koto+shakuhachi")) {
                return new NomadMcpConciergeService.UpstreamHttpResult(
                        200,
                        "{\"resultCount\":1,\"results\":[{\"trackName\":\"Rokudan no Shirabe\",\"artistName\":\"Yatsuhashi Kengyo\",\"previewUrl\":\"https://audio-ssl.itunes.apple.com/koto.m4a\",\"artworkUrl100\":\"https://is1-ssl.mzstatic.com/100x100bb.jpg\"}]}",
                        null,
                        15L);
            }
            if (url.contains("/v1/forecast")) {
                return new NomadMcpConciergeService.UpstreamHttpResult(
                        200,
                        "{\"current\":{\"temperature_2m\":18.2,\"wind_speed_10m\":6.1,\"weather_code\":1}}",
                        null,
                        15L);
            }
            if (url.contains("/api/rest_v1/page/summary/")) {
                return new NomadMcpConciergeService.UpstreamHttpResult(
                        200,
                        "{\"title\":\"Paris\",\"extract\":\"Paris is renowned for Haussmannian boulevards.\",\"originalimage\":{\"source\":\"https://upload.wikimedia.org/paris-hd.jpg\"}}",
                        null,
                        15L);
            }
            if (url.contains("/search")) {
                return new NomadMcpConciergeService.UpstreamHttpResult(
                        200,
                        "{\"resultCount\":1,\"results\":[{\"trackName\":\"Gymnopédie No. 1\",\"artistName\":\"Erik Satie\",\"previewUrl\":\"https://audio-ssl.itunes.apple.com/satie.m4a\",\"artworkUrl100\":\"https://is1-ssl.mzstatic.com/100x100bb.jpg\"}]}",
                        null,
                        15L);
            }
            return new NomadMcpConciergeService.UpstreamHttpResult(404, null, "Not found", 5L);
        });
        ToolResponse kyotoFallback = service.discoverLocalCultureAndMedia("Kyoto, Japan", "ObscureNonexistentGenre");
        assertFalse(kyotoFallback.isError());
        Map<String, Object> kyotoCulture = (Map<String, Object>) kyotoFallback.structuredContent();
        assertEquals("Kyoto", kyotoCulture.get("destination"));
        assertEquals("koto shakuhachi", kyotoCulture.get("genre"));
        assertEquals("https://upload.wikimedia.org/kyoto.jpg", kyotoCulture.get("heroImageUrl"));
        assertEquals("https://upload.wikimedia.org/kyoto.jpg", service.getActiveTripSnapshot().get("heroImageUrl"));
        assertEquals("Rokudan no Shirabe", ((Map<?, ?>) kyotoCulture.get("audioTrack")).get("trackName"));

        // Placeholder genre ("empty string", "none") must be sanitized and resolved to curated regional music
        ToolResponse kyotoEmptyStringGenre = service.discoverLocalCultureAndMedia("Kyoto", "empty string");
        assertFalse(kyotoEmptyStringGenre.isError());
        Map<String, Object> kyotoEmptyGenreData = (Map<String, Object>) kyotoEmptyStringGenre.structuredContent();
        assertEquals("koto shakuhachi", kyotoEmptyGenreData.get("genre"));
        assertEquals("Rokudan no Shirabe", ((Map<?, ?>) kyotoEmptyGenreData.get("audioTrack")).get("trackName"));

        // Misrouted destination city in saveTravelerPreferences(travelerName = "Kyoto") must not pollute travelerName
        service.resetState();
        ToolResponse misroutedCityPref = service.saveTravelerPreferences("Kyoto", null, null, null, null, null);
        assertFalse(misroutedCityPref.isError());
        assertNull(service.getTravelerPreferences().get("travelerName"));
        assertEquals("Kyoto", service.getActiveTripSnapshot().get("activeCity"));

        McpToolInvocationResponse weatherDispatch = resource.callTool(
                new McpToolInvocationRequest("fetchCityWeather", Map.of("city", "Kyoto")));
        assertFalse(weatherDispatch.isError());

        McpToolInvocationResponse numericItinerary = resource.callTool(
                new McpToolInvocationRequest("updateTripItinerary", Map.of(
                        "city", "Paris",
                        "stopTitle", "Cour des Vosges",
                        "nights", 5,
                        "costUsd", 2100.0)));
        assertFalse(numericItinerary.isError());
        assertEquals(5, resource.getItinerary().get("nights"));

        McpToolInvocationResponse stringTypedItinerary = resource.callTool(
                new McpToolInvocationRequest("updateTripItinerary", Map.of(
                        "city", "Beirut",
                        "stopTitle", "Albergo Achrafieh",
                        "nights", "4",
                        "costUsd", "1650.5")));
        assertFalse(stringTypedItinerary.isError());
        assertEquals("Beirut", resource.getItinerary().get("activeCity"));

        assertTrue(service.activeTripResource().text().contains("Beirut"));
        assertTrue(service.travelerPreferencesResource().text().contains("traveler://preferences"));
        assertEquals("itinerary://city/paris/dossier", service.cityDossierResourceTemplate("Paris").uri());
        assertEquals("itinerary://city/missing/dossier", service.cityDossierResourceTemplate(null).uri());
        assertEquals("itinerary://city/missing/dossier", service.cityDossierResourceTemplate(" ").uri());

        Map<String, Object> cityDossier = resource.getCityDossier("Zurich");
        assertTrue(String.valueOf(cityDossier.get("architecturalHighlight")).contains("Zurich Historic"));
        assertEquals("ERROR", resource.getCityDossier(null).get("status"));
        assertEquals("ERROR", resource.getCityDossier("").get("status"));

        assertEquals(8, service.completeCityArgument(null).size());
        assertEquals(8, service.completeCityArgument("  ").size());
        assertTrue(service.completeCityArgument("ky").contains("Kyoto"));
        assertTrue(service.completeCityArgument("celon").contains("Barcelona"));
        assertEquals(List.of("Zurich"), service.completeCityArgument("zurich"));
        assertFalse(service.completeWeekendEscapeCity("Pa").values().isEmpty());
        assertFalse(service.completeMusicWalkCity("").values().isEmpty());

        Map<String, Object> completeRes = resource.completePromptArgument("Li");
        assertEquals("city", completeRes.get("argument"));
        assertEquals(((List<?>) completeRes.get("values")).size(), completeRes.get("total"));

        assertEquals(2, ((List<?>) resource.listPrompts().get("prompts")).size());
        assertEquals(6, ((List<?>) resource.listTools().get("tools")).size());
        java.util.concurrent.atomic.AtomicInteger healthProbes = new java.util.concurrent.atomic.AtomicInteger();
        var baseProbe = service.httpProbe;
        service.healthCacheTtlMs = 300_000L;
        service.setHttpProbe(url -> {
            healthProbes.incrementAndGet();
            return baseProbe.apply(url);
        });
        assertEquals("UP", resource.health().get("status"));
        assertEquals(3, healthProbes.get());
        assertEquals(
                org.eclipse.microprofile.health.HealthCheckResponse.Status.UP,
                readinessCheck.call().getStatus());
        assertEquals(3, healthProbes.get(), "Repeated readiness check within TTL must use cached upstream health report");
        assertEquals("nomad-mcp-upstreams", readinessCheck.call().getName());

        Map<String, Object> updates = new HashMap<>();
        updates.put("travelerName", "Fady");
        updates.put("blankKey", "   ");
        updates.put("nullVal", null);
        resource.updatePreferences(updates);
        resource.updatePreferences(null);
        assertEquals("Fady", resource.getPreferences().get("travelerName"));
        assertFalse(resource.getPreferences().containsKey("blankKey"));

        // Native MCP @Tool methods for acoustic sampling, preferences, and state reset
        assertTrue(service.sampleAcousticMood(null, null, null).isError());
        assertTrue(service.sampleAcousticMood("  ", "Clear", "jazz").isError());
        ToolResponse sampledTool = service.sampleAcousticMood("Beirut", "Clear", "oud");
        assertFalse(sampledTool.isError());
        assertEquals("Beirut", ((Map<String, Object>) sampledTool.structuredContent()).get("city"));
        assertFalse(service.dispatchTool(new McpToolInvocationRequest(
                "sampleAcousticMood", Map.of("city", "Lisbon", "weatherCondition", "Sunny", "genre", "fado"))).isError());

        ToolResponse noopPrefs = service.saveTravelerPreferences(
                null, " ", "empty string", "not provided", "Beirut, Lebanon", "   ");
        assertFalse(noopPrefs.isError());
        Map<String, Object> noopPrefsMap = (Map<String, Object>) noopPrefs.structuredContent();
        assertEquals("OK", noopPrefsMap.get("status"));
        assertEquals(false, noopPrefsMap.get("updated"));
        assertEquals("Fady", noopPrefsMap.get("travelerName"));

        ToolResponse savedPrefsTool = service.saveTravelerPreferences(
                "Fady", "Architecture", "Mediterranean", "Oud & Jazz", "Unhurried", "EUR");
        assertFalse(savedPrefsTool.isError());
        Map<String, Object> savedPrefsMap = (Map<String, Object>) savedPrefsTool.structuredContent();
        assertEquals("OK", savedPrefsMap.get("status"));
        assertEquals(true, savedPrefsMap.get("updated"));
        assertEquals("Fady", savedPrefsMap.get("travelerName"));
        assertEquals("Architecture", savedPrefsMap.get("style"));
        assertEquals("EUR", savedPrefsMap.get("homeCurrency"));
        assertFalse(service.dispatchTool(new McpToolInvocationRequest(
                "saveTravelerPreferences", Map.of("pace", "Relaxed", "dietary", "Pescatarian"))).isError());

        ToolResponse resetTool = service.resetNomadState();
        assertFalse(resetTool.isError());
        assertNull(service.getActiveTripSnapshot().get("activeCity"));
        assertNull(service.getTravelerPreferences().get("travelerName"));
        assertFalse(service.dispatchTool(new McpToolInvocationRequest("resetNomadState", Map.of())).isError());

        assertNotNull(service.weekendCulturalEscapePrompt("Paris").content());
        assertNotNull(service.localMusicAndFoodWalkPrompt("Beirut").content());
        assertThrows(IllegalArgumentException.class, () -> service.weekendCulturalEscapePrompt(null));
        assertThrows(IllegalArgumentException.class, () -> service.weekendCulturalEscapePrompt(" "));
        assertThrows(IllegalArgumentException.class, () -> service.localMusicAndFoodWalkPrompt(null));
        assertThrows(IllegalArgumentException.class, () -> service.localMusicAndFoodWalkPrompt(""));

        assertEquals("ERROR", resource.sampleAcousticMood(null).get("status"));
        assertEquals("ERROR", resource.sampleAcousticMood(Map.of()).get("status"));
        Map<String, Object> beirutMood = resource.sampleAcousticMood(
                Map.of("city", "Beirut", "weatherCondition", "Clear", "genre", "oud"));
        assertEquals("OK", beirutMood.get("status"));
        assertTrue(String.valueOf(beirutMood.get("sampledMood")).contains("Fairuz"));

        Map<String, Object> customMood = resource.sampleAcousticMood(Map.of("city", "Oaxaca"));
        assertEquals("OK", customMood.get("status"));
        assertEquals("Ambient local sky", customMood.get("weatherCondition"));
        assertEquals("regional acoustic", customMood.get("genre"));

        // Verify readiness check reports DOWN with upstream error details when an upstream fails
        service.setHttpProbe(url -> new NomadMcpConciergeService.UpstreamHttpResult(503, null, "Upstream offline", 9L));
        var downCheck = readinessCheck.call();
        assertEquals(org.eclipse.microprofile.health.HealthCheckResponse.Status.DOWN, downCheck.getStatus());
        assertEquals("Upstream offline", downCheck.getData().orElseThrow().get("open-meteo.error"));
    }

    @Test
    void checkHealthRunsThreeUpstreamsInParallelWarmsOnStartupAndCapsDegradedCacheTtl() throws Exception {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        service.healthCacheTtlMs = 300_000L;
        service.setHttpProbe(url -> {
            calls.incrementAndGet();
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new NomadMcpConciergeService.UpstreamHttpResult(200, "{\"ok\":true}", null, 150L);
        });

        long t0 = System.nanoTime();
        Map<String, Object> report = service.checkHealth();
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
        assertEquals("UP", report.get("status"));
        assertEquals(3, calls.get());
        assertTrue(elapsedMs < 360, "Expected parallel MCP health probes (<360ms), took " + elapsedMs + "ms");

        calls.set(0);
        service.setHttpProbe(url -> {
            calls.incrementAndGet();
            return new NomadMcpConciergeService.UpstreamHttpResult(200, "{\"ok\":true}", null, 5L);
        });
        service.warmupOnStart = false;
        service.onStartup(new io.quarkus.runtime.StartupEvent());
        Thread.sleep(60);
        assertEquals(0, calls.get());

        service.warmupOnStart = true;
        service.onStartup(new io.quarkus.runtime.StartupEvent());
        long deadline = System.currentTimeMillis() + 1500L;
        while (calls.get() < 3 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(3, calls.get());

        service.healthCacheTtlMs = 300_000L;
        service.degradedHealthCacheTtlMs = 40L;
        service.setHttpProbe(url -> {
            calls.incrementAndGet();
            return new NomadMcpConciergeService.UpstreamHttpResult(503, null, "Temporary blip", 5L);
        });
        calls.set(0);
        assertEquals("DOWN", service.checkHealth().get("status"));
        assertEquals(3, calls.get());
        Thread.sleep(60);
        service.checkHealth();
        assertEquals(6, calls.get(), "Degraded MCP health report must expire quickly");
    }

    @Test
    @SuppressWarnings("unchecked")
    void normalizesSpaceSeparatedCityAndCountryInputsAcrossWeatherCultureAndGeocoding() {
        service.setHttpProbe(url -> {
            if (url.contains("/v1/forecast")) {
                return new NomadMcpConciergeService.UpstreamHttpResult(
                        200,
                        "{\"current\":{\"temperature_2m\":16.5,\"wind_speed_10m\":12.0,\"weather_code\":1}}",
                        null,
                        12L);
            }
            if (url.contains("/v1/search?name=Oaxaca+Mexico") || url.contains("/v1/search?name=Oaxaca+State")) {
                return new NomadMcpConciergeService.UpstreamHttpResult(200, "{}", null, 10L);
            }
            if (url.contains("/v1/search?name=Oaxaca")) {
                return new NomadMcpConciergeService.UpstreamHttpResult(
                        200,
                        "{\"results\":[{\"name\":\"Oaxaca\",\"country\":\"Mexico\",\"latitude\":17.0732,\"longitude\":-96.7266}]}",
                        null,
                        12L);
            }
            if (url.contains("/api/rest_v1/page/summary/Paris")) {
                return new NomadMcpConciergeService.UpstreamHttpResult(
                        200,
                        "{\"title\":\"Paris\",\"extract\":\"Paris historic stone bridges.\",\"originalimage\":{\"source\":\"https://upload.wikimedia.org/paris.jpg\"}}",
                        null,
                        15L);
            }
            if (url.contains("itunes.apple.com/search")) {
                return new NomadMcpConciergeService.UpstreamHttpResult(
                        200,
                        "{\"resultCount\":1,\"results\":[{\"trackName\":\"La Vie En Rose\",\"artistName\":\"Edith Piaf\",\"previewUrl\":\"https://itunes.example/piaf.m4a\"}]}",
                        null,
                        15L);
            }
            return new NomadMcpConciergeService.UpstreamHttpResult(404, null, "Not found: " + url, 5L);
        });

        ToolResponse parisFranceWeather = service.fetchCityWeather("Paris France");
        assertFalse(parisFranceWeather.isError(), "Expected Paris France weather to succeed");
        Map<String, Object> parisData = (Map<String, Object>) parisFranceWeather.structuredContent();
        assertEquals("Paris", parisData.get("city"));
        assertEquals("France", parisData.get("country"));
        assertEquals(16.5, parisData.get("temperatureC"));

        ToolResponse nyUsaWeather = service.fetchCityWeather("New York United States");
        assertFalse(nyUsaWeather.isError());
        assertEquals("New York", ((Map<String, Object>) nyUsaWeather.structuredContent()).get("city"));

        ToolResponse oaxacaMexicoWeather = service.fetchCityWeather("Oaxaca Mexico");
        assertFalse(oaxacaMexicoWeather.isError(), "Multi-word country suffix should resolve Oaxaca from 'Oaxaca Mexico'");
        assertEquals("Oaxaca", ((Map<String, Object>) oaxacaMexicoWeather.structuredContent()).get("city"));

        ToolResponse oaxacaStateWeather = service.fetchCityWeather("Oaxaca State");
        assertFalse(oaxacaStateWeather.isError(), "Prefix geocoding fallback should resolve Oaxaca from 'Oaxaca State'");
        assertEquals("Oaxaca", ((Map<String, Object>) oaxacaStateWeather.structuredContent()).get("city"));

        ToolResponse parisFranceCulture = service.discoverLocalCultureAndMedia("Paris France", null);
        assertFalse(parisFranceCulture.isError());
        assertEquals("Paris", ((Map<String, Object>) parisFranceCulture.structuredContent()).get("destination"));

        McpToolInvocationResponse parisCityAliasCulture = service.dispatchTool(
                new McpToolInvocationRequest("discoverLocalCultureAndMedia", Map.of("city", "Paris France")));
        assertFalse(parisCityAliasCulture.isError(), "discoverLocalCultureAndMedia should accept 'city' alias when LLM omits 'destination'");
        assertEquals("Paris", ((Map<String, Object>) parisCityAliasCulture.result()).get("destination"));

        assertTrue(service.discoverLocalCultureAndMedia("  ", null, "  ").isError());
    }
}
