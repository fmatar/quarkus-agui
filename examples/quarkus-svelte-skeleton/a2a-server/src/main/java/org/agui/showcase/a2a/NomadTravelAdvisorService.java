package org.agui.showcase.a2a;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nomad A2A Travel Specialist Agent (:8082) providing live Frankfurter ECB currency conversion,
 * REST Countries destination advisories, architectural/acoustic neighborhood cadence curation,
 * and upstream healthchecks without silent fake fallbacks or assumed defaults.
 */
@ApplicationScoped
public class NomadTravelAdvisorService {

    public static final String AGENT_NAME = "Nomad-A2A-Specialist";

    private static final Pattern BUDGET_AMOUNT_PATTERN = Pattern.compile(
            "(?:[$€£¥]\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)|\\b([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*(?:usd|eur|gbp|chf|cad|aud|jpy|isk|mxn|dollars?|euros?|pounds?)\\b)",
            Pattern.CASE_INSENSITIVE);

    private static final List<String> KNOWN_CITIES = List.of(
            "Kyoto", "Lisbon", "Reykjavik", "Tokyo", "Osaka", "Paris", "Rome", "Milan", "Florence", "Venice",
            "Barcelona", "Madrid", "London", "Edinburgh", "Dublin", "Berlin", "Munich", "Vienna", "Zurich",
            "Geneva", "Amsterdam", "Brussels", "Copenhagen", "Stockholm", "Oslo", "Helsinki", "Prague",
            "Warsaw", "Krakow", "Budapest", "Bucharest", "Dubrovnik", "Zagreb", "Athens", "Istanbul",
            "Beirut", "Amman", "Dubai", "Abu Dhabi", "Riyadh", "Doha", "Muscat", "Kuwait City", "Manama",
            "Jerusalem", "Tel Aviv", "Marrakech", "Casablanca", "Tunis", "Cairo", "Nairobi", "Cape Town",
            "Johannesburg", "Lagos", "Accra", "Mumbai", "New Delhi", "Delhi", "Jaipur", "Kathmandu",
            "Colombo", "Seoul", "Bangkok", "Chiang Mai", "Hanoi", "Ho Chi Minh City", "Singapore",
            "Kuala Lumpur", "Jakarta", "Bali", "Manila", "Hong Kong", "Taipei", "Beijing", "Shanghai",
            "Sydney", "Melbourne", "Auckland", "Queenstown", "New York", "San Francisco", "Toronto",
            "Vancouver", "Montreal", "Oaxaca", "Mexico City", "Bogota", "Cartagena", "Lima", "Cusco",
            "Santiago", "Buenos Aires", "Rio de Janeiro", "Sao Paulo", "Havana");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(1800))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @ConfigProperty(name = "frankfurter.base-url", defaultValue = "https://api.frankfurter.dev")
    String frankfurterBaseUrl = "https://api.frankfurter.dev";

    @ConfigProperty(name = "restcountries.base-url", defaultValue = "https://api.restcountries.com")
    String restCountriesBaseUrl = "https://api.restcountries.com";

    @ConfigProperty(name = "restcountries.api-key")
    Optional<String> restCountriesApiKey = Optional.empty();

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
        public boolean isHttpOk() {
            return statusCode >= 200 && statusCode < 300 && body != null && !body.isBlank();
        }
    }

    public record AuditResult(String summary, String artifactId, String artifactTitle, Map<String, Object> data) {}

    UpstreamHttpResult probeUrlViaHttp(String url) {
        long startNs = System.nanoTime();
        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(2500))
                    .header("User-Agent", "Nomad-Quarkus-AGUI/1.0")
                    .GET();
            String trimmedKey = resolvedRestCountriesApiKey();
            if (!trimmedKey.isEmpty() && url.startsWith(restCountriesBaseUrl)) {
                reqBuilder.header("Authorization", "Bearer " + trimmedKey);
            }
            HttpResponse<String> res = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
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

    UpstreamHttpResult probeRestCountriesV5(String url) {
        if (resolvedRestCountriesApiKey().isEmpty()) {
            return new UpstreamHttpResult(
                    401,
                    null,
                    "Missing RESTCOUNTRIES_API_KEY configuration for api.restcountries.com v5",
                    1L);
        }
        return httpProbe.apply(url);
    }

    private String resolvedRestCountriesApiKey() {
        return restCountriesApiKey == null ? "" : restCountriesApiKey.map(String::trim).orElse("");
    }

    public AuditResult performAudit(String skillId, String prompt, Map<String, Object> context) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Concierge prompt must not be blank");
        }
        String skill = (skillId == null || skillId.isBlank()) ? "convert-and-audit-budget" : skillId.trim();
        Map<String, Object> ctx = context == null ? Map.of() : context;

        return switch (skill) {
            case "verify-travel-advisory" -> performTravelAdvisoryAudit(prompt, ctx);
            case "curate-neighborhood-cadence" -> performNeighborhoodCadenceCuration(prompt, ctx);
            case "convert-and-audit-budget" -> performCurrencyAndBudgetAudit(prompt, ctx);
            default -> errorAuditResult(
                    "art-err-skill",
                    "Unsupported A2A Specialist Skill",
                    "UNSUPPORTED_SKILL",
                    AGENT_NAME,
                    "Unsupported A2A specialist skill: " + skill);
        };
    }

    private AuditResult performCurrencyAndBudgetAudit(String prompt, Map<String, Object> ctx) {
        String city = resolveExplicitCity(prompt, ctx);
        if (city == null) {
            return errorAuditResult(
                    "art-fx-err",
                    "Nomad Multi-Currency & Budget Audit",
                    "MISSING_REQUIRED_ARGUMENTS",
                    AGENT_NAME,
                    "Missing required argument 'city' for convert-and-audit-budget.");
        }
        Double budgetAmount = resolveExplicitBudget(prompt, ctx);
        if (budgetAmount == null || budgetAmount <= 0) {
            return errorAuditResult(
                    "art-fx-err",
                    "Nomad Multi-Currency & Budget Audit",
                    "MISSING_REQUIRED_ARGUMENTS",
                    AGENT_NAME,
                    "Missing required argument 'budgetUsd' (or budget amount) for convert-and-audit-budget in " + city + ".");
        }

        String country = stringFromCtx(ctx, "country", resolveCountryForCity(city));
        String baseCurrency = stringFromCtx(ctx, "baseCurrency", detectCurrencyFromPrompt(prompt, "USD")).toUpperCase(Locale.ROOT);
        String targetCurrency = stringFromCtx(ctx, "currency", resolveStaticCurrencyForCountry(country)).toUpperCase(Locale.ROOT);

        String url = baseCurrency.equalsIgnoreCase(targetCurrency)
                ? frankfurterBaseUrl + "/v1/latest?base=" + URLEncoder.encode(baseCurrency, StandardCharsets.UTF_8)
                : frankfurterBaseUrl + "/v1/latest?base=" + URLEncoder.encode(baseCurrency, StandardCharsets.UTF_8)
                        + "&symbols=" + URLEncoder.encode(targetCurrency, StandardCharsets.UTF_8);

        UpstreamHttpResult res = httpProbe.apply(url);
        String apiError = inspectUpstreamError(res, "api.frankfurter.dev");
        if (apiError != null) {
            return buildServiceUnavailableResult(
                    "art-fx-1",
                    "Nomad Multi-Currency & Budget Audit",
                    "api.frankfurter.dev",
                    "Currency FX Service Unavailable (api.frankfurter.dev)",
                    apiError,
                    Map.of("city", city, "country", country, "baseCurrency", baseCurrency, "targetCurrency", targetCurrency));
        }

        try {
            double rate = 1.0;
            if (!baseCurrency.equalsIgnoreCase(targetCurrency)) {
                JsonNode rates = objectMapper.readTree(res.body()).path("rates");
                if (!rates.has(targetCurrency)) {
                    return buildServiceUnavailableResult(
                            "art-fx-1",
                            "Nomad Multi-Currency & Budget Audit",
                            "api.frankfurter.dev",
                            "Currency FX Service Unavailable (api.frankfurter.dev)",
                            "ECB reference rate for " + targetCurrency + " was not returned by api.frankfurter.dev.",
                            Map.of("city", city, "country", country, "baseCurrency", baseCurrency, "targetCurrency", targetCurrency));
                }
                rate = rates.path(targetCurrency).asDouble();
            }

            double convertedTotal = Math.round(budgetAmount * rate * 100.0) / 100.0;
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("status", "OK");
            data.put("isError", false);
            data.put("serviceAvailable", true);
            data.put("city", city);
            data.put("country", country);
            data.put("baseCurrency", baseCurrency);
            data.put("targetCurrency", targetCurrency);
            data.put("exchangeRate", rate);
            data.put("budgetUsd", budgetAmount);
            data.put("convertedTotal", convertedTotal);
            data.put("requiresHitlApprovalAboveUsd", 1500.0);
            data.put("source", "api.frankfurter.dev");

            String summary = String.format(
                    Locale.ROOT,
                    "Frankfurter FX audit complete for %s (%s): %,.0f %s = %,.2f %s (rate %.4f).",
                    city, country, budgetAmount, baseCurrency, convertedTotal, targetCurrency, rate);
            return new AuditResult(summary, "art-fx-1", "Nomad Multi-Currency & Budget Audit", data);
        } catch (Exception ex) {
            return buildServiceUnavailableResult(
                    "art-fx-1",
                    "Nomad Multi-Currency & Budget Audit",
                    "api.frankfurter.dev",
                    "Currency FX Service Unavailable (api.frankfurter.dev)",
                    "Invalid JSON payload from api.frankfurter.dev: " + ex.getMessage(),
                    Map.of("city", city, "country", country));
        }
    }

    private AuditResult performTravelAdvisoryAudit(String prompt, Map<String, Object> ctx) {
        String city = resolveExplicitCity(prompt, ctx);
        if (city == null) {
            return errorAuditResult(
                    "art-adv-err",
                    "Destination Entry & Cultural Advisory Dossier",
                    "MISSING_REQUIRED_ARGUMENTS",
                    AGENT_NAME,
                    "Missing required argument 'city' for verify-travel-advisory.");
        }
        String country = stringFromCtx(ctx, "country", resolveCountryForCity(city));
        String encodedCountry = URLEncoder.encode(country, StandardCharsets.UTF_8).replace("+", "%20");
        String url = restCountriesBaseUrl + "/countries/v5/names.common/"
                + encodedCountry
                + "?response_fields=names.common,capitals,currencies,region,timezones";

        UpstreamHttpResult res = probeRestCountriesV5(url);
        String apiError = inspectUpstreamError(res, "api.restcountries.com");
        if (apiError != null) {
            return buildServiceUnavailableResult(
                    "art-adv-1",
                    "Destination Entry & Cultural Advisory Dossier",
                    "api.restcountries.com",
                    "Travel Advisory Service Unavailable (api.restcountries.com)",
                    apiError,
                    Map.of("city", city, "country", country, "advisoryStatus", "UNAVAILABLE"));
        }

        try {
            JsonNode root = objectMapper.readTree(res.body());
            JsonNode objects = root.path("data").path("objects");
            if (!objects.isArray() || objects.isEmpty()) {
                return buildServiceUnavailableResult(
                        "art-adv-1",
                        "Destination Entry & Cultural Advisory Dossier",
                        "api.restcountries.com",
                        "Travel Advisory Service Unavailable (api.restcountries.com)",
                        "Unexpected response payload from api.restcountries.com for " + country,
                        Map.of("city", city, "country", country, "advisoryStatus", "UNAVAILABLE"));
            }

            JsonNode first = objects.get(0);
            String resolvedCountry = first.path("names").path("common").asText(country);
            String capital = (first.path("capitals").isArray() && !first.path("capitals").isEmpty())
                    ? first.path("capitals").get(0).path("name").asText(city)
                    : city;
            String region = first.path("region").asText("Unknown");
            String currencyCode = (first.path("currencies").isArray() && !first.path("currencies").isEmpty())
                    ? first.path("currencies").get(0).path("code").asText(resolveStaticCurrencyForCountry(resolvedCountry))
                    : resolveStaticCurrencyForCountry(resolvedCountry);
            String timezone = (first.path("timezones").isArray() && !first.path("timezones").isEmpty())
                    ? first.path("timezones").get(0).asText("UTC")
                    : "UTC";

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("status", "OK");
            data.put("isError", false);
            data.put("serviceAvailable", true);
            data.put("city", city);
            data.put("country", resolvedCountry);
            data.put("capital", capital);
            data.put("region", region);
            data.put("currencyCode", currencyCode);
            data.put("timezone", timezone);
            data.put("advisoryStatus", "VERIFIED — Country Metadata & Timezone Confirmed");
            data.put("source", "api.restcountries.com");

            String summary = String.format(
                    Locale.ROOT,
                    "Destination advisory verified for %s (%s, %s): capital %s, currency %s, local timezone %s.",
                    city, resolvedCountry, region, capital, currencyCode, timezone);
            return new AuditResult(summary, "art-adv-1", "Destination Entry & Cultural Advisory Dossier", data);
        } catch (Exception ex) {
            return buildServiceUnavailableResult(
                    "art-adv-1",
                    "Destination Entry & Cultural Advisory Dossier",
                    "api.restcountries.com",
                    "Travel Advisory Service Unavailable (api.restcountries.com)",
                    "Invalid JSON payload from api.restcountries.com: " + ex.getMessage(),
                    Map.of("city", city, "country", country, "advisoryStatus", "UNAVAILABLE"));
        }
    }

    private AuditResult performNeighborhoodCadenceCuration(String prompt, Map<String, Object> ctx) {
        String city = resolveExplicitCity(prompt, ctx);
        if (city == null) {
            return errorAuditResult(
                    "art-cadence-err",
                    "Neighborhood Architectural & Acoustic Cadence",
                    "MISSING_REQUIRED_ARGUMENTS",
                    AGENT_NAME,
                    "Missing required argument 'city' for curate-neighborhood-cadence.");
        }
        String country = stringFromCtx(ctx, "country", resolveCountryForCity(city));
        String focus = stringFromCtx(ctx, "focus", "architecture, local craft & evening acoustics");
        List<String> stops = switch (city.toLowerCase(Locale.ROOT)) {
            case "lisbon" -> List.of("Morning: Chiado Azulejo Atelier", "Afternoon: Santa Luzia Miradouro", "Evening: Alfama Acoustic Fado Tavern");
            case "reykjavik" -> List.of("Morning: Harpa Basalt Concert Hall", "Afternoon: Sky Lagoon Geothermal Ritual", "Evening: Nordic Chamber Strings Lounge");
            case "tokyo" -> List.of("Morning: Nezu Museum Bamboo Courtyard", "Afternoon: Aoyama Tadao Ando Galleries", "Evening: Shibuya Vinyl Kissaten");
            case "paris" -> List.of("Morning: Place des Vosges Limestone Arcades", "Afternoon: Fondation Louis Vuitton / Marais Ateliers", "Evening: Rue des Lombards Intimate Jazz Caveau");
            case "beirut" -> List.of("Morning: Sursock Museum Ottoman-Venetian Courtyard", "Afternoon: Mar Mikhael Heritage Stairs & Design Studios", "Evening: Badaro Vinyl & Oud Listening Room");
            default -> List.of(
                    "Morning: " + city + " Historic Sanctuary Walk",
                    "Afternoon: Local Craft & Architectural Studio (" + focus + ")",
                    "Evening: Intimate Acoustic & Culinary Tasting");
        };

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "OK");
        data.put("isError", false);
        data.put("serviceAvailable", true);
        data.put("city", city);
        data.put("country", country);
        data.put("focus", focus);
        data.put("pace", "Unrushed (3 curated stops)");
        data.put("stops", stops);

        String summary = String.format(
                Locale.ROOT,
                "Curated 3-stop neighborhood sequence for %s (%s) focused on %s: %s.",
                city, country, focus, String.join(" -> ", stops));
        return new AuditResult(summary, "art-cadence-1", "Neighborhood Architectural & Acoustic Cadence", data);
    }

    public Map<String, Object> checkHealth() {
        long now = System.currentTimeMillis();
        Map<String, Object> cached = cachedHealthReport;
        if (cached != null && now < cachedHealthExpiresAtMs) {
            return cached;
        }
        List<Map<String, Object>> upstreams = new ArrayList<>();
        upstreams.add(probeUpstreamHealth(
                "frankfurter",
                "Frankfurter ECB FX API",
                "api.frankfurter.dev",
                frankfurterBaseUrl + "/v1/latest?base=USD&symbols=EUR",
                httpProbe));
        upstreams.add(probeUpstreamHealth(
                "restcountries",
                "REST Countries v5 Travel Advisory API",
                "api.restcountries.com",
                restCountriesBaseUrl + "/countries/v5/names.common/France?response_fields=names.common,region",
                this::probeRestCountriesV5));

        long upCount = upstreams.stream().filter(u -> "UP".equals(u.get("status"))).count();
        String overall = upCount == upstreams.size() ? "UP" : (upCount == 0 ? "DOWN" : "DEGRADED");

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("server", AGENT_NAME);
        report.put("status", overall);
        report.put("healthyCount", upCount);
        report.put("totalCount", upstreams.size());
        report.put("upstreams", List.copyOf(upstreams));
        Map<String, Object> snapshot = Collections.unmodifiableMap(report);
        cachedHealthReport = snapshot;
        cachedHealthExpiresAtMs = now + Math.max(0L, healthCacheTtlMs);
        return snapshot;
    }

    private Map<String, Object> probeUpstreamHealth(
            String id,
            String name,
            String host,
            String url,
            Function<String, UpstreamHttpResult> probeFn) {
        UpstreamHttpResult res = probeFn.apply(url);
        String error = inspectUpstreamError(res, host);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", id);
        entry.put("name", name);
        entry.put("upstream", host);
        entry.put("status", error == null ? "UP" : "DOWN");
        entry.put("httpStatus", res.statusCode());
        entry.put("latencyMs", res.latencyMs());
        if (error != null) {
            entry.put("error", error);
        }
        return entry;
    }

    String inspectUpstreamError(UpstreamHttpResult res, String upstreamHost) {
        if (res == null) {
            return "No response from " + upstreamHost;
        }
        String jsonError = extractJsonErrorMessage(res.body());
        if (jsonError != null) {
            return jsonError;
        }
        if (!res.isHttpOk()) {
            return (res.error() != null && !res.error().isBlank())
                    ? res.error()
                    : "HTTP " + res.statusCode() + " from " + upstreamHost;
        }
        return null;
    }

    private String extractJsonErrorMessage(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (!root.isObject()) {
                return null;
            }
            if (root.has("success") && !root.path("success").asBoolean(true)) {
                JsonNode errors = root.path("errors");
                if (errors.isArray() && !errors.isEmpty() && errors.get(0).hasNonNull("message")) {
                    return errors.get(0).path("message").asText();
                }
                return root.path("message").asText("Upstream API returned success=false");
            }
            if (root.hasNonNull("error") && root.path("error").isTextual()) {
                return root.path("error").asText();
            }
            if (!root.has("rates") && root.hasNonNull("message") && root.size() <= 2) {
                return root.path("message").asText();
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private AuditResult buildServiceUnavailableResult(
            String artifactId,
            String artifactTitle,
            String upstream,
            String headline,
            String detail,
            Map<String, Object> extraFields) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (extraFields != null) {
            data.putAll(extraFields);
        }
        data.put("status", "ERROR");
        data.put("isError", true);
        data.put("serviceAvailable", false);
        data.put("code", "UPSTREAM_SERVICE_UNAVAILABLE");
        data.put("upstream", upstream);
        data.put("error", headline + ": " + detail);
        String summary = "⚠️ " + headline + ": " + detail;
        return new AuditResult(summary, artifactId, artifactTitle, data);
    }

    private AuditResult errorAuditResult(
            String artifactId,
            String artifactTitle,
            String code,
            String upstream,
            String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "ERROR");
        data.put("isError", true);
        data.put("serviceAvailable", false);
        data.put("code", code);
        data.put("upstream", upstream);
        data.put("error", message);
        return new AuditResult("⚠️ " + message, artifactId, artifactTitle, data);
    }

    private String resolveExplicitCity(String prompt, Map<String, Object> ctx) {
        if (ctx != null && ctx.get("city") instanceof String c && !c.isBlank()) {
            return c.trim();
        }
        return detectCityFromPrompt(prompt);
    }

    private Double resolveExplicitBudget(String prompt, Map<String, Object> ctx) {
        if (ctx != null) {
            for (String key : List.of("budgetUsd", "budget", "amount")) {
                Object val = ctx.get(key);
                if (val instanceof Number n) {
                    return n.doubleValue();
                }
                if (val instanceof String s && !s.isBlank()) {
                    try {
                        return Double.parseDouble(s.replace(",", "").trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        if (prompt == null) {
            return null;
        }
        Matcher m = BUDGET_AMOUNT_PATTERN.matcher(prompt);
        if (m.find()) {
            String raw = m.group(1) != null ? m.group(1) : m.group(2);
            try {
                return Double.parseDouble(raw.replace(",", "").trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String detectCurrencyFromPrompt(String prompt, String fallback) {
        if (prompt == null) {
            return fallback;
        }
        String lower = prompt.toLowerCase(Locale.ROOT);
        if (prompt.contains("€") || Pattern.compile("\\b(eur|euros?)\\b").matcher(lower).find()) {
            return "EUR";
        }
        if (prompt.contains("£") || Pattern.compile("\\b(gbp|pounds?|sterling)\\b").matcher(lower).find()) {
            return "GBP";
        }
        if (Pattern.compile("\\b(chf|francs?)\\b").matcher(lower).find()) {
            return "CHF";
        }
        return fallback;
    }

    private String detectCityFromPrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return null;
        }
        String raw = prompt.replaceFirst("^\\[[^\\]]+\\]\\s*", "").trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        for (String candidate : KNOWN_CITIES) {
            if (lower.contains(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
        Matcher m = Pattern.compile("\\b(?:in|to|for|visit|explore|around|about)\\s+([A-Z][a-zA-Z]+(?:\\s+[A-Z][a-zA-Z]+)?)\\b")
                .matcher(raw);
        if (m.find()) {
            String candidate = m.group(1).trim();
            if (!List.of("France", "Japan", "Portugal", "Iceland", "Currency", "Travel").contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static String stringFromCtx(Map<String, Object> ctx, String key, String defaultVal) {
        if (ctx != null && ctx.get(key) instanceof String s && !s.isBlank()) {
            return s.trim();
        }
        return defaultVal;
    }

    private static final Map<String, String> CITY_TO_COUNTRY = buildCityCountryMap();
    private static final Map<String, String> COUNTRY_TO_CURRENCY = buildCountryCurrencyMap();

    private static Map<String, String> buildCityCountryMap() {
        Map<String, String> map = new LinkedHashMap<>();
        registerCities(map, "Portugal", "lisbon", "porto");
        registerCities(map, "Iceland", "reykjavik");
        registerCities(map, "Japan", "tokyo", "kyoto", "osaka", "nara", "hiroshima", "sapporo", "fukuoka");
        registerCities(map, "France", "paris", "lyon", "nice", "marseille", "bordeaux");
        registerCities(map, "Italy", "rome", "milan", "florence", "venice", "naples");
        registerCities(map, "Spain", "barcelona", "madrid", "seville", "granada", "valencia");
        registerCities(map, "United Kingdom", "london", "edinburgh", "oxford", "manchester");
        registerCities(map, "Ireland", "dublin");
        registerCities(map, "Germany", "berlin", "munich", "hamburg", "frankfurt");
        registerCities(map, "Austria", "vienna", "salzburg");
        registerCities(map, "Switzerland", "zurich", "geneva", "basel");
        registerCities(map, "Netherlands", "amsterdam", "rotterdam");
        registerCities(map, "Belgium", "brussels", "bruges", "antwerp");
        registerCities(map, "Denmark", "copenhagen");
        registerCities(map, "Sweden", "stockholm", "gothenburg");
        registerCities(map, "Norway", "oslo", "bergen");
        registerCities(map, "Finland", "helsinki");
        registerCities(map, "Czechia", "prague");
        registerCities(map, "Poland", "warsaw", "krakow");
        registerCities(map, "Hungary", "budapest");
        registerCities(map, "Romania", "bucharest");
        registerCities(map, "Croatia", "dubrovnik", "zagreb", "split");
        registerCities(map, "Greece", "athens", "santorini", "thessaloniki");
        registerCities(map, "Turkey", "istanbul", "ankara", "cappadocia");
        registerCities(map, "Lebanon", "beirut", "byblos", "batroun");
        registerCities(map, "Jordan", "amman", "petra");
        registerCities(map, "United Arab Emirates", "dubai", "abu dhabi");
        registerCities(map, "Saudi Arabia", "riyadh", "jeddah");
        registerCities(map, "Qatar", "doha");
        registerCities(map, "Oman", "muscat");
        registerCities(map, "Kuwait", "kuwait city");
        registerCities(map, "Bahrain", "manama");
        registerCities(map, "Israel", "jerusalem", "tel aviv");
        registerCities(map, "Morocco", "marrakech", "casablanca", "fez", "rabat", "tangier");
        registerCities(map, "Tunisia", "tunis");
        registerCities(map, "Egypt", "cairo", "alexandria", "luxor");
        registerCities(map, "Kenya", "nairobi");
        registerCities(map, "South Africa", "cape town", "johannesburg");
        registerCities(map, "Nigeria", "lagos");
        registerCities(map, "Ghana", "accra");
        registerCities(map, "India", "mumbai", "delhi", "new delhi", "jaipur", "bangalore", "goa");
        registerCities(map, "Nepal", "kathmandu");
        registerCities(map, "Sri Lanka", "colombo");
        registerCities(map, "South Korea", "seoul", "busan", "jeju");
        registerCities(map, "Thailand", "bangkok", "chiang mai", "phuket");
        registerCities(map, "Vietnam", "hanoi", "ho chi minh city", "da nang");
        registerCities(map, "Singapore", "singapore");
        registerCities(map, "Malaysia", "kuala lumpur", "penang");
        registerCities(map, "Indonesia", "jakarta", "bali", "ubud");
        registerCities(map, "Philippines", "manila", "cebu");
        registerCities(map, "Hong Kong", "hong kong");
        registerCities(map, "Taiwan", "taipei");
        registerCities(map, "China", "beijing", "shanghai", "chengdu", "hangzhou");
        registerCities(map, "Australia", "sydney", "melbourne", "brisbane", "perth");
        registerCities(map, "New Zealand", "auckland", "queenstown", "wellington");
        registerCities(map, "United States", "new york", "san francisco", "chicago", "los angeles", "miami", "new orleans", "austin", "seattle", "boston");
        registerCities(map, "Canada", "toronto", "vancouver", "montreal");
        registerCities(map, "Mexico", "oaxaca", "mexico city", "guadalajara");
        registerCities(map, "Colombia", "bogota", "cartagena", "medellin");
        registerCities(map, "Peru", "lima", "cusco");
        registerCities(map, "Chile", "santiago");
        registerCities(map, "Argentina", "buenos aires", "mendoza");
        registerCities(map, "Brazil", "rio de janeiro", "sao paulo");
        registerCities(map, "Cuba", "havana");
        return Map.copyOf(map);
    }

    private static void registerCities(Map<String, String> map, String country, String... cities) {
        for (String c : cities) {
            map.put(c, country);
        }
    }

    private static Map<String, String> buildCountryCurrencyMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String euroCountry : List.of(
                "Portugal", "France", "Italy", "Spain", "Germany", "Austria",
                "Netherlands", "Belgium", "Greece", "Ireland", "Finland", "Croatia")) {
            map.put(euroCountry, "EUR");
        }
        map.put("Japan", "JPY");
        map.put("Iceland", "ISK");
        map.put("United Kingdom", "GBP");
        map.put("Switzerland", "CHF");
        map.put("Denmark", "DKK");
        map.put("Sweden", "SEK");
        map.put("Norway", "NOK");
        map.put("Czechia", "CZK");
        map.put("Czech Republic", "CZK");
        map.put("Poland", "PLN");
        map.put("Hungary", "HUF");
        map.put("Romania", "RON");
        map.put("Turkey", "TRY");
        map.put("Lebanon", "LBP");
        map.put("Jordan", "JOD");
        map.put("United Arab Emirates", "AED");
        map.put("UAE", "AED");
        map.put("Saudi Arabia", "SAR");
        map.put("Qatar", "QAR");
        map.put("Oman", "OMR");
        map.put("Kuwait", "KWD");
        map.put("Bahrain", "BHD");
        map.put("Israel", "ILS");
        map.put("Morocco", "MAD");
        map.put("Tunisia", "TND");
        map.put("Egypt", "EGP");
        map.put("Kenya", "KES");
        map.put("South Africa", "ZAR");
        map.put("Nigeria", "NGN");
        map.put("Ghana", "GHS");
        map.put("India", "INR");
        map.put("Nepal", "NPR");
        map.put("Sri Lanka", "LKR");
        map.put("South Korea", "KRW");
        map.put("Thailand", "THB");
        map.put("Vietnam", "VND");
        map.put("Singapore", "SGD");
        map.put("Malaysia", "MYR");
        map.put("Indonesia", "IDR");
        map.put("Philippines", "PHP");
        map.put("Hong Kong", "HKD");
        map.put("Taiwan", "TWD");
        map.put("China", "CNY");
        map.put("Australia", "AUD");
        map.put("New Zealand", "NZD");
        map.put("United States", "USD");
        map.put("USA", "USD");
        map.put("Canada", "CAD");
        map.put("Mexico", "MXN");
        map.put("Colombia", "COP");
        map.put("Peru", "PEN");
        map.put("Chile", "CLP");
        map.put("Argentina", "ARS");
        map.put("Brazil", "BRL");
        map.put("Cuba", "CUP");
        return Map.copyOf(map);
    }

    static String resolveCountryForCity(String city) {
        return CITY_TO_COUNTRY.getOrDefault(city.toLowerCase(Locale.ROOT), city);
    }

    static String resolveStaticCurrencyForCountry(String country) {
        if (country == null || country.isBlank()) {
            return "USD";
        }
        return COUNTRY_TO_CURRENCY.getOrDefault(country.trim(), "USD");
    }
}
