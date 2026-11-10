# C4 Architecture Specification: `quarkus-agui`

## 1. System Context Diagram (C4 Level 1 — Tri-Protocol Architecture)

```mermaid
flowchart LR
    User["End User / Traveler"]
    Client["SvelteKit 5 + @agui/svelte\n(@ag-ui/client + Skeleton UI :8080 via Quinoa)"]
    Backend["Nomad Quarkus Orchestrator (:8080)\n(ag-ui-quarkus + ag-ui-quarkus-langchain4j + Java 25)"]
    McpServer["Quarkus MCP Server (:8081)\n(Open-Meteo, Wikipedia, iTunes Tools & Resources)"]
    A2aServer["Quarkus A2A Specialist (:8082)\n(Frankfurter FX & REST Countries v5 AgentCard)"]

    User -->|"Prompts, Trip Edits & HITL Resume"| Client
    Client -->|"POST /api/agent/run (SSE) & /api/agent/ws\n(Canonical RunAgentInput & AG-UI Events)"| Backend
    Backend -->|"MCP Streamable HTTP Tools & Resources"| McpServer
    Backend -->|"A2A JSON-RPC Task Delegation"| A2aServer
```

## 2. Container & Module Diagram (C4 Level 2 — `ADR-007`)

```mermaid
flowchart TD
    UpstreamJava["com.ag-ui.community:{java-core,java-server}:0.1.1"]
    UpstreamJs["@ag-ui/core + @ag-ui/client"]

    subgraph Repo["quarkus-agui Multi-Module Workspace (Apache-2.0 OR MIT)"]
        QuarkusMod["ag-ui-quarkus (Java 17)\n• AgUiJacksonMixins & AgUiObjectMapperCustomizer\n• AgUiQuarkusBridge (Mutiny Multi SSE)\n• AgUiWebSocketHandler & AgUiSessionRegistry\n• GraalVM reflect-config.json"]
        Lc4jMod["ag-ui-langchain4j (Java 17)\n• AgUiLangChain4jBridge & RunSession\n• AgUiStatePatch (RFC 6902 JSON Patch)\n• AgUiMcpBridge & HITL Interrupts"]
        QLc4jMod["ag-ui-quarkus-langchain4j (Java 17)\n• AgUiQuarkusLangChain4jBridge (Multi<ChatEvent>)"]
        A2aMod["ag-ui-a2a (Java 17)\n• AgUiA2aBridge (Step & ActivitySnapshot Events)"]
        SveltePkg["packages/agui-svelte\n• Headless Svelte 5 Runes SDK\n• HttpAgent & WebSocketAgent"]
        ExMcp["examples/quarkus-svelte-skeleton/mcp-server (:8081, Java 25)"]
        ExA2a["examples/quarkus-svelte-skeleton/a2a-server (:8082, Java 25)"]
        ExBackend["examples/quarkus-svelte-skeleton/backend (:8080, Java 25)"]
        ExFrontend["examples/quarkus-svelte-skeleton/frontend (SvelteKit 5 + Quinoa)"]
    end

    QuarkusMod -->|"Depends On"| UpstreamJava
    Lc4jMod -->|"Depends On"| UpstreamJava
    A2aMod -->|"Depends On"| UpstreamJava
    QLc4jMod -->|"Depends On"| QuarkusMod
    QLc4jMod -->|"Depends On"| Lc4jMod
    SveltePkg -->|"Wraps"| UpstreamJs
    ExBackend -->|"Uses"| QLc4jMod
    ExBackend -->|"Uses"| A2aMod
    ExBackend -->|"Invokes MCP"| ExMcp
    ExBackend -->|"Delegates A2A"| ExA2a
    ExFrontend -->|"Uses"| SveltePkg
    ExFrontend -->|"Streams AG-UI SSE & WS"| ExBackend
```

## 3. Clean Architecture Concentric Rings (Archlens Governance on `ag-ui-quarkus`)

```mermaid
flowchart TD
    L2["Level 2: Adapters & Bridges\n(org.agui.quarkus.adapters: Jackson MixIns, Mutiny SSE, WebSocket Handler)"]
    L1["Level 1: Application Ports & Session Registry\n(org.agui.quarkus.application: AgUiTransportSession, AgUiSessionRegistry)"]
    L0["Level 0: Domain Core\n(org.agui.quarkus.domain + com.agui.community.core)"]

    L2 --> L1
    L1 --> L0
```
