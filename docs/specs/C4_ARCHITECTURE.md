# C4 Architecture Specification: `quarkus-agui`

## 1. System Context Diagram (C4 Level 1 — Tri-Protocol Architecture)

```mermaid
flowchart LR
    User["👤 End User / SRE Operator"]
    Client["🖥️ SvelteKit + Skeleton UI + @lucide/svelte\n(AG-UI 3-Pane Workbench :5173 via pnpm)"]
    Backend["⚙️ OpsPilot Quarkus Orchestrator (:8080)\n(quarkus-ag-ui + ag-ui-core + LangChain4j)"]
    McpServer["🔧 Quarkus MCP Server (:8081)\n(Cluster Tools & Topology Resource)"]
    A2aServer["🛡️ Quarkus A2A Specialist (:8082)\n(Sentinel-A2A-Auditor AgentCard & Tasks)"]

    User -->|"Prompts, State Edits & HITL Approvals"| Client
    Client -->|"POST /api/agent/run & /approve\n(AG-UI SSE text/event-stream)"| Backend
    Backend -->|"MCP Tool Calls & Resource Reads"| McpServer
    Backend -->|"A2A Task Delegation & AgentCard"| A2aServer
```

## 2. Container & Module Diagram (C4 Level 2)

```mermaid
flowchart TD
    subgraph Repo["ag-ui-java Multi-Module Workspace (Java 25 + Quarkus 3.40.1.1 + pnpm)"]
        Core["📦 ag-ui-core (Plain Java 25)\n• Level 0: AgUiEvent<T> & Payload Records\n• Level 1: AgUiEventSerializer & AgUiSseFormatter\n• Level 2: AgUiFlowAdapter (Flow.Publisher)"]
        Runtime["📦 quarkus-ag-ui-runtime\n• AgUiBridge (LangChain4j TokenStream -> Multi<AgUiEvent>)\n• AgUiMcpBridge & AgUiA2aBridge\n• RESTEasy Reactive SSE Writer"]
        Deployment["📦 quarkus-ag-ui-deployment\n• AgUiProcessor (GraalVM 25 ReflectiveClassBuildItem)\n• Dev UI AG-UI Inspector Card"]
        IT["📦 quarkus-ag-ui-integration-tests\n• @QuarkusTest & @QuarkusIntegrationTest"]
        ExMcp["📦 examples/quarkus-svelte-skeleton/mcp-server (:8081)\n• McpServerResource & ClusterTelemetryService"]
        ExA2a["📦 examples/quarkus-svelte-skeleton/a2a-server (:8082)\n• A2aAgentResource & SpecialistAuditAgentService"]
        ExBackend["📦 examples/quarkus-svelte-skeleton/backend (:8080)\n• OpsPilot Orchestrator & AgentResource"]
        ExFrontend["📦 examples/quarkus-svelte-skeleton/frontend (:5173)\n• AgentChatContainer, MessageHistory, StreamRenderer\n• InputBar, ClusterStatePanel, ProtocolInspector"]
    end

    Runtime -->|"Depends On"| Core
    Deployment -->|"Depends On"| Runtime
    Deployment -->|"Registers Reflection"| Core
    IT -->|"Tests"| Runtime
    IT -->|"Tests"| Deployment
    ExBackend -->|"Uses Extension"| Runtime
    ExBackend -->|"Invokes MCP"| ExMcp
    ExBackend -->|"Delegates A2A"| ExA2a
    ExFrontend -->|"Streams AG-UI SSE"| ExBackend
```

## 3. Clean Architecture Concentric Rings (Archlens Governance)

```mermaid
flowchart TD
    L3["Level 3: Infrastructure & Build-Time Deployment\n(org.agui.quarkus.deployment, Quarkus RESTEasy Reactive)"]
    L2["Level 2: Adapters & Bridges\n(org.agui.core.adapters, org.agui.quarkus.runtime)"]
    L1["Level 1: Application Ports & Formatters\n(org.agui.core.application)"]
    L0["Level 0: Domain Core\n(org.agui.core.domain: AgUiEvent, Payload Records)"]

    L3 --> L2
    L2 --> L1
    L1 --> L0
```
