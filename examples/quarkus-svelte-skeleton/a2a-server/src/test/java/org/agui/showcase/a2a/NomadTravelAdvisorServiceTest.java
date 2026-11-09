package org.agui.showcase.a2a;

import org.a2aproject.sdk.spec.AgentCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NomadTravelAdvisorServiceTest {

    private static final String REST_COUNTRIES_DEPRECATION_JSON = """
            {
                "success": false,
                "data": null,
                "errors": [
                    {
                        "message": "This API version has been deprecated. Please visit https://restcountries.com/docs/countries/legacy-api-deprecation to migrate to our new version (v5)."
                    }
                ]
            }
            """;

    private NomadTravelAdvisorService service;

    @BeforeEach
    void setUp() {
        service = new NomadTravelAdvisorService();
    }

    @Test
    void performAuditRejectsBlankPromptAndMissingRequiredArgumentsWithoutAssumingKyotoOrDefaultBudget() {
        assertThrows(IllegalArgumentException.class,
                () -> service.performAudit("convert-and-audit-budget", null, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> service.performAudit("convert-and-audit-budget", "   ", Map.of()));

        // Missing city must NOT assume "Kyoto"
        NomadTravelAdvisorService.AuditResult missingCityAdvisory = service.performAudit(
                "verify-travel-advisory",
                "Verify destination travel advisory",
                Map.of());
        assertEquals("ERROR", missingCityAdvisory.data().get("status"));
        assertEquals(Boolean.TRUE, missingCityAdvisory.data().get("isError"));
        assertEquals("MISSING_REQUIRED_ARGUMENTS", missingCityAdvisory.data().get("code"));

        NomadTravelAdvisorService.AuditResult missingCityCadence = service.performAudit(
                "curate-neighborhood-cadence",
                "Curate architectural neighborhood sequence",
                Map.of());
        assertEquals("ERROR", missingCityCadence.data().get("status"));
        assertEquals(Boolean.TRUE, missingCityCadence.data().get("isError"));
        assertEquals("MISSING_REQUIRED_ARGUMENTS", missingCityCadence.data().get("code"));

        // Missing budgetUsd/amount must NOT assume $3,200.0
        NomadTravelAdvisorService.AuditResult missingBudgetFx = service.performAudit(
                "convert-and-audit-budget",
                "Convert trip budget for Paris",
                Map.of("city", "Paris"));
        assertEquals("ERROR", missingBudgetFx.data().get("status"));
        assertEquals(Boolean.TRUE, missingBudgetFx.data().get("isError"));
        assertEquals("MISSING_REQUIRED_ARGUMENTS", missingBudgetFx.data().get("code"));
    }

    @Test
    void performTravelAdvisorySurfacesRestCountriesDeprecationErrorWithoutFakeClearFallback() {
        service.setHttpProbe(url -> new NomadTravelAdvisorService.UpstreamHttpResult(
                400, REST_COUNTRIES_DEPRECATION_JSON, "HTTP 400 from restcountries.com", 42L));

        NomadTravelAdvisorService.AuditResult advError = service.performAudit(
                "verify-travel-advisory",
                "Verify travel advisory for Paris",
                Map.of("city", "Paris"));

        assertEquals("ERROR", advError.data().get("status"));
        assertEquals(Boolean.TRUE, advError.data().get("isError"));
        assertEquals(Boolean.FALSE, advError.data().get("serviceAvailable"));
        assertEquals("restcountries.com", advError.data().get("upstream"));
        assertTrue(String.valueOf(advError.data().get("error")).contains("This API version has been deprecated"));
        assertTrue(advError.summary().contains("Travel Advisory Service Unavailable (restcountries.com)"));
        assertTrue(advError.summary().contains("This API version has been deprecated"));
        assertFalse(advError.summary().contains("CLEAR"));

        // Even if HTTP status is 200 with {"success": false, "errors": [...]}, it MUST still fail honestly
        service.setHttpProbe(url -> new NomadTravelAdvisorService.UpstreamHttpResult(
                200, REST_COUNTRIES_DEPRECATION_JSON, null, 35L));

        NomadTravelAdvisorService.AuditResult advEnvelopeError = service.performAudit(
                "verify-travel-advisory",
                "Verify travel advisory for Lisbon",
                Map.of("city", "Lisbon"));
        assertEquals("ERROR", advEnvelopeError.data().get("status"));
        assertEquals(Boolean.TRUE, advEnvelopeError.data().get("isError"));
        assertTrue(advEnvelopeError.summary().contains("This API version has been deprecated"));
    }

    @Test
    void performCurrencyAuditSurfacesFrankfurterFailureAndConvertsLiveRatesWhenHealthy() {
        // 1. Frankfurter down -> honest ERROR, never fake 151.40 JPY
        service.setHttpProbe(url -> new NomadTravelAdvisorService.UpstreamHttpResult(
                0, null, "ConnectException: Connection refused", 12L));

        NomadTravelAdvisorService.AuditResult fxDown = service.performAudit(
                "convert-and-audit-budget",
                "Convert $2,400 USD budget for Kyoto",
                Map.of("city", "Kyoto", "currency", "JPY", "budgetUsd", 2400.0, "baseCurrency", "USD"));
        assertEquals("ERROR", fxDown.data().get("status"));
        assertEquals(Boolean.TRUE, fxDown.data().get("isError"));
        assertEquals(Boolean.FALSE, fxDown.data().get("serviceAvailable"));
        assertEquals("api.frankfurter.dev", fxDown.data().get("upstream"));
        assertFalse(fxDown.summary().contains("Frankfurter FX audit complete"));

        // 2. Frankfurter & REST Countries healthy -> live rates & country metadata
        service.setHttpProbe(url -> {
            if (url.contains("/v1/latest")) {
                return new NomadTravelAdvisorService.UpstreamHttpResult(
                        200,
                        "{\"amount\":1.0,\"base\":\"USD\",\"rates\":{\"JPY\":153.25,\"EUR\":0.91}}",
                        null,
                        28L);
            }
            if (url.contains("/v3.1/name/")) {
                return new NomadTravelAdvisorService.UpstreamHttpResult(
                        200,
                        "[{\"name\":{\"common\":\"Japan\"},\"capital\":[\"Tokyo\"],\"region\":\"Asia\",\"timezones\":[\"UTC+09:00\"]}]",
                        null,
                        31L);
            }
            return new NomadTravelAdvisorService.UpstreamHttpResult(404, null, "HTTP 404", 10L);
        });

        NomadTravelAdvisorService.AuditResult fxLive = service.performAudit(
                "convert-and-audit-budget",
                "Convert $2,000 USD budget for Kyoto",
                Map.of("city", "Kyoto", "currency", "JPY", "budgetUsd", 2000.0, "baseCurrency", "USD"));
        assertEquals("OK", fxLive.data().get("status"));
        assertEquals(Boolean.FALSE, fxLive.data().get("isError"));
        assertEquals(153.25, fxLive.data().get("exchangeRate"));
        assertEquals("api.frankfurter.dev", fxLive.data().get("source"));
        assertTrue(fxLive.summary().contains("Frankfurter FX audit complete"));

        NomadTravelAdvisorService.AuditResult advLive = service.performAudit(
                "verify-travel-advisory",
                "Verify travel advisory for Kyoto",
                Map.of("city", "Kyoto"));
        assertEquals("OK", advLive.data().get("status"));
        assertEquals(Boolean.FALSE, advLive.data().get("isError"));
        assertEquals("Japan", advLive.data().get("country"));
        assertEquals("restcountries.com", advLive.data().get("source"));

        // Same-currency identity conversion (e.g. EUR -> EUR for Paris) still probes Frankfurter health
        NomadTravelAdvisorService.AuditResult sameCurFx = service.performAudit(
                "convert-and-audit-budget",
                "Audit €2,500 EUR budget in Paris",
                Map.of("city", "Paris", "baseCurrency", "EUR", "currency", "EUR", "budgetUsd", 2500.0));
        assertEquals("OK", sameCurFx.data().get("status"));
        assertEquals(1.0, sameCurFx.data().get("exchangeRate"));

        // Neighborhood cadence with explicit city
        NomadTravelAdvisorService.AuditResult cadence = service.performAudit(
                "curate-neighborhood-cadence",
                "Curate neighborhood cadence in Lisbon",
                Map.of("city", "Lisbon", "focus", "architecture and fado"));
        assertEquals("OK", cadence.data().get("status"));
        assertEquals("Lisbon", cadence.data().get("city"));
    }

    @Test
    void checkHealthReportsDegradedWhenRestCountriesIsDeprecatedAndUpWhenBothSucceed() {
        service.setHttpProbe(url -> {
            if (url.contains("frankfurter")) {
                return new NomadTravelAdvisorService.UpstreamHttpResult(
                        200, "{\"amount\":1.0,\"base\":\"USD\",\"rates\":{\"EUR\":0.92}}", null, 20L);
            }
            return new NomadTravelAdvisorService.UpstreamHttpResult(
                    400, REST_COUNTRIES_DEPRECATION_JSON, "HTTP 400 from restcountries.com", 45L);
        });

        Map<String, Object> degraded = service.checkHealth();
        assertEquals("DEGRADED", degraded.get("status"));
        assertEquals(1L, ((Number) degraded.get("healthyCount")).longValue());
        assertEquals(2, ((Number) degraded.get("totalCount")).intValue());

        NomadA2aAgentConfiguration config = new NomadA2aAgentConfiguration();
        AgentCard sdkCard = config.publicAgentCard();
        assertEquals("Nomad-A2A-Specialist", sdkCard.name());
        assertEquals(3, sdkCard.skills().size());
        assertNotNull(config.agentExecutor(service));
        assertEquals("verify-travel-advisory",
                NomadA2aAgentConfiguration.resolveSkillId("Verify travel advisory for France", Map.of()));
        assertEquals("curate-neighborhood-cadence",
                NomadA2aAgentConfiguration.resolveSkillId("Curate neighborhood sequence", Map.of()));
        assertEquals("convert-and-audit-budget",
                NomadA2aAgentConfiguration.resolveSkillId("Convert currency budget", Map.of()));
        assertEquals("verify-travel-advisory",
                NomadA2aAgentConfiguration.resolveSkillId("Any prompt", Map.of("skillId", "verify-travel-advisory")));

        // Additional edge cases: unsupported skill, prompt-parsed budget/city, cadences across cities, malformed JSON
        NomadTravelAdvisorService.AuditResult unsupported = service.performAudit("unknown-skill", "Check Paris", Map.of());
        assertEquals("UNSUPPORTED_SKILL", unsupported.data().get("code"));

        for (String city : java.util.List.of("Reykjavik", "Tokyo", "Paris", "Beirut", "Oaxaca")) {
            NomadTravelAdvisorService.AuditResult c = service.performAudit(
                    "curate-neighborhood-cadence", "Explore " + city, Map.of());
            assertEquals("OK", c.data().get("status"));
        }

        // Prompt-based £ / CHF budget extraction & missing rate handling
        service.setHttpProbe(url -> new NomadTravelAdvisorService.UpstreamHttpResult(
                200, "{\"amount\":1.0,\"base\":\"GBP\",\"rates\":{}}", null, 15L));
        NomadTravelAdvisorService.AuditResult missingRate = service.performAudit(
                "convert-and-audit-budget",
                "Convert £1,800 pounds for Tokyo",
                Map.of());
        assertEquals("ERROR", missingRate.data().get("status"));

        // Invalid JSON handling & custom city fallback via regex
        service.setHttpProbe(url -> new NomadTravelAdvisorService.UpstreamHttpResult(200, "not-json-array", null, 10L));
        NomadTravelAdvisorService.AuditResult badAdvJson = service.performAudit(
                "verify-travel-advisory",
                "Check travel in Bruges",
                Map.of());
        assertEquals("ERROR", badAdvJson.data().get("status"));
    }
}
