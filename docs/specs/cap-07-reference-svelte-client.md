# SPEC-07: Reference SvelteKit + Skeleton UI Streaming Client (`examples/quarkus-svelte-skeleton`)

- **Linked Capability**: `CAP-07`
- **Depends On**: `CAP-01`, `CAP-02`
- **Module**: `examples/quarkus-svelte-skeleton/frontend`
- **Status**: `ACTIVE`

## 1. Problem Statement & Technical Scope
Implements the reference AG-UI Frontend Client in **Svelte (SvelteKit) + Skeleton UI (Tailwind CSS)** consuming the `/api/agent/run` SSE stream via `response.body.getReader()`, parsing `\n\n`-delimited `data: ` frames, and updating reactive UI state.

## 2. Component Hierarchy
1. **`AgentChatContainer.svelte`**: Top-level container coordinating thread state, SSE stream lifecycle, and child components.
2. **`MessageHistory.svelte`**:
   - User Message Card (`variant-filled-primary`)
   - Assistant Message Card (`variant-soft`)
   - AG-UI Tool Badge Component (Skeleton chip/badge displaying tool name, arguments, and execution result)
3. **`StreamRenderer.svelte`**: Live token buffer (`TEXT_DELTA` accumulator) + status indicator spinner (`thinking` / `executing_tool`).
4. **`InputBar.svelte`**: Prompt submission form with active run status lock (`disabled` while streaming) and keyboard accessibility (`Enter` to submit).

## 3. Acceptance Criteria (Gherkin / DoD)
- **AC-01**: SSE stream parser splits chunk boundaries on `\n\n`, strips `data: ` prefixes, and routes `RUN_STARTED`, `STATUS`, `TEXT_DELTA`, `TOOL_CALL`, `TOOL_RESULT`, and `RUN_FINISHED` events accurately even when chunks split across network reads.
- **AC-02**: WCAG 2.1 AA accessibility: `aria-live="polite"` on stream output, keyboard navigation on `InputBar.svelte`, and high-contrast Skeleton UI badges.
- **AC-03**: Vitest + Stryker Dual-Gate verification passes with $\ge 85\%$ coverage and $\ge 80\%$ mutation kill score.
