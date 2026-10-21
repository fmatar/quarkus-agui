package org.agui.showcase.mcp;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

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
    @Path("/resources/topology")
    public Map<String, Object> getTopology() {
        return telemetryService.getTopologySnapshot();
    }
}
