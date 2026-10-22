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
        assertEquals(3, sdkCard.skills().size());
        assertNotNull(config.agentExecutor(service));

        assertEquals("assess-security-posture", SentinelA2aAgentConfiguration.resolveSkillId("Audit security posture", Map.of()));
        assertEquals("plan-incident-remediation", SentinelA2aAgentConfiguration.resolveSkillId("Scale remediation", Map.of()));
        assertEquals("audit-clean-architecture", SentinelA2aAgentConfiguration.resolveSkillId("Check rings", Map.of()));
        assertEquals("assess-security-posture", SentinelA2aAgentConfiguration.resolveSkillId("Check rings", Map.of("skillId", "assess-security-posture")));
    }
}
