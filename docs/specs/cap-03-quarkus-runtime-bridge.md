# SPEC-03: Quarkus AG-UI Runtime & LangChain4j TokenStream/Tool Bridge (`quarkus-ag-ui-runtime`)

- **Linked Capability**: `CAP-03`
- **Depends On**: `CAP-01`, `CAP-02`
- **Module**: `quarkus-extension/runtime` (`src/main/java/org/agui/quarkus/runtime/`)
- **Clean Architecture Layer**: Level 2 (Adapters) & Level 3 (Infrastructure)
- **Status**: `ACTIVE`

## 1. Problem Statement & Technical Scope
Quarkus applications using LangChain4j need a zero-boilerplate bridge (`AgUiBridge`) that converts LangChain4j `TokenStream` callbacks and `@Tool` executions into SmallRye Mutiny `Multi<AgUiEvent<?>>` streams and RESTEasy Reactive `text/event-stream` responses.

## 2. Runtime Components
1. **`AgUiBridge`**:
   - Emits initial `RUN_STARTED` and `STATUS("thinking", ...)` events when a stream subscribes.
   - `onNext(String token)` $\rightarrow$ Emits `TEXT_DELTA` event.
   - `onToolExecuted` / tool invocation interceptor $\rightarrow$ Emits `STATUS("executing_tool", ...)`, `TOOL_CALL`, and `TOOL_RESULT` events.
   - `onComplete` $\rightarrow$ Emits `RUN_FINISHED` with status `completed`.
   - `onError(Throwable)` $\rightarrow$ Emits `RUN_FINISHED` with status `failed` and error message before completing gracefully.
2. **Mutiny & RESTEasy Reactive SSE Auto-Configuration**:
   - Adapts `Multi<AgUiEvent<?>>` to SSE frames (`text/event-stream`) with automatic Jackson serialization.

## 3. Acceptance Criteria (Gherkin / DoD)
- **AC-01**: Subscribing to `AgUiBridge.stream(threadId, tokenStreamSource)` emits deterministic `RUN_STARTED` $\rightarrow$ `STATUS` $\rightarrow$ `TEXT_DELTA`* $\rightarrow$ (`TOOL_CALL` $\rightarrow$ `TOOL_RESULT`)* $\rightarrow$ `RUN_FINISHED` event sequences.
- **AC-02**: Error during streaming emits a terminal `RUN_FINISHED` event with `status = "failed"` rather than dropping the connection silently.
- **AC-03**: Dual-Gate verification passes with $\ge 85\%$ coverage and $\ge 80\%$ PITest mutation score.
