package org.agui.showcase.a2a;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Official A2A Java SDK ({@code org.a2aproject.sdk} 1.4.0.Final) configuration for {@code Sentinel-A2A-Auditor} (:8082).
 * Exposes {@link PublicAgentCard} at {@code GET /.well-known/agent-card.json} and {@link AgentExecutor} over JSON-RPC 2.0 at {@code POST /}.
 */
@ApplicationScoped
public class SentinelA2aAgentConfiguration {

    @ConfigProperty(name = "quarkus.http.port", defaultValue = "8082")
    int httpPort = 8082;

    @Produces
    @PublicAgentCard
    public org.a2aproject.sdk.spec.AgentCard publicAgentCard() {
        String endpointUrl = "http://localhost:" + httpPort;
        return org.a2aproject.sdk.spec.AgentCard.builder()
                .name(SpecialistAuditAgentService.AGENT_NAME)
                .description("Specialist A2A Agent for Clean Architecture Concentric Ring Governance, Security Posture, and Incident Remediation")
                .version("1.4.0")
                .url(endpointUrl)
                .preferredTransport(TransportProtocol.JSONRPC.asString())
                .supportedInterfaces(List.of(
                        new AgentInterface(TransportProtocol.JSONRPC.asString(), endpointUrl)))
                .defaultInputModes(List.of("text/plain"))
                .defaultOutputModes(List.of("text/plain"))
                .capabilities(AgentCapabilities.builder()
                        .streaming(true)
                        .pushNotifications(false)
                        .build())
                .skills(List.of(
                        org.a2aproject.sdk.spec.AgentSkill.builder()
                                .id("audit-clean-architecture")
                                .name("Audit Clean Architecture Boundaries")
                                .description("Evaluates concentric layer dependencies (Domain <- Application <- Adapters) and DIP compliance")
                                .tags(List.of("architecture", "archlens", "clean-code"))
                                .examples(List.of("Audit cluster clean architecture boundaries"))
                                .build(),
                        org.a2aproject.sdk.spec.AgentSkill.builder()
                                .id("assess-security-posture")
                                .name("Assess Security & Zero-Trust Posture")
                                .description("Audits mTLS, OIDC token lifecycles, and secret hygiene across cluster workloads")
                                .tags(List.of("security", "devsecops", "mtls"))
                                .examples(List.of("Assess cluster zero-trust security posture"))
                                .build(),
                        org.a2aproject.sdk.spec.AgentSkill.builder()
                                .id("plan-incident-remediation")
                                .name("Plan Incident Auto-Remediation")
                                .description("Synthesizes horizontal scaling and circuit-breaker remediation blueprints")
                                .tags(List.of("sre", "remediation", "scaling"))
                                .examples(List.of("Plan remediation for degraded payment-service"))
                                .build()))
                .build();
    }

    @Produces
    @ApplicationScoped
    public AgentExecutor agentExecutor(SpecialistAuditAgentService auditService) {
        return new AgentExecutor() {
            @Override
            public void execute(RequestContext context, AgentEmitter emitter) {
                String userInput = context.getUserInput();
                String prompt = (userInput == null || userInput.isBlank()) ? "Audit architecture" : userInput;
                Map<String, Object> metadata = context.getMetadata() != null ? context.getMetadata() : Map.of();
                A2aTaskResponse response = auditService.executeTask(
                        new A2aTaskRequest(context.getTaskId(), resolveSkillId(prompt, metadata), prompt, metadata));
                emitter.complete(emitter.newAgentMessage(List.of(new TextPart(response.summary())), Map.of()));
            }

            @Override
            public void cancel(RequestContext context, AgentEmitter emitter) {
                emitter.cancel(emitter.newAgentMessage(List.of(new TextPart("Cancelled")), Map.of()));
            }
        };
    }

    static String resolveSkillId(String prompt, Map<String, Object> metadata) {
        if (metadata != null && metadata.get("skillId") instanceof String metaSkill && !metaSkill.isBlank()) {
            return metaSkill.trim();
        }
        String lower = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        if (lower.contains("security") || lower.contains("posture") || lower.contains("mtls") || lower.contains("cve")) {
            return "assess-security-posture";
        }
        if (lower.contains("remediat") || lower.contains("incident") || lower.contains("scale")) {
            return "plan-incident-remediation";
        }
        return "audit-clean-architecture";
    }
}
