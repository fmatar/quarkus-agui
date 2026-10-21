# SPEC-04: Full 3-Pane SvelteKit + Skeleton UI + `@lucide/svelte` AG-UI Workbench

- **Linked Capability**: `CAP-04`
- **Depends On**: `CAP-02`
- **Ticket**: `#5`
- **Module**: `examples/quarkus-svelte-skeleton/frontend`
- **Status**: `ACTIVE`

## 1. Problem Statement & Technical Scope
Evolves the SvelteKit + Skeleton UI + `@lucide/svelte` client into the complete 3-Pane AG-UI Workbench:
1. **`AgentChatContainer.svelte`**, **`MessageHistory.svelte`**, **`StreamRenderer.svelte`**, **`InputBar.svelte`** with 1-Click Scenario Chips, MCP Tool Badges, A2A Agent Handoff Cards, and Inline HITL Approve/Reject buttons.
2. **`ClusterStatePanel.svelte`** rendering live `STATE_SNAPSHOT` and `STATE_DELTA` updates with bidirectional UI-to-Agent state editing.
3. **`ProtocolInspector.svelte`** displaying live AG-UI SSE frames, MCP calls, and A2A task envelopes with `< 10ms` overhead metrics.

## 2. Acceptance Criteria (Gherkin / DoD)
- **AC-01**: `ReadableStream` SSE consumer splits frames on `\n\n` and routes all AG-UI event types accurately across network chunk boundaries.
- **AC-02**: Uses `@lucide/svelte` icons and Svelte 5 Runes (`$state`, `$derived`, `$props`).
- **AC-03**: `pnpm test` and `pnpm run build` pass with $\ge 85\%$ coverage.
