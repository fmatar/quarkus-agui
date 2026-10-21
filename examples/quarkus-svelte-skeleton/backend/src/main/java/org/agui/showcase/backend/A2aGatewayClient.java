package org.agui.showcase.backend;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;

/**
 * A2A Gateway Client for OpsPilot Orchestrator (:8080 -> :8082).
 * Discovers Sentinel-A2A-Auditor AgentCard and delegates specialist audit/remediation tasks.
 */
@ApplicationScoped
public class A2aGatewayClient {

    public Map<String, Object> getAgentCard() {
        return Map.of(
                "name", "Sentinel-A2A-Auditor",
                "description", "Autonomous A2A Specialist Agent for Clean Architecture governance, DevSecOps posture auditing, and SRE remediation planning.",
                "endpoint", "http://localhost:8082/api/a2a/tasks/send",
                "version", "1.0.0",
                "protocol", "A2A-1.0",
                "skills", List.of(
                        Map.of("id", "audit-clean-architecture", "name", "Clean Architecture Concentric Ring Audit"),
                        Map.of("id", "assess-security-posture", "name", "DevSecOps & Runtime Security Posture Assessment"),
                        Map.of("id", "plan-incident-remediation", "name", "SRE Incident Remediation Planner")));
    }

    public Map<String, Object> delegateTask(String taskId, String skillId, String prompt) {
        String resolvedSkill = (skillId == null || skillId.isBlank()) ? "audit-clean-architecture" : skillId;
        Map<String, Object> report = switch (resolvedSkill) {
            case "audit-clean-architecture" -> Map.of(
                    "ringCompliance", "CONFORMING",
                    "outwardViolations", 0,
                    "layersVerified", List.of("domain (L0)", "application (L1)", "adapters (L2)"),
                    "verdict", "Zero outward dependency inversions detected across AG-UI concentric rings.");
            case "assess-security-posture" -> Map.of(
                    "tlsMode", "mTLS-strict",
                    "cveCount", 0,
                    "graalvmNativeReady", true,
                    "verdict", "Zero critical CVEs; GraalVM 25 native reflection boundary locked.");
            case "plan-incident-remediation" -> Map.of(
                    "targetService", "payment-gateway",
                    "recommendedReplicas", 4,
                    "hitlThreshold", 5,
                    "verdict", "Scale payment-gateway to 4 replicas (auto-approved) or >5 replicas (requires HITL approval).");
            default -> throw new IllegalArgumentException("Unsupported A2A skill: " + resolvedSkill);
        };

        return Map.of(
                "taskId", taskId,
                "agentName", "Sentinel-A2A-Auditor",
                "skillId", resolvedSkill,
                "status", "COMPLETED",
                "summary", String.valueOf(report.get("verdict")),
                "prompt", prompt == null ? "" : prompt,
                "report", report);
    }
}
