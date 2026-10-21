package org.agui.showcase.a2a;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void getAgentCardReturnsCompliantA2aMetadataAndSkills() {
        AgentCard card = service.getAgentCard();
        assertEquals("Sentinel-A2A-Auditor", card.name());
        assertEquals("1.0.0", card.version());
        assertTrue(card.capabilities().getOrDefault("streaming", false));
        assertEquals(3, card.skills().size());
    }

    @Test
    void executeTaskProducesArchitectureAndSecurityArtifacts() {
        A2aTaskResponse archRes = service.executeTask(new A2aTaskRequest(
            "task-101",
            "audit-clean-architecture",
            "Verify concentric ring policy for payment-service",
            Map.of("serviceName", "payment-service")
        ));
        assertEquals("task-101", archRes.taskId());
        assertEquals("COMPLETED", archRes.status());
        assertFalse(archRes.artifacts().isEmpty());
        assertEquals("clean-architecture-dossier", archRes.artifacts().get(0).type());

        A2aTaskResponse secRes = service.executeTask(new A2aTaskRequest(
            "task-102",
            "assess-security-posture",
            "Audit mTLS and token rotation",
            Map.of("region", "us-east-1")
        ));
        assertEquals("COMPLETED", secRes.status());
        assertEquals("security-posture-report", secRes.artifacts().get(0).type());

        A2aTaskResponse remRes = service.executeTask(new A2aTaskRequest(
            "",
            "plan-incident-remediation",
            "Mitigate CPU spike on payment-service",
            Map.of("serviceName", "payment-service", "recommendedReplicas", 6)
        ));
        assertNotNull(remRes.taskId());
        assertFalse(remRes.taskId().isBlank());
        assertEquals("remediation-plan", remRes.artifacts().get(0).type());
    }

    @Test
    void executeTaskRejectsNullRequestOrBlankPrompt() {
        assertThrows(IllegalArgumentException.class, () -> service.executeTask(null));
        assertThrows(IllegalArgumentException.class, () -> service.executeTask(
            new A2aTaskRequest("task-x", "audit-clean-architecture", "   ", Map.of())
        ));
    }
}
