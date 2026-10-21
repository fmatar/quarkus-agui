# Product Requirements Document (PRD): Java/Quarkus Implementation of the AG-UI Protocol (`quarkus-agui`)

- **Author:** Agentic SDLC Product Autopilot (`sdlc-product`)
- **Status:** `APPROVED`
- **License:** Apache License 2.0
- **Constitution Link:** [`PRODUCT.md`](../../PRODUCT.md)

---

## 1. Executive Summary & Problem Statement

The **AG-UI (Agent–User Interaction Protocol)** standardizes real-time, bidirectional streaming interaction between AI agents and front-end user interfaces (covering run lifecycle events, streaming text deltas, tool call invocations, tool results, and intermediate thinking states). While TypeScript and Python ecosystems have early AG-UI bindings, **Java and JVM enterprise ecosystems lack a first-class, low-overhead AG-UI protocol library and Quarkus extension**.

**`quarkus-agui`** fills this gap through a disciplined 3-phase architecture:
1. **End-to-End Reference Prototype (`examples/quarkus-svelte-skeleton`)**: Quarkus + LangChain4j SSE backend paired with a SvelteKit + Skeleton UI streaming client.
2. **Core Java Library (`ag-ui-core`)**: Zero-dependency Java 17+ library containing standard AG-UI Event records, SSE wire formatters, and `Flow.Publisher` reactive adapters.
3. **Quarkus Extension (`quarkus-ag-ui`)**: Runtime and build-time deployment modules providing RESTEasy Reactive SSE auto-configuration, LangChain4j `TokenStream` & `@Tool` interceptors, GraalVM native image reflection (`ReflectiveClassBuildItem`), and a Quarkus Dev UI Inspector Card.

---

## 2. Target Personas & Goals

| Persona | Primary Need | Success Metric |
| :--- | :--- | :--- |
| **Java / JVM Library Author** | Framework-agnostic AG-UI records & SSE formatters (`ag-ui-core`) | Zero external framework dependencies; $< 10\text{ms}$ serialization overhead |
| **Quarkus AI Engineer** | Drop-in `quarkus-ag-ui` extension bridging LangChain4j `TokenStream` & `@Tool` to SSE | Zero boilerplate configuration; $< 50\text{MB}$ GraalVM native RSS |
| **Full-Stack Frontend Engineer** | Reactive SvelteKit + Skeleton UI reference client consuming AG-UI SSE frames | Real-time token streaming, tool call badges, and status locks |

---

## 3. User Stories & Acceptance Criteria

### US-001 (`CAP-01`): Core AG-UI Event Schema & Payload Records
**Description:** As a Java developer, I want immutable Java 17+ records for `AgUiEvent<T>` and all standard payloads (`RUN_STARTED`, `TEXT_DELTA`, `TOOL_CALL`, `TOOL_RESULT`, `STATUS`, `RUN_FINISHED`) so that I can construct type-safe AG-UI events without framework lock-in.
- [ ] `AgUiEvent<T>` envelope with `id`, `type`, `threadId`, `timestamp`, and `T payload`.
- [ ] Typed payload records: `RunStartedPayload`, `TextDeltaPayload`, `ToolCallPayload`, `ToolResultPayload`, `StatusPayload`, `RunFinishedPayload`.
- [ ] Zero Quarkus/Spring dependencies in `ag-ui-core`.

### US-002 (`CAP-02`): SSE Formatter & Flow.Publisher Reactive Adapters
**Description:** As a backend developer, I want to format `AgUiEvent<T>` instances into standard `data: <json>\n\n` SSE wire frames and adapt `Flow.Publisher<AgUiEvent<?>>` streams with sub-10ms latency overhead.
- [ ] Deterministic JSON serialization/deserialization and SSE framing (`data: ...\n\n`).
- [ ] `Flow.Publisher` reactive adapter preserving backpressure.
- [ ] Sub-10ms serialization overhead verified by benchmark tests.

### US-003 (`CAP-03`): Quarkus Runtime & LangChain4j TokenStream/Tool Bridge
**Description:** As a Quarkus engineer using LangChain4j, I want an `AgUiBridge` that attaches to `TokenStream` and `@Tool` executions and emits a SmallRye Mutiny `Multi<AgUiEvent<?>>` over `text/event-stream`.
- [ ] `onNext(token)` emits `TEXT_DELTA`.
- [ ] Tool execution emits `TOOL_CALL` and `TOOL_RESULT`.
- [ ] Stream start/completion/error emits `RUN_STARTED`, `STATUS`, and `RUN_FINISHED`.

### US-004 (`CAP-04`): Quarkus Deployment Processor, GraalVM Reflection & Dev UI Card
**Description:** As a Quarkus extension user, I want automatic GraalVM native reflection registration for all AG-UI records and a Dev UI Inspector Card at `/q/devui`.
- [ ] `ReflectiveClassBuildItem` registered for all `ag-ui-core` records and DTOs.
- [ ] JAX-RS `text/event-stream` route bindings registered at build time.
- [ ] Quarkus Dev UI Inspector Card displays event catalog and mock stream inspector.

### US-005 (`CAP-05`): Quarkus Extension JVM & Native Integration Tests
**Description:** As a maintainer, I want automated JVM and GraalVM Native integration tests verifying the extension end-to-end.
- [ ] `@QuarkusTest` and `@QuarkusIntegrationTest` suites with ephemeral port isolation (`quarkus.http.test-port=0`).

### US-006 (`CAP-06`): Reference Quarkus + LangChain4j Agent Backend
**Description:** As an application developer, I want a reference Quarkus endpoint (`POST /api/agent/run`) that runs an AI agent with tools and streams AG-UI events.
- [ ] `POST /api/agent/run` accepts `{ prompt, threadId }` and streams `text/event-stream` AG-UI events.

### US-007 (`CAP-07`): Reference SvelteKit + Skeleton UI Client
**Description:** As a frontend developer, I want a modular SvelteKit + Skeleton UI chat interface (`AgentChatContainer`, `MessageHistory`, `StreamRenderer`, `InputBar`) that parses AG-UI SSE frames in real time.
- [ ] `response.body.getReader()` line-by-line `\n\n` boundary parser and `data: ` JSON dispatcher.
- [ ] User message cards (`variant-filled-primary`), assistant cards (`variant-soft`), and AG-UI Tool Badges.

### US-008 (`CAP-08`): Full-Stack E2E Verification, Benchmarks & Showcase
**Description:** As an adopter, I want a 5-minute quickstart, BYOA contract, and verified NFR benchmarks.
- [ ] Sub-10ms serialization benchmark and `< 50MB` native RSS targets documented and tested.

---

## 4. Non-Goals (Out of Scope for v1.0)
- WebSocket or gRPC transport bindings (v1.0 focuses strictly on HTTP `POST` + Server-Sent Events `text/event-stream` per AG-UI specification).
- Multi-tenant database persistence for chat history (state is scoped per `threadId` in memory / pluggable port).
