# SPEC-02: AG-UI SSE Frame Formatter & Flow.Publisher Reactive Adapters (`ag-ui-core`)

- **Linked Capability**: `CAP-02`
- **Depends On**: `CAP-01`
- **Module**: `ag-ui-core` (`src/main/java/org/agui/core/application/`, `src/main/java/org/agui/core/adapters/`)
- **Clean Architecture Layer**: Level 1 (Application Ports) & Level 2 (Adapters)
- **Status**: `ACTIVE`

## 1. Problem Statement & Technical Scope
AG-UI events must be formatted into standard Server-Sent Events (SSE) wire frames (`data: <json>\n\n`) with sub-10ms serialization latency overhead (`INV-05`) and bridged cleanly across `java.util.concurrent.Flow.Publisher` reactive streams without coupling `ag-ui-core` to Quarkus or Spring.

## 2. Architecture & Interface Ports

### 2.1 Application Port (`AgUiEventSerializer` & `AgUiSseFormatter`)
- `AgUiEventSerializer` (Level 1 Port): Serializes `AgUiEvent<?>` to JSON strings and deserializes JSON strings back into typed `AgUiEvent<?>` envelopes.
- `AgUiSseFormatter` (Level 1 Service): Formats `AgUiEvent<?>` into wire-compliant SSE frames (`id: <id>\nevent: <type>\ndata: <json>\n\n` or compact `data: <json>\n\n`) and parses incoming SSE frames.
- `AgUiFlowAdapter` (Level 2 Adapter): Maps `Flow.Publisher<AgUiEvent<?>>` into `Flow.Publisher<String>` SSE frames with backpressure preservation.

## 3. Acceptance Criteria (Gherkin / DoD)
- **AC-01**: Formatting any `AgUiEvent<T>` produces a valid SSE frame prefixed with `data: ` and terminated by `\n\n`.
- **AC-02**: Round-trip serialization/deserialization preserves `id`, `type`, `threadId`, `timestamp`, and nested payload fields (`RunStartedPayload`, `TextDeltaPayload`, `ToolCallPayload`, `ToolResultPayload`, `StatusPayload`, `RunFinishedPayload`).
- **AC-03**: Serialization overhead benchmark confirms $< 10\text{ms}$ per event batch (`INV-05`).
- **AC-04**: Dual-Gate verification passes with $\ge 85\%$ coverage and $\ge 80\%$ PITest mutation kill score.
