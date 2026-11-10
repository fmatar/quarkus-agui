# SPEC-03: Canonical AG-UI Protocol Orchestration (SSE + WebSockets + RFC 6902 State + Native HITL)

- **Linked Capability**: `CAP-03`
- **Depends On**: `CAP-02`
- **Tickets**: `#4`, `#51`, `#85`
- **Module**: `examples/quarkus-svelte-skeleton/backend`
- **Status**: `IMPLEMENTED (Amended by ADR-007)`

## 1. Problem Statement & Technical Scope
Streams the canonical **AG-UI Protocol** (`com.ag-ui.community:java-core:0.1.1`) over `POST /api/agent/run` (`text/event-stream`) and `/api/agent/ws` (`WebSockets Next`):
- Accepts canonical `RunAgentInput` (`threadId`, `runId`, `state`, `messages`, `tools`, `context`, `forwardedProps`, `resume`).
- Emits `RUN_STARTED`, `REASONING_*`, `STEP_STARTED`/`STEP_FINISHED`, `ACTIVITY_SNAPSHOT`, `TOOL_CALL_*`, `STATE_SNAPSHOT`, RFC 6902 `STATE_DELTA` (`List<JsonPatchOperation>`), `TEXT_MESSAGE_*`, and `RUN_FINISHED` / `RUN_ERROR`.
- Attaches Tri-Protocol provenance (`AG-UI`, `MCP`, `A2A`) in `Event.rawEvent()`.
- Emits native AG-UI `InterruptOutcome(List<Interrupt>)` for high-budget (`>= $1,500 USD`) sanctuary reservations and resumes via `RunAgentInput.resume()`.

## 2. Acceptance Criteria (DoD)
- **AC-01**: Wire frames pass `@ag-ui/client` (`HttpAgent` & `WebSocketAgent`) Zod schema validation end-to-end.
- **AC-02**: Legacy `/api/agent/approve` endpoint is retired (`404`); HITL resumes natively via `RunAgentInput.resume()`.
- **AC-03**: Dual-Gate verification passes (`>= 85%` coverage, `>= 80%` PITest mutation score).
