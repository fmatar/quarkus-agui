# ⚡ Quarkus AG-UI (`quarkus-agui`)

> **Orthogonal Java 17 Quarkus & LangChain4j Adapters (`ag-ui-quarkus`, `ag-ui-langchain4j`, `ag-ui-quarkus-langchain4j`, `ag-ui-a2a`) built on the Official Upstream AG-UI SDK (`com.ag-ui.community`)**  
> Complete **Tri-Protocol (`AG-UI + MCP + A2A`)** Reference Stack powered by **Quarkus `3.40.1.1` RESTEasy Reactive**, **LangChain4j**, **SmallRye Mutiny**, **Java 25 + GraalVM 25 Native** showcase, and a **SvelteKit (Svelte 5 Runes) + `@agui/svelte` + Skeleton UI (`@skeletonlabs/skeleton-svelte`)** Workbench via **`pnpm`**.

[![License: Apache-2.0 OR MIT](https://img.shields.io/badge/License-Apache--2.0_OR_MIT-blue.svg)](LICENSE)
[![Java 17+ / 25](https://img.shields.io/badge/Java-17%2B_(Libs)_%7C_25_(Showcase)-orange.svg)](pom.xml)
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

# Stream canonical AG-UI SSE events from the Nomad Concierge Orchestrator (:8080)
curl -N -X POST http://localhost:8080/api/agent/run \
  -H "Content-Type: application/json" \
  -d '{"threadId":"thread-demo-1","runId":"run-demo-1","state":{},"messages":[{"id":"u-1","role":"user","content":"Plan a 4-night architecture and tea trip in Kyoto with a $2,400 USD budget"}],"tools":[],"context":[],"forwardedProps":{"llmMode":"ollama:llama3.2:latest"}}'
```

### 4. Portable GraalVM 25 Native Compilation ("Runs Everywhere")
```bash
# Uses local GraalVM 25 if installed, or auto-falls back to Mandrel JDK 25 container build
./mvnw verify -Dnative

# Explicitly force Mandrel JDK 25 Linux container build on any OS
./mvnw verify -Dnative-container
```

### 5. Bring Your Own Agent (BYOA) Contract
To expose any `com.agui.community.core.agent.Agent`, LangChain4j `TokenStream`, Quarkiverse `@RegisterAiService` (`Multi<ChatEvent>`), MCP tool client, or A2A sub-agent over the canonical AG-UI protocol in **any Java 17+ framework** (Spring Boot, Quarkus, Helidon, Micronaut, Jakarta EE, or plain Java) and connect any Svelte 5 / JS client:
1. Import `org.agui:ag-ui-bom` in `<dependencyManagement>` and add `org.agui:ag-ui-quarkus` (Quarkus SSE + WebSockets Next + Jackson MixIns + GraalVM reflection), `org.agui:ag-ui-langchain4j` (upstream `TokenStream` + RFC 6902 `AgUiStatePatch` + HITL `Interrupt`), `org.agui:ag-ui-quarkus-langchain4j` (Quarkiverse `Multi<ChatEvent>`), and/or `org.agui:ag-ui-a2a` (A2A `Task` delegation).
2. Instantiate or `@Inject` `AgUiQuarkusBridge`, `AgUiWebSocketHandler`, `AgUiLangChain4jBridge`, `AgUiQuarkusLangChain4jBridge`, `AgUiMcpBridge`, `AgUiA2aBridge`, and `AgUiSessionRegistry`.
3. Stream `Flow.Publisher<Event>` or Mutiny `Multi<String>` emitting canonical AG-UI SSE (`data: {...}\n\n`) and WebSocket frames (`RunAgentInput` $\rightarrow$ `RUN_STARTED` $\rightarrow$ `REASONING_*` $\rightarrow$ `STEP_*` $\rightarrow$ `TOOL_CALL_*` $\rightarrow$ `STATE_SNAPSHOT` / `STATE_DELTA` $\rightarrow$ `TEXT_MESSAGE_*` $\rightarrow$ `RUN_FINISHED` / `RUN_ERROR`).
4. Connect any Svelte 5 / browser client with headless `@agui/svelte` (`HttpAgent`, `WebSocketAgent`, `createAgUiStore`, `applyJsonPatch`):
   ```js
   import { HttpAgent, WebSocketAgent, createAgUiStore } from '@agui/svelte';

   const agent = new WebSocketAgent({ url: 'ws://localhost:8080/api/agent/ws', threadId: 'thread-1' });
   const store = createAgUiStore({ agent });
   await store.runAgent('Plan Kyoto', { forwardedProps: { llmMode: 'ollama:llama3.2:latest' } });
   ```

---

## 📊 Module & Archetype Matrix

| Module | Path | Stack / Runtime | Target Latency / Footprint |
| :--- | :--- | :--- | :--- |
| **`ag-ui-bom`** | [`ag-ui-bom/`](ag-ui-bom/pom.xml) | Maven Bill of Materials (`org.agui` + `com.ag-ui.community:0.1.1`) | Unified dependency management |
| **`ag-ui-quarkus`** | [`ag-ui-quarkus/`](ag-ui-quarkus/pom.xml) | Java 17 Quarkus CDI, Jackson MixIns (`@ag-ui/core` Zod-compatible), Mutiny `Multi` SSE, reusable `AgUiWebSocketHandler`, and 57-type GraalVM `reflect-config.json` | Zero LangChain4j/A2A lock-in, $< 10\text{ms}$ framing |
| **`ag-ui-langchain4j`** | [`ag-ui-langchain4j/`](ag-ui-langchain4j/pom.xml) | Framework-agnostic Java 17 `dev.langchain4j` `TokenStream` & `Agent` adapter (`AgUiLangChain4jBridge`, `AgUiStatePatch`, `AgUiMcpBridge`, `AgUiRunOptions`) | Works in Quarkus, Spring, Helidon, Micronaut & plain Java |
| **`ag-ui-quarkus-langchain4j`** | [`ag-ui-quarkus-langchain4j/`](ag-ui-quarkus-langchain4j/pom.xml) | Java 17 Quarkiverse `Multi<ChatEvent>` reactive adapter (`AgUiQuarkusLangChain4jBridge`) | Decouples `quarkus-langchain4j-core` from `ag-ui-quarkus` |
| **`ag-ui-a2a`** | [`ag-ui-a2a/`](ag-ui-a2a/pom.xml) | Framework-agnostic Java 17 A2A Protocol Bridge (`AgUiA2aBridge` over `org.a2aproject.sdk`) | Emits canonical `StepStarted`/`ActivitySnapshot`/`StepFinished` |
| **`packages/agui-svelte`** | [`packages/agui-svelte/`](packages/agui-svelte/package.json) | Headless Svelte 5 Runes & JS SDK (`@agui/svelte`) over `@ag-ui/client` + `@ag-ui/core` (`^1.0.4`) | `HttpAgent`, `WebSocketAgent`, RFC 6902 `applyJsonPatch`, & Tri-Protocol telemetry |
| **`mcp-server` (`:8081`)** | [`examples/quarkus-svelte-skeleton/mcp-server/`](examples/quarkus-svelte-skeleton/mcp-server/pom.xml) | Java 25 Quarkus 3.40.1.1 Nomad MCP Server + SmallRye Health (`/q/health/ready`) + Jib Container (`nomad-agui-showcase/mcp-server:latest`) | `fetchCityWeather`, `discoverLocalCultureAndMedia`, `updateTripItinerary`, `saveTravelerPreferences`, `sampleAcousticMood`, `resetNomadState` |
| **`a2a-server` (`:8082`)** | [`examples/quarkus-svelte-skeleton/a2a-server/`](examples/quarkus-svelte-skeleton/a2a-server/pom.xml) | Java 25 Quarkus 3.40.1.1 A2A Specialist Server + SmallRye Health (`/q/health/ready`) + Jib Container (`nomad-agui-showcase/a2a-server:latest`) | `convert-and-audit-budget` (Frankfurter FX), `verify-travel-advisory` (REST Countries v5 `api.restcountries.com`) |
| **`backend` (`:8080`)** | [`examples/quarkus-svelte-skeleton/backend/`](examples/quarkus-svelte-skeleton/backend/pom.xml) | Java 25 Quarkus 3.40.1.1 `@RegisterAiService` + `@McpToolBox("nomad")` + SmallRye Health + **Quarkus Quinoa** + Jib Container (`nomad-agui-showcase/backend:latest`) | Serves SvelteKit 5 SPA, AG-UI WebSocket (`/api/agent/ws`), SSE (`/api/agent/run`), and `/q/health/ready` on `:8080` |
| **`frontend` (Quinoa SPA)** | [`examples/quarkus-svelte-skeleton/frontend/`](examples/quarkus-svelte-skeleton/frontend/) | `pnpm` + SvelteKit 5 (`@sveltejs/adapter-static`) packaged into `backend` via `quarkus-quinoa` | 3-Pane Nomad Concierge Studio + Wire Telemetry Inspector served from `:8080` |

---

## 🏛️ System Architecture & Governance

- **Product Constitution**: [`PRODUCT.md`](PRODUCT.md) (`CAP-01`–`CAP-08`, `INV-01`–`INV-08`)
- **Product Requirements (PRD)**: [`docs/specs/PRD.md`](docs/specs/PRD.md)
- **Software Requirements (SRS)**: [`docs/specs/SRS.md`](docs/specs/SRS.md)
- **C4 Architecture**: [`docs/specs/C4_ARCHITECTURE.md`](docs/specs/C4_ARCHITECTURE.md)
- **Living Kanban & Traceability Matrix**: [`docs/KANBAN.md`](docs/KANBAN.md)
- **Causal Decision Lineage**: [`docs/DECISION_MAP.md`](docs/DECISION_MAP.md)
- **Architectural Decision Records (ADRs)**: [`docs/adr/ADR-001`](docs/adr/ADR-001-multi-module-maven-sveltekit-clean-architecture-.md), [`docs/adr/ADR-002`](docs/adr/ADR-002-example-first-tri-protocol-ag-ui-mcp-a2a-evoluti.md), [`docs/adr/ADR-003`](docs/adr/ADR-003-multi-extension-bom-and-concentric-transport-arc.md), [`docs/adr/ADR-004`](docs/adr/ADR-004-nomad-concierge-and-svelte5-agui-component-kit.md), [`docs/adr/ADR-005`](docs/adr/ADR-005-quarkus-langchain4j-mcp-and-smallrye-health.md), [`docs/adr/ADR-006`](docs/adr/ADR-006-framework-agnostic-module-topology-ag-ui-core-ag.md), [`docs/adr/ADR-007`](docs/adr/ADR-007-adopt-upstream-ag-ui-sdks-com-ag-ui-community-ag.md)
