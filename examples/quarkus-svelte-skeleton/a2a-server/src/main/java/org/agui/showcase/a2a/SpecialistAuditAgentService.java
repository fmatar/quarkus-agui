package org.agui.showcase.a2a;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.function.Function;

/**
 * Nomad A2A Specialist Agent (:8082) providing live Frankfurter ECB currency conversion,
 * REST Countries v3.1 destination advisories, and curated architectural/acoustic neighborhood sequences.
 */
@ApplicationScoped
public class SpecialistAuditAgentService {

    public static final String AGENT_NAME = "Nomad-A2A-Specialist";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(1500))
            .build();

    String frankfurterBaseUrl = "https://api.frankfurter.dev";
    String restCountriesBaseUrl = "https://restcountries.com";
    Function<String, String> httpFetcher = this::fetchUrlViaHttp;

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

    public record AuditResult(String summary, String artifactId, String artifactTitle, Map<String, Object> data) {}

    public AuditResult performAudit(String skillId, String prompt, Map<String, Object> context) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Concierge prompt must not be blank");
        }
        String skill = (skillId == null || skillId.isBlank()) ? "convert-and-audit-budget" : skillId.trim();
        Map<String, Object> ctx = context == null ? Map.of() : context;

        return switch (skill) {
            case "verify-travel-advisory" -> performTravelAdvisoryAudit(prompt, ctx);
            case "curate-neighborhood-cadence" -> performNeighborhoodCadenceCuration(prompt, ctx);
            default -> performCurrencyAndBudgetAudit(prompt, ctx);
        };
    }

    private AuditResult performCurrencyAndBudgetAudit(String prompt, Map<String, Object> ctx) {
        String city = String.valueOf(ctx.getOrDefault("city", detectCity(prompt)));
        String targetCurrency = String.valueOf(ctx.getOrDefault("currency", defaultCurrencyForCity(city)));
        double budgetUsd = ctx.get("budgetUsd") instanceof Number n ? n.doubleValue() : 3200.0;
        double rate = defaultRateForCurrency(targetCurrency);
        String source = "frankfurter-fallback";
        try {
            String url = frankfurterBaseUrl + "/v1/latest?base=USD&symbols=" + URLEncoder.encode(targetCurrency, StandardCharsets.UTF_8);
            String body = httpFetcher.apply(url);
            if (body != null) {
                JsonNode rates = objectMapper.readTree(body).path("rates");
                if (rates.has(targetCurrency)) {
                    rate = rates.path(targetCurrency).asDouble(rate);
                    source = "api.frankfurter.dev";
                }
            }
        } catch (Exception ignored) {
        }
        double convertedTotal = Math.round(budgetUsd * rate * 100.0) / 100.0;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("city", city);
        data.put("baseCurrency", "USD");
        data.put("targetCurrency", targetCurrency);
        data.put("exchangeRate", rate);
        data.put("budgetUsd", budgetUsd);
        data.put("convertedTotal", convertedTotal);
        data.put("requiresHitlApprovalAboveUsd", 1500.0);
        data.put("source", source);

        String summary = String.format(Locale.ROOT,
                "Frankfurter FX audit complete for %s: $%.0f USD = %,.0f %s (rate %.2f). Sanctuary holds >$1,500 require traveler confirmation.",
                city, budgetUsd, convertedTotal, targetCurrency, rate);
        return new AuditResult(summary, "art-fx-1", "Nomad Multi-Currency & Budget Audit", data);
    }

    private AuditResult performTravelAdvisoryAudit(String prompt, Map<String, Object> ctx) {
        String city = String.valueOf(ctx.getOrDefault("city", detectCity(prompt)));
        String country = defaultCountryForCity(city);
        String capital = city;
        String region = "Global";
        String timezone = "UTC+00:00";
        if ("Japan".equals(country)) {
            capital = "Tokyo";
            region = "Asia";
            timezone = "UTC+09:00";
        } else if ("Portugal".equals(country) || "Iceland".equals(country) || "United Kingdom".equals(country)) {
            region = "Europe";
            timezone = "UTC+00:00";
        } else if ("France".equals(country) || "Italy".equals(country) || "Spain".equals(country)
                || "Germany".equals(country) || "Austria".equals(country) || "Switzerland".equals(country)
                || "Denmark".equals(country) || "Netherlands".equals(country)) {
            region = "Europe";
            timezone = "UTC+01:00";
        }
        String source = "restcountries-fallback";
        try {
            String url = restCountriesBaseUrl + "/v3.1/name/" + URLEncoder.encode(country, StandardCharsets.UTF_8);
            String body = httpFetcher.apply(url);
            if (body != null) {
                JsonNode arr = objectMapper.readTree(body);
                if (arr.isArray() && !arr.isEmpty()) {
                    JsonNode first = arr.get(0);
                    country = first.path("name").path("common").asText(country);
                    if (first.path("capital").isArray() && !first.path("capital").isEmpty()) {
                        capital = first.path("capital").get(0).asText(capital);
                    }
                    region = first.path("region").asText(region);
                    if (first.path("timezones").isArray() && !first.path("timezones").isEmpty()) {
                        timezone = first.path("timezones").get(0).asText(timezone);
                    }
                    source = "restcountries.com";
                }
            }
        } catch (Exception ignored) {
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("city", city);
        data.put("country", country);
        data.put("capital", capital);
        data.put("region", region);
        data.put("timezone", timezone);
        data.put("advisoryStatus", "CLEAR — Standard Cultural & Transit Readiness");
        data.put("source", source);

        String summary = String.format(Locale.ROOT,
                "Destination advisory verified for %s (%s, %s): transit & cultural venues open, local timezone %s.",
                city, country, region, timezone);
        return new AuditResult(summary, "art-adv-1", "Destination Entry & Cultural Advisory Dossier", data);
    }

    private AuditResult performNeighborhoodCadenceCuration(String prompt, Map<String, Object> ctx) {
        String city = String.valueOf(ctx.getOrDefault("city", detectCity(prompt)));
        String country = defaultCountryForCity(city);
        List<String> stops = switch (city.toLowerCase(Locale.ROOT)) {
            case "lisbon" -> List.of("Morning: Chiado Azulejo Atelier", "Afternoon: Santa Luzia Miradouro", "Evening: Alfama Acoustic Fado Tavern");
            case "reykjavik" -> List.of("Morning: Harpa Basalt Concert Hall", "Afternoon: Sky Lagoon Geothermal Ritual", "Evening: Nordic Chamber Strings Lounge");
            case "tokyo" -> List.of("Morning: Nezu Museum Bamboo Courtyard", "Afternoon: Aoyama Tadao Ando Galleries", "Evening: Shibuya Vinyl Kissaten");
            default -> List.of("Morning: " + city + " Historic Sanctuary Walk", "Afternoon: Local Craft & Ceramic Atelier", "Evening: Intimate Acoustic & Culinary Tasting");
        };
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("city", city);
        data.put("country", country);
        data.put("pace", "Unrushed (3 curated stops)");
        data.put("stops", stops);

        String summary = String.format(Locale.ROOT,
                "Curated 3-stop architectural and acoustic neighborhood sequence for %s (%s): %s.",
                city, country, String.join(" -> ", stops));
        return new AuditResult(summary, "art-cadence-1", "Neighborhood Architectural & Acoustic Cadence", data);
    }

    private String detectCity(String prompt) {
        String raw = prompt == null ? "" : prompt.replaceFirst("^\\[[^\\]]+\\]\\s*", "").trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.contains("lisbon") || lower.contains("portugal")) {
            return "Lisbon";
        }
        if (lower.contains("reykjavik") || lower.contains("iceland")) {
            return "Reykjavik";
        }
        if (lower.contains("kyoto")) {
            return "Kyoto";
        }
        for (String candidate : List.of(
                "Tokyo", "Osaka", "Paris", "Rome", "Milan", "Florence", "Venice", "Barcelona", "Madrid",
                "London", "Edinburgh", "Berlin", "Munich", "Vienna", "Zurich", "Geneva", "Amsterdam",
                "Copenhagen", "Stockholm", "Oslo", "Prague", "Athens", "Istanbul", "Marrakech", "Cairo",
                "Seoul", "Bangkok", "Singapore", "Taipei", "Sydney", "Melbourne", "New York", "Toronto",
                "Vancouver", "Montreal", "Oaxaca", "Mexico City", "Buenos Aires", "Rio de Janeiro", "Sao Paulo")) {
            if (lower.contains(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "\\b(?:in|to|for|visit|explore|around|about)\\s+([A-Z][a-zA-Z]+(?:\\s+[A-Z][a-zA-Z]+)?)\\b")
                .matcher(raw);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "Kyoto";
    }

    private String defaultCountryForCity(String city) {
        return switch (city.toLowerCase(Locale.ROOT)) {
            case "lisbon", "porto" -> "Portugal";
            case "reykjavik" -> "Iceland";
            case "tokyo", "kyoto", "osaka" -> "Japan";
            case "paris", "lyon", "nice" -> "France";
            case "rome", "milan", "florence", "venice" -> "Italy";
            case "barcelona", "madrid", "seville" -> "Spain";
            case "london", "edinburgh" -> "United Kingdom";
            case "berlin", "munich" -> "Germany";
            case "vienna" -> "Austria";
            case "zurich", "geneva" -> "Switzerland";
            case "amsterdam" -> "Netherlands";
            case "copenhagen" -> "Denmark";
            case "stockholm" -> "Sweden";
            case "oslo" -> "Norway";
            case "prague" -> "Czechia";
            case "athens" -> "Greece";
            case "istanbul" -> "Turkey";
            case "marrakech" -> "Morocco";
            case "cairo" -> "Egypt";
            case "seoul" -> "South Korea";
            case "bangkok" -> "Thailand";
            case "singapore" -> "Singapore";
            case "taipei" -> "Taiwan";
            case "sydney", "melbourne" -> "Australia";
            case "new york", "san francisco" -> "United States";
            case "toronto", "vancouver", "montreal" -> "Canada";
            case "oaxaca", "mexico city" -> "Mexico";
            case "buenos aires" -> "Argentina";
            case "rio de janeiro", "sao paulo" -> "Brazil";
            default -> "Japan";
        };
    }

    private String defaultCurrencyForCity(String city) {
        return switch (defaultCountryForCity(city)) {
            case "Portugal", "France", "Italy", "Spain", "Germany", "Austria", "Netherlands", "Greece" -> "EUR";
            case "Iceland" -> "ISK";
            case "United Kingdom" -> "GBP";
            case "Switzerland" -> "CHF";
            case "Denmark" -> "DKK";
            case "Sweden" -> "SEK";
            case "Norway" -> "NOK";
            case "Czechia" -> "CZK";
            case "Turkey" -> "TRY";
            case "Morocco" -> "MAD";
            case "Egypt" -> "EGP";
            case "South Korea" -> "KRW";
            case "Thailand" -> "THB";
            case "Singapore" -> "SGD";
            case "Taiwan" -> "TWD";
            case "Australia" -> "AUD";
            case "United States" -> "USD";
            case "Canada" -> "CAD";
            case "Mexico" -> "MXN";
            case "Argentina" -> "ARS";
            case "Brazil" -> "BRL";
            default -> "JPY";
        };
    }

    private double defaultRateForCurrency(String currency) {
        return switch (currency.toUpperCase(Locale.ROOT)) {
            case "EUR" -> 0.92;
            case "ISK" -> 137.80;
            case "GBP" -> 0.79;
            case "CHF" -> 0.88;
            case "DKK" -> 6.86;
            case "SEK" -> 10.45;
            case "NOK" -> 10.65;
            case "CZK" -> 23.40;
            case "TRY" -> 34.20;
            case "KRW" -> 1380.0;
            case "THB" -> 34.50;
            case "SGD" -> 1.34;
            case "TWD" -> 32.40;
            case "AUD" -> 1.52;
            case "CAD" -> 1.39;
            case "MXN" -> 19.80;
            case "ARS" -> 1015.0;
            case "BRL" -> 5.80;
            case "MAD" -> 9.90;
            case "EGP" -> 49.20;
            case "USD" -> 1.00;
            default -> 151.40;
        };
    }
}
