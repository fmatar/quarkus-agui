package org.agui.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agui.community.core.event.ActivitySnapshotEvent;
import com.agui.community.core.event.Event;
import com.agui.community.core.event.EventType;
import com.agui.community.core.event.StepFinishedEvent;
import com.agui.community.core.event.StepStartedEvent;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.a2aproject.sdk.A2A;
import org.a2aproject.sdk.client.MessageEvent;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.client.TaskUpdateEvent;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendMessageResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendStreamingMessageResponse;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.agui.a2a.adapters.AgUiA2aBridge;
import org.agui.a2a.adapters.AgUiA2aClient;
import org.agui.a2a.domain.A2aDelegationResult;
import org.agui.a2a.domain.A2aHandoff;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AgUiA2aBridgeTest {

    @Test
    @DisplayName("A2aHandoff validates mandatory fields and copies metadata defensively")
    void handoffRecordValidatesAndDefensivelyCopies() {
        Map<String, Object> mutable = new HashMap<>();
        mutable.put("country", "Japan");
        A2aHandoff handoff = new A2aHandoff("task-1", "Nomad-A2A", "verify-advisory", "Verified", mutable);
        mutable.put("country", "Mutated");

        assertEquals("Japan", handoff.metadata().get("country"));
        assertThrows(UnsupportedOperationException.class, () -> handoff.metadata().put("x", 1));

        A2aHandoff nullMeta = new A2aHandoff("task-2", "Nomad-A2A", "verify-advisory", "Verified", null);
        assertTrue(nullMeta.metadata().isEmpty());

        assertThrows(NullPointerException.class, () -> new A2aHandoff(null, "a", "s", "sum", Map.of()));
        assertThrows(NullPointerException.class, () -> new A2aHandoff("t", null, "s", "sum", Map.of()));
        assertThrows(NullPointerException.class, () -> new A2aHandoff("t", "a", null, "sum", Map.of()));
        assertThrows(NullPointerException.class, () -> new A2aHandoff("t", "a", "s", null, Map.of()));
    }

    @Test
    @DisplayName("AgUiA2aBridge emits canonical StepStartedEvent, ActivitySnapshotEvent, and StepFinishedEvent with rawEvent provenance")
    @SuppressWarnings("unchecked")
    void agUiA2aBridgeCanonicalEventMapping() {
        AgUiA2aBridge bridge = new AgUiA2aBridge();

        assertThrows(NullPointerException.class, () -> bridge.recordDelegation(null));

        A2aHandoff handoff = new A2aHandoff(
                "task-202",
                "Nomad-A2A-Specialist",
                "verify-travel-advisory",
                "Destination advisory verified",
                Map.of("country", "Japan"));

        List<Event> delegationEvents = bridge.recordDelegation(handoff);
        assertEquals(2, delegationEvents.size());
        StepStartedEvent started = assertInstanceOf(StepStartedEvent.class, delegationEvents.get(0));
        StepFinishedEvent finished = assertInstanceOf(StepFinishedEvent.class, delegationEvents.get(1));

        assertEquals(EventType.STEP_STARTED, started.type());
        assertEquals("a2a:Nomad-A2A-Specialist:verify-travel-advisory", started.stepName());
        assertNotNull(started.timestamp());
        Map<String, Object> startMeta = (Map<String, Object>) started.rawEvent();
        assertEquals("A2A", startMeta.get("protocol"));
        assertEquals("a2a:task", startMeta.get("callType"));
        assertEquals("task-202", startMeta.get("stepId"));
        assertEquals("Delegated", startMeta.get("summary"));

        assertEquals(EventType.STEP_FINISHED, finished.type());
        Map<String, Object> finishMeta = (Map<String, Object>) finished.rawEvent();
        assertEquals("Destination advisory verified", finishMeta.get("summary"));
        assertEquals("OK", finishMeta.get("status"));
        assertFalse((Boolean) finishMeta.get("isError"));
        assertEquals(Map.of("country", "Japan"), finishMeta.get("metadata"));

        // Task with artifacts produces [StepStartedEvent, ActivitySnapshotEvent, StepFinishedEvent]
        Artifact artifact = Artifact.builder()
                .artifactId("art-1")
                .parts(List.of(new TextPart("Artifact summary text"), new DataPart(Map.of("score", 95))))
                .build();
        Task taskWithArtifact = Task.builder()
                .id("task-art-1")
                .contextId("ctx-1")
                .status(new TaskStatus(TaskState.TASK_STATE_COMPLETED))
                .artifacts(List.of(artifact))
                .metadata(Map.of("city", "Kyoto"))
                .build();

        List<Event> taskEvents = bridge.fromA2aTask("Nomad-A2A-Specialist", "verify-travel-advisory", taskWithArtifact);
        assertEquals(3, taskEvents.size());
        assertEquals(EventType.STEP_STARTED, taskEvents.get(0).type());
        ActivitySnapshotEvent activity = assertInstanceOf(ActivitySnapshotEvent.class, taskEvents.get(1));
        assertEquals(EventType.ACTIVITY_SNAPSHOT, activity.type());
        assertEquals("a2a:artifact", activity.activityType());
        assertEquals("task-art-1", activity.messageId());
        assertTrue(activity.replace());
        assertEquals(EventType.STEP_FINISHED, taskEvents.get(2).type());
        assertEquals("Artifact summary text", bridge.extractTaskSummary(taskWithArtifact));

        // Task without artifacts + message status
        Message agentMsg = A2A.toAgentMessage("Advisory verified");
        assertEquals("Advisory verified", bridge.extractMessageText(agentMsg));
        assertEquals("", bridge.extractMessageText(null));

        Task taskWorking = Task.builder()
                .id("task-work-1")
                .contextId("ctx-1")
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING, agentMsg, null))
                .build();
        List<Event> workingEvents = bridge.fromA2aTask("Nomad", "skill", taskWorking);
        assertEquals(2, workingEvents.size());
        assertEquals("Advisory verified", bridge.extractTaskSummary(taskWorking));

        // Failed task marks error in rawEvent
        Task failedTask = Task.builder()
                .id("task-fail-1")
                .contextId("ctx-1")
                .status(new TaskStatus(TaskState.TASK_STATE_FAILED))
                .build();
        assertEquals("TASK_STATE_FAILED", bridge.extractTaskSummary(failedTask));
        List<Event> failedEvents = bridge.fromA2aTask("Nomad", "skill", failedTask);
        Map<String, Object> failedMeta = (Map<String, Object>) failedEvents.get(1).rawEvent();
        assertTrue((Boolean) failedMeta.get("isError"));
        assertEquals("ERROR", failedMeta.get("status"));

        // Rejected task marks error in rawEvent
        Task rejectedTask = Task.builder()
                .id("task-rej-1")
                .contextId("ctx-1")
                .status(new TaskStatus(TaskState.TASK_STATE_REJECTED))
                .build();
        Map<String, Object> rejMeta = (Map<String, Object>) bridge.fromA2aTask("Nomad", "skill", rejectedTask).get(1).rawEvent();
        assertTrue((Boolean) rejMeta.get("isError"));

        // Client events: MessageEvent, TaskEvent, TaskUpdateEvent
        Event msgEvt = bridge.fromA2aClientEvent("verify", new MessageEvent(agentMsg), null);
        assertEquals(EventType.STEP_FINISHED, msgEvt.type());
        Map<String, Object> msgMeta = (Map<String, Object>) msgEvt.rawEvent();
        assertEquals("A2A-Agent", msgMeta.get("agentName"));

        Message msgWithTaskId = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .parts(List.of(new TextPart("Task-linked message"), new DataPart(Map.of("k", "v"))))
                .taskId("task-linked-1")
                .messageId("msg-1")
                .build();
        Event linkedEvt = bridge.fromA2aClientEvent("verify", new MessageEvent(msgWithTaskId), null);
        assertEquals("task-linked-1", ((Map<String, Object>) linkedEvt.rawEvent()).get("stepId"));

        Event taskClientEvt = bridge.fromA2aClientEvent("verify", new TaskEvent(taskWithArtifact), null);
        assertEquals(EventType.STEP_FINISHED, taskClientEvt.type());

        TaskUpdateEvent updateEvent = new TaskUpdateEvent(
                failedTask,
                new TaskStatusUpdateEvent("task-fail-1", new TaskStatus(TaskState.TASK_STATE_WORKING), "ctx-1", Map.of()));
        AgentCard card = AgentCard.builder()
                .name("Nomad-Specialist")
                .description("Travel specialist")
                .supportedInterfaces(List.of(new AgentInterface("JSONRPC", "http://localhost:8082")))
                .version("1.0.0")
                .capabilities(AgentCapabilities.builder().build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of())
                .build();
        Event updateStep = bridge.fromA2aClientEvent("verify", updateEvent, card);
        assertEquals(EventType.STEP_STARTED, updateStep.type());
        assertEquals("Nomad-Specialist", ((Map<String, Object>) updateStep.rawEvent()).get("agentName"));

        // Null checks
        assertThrows(NullPointerException.class, () -> bridge.fromA2aTask(null, "s", taskWorking));
        assertThrows(NullPointerException.class, () -> bridge.fromA2aTask("a", null, taskWorking));
        assertThrows(NullPointerException.class, () -> bridge.fromA2aTask("a", "s", null));
        assertThrows(NullPointerException.class, () -> bridge.fromA2aClientEvent(null, updateEvent, card));
        assertThrows(NullPointerException.class, () -> bridge.fromA2aClientEvent("s", null, card));
        assertThrows(NullPointerException.class, () -> bridge.extractTaskSummary(null));
        assertNotNull(bridge.getClass().getClassLoader().getResourceAsStream("META-INF/beans.xml"));

        Map<String, Object> taskData = bridge.extractTaskData(taskWithArtifact);
        assertEquals(95, taskData.get("score"));
        assertEquals("Kyoto", taskData.get("city"));

        // Task with status message DataPart and semantic payload error
        Message statusWithDataErr = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .messageId("msg-err-1")
                .parts(List.of(new TextPart("Semantic upstream error"), new DataPart(Map.of("status", "ERROR", "reason", "401"))))
                .build();
        Task semanticErrTask = Task.builder()
                .id("task-sem-err")
                .contextId("ctx-err")
                .status(new TaskStatus(TaskState.TASK_STATE_COMPLETED, statusWithDataErr, null))
                .build();
        assertEquals("401", bridge.extractTaskData(semanticErrTask).get("reason"));
        List<Event> semEvents = bridge.fromA2aTask("Nomad", "verify", semanticErrTask);
        assertTrue((Boolean) ((Map<String, Object>) semEvents.get(1).rawEvent()).get("isError"));

        Event semClientEvt = bridge.fromA2aClientEvent("verify", new MessageEvent(statusWithDataErr), card);
        assertTrue((Boolean) ((Map<String, Object>) semClientEvt.rawEvent()).get("isError"));
        Event semTaskEvt = bridge.fromA2aClientEvent("verify", new TaskEvent(semanticErrTask), card);
        assertTrue((Boolean) ((Map<String, Object>) semTaskEvt.rawEvent()).get("isError"));

        assertEquals("v", bridge.extractMessageData(msgWithTaskId).get("k"));
        assertTrue(bridge.extractMessageData(null).isEmpty());
    }

    @Test
    @DisplayName("A2aDelegationResult validates mandatory fields and copies data defensively")
    void delegationResultValidatesAndCopies() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("currency", "EUR");
        A2aDelegationResult res = new A2aDelegationResult(
                "task-1", "Nomad-A2A-Specialist", "verify-travel-advisory", "COMPLETED", false, "Verified", raw);
        raw.put("currency", "MUTATED");

        assertEquals("EUR", res.data().get("currency"));
        assertThrows(UnsupportedOperationException.class, () -> res.data().put("x", 1));
        assertTrue(new A2aDelegationResult("t", "a", "s", "COMPLETED", false, "ok", null).data().isEmpty());

        assertThrows(NullPointerException.class, () -> new A2aDelegationResult(null, "a", "s", "st", false, "sum", Map.of()));
        assertThrows(NullPointerException.class, () -> new A2aDelegationResult("t", null, "s", "st", false, "sum", Map.of()));
        assertThrows(NullPointerException.class, () -> new A2aDelegationResult("t", "a", null, "st", false, "sum", Map.of()));
        assertThrows(NullPointerException.class, () -> new A2aDelegationResult("t", "a", "s", null, false, "sum", Map.of()));
        assertThrows(NullPointerException.class, () -> new A2aDelegationResult("t", "a", "s", "st", false, null, Map.of()));
    }

    @Test
    @DisplayName("AgUiA2aClient resolves AgentCard via A2ACardResolver, rebinds proxy/container endpoints, and delegates over JSON-RPC & SSE")
    @SuppressWarnings("unchecked")
    void agUiA2aClientResolvesCardAndDelegatesOverJsonRpcAndSse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        String baseUrl = "http://127.0.0.1:" + port;

        // Advertise localhost:9999 in card to prove AgUiA2aClient rebinds container/proxy URLs to baseUrl
        AgentCard advertisedCard = AgentCard.builder()
                .name("Nomad-A2A-Specialist")
                .description("Specialist Travel Agent")
                .version("1.5.0")
                .url("http://localhost:9999")
                .preferredTransport(TransportProtocol.JSONRPC.asString())
                .supportedInterfaces(List.of(new AgentInterface(TransportProtocol.JSONRPC.asString(), "http://localhost:9999")))
                .capabilities(AgentCapabilities.builder().streaming(true).pushNotifications(false).build())
                .defaultInputModes(List.of("text/plain", "application/json"))
                .defaultOutputModes(List.of("text/plain", "application/json"))
                .skills(List.of(AgentSkill.builder()
                        .id("verify-travel-advisory")
                        .name("Verify Advisory")
                        .description("REST Countries v5")
                        .tags(List.of("advisory"))
                        .build()))
                .build();

        List<String> capturedAuth = new java.util.concurrent.CopyOnWriteArrayList<>();
        List<String> capturedTrace = new java.util.concurrent.CopyOnWriteArrayList<>();
        List<String> capturedBodies = new java.util.concurrent.CopyOnWriteArrayList<>();

        server.createContext("/.well-known/agent-card.json", exchange -> {
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if (auth != null) {
                capturedAuth.add(auth);
            }
            writeResponse(exchange, 200, "application/json", toJson(advertisedCard));
        });
        server.createContext("/", exchange -> {
            String trace = exchange.getRequestHeaders().getFirst("X-Trace-Id");
            if (trace != null) {
                capturedTrace.add(trace);
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            capturedBodies.add(body);
            if (body.contains("slow-stream")) {
                writeResponse(exchange, 200, "text/event-stream",
                        "data: " + toJson(new SendStreamingMessageResponse("1", Task.builder()
                                .id("task-slow")
                                .contextId("ctx-slow")
                                .status(new TaskStatus(TaskState.TASK_STATE_WORKING))
                                .build())) + "\n\n");
            } else if (body.contains("SendStreamingMessage")) {
                Artifact artifact = Artifact.builder()
                        .artifactId("art-sse-1")
                        .parts(List.of(
                                new TextPart("SSE France advisory verified"),
                                new DataPart(Map.of("country", "France", "currency", "EUR", "status", "OK", "isError", false))))
                        .build();
                Task initialTask = Task.builder()
                        .id("task-sse-1")
                        .contextId("ctx-sse-1")
                        .status(new TaskStatus(TaskState.TASK_STATE_SUBMITTED))
                        .build();
                TaskArtifactUpdateEvent artEvt = TaskArtifactUpdateEvent.builder()
                        .taskId("task-sse-1")
                        .contextId("ctx-sse-1")
                        .artifact(artifact)
                        .lastChunk(true)
                        .build();
                TaskStatusUpdateEvent doneEvt = new TaskStatusUpdateEvent(
                        "task-sse-1",
                        new TaskStatus(TaskState.TASK_STATE_COMPLETED),
                        "ctx-sse-1",
                        Map.of());
                String ssePayload = "data: " + toJson(new SendStreamingMessageResponse("1", initialTask)) + "\n\n"
                        + "data: " + toJson(new SendStreamingMessageResponse("1", artEvt)) + "\n\n"
                        + "data: " + toJson(new SendStreamingMessageResponse("1", doneEvt)) + "\n\n";
                writeResponse(exchange, 200, "text/event-stream", ssePayload);
            } else if (body.contains("direct-message")) {
                Message reply = Message.builder()
                        .role(Message.Role.ROLE_AGENT)
                        .messageId("msg-direct-1")
                        .parts(List.of(
                                new TextPart("Direct agent reply"),
                                new DataPart(Map.of("mode", "direct", "isError", false))))
                        .build();
                writeResponse(exchange, 200, "application/json", toJson(new SendMessageResponse("1", reply)));
            } else if (body.contains("fail-task")) {
                Task failed = Task.builder()
                        .id("task-failed-99")
                        .contextId("ctx-fail")
                        .status(new TaskStatus(TaskState.TASK_STATE_FAILED, A2A.toAgentMessage("Upstream failure"), null))
                        .build();
                writeResponse(exchange, 200, "application/json", toJson(new SendMessageResponse("1", failed)));
            } else {
                Artifact artifact = Artifact.builder()
                        .artifactId("art-rpc-1")
                        .parts(List.of(
                                new TextPart("Paris advisory verified"),
                                new DataPart(Map.of("country", "France", "capital", "Paris", "currency", "EUR", "status", "OK", "isError", false))))
                        .build();
                Task completed = Task.builder()
                        .id("task-rpc-1")
                        .contextId("ctx-rpc-1")
                        .status(new TaskStatus(TaskState.TASK_STATE_COMPLETED))
                        .artifacts(List.of(artifact))
                        .build();
                writeResponse(exchange, 200, "application/json", toJson(new SendMessageResponse("1", completed)));
            }
        });
        server.start();

        try {
            // 1. Non-streaming JSON-RPC SendMessage client with trailing slash, authHeaders, httpClient & bridge
            org.a2aproject.sdk.client.http.A2AHttpClient customHttp =
                    org.a2aproject.sdk.client.http.A2AHttpClientFactory.create();
            try (AgUiA2aClient rpcClient = AgUiA2aClient.builder(baseUrl + "/")
                    .streaming(false)
                    .requestTimeout(Duration.ofMillis(600))
                    .httpClient(customHttp)
                    .authHeaders(Map.of("Authorization", "Bearer test-token"))
                    .bridge(new AgUiA2aBridge())
                    .build()) {
                assertFalse(rpcClient.isCached());
                AgentCard resolved = rpcClient.resolveAgentCard();
                assertTrue(rpcClient.isCached());
                assertEquals("Nomad-A2A-Specialist", resolved.name());
                assertEquals(baseUrl, resolved.url());
                assertEquals(baseUrl, resolved.supportedInterfaces().get(0).url());
                assertTrue(capturedAuth.contains("Bearer test-token"));

                AgUiA2aClient.DelegationExecution exec = rpcClient.delegate(
                        "req-1",
                        "verify-travel-advisory",
                        "Verify travel advisory for Paris",
                        Map.of("city", "Paris", "country", "France"),
                        Map.of("X-Trace-Id", "trace-100"));
                assertTrue(capturedTrace.contains("trace-100"));
                assertTrue(capturedBodies.stream().anyMatch(b -> b.contains("France")));
                assertFalse(exec.result().isError());
                assertEquals("COMPLETED", exec.result().status());
                assertEquals("Paris advisory verified", exec.result().summary());
                assertEquals("France", exec.result().data().get("country"));
                assertEquals("EUR", exec.result().data().get("currency"));
                assertEquals(3, exec.agUiEvents().size());
                ActivitySnapshotEvent snap = assertInstanceOf(ActivitySnapshotEvent.class, exec.agUiEvents().get(1));
                Map<String, Object> snapContent = (Map<String, Object>) snap.content();
                Map<String, Object> snapData = (Map<String, Object>) snapContent.get("data");
                assertEquals("Paris", snapData.get("capital"));

                // Direct MessageEvent reply path (blank requestId generates UUID)
                AgUiA2aClient.DelegationExecution directExec = rpcClient.delegate(
                        " ", "verify-travel-advisory", "direct-message", null, null);
                assertFalse(directExec.result().isError());
                assertEquals("msg-direct-1", directExec.result().taskId());
                assertEquals("Direct agent reply", directExec.result().summary());
                assertEquals("direct", directExec.result().data().get("mode"));

                // Terminal failed TaskState path
                AgUiA2aClient.DelegationExecution failExec = rpcClient.delegate(
                        "req-3", "verify-travel-advisory", "fail-task", Map.of(), Map.of());
                assertTrue(failExec.result().isError());
                assertEquals("ERROR", failExec.result().status());
                assertEquals("Upstream failure", failExec.result().summary());

                rpcClient.invalidate();
                assertFalse(rpcClient.isCached());
            }

            // 2. Streaming SSE SendStreamingMessage client + timeout handling
            try (AgUiA2aClient sseClient = AgUiA2aClient.builder(baseUrl)
                    .streaming(true)
                    .requestTimeout(Duration.ofMillis(400))
                    .authHeaders(null)
                    .build()) {
                AgUiA2aClient.DelegationExecution sseExec = sseClient.delegate(
                        "req-sse-1",
                        "verify-travel-advisory",
                        "Stream advisory for Paris",
                        Map.of("city", "Paris"),
                        Map.of());
                assertFalse(sseExec.result().isError());
                assertEquals("COMPLETED", sseExec.result().status());
                assertEquals("SSE France advisory verified", sseExec.result().summary());
                assertEquals("France", sseExec.result().data().get("country"));
                assertFalse(sseExec.agUiEvents().isEmpty());

                AgUiA2aClient.DelegationExecution timeoutExec = sseClient.delegate(
                        "req-slow", "verify-travel-advisory", "slow-stream", Map.of(), Map.of());
                assertTrue(timeoutExec.result().isError());
                assertEquals("ERROR", timeoutExec.result().status());
            }
        } finally {
            server.stop(0);
        }

        // 3. Unreachable endpoint + builder validation
        assertThrows(IllegalArgumentException.class, () -> AgUiA2aClient.builder("   ").build());
        try (AgUiA2aClient brokenClient = AgUiA2aClient.builder("http://127.0.0.1:1")
                .streaming(false)
                .requestTimeout(Duration.ofMillis(300))
                .build()) {
            AgUiA2aClient.DelegationExecution errExec = brokenClient.delegate(
                    "req-err", "verify-travel-advisory", "Verify Paris", Map.of("city", "Paris"), Map.of());
            assertTrue(errExec.result().isError());
            assertEquals("ERROR", errExec.result().status());
            assertEquals("A2A-Agent", errExec.result().agentName());
        }
    }

    private static String toJson(Object value) throws IOException {
        try {
            return JsonUtil.toJson(value);
        } catch (Exception ex) {
            throw new IOException(ex);
        }
    }

    private static void writeResponse(HttpExchange exchange, int status, String contentType, String payload)
            throws IOException {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
