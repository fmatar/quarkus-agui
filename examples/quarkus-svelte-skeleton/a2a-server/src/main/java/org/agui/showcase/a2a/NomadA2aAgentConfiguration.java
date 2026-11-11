package org.agui.showcase.a2a;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Official A2A Java SDK ({@code org.a2aproject.sdk} 1.5.0.Final) configuration for {@code Nomad-A2A-Specialist} (:8082).
 * Exposes {@link PublicAgentCard} at {@code GET /.well-known/agent-card.json} and {@link AgentExecutor} over JSON-RPC 2.0 at {@code POST /}.
 */
@ApplicationScoped
public class NomadA2aAgentConfiguration {

    @ConfigProperty(name = "quarkus.http.port", defaultValue = "8082")
    int httpPort = 8082;

    @ConfigProperty(name = "agui.a2a.public-url")
    java.util.Optional<String> publicUrl = java.util.Optional.empty();

    @Produces
    @PublicAgentCard
    public org.a2aproject.sdk.spec.AgentCard publicAgentCard() {
        String endpointUrl = publicUrl.filter(s -> !s.isBlank()).orElse("http://localhost:" + httpPort);
        return org.a2aproject.sdk.spec.AgentCard.builder()
                .name(NomadTravelAdvisorService.AGENT_NAME)
                .description("Specialist A2A Travel Advisor Agent for Frankfurter ECB FX Conversion, Destination Entry Advisories, and Architectural Neighborhood Curation")
                .version("1.5.0")
                .url(endpointUrl)
                .preferredTransport(TransportProtocol.JSONRPC.asString())
                .supportedInterfaces(List.of(
                        new AgentInterface(TransportProtocol.JSONRPC.asString(), endpointUrl)))
                .defaultInputModes(List.of("text/plain", "application/json"))
                .defaultOutputModes(List.of("text/plain", "application/json"))
                .capabilities(AgentCapabilities.builder()
                        .streaming(true)
                        .pushNotifications(false)
                        .build())
                .skills(List.of(
                        org.a2aproject.sdk.spec.AgentSkill.builder()
                                .id("convert-and-audit-budget")
                                .name("Convert Currency & Audit Travel Budget")
                                .description("Converts explicit trip budgets via Frankfurter FX API and verifies reservation limits")
                                .tags(List.of("nomad", "currency", "frankfurter", "budget"))
                                .examples(List.of("Convert $2,500 USD budget for Paris to EUR"))
                                .build(),
                        org.a2aproject.sdk.spec.AgentSkill.builder()
                                .id("verify-travel-advisory")
                                .name("Verify Destination Country & Cultural Advisory")
                                .description("Resolves destination country metadata and timezone via REST Countries API")
                                .tags(List.of("nomad", "advisory", "countries"))
                                .examples(List.of("Verify travel advisory for Lisbon Portugal"))
                                .build(),
                        org.a2aproject.sdk.spec.AgentSkill.builder()
                                .id("curate-neighborhood-cadence")
                                .name("Curate Neighborhood Architectural & Acoustic Cadence")
                                .description("Synthesizes a morning-to-evening architectural, craft, and listening-bar sequence for an explicit destination")
                                .tags(List.of("nomad", "itinerary", "architecture", "music"))
                                .examples(List.of("Curate neighborhood cadence for Tokyo"))
                                .build()))
                .build();
    }

    @Produces
    @ApplicationScoped
    public AgentExecutor agentExecutor(NomadTravelAdvisorService advisorService) {
        return (context, emitter) -> {
            String prompt = context.getUserInput();
            Map<String, Object> execCtx = extractExecutionContext(context);
            NomadTravelAdvisorService.AuditResult result = advisorService.performAudit(
                    resolveSkillId(prompt, execCtx),
                    (prompt == null || prompt.isBlank()) ? "Execute travel specialist skill" : prompt,
                    execCtx);

            Map<String, Object> metadata = new LinkedHashMap<>(result.data());
            metadata.put("artifactId", result.artifactId());
            metadata.put("artifactTitle", result.artifactTitle());
            metadata.put("summary", result.summary());

            List<Part<?>> responseParts = List.of(
                    new TextPart(result.summary()),
                    new DataPart(metadata));

            if (context.getTask() == null) {
                emitter.addTask(emitter.taskBuilder()
                        .status(new org.a2aproject.sdk.spec.TaskStatus(
                                org.a2aproject.sdk.spec.TaskState.TASK_STATE_SUBMITTED))
                        .build());
            }
            emitter.startWork();
            emitter.addArtifact(responseParts, result.artifactId(), result.artifactTitle(), metadata);
            emitter.complete(emitter.newAgentMessage(responseParts, metadata));
        };
    }

    static Map<String, Object> extractExecutionContext(RequestContext context) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (context != null && context.getMetadata() != null) {
            merged.putAll(context.getMetadata());
        }
        if (context != null && context.getMessage() != null && context.getMessage().parts() != null) {
            for (Part<?> part : context.getMessage().parts()) {
                if (part instanceof DataPart dataPart && dataPart.data() instanceof Map<?, ?> dataMap) {
                    for (Map.Entry<?, ?> entry : dataMap.entrySet()) {
                        if (entry.getKey() != null && entry.getValue() != null) {
                            merged.put(String.valueOf(entry.getKey()), entry.getValue());
                        }
                    }
                }
            }
        }
        return merged;
    }

    public static String resolveSkillId(String prompt, Map<String, Object> metadata) {
        if (metadata != null && metadata.get("skillId") instanceof String metaSkill && !metaSkill.isBlank()) {
            return metaSkill.trim();
        }
        String lower = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        if (lower.contains("[verify-travel-advisory]") || lower.contains("advisory") || lower.contains("visa") || lower.contains("country") || lower.contains("timezone")) {
            return "verify-travel-advisory";
        }
        if (lower.contains("[curate-neighborhood-cadence]") || lower.contains("cadence") || lower.contains("neighborhood") || lower.contains("sequence")) {
            return "curate-neighborhood-cadence";
        }
        return "convert-and-audit-budget";
    }
}
