package org.agui.showcase.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpsPilotOrchestratorServiceTest {

    private McpGatewayClient mcpClient;
    private A2aGatewayClient a2aClient;
    private OpsPilotOrchestratorService service;
    private HttpServer mockOllamaServer;
    private int statusToReturn = 200;

    @BeforeEach
    void setUp() throws IOException {
        mcpClient = new McpGatewayClient();
        a2aClient = new A2aGatewayClient();
        service = new OpsPilotOrchestratorService(mcpClient, a2aClient);

        mockOllamaServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mockOllamaServer.createContext("/api/chat", exchange -> {
            byte[] body = ("{\"message\":{\"content\":\"Hello \"}}\n"
                    + "\n"
                    + "{\"message\":{\"content\":\"from Nomad Ollama!\"}}\n"
                    + "{\"done\":true}\n").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusToReturn, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        mockOllamaServer.start();
        service.ollamaBaseUrl = "http://127.0.0.1:" + mockOllamaServer.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (mockOllamaServer != null) {
            mockOllamaServer.stop(0);
        }
    }

    @Test
    void catalogReturnsMcpToolsA2aAgentCardAndTripState() {
        Map<String, Object> catalog = service.getCatalog();
        assertEquals("deterministic", catalog.get("llmMode"));
        assertEquals("org.agui:quarkus-ag-ui", catalog.get("extension"));
        assertEquals("Nomad-Concierge-Orchestrator", catalog.get("orchestrator"));
        assertEquals("llama3.2:latest", catalog.get("defaultOllamaModel"));
        assertNotNull(service.agUiBridge());
        assertNotNull(service.agUiMcpBridge());
        assertNotNull(service.agUiA2aBridge());

        List<?> mcpTools = (List<?>) catalog.get("mcpTools");
        assertNotNull(mcpTools);
        assertEquals(3, mcpTools.size());

        Map<?, ?> a2aAgent = (Map<?, ?>) catalog.get("a2aAgent");
        assertNotNull(a2aAgent);
        assertEquals("Nomad-A2A-Specialist", a2aAgent.get("name"));

        Map<?, ?> clusterState = (Map<?, ?>) catalog.get("clusterState");
        assertNotNull(clusterState);
        assertEquals("itinerary://active-trip", clusterState.get("uri"));
        assertNotNull(clusterState.get("nomadTrip"));
    }

    @Test
    void executeChatRunsMcpWeatherCultureAndA2aBudgetAdvisoryAndHandlesNullRequest() {
        AgentChatResponse nullRes = service.executeChat(null);
        assertEquals("thread-default", nullRes.threadId());
        assertEquals("COMPLETED", nullRes.status());

        AgentRunRequest req = new AgentRunRequest(
                "thread-1",
                "Curate a 4-night architectural and koto music escape in Kyoto",
                Map.of("city", "Kyoto"),
                "deterministic");

        AgentChatResponse res = service.executeChat(req);
        assertEquals("thread-1", res.threadId());
        assertEquals("COMPLETED", res.status());
        assertFalse(res.mcpCalls().isEmpty());
        assertEquals("fetchCityWeather", res.mcpCalls().getFirst().get("toolName"));
        assertFalse(res.a2aHandoffs().isEmpty());
        assertEquals("convert-and-audit-budget", res.a2aHandoffs().getFirst().get("skillId"));
        assertTrue(res.reply().contains("Kyoto"));
    }

    @Test
    void executeChatHandlesItineraryUpdatesAndNeighborhoodCadence() {
        AgentRunRequest req = new AgentRunRequest(
                "thread-walk",
                "Update our 5-night itinerary and neighborhood walking cadence in Lisbon with Fado music",
                Map.of(),
                "deterministic");

        AgentChatResponse res = service.executeChat(req);
        assertEquals("COMPLETED", res.status());
        assertTrue(res.mcpCalls().stream().anyMatch(c -> "updateTripItinerary".equals(c.get("toolName"))));
        assertTrue(res.a2aHandoffs().stream().anyMatch(h -> "curate-neighborhood-cadence".equals(h.get("skillId"))));
        assertTrue(res.reply().contains("Lisbon"));
    }

    @Test
    void mcpAndA2aClientsHandlePromptTemplatesResourcesAndUnsupportedOperations() {
        Map<String, Object> walkTpl = mcpClient.getPromptTemplate("local-music-and-food-walk", Map.of("city", "Lisbon", "genre", "fado"));
        assertEquals("local-music-and-food-walk", walkTpl.get("promptName"));
        assertTrue(String.valueOf(walkTpl.get("expandedPrompt")).contains("Lisbon"));

        Map<String, Object> activeTripRes = mcpClient.readResource("itinerary://active-trip");
        assertEquals("itinerary://active-trip", activeTripRes.get("uri"));

        assertThrows(IllegalArgumentException.class, () -> mcpClient.callTool("unknownTool", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> a2aClient.delegateTask("t-err", "unknown-skill", "Kyoto"));
    }

    @Test
    void streamRunEmitsFormattedSseFramesInDeterministicOrder() {
        AgentRunRequest req = new AgentRunRequest(
                "thread-sse-1",
                "Explore Reykjavik basalt architecture and aurora sky",
                Map.of(),
                "deterministic");

        List<String> frames = service.streamRun(req).collect().asList().await().indefinitely();
        assertFalse(frames.isEmpty());
        assertTrue(frames.getFirst().startsWith("data: "));
        assertTrue(frames.getFirst().endsWith("\n\n"));
        assertTrue(frames.getFirst().contains("\"type\":\"RUN_STARTED\""));
        assertTrue(frames.stream().anyMatch(f -> f.contains("\"type\":\"STATE_SNAPSHOT\"")));
        assertTrue(frames.stream().anyMatch(f -> f.contains("\"type\":\"TOOL_CALL\"")));
        assertTrue(frames.stream().anyMatch(f -> f.contains("\"type\":\"TOOL_RESULT\"")));
        assertTrue(frames.stream().anyMatch(f -> f.contains("\"type\":\"STATE_DELTA\"")));
        assertTrue(frames.stream().anyMatch(f -> f.contains("\"type\":\"TEXT_DELTA\"")));
        assertTrue(frames.getLast().contains("\"type\":\"RUN_FINISHED\""));
    }

    @Test
    void sanctuaryBookingAboveThresholdTriggersHitlInterruptAndApprovalResumesRun() {
        AgentRunRequest hitlBookingReq = new AgentRunRequest(
                "thread-hitl-1",
                "Reserve Hoshinoya Arashiyama Pavilion in Kyoto for 4 nights ($1840)",
                Map.of("city", "Kyoto"),
                "deterministic");

        List<String> interruptedFrames = service.streamRun(hitlBookingReq).collect().asList().await().indefinitely();
        assertTrue(interruptedFrames.getLast().contains("\"status\":\"interrupted\""));
        assertTrue(interruptedFrames.getLast().contains("\"approvalRequired\":true"));

        AgentApprovalRequest approveReq = new AgentApprovalRequest("thread-hitl-1", true, null);
        List<String> approvedFrames = service.approvePendingAction(approveReq).collect().asList().await().indefinitely();
        assertTrue(approvedFrames.stream().anyMatch(f -> f.contains("\"type\":\"TOOL_RESULT\"")));
        assertTrue(approvedFrames.stream().anyMatch(f -> f.contains("\"type\":\"STATE_DELTA\"")));
        assertTrue(approvedFrames.getLast().contains("\"status\":\"completed\""));

        AgentApprovalRequest rejectReq = new AgentApprovalRequest("thread-none", false, "Declined by traveler");
        List<String> rejectedFrames = service.approvePendingAction(rejectReq).collect().asList().await().indefinitely();
        assertTrue(rejectedFrames.getLast().contains("\"status\":\"cancelled\""));

        List<String> nullApproveFrames = service.approvePendingAction(null).collect().asList().await().indefinitely();
        assertTrue(nullApproveFrames.getLast().contains("\"status\":\"cancelled\""));
    }

    @Test
    void streamRunLiveOllamaModeStreamsRealNdjsonAndCoversAllBranches() {
        // 1. Live Ollama with Lisbon Fado & FX
        AgentRunRequest liveReq = new AgentRunRequest(
                "thread-ollama-1",
                "Curate an evening Fado and azulejo walk in Lisbon",
                Map.of(),
                "ollama:llama3.2:latest");

        List<String> frames = service.streamRun(liveReq).collect().asList().await().indefinitely();
        assertFalse(frames.isEmpty());
        assertTrue(frames.getFirst().contains("\"llmMode\":\"ollama:llama3.2:latest\""));
        assertTrue(frames.stream().anyMatch(f -> f.contains("\"type\":\"TOOL_CALL\"")));
        assertTrue(frames.stream().anyMatch(f -> f.contains("\"type\":\"STEP_FINISHED\"")));
        assertTrue(frames.stream().anyMatch(f -> f.contains("\"type\":\"TEXT_DELTA\"")));
        assertTrue(frames.getLast().contains("\"status\":\"completed\""));

        // 2. Live Ollama with HTTP 500 fallback
        statusToReturn = 500;
        AgentRunRequest fallbackReq = new AgentRunRequest(
                "",
                "Explore jazz listening bars in Tokyo",
                null,
                "ollama:");
        List<String> fallbackFrames = service.streamRun(fallbackReq).collect().asList().await().indefinitely();
        assertTrue(fallbackFrames.stream().anyMatch(f -> f.contains("\"type\":\"TEXT_DELTA\"")));
        assertTrue(fallbackFrames.getLast().contains("\"status\":\"completed\""));

        // 3. Live Ollama HITL sanctuary reservation interrupt
        AgentRunRequest liveHitl = new AgentRunRequest(
                "thread-ollama-hitl",
                "Reserve The Retreat at Blue Lagoon Moss Villa in Reykjavik for 4 nights",
                Map.of(),
                "ollama");
        List<String> hitlFrames = service.streamRun(liveHitl).collect().asList().await().indefinitely();
        assertTrue(hitlFrames.getLast().contains("\"status\":\"interrupted\""));

        assertEquals("", AgentResource.unwrapSseDataPayload(null));
        assertEquals("{\"id\":\"1\"}", AgentResource.unwrapSseDataPayload("data: {\"id\":\"1\"}\n\n"));
    }

    @Test
    void nomadDestinationAndSlashCommandWorkflowsEmitCallTypesMediaInteractionAndHitlBooking() {
        // 1. /kyoto cultural concierge workflow
        AgentRunRequest kyotoReq = new AgentRunRequest(
                "thread-nomad-kyoto",
                "/kyoto Curate a 4-night cultural and musical escape in Kyoto",
                Map.of("city", "Kyoto"),
                "deterministic");
        List<String> kyotoFrames = service.streamRun(kyotoReq).collect().asList().await().indefinitely();
        assertTrue(kyotoFrames.stream().anyMatch(f -> f.contains("\"callType\":\"mcp:prompt\"")));
        assertTrue(kyotoFrames.stream().anyMatch(f -> f.contains("\"callType\":\"mcp:resource\"")));
        assertTrue(kyotoFrames.stream().anyMatch(f -> f.contains("\"callType\":\"mcp:tool\"")));
        assertTrue(kyotoFrames.stream().anyMatch(f -> f.contains("\"callType\":\"tool:function\"")));
        assertTrue(kyotoFrames.stream().anyMatch(f -> f.contains("\"callType\":\"tool:ui\"")));
        assertTrue(kyotoFrames.stream().anyMatch(f -> f.contains("\"callType\":\"a2a:discovery\"")));
        assertTrue(kyotoFrames.stream().anyMatch(f -> f.contains("\"callType\":\"a2a:task\"")));
        assertTrue(kyotoFrames.stream().anyMatch(f -> f.contains("\"callType\":\"a2a:artifact\"")));
        assertTrue(kyotoFrames.stream().anyMatch(f -> f.contains("\"media\":")));
        assertTrue(kyotoFrames.stream().anyMatch(f -> f.contains("\"interaction\":")));
        assertTrue(kyotoFrames.stream().anyMatch(f -> f.contains("\"sources\":")));
        assertTrue(kyotoFrames.stream().anyMatch(f -> f.contains("\"followUps\":")));

        // 2. /lisbon and /reykjavik and /choices slash commands
        List<String> lisbonFrames = service.streamRun(new AgentRunRequest(
                "thread-lisbon", "/lisbon Fado & coastal walk", Map.of(), "deterministic")).collect().asList().await().indefinitely();
        assertTrue(lisbonFrames.stream().anyMatch(f -> f.contains("Lisbon")));

        List<String> reykjavikFrames = service.streamRun(new AgentRunRequest(
                "thread-reykjavik", "/reykjavik Aurora & geothermal", Map.of(), "deterministic")).collect().asList().await().indefinitely();
        assertTrue(reykjavikFrames.stream().anyMatch(f -> f.contains("Reykjavik")));

        List<String> choicesFrames = service.streamRun(new AgentRunRequest(
                "thread-choices", "/choices multi_select neighborhoods", Map.of(), "deterministic")).collect().asList().await().indefinitely();
        assertTrue(choicesFrames.stream().anyMatch(f -> f.contains("multi_select")));

        // 3. Nomad HITL luxury sanctuary booking gate (/hitl or reserve > $1,500) + approval with modifiedArgs
        AgentRunRequest hitlBookingReq = new AgentRunRequest(
                "thread-nomad-hitl",
                "/hitl Reserve Hoshinoya Arashiyama Pavilion in Kyoto for 4 nights ($1840)",
                Map.of("city", "Kyoto"),
                "deterministic");
        List<String> hitlFrames = service.streamRun(hitlBookingReq).collect().asList().await().indefinitely();
        assertTrue(hitlFrames.stream().anyMatch(f -> f.contains("\"callType\":\"hitl:approval\"")));
        assertTrue(hitlFrames.getLast().contains("\"status\":\"interrupted\""));

        AgentApprovalRequest approveWithMods = new AgentApprovalRequest(
                "thread-nomad-hitl",
                true,
                "Confirmed 5 nights with private tea pavilion",
                Map.of("nights", 5, "costUsd", 2150.0, "city", "Kyoto"));
        List<String> resumedFrames = service.approvePendingAction(approveWithMods).collect().asList().await().indefinitely();
        assertTrue(resumedFrames.stream().anyMatch(f -> f.contains("updateTripItinerary")));
        assertTrue(resumedFrames.getLast().contains("\"status\":\"completed\""));

        // 4. Free-form chat prompt routes to Nomad Concierge with distinct A2A taskIds
        AgentChatResponse freeFormChat = service.executeChat(new AgentRunRequest(
                "thread-freeform",
                "Hello! Plan a weekend in Tokyo with local music",
                Map.of("nomadTrip", Map.of("activeCity", "Lisbon")),
                "deterministic"));
        assertEquals(2, freeFormChat.a2aHandoffs().size());
        assertNotNull(freeFormChat.a2aHandoffs().get(0).get("taskId"));
        assertNotNull(freeFormChat.a2aHandoffs().get(1).get("taskId"));
        assertFalse(freeFormChat.a2aHandoffs().get(0).get("taskId").equals(freeFormChat.a2aHandoffs().get(1).get("taskId")));
        assertTrue(freeFormChat.reply().contains("Tokyo"));

        // 5. Natural language city extraction ("in Oaxaca") + weather-focused prompt
        AgentChatResponse oaxacaWeather = service.executeChat(new AgentRunRequest(
                "thread-oaxaca",
                "What should I pack for the weather in Oaxaca?",
                Map.of(),
                "deterministic"));
        assertTrue(oaxacaWeather.reply().contains("Oaxaca"));
        assertTrue(oaxacaWeather.reply().contains("Meteorological"));
    }
}
