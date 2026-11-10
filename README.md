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

#### Option A: One-Command Jib + Quinoa Docker Compose Stack (Recommended)
```bash
# Build all 3 Jib layered containers (with SvelteKit 5 SPA bundled via Quarkus Quinoa) & launch on :8080
./examples/quarkus-svelte-skeleton/run-showcase.sh --docker

# Or build GraalVM/Mandrel native Jib containers via the opt-in -Pnative profile:
./examples/quarkus-svelte-skeleton/run-showcase.sh --docker-native
```

#### Option B: Local Hot-Reload Dev Mode (Quinoa Auto-Managed Vite Proxy)
```bash
# Launch all 3 Quarkus services in quarkus:dev (Quinoa auto-manages & proxies Vite on :8080):
./examples/quarkus-svelte-skeleton/run-showcase.sh --local
```

Verify the MCP Server (`:8081`) and A2A Specialist Server (`:8082`) directly from your terminal:
```bash
# Check MicroProfile SmallRye Readiness (:8080, :8081, :8082)
curl -s http://localhost:8080/q/health/ready | jq .
curl -s http://localhost:8081/q/health/ready | jq .
curl -s http://localhost:8082/q/health/ready | jq .

# Discover MCP Tools & City Dossier Resource (:8081/mcp + Companion REST Bridge)
curl -s http://localhost:8081/api/mcp/tools | jq .
curl -s http://localhost:8081/api/mcp/resources/city/Kyoto | jq .

# Discover Official A2A AgentCard & Delegate Travel Specialist Audit (:8082 — Frankfurter FX & REST Countries v5)
curl -s http://localhost:8082/.well-known/agent-card.json | jq .
curl -s -X POST http://localhost:8082/api/a2a/audit \
  -H "Content-Type: application/json" \
  -d '{"skillId":"verify-travel-advisory","prompt":"Verify Paris advisory","context":{"city":"Paris","country":"France"}}' | jq .

# Stream AG-UI SSE events from the Nomad Concierge Orchestrator (:8080)
curl -N -X POST http://localhost:8080/api/agent/run \
  -H "Content-Type: application/json" \
  -d '{"threadId":"thread-demo-1","prompt":"Plan a 4-night architecture and tea trip in Kyoto with a $2,400 USD budget"}'
```

### 4. Portable GraalVM 25 Native Compilation ("Runs Everywhere")
```bash
# Uses local GraalVM 25 if installed, or auto-falls back to Mandrel JDK 25 container build
./mvnw verify -Dnative

# Explicitly force Mandrel JDK 25 Linux container build on any OS
./mvnw verify -Dnative-container
```

### 5. Bring Your Own Agent (BYOA) Contract
To expose any LangChain4j `TokenStream`, Quarkus LangChain4j `@RegisterAiService` (`Multi<ChatEvent>`), MCP tool client, or A2A sub-agent over the AG-UI protocol in **any Java framework** (Spring Boot, Quarkus, Helidon, Micronaut, Jakarta EE, or plain Java):
1. Import `org.agui:ag-ui-bom` in `<dependencyManagement>` and add `org.agui:ag-ui-core`, `org.agui:ag-ui-a2a`, and `org.agui:ag-ui-langchain4j` (plus `org.agui:ag-ui-quarkus` when using Quarkus Mutiny / WebSockets Next / Quarkiverse `Multi<ChatEvent>`).
2. Instantiate or `@Inject` `AgUiLangChain4jBridge` (or `AgUiQuarkusLangChain4jBridge` in Quarkus), `AgUiBridge` (`AgUiQuarkusBridge`), `AgUiMcpBridge`, `AgUiA2aBridge`, and `AgUiSessionRegistry`.
3. Stream `Flow.Publisher<String>` (or Mutiny `Multi<String>` in Quarkus) emitting standard AG-UI SSE frames (`data: {...}\n\n`):
   - `RUN_STARTED` $\rightarrow$ `STATUS` $\rightarrow$ `STATE_SNAPSHOT` $\rightarrow$ `TOOL_CALL` / `TOOL_RESULT` $\rightarrow$ `STEP_STARTED` / `STEP_FINISHED` $\rightarrow$ `STATE_DELTA` $\rightarrow$ `TEXT_DELTA` $\rightarrow$ `RUN_FINISHED`.

---

## 📊 Module & Archetype Matrix

| Module | Path | Stack / Runtime | Target Latency / Footprint |
| :--- | :--- | :--- | :--- |
| **`ag-ui-bom`** | [`ag-ui-bom/`](ag-ui-bom/pom.xml) | Maven Bill of Materials (BOM) | Unified dependency management |
| **`ag-ui-core`** | [`ag-ui-core/`](ag-ui-core/pom.xml) | Framework-agnostic Java 25 (`AgUiEvent`, `AgUiSseFormatter`, `AgUiFlowAdapter`, `AgUiSessionRegistry`, `AgUiSseTransportSession`, `AgUiWebSocketTransportSession`, `AgUiMcpBridge`, `AgUiBridge`, GraalVM `reflect-config.json`) | $< 10\text{ms}$ serialization overhead |
| **`ag-ui-a2a`** | [`ag-ui-a2a/`](ag-ui-a2a/pom.xml) | Framework-agnostic A2A Protocol Bridge (`AgUiA2aBridge` over `org.a2aproject.sdk`) | Zero framework lock-in |
| **`ag-ui-langchain4j`** | [`ag-ui-langchain4j/`](ag-ui-langchain4j/pom.xml) | Framework-agnostic upstream `dev.langchain4j` `TokenStream` & lifecycle adapter (`AgUiLangChain4jBridge`, `AgUiRunOptions`, `AgUiToolClassifier`) | Works in Spring, Quarkus, Helidon, Micronaut & plain Java |
| **`ag-ui-quarkus`** | [`ag-ui-quarkus/`](ag-ui-quarkus/pom.xml) | Plain Quarkus CDI, Mutiny `Multi`, WebSockets Next & Quarkiverse `Multi<ChatEvent>` adapter (`AgUiQuarkusBridge`, `AgUiQuarkusLangChain4jBridge`, `AgUiWebSocketEndpoint`) | Zero `-deployment` build-step overhead |
| **`mcp-server` (`:8081`)** | [`examples/quarkus-svelte-skeleton/mcp-server/`](examples/quarkus-svelte-skeleton/mcp-server/pom.xml) | Quarkus 3.40.1.1 Nomad MCP Server + SmallRye Health (`/q/health/ready`) + Jib Container (`nomad-agui-showcase/mcp-server:latest`) | `fetchCityWeather`, `discoverLocalCultureAndMedia`, `updateTripItinerary`, `saveTravelerPreferences`, `sampleAcousticMood`, `resetNomadState` |
| **`a2a-server` (`:8082`)** | [`examples/quarkus-svelte-skeleton/a2a-server/`](examples/quarkus-svelte-skeleton/a2a-server/pom.xml) | Quarkus 3.40.1.1 A2A Specialist Server + SmallRye Health (`/q/health/ready`) + Jib Container (`nomad-agui-showcase/a2a-server:latest`) | `convert-and-audit-budget` (Frankfurter FX), `verify-travel-advisory` (REST Countries v5 `api.restcountries.com`) |
| **`backend` (`:8080`)** | [`examples/quarkus-svelte-skeleton/backend/`](examples/quarkus-svelte-skeleton/backend/pom.xml) | Quarkus 3.40.1.1 `@RegisterAiService` + `@McpToolBox("nomad")` + SmallRye Health + **Quarkus Quinoa** + Jib Container (`nomad-agui-showcase/backend:latest`) | Serves SvelteKit 5 SPA, AG-UI WebSocket (`/api/agent/ws`), SSE (`/api/agent/run`), and `/q/health/ready` on `:8080` |
| **`frontend` (Quinoa SPA)** | [`examples/quarkus-svelte-skeleton/frontend/`](examples/quarkus-svelte-skeleton/frontend/) | `pnpm` + SvelteKit 5 (`@sveltejs/adapter-static`) packaged into `backend` via `quarkus-quinoa` | 3-Pane Nomad Concierge Studio + Wire Telemetry Inspector served from `:8080` |

---

## 🏛️ System Architecture & Governance

- **Product Constitution**: [`PRODUCT.md`](PRODUCT.md) (`CAP-01`–`CAP-08`, `INV-01`–`INV-08`)
- **Product Requirements (PRD)**: [`docs/specs/PRD.md`](docs/specs/PRD.md)
- **Software Requirements (SRS)**: [`docs/specs/SRS.md`](docs/specs/SRS.md)
- **C4 Architecture**: [`docs/specs/C4_ARCHITECTURE.md`](docs/specs/C4_ARCHITECTURE.md)
- **Living Kanban & Traceability Matrix**: [`docs/KANBAN.md`](docs/KANBAN.md)
- **Causal Decision Lineage**: [`docs/DECISION_MAP.md`](docs/DECISION_MAP.md)
- **Architectural Decision Records (ADRs)**: [`docs/adr/ADR-001`](docs/adr/ADR-001-multi-module-maven-sveltekit-clean-architecture-.md), [`docs/adr/ADR-002`](docs/adr/ADR-002-example-first-tri-protocol-ag-ui-mcp-a2a-evoluti.md), [`docs/adr/ADR-003`](docs/adr/ADR-003-multi-extension-bom-and-concentric-transport-arc.md), [`docs/adr/ADR-004`](docs/adr/ADR-004-nomad-concierge-and-svelte5-agui-component-kit.md), [`docs/adr/ADR-005`](docs/adr/ADR-005-quarkus-langchain4j-mcp-and-smallrye-health.md), [`docs/adr/ADR-006`](docs/adr/ADR-006-framework-agnostic-module-topology-ag-ui-core-ag.md)
