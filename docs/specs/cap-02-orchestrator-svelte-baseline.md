# SPEC-02: OpsPilot Quarkus Orchestrator (`:8080`) & SvelteKit + `pnpm` 3-Pane Baseline (`:5173`)

- **Linked Capability**: `CAP-02`
- **Depends On**: `CAP-01`
- **Ticket**: `#3`
- **Modules**: `examples/quarkus-svelte-skeleton/backend` & `examples/quarkus-svelte-skeleton/frontend`
- **Stack**: Java 25 + Quarkus `3.40.1.1` + LangChain4j; Svelte 5 + `@skeletonlabs/skeleton-svelte` + `@lucide/svelte` via `pnpm`
- **Status**: `ACTIVE`

## 1. Problem Statement & Technical Scope
Establishes the working end-to-end "OpsPilot" Orchestrator Agent in Quarkus (`:8080`) connected to `mcp-server` (`:8081`) and `a2a-server` (`:8082`), paired with the SvelteKit + Skeleton UI + `@lucide/svelte` 3-pane workbench baseline (`:5173`).

## 2. Acceptance Criteria (Gherkin / DoD)
- **AC-01**: Dual-Mode LangChain4j driver in `backend` executes deterministic tool/A2A orchestration by default and supports live OpenAI/Ollama when configured.
- **AC-02**: `frontend` is managed via `pnpm` with `@lucide/svelte` and `@skeletonlabs/skeleton-svelte` (zero deprecated packages).
- **AC-03**: Dual-Gate verification passes with $\ge 85\%$ coverage and $\ge 80\%$ mutation kill score.
