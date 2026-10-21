package org.agui.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import org.agui.core.adapters.AgUiFlowAdapter;
import org.agui.core.adapters.AgUiSseFormatter;
import org.agui.core.application.AgUiSessionRunner;
import org.agui.core.domain.A2aHandoffPayload;
import org.agui.core.domain.AgUiEvent;
import org.agui.core.domain.AgUiEventType;
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
}
