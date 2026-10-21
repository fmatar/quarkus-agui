# C4 Architecture Specification: `quarkus-agui`

## 1. System Context Diagram (C4 Level 1)

```mermaid
flowchart LR
    User["👤 End User / Developer"]
    Client["🖥️ SvelteKit + Skeleton UI Client\n(AG-UI Frontend)"]
    Backend["⚙️ Quarkus Application\n(quarkus-ag-ui + ag-ui-core)"]
    LLM["🧠 LLM / LangChain4j Model\n(OpenAI / Local Model)"]

    User -->|"Submits prompt & views streamed response"| Client
    Client -->|"POST /api/agent/run\n(SSE text/event-stream)"| Backend
    Backend -->|"TokenStream & @Tool orchestration"| LLM
```

## 2. Container & Module Diagram (C4 Level 2)

```mermaid
flowchart TD
    subgraph Repo["ag-ui-java Multi-Module Workspace"]
        Core["📦 ag-ui-core (Plain Java 17+)\n• Level 0: AgUiEvent<T> & Payload Records\n• Level 1: AgUiEventSerializer & AgUiSseFormatter\n• Level 2: AgUiFlowAdapter (Flow.Publisher)"]
        Runtime["📦 quarkus-ag-ui-runtime\n• AgUiBridge (LangChain4j TokenStream -> Multi<AgUiEvent>)\n• RESTEasy Reactive SSE Writer"]
        Deployment["📦 quarkus-ag-ui-deployment\n• AgUiProcessor (ReflectiveClassBuildItem)\n• Dev UI AG-UI Inspector Card"]
        IT["📦 quarkus-ag-ui-integration-tests\n• @QuarkusTest & @QuarkusIntegrationTest"]
        ExBackend["📦 examples/quarkus-svelte-skeleton/backend\n• AgentResource (POST /api/agent/run)"]
        ExFrontend["📦 examples/quarkus-svelte-skeleton/frontend\n• AgentChatContainer.svelte\n• MessageHistory.svelte\n• StreamRenderer.svelte\n• InputBar.svelte"]
    end

    Runtime -->|"Depends On"| Core
    Deployment -->|"Depends On"| Runtime
    Deployment -->|"Registers Reflection"| Core
    IT -->|"Tests"| Runtime
    IT -->|"Tests"| Deployment
    ExBackend -->|"Uses Extension"| Runtime
    ExFrontend -->|"Streams SSE"| ExBackend
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
