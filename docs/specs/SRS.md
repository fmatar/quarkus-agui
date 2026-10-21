# Software Requirements Specification (SRS — IEEE 29148): `quarkus-agui`

- **Standard:** IEEE 29148-2018
- **System:** `ag-ui-java` (`ag-ui-core`, `quarkus-ag-ui`, `examples/quarkus-svelte-skeleton`)
- **Constitution Link:** [`PRODUCT.md`](../../PRODUCT.md)
- **ADR Links:** [`ADR-001`](../adr/ADR-001-multi-module-maven-sveltekit-clean-architecture-fo.md), [`ADR-002`](../adr/ADR-002-example-first-tri-protocol-ag-ui-mcp-a2a-evoluti.md)

---

## 1. Functional Requirements (`FR-XX`)

- **FR-01 (`CAP-01`)**: `examples/quarkus-svelte-skeleton/mcp-server` (`:8081`) SHALL expose deterministic MCP tools (`inspectClusterHealth`, `queryServiceLogs`, `scaleDeployment`) and `cluster://topology` resource on Quarkus `3.40.1.1` and Java 25.
- **FR-02 (`CAP-01`)**: `examples/quarkus-svelte-skeleton/a2a-server` (`:8082`) SHALL expose an A2A `AgentCard` (`GET /.well-known/agent.json`) and task endpoint (`POST /api/a2a/tasks/send`) executing `audit-clean-architecture`, `assess-security-posture`, and `plan-incident-remediation`.
- **FR-03 (`CAP-02`)**: `examples/quarkus-svelte-skeleton/backend` (`:8080`) SHALL provide a Dual-Mode LangChain4j Orchestrator invoking `:8081` MCP and `:8082` A2A, paired with a `pnpm`-managed SvelteKit + `@skeletonlabs/skeleton-svelte` + `@lucide/svelte` frontend (`:5173`).
- **FR-04 (`CAP-03`)**: The orchestrator SHALL emit all 10 AG-UI event types (`RUN_STARTED`, `STATUS`, `TEXT_DELTA`, `TOOL_CALL`, `TOOL_RESULT`, `RUN_FINISHED`, `STEP_STARTED`, `STEP_FINISHED`, `STATE_SNAPSHOT`, `STATE_DELTA`) and enforce Human-in-the-Loop approval (`status: "interrupted"`, `POST /api/agent/approve`) when scaling above 5 replicas.
- **FR-05 (`CAP-04`)**: The SvelteKit frontend SHALL consume `POST /api/agent/run` via `ReadableStreamDefaultReader`, split frames on `\n\n`, parse `data: ` JSON payloads, support `AbortController` stream cancellation, and render the 3-Pane AG-UI Workbench.
- **FR-06 (`CAP-05`)**: `ag-ui-core` SHALL extract the generic immutable Java 25 record `AgUiEvent<T>`, all typed payload records, `AgUiSseFormatter`, `AgUiEventSerializer`, and `AgUiFlowAdapter` (`Flow.Publisher`) with zero framework dependencies.
- **FR-07 (`CAP-06`)**: `quarkus-ag-ui-runtime` SHALL provide `AgUiBridge`, `AgUiMcpBridge`, and `AgUiA2aBridge` adapting LangChain4j, MCP, and A2A callbacks into SmallRye Mutiny `Multi<AgUiEvent<?>>`.
- **FR-08 (`CAP-07`, `CAP-08`)**: `quarkus-ag-ui-deployment` SHALL emit `ReflectiveClassBuildItem` for all `ag-ui-core` records for GraalVM 25 native compilation and contribute an **AG-UI Inspector** card to Quarkus Dev UI (`/q/devui`).

---

## 2. Non-Functional Requirements (`NFR-XX`)

- **NFR-01 (Latency — `INV-05`)**: Core event serialization and SSE framing SHALL add $< 10\text{ms}$ p99 latency overhead.
- **NFR-02 (Memory Footprint — `INV-06`)**: Portable GraalVM 25 native binary (`-Dnative` local GraalVM 25 or `-Dnative-container` Mandrel JDK 25 fallback) SHALL maintain $< 50\text{MB}$ RSS baseline.
- **NFR-03 (Zero Configuration — `INV-07`)**: Importing `quarkus-ag-ui` SHALL auto-configure Jackson and RESTEasy Reactive SSE bindings without manual bean wiring.
- **NFR-04 (Dual-Gate Quality — `INV-02`)**: All modules SHALL maintain $\ge 85\%$ line/branch diff coverage and $\ge 80\%$ mutation kill score (PITest 1.20.1 / Stryker).
- **NFR-05 (Clean Architecture — `INV-03`)**: Source dependencies SHALL strictly point inward (`Adapters -> Application -> Domain Core`) verified by Archlens (`.archlens/policy.json`).
- **NFR-06 (Toolchain Standards — `INV-08`)**: Java 25 + Quarkus `3.40.1.1` via `./mvnw` on backend; `pnpm` + Svelte 5 Runes + `@skeletonlabs/skeleton-svelte` + `@lucide/svelte` on frontend.
