package org.agui.showcase.backend;

import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;
import org.agui.core.adapters.AgUiSseFormatter;
import org.jboss.resteasy.reactive.RestStreamElementType;

/**
 * REST & SSE Endpoint for the OpsPilot Quarkus Orchestrator (:8080).
 */
@Path("/api/agent")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AgentResource {

    private final OpsPilotOrchestratorService orchestrator;

    @Inject
    public AgentResource(OpsPilotOrchestratorService orchestrator) {
        this.orchestrator = orchestrator;
    }

    @GET
    @Path("/catalog")
    public Map<String, Object> catalog() {
        return orchestrator.getCatalog();
    }

    @POST
    @Path("/chat")
    public AgentChatResponse chat(AgentRunRequest request) {
        return orchestrator.executeChat(request);
    }

    @POST
    @Path("/run")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> run(AgentRunRequest request) {
        return orchestrator.streamRun(request).map(AgentResource::unwrapSseDataPayload);
    }

    @POST
    @Path("/approve")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> approve(AgentApprovalRequest approval) {
        return orchestrator.approvePendingAction(approval).map(AgentResource::unwrapSseDataPayload);
    }

    static String unwrapSseDataPayload(String rawFrame) {
        return AgUiSseFormatter.unwrapSseData(rawFrame);
    }
}
