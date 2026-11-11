package org.agui.showcase.backend;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * LangChain4j {@code @Tool} bridge exposing the remote {@code Nomad-A2A-Specialist} ({@code :8082})
 * skills to {@link NomadConciergeAiService} with strict required-parameter validation.
 */
@ApplicationScoped
public class NomadA2aAgentTools {

    private final A2aGatewayClient a2aClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    public NomadA2aAgentTools(A2aGatewayClient a2aClient) {
        this.a2aClient = a2aClient;
    }

    @Tool("Verify official destination country metadata, capital, currency, and travel advisory via REST Countries v5 for a specific city. Never assume a default city.")
    public String verifyTravelAdvisory(
            @P("Destination city name (required; ask the user if not specified)") String city) {
        if (city == null || city.isBlank()) {
            return missingArgsJson("city");
        }
        String rawCity = city.trim();
        String resolvedCity = TripDossierStore.normalizeCityName(rawCity);
        String country = A2aGatewayClient.resolveCountryForCity(rawCity);
        Map<String, Object> handoff = a2aClient.delegateTask(
                "a2a-adv-" + UUID.randomUUID().toString().substring(0, 8),
                "verify-travel-advisory",
                "Verify travel advisory for " + resolvedCity,
                Map.of("city", resolvedCity, "country", country));
        return toJson(handoff);
    }

    @Tool("Convert and audit a traveler's trip budget into local destination currency using live Frankfurter ECB exchange rates. Requires city, positive budgetAmount, and 3-letter ISO baseCurrency.")
    public String convertAndAuditBudget(
            @P("Destination city name (required)") String city,
            @P("Total trip budget amount as a positive number (required; never assume a default budget)") Double budgetAmount,
            @P("3-letter ISO base currency code such as USD, EUR, GBP, or CHF (required)") String baseCurrency) {
        if (city == null || city.isBlank()
                || budgetAmount == null || budgetAmount <= 0.0
                || baseCurrency == null || baseCurrency.isBlank()) {
            return missingArgsJson("city, budgetAmount (> 0), baseCurrency");
        }
        String rawCity = city.trim();
        String resolvedCity = TripDossierStore.normalizeCityName(rawCity);
        String normBase = TripDossierStore.normalizeCurrencyToken(baseCurrency);
        String country = A2aGatewayClient.resolveCountryForCity(rawCity);
        String targetCurrency = A2aGatewayClient.resolveCurrencyForCountry(country);
        Map<String, Object> handoff = a2aClient.delegateTask(
                "a2a-fx-" + UUID.randomUUID().toString().substring(0, 8),
                "convert-and-audit-budget",
                "Convert " + budgetAmount + " " + normBase + " for " + resolvedCity,
                Map.of(
                        "city", resolvedCity,
                        "country", country,
                        "budgetUsd", budgetAmount,
                        "baseCurrency", normBase,
                        "targetCurrency", targetCurrency));
        return toJson(handoff);
    }

    @Tool("Curate walkable neighborhood architectural and acoustic cadence for a trip. Requires city, positive nights, and explicit travel focus.")
    public String curateNeighborhoodCadence(
            @P("Destination city name (required)") String city,
            @P("Trip duration in nights as a positive integer (required; never assume 3 nights)") Integer nights,
            @P("Traveler's cultural, culinary, musical, or architectural focus (required)") String focus) {
        if (city == null || city.isBlank()
                || nights == null || nights <= 0
                || focus == null || focus.isBlank()) {
            return missingArgsJson("city, nights (> 0), focus");
        }
        String rawCity = city.trim();
        String resolvedCity = TripDossierStore.normalizeCityName(rawCity);
        String country = A2aGatewayClient.resolveCountryForCity(rawCity);
        Map<String, Object> handoff = a2aClient.delegateTask(
                "a2a-cad-" + UUID.randomUUID().toString().substring(0, 8),
                "curate-neighborhood-cadence",
                "Curate " + nights + "-night cadence in " + resolvedCity + " focused on " + focus.trim(),
                Map.of("city", resolvedCity, "country", country, "nights", nights, "focus", focus.trim()));
        return toJson(handoff);
    }

    private String missingArgsJson(String fields) {
        return toJson(Map.of(
                "status", "ERROR",
                "isError", true,
                "code", "MISSING_REQUIRED_ARGUMENTS",
                "error", "Missing required arguments (" + fields + "). Ask the user to provide them instead of assuming defaults."));
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException ex) {
            return "{\"status\":\"ERROR\",\"isError\":true,\"error\":\"Serialization failure\"}";
        }
    }
}
