package org.agui.quarkus.it.adapters;

import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.agui.core.domain.A2aHandoffPayload;
import org.agui.core.domain.AgUiEvent;
import org.agui.core.domain.McpToolMetadata;
import org.agui.quarkus.runtime.adapters.AgUiA2aBridge;
import org.agui.quarkus.runtime.adapters.AgUiBridge;
import org.agui.quarkus.runtime.adapters.AgUiMcpBridge;
import org.jboss.resteasy.reactive.RestStreamElementType;

/**
 * Integration test endpoint verifying CDI injection of {@link AgUiBridge}, {@link AgUiMcpBridge}, and {@link AgUiA2aBridge}.
 */
@Path("/api/it/agui")
public class AgUiIntegrationResource {

    private final AgUiBridge bridge;
    private final AgUiMcpBridge mcpBridge;
    private final AgUiA2aBridge a2aBridge;

    @Inject
    public AgUiIntegrationResource(AgUiBridge bridge, AgUiMcpBridge mcpBridge, AgUiA2aBridge a2aBridge) {
        this.bridge = bridge;
        this.mcpBridge = mcpBridge;
        this.a2aBridge = a2aBridge;
    }

    @GET
    @Path("/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> stream(@QueryParam("threadId") String threadId) {
        String tid = (threadId == null || threadId.isBlank()) ? "thread-it" : threadId;
        List<AgUiEvent<?>> events = new ArrayList<>();
        events.addAll(mcpBridge.recordToolExecution(
                tid,
                "call-it-1",
                new McpToolMetadata("mcp-it", "inspectClusterHealth", "http-sse"),
                Map.of("region", "us-east-1"),
                Map.of("status", "HEALTHY")));
        events.addAll(a2aBridge.recordDelegation(
                tid,
                new A2aHandoffPayload("a2a-it-1", "Sentinel", "audit-clean-architecture", "OK", Map.of())));
        events.addAll(bridge.buildTokenEvents(tid, List.of("Extension ", "verified!")));
        return Multi.createFrom().iterable(events).map(bridge::toSseServerSentEventData);
    }
}
