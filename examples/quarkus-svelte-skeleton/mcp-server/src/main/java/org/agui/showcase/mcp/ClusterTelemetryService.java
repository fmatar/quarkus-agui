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
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Quarkus MCP Server (:8081) for the Nomad Travel, Weather, Local Culture & Itinerary Concierge.
 * Exposes MCP Tools (@Tool), Resources (@Resource), Resource Templates (@ResourceTemplate),
 * Prompt Templates (@Prompt), Prompt Completions (@CompletePrompt), and Sampling endpoints
 * backed by Open-Meteo Geocoding & Forecast APIs, Wikipedia REST API v1, and Apple iTunes Search API.
 */
@ApplicationScoped
public class ClusterTelemetryService {

    public static final String SERVER_NAME = "nomad-mcp-concierge";

    private static final List<String> CURATED_GLOBAL_CITIES = List.of(
        "Kyoto", "Lisbon", "Reykjavik", "Tokyo", "Beirut", "Oaxaca", "Paris", "Rome",
        "Barcelona", "Marrakech", "Copenhagen", "Vienna", "Berlin", "Seoul", "Istanbul",
        "Buenos Aires", "Amman", "Cairo", "Athens", "Rio de Janeiro"
    );

    private final Map<String, Object> activeTrip = new ConcurrentHashMap<>();
    private final Map<String, Object> travelerPreferences = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(1500))
            .build();

    String openMeteoBaseUrl = "https://api.open-meteo.com";
    String openMeteoGeoBaseUrl = "https://geocoding-api.open-meteo.com";
    String wikipediaBaseUrl = "https://en.wikipedia.org";
    String itunesBaseUrl = "https://itunes.apple.com";
    Function<String, String> httpFetcher = this::fetchUrlViaHttp;

    @PostConstruct
    void init() {
        resetState();
    }

    public void resetState() {
        activeTrip.clear();
        activeTrip.put("uri", "itinerary://active-trip");
        activeTrip.put("activeCity", "Kyoto");
        activeTrip.put("country", "Japan");
        activeTrip.put("currency", "JPY");
        activeTrip.put("exchangeRate", 151.40);
        activeTrip.put("temperatureC", 18.4);
        activeTrip.put("weatherCondition", "Clear Cedar & Autumn Mist");
        activeTrip.put("nights", 4);
        activeTrip.put("stopTitle", "Hoshinoya Arashiyama Pavilion");
        activeTrip.put("budgetUsd", 3200.0);
        activeTrip.put("allocatedUsd", 1680.0);
        activeTrip.put("soundtrack", Map.of(
            "trackName", "Four Seasons in Kyoto (Koto & Shakuhachi)",
            "artistName", "Chikatoyo Tsujimoto",
            "previewUrl", "https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/9b/27/76/9b2776c5-65cd-87f1-3f33-ccbccc642285/mzaf_16549365214060917501.plus.aac.p.m4a",
            "durationSeconds", 30
        ));

        travelerPreferences.clear();
        travelerPreferences.put("uri", "traveler://preferences");
        travelerPreferences.put("style", "Architectural Minimalist & Slow Cultural Immersion");
        travelerPreferences.put("dietary", "Seasonal Local Terroir");
        travelerPreferences.put("musicTaste", "Analog Vinyl & Regional Acoustic");
        travelerPreferences.put("pace", "Unrushed (2 curated stops per day)");
        travelerPreferences.put("audioPreference", "Authentic regional acoustic & chamber traditions (30s iTunes preview)");
        travelerPreferences.put("homeCurrency", "USD");
    }

    private String fetchUrlViaHttp(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(1500))
                    .header("User-Agent", "Nomad-Quarkus-AGUI/1.0")
                    .GET()
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            return res.statusCode() == 200 ? res.body() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    @Resource(uri = "itinerary://active-trip", description = "Active Nomad traveler itinerary, destination weather, and sanctuary allocation")
    public TextResourceContents activeTripResource() {
        try {
            return new TextResourceContents(
                "itinerary://active-trip",
                objectMapper.writeValueAsString(getActiveTripSnapshot()),
                "application/json"
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize active trip resource", e);
        }
    }

    @Resource(uri = "traveler://preferences", description = "Traveler architectural, culinary, and acoustic preferences")
    public TextResourceContents travelerPreferencesResource() {
        try {
            return new TextResourceContents(
                "traveler://preferences",
                objectMapper.writeValueAsString(getTravelerPreferences()),
                "application/json"
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize traveler preferences resource", e);
        }
    }

    @ResourceTemplate(
        uriTemplate = "itinerary://city/{city}/dossier",
        description = "Parameterized destination architectural, meteorological, and acoustic dossier"
    )
    public TextResourceContents cityDossierResourceTemplate(
            @ResourceTemplateArg(name = "city") String city) {
        String resolvedCity = normalizeCity(city);
        String uri = "itinerary://city/" + resolvedCity.toLowerCase(Locale.ROOT).replace(" ", "-") + "/dossier";
        try {
            return new TextResourceContents(
                uri,
                objectMapper.writeValueAsString(getCityDossier(resolvedCity)),
                "application/json"
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize city dossier resource template", e);
        }
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
                .filter(c -> c.toLowerCase(Locale.ROOT).startsWith(normalized) || c.toLowerCase(Locale.ROOT).contains(normalized))
                .limit(8)
                .toList();
        return matches.isEmpty() ? List.of(normalizeCity(prefix)) : matches;
    }

    @Prompt(name = "weekend-cultural-escape", description = "Synthesize a 3-day architectural, culinary, and musical itinerary for a target city")
    public PromptMessage weekendCulturalEscapePrompt(
            @PromptArg(description = "Destination city", defaultValue = "Kyoto") String city) {
        String target = (city == null || city.isBlank()) ? "Kyoto" : city.trim();
        return PromptMessage.withUserRole(new TextContent(
            "Curate a 3-day architectural, culinary, and local music itinerary in " + target
            + " with live weather, currency conversion, and a 30s local soundtrack preview."));
    }

    @Prompt(name = "local-music-and-food-walk", description = "Curate an evening listening bar and neighborhood culinary walk")
    public PromptMessage localMusicAndFoodWalkPrompt(
            @PromptArg(description = "Destination city", defaultValue = "Lisbon") String city) {
        String target = (city == null || city.isBlank()) ? "Lisbon" : city.trim();
        return PromptMessage.withUserRole(new TextContent(
            "Design an intimate evening listening-bar and neighborhood culinary walk through " + target
            + " with playable audio preview and local budget breakdown."));
    }

    @Tool(description = "Fetch live Open-Meteo weather forecast and temperature for a travel destination")
    public Map<String, Object> fetchCityWeather(
            @ToolArg(description = "City name (e.g., Kyoto, Lisbon, Reykjavik)", defaultValue = "Kyoto") String city) {
        String resolvedCity = normalizeCity(city);
        String lowerCity = resolvedCity.toLowerCase(Locale.ROOT);
        boolean isPreseeded = List.of(
                "kyoto", "lisbon", "reykjavik", "tokyo", "paris", "rome",
                "barcelona", "london", "seoul", "new york", "marrakech").contains(lowerCity);
        double[] coords = switch (lowerCity) {
            case "lisbon" -> new double[]{38.7223, -9.1393, 22.1, 14.0};
            case "reykjavik" -> new double[]{64.1466, -21.9426, 6.2, 19.5};
            case "tokyo" -> new double[]{35.6762, 139.6503, 19.2, 11.0};
            case "paris" -> new double[]{48.8566, 2.3522, 16.8, 12.0};
            case "rome" -> new double[]{41.9028, 12.4964, 21.4, 9.5};
            case "barcelona" -> new double[]{41.3874, 2.1686, 22.5, 13.0};
            case "london" -> new double[]{51.5072, -0.1276, 14.6, 15.0};
            case "seoul" -> new double[]{37.5665, 126.9780, 17.5, 10.0};
            case "new york" -> new double[]{40.7128, -74.0060, 18.0, 14.5};
            case "marrakech" -> new double[]{31.6295, -7.9811, 26.4, 10.5};
            default -> new double[]{35.0116, 135.7681, 18.4, 8.5};
        };
        String condition = switch (lowerCity) {
            case "lisbon" -> "Atlantic Breeze & Golden Sun";
            case "reykjavik" -> "Crisp Sub-Arctic Aurora Sky";
            case "tokyo" -> "Clear Pacific Horizon";
            case "paris" -> "Soft Seine Daylight";
            case "rome" -> "Warm Mediterranean Sun";
            case "barcelona" -> "Balearic Coastal Breeze";
            case "marrakech" -> "Warm Atlas Foothill Sun";
            default -> "Clear Cedar & Autumn Mist";
        };
        String resolvedCountry = switch (lowerCity) {
            case "lisbon", "porto" -> "Portugal";
            case "reykjavik" -> "Iceland";
            case "paris", "lyon", "nice" -> "France";
            case "rome", "milan", "florence", "venice" -> "Italy";
            case "barcelona", "madrid", "seville" -> "Spain";
            case "london", "edinburgh" -> "United Kingdom";
            case "seoul" -> "South Korea";
            case "new york", "san francisco" -> "United States";
            case "marrakech", "casablanca" -> "Morocco";
            case "beirut" -> "Lebanon";
            case "amman" -> "Jordan";
            case "dubai", "abu dhabi" -> "United Arab Emirates";
            case "cairo" -> "Egypt";
            case "istanbul" -> "Turkey";
            case "oaxaca", "mexico city" -> "Mexico";
            case "buenos aires" -> "Argentina";
            case "rio de janeiro", "sao paulo" -> "Brazil";
            case "kyoto", "tokyo", "osaka" -> "Japan";
            default -> resolvedCity;
        };

        if (!isPreseeded) {
            try {
                String geoUrl = openMeteoGeoBaseUrl + "/v1/search?name="
                        + URLEncoder.encode(resolvedCity, StandardCharsets.UTF_8)
                        + "&count=1&language=en&format=json";
                String geoBody = httpFetcher.apply(geoUrl);
                if (geoBody != null) {
                    JsonNode results = objectMapper.readTree(geoBody).path("results");
                    if (results.isArray() && !results.isEmpty()) {
                        JsonNode first = results.get(0);
                        coords[0] = first.path("latitude").asDouble(coords[0]);
                        coords[1] = first.path("longitude").asDouble(coords[1]);
                        resolvedCity = first.path("name").asText(resolvedCity);
                        resolvedCountry = first.path("country").asText(resolvedCountry);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        double tempC = coords[2];
        double windKph = coords[3];
        String source = "open-meteo-fallback";
        try {
            String url = String.format(Locale.ROOT,
                "%s/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,wind_speed_10m",
                openMeteoBaseUrl, coords[0], coords[1]);
            String body = httpFetcher.apply(url);
            if (body != null) {
                JsonNode current = objectMapper.readTree(body).path("current");
                if (current.has("temperature_2m")) {
                    tempC = current.path("temperature_2m").asDouble(tempC);
                    windKph = current.path("wind_speed_10m").asDouble(windKph);
                    if (current.has("weather_code")) {
                        condition = describeWmoCode(current.path("weather_code").asInt(0), condition);
                    }
                    source = "api.open-meteo.com";
                }
            }
        } catch (Exception ignored) {
        }
        Map<String, Object> weather = new LinkedHashMap<>();
        weather.put("city", resolvedCity);
        weather.put("country", resolvedCountry);
        weather.put("latitude", coords[0]);
        weather.put("longitude", coords[1]);
        weather.put("temperatureC", tempC);
        weather.put("windSpeedKph", windKph);
        weather.put("condition", condition);
        weather.put("source", source);
        activeTrip.put("activeCity", resolvedCity);
        activeTrip.put("country", resolvedCountry);
        activeTrip.put("temperatureC", tempC);
        activeTrip.put("weatherCondition", condition);
        return weather;
    }

    private String describeWmoCode(int code, String fallback) {
        return switch (code) {
            case 0 -> "Clear Sky & Crisp Light";
            case 1, 2 -> "Partly Clouded Horizon";
            case 3 -> "Soft Overcast Canopy";
            case 45, 48 -> "Atmospheric Mist & Fog";
            case 51, 53, 55 -> "Light Coastal Drizzle";
            case 61, 63, 65, 80, 81, 82 -> "Fresh Rain Showers";
            case 71, 73, 75, 77, 85, 86 -> "Crisp Snowfall";
            case 95, 96, 99 -> "Electric Storm Front";
            default -> fallback;
        };
    }

    @Tool(description = "Discover local cultural landmarks via Wikipedia REST API and a 30s local music preview via Apple iTunes Search API")
    public Map<String, Object> discoverLocalCultureAndMedia(
            @ToolArg(description = "City or country name", defaultValue = "Kyoto") String destination,
            @ToolArg(description = "Music or cultural search query", defaultValue = "traditional") String genre) {
        String resolvedCity = normalizeCity(destination);
        String lowerCity = resolvedCity.toLowerCase(Locale.ROOT);
        String cityDefaultQuery = switch (lowerCity) {
            case "lisbon", "porto" -> "Amalia Rodrigues Fado";
            case "reykjavik" -> "Olafur Arnalds strings";
            case "kyoto" -> "koto shakuhachi";
            case "tokyo", "osaka" -> "Ryuichi Sakamoto";
            case "paris", "lyon" -> "Erik Satie piano";
            case "rome", "florence", "venice", "milan" -> "Ennio Morricone";
            case "barcelona", "madrid", "seville" -> "Paco de Lucia guitar";
            case "marrakech" -> "gnawa oud";
            case "oaxaca", "mexico city" -> "Lila Downs Oaxaca";
            case "buenos aires" -> "Astor Piazzolla tango";
            case "vienna" -> "Vienna Philharmonic";
            case "berlin" -> "Nils Frahm";
            case "copenhagen" -> "Danish String Quartet";
            case "seoul" -> "Yiruma";
            case "istanbul" -> "Mercan Dede";
            case "beirut" -> "Fairuz";
            case "cairo" -> "Umm Kulthum";
            case "amman" -> "Faraj Abyad";
            case "dubai", "abu dhabi" -> "Naseer Shamma";
            case "rio de janeiro", "sao paulo" -> "Antonio Carlos Jobim";
            case "athens" -> "Mikis Theodorakis";
            default -> resolvedCity;
        };
        String trimmedGenre = genre == null ? "" : genre.trim();
        boolean isGenericGenre = trimmedGenre.isEmpty()
                || List.of("traditional", "jazz", "ambient", "fado", "acoustic", "classical", "folk")
                        .contains(trimmedGenre.toLowerCase(Locale.ROOT));
        String resolvedGenre = isGenericGenre ? cityDefaultQuery : trimmedGenre;

        String wikiExtract = switch (lowerCity) {
            case "lisbon" -> "Lisbon's hillside neighborhoods of Alfama and Chiado blend maritime azulejo tilework, historic tramlines, and intimate Fado houses.";
            case "reykjavik" -> "Reykjavik pairs geothermal coastal lagoons and modernist Nordic architecture at Harpa with an experimental neo-classical music scene.";
            case "kyoto" -> "Kyoto preserves over a millennium of Zen temple gardens, machiya timber townhouses in Gion, and contemplative koto and shakuhachi acoustics.";
            default -> resolvedCity + " blends historic and modernist architecture, neighborhood culinary traditions, and local acoustic heritage.";
        };
        String imageUrl = switch (lowerCity) {
            case "lisbon" -> "https://images.unsplash.com/photo-1513735492246-483525079686?auto=format&fit=crop&w=1000&q=80";
            case "reykjavik" -> "https://images.unsplash.com/photo-1504893524553-b855bce32c67?auto=format&fit=crop&w=1000&q=80";
            default -> "https://images.unsplash.com/photo-1493976040374-85c8e12f0c0e?auto=format&fit=crop&w=1000&q=80";
        };
        String trackName = switch (lowerCity) {
            case "lisbon" -> "Canção do Mar (Acoustic Fado Session)";
            case "reykjavik" -> "Hoppípolla — Nordic Strings";
            default -> "Four Seasons in Kyoto (Koto & Shakuhachi)";
        };
        String artistName = switch (lowerCity) {
            case "lisbon" -> "Alfama Guitarra Ensemble";
            case "reykjavik" -> "Reykjavik Chamber Collective";
            default -> "Chikatoyo Tsujimoto";
        };
        String previewUrl = "https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/9b/27/76/9b2776c5-65cd-87f1-3f33-ccbccc642285/mzaf_16549365214060917501.plus.aac.p.m4a";
        String artworkUrl = imageUrl;

        try {
            String wikiTitle = URLEncoder.encode(resolvedCity.replace(' ', '_'), StandardCharsets.UTF_8);
            String wikiUrl = wikipediaBaseUrl + "/api/rest_v1/page/summary/" + wikiTitle;
            String wikiBody = httpFetcher.apply(wikiUrl);
            if (wikiBody != null) {
                JsonNode root = objectMapper.readTree(wikiBody);
                if (root.hasNonNull("extract") && !root.path("extract").asText().isBlank()) {
                    wikiExtract = root.path("extract").asText();
                }
                if (root.path("originalimage").hasNonNull("source")) {
                    imageUrl = root.path("originalimage").path("source").asText();
                } else if (root.path("thumbnail").hasNonNull("source")) {
                    imageUrl = root.path("thumbnail").path("source").asText();
                }
            }
        } catch (Exception ignored) {
        }

        try {
            String searchTerm = URLEncoder.encode(resolvedGenre, StandardCharsets.UTF_8);
            String itunesUrl = itunesBaseUrl + "/search?term=" + searchTerm + "&media=music&entity=song&limit=5";
            String itunesBody = httpFetcher.apply(itunesUrl);
            if (itunesBody != null) {
                JsonNode results = objectMapper.readTree(itunesBody).path("results");
                if (results.isArray() && !results.isEmpty()) {
                    for (JsonNode item : results) {
                        if (item.hasNonNull("previewUrl") && !item.path("previewUrl").asText().isBlank()) {
                            trackName = item.path("trackName").asText(trackName);
                            artistName = item.path("artistName").asText(artistName);
                            previewUrl = item.path("previewUrl").asText(previewUrl);
                            artworkUrl = item.path("artworkUrl100").asText(artworkUrl).replace("100x100bb", "300x300bb");
                            break;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        Map<String, Object> audioTrack = new LinkedHashMap<>();
        audioTrack.put("trackName", trackName);
        audioTrack.put("artistName", artistName);
        audioTrack.put("previewUrl", previewUrl);
        audioTrack.put("artworkUrl", artworkUrl);
        audioTrack.put("durationSeconds", 30);

        activeTrip.put("activeCity", resolvedCity);
        activeTrip.put("soundtrack", audioTrack);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("destination", resolvedCity);
        payload.put("genre", resolvedGenre);
        payload.put("culturalSummary", wikiExtract);
        payload.put("heroImageUrl", imageUrl);
        payload.put("audioTrack", audioTrack);
        payload.put("wikiSourceUrl", "https://en.wikipedia.org/wiki/" + URLEncoder.encode(resolvedCity, StandardCharsets.UTF_8));
        return payload;
    }

    @Tool(description = "Update or reserve a cultural stop in the active trip itinerary")
    public Map<String, Object> updateTripItinerary(
            @ToolArg(description = "Destination city") String city,
            @ToolArg(description = "Stop or boutique stay title") String stopTitle,
            @ToolArg(description = "Number of nights or duration", defaultValue = "3") int nights,
            @ToolArg(description = "Estimated cost in USD", defaultValue = "950") double costUsd) {
        String resolvedCity = normalizeCity(city);
        String resolvedStop = (stopTitle == null || stopTitle.isBlank())
            ? resolvedCity + " Architectural Sanctuary"
            : stopTitle.trim();
        int clampedNights = Math.max(1, Math.min(nights, 30));
        double safeCost = Math.max(0.0, costUsd);

        activeTrip.put("activeCity", resolvedCity);
        activeTrip.put("stopTitle", resolvedStop);
        activeTrip.put("nights", clampedNights);
        activeTrip.put("allocatedUsd", safeCost);
        return getActiveTripSnapshot();
    }

    public Map<String, Object> getActiveTripSnapshot() {
        return new LinkedHashMap<>(activeTrip);
    }

    public Map<String, Object> getTravelerPreferences() {
        return new LinkedHashMap<>(travelerPreferences);
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
        String resolvedCity = normalizeCity(city);
        String slug = resolvedCity.toLowerCase(Locale.ROOT).replace(" ", "-");
        Map<String, Object> weather = fetchCityWeather(resolvedCity);
        Map<String, Object> dossier = new LinkedHashMap<>();
        dossier.put("uri", "itinerary://city/" + slug + "/dossier");
        dossier.put("city", resolvedCity);
        dossier.put("country", weather.getOrDefault("country", "Japan"));
        dossier.put("temperatureC", weather.getOrDefault("temperatureC", 18.4));
        dossier.put("condition", weather.getOrDefault("condition", "Clear Sky"));
        dossier.put("architecturalHighlight", switch (resolvedCity.toLowerCase(Locale.ROOT)) {
            case "lisbon" -> "Alfama Azulejo Terraces, Chiado Pombaline Ironwork & MAAT Riverfront";
            case "reykjavik" -> "Harpa Basalt Columns, Hallgrímskirkja Concrete Vault & Turf Sanctuaries";
            case "beirut" -> "Sursock Museum Ottoman-Venetian Arches, Mar Mikhael Heritage Stairs & Corniche";
            case "tokyo" -> "Tadao Ando Concrete Galleries, Omotesando Modernism & Yanaka Timber Alleys";
            case "oaxaca" -> "Santo Domingo Cantera Stone Courtyards & Contemporary Adobe Ateliers";
            default -> "Sukiya-zukuri Cedar Machiya, Katsura Imperial Joinery & Zen Dry-Landscape Gardens";
        });
        dossier.put("acousticProfile", sampleAcousticMoodProfile(
            resolvedCity,
            String.valueOf(weather.getOrDefault("condition", "Clear Sky")),
            "traditional"
        ));
        return dossier;
    }

    public Map<String, Object> sampleAcousticMoodProfile(String city, String weatherCondition, String genre) {
        String resolvedCity = normalizeCity(city);
        String cond = (weatherCondition == null || weatherCondition.isBlank()) ? "Clear Sky" : weatherCondition;
        String resolvedGenre = (genre == null || genre.isBlank()) ? "traditional" : genre;
        String moodDistillation = switch (resolvedCity.toLowerCase(Locale.ROOT)) {
            case "lisbon" -> "Warm 12-string Portuguese guitarra resonance paired with Atlantic twilight breeze (" + cond + ").";
            case "reykjavik" -> "Intimate bowed cello harmonics and sub-Arctic chamber acoustics inside basalt walls (" + cond + ").";
            case "beirut" -> "Levantine oud and Fairuz orchestral nostalgia drifting across Mediterranean limestone courtyards (" + cond + ").";
            case "tokyo" -> "Tube-amplified modal jazz vinyl warmth in a subterranean Shibuya kissaten (" + cond + ").";
            default -> "Contemplative silk-stringed koto and bamboo shakuhachi phrasing tuned to cedar courtyard mist (" + cond + ").";
        };
        return Map.of(
            "protocol", "mcp:sampling",
            "server", SERVER_NAME,
            "city", resolvedCity,
            "genre", resolvedGenre,
            "weatherCondition", cond,
            "sampledMood", moodDistillation,
            "recommendedListeningWindow", "19:30 – 22:00 Local Twilight"
        );
    }

    public List<Map<String, Object>> listPrompts() {
        return List.of(
            Map.of(
                "name", "weekend-cultural-escape",
                "description", "Synthesize a 3-day architectural, culinary, and musical itinerary for a target city",
                "arguments", List.of("city")
            ),
            Map.of(
                "name", "local-music-and-food-walk",
                "description", "Curate an evening listening bar and neighborhood culinary walk",
                "arguments", List.of("city")
            )
        );
    }

    public Map<String, Object> renderPrompt(String promptName, String city) {
        String resolvedName = (promptName == null || promptName.isBlank()) ? "weekend-cultural-escape" : promptName.trim();
        PromptMessage msg = "local-music-and-food-walk".equals(resolvedName)
            ? localMusicAndFoodWalkPrompt(city)
            : weekendCulturalEscapePrompt(city);
        TextContent content = (TextContent) msg.content();
        return Map.of(
            "promptName", resolvedName,
            "role", msg.role(),
            "prompt", content.text()
        );
    }

    private String normalizeCity(String city) {
        if (city == null || city.isBlank()) {
            return "Kyoto";
        }
        String lower = city.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("lisbon") || lower.contains("portugal")) {
            return "Lisbon";
        }
        if (lower.contains("reykjavik") || lower.contains("iceland")) {
            return "Reykjavik";
        }
        if (lower.contains("kyoto") || lower.contains("japan")) {
            return "Kyoto";
        }
        return city.trim();
    }

    public List<Map<String, Object>> listTools() {
        return List.of(
            Map.of(
                "name", "fetchCityWeather",
                "description", "Fetch live Open-Meteo weather forecast and temperature for a travel destination",
                "parameters", List.of("city")
            ),
            Map.of(
                "name", "discoverLocalCultureAndMedia",
                "description", "Discover local cultural landmarks via Wikipedia REST API and a 30s local music preview via Apple iTunes Search API",
                "parameters", List.of("destination", "genre")
            ),
            Map.of(
                "name", "updateTripItinerary",
                "description", "Update or reserve a cultural stop in the active trip itinerary",
                "parameters", List.of("city", "stopTitle", "nights", "costUsd")
            )
        );
    }

    public McpToolInvocationResponse dispatchTool(McpToolInvocationRequest request) {
        long startNs = System.nanoTime();
        String toolName = (request == null || request.tool() == null) ? "" : request.tool().trim();
        Map<String, Object> args = (request == null || request.arguments() == null) ? Map.of() : request.arguments();

        try {
            Object output = switch (toolName) {
                case "fetchCityWeather" -> fetchCityWeather(String.valueOf(args.getOrDefault("city", "Kyoto")));
                case "discoverLocalCultureAndMedia" -> discoverLocalCultureAndMedia(
                    String.valueOf(args.getOrDefault("destination", "Kyoto")),
                    String.valueOf(args.getOrDefault("genre", "traditional"))
                );
                case "updateTripItinerary" -> {
                    String city = String.valueOf(args.getOrDefault("city", "Kyoto"));
                    String stopTitle = String.valueOf(args.getOrDefault("stopTitle", "Hoshinoya Arashiyama"));
                    int nights = args.get("nights") instanceof Number n ? n.intValue() : 3;
                    double costUsd = args.get("costUsd") instanceof Number n ? n.doubleValue() : 950.0;
                    yield updateTripItinerary(city, stopTitle, nights, costUsd);
                }
                default -> throw new IllegalArgumentException("Unsupported MCP tool: " + toolName);
            };
            long elapsedMs = Math.max(1L, (System.nanoTime() - startNs) / 1_000_000L);
            return new McpToolInvocationResponse(SERVER_NAME, toolName, output, false, elapsedMs);
        } catch (RuntimeException ex) {
            long elapsedMs = Math.max(1L, (System.nanoTime() - startNs) / 1_000_000L);
            return new McpToolInvocationResponse(SERVER_NAME, toolName, Map.of("error", ex.getMessage()), true, elapsedMs);
        }
    }
}
