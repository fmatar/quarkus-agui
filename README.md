# ⚡ Quarkus AG-UI (`quarkus-agui`)

> **Java 25 Core Library (`ag-ui-core`) & Quarkus `3.40.1.1` Extension (`quarkus-ag-ui`) for the AG-UI (Agent–User Interaction) Protocol**  
> Complete **Tri-Protocol (`AG-UI + MCP + A2A`)** Reference Stack powered by **Quarkus RESTEasy Reactive**, **LangChain4j**, **SmallRye Mutiny**, **GraalVM 25 Native**, and a **SvelteKit (Svelte 5 Runes) + Skeleton UI (`@skeletonlabs/skeleton-svelte`) + `@lucide/svelte`** Workbench via **`pnpm`**.

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Java 25](https://img.shields.io/badge/Java-25-orange.svg)](pom.xml)
[![Quarkus 3.40.1.1](https://img.shields.io/badge/Quarkus-3.40.1.1-4695EB.svg)](pom.xml)
[![Architecture: Archlens Clean Architecture](https://img.shields.io/badge/Architecture-Clean_Concentric_Rings-success.svg)](.archlens/policy.json)

---

## 🚀 5-Minute Developer Quickstart

### 1. CLI & Toolchain Prerequisites
Ensure **Java 25** (OpenJDK 25 or GraalVM 25 CE) and **`pnpm`** are installed:
```bash
java -version
./mvnw -version
pnpm -v
```

### 2. Local Build & Environment Bootstrap
```bash
# Clone and configure environment
cp .env.example .env

# Run unit tests, JaCoCo coverage, and PITest mutation coverage across all modules
./mvnw test jacoco:report
./mvnw test-compile org.pitest:pitest-maven:mutationCoverage

# Verify Agentic SDLC health & Clean Architecture policy
node .agent/scripts/sdlc-engine.mjs env
node .agent/scripts/sdlc-engine.mjs product status
```

### 3. Explore the Tri-Protocol Reference Showcase (`examples/quarkus-svelte-skeleton`)
```bash
# Terminal 1: Start the Quarkus MCP Server (Port 8081)
./mvnw -f examples/quarkus-svelte-skeleton/mcp-server/pom.xml quarkus:dev

# Terminal 2: Start the Quarkus A2A Specialist Auditor Server (Port 8082)
./mvnw -f examples/quarkus-svelte-skeleton/a2a-server/pom.xml quarkus:dev

# Terminal 3: Start the OpsPilot Quarkus Orchestrator Backend (Port 8080)
./mvnw -f examples/quarkus-svelte-skeleton/backend/pom.xml quarkus:dev

# Terminal 4: Start the SvelteKit + Skeleton UI + @lucide/svelte Workbench (Port 5173)
cd examples/quarkus-svelte-skeleton/frontend && pnpm install && pnpm run dev
```

Verify the MCP Server (`:8081`) and A2A Specialist Server (`:8082`) directly from your terminal:
```bash
# Discover MCP Tools & Resources (:8081)
curl -s http://localhost:8081/api/mcp/tools | jq .
curl -s http://localhost:8081/api/mcp/resources/topology | jq .

# Discover A2A AgentCard & Delegate Specialist Task (:8082)
curl -s http://localhost:8082/.well-known/agent.json | jq .
curl -s -X POST http://localhost:8082/api/a2a/tasks/send \
  -H "Content-Type: application/json" \
  -d '{"taskId":"task-demo-1","skillId":"audit-clean-architecture","prompt":"Audit cluster topology"}' | jq .

# Stream AG-UI SSE events from the OpsPilot Orchestrator (:8080)
curl -N -X POST http://localhost:8080/api/agent/run \
  -H "Content-Type: application/json" \
  -d '{"threadId":"thread-demo-1","prompt":"Inspect system architecture"}'
```

### 4. Portable GraalVM 25 Native Compilation ("Runs Everywhere")
```bash
# Uses local GraalVM 25 if installed, or auto-falls back to Mandrel JDK 25 container build
./mvnw verify -Dnative

# Explicitly force Mandrel JDK 25 Linux container build on any OS
./mvnw verify -Dnative-container
```

### 5. Bring Your Own Agent (BYOA) Contract
To expose any LangChain4j `TokenStream`, MCP tool client, or A2A sub-agent over the AG-UI protocol in your own Quarkus project:
1. Add `org.agui:quarkus-ag-ui` to your `pom.xml`.
2. Inject `AgUiBridge`, `AgUiMcpBridge`, and `AgUiA2aBridge` (or use `AgUiSseFormatter` from `ag-ui-core` in non-Quarkus apps).
3. Return `Multi<String>` (SSE frames) or `Multi<ServerSentEvent<AgUiEvent<?>>>` with `@Produces(MediaType.SERVER_SENT_EVENTS)` emitting standard AG-UI events:
   - `RUN_STARTED` $\rightarrow$ `STATUS` $\rightarrow$ `STATE_SNAPSHOT` $\rightarrow$ `TOOL_CALL` / `TOOL_RESULT` $\rightarrow$ `STEP_STARTED` / `STEP_FINISHED` $\rightarrow$ `STATE_DELTA` $\rightarrow$ `TEXT_DELTA` $\rightarrow$ `RUN_FINISHED`.

---

## 📊 Module & Archetype Matrix

| Module | Path | Stack / Runtime | Target Latency / Footprint |
| :--- | :--- | :--- | :--- |
| **`ag-ui-core`** | [`ag-ui-core/`](ag-ui-core/pom.xml) | Plain Java 25 (Zero framework deps) | $< 10\text{ms}$ serialization overhead |
| **`quarkus-ag-ui-runtime`** | [`quarkus-extension/runtime/`](quarkus-extension/runtime/pom.xml) | Quarkus 3.40.1.1 + Mutiny + LangChain4j + MCP/A2A Bridges | Reactive non-blocking SSE |
| **`quarkus-ag-ui-deployment`** | [`quarkus-extension/deployment/`](quarkus-extension/deployment/pom.xml) | Quarkus Build-Time Processor + Dev UI + GraalVM 25 | $< 50\text{MB}$ Native Image RSS |
| **`quarkus-ag-ui-integration-tests`** | [`quarkus-extension/integration-tests/`](quarkus-extension/integration-tests/pom.xml) | `@QuarkusTest` & `@QuarkusIntegrationTest` | Ephemeral port `0` isolation |
| **`mcp-server` (`:8081`)** | [`examples/quarkus-svelte-skeleton/mcp-server/`](examples/quarkus-svelte-skeleton/mcp-server/pom.xml) | Quarkus 3.40.1.1 MCP Tool & Resource Server | `inspectClusterHealth`, `queryServiceLogs`, `scaleDeployment` |
| **`a2a-server` (`:8082`)** | [`examples/quarkus-svelte-skeleton/a2a-server/`](examples/quarkus-svelte-skeleton/a2a-server/pom.xml) | Quarkus 3.40.1.1 A2A Specialist Auditor (`Sentinel-A2A-Auditor`) | `/.well-known/agent.json` & `/api/a2a/tasks/send` |
| **`backend` (`:8080`)** | [`examples/quarkus-svelte-skeleton/backend/`](examples/quarkus-svelte-skeleton/backend/pom.xml) | Quarkus 3.40.1.1 + Dual-Mode LangChain4j OpsPilot Orchestrator | Real-time `\n\n` AG-UI SSE frame streaming |
| **`frontend` (`:5173`)** | [`examples/quarkus-svelte-skeleton/frontend/`](examples/quarkus-svelte-skeleton/frontend/) | `pnpm` + SvelteKit (Svelte 5 Runes) + `@skeletonlabs/skeleton-svelte` + `@lucide/svelte` | 3-Pane AG-UI Workbench + Inspector |

---

## 🏛️ System Architecture & Governance

- **Product Constitution**: [`PRODUCT.md`](PRODUCT.md) (`CAP-01`–`CAP-08`, `INV-01`–`INV-08`)
- **Product Requirements (PRD)**: [`docs/specs/PRD.md`](docs/specs/PRD.md)
- **Software Requirements (SRS)**: [`docs/specs/SRS.md`](docs/specs/SRS.md)
- **C4 Architecture**: [`docs/specs/C4_ARCHITECTURE.md`](docs/specs/C4_ARCHITECTURE.md)
- **Living Kanban & Traceability Matrix**: [`docs/KANBAN.md`](docs/KANBAN.md)
- **Causal Decision Lineage**: [`docs/DECISION_MAP.md`](docs/DECISION_MAP.md)
- **Architectural Decision Records (ADRs)**: [`docs/adr/ADR-001`](docs/adr/ADR-001-multi-module-maven-sveltekit-clean-architecture-fo.md), [`docs/adr/ADR-002`](docs/adr/ADR-002-example-first-tri-protocol-ag-ui-mcp-a2a-evoluti.md)
