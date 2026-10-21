# SPEC-01: AG-UI Core Event Schema & Payload Records (`ag-ui-core`)

- **Linked Capability**: `CAP-01`
- **Module**: `ag-ui-core` (`src/main/java/org/agui/core/domain/`)
- **Clean Architecture Layer**: Level 0 (Domain Core)
- **Status**: `ACTIVE`

## 1. Problem Statement & Domain Scope
Java/JVM ecosystems lack a canonical, framework-agnostic implementation of the **AG-UI (Agent–User Interaction Protocol)** event envelope and typed payloads. `ag-ui-core` Level 0 Domain Core defines immutable Java 17+ records for all AG-UI event primitives with zero external framework dependencies (`INV-04`).

## 2. Canonical Event Schema & Records

### 2.1 Event Envelope (`AgUiEvent<T>`)
```java
package org.agui.core.domain;

public record AgUiEvent<T>(
    String id,
    String type,
    String threadId,
    long timestamp,
    T payload
) {}
```

### 2.2 Supported AG-UI Event Types (`AgUiEventType`)
| Event Type | Direction | Payload Record | Description |
| :--- | :--- | :--- | :--- |
| `RUN_STARTED` | Backend $\rightarrow$ Frontend | `RunStartedPayload(String threadId, String status)` | Emitted when an agent session/run initializes. |
| `TEXT_DELTA` | Backend $\rightarrow$ Frontend | `TextDeltaPayload(String content)` | Streamed chunks of LLM output text tokens. |
| `TOOL_CALL` | Backend $\rightarrow$ Frontend | `ToolCallPayload(String callId, String tool, Map<String, Object> args)` | Signals agent intent to execute a specific tool with arguments. |
| `TOOL_RESULT` | Backend $\rightarrow$ Frontend | `ToolResultPayload(String callId, String tool, Object result, boolean isError)` | Emits output or response payload after tool execution. |
| `STATUS` | Backend $\rightarrow$ Frontend | `StatusPayload(String state, String message)` | System lifecycle/thinking state changes (`thinking`, `executing_tool`). |
| `RUN_FINISHED` | Backend $\rightarrow$ Frontend | `RunFinishedPayload(String threadId, String status, String error)` | Marks run termination (`completed`, `failed`, `interrupted`). |

## 3. Acceptance Criteria (Gherkin / DoD)
- **AC-01**: All records enforce non-null validation on required fields (`id`, `type`, `threadId`, non-negative `timestamp`) via compact constructors.
- **AC-02**: Factory helper methods on `AgUiEvent` generate deterministic or UUID-backed envelopes with `Instant.now().toEpochMilli()`.
- **AC-03**: `ToolCallPayload` defensively copies `args` (`Map.copyOf`) to guarantee deep record immutability.
- **AC-04**: Dual-Gate verification passes with $\ge 85\%$ line/branch coverage and $\ge 80\%$ PITest mutation kill score.
