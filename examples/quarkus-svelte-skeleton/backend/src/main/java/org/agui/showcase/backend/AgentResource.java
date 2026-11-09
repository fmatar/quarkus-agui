package org.agui.showcase.backend;

import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import org.agui.core.adapters.AgUiSseFormatter;
import org.jboss.resteasy.reactive.RestStreamElementType;

/**
 * REST & SSE Endpoint for the Nomad Travel Concierge Quarkus Orchestrator (:8080).
 */
@Path("/api/agent")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AgentResource {

    private final NomadConciergeOrchestratorService orchestrator;

    @Inject
    public AgentResource(NomadConciergeOrchestratorService orchestrator) {
        this.orchestrator = orchestrator;
    }

    @GET
    @Path("/health")
    public Map<String, Object> health() {
        return orchestrator.checkSystemHealth();
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

    @POST
    @Path("/params")
    public Map<String, Object> updateParams(Map<String, Object> body) {
        String threadId = body != null && body.get("threadId") instanceof String t ? t : "thread-nomad-1";
        Integer nights = body != null && body.get("nights") instanceof Number n ? n.intValue() : null;
        String homeCurrency = body != null && body.get("homeCurrency") instanceof String c ? c : null;
        return orchestrator.updateTripParams(threadId, nights, homeCurrency);
    }

    @POST
    @Path("/memory/reset")
    public Map<String, Object> resetMemory(Map<String, Object> body) {
        String threadId = body != null && body.get("threadId") instanceof String t ? t : "thread-nomad-1";
        return orchestrator.resetMemory(threadId);
    }

    @POST
    @Path("/cancel")
    public Map<String, Object> cancelRun(Map<String, Object> body) {
        String threadId = body != null && body.get("threadId") instanceof String t ? t : "thread-nomad-1";
        orchestrator.cancelRun(threadId);
        return Map.of("threadId", threadId, "status", "cancelled");
    }

    @GET
    @Path("/complete")
    public Map<String, Object> completeCity(@QueryParam("prefix") String prefix) {
        List<String> values = orchestrator.completeCityPrefix(prefix);
        return Map.of("argument", "city", "values", values);
    }

    static String unwrapSseDataPayload(String rawFrame) {
        return AgUiSseFormatter.unwrapSseData(rawFrame);
    }
}
