# SPEC-04: Headless `@agui/svelte` SDK & 3-Pane Nomad Studio Workbench

- **Linked Capability**: `CAP-04`
- **Depends On**: `CAP-02`
- **Tickets**: `#5`, `#29`, `#32`, `#57`, `#83`, `#85`
- **Modules**: `packages/agui-svelte` & `examples/quarkus-svelte-skeleton/frontend`
- **Status**: `IMPLEMENTED (Amended by ADR-004 & ADR-007)`

## 1. Problem Statement & Technical Scope
Splits client responsibilities cleanly across two workspace packages:
1. **`packages/agui-svelte` (`@agui/svelte`)**: Headless, CSS-framework-free Svelte 5 Runes & JS SDK wrapping `@ag-ui/client` + `@ag-ui/core` (`^1.0.4`) with `WebSocketAgent extends AbstractAgent`, immutable RFC 6902 `applyJsonPatch` (with CWE-1321 prototype-pollution protection), `rawEvent` Tri-Protocol taxonomy helpers, and `createAgUiStore` / `useAgUiAgent`.
2. **`examples/quarkus-svelte-skeleton/frontend`**: 3-Pane Nomad Concierge Studio (`AgentChatContainer.svelte`, `TripDossierPanel.svelte`, `ProtocolInspector.svelte`) built with Svelte 5 Runes, Skeleton v4, Tailwind v4, OWASP XSS URL/attribute sanitization, and WCAG 2.1 AA contrast (`>= 4.5:1`).

## 2. Acceptance Criteria (DoD)
- **AC-01**: `@agui/svelte` has zero UI/CSS framework dependencies and passes `tsc --noEmit` with `skipLibCheck: false`.
- **AC-02**: Dual-Gate verification passes (`99.53%–100%` line coverage, `86.58%–92.94%` AST mutation kill score).
