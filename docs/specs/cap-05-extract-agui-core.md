# SPEC-05: Extract `ag-ui-core` Standalone Java 25 Library & Refactor Showcase

- **Linked Capability**: `CAP-05`
- **Depends On**: `CAP-03`, `CAP-04`
- **Ticket**: `#6`
- **Module**: `ag-ui-core` & `examples/quarkus-svelte-skeleton/backend`
- **Clean Architecture Layers**: `domain` (Level 0) $\leftarrow$ `application` (Level 1) $\leftarrow$ `adapters` (Level 2)
- **Status**: `ACTIVE`

## 1. Problem Statement & Technical Scope
Extracts the battle-tested AG-UI event envelopes (`AgUiEvent<T>`), typed payload records (`RunStartedPayload`, `TextDeltaPayload`, `ToolCallPayload`, `ToolResultPayload`, `StatusPayload`, `RunFinishedPayload`, `StepPayload`, `StateSnapshotPayload`, `StateDeltaPayload`, `A2aHandoffPayload`, `McpToolMetadata`), sub-10ms `AgUiSseFormatter`, and `Flow.Publisher` reactive adapters (`AgUiFlowAdapter`) from the showcase into `ag-ui-core` (Plain Java 25, zero external framework dependencies), and refactors the showcase backend to use `ag-ui-core`.

## 2. Acceptance Criteria (Gherkin / DoD)
- **AC-01**: `ag-ui-core` has zero Quarkus/Spring runtime dependencies (`INV-04`) and passes Archlens concentric layer checks (`INV-03`).
- **AC-02**: `examples/quarkus-svelte-skeleton/backend` deletes its inline AG-UI model classes and imports `org.agui.core.*`.
- **AC-03**: Dual-Gate verification passes with $\ge 85\%$ coverage and $\ge 80\%$ PITest mutation kill score.
