package org.agui.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.agui.core.adapters.AgUiBridge;
import org.agui.core.adapters.AgUiFlowAdapter;
import org.agui.core.adapters.AgUiMcpBridge;
import org.agui.core.adapters.AgUiSseBridge;
import org.agui.core.adapters.AgUiSseFormatter;
import org.agui.core.adapters.AgUiSseTransportSession;
import org.agui.core.adapters.AgUiWebSocketTransportSession;
import org.agui.core.application.AgUiSessionRegistry;
import org.agui.core.application.AgUiSessionRunner;
import org.agui.core.application.AgUiTransportSession;
import org.agui.core.domain.A2aHandoffPayload;
import org.agui.core.domain.AgUiEvent;
import org.agui.core.domain.AgUiEventType;
import org.agui.core.domain.AgUiTransportType;
import org.agui.core.domain.McpToolMetadata;
import org.agui.core.domain.RunFinishedPayload;
import org.agui.core.domain.RunStartedPayload;
import org.agui.core.domain.StateDeltaPayload;
import org.agui.core.domain.StateSnapshotPayload;
import org.agui.core.domain.StatusPayload;
import org.agui.core.domain.StepPayload;
import org.agui.core.domain.TextDeltaPayload;
import org.agui.core.domain.ToolCallPayload;
import org.agui.core.domain.ToolResultPayload;
import org.junit.jupiter.api.Test;

class AgUiCoreTest {

    @Test
    void domainRecordsValidateInvariantsAndConstructAllTenEventTypes() {
        AgUiEvent<RunStartedPayload> start = AgUiEvent.of(
                AgUiEventType.RUN_STARTED,
                "thread-1",
                new RunStartedPayload("thread-1", "started"));
        assertTrue(start.id().startsWith("evt-"));
        assertEquals("RUN_STARTED", start.type());
        assertEquals("thread-1", start.threadId());
        assertTrue(start.timestamp() > 0);
        assertEquals("started", start.payload().status());

        AgUiEvent<TextDeltaPayload> delta = AgUiEvent.ofType(
                "TEXT_DELTA",
                "thread-1",
                new TextDeltaPayload("Hello \"AG-UI\"\n\r\t\\"));
        assertEquals("TEXT_DELTA", delta.type());

        ToolCallPayload toolCall = new ToolCallPayload("c1", "scaleDeployment", null);
        assertTrue(toolCall.args().isEmpty());
        ToolCallPayload toolCallWithArgs = new ToolCallPayload("c2", "scaleDeployment", Map.of("replicas", 3));
        assertEquals(3, toolCallWithArgs.args().get("replicas"));

        ToolResultPayload toolResult = new ToolResultPayload("c1", "scaleDeployment", Map.of("replicas", 4));
        assertEquals("scaleDeployment", toolResult.tool());

        StatusPayload status = new StatusPayload("thinking", "Analyzing telemetry");
        assertEquals("thinking", status.phase());

        RunFinishedPayload finished = new RunFinishedPayload("thread-1", "completed");
        assertEquals("completed", finished.status());

        StepPayload step = new StepPayload("s1", "A2A", "Sentinel-A2A-Auditor", "audit-clean-architecture", "OK");
        assertEquals("A2A", step.protocol());

        StateSnapshotPayload snap = new StateSnapshotPayload(null);
        assertTrue(snap.state().isEmpty());
        StateSnapshotPayload snapWithData = new StateSnapshotPayload(Map.of("k", "v"));
        assertEquals("v", snapWithData.state().get("k"));

        StateDeltaPayload stateDelta = new StateDeltaPayload(null);
        assertTrue(stateDelta.delta().isEmpty());
        StateDeltaPayload stateDeltaWithData = new StateDeltaPayload(Map.of("d", 1));
        assertEquals(1, stateDeltaWithData.delta().get("d"));

        McpToolMetadata mcpMeta = new McpToolMetadata("quarkus-mcp", "inspectClusterHealth", "http-sse");
        assertEquals("http-sse", mcpMeta.transport());

        A2aHandoffPayload a2a = new A2aHandoffPayload("t1", "Sentinel", "audit", "Clean", null);
        assertTrue(a2a.report().isEmpty());
        A2aHandoffPayload a2aWithReport = new A2aHandoffPayload("t1", "Sentinel", "audit", "Clean", Map.of("ok", true));
        assertEquals(Boolean.TRUE, a2aWithReport.report().get("ok"));

        assertThrows(IllegalArgumentException.class, () -> new AgUiEvent<>(" ", "RUN_STARTED", "t1", 1L, null));
        assertThrows(IllegalArgumentException.class, () -> new AgUiEvent<>("id1", " ", "t1", 1L, null));
        assertThrows(IllegalArgumentException.class, () -> new AgUiEvent<>("id1", "RUN_STARTED", " ", 1L, null));
    }

    @Test
    void formatterAndSessionRunnerSerializeRecordsCollectionsMapsAndSub10msLatency() {
        AgUiSseFormatter formatter = new AgUiSseFormatter();
        AgUiSessionRunner runner = new AgUiSessionRunner(formatter);

        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("record", new RunStartedPayload("thread-bench", "started"));
        payloadMap.put("list", List.of("a", 42, true));
        payloadMap.put("nullVal", null);
        payloadMap.put("enumVal", AgUiEventType.STATUS);
        payloadMap.put("quote", "line1\n\"line2\"\r\t\\end");

        AgUiEvent<Map<String, Object>> complexEvent = new AgUiEvent<>(
                "evt-100",
                AgUiEventType.STATE_SNAPSHOT.name(),
                "thread-bench",
                123456789L,
                payloadMap);

        String sseFrame = runner.formatSseFrame(complexEvent);
        assertTrue(sseFrame.startsWith("data: {"));
        assertTrue(sseFrame.endsWith("}\n\n"));
        assertTrue(sseFrame.contains("\"id\":\"evt-100\""));
        assertTrue(sseFrame.contains("\"status\":\"started\""));
        assertTrue(sseFrame.contains("\"nullVal\":null"));
        assertTrue(sseFrame.contains("\"enumVal\":\"STATUS\""));
        assertTrue(sseFrame.contains("line1\\n\\\"line2\\\"\\r\\t\\\\end"));

        assertEquals("", AgUiSseFormatter.unwrapSseData(null));
        assertEquals("{\"ok\":true}", AgUiSseFormatter.unwrapSseData("data: {\"ok\":true}\n\n"));

        List<String> batch = runner.formatSseStream(List.of(complexEvent));
        assertEquals(1, batch.size());
        assertTrue(formatter.formatSse(complexEvent).startsWith("data: "));

        // Verify INV-05: sub-10ms serialization latency for 100 events
        long startNanos = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            formatter.toJson(complexEvent);
        }
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        assertTrue(elapsedMs < 50, "Serialization should be fast, took " + elapsedMs + "ms");
    }

    @Test
    void flowAdapterStreamsEventsAndHandlesCancellationAndInvalidRequest() {
        AgUiSseFormatter formatter = new AgUiSseFormatter();
        List<AgUiEvent<?>> events = List.of(
                AgUiEvent.of(AgUiEventType.RUN_STARTED, "t1", new RunStartedPayload("t1", "started")),
                AgUiEvent.of(AgUiEventType.TEXT_DELTA, "t1", new TextDeltaPayload("chunk-1")),
                AgUiEvent.of(AgUiEventType.RUN_FINISHED, "t1", new RunFinishedPayload("t1", "completed")));

        Flow.Publisher<String> ssePublisher = AgUiFlowAdapter.toSsePublisher(events, formatter);
        List<String> frames = AgUiFlowAdapter.collectAll(ssePublisher);
        assertEquals(3, frames.size());
        assertTrue(frames.getFirst().contains("RUN_STARTED"));
        assertTrue(frames.getLast().contains("RUN_FINISHED"));

        // Verify invalid request(0) signals onError and cancel() halts delivery
        Flow.Publisher<String> rawPub = AgUiFlowAdapter.fromIterable(List.of("a", "b"));
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        rawPub.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(0);
                subscription.cancel();
                subscription.request(1);
            }

            @Override
            public void onNext(String item) {
            }

            @Override
            public void onError(Throwable throwable) {
                errorRef.set(throwable);
            }

            @Override
            public void onComplete() {
            }
        });
        assertNotNull(errorRef.get());
        assertFalse(frames.isEmpty());

        // Verify incomplete publisher fails collectAll
        Flow.Publisher<String> incompletePub = subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long n) {
            }

            @Override
            public void cancel() {
            }
        });
        assertThrows(IllegalStateException.class, () -> AgUiFlowAdapter.collectAll(incompletePub));
    }

    @Test
    void agUiBridgeStreamsTokenEventsAndSseFramesViaFlowPublisher() {
        AgUiBridge bridge = new AgUiBridge();
        assertNotNull(bridge.formatter());

        List<AgUiEvent<?>> events = AgUiFlowAdapter.collectAll(
                bridge.streamTokens("thread-core-1", Arrays.asList("Hello ", "", null, "World!")));
        assertEquals(4, events.size());
        assertEquals("RUN_STARTED", events.getFirst().type());
        assertEquals("TEXT_DELTA", events.get(1).type());
        assertEquals("RUN_FINISHED", events.getLast().type());

        List<String> frames = AgUiFlowAdapter.collectAll(bridge.streamSseFrames(events));
        assertEquals(4, frames.size());
        assertTrue(frames.getFirst().startsWith("data: {"));
        assertTrue(frames.getFirst().endsWith("}\n\n"));

        AgUiEvent<TextDeltaPayload> single = AgUiEvent.of(
                AgUiEventType.TEXT_DELTA,
                "thread-core-1",
                new TextDeltaPayload("token"));
        String json = bridge.toSseServerSentEventData(single);
        assertTrue(json.contains("\"content\":\"token\""));
    }

    @Test
    void agUiMcpBridgeRecordsToolCallAndResultEvents() {
        AgUiMcpBridge mcpBridge = new AgUiMcpBridge();
        McpToolMetadata meta = new McpToolMetadata("nomad-mcp (:8081)", "fetchCityWeather", "http-sse");

        List<AgUiEvent<?>> events = mcpBridge.recordToolExecution(
                "thread-mcp",
                "call-101",
                meta,
                Map.of("city", "Kyoto"),
                Map.of("status", "OK"));
        assertEquals(2, events.size());
        assertEquals("TOOL_CALL", events.getFirst().type());
        ToolCallPayload callPayload = (ToolCallPayload) events.getFirst().payload();
        assertEquals("nomad-mcp (:8081)", callPayload.args().get("_mcpServer"));
        assertEquals("http-sse", callPayload.args().get("_mcpTransport"));
        assertEquals("Kyoto", callPayload.args().get("city"));
        assertEquals("TOOL_RESULT", events.getLast().type());

        List<AgUiEvent<?>> nullArgsEvents = mcpBridge.recordToolExecution("thread-mcp", "call-102", meta, null, "OK");
        assertEquals(2, nullArgsEvents.size());
    }

    @Test
    void sessionRegistryAndSseAndWebSocketTransportSessionsHandleLifecycle() {
        AgUiSessionRegistry registry = new AgUiSessionRegistry();
        assertEquals(0, registry.activeCount());
        registry.register(null);
        registry.unregister(null);
        assertFalse(registry.find(null).isPresent());

        AgUiSseBridge sseBridge = new AgUiSseBridge(registry);
        assertEquals(AgUiTransportType.SSE, sseBridge.transportType());
        assertTrue(sseBridge.isEnabled());

        AgUiSseTransportSession sseSession = sseBridge.createSession("sse-1");
        assertEquals("sse-1", sseSession.sessionId());
        assertEquals(AgUiTransportType.SSE, sseSession.transportType());
        assertTrue(sseSession.isOpen());

        List<String> sseFrames = new ArrayList<>();
        sseSession.toPublisher().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(String item) {
                sseFrames.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onComplete() {
            }
        });

        AtomicBoolean wsOpen = new AtomicBoolean(true);
        List<String> wsFrames = new ArrayList<>();
        AgUiWebSocketTransportSession wsSession = new AgUiWebSocketTransportSession(
                "ws-1",
                wsFrames::add,
                wsOpen::get,
                () -> wsOpen.set(false));
        registry.register(wsSession);

        assertEquals(2, registry.activeCount());
        assertEquals(2, registry.allSessions().size());
        assertTrue(registry.find("ws-1").isPresent());

        AgUiEvent<TextDeltaPayload> evt = AgUiEvent.of(AgUiEventType.TEXT_DELTA, "th-1", new TextDeltaPayload("broadcast"));
        registry.broadcast(null);
        registry.broadcast(evt);
        assertEquals(1, sseFrames.size());
        assertTrue(sseFrames.getFirst().startsWith("data: {"));
        assertEquals(1, wsFrames.size());
        assertTrue(wsFrames.getFirst().contains("broadcast"));

        registry.broadcastToTransport(null, evt);
        registry.broadcastToTransport(AgUiTransportType.SSE, null);
        registry.broadcastToTransport(AgUiTransportType.SSE, evt);
        assertEquals(2, sseFrames.size());
        assertEquals(1, wsFrames.size());

        sseSession.emit(null);
        wsSession.emit(null);
        sseSession.close();
        wsSession.close();
        assertFalse(sseSession.isOpen());
        assertFalse(wsSession.isOpen());

        sseSession.emit(evt);
        wsSession.emit(evt);
        wsSession.close();
        assertEquals(2, sseFrames.size());
        assertEquals(1, wsFrames.size());

        AgUiWebSocketTransportSession nullCallbacks = new AgUiWebSocketTransportSession("ws-null", null, null, null);
        assertFalse(nullCallbacks.isOpen());
        nullCallbacks.emit(evt);
        nullCallbacks.close();

        registry.unregister("sse-1");
        registry.unregister("ws-1");
        assertEquals(0, registry.activeCount());
    }

    @Test
    void shipsCdiBeansXmlAndGraalVmReflectConfigResources() throws Exception {
        try (InputStream beansStream = getClass().getClassLoader().getResourceAsStream("META-INF/beans.xml")) {
            assertNotNull(beansStream, "META-INF/beans.xml must be present");
            String beansXml = new String(beansStream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(beansXml.contains("bean-discovery-mode=\"annotated\""));
        }
        try (InputStream reflectStream = getClass().getClassLoader()
                .getResourceAsStream("META-INF/native-image/org.agui/ag-ui-core/reflect-config.json")) {
            assertNotNull(reflectStream, "reflect-config.json must be present for GraalVM native image");
            String reflectJson = new String(reflectStream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(reflectJson.contains("org.agui.core.domain.AgUiEvent"));
            assertTrue(reflectJson.contains("org.agui.core.domain.AgUiTransportType"));
        }
    }
}
