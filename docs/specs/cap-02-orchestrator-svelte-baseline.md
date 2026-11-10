# SPEC-02: Nomad Quarkus Orchestrator (`:8080`) & Quinoa SvelteKit 5 SPA

- **Linked Capability**: `CAP-02`
- **Depends On**: `CAP-01`
- **Tickets**: `#3`, `#32`, `#59`, `#68`, `#85`
- **Modules**: `examples/quarkus-svelte-skeleton/backend` & `examples/quarkus-svelte-skeleton/frontend`
- **Stack**: Java 25 + Quarkus `3.40.1.1` + `quarkus-langchain4j-mcp` + `quarkus-quinoa` + Jib; Svelte 5 + `@agui/svelte` + `@skeletonlabs/skeleton-svelte` + `@lucide/svelte` via `pnpm`
- **Status**: `IMPLEMENTED (Amended by ADR-004, ADR-005 & ADR-007)`

## 1. Problem Statement & Technical Scope
Establishes the end-to-end Nomad Concierge Orchestrator in Quarkus (`:8080`) connecting `@RegisterAiService` (`NomadConciergeAiService`) + `@McpToolBox("nomad")` (`:8081`) + A2A Specialist delegation (`:8082`), bundling the 3-Pane SvelteKit 5 Studio via Quarkus Quinoa (`2.9.3`) into a single origin (`:8080`).

## 2. Acceptance Criteria (DoD)
- **AC-01**: Executes on Java 25 Virtual Threads (`@RunOnVirtualThread`) supporting local Ollama models (`llama3.2:latest`, `qwen3.5:4b`, `ministral-3:8b`) via `forwardedProps.llmMode`.
- **AC-02**: `frontend` is managed via root `pnpm-workspace.yaml` and statically bundled into `backend` by Quarkus Quinoa.
- **AC-03**: Dual-Gate verification passes (`>= 85%` coverage, `>= 80%` mutation score).
