package org.agui.showcase.a2a;

import org.a2aproject.sdk.spec.AgentCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    void performAuditProducesArchitectureSecurityAndRemediationResults() {
        SpecialistAuditAgentService.AuditResult archRes = service.performAudit(
            "audit-clean-architecture",
            "Verify concentric ring policy for payment-service",
            Map.of("serviceName", "payment-service")
        );
        assertEquals("art-arch-1", archRes.artifactId());
        assertEquals("Clean Architecture Concentric Ring Report", archRes.artifactTitle());
        assertTrue(archRes.summary().contains("Archlens concentric layer verification passed"));
        assertTrue((Boolean) archRes.data().get("dipCompliant"));

        SpecialistAuditAgentService.AuditResult secRes = service.performAudit(
            "assess-security-posture",
            "Audit mTLS and token rotation",
            Map.of("region", "us-east-1")
        );
        assertEquals("art-sec-1", secRes.artifactId());
        assertTrue(secRes.summary().contains("Zero-trust mTLS"));
        assertEquals(0, secRes.data().get("cvesDetected"));

        SpecialistAuditAgentService.AuditResult remRes = service.performAudit(
            "plan-incident-remediation",
            "Mitigate CPU spike on payment-service",
            Map.of("serviceName", "payment-service", "recommendedReplicas", 6)
        );
        assertEquals("art-rem-1", remRes.artifactId());
        assertEquals(6, remRes.data().get("recommendedReplicas"));
    }

    @Test
    void performAuditRejectsBlankPrompt() {
        assertThrows(IllegalArgumentException.class, () -> service.performAudit("audit-clean-architecture", null, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> service.performAudit("audit-clean-architecture", "   ", Map.of()));
    }

    @Test
    void officialA2aSdkConfigurationProducesPublicAgentCardAndResolvesSkills() {
        SentinelA2aAgentConfiguration config = new SentinelA2aAgentConfiguration();
        AgentCard sdkCard = config.publicAgentCard();
        assertEquals("Sentinel-A2A-Auditor", sdkCard.name());
        assertEquals(5, sdkCard.skills().size());
        assertNotNull(config.agentExecutor(service));

        assertEquals("assess-security-posture", SentinelA2aAgentConfiguration.resolveSkillId("Audit security posture", Map.of()));
        assertEquals("plan-incident-remediation", SentinelA2aAgentConfiguration.resolveSkillId("Scale remediation", Map.of()));
        assertEquals("convert-and-audit-budget", SentinelA2aAgentConfiguration.resolveSkillId("Convert budget to JPY currency", Map.of()));
        assertEquals("verify-travel-advisory", SentinelA2aAgentConfiguration.resolveSkillId("Verify travel advisory for Japan", Map.of()));
        assertEquals("audit-clean-architecture", SentinelA2aAgentConfiguration.resolveSkillId("Check rings", Map.of()));
        assertEquals("assess-security-posture", SentinelA2aAgentConfiguration.resolveSkillId("Check rings", Map.of("skillId", "assess-security-posture")));
    }

    @Test
    void performAuditExecutesCurrencyAndTravelAdvisorySkillsWithFallbackAndLiveJson() {
        // Fallback path
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

        // Live JSON parsing path
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
    }
}
