package org.agui.showcase.a2a;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class A2aAgentResource {

    private final SpecialistAuditAgentService agentService;

    @Inject
    public A2aAgentResource(SpecialistAuditAgentService agentService) {
        this.agentService = agentService;
    }

    @GET
    @Path("/.well-known/agent.json")
    public AgentCard getAgentCard() {
        return agentService.getAgentCard();
    }

    @POST
    @Path("/api/a2a/tasks/send")
    public A2aTaskResponse sendTask(A2aTaskRequest request) {
        return agentService.executeTask(request);
    }
}
