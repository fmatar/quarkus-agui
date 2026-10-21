# SPEC-03: In-Place AG-UI Protocol Engine in Orchestrator (Core 6 + MCP/A2A + State + HITL)

- **Linked Capability**: `CAP-03`
- **Depends On**: `CAP-02`
- **Ticket**: `#4`
- **Module**: `examples/quarkus-svelte-skeleton/backend`
- **Status**: `ACTIVE`

## 1. Problem Statement & Technical Scope
Evolves the `examples/quarkus-svelte-skeleton/backend` orchestrator in-place to speak the full **AG-UI Protocol** over `POST /api/agent/run` (`text/event-stream`) and `POST /api/agent/approve`:
- Core 6 events (`RUN_STARTED`, `STATUS`, `TEXT_DELTA`, `TOOL_CALL`, `TOOL_RESULT`, `RUN_FINISHED`).
- MCP provenance metadata on `TOOL_CALL` / `TOOL_RESULT`.
- A2A delegation step events (`STEP_STARTED`, `STEP_FINISHED`).
- Shared state synchronization (`STATE_SNAPSHOT`, `STATE_DELTA`).
- Human-in-the-Loop interruption (`RUN_FINISHED` with `status: "interrupted"`) when scaling above 5 replicas, resumed via `POST /api/agent/approve`.

## 2. Acceptance Criteria (Gherkin / DoD)
- **AC-01**: `POST /api/agent/run` streams valid `data: <json>\n\n` AG-UI SSE frames for all 4 showcase scenarios.
- **AC-02**: High-replica `scaleDeployment` triggers HITL interruption and resumes cleanly upon approval.
- **AC-03**: Dual-Gate verification passes with $\ge 85\%$ coverage and $\ge 80\%$ PITest mutation kill score.
