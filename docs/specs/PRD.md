# Product Requirements Document (PRD): Java/Quarkus Implementation of the AG-UI Protocol (`quarkus-agui`)

- **Author:** Agentic SDLC Product Autopilot (`sdlc-product`)
- **Status:** `APPROVED (Amended by ADR-002)`
- **License:** Apache License 2.0
- **Constitution Link:** [`PRODUCT.md`](../../PRODUCT.md)
- **ADR Links:** [`ADR-001`](../adr/ADR-001-multi-module-maven-sveltekit-clean-architecture-fo.md), [`ADR-002`](../adr/ADR-002-example-first-tri-protocol-ag-ui-mcp-a2a-evoluti.md)

---

## 1. Executive Summary & Problem Statement

The **AG-UI (Agent–User Interaction Protocol)** standardizes real-time, bidirectional streaming interaction between AI agents and front-end user interfaces, working alongside **MCP (Model Context Protocol)** for tool/resource discovery and **A2A (Agent-to-Agent Protocol)** for specialist sub-agent delegation.

**`quarkus-agui`** delivers a complete **Tri-Protocol (`AG-UI + MCP + A2A`)** solution for **Java 25** and **Quarkus `3.40.1.1` (JVM & GraalVM Native)** via a **4-Wave Example-First Evolution**:
1. **Wave 1 (`CAP-01`, `CAP-02`) — Tri-Protocol Agent Baseline (`examples/quarkus-svelte-skeleton`)**: Build a working Quarkus MCP Server (`:8081`), Quarkus A2A Specialist Server (`:8082`), Quarkus LangChain4j Orchestrator (`:8080`), and a `pnpm` + SvelteKit (Svelte 5 Runes) + `@skeletonlabs/skeleton-svelte` + `@lucide/svelte` 3-pane workbench (`:5173`).
2. **Wave 2 (`CAP-03`, `CAP-04`) — Full AG-UI Protocol Evolution In-Place (`examples/quarkus-svelte-skeleton`)**: Evolve the orchestrator and SvelteKit client in-place to stream all 10 AG-UI event types (`RUN_STARTED`, `STATUS`, `TEXT_DELTA`, `TOOL_CALL`, `TOOL_RESULT`, `RUN_FINISHED`, `STEP_STARTED`, `STEP_FINISHED`, `STATE_SNAPSHOT`, `STATE_DELTA`) plus Human-in-the-Loop (`interrupted`) approvals.
3. **Wave 3 (`CAP-05`) — Core Java 25 Library Extraction (`ag-ui-core`)**: Extract battle-tested AG-UI event records, `< 10ms` SSE wire formatters, and `Flow.Publisher` reactive adapters into a standalone zero-dependency Java 25 library governed by Archlens concentric rings.
4. **Wave 4 (`CAP-06`, `CAP-07`, `CAP-08`) — Quarkus Extension Packaging (`quarkus-ag-ui`) & Showcase Migration**: Extract `AgUiBridge`, `AgUiMcpBridge`, `AgUiA2aBridge`, GraalVM 25 `ReflectiveClassBuildItem`s (`< 50MB` native RSS), and the Quarkus Dev UI Inspector Card into `quarkus-extension/`, then migrate the showcase backend to consume `org.agui:quarkus-ag-ui`.

---

## 2. Target Personas & Goals

| Persona | Primary Need | Success Metric |
| :--- | :--- | :--- |
| **Java 25 / JVM Library Author** | Framework-agnostic AG-UI records & SSE formatters (`ag-ui-core`) | Zero external framework dependencies; $< 10\text{ms}$ serialization overhead |
| **Quarkus 3.40.1.1 AI Engineer** | Drop-in `quarkus-ag-ui` extension bridging LangChain4j, MCP, and A2A to SSE | Zero boilerplate configuration; $< 50\text{MB}$ GraalVM 25 native RSS |
| **Full-Stack Frontend Engineer** | Reactive `pnpm` + SvelteKit + `@skeletonlabs/skeleton-svelte` + `@lucide/svelte` workbench | Real-time token streaming, MCP tool badges, A2A handoff cards, and HITL approvals |

---

## 3. User Stories & Acceptance Criteria

### US-001 (`CAP-01`, #2): Quarkus MCP Server (`:8081`) & Quarkus A2A Server (`:8082`)
- [x] `mcp-server` (`:8081`) exposes MCP tools (`inspectClusterHealth`, `queryServiceLogs`, `scaleDeployment`) and `cluster://topology` resource on Java 25 & Quarkus `3.40.1.1`.
- [x] `a2a-server` (`:8082`) exposes `GET /.well-known/agent.json` (`AgentCard`) and `POST /api/a2a/tasks/send` (`audit-clean-architecture`, `assess-security-posture`, `plan-incident-remediation`).

### US-002 (`CAP-02`, #3): OpsPilot Quarkus Orchestrator (`:8080`) & SvelteKit + `pnpm` Baseline (`:5173`)
- [ ] Dual-Mode LangChain4j Orchestrator (`:8080`) invoking `:8081` MCP and `:8082` A2A.
- [ ] `pnpm` SvelteKit + `@skeletonlabs/skeleton-svelte` + `@lucide/svelte` 3-pane baseline UI (`:5173`).

### US-003 (`CAP-03`, #4): In-Place AG-UI Protocol Engine in Orchestrator (`:8080`)
- [ ] Streams Core 6 + `STEP_STARTED`/`STEP_FINISHED` + `STATE_SNAPSHOT`/`STATE_DELTA` + HITL `interrupted` approvals (`POST /api/agent/approve`).

### US-004 (`CAP-04`, #5): Full 3-Pane SvelteKit + Skeleton UI + `@lucide/svelte` AG-UI Workbench (`:5173`)
- [ ] `AgentChatContainer.svelte`, `MessageHistory.svelte`, `StreamRenderer.svelte`, `InputBar.svelte`, `ClusterStatePanel.svelte`, and `ProtocolInspector.svelte` (`\n\n` SSE frame parser + `AbortController`).

### US-005 (`CAP-05`, #6): Extract `ag-ui-core` Standalone Java 25 Library
- [ ] Zero-dependency Java 25 library (`org.agui.core.domain`, `application`, `adapters`) governed by Archlens concentric rings.

### US-006 (`CAP-06`, #7): Extract `quarkus-ag-ui-runtime`
- [ ] `AgUiBridge`, `AgUiMcpBridge`, `AgUiA2aBridge`, and SmallRye Mutiny `Multi<AgUiEvent<?>>` SSE support.

### US-007 (`CAP-07`, #8): Extract `quarkus-ag-ui-deployment` & Integration Tests
- [ ] `AgUiProcessor`, `ReflectiveClassBuildItem`s for GraalVM 25, Quarkus Dev UI Inspector Card, and `@QuarkusTest`/`@QuarkusIntegrationTest` suites.

### US-008 (`CAP-08`, #9): Migrate Showcase to `quarkus-ag-ui` Extension & Native Benchmarks
- [ ] Showcase `backend` consumes `org.agui:quarkus-ag-ui`; verified `< 10ms` serialization overhead and portable `-Dnative` / `-Dnative-container` GraalVM 25 builds (`< 50MB` RSS).
