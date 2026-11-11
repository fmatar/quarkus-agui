package org.agui.showcase.backend;

import io.vertx.core.Vertx;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.a2aproject.sdk.client.http.vertx.VertxA2AHttpClient;
import org.a2aproject.sdk.spec.AgentCard;
import org.agui.a2a.adapters.AgUiA2aClient;
import org.agui.a2a.domain.A2aDelegationResult;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Official A2A 1.5.0.Final Specialist Gateway Client (:8080 -> :8082) for {@code Nomad-A2A-Specialist}.
 * Discovers {@code /.well-known/agent-card.json} via {@code A2ACardResolver} and delegates
 * Frankfurter FX, REST Countries v5 Travel Advisory, and Neighborhood Cadence tasks over
 * official A2A JSON-RPC 2.0 via {@link AgUiA2aClient} + {@link VertxA2AHttpClient}.
 */
@ApplicationScoped
public class A2aGatewayClient {

    private static final Set<String> SUPPORTED_SKILLS = Set.of(
            "convert-and-audit-budget", "verify-travel-advisory", "curate-neighborhood-cadence");

    private static final List<Map<String, String>> FALLBACK_SKILLS = List.of(
            Map.of("id", "convert-and-audit-budget", "name", "Frankfurter Multi-Currency & Budget Audit"),
            Map.of("id", "verify-travel-advisory", "name", "REST Countries v5 Destination & Cultural Advisory"),
            Map.of("id", "curate-neighborhood-cadence", "name", "Neighborhood Architectural & Acoustic Cadence Curation"));

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
            Map.entry("bordeaux", "France"), Map.entry("marseille", "France"),
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

    @ConfigProperty(name = "agui.a2a.base-url", defaultValue = "http://127.0.0.1:8082")
    String a2aBaseUrl = "http://127.0.0.1:8082";

    @Inject
    Instance<Vertx> vertxInstance;

    private final Object clientLock = new Object();
    private String activeBaseUrl;
    private VertxA2AHttpClient sharedVertxHttpClient;
    private AgUiA2aClient activeClient;

    static void registerCityCountry(String city, String country) {
        if (city != null && !city.isBlank() && country != null && !country.isBlank()) {
            DYNAMIC_CITY_COUNTRIES.put(city.trim().toLowerCase(Locale.ROOT), country.trim());
        }
    }

    public Map<String, Object> getAgentCard() {
        try {
            AgentCard card = client().resolveAgentCard();
            List<Map<String, String>> skills = card.skills() == null
                    ? List.of()
                    : card.skills().stream()
                            .map(s -> Map.of(
                                    "id", s.id(),
                                    "name", s.name(),
                                    "description", s.description() == null ? "" : s.description()))
                            .toList();
            return Map.of(
                    "name", card.name(),
                    "description", card.description() == null ? "" : card.description(),
                    "endpoint", a2aBaseUrl,
                    "version", card.version(),
                    "protocol", card.preferredTransport() != null ? card.preferredTransport() : "JSONRPC",
                    "streaming", card.capabilities() != null && Boolean.TRUE.equals(card.capabilities().streaming()),
                    "status", "UP",
                    "skills", skills);
        } catch (RuntimeException ex) {
            return Map.of(
                    "name", "Nomad-A2A-Specialist",
                    "description", "Specialist A2A Travel Advisor Agent (card resolution degraded: " + ex.getMessage() + ")",
                    "endpoint", a2aBaseUrl,
                    "version", "1.5.0",
                    "protocol", "JSONRPC",
                    "streaming", false,
                    "status", "UNAVAILABLE",
                    "skills", FALLBACK_SKILLS);
        }
    }

    public Map<String, Object> delegateTask(String taskId, String skillId, String prompt, Map<String, Object> context) {
        String resolvedSkill = (skillId == null || skillId.isBlank()) ? "convert-and-audit-budget" : skillId.trim();
        if (!SUPPORTED_SKILLS.contains(resolvedSkill)) {
            throw new IllegalArgumentException("Unsupported A2A skill: " + resolvedSkill);
        }
        String rawPrompt = prompt == null ? "" : prompt;
        String wirePrompt = rawPrompt.isBlank() ? "Execute " + resolvedSkill : rawPrompt;
        Map<String, Object> execCtx = context == null ? Map.of() : new LinkedHashMap<>(context);
        String requestId = (taskId == null || taskId.isBlank()) ? "task-1" : taskId;

        AgUiA2aClient.DelegationExecution execution = client().delegate(
                requestId, resolvedSkill, wirePrompt, execCtx, Map.of());
        A2aDelegationResult res = execution.result();
        boolean isError = res.isError();

        String resolvedTaskId = taskId != null ? taskId : (isError ? "task-err" : "task-1");
        String resolvedAgentName = "A2A-Agent".equals(res.agentName()) ? "Nomad-A2A-Specialist" : res.agentName();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("taskId", resolvedTaskId);
        out.put("a2aTaskId", res.taskId());
        out.put("agentName", resolvedAgentName);
        out.put("protocol", "A2A-1.0-JSONRPC");
        out.put("skillId", resolvedSkill);
        out.put("status", isError ? "ERROR" : "COMPLETED");
        out.put("isError", isError);
        out.put("summary", res.summary());
        out.put("prompt", rawPrompt);
        out.put("report", res.data());
        if (isError) {
            out.put("error", res.data().getOrDefault("error", res.summary()));
        }
        return out;
    }

    private AgUiA2aClient client() {
        synchronized (clientLock) {
            if (activeClient == null || !Objects.equals(a2aBaseUrl, activeBaseUrl)) {
                closeActiveLocked();
                AgUiA2aClient.Builder builder = AgUiA2aClient.builder(a2aBaseUrl)
                        .streaming(false)
                        .requestTimeout(Duration.ofSeconds(6));
                if (vertxInstance != null && vertxInstance.isResolvable()) {
                    sharedVertxHttpClient = new VertxA2AHttpClient(vertxInstance.get());
                    builder.httpClient(sharedVertxHttpClient);
                }
                activeClient = builder.build();
                activeBaseUrl = a2aBaseUrl;
            }
            return activeClient;
        }
    }

    @PreDestroy
    void close() {
        synchronized (clientLock) {
            closeActiveLocked();
        }
    }

    private void closeActiveLocked() {
        if (activeClient != null) {
            activeClient.close();
            activeClient = null;
        }
        if (sharedVertxHttpClient != null) {
            sharedVertxHttpClient.close();
            sharedVertxHttpClient = null;
        }
        activeBaseUrl = null;
    }

    private static final Map<String, String> COUNTRY_ALIASES = Map.ofEntries(
            Map.entry("uk", "United Kingdom"),
            Map.entry("great britain", "United Kingdom"),
            Map.entry("england", "United Kingdom"),
            Map.entry("scotland", "United Kingdom"),
            Map.entry("usa", "United States"),
            Map.entry("us", "United States"),
            Map.entry("united states of america", "United States"),
            Map.entry("uae", "United Arab Emirates"),
            Map.entry("korea", "South Korea"),
            Map.entry("czech republic", "Czechia"));

    static String stripTrailingCountry(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String trimmed = raw.trim().replaceAll("\\s+", " ");
        int commaIdx = trimmed.indexOf(',');
        if (commaIdx > 0) {
            trimmed = trimmed.substring(0, commaIdx).trim();
        }
        if (trimmed.isEmpty()) {
            return "";
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (DYNAMIC_CITY_COUNTRIES.containsKey(lower) || CITY_TO_COUNTRY.containsKey(lower)) {
            return trimmed;
        }
        if (trimmed.contains(" ")) {
            String[] words = trimmed.split(" ");
            for (int suffixLen = Math.min(4, words.length - 1); suffixLen >= 1; suffixLen--) {
                int splitIdx = words.length - suffixLen;
                String suffix = String.join(" ", Arrays.copyOfRange(words, splitIdx, words.length));
                if (matchCanonicalCountry(suffix) != null) {
                    return String.join(" ", Arrays.copyOf(words, splitIdx));
                }
            }
        }
        return trimmed;
    }

    static String extractTrailingCountry(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim().replaceAll("\\s+", " ");
        int commaIdx = trimmed.indexOf(',');
        if (commaIdx > 0) {
            String afterComma = trimmed.substring(commaIdx + 1).trim();
            String canonical = matchCanonicalCountry(afterComma);
            return canonical != null ? canonical : (afterComma.isEmpty() ? null : afterComma);
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (DYNAMIC_CITY_COUNTRIES.containsKey(lower) || CITY_TO_COUNTRY.containsKey(lower)) {
            return null;
        }
        if (trimmed.contains(" ")) {
            String[] words = trimmed.split(" ");
            for (int suffixLen = Math.min(4, words.length - 1); suffixLen >= 1; suffixLen--) {
                int splitIdx = words.length - suffixLen;
                String suffix = String.join(" ", Arrays.copyOfRange(words, splitIdx, words.length));
                String canonical = matchCanonicalCountry(suffix);
                if (canonical != null) {
                    return canonical;
                }
            }
        }
        return null;
    }

    private static String matchCanonicalCountry(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        String lower = candidate.trim().toLowerCase(Locale.ROOT);
        String alias = COUNTRY_ALIASES.get(lower);
        if (alias != null) {
            return alias;
        }
        for (String country : COUNTRY_TO_CURRENCY.keySet()) {
            if (country.equalsIgnoreCase(candidate.trim())) {
                return country;
            }
        }
        return null;
    }

    static String resolveCountryForCity(String city) {
        if (city == null || city.isBlank()) {
            return "";
        }
        String key = city.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        String dynamic = DYNAMIC_CITY_COUNTRIES.get(key);
        if (dynamic != null && !dynamic.isBlank()) {
            return dynamic;
        }
        String known = CITY_TO_COUNTRY.get(key);
        if (known != null) {
            return known;
        }
        String trailing = extractTrailingCountry(city);
        if (trailing != null) {
            return trailing;
        }
        String stripped = stripTrailingCountry(city);
        String strippedKey = stripped.toLowerCase(Locale.ROOT);
        String strippedDynamic = DYNAMIC_CITY_COUNTRIES.get(strippedKey);
        if (strippedDynamic != null && !strippedDynamic.isBlank()) {
            return strippedDynamic;
        }
        return CITY_TO_COUNTRY.getOrDefault(strippedKey, stripped);
    }

    static String resolveCurrencyForCountry(String country) {
        if (country == null || country.isBlank()) {
            return "USD";
        }
        return COUNTRY_TO_CURRENCY.getOrDefault(country.trim(), "USD");
    }
}
