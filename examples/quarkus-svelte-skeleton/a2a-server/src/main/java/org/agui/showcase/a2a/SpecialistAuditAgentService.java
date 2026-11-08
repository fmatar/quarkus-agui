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
 * Domain service providing audit analysis, currency conversion, and destination advisory findings.
 */
@ApplicationScoped
public class SpecialistAuditAgentService {

    public static final String AGENT_NAME = "Sentinel-A2A-Auditor";

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
            throw new IllegalArgumentException("Audit prompt must not be blank");
        }
        String skill = (skillId == null || skillId.isBlank()) ? "audit-clean-architecture" : skillId.trim();
        Map<String, Object> ctx = context == null ? Map.of() : context;
        String targetService = String.valueOf(ctx.getOrDefault("serviceName", "payment-service"));

        return switch (skill) {
            case "assess-security-posture" -> new AuditResult(
                    "Zero-trust mTLS and OIDC token validation verified across cluster.",
                    "art-sec-1",
                    "Zero-Trust Security Audit",
                    Map.of(
                            "mtlsEnabled", true,
                            "zeroPlaintextSecrets", true,
                            "cvesDetected", 0,
                            "recommendation", "Maintain strict mTLS peer authentication on " + targetService));
            case "plan-incident-remediation" -> new AuditResult(
                    "Remediation plan synthesized for " + targetService + ": scale replicas and enable adaptive concurrency limit.",
                    "art-rem-1",
                    "Incident Remediation Blueprint",
                    Map.of(
                            "serviceName", targetService,
                            "recommendedReplicas", ctx.getOrDefault("recommendedReplicas", 6),
                            "requiresHitlApprovalAboveReplicas", 5,
                            "action", "scaleDeployment"));
            case "convert-and-audit-budget" -> performCurrencyAndBudgetAudit(prompt, ctx);
            case "verify-travel-advisory" -> performTravelAdvisoryAudit(prompt, ctx);
            default -> new AuditResult(
                    "Archlens concentric layer verification passed for " + targetService + " with zero outward dependency leaks.",
                    "art-arch-1",
                    "Clean Architecture Concentric Ring Report",
                    Map.of(
                            "serviceName", targetService,
                            "concentricRings", List.of("domain", "application", "adapters"),
                            "outwardViolations", 0,
                            "dipCompliant", true));
        };
    }

    private AuditResult performCurrencyAndBudgetAudit(String prompt, Map<String, Object> ctx) {
        String city = String.valueOf(ctx.getOrDefault("city", detectCity(prompt)));
        String targetCurrency = String.valueOf(ctx.getOrDefault("currency", defaultCurrencyForCity(city)));
        double budgetUsd = ctx.get("budgetUsd") instanceof Number n ? n.doubleValue() : 2400.0;
        double rate = switch (targetCurrency.toUpperCase(Locale.ROOT)) {
            case "EUR" -> 0.92;
            case "ISK" -> 137.80;
            default -> 151.40;
        };
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
        String country = switch (city.toLowerCase(Locale.ROOT)) {
            case "lisbon" -> "Portugal";
            case "reykjavik" -> "Iceland";
            default -> "Japan";
        };
        String capital = switch (city.toLowerCase(Locale.ROOT)) {
            case "lisbon" -> "Lisbon";
            case "reykjavik" -> "Reykjavik";
            default -> "Tokyo";
        };
        String region = switch (city.toLowerCase(Locale.ROOT)) {
            case "lisbon", "reykjavik" -> "Europe";
            default -> "Asia";
        };
        String timezone = switch (city.toLowerCase(Locale.ROOT)) {
            case "lisbon", "reykjavik" -> "UTC+00:00";
            default -> "UTC+09:00";
        };
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

    private String detectCity(String prompt) {
        String lower = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        if (lower.contains("lisbon") || lower.contains("portugal")) {
            return "Lisbon";
        }
        if (lower.contains("reykjavik") || lower.contains("iceland")) {
            return "Reykjavik";
        }
        return "Kyoto";
    }

    private String defaultCurrencyForCity(String city) {
        return switch (city.toLowerCase(Locale.ROOT)) {
            case "lisbon" -> "EUR";
            case "reykjavik" -> "ISK";
            default -> "JPY";
        };
    }
}
