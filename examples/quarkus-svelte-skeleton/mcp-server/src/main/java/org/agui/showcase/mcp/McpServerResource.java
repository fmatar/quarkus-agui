package org.agui.showcase.mcp;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;

@Path("/api/mcp")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class McpServerResource {

    private final NomadMcpConciergeService conciergeService;

    @Inject
    public McpServerResource(NomadMcpConciergeService conciergeService) {
        this.conciergeService = conciergeService;
    }

    @GET
    @Path("/health")
    public Map<String, Object> health() {
        return conciergeService.checkHealth();
    }

    @GET
    @Path("/tools")
    public Map<String, Object> listTools() {
        return Map.of(
                "serverName", NomadMcpConciergeService.SERVER_NAME,
                "protocol", "mcp/2025-03-26",
                "tools", conciergeService.listTools());
    }

    @POST
    @Path("/tools/call")
    public McpToolInvocationResponse callTool(McpToolInvocationRequest request) {
        return conciergeService.dispatchTool(request);
    }

    @GET
    @Path("/resources/itinerary")
    public Map<String, Object> getItinerary() {
        return conciergeService.getActiveTripSnapshot();
    }

    @GET
    @Path("/resources/city/{city}")
    public Map<String, Object> getCityDossier(@PathParam("city") String city) {
        return conciergeService.getCityDossier(city);
    }

    @GET
    @Path("/resources/preferences")
    public Map<String, Object> getPreferences() {
        return conciergeService.getTravelerPreferences();
    }

    @POST
    @Path("/resources/preferences")
    public Map<String, Object> updatePreferences(Map<String, Object> updates) {
        return conciergeService.updateTravelerPreferences(updates);
    }

    @GET
    @Path("/prompts")
    public Map<String, Object> listPrompts() {
        return Map.of(
                "serverName", NomadMcpConciergeService.SERVER_NAME,
                "prompts", conciergeService.listPrompts());
    }

    @GET
    @Path("/prompts/complete")
    public Map<String, Object> completePromptArgument(@QueryParam("prefix") String prefix) {
        List<String> values = conciergeService.completeCityArgument(prefix);
        return Map.of(
                "serverName", NomadMcpConciergeService.SERVER_NAME,
                "argument", "city",
                "values", values,
                "total", values.size());
    }

    @POST
    @Path("/sampling")
    public Map<String, Object> sampleAcousticMood(Map<String, Object> request) {
        String city = request == null || request.get("city") == null ? null : String.valueOf(request.get("city"));
        String condition = request == null || request.get("weatherCondition") == null ? null : String.valueOf(request.get("weatherCondition"));
        String genre = request == null || request.get("genre") == null ? null : String.valueOf(request.get("genre"));
        return conciergeService.sampleAcousticMoodProfile(city, condition, genre);
    }
}
