# ⚡ Quarkus AG-UI (`quarkus-agui`)

> **Java 17+ Core Library (`ag-ui-core`) & Quarkus Extension (`quarkus-ag-ui`) for the AG-UI (Agent–User Interaction) Protocol**  
> Powered by **Quarkus RESTEasy Reactive**, **LangChain4j**, **SmallRye Mutiny**, and a **SvelteKit + Skeleton UI** Reference Client.

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-orange.svg)](pom.xml)
[![Architecture: Archlens Clean Architecture](https://img.shields.io/badge/Architecture-Clean_Concentric_Rings-success.svg)](.archlens/policy.json)

---

## 🚀 5-Minute Developer Quickstart

### 1. CLI & Toolchain Prerequisites
Ensure **Java 17+** (or Java 21+ / GraalVM) and **Node.js 20+** are installed:
```bash
java -version
mvn -version
node -v
```

### 2. Local Build & Environment Bootstrap
```bash
# Clone and configure environment
cp .env.example .env

# Build Core Java Library and Quarkus Extension modules
mvn clean test-compile

# Verify Agentic SDLC health & Clean Architecture policy
node .agent/scripts/sdlc-engine.mjs env
node .agent/scripts/sdlc-engine.mjs product status
```

### 3. Explore the Reference Showcase (`examples/quarkus-svelte-skeleton`)
```bash
# Terminal 1: Start the Quarkus + LangChain4j AG-UI Backend (Port 8080)
mvn -f examples/quarkus-svelte-skeleton/backend/pom.xml quarkus:dev

# Terminal 2: Start the SvelteKit + Skeleton UI Streaming Client (Port 5173)
cd examples/quarkus-svelte-skeleton/frontend && npm install && npm run dev
```

Test the live AG-UI SSE stream directly from your terminal:
```bash
curl -N -X POST http://localhost:8080/api/agent/run \
  -H "Content-Type: application/json" \
  -d '{"threadId":"thread-demo-1","prompt":"Inspect system architecture"}'
```

### 4. Bring Your Own Agent (BYOA) Contract
To expose any LangChain4j `TokenStream` or custom JVM agent over the AG-UI protocol in your own Quarkus project:
1. Add `org.agui:quarkus-ag-ui` to your `pom.xml`.
2. Inject `AgUiBridge` (or use `AgUiSseFormatter` from `ag-ui-core` in non-Quarkus apps).
3. Return `Multi<String>` (SSE frames) or `Multi<ServerSentEvent<AgUiEvent<?>>>` with `@Produces(MediaType.SERVER_SENT_EVENTS)` emitting standard AG-UI events:
   - `RUN_STARTED` $\rightarrow$ `STATUS` $\rightarrow$ `TOOL_CALL` / `TOOL_RESULT` $\rightarrow$ `TEXT_DELTA` $\rightarrow$ `RUN_FINISHED`.

---

## 📊 Module & Archetype Matrix

| Module | Path | Stack / Runtime | Target Latency / Footprint |
| :--- | :--- | :--- | :--- |
| **`ag-ui-core`** | [`ag-ui-core/`](ag-ui-core/pom.xml) | Plain Java 17+ (Zero framework deps) | $< 10\text{ms}$ serialization overhead |
| **`quarkus-ag-ui-runtime`** | [`quarkus-extension/runtime/`](quarkus-extension/runtime/pom.xml) | Quarkus RESTEasy Reactive + Mutiny + LangChain4j | Reactive non-blocking SSE |
| **`quarkus-ag-ui-deployment`** | [`quarkus-extension/deployment/`](quarkus-extension/deployment/pom.xml) | Quarkus Build-Time Processor + Dev UI + GraalVM | $< 50\text{MB}$ Native Image RSS |
| **`quarkus-ag-ui-integration-tests`** | [`quarkus-extension/integration-tests/`](quarkus-extension/integration-tests/pom.xml) | `@QuarkusTest` & `@QuarkusIntegrationTest` | Ephemeral port `0` isolation |
| **`quarkus-svelte-skeleton`** | [`examples/quarkus-svelte-skeleton/`](examples/quarkus-svelte-skeleton/) | Quarkus + LangChain4j + SvelteKit + Skeleton UI | Real-time `\n\n` SSE frame streaming |

---

## 🏛️ System Architecture & Governance

- **Product Constitution**: [`PRODUCT.md`](PRODUCT.md) (`CAP-01`–`CAP-08`, `INV-01`–`INV-07`)
- **Product Requirements (PRD)**: [`docs/specs/PRD.md`](docs/specs/PRD.md)
- **Software Requirements (SRS)**: [`docs/specs/SRS.md`](docs/specs/SRS.md)
- **C4 Architecture**: [`docs/specs/C4_ARCHITECTURE.md`](docs/specs/C4_ARCHITECTURE.md)
- **Living Kanban & Traceability Matrix**: [`docs/KANBAN.md`](docs/KANBAN.md)
- **Causal Decision Lineage**: [`docs/DECISION_MAP.md`](docs/DECISION_MAP.md)
