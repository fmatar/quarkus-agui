package org.agui.showcase.a2a;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;

/**
 * Companion observability & health endpoint for the Nomad A2A Specialist Agent (:8082).
 */
@Path("/api/a2a")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class NomadA2aHealthResource {

    private final NomadTravelAdvisorService advisorService;

    @Inject
    public NomadA2aHealthResource(NomadTravelAdvisorService advisorService) {
        this.advisorService = advisorService;
    }

    @GET
    @Path("/health")
    public Map<String, Object> health() {
        return advisorService.checkHealth();
    }

    @POST
    @Path("/audit")
    public NomadTravelAdvisorService.AuditResult audit(Map<String, Object> body) {
        String skillId = body != null && body.get("skillId") instanceof String s ? s : null;
        String prompt = body != null && body.get("prompt") instanceof String p ? p : "Execute travel audit";
        @SuppressWarnings("unchecked")
        Map<String, Object> context = body != null && body.get("context") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.of();
        return advisorService.performAudit(skillId, prompt, context);
    }
}
