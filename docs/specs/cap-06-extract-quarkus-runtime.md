# SPEC-06: Extract `quarkus-ag-ui-runtime` (`AgUiBridge`, `AgUiMcpBridge`, `AgUiA2aBridge`, Mutiny SSE)

- **Linked Capability**: `CAP-06`
- **Depends On**: `CAP-05`
- **Ticket**: `#7`
- **Module**: `quarkus-extension/runtime`
- **Status**: `ACTIVE`

## 1. Problem Statement & Technical Scope
Extracts the Quarkus runtime components into `quarkus-extension/runtime` (`org.agui:quarkus-ag-ui`):
- `AgUiBridge`: Adapts LangChain4j `TokenStream` callbacks and tool invocations into SmallRye Mutiny `Multi<AgUiEvent<?>>` and SSE frames.
- `AgUiMcpBridge`: Enriches MCP tool invocations with `McpToolMetadata` and emits `TOOL_CALL` / `TOOL_RESULT`.
- `AgUiA2aBridge`: Wraps A2A task delegations into `STEP_STARTED`, `A2aHandoffPayload`, and `STEP_FINISHED` events.

## 2. Acceptance Criteria (Gherkin / DoD)
- **AC-01**: `AgUiBridge`, `AgUiMcpBridge`, and `AgUiA2aBridge` stream deterministic Mutiny `Multi<AgUiEvent<?>>` and formatted SSE frames.
- **AC-02**: Dual-Gate verification passes with $\ge 85\%$ coverage and $\ge 80\%$ PITest mutation kill score.
