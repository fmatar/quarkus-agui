# Product Requirements Document (PRD): Java/Quarkus Implementation of the AG-UI Protocol (`quarkus-agui`)

- **Author:** Agentic SDLC Product Autopilot (`sdlc-product`)
- **Status:** `APPROVED (Amended by ADR-004, ADR-005, ADR-006, ADR-007, ADR-008 & ADR-009)`
- **Version:** `0.2.0`
- **License:** Apache-2.0 OR MIT
- **Constitution Link:** [`PRODUCT.md`](../../PRODUCT.md)
- **ADR Links:** [`ADR-001`](../adr/ADR-001-multi-module-maven-sveltekit-clean-architecture-.md), [`ADR-002`](../adr/ADR-002-example-first-tri-protocol-ag-ui-mcp-a2a-evoluti.md), [`ADR-003`](../adr/ADR-003-multi-extension-bom-and-concentric-transport-arc.md), [`ADR-004`](../adr/ADR-004-nomad-concierge-and-svelte5-agui-component-kit.md), [`ADR-005`](../adr/ADR-005-quarkus-langchain4j-mcp-and-smallrye-health.md), [`ADR-006`](../adr/ADR-006-framework-agnostic-module-topology-ag-ui-core-ag.md), [`ADR-007`](../adr/ADR-007-adopt-upstream-ag-ui-sdks-com-ag-ui-community-ag.md), [`ADR-008`](../adr/ADR-008-canonical-ag-ui-activity-snapshot-surface-kit-co.md), [`ADR-009`](../adr/ADR-009-adopt-official-a2a-java-sdk-1-5-0-final-client-a.md)

---

## 1. Executive Summary & Problem Statement

The **AG-UI (Agent–User Interaction Protocol)** standardizes real-time, bidirectional streaming interaction between AI agents and front-end user interfaces, working alongside **MCP (Model Context Protocol)** for tool/resource discovery and **A2A (Agent-to-Agent Protocol)** for specialist sub-agent delegation.

**`quarkus-agui` (`v0.2.0`)** delivers orthogonal **Java 17** adapters built directly on upstream `com.ag-ui.community:{java-core,java-server,java-client}:0.1.1`, a headless **Svelte 5 Runes** client library (`@agui/svelte`) built on `@ag-ui/client` + `@ag-ui/core` (`^1.0.4`), and the **Nomad Tri-Protocol (`AG-UI + MCP + A2A`) Travel & Culture Concierge** showcase running on **Java 25 + Virtual Threads + Quarkus `3.40.1.1` (JVM & GraalVM/Mandrel Native)**:

1. **Java 17 Orthogonal Library Modules (`org.agui` managed by `ag-ui-bom`)**:
   - `ag-ui-quarkus`: Quarkus CDI `AgUiObjectMapperCustomizer` (Jackson MixIns matching `@ag-ui/core` Zod schemas), `AgUiQuarkusBridge` (Mutiny `Multi` SSE), reusable `AgUiWebSocketHandler` + `AgUiSessionRegistry`, and 57-type GraalVM `reflect-config.json` with zero LangChain4j/A2A lock-in.
   - `ag-ui-langchain4j`: Framework-agnostic `dev.langchain4j` `TokenStream` & `Agent` bridge (`AgUiLangChain4jBridge`, per-run `AgUiLangChain4jRunSession`, `AgUiRunOptions` `postToolActivityProvider` & `postRunActivityProvider` `ActivitySnapshotEvent` hooks, RFC 6902 `AgUiStatePatch`, `AgUiMcpBridge`, and native HITL `InterruptOutcome`).
   - `ag-ui-quarkus-langchain4j`: Reactive Quarkiverse `Multi<ChatEvent>` adapter (`AgUiQuarkusLangChain4jBridge`).
   - `ag-ui-a2a`: Official `org.a2aproject.sdk` `1.5.0.Final` client & event bridge (`AgUiA2aClient` wrapping `A2ACardResolver` + `Client.builder(card)` + `JSONRPCTransport` + `A2AHttpClientFactory`, `A2aDelegationResult`, and `AgUiA2aBridge` emitting canonical `StepStartedEvent`, `ActivitySnapshotEvent`, and `StepFinishedEvent`).
2. **Headless Svelte 5 SDK (`packages/agui-svelte` / `@agui/svelte`)**:
   - Wraps `@ag-ui/client` (`HttpAgent`, `AbstractAgent`) and `@ag-ui/core` with full-duplex `WebSocketAgent`, immutable RFC 6902 `applyJsonPatch` (with CWE-1321 prototype-pollution guards), `rawEvent` Tri-Protocol taxonomy extraction, canonical activity surface helpers (`AGUI_ACTIVITY_TYPES`, `createActivityRegistry`, `sanitizeExternalUrl`, `buildPlaceMapLinks`), and `createAgUiStore` / `useAgUiAgent`.
3. **Nomad Tri-Protocol Reference Showcase (`examples/quarkus-svelte-skeleton`)**:
   - `mcp-server` (`:8081`), `a2a-server` (`:8082`), and `backend` (`:8080`, bundling the SvelteKit 5 + Skeleton v4 + Tailwind v4 SPA via **Quarkus Quinoa** and packaging daemonless **Jib** containers).

---

## 2. Target Personas & Goals

| Persona | Primary Need | Success Metric |
| :--- | :--- | :--- |
| **Java 17+ / JVM Framework Author** | Framework-agnostic LangChain4j & A2A bridges over `com.ag-ui.community` | `--release 17` compatibility; zero duplicate event POJOs; `< 10ms` framing |
| **Quarkus 3.40.1.1 AI Engineer** | Drop-in `ag-ui-quarkus` & `ag-ui-quarkus-langchain4j` modules for SSE, WebSockets Next, and GraalVM Native | Zero boilerplate; zero unwanted transitive deps; `< 50MB` native RSS |
| **Full-Stack Svelte 5 Engineer** | Headless `@agui/svelte` SDK + 3-Pane Nomad Studio reference UI | Real-time token streaming, RFC 6902 `STATE_DELTA`, MCP/A2A telemetry, and native HITL `resume` |

---

## 3. User Stories & Acceptance Criteria

### US-001 (`CAP-01`, #2, #32, #76, #78, #100): Quarkus MCP Server (`:8081`) & Quarkus A2A Server (`:8082`)
- [x] `mcp-server` (`:8081`) exposes 6 `@Tool` endpoints (`fetchCityWeather`, `discoverLocalCultureAndMedia`, `updateTripItinerary`, `saveTravelerPreferences`, `sampleAcousticMood`, `resetNomadState`), `@ResourceTemplate` city dossiers, and `/q/health/ready`.
- [x] `a2a-server` (`:8082`) exposes official A2A `1.5.0.Final` `GET /.well-known/agent-card.json` (`Nomad-A2A-Specialist`) and JSON-RPC 2.0 `POST /` delegation (`convert-and-audit-budget` via Frankfurter + `open.er-api.com` fallback, `verify-travel-advisory` via REST Countries v5, and `curate-neighborhood-cadence`) with zero bespoke REST audit side-doors.

### US-002 (`CAP-02`, #3, #59, #68): Nomad Quarkus Orchestrator (`:8080`) & Quinoa SvelteKit 5 SPA
- [x] `@RegisterAiService` (`NomadConciergeAiService`) + `@McpToolBox("nomad")` orchestrator on Java 25 Virtual Threads (`@RunOnVirtualThread`) bundling the SvelteKit 5 SPA via Quarkus Quinoa and Jib containers.

### US-003 (`CAP-03`, #4, #51, #85): Canonical AG-UI Streaming, RFC 6902 State & Native HITL
- [x] Streams canonical AG-UI events over `POST /api/agent/run` (`text/event-stream`) and `/api/agent/ws` (`WebSockets Next`), RFC 6902 `StateDeltaEvent` patches, and native `InterruptOutcome` resumed via `RunAgentInput.resume()`.

### US-004 (`CAP-04`, #5, #29, #83, #85, #93): Headless `@agui/svelte`, 5-Card `ACTIVITY_SNAPSHOT` Surface Kit & Complete Trip Dossier Report
- [x] Headless `packages/agui-svelte` (`HttpAgent`, `WebSocketAgent`, `createAgUiStore`, `createActivityRegistry`, `buildPlaceMapLinks`) + 3-Pane Nomad Studio (`AgentChatContainer.svelte`, `TripDossierPanel.svelte`, `ProtocolInspector.svelte`) rendering canonical `ACTIVITY_SNAPSHOT` cards (`AgUiWeatherCard`, `AgUiLocationMapCard`, `AgUiSecurityAdvisoryCard`, `AgUiFxBudgetCard`, `AgUiCultureAudioCard`, `AgUiActivityRenderer`) inline and in the Complete Trip Dossier Report (`ADR-008`).

### US-005 (`CAP-05`, #6, #81, #85): Upstream `com.ag-ui.community` Java 17 Adoption (`ADR-007`)
- [x] Retired custom `ag-ui-core` in favor of upstream `com.ag-ui.community:{java-core,java-server,java-client}:0.1.1` managed by `ag-ui-bom`.

### US-006 (`CAP-06`, #7, #74, #81, #100): Orthogonal Java 17 Bridges (`ag-ui-quarkus`, `ag-ui-langchain4j`, `ag-ui-quarkus-langchain4j`, `ag-ui-a2a`)
- [x] Four focused Java 17 (`major version: 61`) modules governed by Archlens concentric rings (`domain <- application <- adapters`).

### US-007 (`CAP-07`, #8, #74, #81): Standard CDI Discovery & GraalVM 25 Reflection Metadata
- [x] Standard `META-INF/beans.xml` + `AgUiObjectMapperCustomizer` + 57-type `reflect-config.json` verified under `Mandrel-25.0.5.0-Final`.

### US-008 (`CAP-08`, #9, #85, #87, #93, #95): Full Showcase Cutover, `local-quarkus-agui` CI & `v0.2.0` Release
- [x] Live 3-service E2E stack verified against `@ag-ui/client` Zod wire schemas and 5 canonical `ACTIVITY_SNAPSHOT` surfaces; self-hosted `local-quarkus-agui` GitHub Actions CI, Dual-Gate coverage (`>= 85%`), and mutation kill scores (`85%–97%`) verified across all modules for `v0.2.0`.
