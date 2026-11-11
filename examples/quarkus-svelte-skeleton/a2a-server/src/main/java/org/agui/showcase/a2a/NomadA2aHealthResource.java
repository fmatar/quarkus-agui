package org.agui.showcase.a2a;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;

/**
 * Companion observability & health endpoint for the Nomad A2A Specialist Agent (:8082).
 * All task delegations execute over the official A2A JSON-RPC 2.0 transport ({@code POST /}).
 */
@Path("/api/a2a")
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
}
