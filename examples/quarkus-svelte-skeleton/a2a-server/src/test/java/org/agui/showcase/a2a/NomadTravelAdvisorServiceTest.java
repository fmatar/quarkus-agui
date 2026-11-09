package org.agui.showcase.a2a;

import com.sun.net.httpserver.HttpServer;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.events.EventQueue;
import org.a2aproject.sdk.server.events.EventQueueItem;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NomadTravelAdvisorServiceTest {

    private static final String REST_COUNTRIES_DEPRECATION_JSON = """
            {
                "success": false,
                "data": null,
                "errors": [
                    {
                        "message": "This API version has been deprecated. Please visit https://restcountries.com/docs/countries/legacy-api-deprecation to migrate to our new version (v5)."
                    }
                ]
            }
            """;

    private NomadTravelAdvisorService service;
    private NomadA2aHealthResource healthResource;

    @BeforeEach
    void setUp() {
        service = new NomadTravelAdvisorService();
        healthResource = new NomadA2aHealthResource(service);
    }

    @Test
    void upstreamHttpResultBoundariesAndLiveHttpProbeWorkDeterministically() throws Exception {
        assertFalse(new NomadTravelAdvisorService.UpstreamHttpResult(199, "{}", null, 1L).isHttpOk());
        assertTrue(new NomadTravelAdvisorService.UpstreamHttpResult(200, "{}", null, 1L).isHttpOk());
        assertTrue(new NomadTravelAdvisorService.UpstreamHttpResult(299, "{}", null, 1L).isHttpOk());
        assertFalse(new NomadTravelAdvisorService.UpstreamHttpResult(300, "{}", null, 1L).isHttpOk());
        assertFalse(new NomadTravelAdvisorService.UpstreamHttpResult(200, null, null, 1L).isHttpOk());
        assertFalse(new NomadTravelAdvisorService.UpstreamHttpResult(200, "   ", null, 1L).isHttpOk());

        assertEquals("No response from host", service.inspectUpstreamError(null, "host"));
        assertEquals("HTTP 503 from host", service.inspectUpstreamError(
                new NomadTravelAdvisorService.UpstreamHttpResult(503, null, "  ", 2L), "host"));
        assertEquals("Bad gateway", service.inspectUpstreamError(
                new NomadTravelAdvisorService.UpstreamHttpResult(200, "{\"error\":\"Bad gateway\"}", null, 2L), "host"));
        assertEquals("Rate limited", service.inspectUpstreamError(
                new NomadTravelAdvisorService.UpstreamHttpResult(200, "{\"message\":\"Rate limited\"}", null, 2L), "host"));
        assertEquals("Deprecated v3", service.inspectUpstreamError(
                new NomadTravelAdvisorService.UpstreamHttpResult(200, "{\"success\":false,\"message\":\"Deprecated v3\"}", null, 2L), "host"));
        assertNull(service.inspectUpstreamError(
                new NomadTravelAdvisorService.UpstreamHttpResult(200, "[{\"ok\":true}]", null, 2L), "host"));

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", exchange -> {
            byte[] bytes = "{\"rates\":{\"EUR\":0.92}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.createContext("/err", exchange -> {
            byte[] bytes = "bad".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        int port = server.getAddress().getPort();
        try {
            service.setHttpProbe(null);
            NomadTravelAdvisorService.UpstreamHttpResult ok =
                    service.probeUrlViaHttp("http://127.0.0.1:" + port + "/ok");
            assertTrue(ok.isHttpOk());
            assertEquals(200, ok.statusCode());
            assertTrue(ok.latencyMs() >= 1L);

            NomadTravelAdvisorService.UpstreamHttpResult err =
                    service.probeUrlViaHttp("http://127.0.0.1:" + port + "/err");
            assertFalse(err.isHttpOk());
            assertEquals(500, err.statusCode());
            assertTrue(err.error().contains("HTTP 500"));
        } finally {
            server.stop(0);
        }

        NomadTravelAdvisorService.UpstreamHttpResult closed =
                service.probeUrlViaHttp("http://127.0.0.1:" + port + "/closed");
        assertFalse(closed.isHttpOk());
        assertEquals(0, closed.statusCode());
        assertNotNull(closed.error());
    }

    @Test
    void performAuditRejectsBlankPromptMissingArgsAndUnsupportedSkills() {
        assertThrows(IllegalArgumentException.class,
                () -> service.performAudit("convert-and-audit-budget", null, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> service.performAudit("convert-and-audit-budget", "   ", Map.of()));

        NomadTravelAdvisorService.AuditResult unsupported = service.performAudit(
                "unknown-skill", "Check something", null);
        assertEquals("ERROR", unsupported.data().get("status"));
        assertEquals("UNSUPPORTED_SKILL", unsupported.data().get("code"));

        NomadTravelAdvisorService.AuditResult missingCityFx = service.performAudit(
                null, "Convert $1,000 USD budget", Map.of());
        assertEquals("MISSING_REQUIRED_ARGUMENTS", missingCityFx.data().get("code"));

        NomadTravelAdvisorService.AuditResult missingCityAdvisory = service.performAudit(
                "verify-travel-advisory", "Verify destination travel advisory", Map.of("city", "  "));
        assertEquals("MISSING_REQUIRED_ARGUMENTS", missingCityAdvisory.data().get("code"));

        NomadTravelAdvisorService.AuditResult missingCityCadence = service.performAudit(
                "curate-neighborhood-cadence", "Curate architectural neighborhood sequence", Map.of());
        assertEquals("MISSING_REQUIRED_ARGUMENTS", missingCityCadence.data().get("code"));

        NomadTravelAdvisorService.AuditResult missingBudgetFx = service.performAudit(
                "convert-and-audit-budget", "Convert trip budget for Paris", Map.of("city", "Paris"));
        assertEquals("MISSING_REQUIRED_ARGUMENTS", missingBudgetFx.data().get("code"));

        NomadTravelAdvisorService.AuditResult zeroBudgetFx = service.performAudit(
                "convert-and-audit-budget", "Convert trip budget for Paris", Map.of("city", "Paris", "budgetUsd", 0.0));
        assertEquals("MISSING_REQUIRED_ARGUMENTS", zeroBudgetFx.data().get("code"));

        NomadTravelAdvisorService.AuditResult negativeBudgetFx = service.performAudit(
                "convert-and-audit-budget", "Convert trip budget for Paris", Map.of("city", "Paris", "budget", "-50"));
        assertEquals("MISSING_REQUIRED_ARGUMENTS", negativeBudgetFx.data().get("code"));
    }

    @Test
    void performTravelAdvisorySurfacesRestCountriesDeprecationAndPayloadErrorsWithoutFakeFallback() {
        service.setHttpProbe(url -> new NomadTravelAdvisorService.UpstreamHttpResult(
                400, REST_COUNTRIES_DEPRECATION_JSON, "HTTP 400 from restcountries.com", 42L));

        NomadTravelAdvisorService.AuditResult advError = service.performAudit(
                "verify-travel-advisory", "Verify travel advisory for Paris", Map.of("city", "Paris"));
        assertEquals("ERROR", advError.data().get("status"));
        assertEquals(Boolean.TRUE, advError.data().get("isError"));
        assertEquals(Boolean.FALSE, advError.data().get("serviceAvailable"));
        assertEquals("UNAVAILABLE", advError.data().get("advisoryStatus"));
        assertEquals("restcountries.com", advError.data().get("upstream"));
        assertTrue(String.valueOf(advError.data().get("error")).contains("This API version has been deprecated"));
        assertFalse(advError.summary().contains("CLEAR"));

        // Empty array and broken JSON also surface ERROR
        service.setHttpProbe(url -> new NomadTravelAdvisorService.UpstreamHttpResult(200, "[]", null, 10L));
        assertEquals("ERROR", service.performAudit("verify-travel-advisory", "Advisory in Lisbon", Map.of()).data().get("status"));

        service.setHttpProbe(url -> new NomadTravelAdvisorService.UpstreamHttpResult(200, "{broken-json", null, 10L));
        assertEquals("ERROR", service.performAudit("verify-travel-advisory", "Advisory in Lisbon", Map.of()).data().get("status"));
    }

    @Test
    void performCurrencyAuditCadenceAndCurrencyDetectionHandleAllBranches() {
        service.setHttpProbe(url -> new NomadTravelAdvisorService.UpstreamHttpResult(
                0, null, "ConnectException: Connection refused", 12L));

        NomadTravelAdvisorService.AuditResult fxDown = service.performAudit(
                "convert-and-audit-budget",
                "Convert $2,400 USD budget for Kyoto",
                Map.of("city", "Kyoto", "currency", "JPY", "budgetUsd", 2400.0, "baseCurrency", "USD"));
        assertEquals("ERROR", fxDown.data().get("status"));
        assertEquals("Kyoto", fxDown.data().get("city"));

        // Missing target symbol in rates JSON + broken JSON
        service.setHttpProbe(url -> new NomadTravelAdvisorService.UpstreamHttpResult(
                200, "{\"rates\":{\"EUR\":0.91}}", null, 15L));
        assertEquals("ERROR", service.performAudit(
                "convert-and-audit-budget", "Convert $1,000 USD for Kyoto", Map.of("city", "Kyoto")).data().get("status"));

        service.setHttpProbe(url -> new NomadTravelAdvisorService.UpstreamHttpResult(
                200, "{broken", null, 15L));
        assertEquals("ERROR", service.performAudit(
                "convert-and-audit-budget", "Convert $1,000 USD for Kyoto", Map.of("city", "Kyoto")).data().get("status"));

        // Healthy Frankfurter & REST Countries
        service.setHttpProbe(url -> {
            if (url.contains("/v1/latest")) {
                return new NomadTravelAdvisorService.UpstreamHttpResult(
                        200,
                        "{\"amount\":1.0,\"base\":\"USD\",\"rates\":{\"JPY\":153.25,\"EUR\":0.91,\"GBP\":0.79,\"CHF\":0.88,\"USD\":1.0}}",
                        null,
                        28L);
            }
            return new NomadTravelAdvisorService.UpstreamHttpResult(
                    200,
                    "[{\"name\":{\"common\":\"Japan\"},\"capital\":[\"Tokyo\"],\"region\":\"Asia\",\"timezones\":[\"UTC+09:00\"]}]",
                    null,
                    31L);
        });

        NomadTravelAdvisorService.AuditResult fxLive = service.performAudit(
                "  ",
                "Convert $2,000 USD budget for Kyoto",
                Map.of("city", "Kyoto", "currency", "JPY", "amount", "2,000", "baseCurrency", "USD"));
        assertEquals("OK", fxLive.data().get("status"));
        assertEquals(153.25, fxLive.data().get("exchangeRate"));
        assertEquals(306500.0, fxLive.data().get("convertedTotal"));

        // Prompt-parsed currencies (€, £, CHF) and regex city fallback ("in Lucerne")
        NomadTravelAdvisorService.AuditResult eurPrompt = service.performAudit(
                "convert-and-audit-budget", "Convert €1,200 budget in Tokyo", Map.of("budget", "invalid-num"));
        assertEquals("EUR", eurPrompt.data().get("baseCurrency"));
        assertEquals(1200.0, eurPrompt.data().get("budgetUsd"));

        NomadTravelAdvisorService.AuditResult gbpPrompt = service.performAudit(
                "convert-and-audit-budget", "Convert 1500 pounds in Lucerne", Map.of("currency", "CHF"));
        assertEquals("GBP", gbpPrompt.data().get("baseCurrency"));
        assertEquals("Lucerne", gbpPrompt.data().get("city"));

        NomadTravelAdvisorService.AuditResult chfPrompt = service.performAudit(
                "convert-and-audit-budget", "Convert 900 chf for Lisbon", Map.of());
        assertEquals("CHF", chfPrompt.data().get("baseCurrency"));
        assertEquals("EUR", chfPrompt.data().get("targetCurrency"));

        NomadTravelAdvisorService.AuditResult sameCurFx = service.performAudit(
                "convert-and-audit-budget",
                "Audit €2,500 EUR budget in Paris",
                Map.of("city", "Paris", "baseCurrency", "EUR", "currency", "EUR", "budgetUsd", 2500.0));
        assertEquals(1.0, sameCurFx.data().get("exchangeRate"));
        assertEquals(2500.0, sameCurFx.data().get("convertedTotal"));

        NomadTravelAdvisorService.AuditResult advLive = service.performAudit(
                "verify-travel-advisory", "[verify-travel-advisory] Verify Kyoto", Map.of());
        assertEquals("OK", advLive.data().get("status"));
        assertEquals("Tokyo", advLive.data().get("capital"));
        assertEquals("UTC+09:00", advLive.data().get("timezone"));

        for (String city : List.of("Lisbon", "Reykjavik", "Tokyo", "Paris", "Beirut", "Zurich")) {
            NomadTravelAdvisorService.AuditResult cadence = service.performAudit(
                    "curate-neighborhood-cadence", "Curate cadence", Map.of("city", city));
            assertEquals("OK", cadence.data().get("status"));
            assertEquals(city, cadence.data().get("city"));
        }

        assertEquals("USD", NomadTravelAdvisorService.resolveStaticCurrencyForCountry(null));
        assertEquals("USD", NomadTravelAdvisorService.resolveStaticCurrencyForCountry(" "));
        assertEquals("JPY", NomadTravelAdvisorService.resolveStaticCurrencyForCountry("Japan"));
        assertEquals("USD", NomadTravelAdvisorService.resolveStaticCurrencyForCountry("UnknownLand"));
    }

    @Test
    void checkHealthAgentConfigurationAndHealthResourceCoverAllExecutionPaths() throws Exception {
        // 1. DEGRADED health
        service.setHttpProbe(url -> url.contains("frankfurter")
                ? new NomadTravelAdvisorService.UpstreamHttpResult(200, "{\"rates\":{\"EUR\":0.92}}", null, 20L)
                : new NomadTravelAdvisorService.UpstreamHttpResult(400, REST_COUNTRIES_DEPRECATION_JSON, "HTTP 400", 45L));
        Map<String, Object> degraded = healthResource.health();
        assertEquals("DEGRADED", degraded.get("status"));
        assertEquals(1L, ((Number) degraded.get("healthyCount")).longValue());

        // 2. UP health
        service.setHttpProbe(url -> url.contains("frankfurter")
                ? new NomadTravelAdvisorService.UpstreamHttpResult(200, "{\"rates\":{\"EUR\":0.92}}", null, 15L)
                : new NomadTravelAdvisorService.UpstreamHttpResult(200, "[{\"name\":{\"common\":\"France\"}}]", null, 18L));
        assertEquals("UP", healthResource.health().get("status"));

        // 3. DOWN health
        service.setHttpProbe(url -> new NomadTravelAdvisorService.UpstreamHttpResult(503, null, "down", 5L));
        assertEquals("DOWN", healthResource.health().get("status"));

        // 4. NomadA2aHealthResource.audit
        assertEquals("MISSING_REQUIRED_ARGUMENTS", healthResource.audit(null).data().get("code"));
        NomadTravelAdvisorService.AuditResult resourceCadence = healthResource.audit(Map.of(
                "skillId", "curate-neighborhood-cadence",
                "prompt", "Curate Paris",
                "context", Map.of("city", "Paris")));
        assertEquals("OK", resourceCadence.data().get("status"));

        // 5. NomadA2aAgentConfiguration publicAgentCard + extractExecutionContext + agentExecutor
        NomadA2aAgentConfiguration config = new NomadA2aAgentConfiguration();
        config.publicUrl = Optional.of("   ");
        AgentCard defaultCard = config.publicAgentCard();
        assertEquals("http://localhost:8082", defaultCard.url());

        config.publicUrl = Optional.of("http://a2a-server:8082");
        AgentCard customCard = config.publicAgentCard();
        assertEquals("http://a2a-server:8082", customCard.url());
        assertEquals(3, customCard.skills().size());

        assertEquals("verify-travel-advisory",
                NomadA2aAgentConfiguration.resolveSkillId("Verify visa advisory", Map.of()));
        assertEquals("curate-neighborhood-cadence",
                NomadA2aAgentConfiguration.resolveSkillId("Curate neighborhood cadence", Map.of()));
        assertEquals("convert-and-audit-budget",
                NomadA2aAgentConfiguration.resolveSkillId(null, null));
        assertEquals("verify-travel-advisory",
                NomadA2aAgentConfiguration.resolveSkillId("Any prompt", Map.of("skillId", "verify-travel-advisory")));

        assertTrue(NomadA2aAgentConfiguration.extractExecutionContext(null).isEmpty());

        Message userMsg = Message.builder()
                .role(Message.Role.ROLE_USER)
                .parts(List.of(
                        new TextPart("Curate neighborhood cadence for Beirut"),
                        new DataPart(Map.of("city", "Beirut", "skillId", "curate-neighborhood-cadence"))))
                .build();
        RequestContext reqCtx = new RequestContext.Builder()
                .setParams(new MessageSendParams(userMsg, null, Map.of("focus", "Ottoman arches")))
                .setTaskId("t-1")
                .setContextId("ctx-1")
                .build();

        Map<String, Object> extracted = NomadA2aAgentConfiguration.extractExecutionContext(reqCtx);
        assertEquals("Beirut", extracted.get("city"));
        assertEquals("Ottoman arches", extracted.get("focus"));

        EventQueue noopQueue = new EventQueue() {
            @Override public void awaitQueuePollerStart() {}
            @Override public void signalQueuePollerStarted() {}
            @Override public void enqueueItem(EventQueueItem item) {}
            @Override public EventQueue tap() { return this; }
            @Override public EventQueueItem dequeueEventItem(int timeout) { return null; }
            @Override public int size() { return 0; }
            @Override public void close() {}
            @Override public void close(boolean immediate) {}
            @Override public void close(boolean immediate, boolean notify) {}
        };

        List<String> emitterCalls = new ArrayList<>();
        AgentEmitter recordingEmitter = new AgentEmitter(reqCtx, noopQueue) {
            @Override
            public void addTask(Task task) {
                emitterCalls.add("addTask");
            }

            @Override
            public void startWork() {
                emitterCalls.add("startWork");
            }

            @Override
            public void addArtifact(List<Part<?>> parts, String artifactId, String name, Map<String, Object> metadata) {
                emitterCalls.add("addArtifact:" + artifactId);
            }

            @Override
            public void complete(Message message) {
                emitterCalls.add("complete");
            }
        };

        AgentExecutor executor = config.agentExecutor(service);
        executor.execute(reqCtx, recordingEmitter);
        assertTrue(emitterCalls.contains("addTask"));
        assertTrue(emitterCalls.contains("startWork"));
        assertTrue(emitterCalls.contains("addArtifact:art-cadence-1"));
        assertTrue(emitterCalls.contains("complete"));
    }
}
