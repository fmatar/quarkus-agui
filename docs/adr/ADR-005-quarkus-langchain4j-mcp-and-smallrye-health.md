# ADR-005: Quarkus LangChain4j Reactive AG-UI Bridge, Declarative `@McpToolBox` & SmallRye Health + REST Countries v5

- **Status:** `accepted`
- **Date:** `2026-11-09`
- **Triggered By:** `#68` (`feature/issue-#68-quarkus-langchain4j-mcp-v5`)
- **Scope:** `technical`
- **Amends / Realizes:** `CAP-03`, `CAP-04`, `CAP-06`, `CAP-07`, `CAP-08`, `INV-02`, `INV-03`, `INV-07`

---

## 1. Context
1. **Custom MCP Client Boilerplate vs. Idiomatic Quarkus LangChain4j MCP**: The Nomad showcase previously wrapped upstream `DefaultMcpClient` and manual JDK `HttpClient` calls inside `McpGatewayClient.java` alongside a regex/deterministic routing path in `NomadConciergeOrchestratorService.java`, bypassing Quarkus's native Vert.x `QuarkusStreamableHttpMcpTransport`, `@RegisterAiService` + `@McpToolBox`, and `McpClientHealthCheck`.
2. **Custom Health Maps vs. MicroProfile / SmallRye Health**: Health probes previously used bespoke `/api/*/health` JSON envelopes rather than standard Quarkus SmallRye Health (`/q/health/ready`).
3. **REST Countries v3.1 Deprecation**: Upstream `restcountries.com/v3.1` was deprecated in favor of authenticated **REST Countries v5** (`https://api.restcountries.com/countries/v5/names.common/{country}`).

## 2. Decision
1. **New `quarkus-ag-ui-langchain4j` Extension Module (`org.agui:quarkus-ag-ui-langchain4j` + `-deployment`)**:
   - Added to `quarkus-extension/langchain4j/runtime/` and `quarkus-extension/langchain4j/deployment/` and managed in `quarkus-ag-ui-bom`.
   - Provides `AgUiLangChain4jBridge`, `AgUiRunOptions`, and `AgUiToolClassifier` to translate Quarkus LangChain4j `Multi<ChatEvent>` streams (`BeforeToolExecutionEvent`, `ToolExecutedEvent`, `PartialResponseEvent`, `AccumulatedResponseEvent`, `ChatCompletedEvent`) and guardrail exceptions directly into non-blocking AG-UI protocol events (`RUN_STARTED`, `STATE_SNAPSHOT`, `STEP_STARTED`/`STEP_FINISHED`, `TOOL_CALL`/`TOOL_RESULT`, `STATE_DELTA`, `TEXT_DELTA`, `RUN_FINISHED`).
2. **Declarative `@ApplicationScoped @RegisterAiService` + `@McpToolBox("nomad")` + `@McpClientName("nomad")`**:
   - Deleted custom `McpGatewayClient.java` and removed the `src/main` `"deterministic"` engine.
   - Configured `quarkus.langchain4j.mcp.nomad.*` (`transport-type=streamable-http` over Vert.x `QuarkusStreamableHttpMcpTransport`) in `application.properties`, wired `@ApplicationScoped @RegisterAiService NomadConciergeAiService` with `@McpToolBox("nomad")`, `@InputGuardrails(TravelSafetyInputGuardrail.class)`, `@MemoryId` backed by `NomadChatMemoryStore` (`InMemoryChatMemoryStore`), `NomadA2aAgentTools`, and `NomadUiSurfaceTools`, and exposed `saveTravelerPreferences`, `sampleAcousticMood`, and `resetNomadState` as native `@Tool` methods on `mcp-server`.
3. **Standard MicroProfile / SmallRye Health (`/q/health/ready`)**:
   - Enabled `io.quarkus:quarkus-smallrye-health` across `mcp-server` (`:8081`), `a2a-server` (`:8082`), and `backend` (`:8080`), combining `quarkus-langchain4j-mcp`'s built-in `McpClientHealthCheck` with `@Readiness` `HealthCheck` beans (`NomadMcpReadinessCheck`, `NomadA2aReadinessCheck`, `NomadBackendReadinessChecks`) and wiring `/q/health/ready` into Quinoa, Vite, and `agui-client.js` (`summarizeHealthReport` handling HTTP `200` and `503`).
4. **Authenticated REST Countries v5 (`api.restcountries.com`)**:
   - Upgraded `NomadTravelAdvisorService` (`a2a-server`) and `NomadA2aReadinessCheck` to `https://api.restcountries.com/countries/v5/names.common/{country}` with `Authorization: Bearer <RESTCOUNTRIES_API_KEY>` loaded via environment configuration while preserving strict `isError: true` / `503 DOWN` diagnostics when unconfigured or unreachable.

## 3. Consequences
- **Positive:** Showcases idiomatic Quarkus + Quarkus LangChain4j + Quarkus MCP + SmallRye Health architecture end-to-end with reusable `quarkus-ag-ui-langchain4j` extension primitives, verified live REST Countries v5 travel advisories, and `>= 85%` JaCoCo / `>= 80%` PITest Dual-Gate craftsmanship across all modules.
