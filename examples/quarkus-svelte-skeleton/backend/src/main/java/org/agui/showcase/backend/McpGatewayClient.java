package org.agui.showcase.backend;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * MCP Gateway Client for the Nomad Travel & Local Culture Concierge Orchestrator (:8080 -> :8081).
 * Exposes MCP tools ({@code fetchCityWeather}, {@code discoverLocalCultureAndMedia},
 * {@code updateTripItinerary}), resources ({@code itinerary://active-trip}, {@code traveler://preferences}),
 * and prompt templates ({@code weekend-cultural-escape}, {@code local-music-and-food-walk}).
 */
@ApplicationScoped
public class McpGatewayClient {

    private final Map<String, Object> nomadTrip = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(1200))
            .build();

    @ConfigProperty(name = "agui.mcp.base-url", defaultValue = "http://127.0.0.1:8081")
    String mcpBaseUrl = "http://127.0.0.1:8081";

    BiFunction<String, Map<String, Object>, Map<String, Object>> remoteToolCaller = this::invokeRemoteMcpTool;

    public McpGatewayClient() {
        resetState();
    }

    public final void resetState() {
        nomadTrip.clear();
        nomadTrip.put("uri", "itinerary://active-trip");
        nomadTrip.put("activeCity", "Kyoto");
        nomadTrip.put("country", "Japan");
        nomadTrip.put("currency", "JPY");
        nomadTrip.put("exchangeRate", 151.40);
        nomadTrip.put("temperatureC", 18.4);
        nomadTrip.put("weatherCondition", "Clear Cedar & Autumn Mist");
        nomadTrip.put("nights", 4);
        nomadTrip.put("stopTitle", "Hoshinoya Arashiyama Pavilion");
        nomadTrip.put("budgetUsd", 3200.0);
        nomadTrip.put("allocatedUsd", 1680.0);
        nomadTrip.put("soundtrack", Map.of(
                "trackName", "Haru no Umi (Spring Sea — Koto & Shakuhachi)",
                "artistName", "Kyoto Koto Consort",
                "previewUrl", "https://www.w3schools.com/html/horse.mp3"));
    }

    public List<Map<String, Object>> listTools() {
        return List.of(
                Map.of(
                        "name", "fetchCityWeather",
                        "server", "nomad-mcp-concierge (:8081)",
                        "description", "Fetches live Open-Meteo weather forecast and temperature for any global travel destination."),
                Map.of(
                        "name", "discoverLocalCultureAndMedia",
                        "server", "nomad-mcp-concierge (:8081)",
                        "description", "Queries Wikipedia REST API and Apple iTunes Search API for cultural landmarks and 30s local music previews."),
                Map.of(
                        "name", "updateTripItinerary",
                        "server", "nomad-mcp-concierge (:8081)",
                        "description", "Updates or reserves a cultural sanctuary or stop in the active trip itinerary."));
    }

    public Map<String, Object> readClusterState() {
        return Map.of(
                "uri", "itinerary://active-trip",
                "nomadTrip", new LinkedHashMap<>(nomadTrip),
                "preferences", readResource("traveler://preferences"));
    }

    public Map<String, Object> readResource(String uri) {
        if ("traveler://preferences".equals(uri)) {
            return Map.of(
                    "uri", "traveler://preferences",
                    "style", "Architectural Minimalist & Slow Cultural Immersion",
                    "pace", "Unrushed (2 curated stops per day)",
                    "homeCurrency", "USD");
        }
        return new LinkedHashMap<>(nomadTrip);
    }

    public Map<String, Object> getPromptTemplate(String promptName, Map<String, Object> args) {
        String resolvedName = (promptName == null || promptName.isBlank()) ? "weekend-cultural-escape" : promptName;
        String city = String.valueOf(args != null ? args.getOrDefault("city", "Kyoto") : "Kyoto");
        if ("local-music-and-food-walk".equals(resolvedName)) {
            String genre = String.valueOf(args != null ? args.getOrDefault("genre", "traditional") : "traditional");
            return Map.of(
                    "promptName", "local-music-and-food-walk",
                    "arguments", Map.of("city", city, "genre", genre),
                    "expandedPrompt", "Curate an evening neighborhood walk in " + city
                            + " pairing regional culinary craftsmanship with intimate " + genre + " music venues.");
        }
        return Map.of(
                "promptName", resolvedName,
                "arguments", Map.of("city", city),
                "expandedPrompt", "Synthesize a 3-day architectural, culinary, and local music itinerary for " + city
                        + " with Open-Meteo telemetry, Wikipedia cultural context, and a 30s iTunes soundtrack preview.");
    }

    public Map<String, Object> callTool(String toolName, Map<String, Object> arguments) {
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        return switch (toolName) {
            case "fetchCityWeather" -> {
                String city = normalizeCity(String.valueOf(args.getOrDefault("city", "Kyoto")));
                double tempC = switch (city.toLowerCase(Locale.ROOT)) {
                    case "lisbon" -> 22.1;
                    case "reykjavik" -> 6.2;
                    default -> 18.4;
                };
                String condition = switch (city.toLowerCase(Locale.ROOT)) {
                    case "lisbon" -> "Atlantic Breeze & Golden Sun";
                    case "reykjavik" -> "Crisp Sub-Arctic Aurora Sky";
                    default -> "Clear Cedar & Autumn Mist";
                };
                String defaultCountry = A2aGatewayClient.resolveCountryForCity(city);
                Map<String, Object> weather = new LinkedHashMap<>();
                weather.put("city", city);
                weather.put("country", defaultCountry);
                weather.put("temperatureC", tempC);
                weather.put("condition", condition);
                weather.put("source", "api.open-meteo.com");
                Map<String, Object> remote = remoteToolCaller.apply("fetchCityWeather", Map.of("city", city));
                if (remote != null && !remote.isEmpty()) {
                    weather.putAll(remote);
                }
                String resolvedCity = String.valueOf(weather.getOrDefault("city", city));
                String resolvedCountry = String.valueOf(weather.getOrDefault("country", defaultCountry));
                A2aGatewayClient.registerCityCountry(city, resolvedCountry);
                A2aGatewayClient.registerCityCountry(resolvedCity, resolvedCountry);
                String resolvedCurrency = A2aGatewayClient.resolveCurrencyForCountry(resolvedCountry);
                nomadTrip.put("activeCity", resolvedCity);
                nomadTrip.put("country", resolvedCountry);
                nomadTrip.put("currency", resolvedCurrency);
                nomadTrip.put("exchangeRate", A2aGatewayClient.resolveDefaultRateForCurrency(resolvedCurrency));
                nomadTrip.put("stopTitle", defaultSanctuaryForCity(resolvedCity));
                nomadTrip.put("temperatureC", weather.getOrDefault("temperatureC", tempC));
                nomadTrip.put("weatherCondition", weather.getOrDefault("condition", condition));
                yield Map.of(
                        "toolName", "fetchCityWeather",
                        "server", "nomad-mcp-concierge (:8081)",
                        "arguments", Map.of("city", city),
                        "result", weather);
            }
            case "discoverLocalCultureAndMedia" -> {
                String city = normalizeCity(String.valueOf(args.getOrDefault("destination", "Kyoto")));
                String genre = String.valueOf(args.getOrDefault("genre", "traditional"));
                Map<String, Object> culture = new LinkedHashMap<>(buildCultureResult(city, genre));
                Map<String, Object> remote = remoteToolCaller.apply(
                        "discoverLocalCultureAndMedia",
                        Map.of("destination", city, "genre", genre));
                if (remote != null && !remote.isEmpty()) {
                    culture.putAll(remote);
                }
                String resolvedCountry = nomadTrip.get("country") instanceof String c && !c.isBlank()
                        && city.equalsIgnoreCase(String.valueOf(nomadTrip.get("activeCity")))
                        ? c
                        : A2aGatewayClient.resolveCountryForCity(city);
                String resolvedCurrency = A2aGatewayClient.resolveCurrencyForCountry(resolvedCountry);
                nomadTrip.put("activeCity", city);
                nomadTrip.put("country", resolvedCountry);
                nomadTrip.put("currency", resolvedCurrency);
                nomadTrip.put("exchangeRate", A2aGatewayClient.resolveDefaultRateForCurrency(resolvedCurrency));
                if (culture.get("audioTrack") != null) {
                    nomadTrip.put("soundtrack", culture.get("audioTrack"));
                }
                if (culture.get("heroImageUrl") != null) {
                    nomadTrip.put("heroImageUrl", culture.get("heroImageUrl"));
                }
                if (culture.get("culturalSummary") != null) {
                    nomadTrip.put("culturalSummary", culture.get("culturalSummary"));
                }
                yield Map.of(
                        "toolName", "discoverLocalCultureAndMedia",
                        "server", "nomad-mcp-concierge (:8081)",
                        "arguments", Map.of("destination", city, "genre", genre),
                        "result", culture);
            }
            case "updateTripItinerary" -> {
                String city = normalizeCity(String.valueOf(args.getOrDefault("city", "Kyoto")));
                String stopTitle = String.valueOf(args.getOrDefault("stopTitle", defaultSanctuaryForCity(city)));
                int nights = args.get("nights") instanceof Number n ? n.intValue() : 4;
                double costUsd = args.get("costUsd") instanceof Number n ? n.doubleValue() : 1680.0;
                String country = A2aGatewayClient.resolveCountryForCity(city);
                String currency = A2aGatewayClient.resolveCurrencyForCountry(country);
                nomadTrip.put("activeCity", city);
                nomadTrip.put("country", country);
                nomadTrip.put("currency", currency);
                nomadTrip.put("exchangeRate", A2aGatewayClient.resolveDefaultRateForCurrency(currency));
                nomadTrip.put("stopTitle", stopTitle);
                nomadTrip.put("nights", nights);
                nomadTrip.put("allocatedUsd", costUsd);
                remoteToolCaller.apply("updateTripItinerary", Map.of("city", city, "stopTitle", stopTitle, "nights", nights, "costUsd", costUsd));
                yield Map.of(
                        "toolName", "updateTripItinerary",
                        "server", "nomad-mcp-concierge (:8081)",
                        "arguments", Map.of("city", city, "stopTitle", stopTitle, "nights", nights, "costUsd", costUsd),
                        "result", new LinkedHashMap<>(nomadTrip));
            }
            default -> throw new IllegalArgumentException("Unsupported MCP tool: " + toolName);
        };
    }

    Map<String, Object> invokeRemoteMcpTool(String toolName, Map<String, Object> arguments) {
        try {
            String bodyJson = objectMapper.writeValueAsString(Map.of("tool", toolName, "arguments", arguments));
            HttpRequest req = HttpRequest.newBuilder(URI.create(mcpBaseUrl + "/api/mcp/tools/call"))
                    .timeout(Duration.ofMillis(2000))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200 && res.body() != null) {
                JsonNode root = objectMapper.readTree(res.body());
                if (!root.path("isError").asBoolean(false) && root.has("result") && root.path("result").isObject()) {
                    return objectMapper.convertValue(root.path("result"), new TypeReference<Map<String, Object>>() {});
                }
            }
        } catch (Exception ignored) {
        }
        return Map.of();
    }

    private Map<String, Object> buildCultureResult(String city, String genre) {
        return switch (city.toLowerCase(Locale.ROOT)) {
            case "lisbon" -> Map.of(
                    "destination", "Lisbon",
                    "country", "Portugal",
                    "currency", "EUR",
                    "exchangeRate", 0.92,
                    "genre", genre,
                    "culturalSummary", "Lisbon's hillside districts of Alfama and Chiado pair hand-painted azulejo tile facades and Miradouro terraces with intimate evening Fado sessions.",
                    "heroImageUrl", "https://images.unsplash.com/photo-1513735492246-483525079686?auto=format&fit=crop&w=1000&q=80",
                    "audioTrack", Map.of(
                            "trackName", "Canção do Mar (Acoustic Fado Session)",
                            "artistName", "Alfama Guitarra Ensemble",
                            "previewUrl", "https://www.w3schools.com/html/horse.mp3",
                            "durationSeconds", 30));
            case "reykjavik" -> Map.of(
                    "destination", "Reykjavik",
                    "country", "Iceland",
                    "currency", "ISK",
                    "exchangeRate", 137.80,
                    "genre", genre,
                    "culturalSummary", "Reykjavik contrasts basalt coastal geometry at Harpa Concert Hall and geothermal Sky Lagoons with Nordic neo-classical string arrangements.",
                    "heroImageUrl", "https://images.unsplash.com/photo-1504893524553-b855bce32c67?auto=format&fit=crop&w=1000&q=80",
                    "audioTrack", Map.of(
                            "trackName", "Hoppípolla — Nordic Strings",
                            "artistName", "Reykjavik Chamber Collective",
                            "previewUrl", "https://www.w3schools.com/html/horse.mp3",
                            "durationSeconds", 30));
            default -> {
                String country = A2aGatewayClient.resolveCountryForCity(city);
                String currency = A2aGatewayClient.resolveCurrencyForCountry(country);
                double rate = A2aGatewayClient.resolveDefaultRateForCurrency(currency);
                yield Map.of(
                        "destination", city,
                        "country", country,
                        "currency", currency,
                        "exchangeRate", rate,
                        "genre", genre,
                        "culturalSummary", city + " weaves architectural sanctuaries, neighborhood culinary craft, and local acoustic traditions.",
                        "heroImageUrl", "https://images.unsplash.com/photo-1493976040374-85c8e12f0c0e?auto=format&fit=crop&w=1000&q=80",
                        "audioTrack", Map.of(
                                "trackName", "Haru no Umi (Spring Sea — Koto & Shakuhachi)",
                                "artistName", "Kyoto Koto Consort",
                                "previewUrl", "https://www.w3schools.com/html/horse.mp3",
                                "durationSeconds", 30));
            }
        };
    }

    private String normalizeCity(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Kyoto";
        }
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("lisbon") || lower.contains("portugal")) {
            return "Lisbon";
        }
        if (lower.contains("reykjavik") || lower.contains("iceland")) {
            return "Reykjavik";
        }
        if (lower.contains("kyoto") || lower.contains("japan")) {
            return "Kyoto";
        }
        String trimmed = raw.trim();
        return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
    }

    static String defaultSanctuaryForCity(String city) {
        return switch (city.toLowerCase(Locale.ROOT)) {
            case "kyoto" -> "Hoshinoya Arashiyama Pavilion";
            case "lisbon" -> "Palácio Belmonte Alfama Suite";
            case "reykjavik" -> "The Retreat at Blue Lagoon Moss Villa";
            case "tokyo" -> "Aman Tokyo Otemachi Sanctuary";
            case "paris" -> "Cour des Vosges Marais Residence";
            case "oaxaca" -> "Casa Oaxaca Courtyard Sanctuary";
            default -> city + " Architectural Sanctuary";
        };
    }
}
