package org.agui.showcase.a2a;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class SpecialistAuditAgentService {

    public static final String AGENT_NAME = "Sentinel-A2A-Auditor";

    public AgentCard getAgentCard() {
        return new AgentCard(
            AGENT_NAME,
            "Specialist A2A Agent for Clean Architecture Concentric Ring Governance, Security Posture, and Incident Remediation",
            "http://localhost:8082",
            "1.0.0",
            Map.of("streaming", true, "pushNotifications", false, "stateTransitionHistory", true),
            List.of(
                new AgentSkill(
                    "audit-clean-architecture",
                    "Audit Clean Architecture Boundaries",
                    "Evaluates concentric layer dependencies (Domain <- Application <- Adapters) and DIP compliance",
                    List.of("architecture", "archlens", "clean-code")
                ),
                new AgentSkill(
                    "assess-security-posture",
                    "Assess Security & Zero-Trust Posture",
                    "Audits mTLS, OIDC token lifecycles, and secret hygiene across cluster workloads",
                    List.of("security", "devsecops", "mtls")
                ),
                new AgentSkill(
                    "plan-incident-remediation",
                    "Plan Incident Auto-Remediation",
                    "Synthesizes horizontal scaling and circuit-breaker remediation blueprints",
                    List.of("sre", "remediation", "scaling")
                )
            )
        );
    }

    public A2aTaskResponse executeTask(A2aTaskRequest request) {
        if (request == null || request.prompt() == null || request.prompt().isBlank()) {
            throw new IllegalArgumentException("A2A task request and prompt must not be blank");
        }
        String resolvedTaskId = (request.taskId() == null || request.taskId().isBlank())
            ? "a2a-" + UUID.randomUUID().toString().substring(0, 8)
            : request.taskId().trim();
        String skill = (request.skillId() == null || request.skillId().isBlank())
            ? "audit-clean-architecture"
            : request.skillId().trim();
        Map<String, Object> ctx = request.context() == null ? Map.of() : request.context();
        String targetService = String.valueOf(ctx.getOrDefault("serviceName", "payment-service"));

        return switch (skill) {
            case "assess-security-posture" -> new A2aTaskResponse(
                resolvedTaskId,
                AGENT_NAME,
                skill,
                "COMPLETED",
                "Zero-trust mTLS and OIDC token validation verified across cluster.",
                List.of(new A2aArtifact(
                    "art-sec-1",
                    "security-posture-report",
                    "Zero-Trust Security Audit",
                    Map.of(
                        "mtlsEnabled", true,
                        "zeroPlaintextSecrets", true,
                        "cvesDetected", 0,
                        "recommendation", "Maintain strict mTLS peer authentication on " + targetService
                    )
                ))
            );
            case "plan-incident-remediation" -> new A2aTaskResponse(
                resolvedTaskId,
                AGENT_NAME,
                skill,
                "COMPLETED",
                "Remediation plan synthesized for " + targetService + ": scale replicas and enable adaptive concurrency limit.",
                List.of(new A2aArtifact(
                    "art-rem-1",
                    "remediation-plan",
                    "Incident Remediation Blueprint",
                    Map.of(
                        "serviceName", targetService,
                        "recommendedReplicas", ctx.getOrDefault("recommendedReplicas", 6),
                        "requiresHitlApprovalAboveReplicas", 5,
                        "action", "scaleDeployment"
                    )
                ))
            );
            default -> new A2aTaskResponse(
                resolvedTaskId,
                AGENT_NAME,
                "audit-clean-architecture",
                "COMPLETED",
                "Archlens concentric layer verification passed for " + targetService + " with zero outward dependency leaks.",
                List.of(new A2aArtifact(
                    "art-arch-1",
                    "clean-architecture-dossier",
                    "Clean Architecture Concentric Ring Report",
                    Map.of(
                        "serviceName", targetService,
                        "concentricRings", List.of("domain", "application", "adapters"),
                        "outwardViolations", 0,
                        "dipCompliant", true
                    )
                ))
            );
        };
    }
}
