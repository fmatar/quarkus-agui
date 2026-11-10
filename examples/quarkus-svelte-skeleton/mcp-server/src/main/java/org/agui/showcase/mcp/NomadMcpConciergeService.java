package org.agui.showcase.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.mcp.server.CompleteArg;
import io.quarkiverse.mcp.server.CompletePrompt;
import io.quarkiverse.mcp.server.CompletionResponse;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.ResourceTemplateArg;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Quarkus MCP Server (:8081) for the Nomad Travel, Weather, Local Culture & Itinerary Concierge.
 * Exposes native Quarkus MCP Tools (@Tool returning {@link ToolResponse}), Resources (@Resource),
 * Resource Templates (@ResourceTemplate), Prompt Templates (@Prompt), Prompt Completions (@CompletePrompt),
 * and Upstream Dependency Healthchecks without silent fake fallbacks or assumed defaults.
 */
@ApplicationScoped
public class NomadMcpConciergeService {

    public static final String SERVER_NAME = "nomad-mcp-concierge";

    private static final List<String> CURATED_GLOBAL_CITIES = List.of(
            "Beirut", "Kyoto", "Lisbon", "Reykjavik", "Tokyo", "Oaxaca", "Paris", "Rome",
            "Barcelona", "Marrakech", "Copenhagen", "Vienna", "Berlin", "Seoul", "Istanbul",
            "Buenos Aires", "Amman", "Cairo", "Athens", "Rio de Janeiro");

    private final Map<String, Object> activeTrip = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<String, Object> travelerPreferences = Collections.synchronizedMap(new LinkedHashMap<>());
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(1800))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    String openMeteoBaseUrl = "https://api.open-meteo.com";
    String openMeteoGeoBaseUrl = "https://geocoding-api.open-meteo.com";
    String wikipediaBaseUrl = "https://en.wikipedia.org";
    String itunesBaseUrl = "https://itunes.apple.com";
    @ConfigProperty(name = "agui.health.cache-ttl-ms", defaultValue = "300000")
    long healthCacheTtlMs = 300_000L;
    private volatile Map<String, Object> cachedHealthReport;
    private volatile long cachedHealthExpiresAtMs;
    Function<String, UpstreamHttpResult> httpProbe = this::probeUrlViaHttp;

    void setHttpProbe(Function<String, UpstreamHttpResult> probe) {
        this.httpProbe = probe == null ? this::probeUrlViaHttp : probe;
        this.cachedHealthReport = null;
        this.cachedHealthExpiresAtMs = 0L;
    }

    public record UpstreamHttpResult(int statusCode, String body, String error, long latencyMs) {
        public boolean isOk() {
            return statusCode >= 200 && statusCode < 300 && body != null && (error == null || error.isBlank());
        }
    }

    private record CityCoordinates(String city, String country, double latitude, double longitude) {}

    private static final Map<String, CityCoordinates> KNOWN_COORDINATES = Map.ofEntries(
            Map.entry("kyoto", new CityCoordinates("Kyoto", "Japan", 35.0116, 135.7681)),
            Map.entry("lisbon", new CityCoordinates("Lisbon", "Portugal", 38.7223, -9.1393)),
            Map.entry("reykjavik", new CityCoordinates("Reykjavik", "Iceland", 64.1466, -21.9426)),
            Map.entry("beirut", new CityCoordinates("Beirut", "Lebanon", 33.8938, 35.5018)),
            Map.entry("tokyo", new CityCoordinates("Tokyo", "Japan", 35.6762, 139.6503)),
            Map.entry("paris", new CityCoordinates("Paris", "France", 48.8566, 2.3522)),
            Map.entry("rome", new CityCoordinates("Rome", "Italy", 41.9028, 12.4964)),
            Map.entry("barcelona", new CityCoordinates("Barcelona", "Spain", 41.3874, 2.1686)),
            Map.entry("london", new CityCoordinates("London", "United Kingdom", 51.5072, -0.1276)),
            Map.entry("seoul", new CityCoordinates("Seoul", "South Korea", 37.5665, 126.9780)),
            Map.entry("new york", new CityCoordinates("New York", "United States", 40.7128, -74.0060)),
            Map.entry("marrakech", new CityCoordinates("Marrakech", "Morocco", 31.6295, -7.9811)));

    private static final Map<String, String> CITY_MUSIC_QUERIES = Map.ofEntries(
            Map.entry("lisbon", "Amalia Rodrigues Fado"),
            Map.entry("porto", "Amalia Rodrigues Fado"),
            Map.entry("reykjavik", "Olafur Arnalds strings"),
            Map.entry("kyoto", "koto shakuhachi"),
            Map.entry("tokyo", "Ryuichi Sakamoto"),
            Map.entry("osaka", "Ryuichi Sakamoto"),
            Map.entry("paris", "Erik Satie piano"),
            Map.entry("lyon", "Erik Satie piano"),
            Map.entry("rome", "Ennio Morricone"),
            Map.entry("florence", "Ennio Morricone"),
            Map.entry("barcelona", "Paco de Lucia guitar"),
            Map.entry("madrid", "Paco de Lucia guitar"),
            Map.entry("marrakech", "gnawa oud"),
            Map.entry("oaxaca", "Lila Downs Oaxaca"),
            Map.entry("buenos aires", "Astor Piazzolla tango"),
            Map.entry("beirut", "Fairuz"),
            Map.entry("amman", "Marcel Khalife oud"),
            Map.entry("cairo", "Umm Kulthum"),
            Map.entry("athens", "Mikis Theodorakis bouzouki"),
            Map.entry("istanbul", "Mercan Dede ney"),
            Map.entry("seoul", "gayageum traditional"),
            Map.entry("vienna", "Mozart Vienna Philharmonic"),
            Map.entry("berlin", "Nils Frahm piano"),
            Map.entry("copenhagen", "Agnes Obel piano"),
            Map.entry("rio de janeiro", "Antonio Carlos Jobim bossa nova"),
            Map.entry("london", "Max Richter strings"),
            Map.entry("new york", "Bill Evans Trio jazz"));

    private static final Map<String, String> CITY_ARCHITECTURE = Map.of(
            "lisbon", "Alfama Azulejo Terraces, Chiado Pombaline Ironwork & MAAT Riverfront",
            "reykjavik", "Harpa Basalt Columns, Hallgrímskirkja Concrete Vault & Turf Sanctuaries",
            "beirut", "Sursock Museum Ottoman-Venetian Arches, Mar Mikhael Heritage Stairs & Corniche",
            "tokyo", "Tadao Ando Concrete Galleries, Omotesando Modernism & Yanaka Timber Alleys",
            "oaxaca", "Santo Domingo Cantera Stone Courtyards & Contemporary Adobe Ateliers",
            "kyoto", "Sukiya-zukuri Cedar Machiya, Katsura Imperial Joinery & Zen Dry-Landscape Gardens",
            "paris", "Haussmannian Limestone Courtyards, Place des Vosges Arcades & Modernist Ateliers");

    private static final Map<String, String> CITY_ACOUSTIC_MOODS = Map.of(
            "lisbon", "Warm 12-string Portuguese guitarra resonance paired with Atlantic twilight breeze",
            "reykjavik", "Intimate bowed cello harmonics and sub-Arctic chamber acoustics inside basalt walls",
            "beirut", "Levantine oud and Fairuz orchestral nostalgia drifting across Mediterranean limestone courtyards",
            "tokyo", "Tube-amplified modal jazz vinyl warmth in a subterranean Shibuya kissaten",
            "kyoto", "Contemplative silk-stringed koto and bamboo shakuhachi phrasing tuned to cedar courtyard mist");

    private static final Map<Integer, String> WMO_CONDITIONS = Map.ofEntries(
            Map.entry(0, "Clear Sky & Crisp Light"),
            Map.entry(1, "Partly Clouded Horizon"),
            Map.entry(2, "Partly Clouded Horizon"),
            Map.entry(3, "Soft Overcast Canopy"),
            Map.entry(45, "Atmospheric Mist & Fog"),
            Map.entry(48, "Atmospheric Mist & Fog"),
            Map.entry(51, "Light Coastal Drizzle"),
            Map.entry(53, "Light Coastal Drizzle"),
            Map.entry(55, "Light Coastal Drizzle"),
            Map.entry(61, "Fresh Rain Showers"),
            Map.entry(63, "Fresh Rain Showers"),
            Map.entry(65, "Fresh Rain Showers"),
            Map.entry(80, "Fresh Rain Showers"),
            Map.entry(81, "Fresh Rain Showers"),
            Map.entry(82, "Fresh Rain Showers"),
            Map.entry(71, "Crisp Snowfall"),
            Map.entry(73, "Crisp Snowfall"),
            Map.entry(75, "Crisp Snowfall"),
            Map.entry(95, "Electric Storm Front"),
            Map.entry(96, "Electric Storm Front"),
            Map.entry(99, "Electric Storm Front"));

    @PostConstruct
    void init() {
        resetState();
    }

    public void resetState() {
        synchronized (activeTrip) {
            activeTrip.clear();
            activeTrip.put("uri", "itinerary://active-trip");
            activeTrip.put("activeCity", null);
            activeTrip.put("country", null);
            activeTrip.put("currency", null);
            activeTrip.put("exchangeRate", null);
            activeTrip.put("temperatureC", null);
            activeTrip.put("weatherCondition", null);
            activeTrip.put("nights", null);
            activeTrip.put("stopTitle", null);
            activeTrip.put("budgetUsd", null);
            activeTrip.put("allocatedUsd", null);
            activeTrip.put("homeCurrency", null);
            activeTrip.put("heroImageUrl", null);
            activeTrip.put("soundtrack", null);
        }

        synchronized (travelerPreferences) {
            travelerPreferences.clear();
            travelerPreferences.put("uri", "traveler://preferences");
            travelerPreferences.put("travelerName", null);
            travelerPreferences.put("style", null);
            travelerPreferences.put("dietary", null);
            travelerPreferences.put("musicTaste", null);
            travelerPreferences.put("pace", null);
            travelerPreferences.put("homeCurrency", null);
        }
    }

    UpstreamHttpResult probeUrlViaHttp(String url) {
        long startNs = System.nanoTime();
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(3500))
                    .header("User-Agent", "Nomad-Quarkus-AGUI/1.0 (https://github.com/fmatar/quarkus-agui; contact@agui.org)")
                    .GET()
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            long elapsedMs = Math.max(1L, (System.nanoTime() - startNs) / 1_000_000L);
            if (res.statusCode() >= 200 && res.statusCode() < 300) {
                return new UpstreamHttpResult(res.statusCode(), res.body(), null, elapsedMs);
            }
            return new UpstreamHttpResult(
                    res.statusCode(),
                    res.body(),
                    "HTTP " + res.statusCode() + " from " + URI.create(url).getHost(),
                    elapsedMs);
        } catch (Exception ex) {
            long elapsedMs = Math.max(1L, (System.nanoTime() - startNs) / 1_000_000L);
            return new UpstreamHttpResult(0, null, ex.getClass().getSimpleName() + ": " + ex.getMessage(), elapsedMs);
        }
    }

    @Resource(uri = "itinerary://active-trip", description = "Active Nomad traveler itinerary, destination weather, and sanctuary allocation")
    public TextResourceContents activeTripResource() {
        return toJsonResource("itinerary://active-trip", getActiveTripSnapshot());
    }

    @Resource(uri = "traveler://preferences", description = "Traveler architectural, culinary, and acoustic preferences")
    public TextResourceContents travelerPreferencesResource() {
        return toJsonResource("traveler://preferences", getTravelerPreferences());
    }

    @ResourceTemplate(
            uriTemplate = "itinerary://city/{city}/dossier",
            description = "Parameterized destination architectural, meteorological, and acoustic dossier")
    public TextResourceContents cityDossierResourceTemplate(@ResourceTemplateArg(name = "city") String city) {
        if (city == null || city.isBlank()) {
            return toJsonResource(
                    "itinerary://city/missing/dossier",
                    Map.of("status", "ERROR", "code", "MISSING_REQUIRED_ARGUMENTS", "error", "City argument is required"));
        }
        String resolvedCity = normalizeCity(city);
        String uri = "itinerary://city/" + resolvedCity.toLowerCase(Locale.ROOT).replace(" ", "-") + "/dossier";
        return toJsonResource(uri, getCityDossier(resolvedCity));
    }

    @CompletePrompt("weekend-cultural-escape")
    public CompletionResponse completeWeekendEscapeCity(@CompleteArg(name = "city") String prefix) {
        return CompletionResponse.create(completeCityArgument(prefix));
    }

    @CompletePrompt("local-music-and-food-walk")
    public CompletionResponse completeMusicWalkCity(@CompleteArg(name = "city") String prefix) {
        return CompletionResponse.create(completeCityArgument(prefix));
    }

    public List<String> completeCityArgument(String prefix) {
        String normalized = prefix == null ? "" : prefix.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return CURATED_GLOBAL_CITIES.subList(0, 8);
        }
        List<String> matches = CURATED_GLOBAL_CITIES.stream()
                .filter(c -> c.toLowerCase(Locale.ROOT).startsWith(normalized)
                        || c.toLowerCase(Locale.ROOT).contains(normalized))
                .limit(8)
                .toList();
        return matches.isEmpty() ? List.of(normalizeCity(prefix)) : matches;
    }

    @Prompt(name = "weekend-cultural-escape", description = "Synthesize a multi-day architectural, culinary, and musical itinerary for a target city")
    public PromptMessage weekendCulturalEscapePrompt(
            @PromptArg(description = "Destination city (required)") String city) {
        if (city == null || city.isBlank()) {
            throw new IllegalArgumentException("Required prompt argument 'city' must not be blank");
        }
        return PromptMessage.withUserRole(new TextContent(
                "Curate an architectural, culinary, and local music itinerary in " + city.trim()
                        + " grounded in live weather, currency conversion, and a 30s local soundtrack preview."));
    }

    @Prompt(name = "local-music-and-food-walk", description = "Curate an evening listening bar and neighborhood culinary walk")
    public PromptMessage localMusicAndFoodWalkPrompt(
            @PromptArg(description = "Destination city (required)") String city) {
        if (city == null || city.isBlank()) {
            throw new IllegalArgumentException("Required prompt argument 'city' must not be blank");
        }
        return PromptMessage.withUserRole(new TextContent(
                "Design an intimate evening listening-bar and neighborhood culinary walk through " + city.trim()
                        + " with playable audio preview and local budget breakdown."));
    }

    @Tool(description = "Fetch live Open-Meteo weather forecast and temperature for an explicit destination city. Never guess or assume a default city; ask the traveler first if no city was specified.")
    public ToolResponse fetchCityWeather(
            @ToolArg(description = "Explicit destination city name stated by the traveler (never invent a default city)", required = true) String city) {
        if (city == null || city.isBlank()) {
            return errorToolResponse(
                    "MISSING_REQUIRED_ARGUMENTS",
                    "Missing required argument 'city' for fetchCityWeather.",
                    "api.open-meteo.com");
        }
        String requestedCity = normalizeCity(city);
        CityCoordinates coords = resolveCityCoordinates(requestedCity);
        if (coords == null) {
            return errorToolResponse(
                    "UPSTREAM_UNAVAILABLE",
                    "Weather Geocoding Service Unavailable (geocoding-api.open-meteo.com) or unknown city: " + requestedCity,
                    "api.open-meteo.com");
        }

        String url = String.format(
                Locale.ROOT,
                "%s/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,wind_speed_10m",
                openMeteoBaseUrl,
                coords.latitude(),
                coords.longitude());
        UpstreamHttpResult res = httpProbe.apply(url);
        if (!res.isOk()) {
            return errorToolResponse(
                    "UPSTREAM_UNAVAILABLE",
                    "Weather Service Unavailable (api.open-meteo.com): " + describeFailure(res),
                    "api.open-meteo.com");
        }

        try {
            JsonNode current = objectMapper.readTree(res.body()).path("current");
            if (!current.has("temperature_2m")) {
                return errorToolResponse(
                        "UPSTREAM_INVALID_PAYLOAD",
                        "Weather Service Unavailable (api.open-meteo.com): response missing current.temperature_2m",
                        "api.open-meteo.com");
            }
            double tempC = current.path("temperature_2m").asDouble();
            double windKph = current.path("wind_speed_10m").asDouble(0.0);
            String condition = describeWmoCode(current.path("weather_code").asInt(-1));

            Map<String, Object> weather = new LinkedHashMap<>();
            weather.put("status", "OK");
            weather.put("serviceAvailable", true);
            weather.put("city", coords.city());
            weather.put("country", coords.country());
            weather.put("latitude", coords.latitude());
            weather.put("longitude", coords.longitude());
            weather.put("temperatureC", tempC);
            weather.put("windSpeedKph", windKph);
            weather.put("condition", condition);
            weather.put("source", "api.open-meteo.com");

            activeTrip.put("activeCity", coords.city());
            activeTrip.put("country", coords.country());
            activeTrip.put("temperatureC", tempC);
            activeTrip.put("weatherCondition", condition);
            return successToolResponse(weather);
        } catch (Exception ex) {
            return errorToolResponse(
                    "UPSTREAM_PARSE_ERROR",
                    "Weather Service Unavailable (api.open-meteo.com): " + ex.getMessage(),
                    "api.open-meteo.com");
        }
    }

    @Tool(description = "Discover local cultural landmarks via Wikipedia REST v1 and a 30s local music preview via Apple iTunes Search API for an explicit destination. Never invent a default destination.")
    public ToolResponse discoverLocalCultureAndMedia(
            @ToolArg(description = "Explicit destination city or country stated by the traveler (never invent a default)", required = true) String destination,
            @ToolArg(description = "Optional regional music artist or instrument explicitly requested by the traveler", required = false) String genre) {
        if (destination == null || destination.isBlank()) {
            return errorToolResponse(
                    "MISSING_REQUIRED_ARGUMENTS",
                    "Missing required argument 'destination' for discoverLocalCultureAndMedia.",
                    "en.wikipedia.org");
        }
        String resolvedCity = normalizeCity(destination);
        String searchQuery = resolveMusicQuery(resolvedCity, genre);

        Map<String, Object> wikiData = fetchWikipediaSummary(resolvedCity);
        Map<String, Object> itunesData = fetchItunesPreview(searchQuery);
        if (!"OK".equals(itunesData.get("status"))) {
            String fallbackQuery = defaultCityMusicQuery(resolvedCity);
            if (!fallbackQuery.equalsIgnoreCase(searchQuery)) {
                Map<String, Object> fallbackItunes = fetchItunesPreview(fallbackQuery);
                if ("OK".equals(fallbackItunes.get("status"))) {
                    itunesData = fallbackItunes;
                    searchQuery = fallbackQuery;
                }
            }
        }

        boolean wikiOk = "OK".equals(wikiData.get("status"));
        boolean itunesOk = "OK".equals(itunesData.get("status"));

        if (!wikiOk && !itunesOk) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", "ERROR");
            err.put("isError", true);
            err.put("serviceAvailable", false);
            err.put("destination", resolvedCity);
            err.put("wikipediaStatus", "ERROR");
            err.put("wikipediaError", wikiData.get("error"));
            err.put("itunesStatus", "ERROR");
            err.put("itunesError", itunesData.get("error"));
            err.put("error", "Cultural & Media Services Unavailable: Wikipedia (" + wikiData.get("error")
                    + "); Apple iTunes (" + itunesData.get("error") + ")");
            return buildToolResponse(true, err);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", (wikiOk && itunesOk) ? "OK" : "PARTIAL");
        payload.put("isError", false);
        payload.put("destination", resolvedCity);
        payload.put("genre", searchQuery);
        payload.put("wikipediaStatus", wikiOk ? "OK" : "ERROR");
        payload.put("itunesStatus", itunesOk ? "OK" : "ERROR");

        if (wikiOk) {
            payload.put("culturalSummary", wikiData.get("extract"));
            payload.put("heroImageUrl", wikiData.get("imageUrl"));
            payload.put("wikiSourceUrl", "https://en.wikipedia.org/wiki/" + URLEncoder.encode(resolvedCity.replace(' ', '_'), StandardCharsets.UTF_8));
            if (wikiData.get("imageUrl") != null) {
                activeTrip.put("heroImageUrl", wikiData.get("imageUrl"));
            }
        } else {
            payload.put("wikipediaError", wikiData.get("error"));
        }

        if (itunesOk) {
            payload.put("audioTrack", itunesData.get("audioTrack"));
            activeTrip.put("soundtrack", itunesData.get("audioTrack"));
        } else {
            payload.put("itunesError", itunesData.get("error"));
        }

        activeTrip.put("activeCity", resolvedCity);
        return successToolResponse(payload);
    }

    @Tool(description = "Update or reserve a cultural stop in the active trip itinerary ONLY when the traveler has explicitly provided city, stopTitle, nights, and costUsd (never assume 3 nights or default costs; for holds >= 1500 USD call requestSanctuaryReservation instead).")
    public ToolResponse updateTripItinerary(
            @ToolArg(description = "Explicit destination city stated by the traveler", required = true) String city,
            @ToolArg(description = "Explicit stop or boutique stay title", required = true) String stopTitle,
            @ToolArg(description = "Explicit number of nights stated by the traveler (1..30, never guess 3)", required = true) Integer nights,
            @ToolArg(description = "Explicit estimated cost in USD (>= 0, never guess)", required = true) Double costUsd) {
        if (city == null || city.isBlank()
                || stopTitle == null || stopTitle.isBlank()
                || nights == null || nights <= 0
                || costUsd == null || costUsd < 0.0) {
            return errorToolResponse(
                    "MISSING_REQUIRED_ARGUMENTS",
                    "updateTripItinerary requires explicit 'city', 'stopTitle', positive 'nights', and non-negative 'costUsd' (no defaults assumed).",
                    SERVER_NAME);
        }
        String resolvedCity = normalizeCity(city);
        int clampedNights = Math.min(nights, 30);

        activeTrip.put("activeCity", resolvedCity);
        activeTrip.put("stopTitle", stopTitle.trim());
        activeTrip.put("nights", clampedNights);
        activeTrip.put("allocatedUsd", costUsd);
        return successToolResponse(getActiveTripSnapshot());
    }

    @Tool(description = "Sample a twilight acoustic listening mood profile for an explicit destination city, optional weather condition, and optional music genre. Never guess a default city.")
    public ToolResponse sampleAcousticMood(
            @ToolArg(description = "Explicit destination city name stated by the traveler", required = true) String city,
            @ToolArg(description = "Observed weather condition if known", required = false) String weatherCondition,
            @ToolArg(description = "Preferred regional or musical genre if stated by the traveler", required = false) String genre) {
        Map<String, Object> result = sampleAcousticMoodProfile(city, weatherCondition, genre);
        if ("ERROR".equals(result.get("status"))) {
            return errorToolResponse(
                    String.valueOf(result.getOrDefault("code", "MISSING_REQUIRED_ARGUMENTS")),
                    String.valueOf(result.getOrDefault("error", "Required argument 'city' was not supplied.")),
                    SERVER_NAME);
        }
        return successToolResponse(result);
    }

    @Tool(description = "Persist explicit human traveler profile preferences (personal given name when the user introduces themselves, architectural/travel style, dietary needs, music taste, pace, home currency) into the MCP traveler://preferences resource. Do NOT call this tool when the traveler only names a destination city, and never pass a destination city name as travelerName.")
    public ToolResponse saveTravelerPreferences(
            @ToolArg(description = "Human traveler's personal given name ONLY when they introduce themselves (never pass a destination city name like Kyoto, Lisbon, Paris, or Beirut)", required = false) String travelerName,
            @ToolArg(description = "Travel or architectural style if explicitly stated", required = false) String style,
            @ToolArg(description = "Dietary preference if explicitly stated", required = false) String dietary,
            @ToolArg(description = "Music taste if explicitly stated", required = false) String musicTaste,
            @ToolArg(description = "Trip pace if explicitly stated", required = false) String pace,
            @ToolArg(description = "3-letter ISO home currency code if explicitly stated", required = false) String homeCurrency) {
        Map<String, Object> updates = new LinkedHashMap<>();
        String sanitizedName = sanitizeOptionalText(travelerName);
        if (!sanitizedName.isEmpty()) {
            if (isKnownCityName(sanitizedName)) {
                activeTrip.putIfAbsent("activeCity", normalizeCity(sanitizedName));
            } else {
                updates.put("travelerName", sanitizedName);
            }
        }
        putIfNonBlankAndNotCity(updates, "style", sanitizeOptionalText(style));
        putIfNonBlankAndNotCity(updates, "dietary", sanitizeOptionalText(dietary));
        putIfNonBlankAndNotCity(updates, "musicTaste", sanitizeOptionalText(musicTaste));
        putIfNonBlankAndNotCity(updates, "pace", sanitizeOptionalText(pace));
        putIfNonBlankAndNotCity(updates, "homeCurrency", sanitizeOptionalText(homeCurrency));
        Map<String, Object> payload = new LinkedHashMap<>(updateTravelerPreferences(updates));
        payload.put("status", "OK");
        payload.put("updated", !updates.isEmpty());
        return successToolResponse(payload);
    }

    @Tool(description = "Reset the active MCP trip itinerary and traveler preferences when starting a brand-new conversation or clearing memory.")
    public ToolResponse resetNomadState() {
        resetState();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "OK");
        payload.put("reset", true);
        payload.put("activeTrip", getActiveTripSnapshot());
        payload.put("preferences", getTravelerPreferences());
        return successToolResponse(payload);
    }

    private void putIfNonBlankAndNotCity(Map<String, Object> target, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (isKnownCityName(value)) {
            activeTrip.putIfAbsent("activeCity", normalizeCity(value));
            return;
        }
        target.put(key, value.trim());
    }

    public Map<String, Object> checkHealth() {
        long now = System.currentTimeMillis();
        Map<String, Object> cached = cachedHealthReport;
        if (cached != null && now < cachedHealthExpiresAtMs) {
            return cached;
        }
        List<Map<String, Object>> upstreams = new ArrayList<>();
        upstreams.add(probeUpstreamHealth(
                "open-meteo",
                "Open-Meteo Weather API",
                openMeteoBaseUrl + "/v1/forecast?latitude=48.8566&longitude=2.3522&current=temperature_2m"));
        upstreams.add(probeUpstreamHealth(
                "wikipedia",
                "Wikipedia REST API v1",
                wikipediaBaseUrl + "/api/rest_v1/page/summary/Paris"));
        upstreams.add(probeUpstreamHealth(
                "itunes",
                "Apple iTunes Search API",
                itunesBaseUrl + "/search?term=jazz&media=music&entity=song&limit=1"));

        long upCount = upstreams.stream().filter(u -> "UP".equals(u.get("status"))).count();
        String overall = upCount == upstreams.size() ? "UP" : (upCount == 0 ? "DOWN" : "DEGRADED");

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("server", SERVER_NAME);
        report.put("status", overall);
        report.put("healthyCount", upCount);
        report.put("totalCount", upstreams.size());
        report.put("upstreams", List.copyOf(upstreams));
        Map<String, Object> snapshot = Collections.unmodifiableMap(report);
        cachedHealthReport = snapshot;
        cachedHealthExpiresAtMs = now + Math.max(0L, healthCacheTtlMs);
        return snapshot;
    }

    private Map<String, Object> probeUpstreamHealth(String id, String name, String url) {
        UpstreamHttpResult res = httpProbe.apply(url);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", id);
        entry.put("name", name);
        entry.put("status", res.isOk() ? "UP" : "DOWN");
        entry.put("httpStatus", res.statusCode());
        entry.put("latencyMs", res.latencyMs());
        if (!res.isOk()) {
            entry.put("error", describeFailure(res));
        }
        return entry;
    }

    public Map<String, Object> getActiveTripSnapshot() {
        synchronized (activeTrip) {
            return new LinkedHashMap<>(activeTrip);
        }
    }

    public Map<String, Object> getTravelerPreferences() {
        synchronized (travelerPreferences) {
            return new LinkedHashMap<>(travelerPreferences);
        }
    }

    public Map<String, Object> updateTravelerPreferences(Map<String, Object> updates) {
        if (updates != null) {
            for (Map.Entry<String, Object> entry : updates.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null && !String.valueOf(entry.getValue()).isBlank()) {
                    travelerPreferences.put(entry.getKey(), entry.getValue());
                }
            }
        }
        travelerPreferences.put("uri", "traveler://preferences");
        return getTravelerPreferences();
    }

    public Map<String, Object> getCityDossier(String city) {
        if (city == null || city.isBlank()) {
            return Map.of("status", "ERROR", "code", "MISSING_REQUIRED_ARGUMENTS", "error", "City is required");
        }
        String resolvedCity = normalizeCity(city);
        String slug = resolvedCity.toLowerCase(Locale.ROOT).replace(" ", "-");
        ToolResponse weatherRes = fetchCityWeather(resolvedCity);
        Map<String, Object> dossier = new LinkedHashMap<>();
        dossier.put("uri", "itinerary://city/" + slug + "/dossier");
        dossier.put("city", resolvedCity);
        dossier.put("weatherStatus", weatherRes.isError() ? "ERROR" : "OK");
        dossier.put("weather", weatherRes.structuredContent());
        dossier.put("architecturalHighlight", architecturalHighlightFor(resolvedCity));
        return dossier;
    }

    public Map<String, Object> sampleAcousticMoodProfile(String city, String weatherCondition, String genre) {
        if (city == null || city.isBlank()) {
            return Map.of(
                    "status", "ERROR",
                    "isError", true,
                    "code", "MISSING_REQUIRED_ARGUMENTS",
                    "error", "Required argument 'city' was not supplied for acoustic mood sampling.");
        }
        String resolvedCity = normalizeCity(city);
        String cleanWeather = sanitizeOptionalText(weatherCondition);
        String cleanGenre = sanitizeOptionalText(genre);
        String cond = cleanWeather.isEmpty() ? "Ambient local sky" : cleanWeather;
        String resolvedGenre = cleanGenre.isEmpty() ? "regional acoustic" : cleanGenre;
        String baseMood = CITY_ACOUSTIC_MOODS.get(resolvedCity.toLowerCase(Locale.ROOT));
        String moodDistillation = baseMood != null
                ? baseMood + " (" + cond + ")."
                : "Curated " + resolvedGenre + " listening atmosphere tailored to " + resolvedCity + " (" + cond + ").";
        return Map.of(
                "status", "OK",
                "protocol", "mcp:sampling",
                "server", SERVER_NAME,
                "city", resolvedCity,
                "genre", resolvedGenre,
                "weatherCondition", cond,
                "sampledMood", moodDistillation,
                "recommendedListeningWindow", "19:30 – 22:00 Local Twilight");
    }

    public List<Map<String, Object>> listPrompts() {
        return List.of(
                Map.of(
                        "name", "weekend-cultural-escape",
                        "description", "Synthesize a multi-day architectural, culinary, and musical itinerary for a target city",
                        "arguments", List.of("city")),
                Map.of(
                        "name", "local-music-and-food-walk",
                        "description", "Curate an evening listening bar and neighborhood culinary walk",
                        "arguments", List.of("city")));
    }

    public List<Map<String, Object>> listTools() {
        return List.of(
                Map.of(
                        "name", "fetchCityWeather",
                        "description", "Fetch live Open-Meteo weather forecast and temperature for an explicit travel destination city",
                        "requiredParameters", List.of("city")),
                Map.of(
                        "name", "discoverLocalCultureAndMedia",
                        "description", "Discover local cultural landmarks via Wikipedia REST API and a 30s local music preview via Apple iTunes Search API",
                        "requiredParameters", List.of("destination"),
                        "optionalParameters", List.of("genre")),
                Map.of(
                        "name", "updateTripItinerary",
                        "description", "Update or reserve a cultural stop in the active trip itinerary",
                        "requiredParameters", List.of("city", "stopTitle", "nights", "costUsd")),
                Map.of(
                        "name", "sampleAcousticMood",
                        "description", "Sample a twilight acoustic listening mood profile for an explicit destination city",
                        "requiredParameters", List.of("city"),
                        "optionalParameters", List.of("weatherCondition", "genre")),
                Map.of(
                        "name", "saveTravelerPreferences",
                        "description", "Persist explicit traveler preferences into traveler://preferences",
                        "requiredParameters", List.of(),
                        "optionalParameters", List.of("travelerName", "style", "dietary", "musicTaste", "pace", "homeCurrency")),
                Map.of(
                        "name", "resetNomadState",
                        "description", "Reset active MCP trip itinerary and traveler preferences",
                        "requiredParameters", List.of()));
    }

    public McpToolInvocationResponse dispatchTool(McpToolInvocationRequest request) {
        long startNs = System.nanoTime();
        String toolName = (request == null || request.tool() == null) ? "" : request.tool().trim();
        Map<String, Object> args = (request == null || request.arguments() == null) ? Map.of() : request.arguments();

        ToolResponse response = switch (toolName) {
            case "fetchCityWeather" -> fetchCityWeather(stringArg(args, "city"));
            case "discoverLocalCultureAndMedia" -> discoverLocalCultureAndMedia(
                    stringArg(args, "destination"),
                    stringArg(args, "genre"));
            case "updateTripItinerary" -> updateTripItinerary(
                    stringArg(args, "city"),
                    stringArg(args, "stopTitle"),
                    intArg(args, "nights"),
                    doubleArg(args, "costUsd"));
            case "sampleAcousticMood" -> sampleAcousticMood(
                    stringArg(args, "city"),
                    stringArg(args, "weatherCondition"),
                    stringArg(args, "genre"));
            case "saveTravelerPreferences" -> saveTravelerPreferences(
                    stringArg(args, "travelerName"),
                    stringArg(args, "style"),
                    stringArg(args, "dietary"),
                    stringArg(args, "musicTaste"),
                    stringArg(args, "pace"),
                    stringArg(args, "homeCurrency"));
            case "resetNomadState" -> resetNomadState();
            default -> errorToolResponse("UNSUPPORTED_TOOL", "Unsupported MCP tool: " + toolName, SERVER_NAME);
        };
        long elapsedMs = Math.max(1L, (System.nanoTime() - startNs) / 1_000_000L);
        return new McpToolInvocationResponse(SERVER_NAME, toolName, response.structuredContent(), response.isError(), elapsedMs);
    }

    private CityCoordinates resolveCityCoordinates(String city) {
        CityCoordinates known = KNOWN_COORDINATES.get(city.toLowerCase(Locale.ROOT));
        if (known != null) {
            return known;
        }
        String geoUrl = openMeteoGeoBaseUrl + "/v1/search?name="
                + URLEncoder.encode(city, StandardCharsets.UTF_8)
                + "&count=1&language=en&format=json";
        UpstreamHttpResult geoRes = httpProbe.apply(geoUrl);
        if (!geoRes.isOk()) {
            return null;
        }
        try {
            JsonNode results = objectMapper.readTree(geoRes.body()).path("results");
            if (results.isArray() && !results.isEmpty()) {
                JsonNode first = results.get(0);
                return new CityCoordinates(
                        first.path("name").asText(city),
                        first.path("country").asText(""),
                        first.path("latitude").asDouble(),
                        first.path("longitude").asDouble());
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private Map<String, Object> fetchWikipediaSummary(String city) {
        String wikiTitle = URLEncoder.encode(city.replace(' ', '_'), StandardCharsets.UTF_8);
        String wikiUrl = wikipediaBaseUrl + "/api/rest_v1/page/summary/" + wikiTitle;
        UpstreamHttpResult res = httpProbe.apply(wikiUrl);
        if (!res.isOk()) {
            return Map.of("status", "ERROR", "error", describeFailure(res));
        }
        try {
            JsonNode root = objectMapper.readTree(res.body());
            String extract = root.path("extract").asText("");
            if (extract.isBlank()) {
                return Map.of("status", "ERROR", "error", "Wikipedia summary extract was empty for " + city);
            }
            String imageUrl = null;
            if (root.path("originalimage").hasNonNull("source")) {
                imageUrl = root.path("originalimage").path("source").asText();
            } else if (root.path("thumbnail").hasNonNull("source")) {
                imageUrl = root.path("thumbnail").path("source").asText();
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("status", "OK");
            out.put("extract", extract);
            if (imageUrl != null) {
                out.put("imageUrl", imageUrl);
            }
            return out;
        } catch (Exception ex) {
            return Map.of("status", "ERROR", "error", ex.getMessage());
        }
    }

    private Map<String, Object> fetchItunesPreview(String searchQuery) {
        String searchTerm = URLEncoder.encode(searchQuery, StandardCharsets.UTF_8);
        String itunesUrl = itunesBaseUrl + "/search?term=" + searchTerm + "&media=music&entity=song&limit=5";
        UpstreamHttpResult res = httpProbe.apply(itunesUrl);
        if (!res.isOk()) {
            return Map.of("status", "ERROR", "error", describeFailure(res));
        }
        try {
            JsonNode results = objectMapper.readTree(res.body()).path("results");
            if (results.isArray()) {
                for (JsonNode item : results) {
                    if (item.hasNonNull("previewUrl") && !item.path("previewUrl").asText().isBlank()) {
                        Map<String, Object> track = new LinkedHashMap<>();
                        track.put("trackName", item.path("trackName").asText("Local Track"));
                        track.put("artistName", item.path("artistName").asText("Local Artist"));
                        track.put("previewUrl", item.path("previewUrl").asText());
                        track.put("artworkUrl", item.path("artworkUrl100").asText("").replace("100x100bb", "300x300bb"));
                        track.put("durationSeconds", 30);
                        return Map.of("status", "OK", "audioTrack", track);
                    }
                }
            }
            return Map.of("status", "ERROR", "error", "No playable 30s preview found on Apple iTunes for '" + searchQuery + "'");
        } catch (Exception ex) {
            return Map.of("status", "ERROR", "error", ex.getMessage());
        }
    }

    private String resolveMusicQuery(String city, String genre) {
        String trimmedGenre = sanitizeOptionalText(genre);
        String lower = trimmedGenre.toLowerCase(Locale.ROOT);
        boolean isGeneric = trimmedGenre.isEmpty()
                || List.of(
                        "traditional", "jazz", "ambient", "fado", "acoustic", "classical", "folk",
                        "koto", "koto music", "oud", "strings", "music", "local", "local music",
                        "architecture", "culinary", "food", "museums", "culture", "nature", "design")
                        .contains(lower)
                || lower.contains("music")
                || lower.contains("traditional")
                || lower.contains("regional")
                || lower.contains("acoustic")
                || lower.contains("classical")
                || lower.contains("folk")
                || lower.contains("ambient")
                || lower.contains("jazz")
                || lower.contains("fado")
                || lower.contains("koto")
                || lower.contains("shakuhachi")
                || lower.contains("oud")
                || lower.contains("strings")
                || lower.contains("piano")
                || lower.contains("guitar")
                || lower.contains("tango")
                || lower.contains("bossa")
                || lower.contains("architecture")
                || lower.contains("culinary")
                || lower.contains("azulejo")
                || lower.contains("museum")
                || lower.contains("food")
                || lower.contains("terroir")
                || lower.contains("sanctuary")
                || lower.contains("itinerary")
                || lower.contains("culture")
                || lower.contains("cultural")
                || lower.contains("nature")
                || lower.contains("design");
        if (!isGeneric) {
            return trimmedGenre;
        }
        return CITY_MUSIC_QUERIES.getOrDefault(
                city.toLowerCase(Locale.ROOT),
                city + (trimmedGenre.isEmpty() || lower.contains("architecture") ? " acoustic" : " " + trimmedGenre));
    }

    static String sanitizeOptionalText(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.contains("empty string")
                || lower.equals("empty")
                || lower.equals("<empty>")
                || lower.equals("none")
                || lower.equals("null")
                || lower.equals("nil")
                || lower.equals("n/a")
                || lower.equals("na")
                || lower.equals("unspecified")
                || lower.equals("not specified")
                || lower.equals("not provided")
                || lower.equals("no preference")
                || lower.equals("no preferences")
                || lower.equals("not applicable")
                || lower.equals("undecided")
                || lower.equals("tbd")
                || lower.equals("unknown")
                || lower.equals("default")
                || lower.equals("optional")
                || lower.equals("any")
                || lower.equals("-")) {
            return "";
        }
        return trimmed;
    }

    boolean isKnownCityName(String candidate) {
        String norm = normalizeCity(candidate == null ? "" : candidate).toLowerCase(Locale.ROOT);
        if (norm.isEmpty()) {
            return false;
        }
        return KNOWN_COORDINATES.containsKey(norm)
                || CITY_MUSIC_QUERIES.containsKey(norm)
                || CURATED_GLOBAL_CITIES.stream().anyMatch(c -> c.equalsIgnoreCase(norm));
    }

    private String defaultCityMusicQuery(String city) {
        return CITY_MUSIC_QUERIES.getOrDefault(city.toLowerCase(Locale.ROOT), city + " acoustic");
    }

    private String architecturalHighlightFor(String city) {
        return CITY_ARCHITECTURE.getOrDefault(
                city.toLowerCase(Locale.ROOT),
                city + " Historic & Modernist Architectural Corridors");
    }

    private String describeWmoCode(int code) {
        return WMO_CONDITIONS.getOrDefault(code, "Variable Local Conditions");
    }

    private String normalizeCity(String raw) {
        String trimmed = raw.trim();
        int commaIdx = trimmed.indexOf(',');
        if (commaIdx > 0) {
            trimmed = trimmed.substring(0, commaIdx).trim();
        }
        if (trimmed.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
    }

    private static String describeFailure(UpstreamHttpResult res) {
        if (res.error() != null && !res.error().isBlank()) {
            return res.error();
        }
        return "HTTP " + res.statusCode();
    }

    private static String stringArg(Map<String, Object> args, String key) {
        Object val = args.get(key);
        return val == null ? null : String.valueOf(val);
    }

    private static Integer intArg(Map<String, Object> args, String key) {
        if (args.get(key) instanceof Number n) {
            return n.intValue();
        }
        if (args.get(key) instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Double doubleArg(Map<String, Object> args, String key) {
        if (args.get(key) instanceof Number n) {
            return n.doubleValue();
        }
        if (args.get(key) instanceof String s && !s.isBlank()) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private ToolResponse successToolResponse(Map<String, Object> payload) {
        return buildToolResponse(false, payload);
    }

    private ToolResponse errorToolResponse(String code, String message, String upstream) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("status", "ERROR");
        err.put("isError", true);
        err.put("serviceAvailable", false);
        err.put("code", code);
        err.put("upstream", upstream);
        err.put("error", message);
        return buildToolResponse(true, err);
    }

    private ToolResponse buildToolResponse(boolean isError, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            return new ToolResponse(isError, List.of(new TextContent(json)), payload, null);
        } catch (JsonProcessingException ex) {
            return ToolResponse.error("Failed to serialize MCP tool payload: " + ex.getMessage());
        }
    }

    private TextResourceContents toJsonResource(String uri, Map<String, Object> data) {
        try {
            return new TextResourceContents(uri, objectMapper.writeValueAsString(data), "application/json");
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize MCP resource " + uri, ex);
        }
    }
}
