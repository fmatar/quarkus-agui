# Software Requirements Specification (SRS — IEEE 29148): `quarkus-agui`

- **Standard:** IEEE 29148-2018
- **System:** `ag-ui-java` (`ag-ui-core`, `quarkus-ag-ui`, `examples/quarkus-svelte-skeleton`)
- **Constitution Link:** [`PRODUCT.md`](../../PRODUCT.md)

---

## 1. Functional Requirements (`FR-XX`)

- **FR-01 (`CAP-01`)**: `ag-ui-core` SHALL define the generic immutable record `AgUiEvent<T>(String id, String type, String threadId, long timestamp, T payload)` in Java 17+.
- **FR-02 (`CAP-01`)**: `ag-ui-core` SHALL support the 6 core AG-UI event types: `RUN_STARTED`, `TEXT_DELTA`, `TOOL_CALL`, `TOOL_RESULT`, `STATUS`, and `RUN_FINISHED`.
- **FR-03 (`CAP-02`)**: `ag-ui-core` SHALL provide an SSE formatter emitting `data: <json>\n\n` wire frames and a `Flow.Publisher` reactive stream mapper.
- **FR-04 (`CAP-03`)**: `quarkus-ag-ui-runtime` SHALL provide `AgUiBridge` adapting LangChain4j `TokenStream` callbacks (`onNext`, `onToolExecuted`, `onComplete`, `onError`) into SmallRye Mutiny `Multi<AgUiEvent<?>>`.
- **FR-05 (`CAP-04`)**: `quarkus-ag-ui-deployment` SHALL emit `ReflectiveClassBuildItem` for all `ag-ui-core` event records and payloads to support GraalVM native compilation without manual `reflection-config.json`.
- **FR-06 (`CAP-04`)**: `quarkus-ag-ui-deployment` SHALL contribute an **AG-UI Inspector** card to Quarkus Dev UI (`/q/devui`).
- **FR-07 (`CAP-06`)**: The reference backend SHALL expose `POST /api/agent/run` accepting JSON `{ "prompt": "...", "threadId": "..." }` and returning `Content-Type: text/event-stream`.
- **FR-08 (`CAP-07`)**: The SvelteKit client SHALL consume `POST /api/agent/run` via `ReadableStreamDefaultReader`, split frames on `\n\n`, parse `data: ` JSON payloads, and render `AgentChatContainer.svelte`, `MessageHistory.svelte`, `StreamRenderer.svelte`, and `InputBar.svelte`.

---

## 2. Non-Functional Requirements (`NFR-XX`)

- **NFR-01 (Latency — `INV-05`)**: Core event serialization and SSE framing SHALL add $< 10\text{ms}$ overhead.
- **NFR-02 (Memory Footprint — `INV-06`)**: Native GraalVM image binary SHALL maintain $< 50\text{MB}$ RSS baseline.
- **NFR-03 (Zero Configuration — `INV-07`)**: Importing `quarkus-ag-ui` SHALL auto-configure Jackson and RESTEasy Reactive SSE bindings without manual bean wiring.
- **NFR-04 (Dual-Gate Quality — `INV-02`)**: All modules SHALL maintain $\ge 85\%$ line/branch diff coverage and $\ge 80\%$ mutation kill score (PITest / Stryker).
- **NFR-05 (Clean Architecture — `INV-03`)**: Source dependencies SHALL strictly point inward (`Adapters -> Application -> Domain Core`) verified by Archlens (`.archlens/policy.json`).
