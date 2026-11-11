# 🛠️ API, Skills & CLI Reference (`Skills-Reference`)

This reference catalogs the public APIs of the four **Java 17 `org.agui` modules**, the headless **`@agui/svelte` frontend SDK**, and the **Agentic SDLC skills & CLI** that govern repository workflows.

For conceptual background and dependency diagrams before diving into individual classes, start with [`wiki/Home.md`](Home.md) and [`wiki/Architecture.md`](Architecture.md).

---

## 1. Java 17 Library Public API Reference (`org.agui:*:0.2.0`)

Each Java module follows **Concentric Clean Architecture** (`.archlens/policy.json`):
- **Domain (`L0`)**: Pure Java 17 records, enums, and RFC 6902 patch builders with zero framework or transport dependencies.
- **Application (`L1`)**: Transport-agnostic session ports and thread-safe registries.
- **Adapters (`L2`)**: Concrete bridges for Quarkus (Mutiny SSE, WebSockets Next, Jackson MixIns), LangChain4j (`TokenStream` & Quarkiverse `Multi<ChatEvent>`), and official A2A (`1.5.0.Final`).

### 1.1 `org.agui:ag-ui-quarkus`
Provides Quarkus transport adapters, automatic Jackson serialization MixIns for upstream `com.ag-ui.community` models, and 57-type GraalVM native reflection metadata.

| Class / Record | Ring | Key Methods & Responsibility |
| :--- | :--- | :--- |
| `AgUiTransportType` | Domain (L0) | Enum (`SSE`, `WEBSOCKET`) identifying active AG-UI transport mode |
| `AgUiTransportSession` | Application (L1) | Port interface (`sessionId()`, `transportType()`, `isOpen()`, `sendJson(String)`) |
| `AgUiSessionRegistry` | Application (L1) | Thread-safe `@ApplicationScoped` registry (`register`, `unregister`, `find`, `activeCount`, `broadcastJson`) |
| `AgUiQuarkusBridge` | Adapter (L2) | `run(Agent, RunAgentInput)`, `runAsSse(...)`, `runAsJsonPayloads(...)`, `toSseFrames(Multi<Event>)`, `toMulti(Flow.Publisher)`, `unwrapSseData(String)` |
| `AgUiWebSocketHandler` | Adapter (L2) | `onOpen`, `onClose`, `onError`, `runAndSend(Agent, String, WebSocketConnection)`, `cancelRun`, `handleControlFrame`, `setStreamIdleTimeout` (`60s` default) |
| `AgUiJacksonSerializer` | Adapter (L2) | Upstream `Serializer` implementation backed by Jackson + `AgUiJacksonMixins` |
| `AgUiObjectMapperCustomizer` | Adapter (L2) | Quarkus `@Singleton` `ObjectMapperCustomizer` auto-registering `AgUiJacksonMixins` |

### 1.2 `org.agui:ag-ui-langchain4j`
Adapts standard `dev.langchain4j` streaming interfaces into canonical AG-UI lifecycle, tool-call, state-delta, activity-snapshot, and Human-in-the-Loop interrupt events on any Java 17+ runtime.

| Class / Record | Ring | Key Methods & Responsibility |
| :--- | :--- | :--- |
| `AgUiStatePatch` | Domain (L0) | RFC 6901/6902 JSON Patch builder (`toPointer`, `add`, `replace`, `remove`, `upsertRootFields`) |
| `McpToolMetadata` | Domain (L0) | Metadata record (`serverName`, `toolName`, `transport`) for MCP tool provenance |
| `AgUiLangChain4jBridge` | Adapter (L2) | `adapt(threadId, TokenStream, AgUiRunOptions)`, `asAgent(streamFactory, optionsFactory)`, `newSession(...)` |
| `AgUiLangChain4jRunSession` | Adapter (L2) | Per-run state machine translating thinking, RAG retrieval, tool calls, `ACTIVITY_SNAPSHOT`s, patches, tokens, and HITL interrupts |
| `AgUiRunOptions` | Adapter (L2) | Builder configuring `runId`, `llmMode`, `initialStateSnapshot`, `toolClassifier`, `postToolStatePatchProvider`, `postRunStatePatchProvider`, `postToolActivityProvider`, `postRunActivityProvider`, `interruptSupplier`, `runResultSupplier` |
| `AgUiMcpBridge` | Adapter (L2) | Builds canonical MCP tool-call and resource provenance payloads for `Event.rawEvent()` |
| `AgUiToolClassifier` | Adapter (L2) | Classifies executed tools into Tri-Protocol `callType` categories (`mcp:tool`, `mcp:sampling`, `a2a:task`, `tool:ui`, `hitl:approval`) |

### 1.3 `org.agui:ag-ui-quarkus-langchain4j`
Connects Quarkiverse `@RegisterAiService` reactive Mutiny streams (`Multi<ChatEvent>`) to `AgUiLangChain4jRunSession` and `AgUiQuarkusBridge` while keeping `ag-ui-quarkus` independent of LangChain4j.

| Class | Ring | Key Methods & Responsibility |
| :--- | :--- | :--- |
| `AgUiQuarkusLangChain4jBridge` | Adapter (L2) | `adapt(threadId, Multi<ChatEvent>, AgUiRunOptions)`, `adaptToSse(...)`, `adaptToJsonPayloads(...)`, `asAgent(chatFactory, optionsFactory)` |

### 1.4 `org.agui:ag-ui-a2a` (`ADR-009`)
Wraps the official A2A Java SDK (`1.5.0.Final`) for remote `AgentCard` discovery, SSRF-safe endpoint rebinding, JSON-RPC / SSE task delegation, and AG-UI event projection.

| Class / Record | Ring | Key Methods & Responsibility |
| :--- | :--- | :--- |
| `A2aDelegationResult` | Domain (L0) | Pure Java 17 record (`taskId`, `agentName`, `skillId`, `status`, `isError`, `summary`, `data`) |
| `A2aHandoff` | Domain (L0) | Specialist delegation handoff descriptor (`taskId`, `agentName`, `skillId`, `summary`, `metadata`) |
| `AgUiA2aClient` | Adapter (L2) | Official A2A `1.5.0.Final` `AutoCloseable` client (`builder(baseUrl)`, `resolveAgentCard()`, `delegate(...)`, `rebindCardEndpoints(...)`, `invalidate()`) |
| `AgUiA2aBridge` | Adapter (L2) | Maps A2A `Task`, `Artifact`, `DataPart`, `Message`, and `ClientEvent` objects into `StepStartedEvent`, `ActivitySnapshotEvent`, and `StepFinishedEvent` |

---

## 2. Headless `@agui/svelte` SDK Reference (`packages/agui-svelte`)

`@agui/svelte` wraps upstream `@ag-ui/client` and `@ag-ui/core` (`^1.0.4`) in a headless reactive controller that works in Svelte 5 Runes apps as well as framework-free JavaScript tests:

- **Agents & Reactive Stores** (`packages/agui-svelte/src/store.js`, `packages/agui-svelte/src/websocket-agent.js`):
  - `HttpAgent`, `AbstractAgent` (re-exported from `@ag-ui/client`), `EventType` (from `@ag-ui/core`)
  - `WebSocketAgent` — Full-duplex `AbstractAgent` implementation with heartbeat `ping`/`pong`, inactivity timeouts, and run cancellation
  - `createAgUiStore`, `useAgUiAgent`, `createDefaultAgentState`, `reduceCanonicalAgUiEvent`, `parseSseChunk`, `selectTurnOption`, `lockTurnInteraction`
- **RFC 6902 JSON Patch** (`packages/agui-svelte/src/json-patch.js`):
  - `applyJsonPatch(state, operations)`, `applyJsonPatchOperation(state, op)`, `unescapeJsonPointerToken(token)` — Immutable updates rejecting `__proto__`, `constructor`, and `prototype` (CWE-1321)
- **Canonical `ACTIVITY_SNAPSHOT` Registry** (`packages/agui-svelte/src/activities.js`, `ADR-008`):
  - `AGUI_ACTIVITY_TYPES` (`WEATHER`, `MAP_PLACES`, `SECURITY_ADVISORY`, `FX_BUDGET`, `CULTURE_AUDIO`), `createActivityRegistry`, `upsertActivityList`, `buildPlaceMapLinks`, `sanitizeExternalUrl`, `formatCoordinatesLabel`
- **Tri-Protocol Wire Taxonomy** (`packages/agui-svelte/src/taxonomy.js`):
  - `PROTOCOL_CALL_TAXONOMY`, `resolveProtocolCallMeta`, `resolveEventProtocolMeta`, `filterInspectorEvents`, `recordClientOutboundEvent`

---

## 3. Canonical Agentic SDLC Skills Suite (`.agent/skills/`)

Repository development, testing, architecture verification, and documentation hygiene are automated through the Universal Router (`sdlc`) plus 9 verb-oriented specialist skills:

| Skill Name | Definition Path | Responsibility |
| :--- | :--- | :--- |
| **`sdlc`** | `.agent/skills/sdlc/SKILL.md` | Universal 1:1 `/sdlc <subcommand>` router mapping chat commands to `.agent/scripts/sdlc-engine.mjs` |
| **`sdlc-product`** | `.agent/skills/sdlc-product/SKILL.md` | Synthesizes `PRODUCT.md` (`CAP-XX`, `INV-XX`), authors `docs/specs/` (`PRD.md`, `SRS.md`, `C4_ARCHITECTURE.md`), and plans topological DAG waves |
| **`sdlc-adr`** | `.agent/skills/sdlc-adr/SKILL.md` | Mints, lists, decides, and compacts Spec-Drift ADRs in `docs/adr/` and syncs `docs/DECISION_MAP.md` |
| **`sdlc-flow`** | `.agent/skills/sdlc-flow/SKILL.md` | End-to-end ticket orchestrator (`stage begin` -> TDD -> `stage pre-pr` -> review -> `stage complete`) |
| **`sdlc-tdd`** | `.agent/skills/sdlc-tdd/SKILL.md` | Enforces Robert C. Martin Red-Green-Refactor TDD, `>= 85%` coverage, and `>= 80%` mutation kill score (`INV-02`) |
| **`sdlc-gate`** | `.agent/skills/sdlc-gate/SKILL.md` | Parallel fork-join PR reviewer, 9-pillar quality gatekeeper (`gate --strict`), and Stage 7 Merkle receipt issuer |
| **`sdlc-status`** | `.agent/skills/sdlc-status/SKILL.md` | Displays active session, 8-stage progress, spec-drift check, `docs/KANBAN.md`, and Architecture Diff UI |
| **`sdlc-doctor`** | `.agent/skills/sdlc-doctor/SKILL.md` | Polyglot stack detection, `doctor --fix`, 4-Layer Git Guard hooks, `.archlens/policy.json`, and multi-agent installer |
| **`sdlc-release`** | `.agent/skills/sdlc-release/SKILL.md` | Synchronizes `README.md`, `docs/specs/`, `CHANGELOG.md`, and `wiki/`, compacts ADRs, cuts SemVer tags, and runs canary watchdogs |
| **`sdlc-wiki`** | `.agent/skills/sdlc-wiki/SKILL.md` | Technical Writer maintaining the 10–30–90 `README.md`, `llms.txt`, and Diátaxis `wiki/*.md` suite |

Workspace companion skills: `ub-mode` (`.agent/skills/ub-mode/SKILL.md`), `impeccable` (`.agent/skills/impeccable/SKILL.md`), and `prd` (`.agent/skills/prd/SKILL.md`).

---

## 4. Core `sdlc-engine.mjs` CLI Reference

Run `node .agent/scripts/sdlc-engine.mjs help` for the complete list of governance commands:

```bash
# Stack & Environment Diagnostics
node .agent/scripts/sdlc-engine.mjs detect
node .agent/scripts/sdlc-engine.mjs doctor
node .agent/scripts/sdlc-engine.mjs session dashboard

# Composite Lifecycle Transitions
node .agent/scripts/sdlc-engine.mjs stage begin --issue <id> --title "<slug>" --checkout
node .agent/scripts/sdlc-engine.mjs stage pre-pr --issue <id> --summary "<summary>"
node .agent/scripts/sdlc-engine.mjs stage complete --issue <id> --pr <prNumber>

# Quality Gate, Coverage & Mutation Defense
node .agent/scripts/sdlc-engine.mjs test --affected-only
node .agent/scripts/sdlc-engine.mjs coverage
node .agent/scripts/sdlc-engine.mjs mutation
node .agent/scripts/sdlc-engine.mjs gate --strict

# Architecture, Spec-Drift & Documentation Audits
node .agent/scripts/sdlc-engine.mjs drift check
node .agent/scripts/sdlc-engine.mjs readme audit --strict
node .agent/scripts/sdlc-engine.mjs wiki sync
node .agent/scripts/sdlc-engine.mjs wiki audit --strict
node .agent/scripts/sdlc-engine.mjs audit verify
```

Under the hood, `.agent/scripts/sdlc-engine.mjs` delegates across focused domain and adapter modules in `.agent/scripts/lib/` (`core.mjs`, `audit-chain.mjs`, `cache.mjs`, `decision-graph.mjs`, `arch-trace.mjs`, `adr-drift.mjs`, `product-dag.mjs`, `dossier.mjs`, `readme-sync.mjs`, `session.mjs`, `wiki-sync.mjs`, `pm-provider.mjs`, `git-guard.mjs`, `installer.mjs`, `workbench-server.mjs`, and `quality-gate.mjs`) — see [`wiki/Codebase-Map.md`](Codebase-Map.md) for full AST signatures.
