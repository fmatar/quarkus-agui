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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * A2A Specialist Gateway Client (:8080 -> :8082) for {@code Nomad-A2A-Specialist}.
 * Invokes real Frankfurter FX, REST Countries v5 Travel Advisory, and Neighborhood Cadence skills
 * on {@code :8082} and surfaces upstream errors honestly without fabricating {@code "CLEAR"} verdicts.
 */
@ApplicationScoped
public class A2aGatewayClient {

    private static final Set<String> SUPPORTED_SKILLS = Set.of(
            "convert-and-audit-budget", "verify-travel-advisory", "curate-neighborhood-cadence");

    private static final Map<String, String> DYNAMIC_CITY_COUNTRIES = new ConcurrentHashMap<>();

    private static final Map<String, String> CITY_TO_COUNTRY = Map.ofEntries(
            Map.entry("beirut", "Lebanon"), Map.entry("byblos", "Lebanon"), Map.entry("batroun", "Lebanon"),
            Map.entry("amman", "Jordan"), Map.entry("petra", "Jordan"),
            Map.entry("dubai", "United Arab Emirates"), Map.entry("abu dhabi", "United Arab Emirates"),
            Map.entry("riyadh", "Saudi Arabia"), Map.entry("doha", "Qatar"), Map.entry("muscat", "Oman"),
            Map.entry("lisbon", "Portugal"), Map.entry("porto", "Portugal"),
            Map.entry("reykjavik", "Iceland"),
            Map.entry("tokyo", "Japan"), Map.entry("kyoto", "Japan"), Map.entry("osaka", "Japan"),
            Map.entry("paris", "France"), Map.entry("lyon", "France"), Map.entry("nice", "France"),
            Map.entry("rome", "Italy"), Map.entry("milan", "Italy"), Map.entry("florence", "Italy"), Map.entry("venice", "Italy"),
            Map.entry("barcelona", "Spain"), Map.entry("madrid", "Spain"), Map.entry("seville", "Spain"),
            Map.entry("london", "United Kingdom"), Map.entry("edinburgh", "United Kingdom"),
            Map.entry("dublin", "Ireland"), Map.entry("berlin", "Germany"), Map.entry("munich", "Germany"),
            Map.entry("vienna", "Austria"), Map.entry("zurich", "Switzerland"), Map.entry("geneva", "Switzerland"),
            Map.entry("amsterdam", "Netherlands"), Map.entry("brussels", "Belgium"),
            Map.entry("copenhagen", "Denmark"), Map.entry("stockholm", "Sweden"), Map.entry("oslo", "Norway"),
            Map.entry("helsinki", "Finland"), Map.entry("prague", "Czechia"), Map.entry("budapest", "Hungary"),
            Map.entry("warsaw", "Poland"), Map.entry("dubrovnik", "Croatia"),
            Map.entry("athens", "Greece"), Map.entry("santorini", "Greece"),
            Map.entry("istanbul", "Turkey"), Map.entry("marrakech", "Morocco"), Map.entry("casablanca", "Morocco"),
            Map.entry("tunis", "Tunisia"), Map.entry("cairo", "Egypt"),
            Map.entry("nairobi", "Kenya"), Map.entry("cape town", "South Africa"),
            Map.entry("seoul", "South Korea"), Map.entry("bangkok", "Thailand"), Map.entry("singapore", "Singapore"),
            Map.entry("taipei", "Taiwan"), Map.entry("hong kong", "Hong Kong"), Map.entry("shanghai", "China"),
            Map.entry("bali", "Indonesia"), Map.entry("mumbai", "India"), Map.entry("sydney", "Australia"),
            Map.entry("auckland", "New Zealand"), Map.entry("new york", "United States"),
            Map.entry("san francisco", "United States"), Map.entry("chicago", "United States"),
            Map.entry("toronto", "Canada"), Map.entry("vancouver", "Canada"), Map.entry("montreal", "Canada"),
            Map.entry("oaxaca", "Mexico"), Map.entry("mexico city", "Mexico"),
            Map.entry("bogota", "Colombia"), Map.entry("lima", "Peru"), Map.entry("cusco", "Peru"),
            Map.entry("santiago", "Chile"), Map.entry("buenos aires", "Argentina"), Map.entry("rio de janeiro", "Brazil"));

    private static final Map<String, String> COUNTRY_TO_CURRENCY = Map.ofEntries(
            Map.entry("Lebanon", "LBP"), Map.entry("Jordan", "JOD"), Map.entry("United Arab Emirates", "AED"),
            Map.entry("Saudi Arabia", "SAR"), Map.entry("Qatar", "QAR"), Map.entry("Oman", "OMR"),
            Map.entry("Portugal", "EUR"), Map.entry("France", "EUR"), Map.entry("Italy", "EUR"),
            Map.entry("Spain", "EUR"), Map.entry("Germany", "EUR"), Map.entry("Austria", "EUR"),
            Map.entry("Netherlands", "EUR"), Map.entry("Belgium", "EUR"), Map.entry("Ireland", "EUR"),
            Map.entry("Finland", "EUR"), Map.entry("Greece", "EUR"), Map.entry("Croatia", "EUR"),
            Map.entry("Iceland", "ISK"), Map.entry("United Kingdom", "GBP"), Map.entry("Switzerland", "CHF"),
            Map.entry("Denmark", "DKK"), Map.entry("Sweden", "SEK"), Map.entry("Norway", "NOK"),
            Map.entry("Czechia", "CZK"), Map.entry("Poland", "PLN"), Map.entry("Hungary", "HUF"),
            Map.entry("Turkey", "TRY"), Map.entry("Morocco", "MAD"), Map.entry("Tunisia", "TND"),
            Map.entry("Egypt", "EGP"), Map.entry("Kenya", "KES"), Map.entry("South Africa", "ZAR"),
            Map.entry("India", "INR"), Map.entry("Japan", "JPY"), Map.entry("South Korea", "KRW"),
            Map.entry("Thailand", "THB"), Map.entry("Singapore", "SGD"), Map.entry("Indonesia", "IDR"),
            Map.entry("Taiwan", "TWD"), Map.entry("Hong Kong", "HKD"), Map.entry("China", "CNY"),
            Map.entry("Australia", "AUD"), Map.entry("New Zealand", "NZD"), Map.entry("United States", "USD"),
            Map.entry("Canada", "CAD"), Map.entry("Mexico", "MXN"), Map.entry("Colombia", "COP"),
            Map.entry("Peru", "PEN"), Map.entry("Chile", "CLP"), Map.entry("Argentina", "ARS"),
            Map.entry("Brazil", "BRL"));

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(1800))
            .build();

    @ConfigProperty(name = "agui.a2a.base-url", defaultValue = "http://127.0.0.1:8082")
    String a2aBaseUrl = "http://127.0.0.1:8082";

    static void registerCityCountry(String city, String country) {
        if (city != null && !city.isBlank() && country != null && !country.isBlank()) {
            DYNAMIC_CITY_COUNTRIES.put(city.trim().toLowerCase(Locale.ROOT), country.trim());
        }
    }

    public Map<String, Object> getAgentCard() {
        return Map.of(
                "name", "Nomad-A2A-Specialist",
                "description", "Autonomous A2A Travel Specialist Agent for Frankfurter ECB multi-currency budget auditing, REST Countries v5 destination advisories, and architectural neighborhood cadence curation.",
                "endpoint", a2aBaseUrl,
                "version", "1.5.0",
                "protocol", "A2A-1.0-JSONRPC",
                "skills", List.of(
                        Map.of("id", "convert-and-audit-budget", "name", "Frankfurter Multi-Currency & Budget Audit"),
                        Map.of("id", "verify-travel-advisory", "name", "REST Countries v5 Destination & Cultural Advisory"),
                        Map.of("id", "curate-neighborhood-cadence", "name", "Neighborhood Architectural & Acoustic Cadence Curation")));
    }

    public Map<String, Object> delegateTask(String taskId, String skillId, String prompt, Map<String, Object> context) {
        String resolvedSkill = (skillId == null || skillId.isBlank()) ? "convert-and-audit-budget" : skillId.trim();
        if (!SUPPORTED_SKILLS.contains(resolvedSkill)) {
            throw new IllegalArgumentException("Unsupported A2A skill: " + resolvedSkill);
        }
        Map<String, Object> execCtx = context == null ? Map.of() : new LinkedHashMap<>(context);

        try {
            String reqBody = objectMapper.writeValueAsString(Map.of(
                    "skillId", resolvedSkill,
                    "prompt", prompt == null ? "" : prompt,
                    "context", execCtx));
            HttpRequest req = HttpRequest.newBuilder(URI.create(a2aBaseUrl + "/api/a2a/audit"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(reqBody))
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() < 200 || res.statusCode() >= 300 || res.body() == null) {
                return buildA2aErrorEnvelope(
                        taskId,
                        resolvedSkill,
                        prompt,
                        "Nomad A2A Specialist (:8082) returned HTTP " + res.statusCode());
            }

            JsonNode root = objectMapper.readTree(res.body());
            String summary = root.path("summary").asText("");
            Map<String, Object> data = root.has("data") && root.path("data").isObject()
                    ? objectMapper.convertValue(root.path("data"), new TypeReference<Map<String, Object>>() {})
                    : Map.of();

            boolean isError = Boolean.TRUE.equals(data.get("isError"))
                    || "ERROR".equalsIgnoreCase(String.valueOf(data.get("status")));

            Map<String, Object> out = new LinkedHashMap<>(Map.ofEntries(
                    Map.entry("taskId", taskId == null ? "task-1" : taskId),
                    Map.entry("agentName", "Nomad-A2A-Specialist"),
                    Map.entry("skillId", resolvedSkill),
                    Map.entry("status", isError ? "ERROR" : "COMPLETED"),
                    Map.entry("isError", isError),
                    Map.entry("summary", summary),
                    Map.entry("prompt", prompt == null ? "" : prompt),
                    Map.entry("report", data)));
            if (isError) {
                out.put("error", data.getOrDefault("error", summary));
            }
            return out;
        } catch (Exception ex) {
            return buildA2aErrorEnvelope(
                    taskId,
                    resolvedSkill,
                    prompt,
                    "Nomad A2A Specialist (:8082) unavailable: " + ex.getMessage());
        }
    }

    private static Map<String, Object> buildA2aErrorEnvelope(
            String taskId, String skillId, String prompt, String errorMsg) {
        Map<String, Object> errReport = Map.of(
                "status", "ERROR",
                "isError", true,
                "serviceAvailable", false,
                "upstream", "Nomad-A2A-Specialist (:8082)",
                "error", errorMsg);
        return Map.ofEntries(
                Map.entry("taskId", taskId == null ? "task-err" : taskId),
                Map.entry("agentName", "Nomad-A2A-Specialist"),
                Map.entry("skillId", skillId),
                Map.entry("status", "ERROR"),
                Map.entry("isError", true),
                Map.entry("error", errorMsg),
                Map.entry("summary", "⚠️ " + errorMsg),
                Map.entry("prompt", prompt == null ? "" : prompt),
                Map.entry("report", errReport));
    }

    static String resolveCountryForCity(String city) {
        if (city == null || city.isBlank()) {
            return "";
        }
        String key = city.trim().toLowerCase(Locale.ROOT);
        String dynamic = DYNAMIC_CITY_COUNTRIES.get(key);
        if (dynamic != null && !dynamic.isBlank()) {
            return dynamic;
        }
        return CITY_TO_COUNTRY.getOrDefault(key, city.trim());
    }

    static String resolveCurrencyForCountry(String country) {
        if (country == null || country.isBlank()) {
            return "USD";
        }
        return COUNTRY_TO_CURRENCY.getOrDefault(country.trim(), "USD");
    }
}
