package org.agui.showcase.a2a;

import org.a2aproject.sdk.spec.AgentCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecialistAuditAgentServiceTest {

    private SpecialistAuditAgentService service;

    @BeforeEach
    void setUp() {
        service = new SpecialistAuditAgentService();
    }

    @Test
    void performAuditProducesCurrencyAdvisoryAndNeighborhoodCadenceResults() {
        SpecialistAuditAgentService.AuditResult fxRes = service.performAudit(
            "convert-and-audit-budget",
            "Audit Kyoto 4-night trip budget in JPY",
            Map.of("city", "Kyoto", "currency", "JPY", "budgetUsd", 3200.0)
        );
        assertEquals("art-fx-1", fxRes.artifactId());
        assertEquals("Nomad Multi-Currency & Budget Audit", fxRes.artifactTitle());
        assertTrue(fxRes.summary().contains("Frankfurter FX audit complete"));

        SpecialistAuditAgentService.AuditResult advRes = service.performAudit(
            "verify-travel-advisory",
            "Verify travel advisory for Lisbon Portugal",
            Map.of("city", "Lisbon")
        );
        assertEquals("art-adv-1", advRes.artifactId());
        assertTrue(advRes.summary().contains("Destination advisory verified"));

        for (String city : List.of(
                "Kyoto", "Lisbon", "Reykjavik", "Tokyo", "Paris", "Rome", "Barcelona",
                "London", "Berlin", "Vienna", "Zurich", "Amsterdam", "Copenhagen",
                "Stockholm", "Oslo", "Prague", "Athens", "Istanbul", "Marrakech",
                "Cairo", "Seoul", "Bangkok", "Singapore", "Sydney", "New York",
                "Oaxaca", "Buenos Aires")) {
            SpecialistAuditAgentService.AuditResult cadRes = service.performAudit(
                "curate-neighborhood-cadence",
                "Curate neighborhood cadence in " + city,
                Map.of()
            );
            assertEquals("art-cadence-1", cadRes.artifactId());
            assertEquals(city, cadRes.data().get("city"));

            SpecialistAuditAgentService.AuditResult cityFx = service.performAudit(
                "convert-and-audit-budget",
                "Audit budget in " + city,
                Map.of()
            );
            assertEquals(city, cityFx.data().get("city"));
        }
    }

    @Test
    void performAuditRejectsBlankPrompt() {
        assertThrows(IllegalArgumentException.class, () -> service.performAudit("convert-and-audit-budget", null, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> service.performAudit("convert-and-audit-budget", "   ", Map.of()));
    }

    @Test
    void officialA2aSdkConfigurationProducesPublicAgentCardAndResolvesSkills() {
        SentinelA2aAgentConfiguration config = new SentinelA2aAgentConfiguration();
        AgentCard sdkCard = config.publicAgentCard();
        assertEquals("Nomad-A2A-Specialist", sdkCard.name());
        assertEquals(3, sdkCard.skills().size());
        assertNotNull(config.agentExecutor(service));

        assertEquals("convert-and-audit-budget", SentinelA2aAgentConfiguration.resolveSkillId("Convert budget to JPY currency", Map.of()));
        assertEquals("verify-travel-advisory", SentinelA2aAgentConfiguration.resolveSkillId("Verify travel advisory for Japan", Map.of()));
        assertEquals("curate-neighborhood-cadence", SentinelA2aAgentConfiguration.resolveSkillId("Curate neighborhood sequence", Map.of()));
        assertEquals("verify-travel-advisory", SentinelA2aAgentConfiguration.resolveSkillId("Check trip", Map.of("skillId", "verify-travel-advisory")));
    }

    @Test
    void performAuditExecutesCurrencyAndTravelAdvisorySkillsWithFallbackAndLiveJson() {
        service.frankfurterBaseUrl = "http://127.0.0.1:1";
        service.restCountriesBaseUrl = "http://127.0.0.1:1";

        SpecialistAuditAgentService.AuditResult fxFallback = service.performAudit(
            "convert-and-audit-budget",
            "Audit Kyoto 4-night trip budget in JPY",
            Map.of("city", "Kyoto", "currency", "JPY", "budgetUsd", 2400.0)
        );
        assertEquals("art-fx-1", fxFallback.artifactId());
        assertEquals("JPY", fxFallback.data().get("targetCurrency"));

        SpecialistAuditAgentService.AuditResult advFallback = service.performAudit(
            "verify-travel-advisory",
            "Verify travel advisory for Lisbon Portugal",
            Map.of("city", "Lisbon")
        );
        assertEquals("art-adv-1", advFallback.artifactId());
        assertEquals("Portugal", advFallback.data().get("country"));

        service.httpFetcher = url -> {
            if (url.contains("/v1/latest")) {
                return "{\"amount\":1.0,\"base\":\"USD\",\"rates\":{\"JPY\":153.25,\"EUR\":0.91}}";
            }
            if (url.contains("/v3.1/name/")) {
                return "[{\"name\":{\"common\":\"Japan\"},\"capital\":[\"Tokyo\"],\"region\":\"Asia\",\"timezones\":[\"UTC+09:00\"]}]";
            }
            return null;
        };

        SpecialistAuditAgentService.AuditResult fxLive = service.performAudit(
            "convert-and-audit-budget",
            "Convert budget for Kyoto",
            Map.of("city", "Kyoto", "currency", "JPY", "budgetUsd", 2000.0)
        );
        assertEquals(153.25, fxLive.data().get("exchangeRate"));
        assertEquals("api.frankfurter.dev", fxLive.data().get("source"));

        SpecialistAuditAgentService.AuditResult advLive = service.performAudit(
            "verify-travel-advisory",
            "Advisory for Kyoto Japan",
            Map.of("city", "Kyoto")
        );
        assertEquals("Japan", advLive.data().get("country"));
        assertEquals("restcountries.com", advLive.data().get("source"));

        SpecialistAuditAgentService.AuditResult dynamicCityFx = service.performAudit(
            "convert-and-audit-budget",
            "[convert-and-audit-budget] Plan a trip to Oaxaca",
            Map.of()
        );
        assertEquals("Oaxaca", dynamicCityFx.data().get("city"));
        assertEquals("MXN", dynamicCityFx.data().get("targetCurrency"));
    }
}
