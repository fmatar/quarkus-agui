# SPEC-06: Orthogonal Java 17 AG-UI Adapter Modules (`ag-ui-quarkus`, `ag-ui-langchain4j`, `ag-ui-quarkus-langchain4j`, `ag-ui-a2a`)

- **Linked Capability**: `CAP-06`
- **Depends On**: `CAP-05`
- **Tickets**: `#7`, `#30`, `#68`, `#74`, `#81`, `#100`
- **Modules**: `ag-ui-quarkus`, `ag-ui-langchain4j`, `ag-ui-quarkus-langchain4j`, `ag-ui-a2a`
- **Clean Architecture Layers (`ag-ui-quarkus` & `ag-ui-a2a`)**: `domain` (Level 0) <- `application` (Level 1) <- `adapters` (Level 2)
- **Status**: `AMENDED (ADR-007 & ADR-009)`

## 1. Problem Statement & Technical Scope
Provides 4 orthogonal Java 17 (`--release 17`) adapter modules over `com.ag-ui.community:0.1.1`:
- **`ag-ui-quarkus`**: `AgUiJacksonMixins`, `AgUiObjectMapperCustomizer`, `AgUiQuarkusBridge`, `AgUiWebSocketHandler`, and `AgUiSessionRegistry` (zero LangChain4j/A2A dependencies).
- **`ag-ui-langchain4j`**: Framework-agnostic `AgUiLangChain4jBridge`, per-run `AgUiLangChain4jRunSession`, RFC 6902 `AgUiStatePatch`, `AgUiMcpBridge`, and `AgUiRunOptions`.
- **`ag-ui-quarkus-langchain4j`**: `AgUiQuarkusLangChain4jBridge` adapting Quarkiverse `Multi<ChatEvent>` to `Multi<Event>` / SSE.
- **`ag-ui-a2a`**: `AgUiA2aClient`, `A2aDelegationResult`, and `AgUiA2aBridge` over official `org.a2aproject.sdk` `1.5.0.Final` (`A2ACardResolver`, `Client.builder(card)`, `ClientConfig`, `JSONRPCTransport`, `A2AHttpClientFactory`, `ClientCallContext`), emitting canonical `StepStartedEvent`, `ActivitySnapshotEvent`, and `StepFinishedEvent`.

## 2. Acceptance Criteria (DoD)
- **AC-01**: All 4 modules compile to Java 17 bytecode (`major version: 61`) with zero Archlens concentric layer inversions (`INV-03`).
- **AC-02**: Dual-Gate verification passes (`97%–100%` line coverage, `83%–97%` PITest mutation score).
