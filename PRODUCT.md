# 🏛️ Product Constitution: Java/Quarkus Implementation of the AG-UI Protocol (quarkus-agui)

> **Single Source of Truth (`PRODUCT.md`)**
> Generated & governed by **Agentic SDLC Framework (`sdlc-product`)**.
> Any architectural or contract deviation from this constitution requires a linked **ADR (`docs/adr/ADR-00X.md`)**.

---

## 1. Product Vision
Fill the gap in Java/JVM ecosystems for the **AG-UI (Agent–User Interaction Protocol)** standard by building a complete **Tri-Protocol (`AG-UI + MCP + A2A`) Reference Application** on **Quarkus `3.40.1.1` (Java 25 + GraalVM Native)** and **SvelteKit (Svelte 5 Runes + `@skeletonlabs/skeleton-svelte` + `@lucide/svelte` via `pnpm`)** first, evolving full AG-UI capabilities in-place, and then extracting a zero-dependency **Core Java 25 Library (`ag-ui-core`)** and a zero-configuration **Quarkus Extension (`quarkus-ag-ui`)** with MCP, A2A, and LangChain4j bridges.

### Strategic 4-Wave Evolutionary Progression
1. **Wave 1 — Tri-Protocol Agent Baseline (`examples/quarkus-svelte-skeleton`):** Build local Quarkus MCP Server (`:8081`), Quarkus A2A Specialist Server (`:8082`), Quarkus LangChain4j Orchestrator (`:8080`), and SvelteKit + `pnpm` 3-Pane UI baseline (`:5173`).
2. **Wave 2 — Full AG-UI Protocol Evolution In-Place (`examples/quarkus-svelte-skeleton`):** Evolve the orchestrator and SvelteKit client to stream the complete AG-UI protocol (`RUN_STARTED`, `STATUS`, `TEXT_DELTA`, `TOOL_CALL`, `TOOL_RESULT`, `RUN_FINISHED`, `STEP_STARTED`, `STEP_FINISHED`, `STATE_SNAPSHOT`, `STATE_DELTA`, and Human-in-the-Loop `interrupted` approvals).
3. **Wave 3 — Core Java 25 Library Extraction (`ag-ui-core`):** Extract immutable AG-UI event records, `< 10ms` SSE wire formatters, and `Flow.Publisher` reactive adapters into a standalone zero-dependency Java 25 library governed by Archlens concentric rings (`domain` $\leftarrow$ `application` $\leftarrow$ `adapters`).
4. **Wave 4 — Quarkus Extension Packaging (`quarkus-ag-ui`) & Showcase Migration:** Extract `AgUiBridge`, `AgUiMcpBridge`, `AgUiA2aBridge`, Mutiny SSE writers, GraalVM 25 `ReflectiveClassBuildItem`s (`< 50MB` native RSS), and the Quarkus Dev UI Inspector Card into `quarkus-extension/`, then migrate the showcase backend to a zero-boilerplate consumer of `quarkus-ag-ui`.

---

## 2. Hard Domain & System Invariants (`INV-XX`)
- **INV-01**: Zero direct commits, merges, or pushes to main or develop; 100% of changes flow via tracked ticket branches and Stage 7 SHA-256 Merkle Gate Receipts.
- **INV-02**: Dual-Gate TDD Craftsmanship: >= 85% line and branch diff coverage AND >= 80% mutation kill score (PITest / Stryker) adhering to Robert C. Martin Three Laws of TDD.
- **INV-03**: Clean Architecture Concentric Layer Governance (Archlens): Dependencies strictly point inward (Adapters -> Application -> Domain Core) with zero domain leaks.
- **INV-04**: Zero-Dependency Core Library: ag-ui-core must remain plain Java 25 records, stream adapters, and formatters without Quarkus or web framework dependencies.
- **INV-05**: Sub-10ms Serialization Overhead: Java core AG-UI event processing and SSE framing must add < 10ms p99 latency overhead.
- **INV-06**: Portable GraalVM 25 Native Footprint: Native executable compiled with quarkus-ag-ui (-Dnative local GraalVM 25 or Mandrel JDK 25 container fallback) must maintain < 50MB RSS baseline memory footprint.
- **INV-07**: Zero-Configuration Extension Defaults: Importing quarkus-ag-ui auto-configures Jackson, RESTEasy Reactive text/event-stream SSE bindings, and LangChain4j/MCP/A2A bridges under Apache License 2.0.
- **INV-08**: Modern Frontend & Toolchain Standards: Java 25 + Quarkus 3.40.1.1 via ./mvnw on backend; pnpm + Svelte 5 Runes + @skeletonlabs/skeleton-svelte + @lucide/svelte (zero deprecated modules) on frontend.

---

## 3. Product Capabilities & Spec Traceability Matrix (`CAP-XX`)

| Capability ID | Capability Title | Functional Spec | Depends On | Status |
| :--- | :--- | :--- | :--- | :--- |
| CAP-01 | Quarkus MCP Server (:8081) & Quarkus A2A Server (:8082) in Reference Showcase | `docs/specs/cap-01-mcp-a2a-servers.md` | None | `IMPLEMENTED (ADR-002)` |
| CAP-02 | OpsPilot Quarkus Orchestrator (:8080) & SvelteKit + pnpm 3-Pane Baseline (:5173) | `docs/specs/cap-02-orchestrator-svelte-baseline.md` | CAP-01 | `IMPLEMENTED (ADR-002)` |
| CAP-03 | In-Place AG-UI Protocol Engine in Orchestrator (Core 6 + MCP/A2A + State + HITL) | `docs/specs/cap-03-inplace-agui-backend.md` | CAP-02 | `IMPLEMENTED (ADR-002)` |
| CAP-04 | Full 3-Pane SvelteKit + Skeleton UI + @lucide/svelte AG-UI Workbench | `docs/specs/cap-04-svelte-agui-workbench.md` | CAP-02 | `IMPLEMENTED (ADR-002)` |
| CAP-05 | Extract ag-ui-core Standalone Java 25 Library & Refactor Showcase | `docs/specs/cap-05-extract-agui-core.md` | CAP-03, CAP-04 | `IMPLEMENTED (ADR-002)` |
| CAP-06 | Extract quarkus-ag-ui-runtime (AgUiBridge, AgUiMcpBridge, AgUiA2aBridge, Mutiny SSE) | `docs/specs/cap-06-extract-quarkus-runtime.md` | CAP-05 | `AMENDED (ADR-002)` |
| CAP-07 | Extract quarkus-ag-ui-deployment, GraalVM 25 Reflection, Dev UI & Integration Tests | `docs/specs/cap-07-extract-quarkus-deployment-it.md` | CAP-06 | `AMENDED (ADR-002)` |
| CAP-08 | Migrate Showcase to quarkus-ag-ui Extension & Full E2E + Native Benchmarks | `docs/specs/cap-08-migrate-showcase-e2e-benchmarks.md` | CAP-07 | `AMENDED (ADR-002)` |

---

## 4. Genesis & Milestone Execution Rules
- **Genesis Ticket `#1`**: `feature/issue-#1-product-genesis` initializes `PRODUCT.md`, baseline specs, and project scaffolding.
- **Zero-Bypass Merge Rule**: No PR merges to `main` without a signed Stage 7 SHA-256 Merkle Gate Receipt.

---

## 5. Architectural Decision Log Index (`ADR-XXX`)
<!-- ADR_INDEX_START -->
| ADR ID | Title | Scope | Status | Ticket | Supersedes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| ADR-001 | Multi-Module Maven & SvelteKit Clean Architecture for AG-UI Protocol | `technical` | `accepted` | #1 | CAP-01, CAP-03, CAP-04, CAP-07 |
| ADR-002 | Example-First Tri-Protocol (AG-UI + MCP + A2A) Evolution on Java 25, Quarkus 3.40.1.1 & pnpm | `product` | `accepted` | #2 | CAP-01, CAP-02, CAP-03, CAP-04, CAP-05, CAP-06, CAP-07, CAP-08 |
<!-- ADR_INDEX_END -->
