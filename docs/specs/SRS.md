# Software Requirements Specification (SRS — IEEE 29148): `quarkus-agui`

- **Standard:** IEEE 29148-2018
- **Version:** `0.1.1`
- **System:** `org.agui` (`ag-ui-bom`, `ag-ui-quarkus`, `ag-ui-langchain4j`, `ag-ui-quarkus-langchain4j`, `ag-ui-a2a`, `packages/agui-svelte`, `examples/quarkus-svelte-skeleton`)
- **Constitution Link:** [`PRODUCT.md`](../../PRODUCT.md)
- **ADR Links:** [`ADR-001`](../adr/ADR-001-multi-module-maven-sveltekit-clean-architecture-.md), [`ADR-002`](../adr/ADR-002-example-first-tri-protocol-ag-ui-mcp-a2a-evoluti.md), [`ADR-003`](../adr/ADR-003-multi-extension-bom-and-concentric-transport-arc.md), [`ADR-004`](../adr/ADR-004-nomad-concierge-and-svelte5-agui-component-kit.md), [`ADR-005`](../adr/ADR-005-quarkus-langchain4j-mcp-and-smallrye-health.md), [`ADR-006`](../adr/ADR-006-framework-agnostic-module-topology-ag-ui-core-ag.md), [`ADR-007`](../adr/ADR-007-adopt-upstream-ag-ui-sdks-com-ag-ui-community-ag.md)

---

## 1. Functional Requirements (`FR-XX`)

- **FR-01 (`CAP-01`)**: `examples/quarkus-svelte-skeleton/mcp-server` (`:8081`) SHALL expose Nomad MCP `@Tool` endpoints (`fetchCityWeather`, `discoverLocalCultureAndMedia`, `updateTripItinerary`, `saveTravelerPreferences`, `sampleAcousticMood`, `resetNomadState`), `@ResourceTemplate` city dossiers, and SmallRye Health (`/q/health/live`, `/q/health/ready`) on Quarkus `3.40.1.1` and Java 25.
- **FR-02 (`CAP-01`)**: `examples/quarkus-svelte-skeleton/a2a-server` (`:8082`) SHALL expose an official A2A `1.4.0.Final` `AgentCard` (`GET /.well-known/agent-card.json`), JSON-RPC 2.0 transport, and `/api/a2a/audit` delegating `convert-and-audit-budget` and `verify-travel-advisory`.
- **FR-03 (`CAP-02`)**: `examples/quarkus-svelte-skeleton/backend` (`:8080`) SHALL orchestrate `@RegisterAiService` (`NomadConciergeAiService`) with `@McpToolBox("nomad")` and A2A delegation on Java 25 Virtual Threads, serving the SvelteKit 5 SPA via Quarkus Quinoa (`2.9.3`).
- **FR-04 (`CAP-03`, `ADR-007`)**: The orchestrator SHALL accept canonical `RunAgentInput` over `POST /api/agent/run` (`text/event-stream`) and `/api/agent/ws` (`WebSockets Next`), emit RFC 6902 `StateDeltaEvent` patches (`List<JsonPatchOperation>`), attach Tri-Protocol provenance in `Event.rawEvent()`, and emit native AG-UI `InterruptOutcome(List<Interrupt>)` on `RunFinishedEvent` resumed via `RunAgentInput.resume()`.
- **FR-05 (`CAP-04`, `ADR-007`)**: `packages/agui-svelte` (`@agui/svelte`) SHALL provide a headless, CSS-framework-free Svelte 5 Runes & JS client wrapping `@ag-ui/client` (`HttpAgent`, `WebSocketAgent extends AbstractAgent`), immutable RFC 6902 `applyJsonPatch` (rejecting `__proto__`, `constructor`, `prototype`), and `createAgUiStore`.
- **FR-06 (`CAP-05`, `ADR-007`)**: `ag-ui-bom` SHALL manage upstream `com.ag-ui.community:{java-core,java-server,java-client}:0.1.1` alongside the 4 orthogonal Java 17 (`--release 17`) `org.agui` library modules with zero custom duplicate event POJOs.
- **FR-07 (`CAP-06`, `ADR-007`)**: `ag-ui-quarkus`, `ag-ui-langchain4j`, `ag-ui-quarkus-langchain4j`, and `ag-ui-a2a` SHALL decouple Quarkus HTTP/WS/Jackson transport, upstream `dev.langchain4j` `TokenStream` lifecycle, Quarkiverse `Multi<ChatEvent>` streaming, and A2A `Task` step bridges respectively.
- **FR-08 (`CAP-07`, `CAP-08`, `ADR-006`, `ADR-007`)**: `ag-ui-quarkus` SHALL auto-configure Jackson wire MixIns (`AgUiObjectMapperCustomizer`) and ship `META-INF/native-image/org.agui/ag-ui-quarkus/reflect-config.json` enabling GraalVM / Mandrel JDK 25 native compilation without Quarkus `-deployment` build-time processors.

---

## 2. Non-Functional Requirements (`NFR-XX`)

- **NFR-01 (Latency — `INV-05`)**: Jackson MixIn event serialization and SSE/WebSocket framing SHALL add `< 10ms` p99 latency overhead.
- **NFR-02 (Memory Footprint — `INV-06`)**: Portable GraalVM / Mandrel 25 native binary (`-Dnative` or `-Dnative-container`) SHALL compile cleanly with all AG-UI event hierarchies registered for reflection and maintain `< 50MB` baseline RSS.
- **NFR-03 (Orthogonal Zero Configuration — `INV-07`)**: Importing `ag-ui-quarkus` SHALL auto-configure CDI beans and Jackson MixIns without pulling LangChain4j or A2A dependencies; all modules are dual-licensed under `Apache-2.0 OR MIT`.
- **NFR-04 (Dual-Gate Quality — `INV-02`)**: All Java and JS modules SHALL maintain `>= 85%` line/branch diff coverage, `>= 80%` mutation kill score (PITest `1.20.1` / AST mutator), and `CRAP < 10` per method/function.
- **NFR-05 (Clean Architecture — `INV-03`)**: Source dependencies in `ag-ui-quarkus` SHALL strictly point inward (`adapters -> application -> domain`) verified by Archlens (`.archlens/policy.json`).
- **NFR-06 (Multi-Release & Security Standards — `INV-08`)**: Java 17 (`major 61`) for reusable libraries; Java 25 (`major 69`) + Quarkus `3.40.1.1` for showcase; `pnpm` workspaces, OWASP XSS URL/attribute sanitization, CWE-1321 JSON Pointer guards, and WCAG 2.1 AA contrast (`>= 4.5:1`) on frontend.
