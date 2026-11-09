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

    private final ClusterTelemetryService telemetryService;

    @Inject
    public McpServerResource(ClusterTelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @GET
    @Path("/tools")
    public Map<String, Object> listTools() {
        return Map.of(
            "serverName", ClusterTelemetryService.SERVER_NAME,
            "protocol", "mcp/1.0",
            "tools", telemetryService.listTools()
        );
    }

    @POST
    @Path("/tools/call")
    public McpToolInvocationResponse callTool(McpToolInvocationRequest request) {
        return telemetryService.dispatchTool(request);
    }

    @GET
    @Path("/resources/itinerary")
    public Map<String, Object> getItinerary() {
        return telemetryService.getActiveTripSnapshot();
    }

    @GET
    @Path("/resources/city/{city}")
    public Map<String, Object> getCityDossier(@PathParam("city") String city) {
        return telemetryService.getCityDossier(city);
    }

    @GET
    @Path("/resources/preferences")
    public Map<String, Object> getPreferences() {
        return telemetryService.getTravelerPreferences();
    }

    @POST
    @Path("/resources/preferences")
    public Map<String, Object> updatePreferences(Map<String, Object> updates) {
        return telemetryService.updateTravelerPreferences(updates);
    }

    @GET
    @Path("/prompts")
    public Map<String, Object> listPrompts() {
        return Map.of(
            "serverName", ClusterTelemetryService.SERVER_NAME,
            "prompts", telemetryService.listPrompts()
        );
    }

    @GET
    @Path("/prompts/complete")
    public Map<String, Object> completePromptArgument(@QueryParam("prefix") String prefix) {
        List<String> values = telemetryService.completeCityArgument(prefix);
        return Map.of(
            "serverName", ClusterTelemetryService.SERVER_NAME,
            "argument", "city",
            "values", values,
            "total", values.size()
        );
    }

    @POST
    @Path("/sampling")
    public Map<String, Object> sampleAcousticMood(Map<String, Object> request) {
        String city = request == null ? "Kyoto" : String.valueOf(request.getOrDefault("city", "Kyoto"));
        String condition = request == null ? "Clear Sky" : String.valueOf(request.getOrDefault("weatherCondition", "Clear Sky"));
        String genre = request == null ? "traditional" : String.valueOf(request.getOrDefault("genre", "traditional"));
        return telemetryService.sampleAcousticMoodProfile(city, condition, genre);
    }
}
