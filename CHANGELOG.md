# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- feat(ui): implement Wide-Resolution (`1920px`–`2560px+`) Container-Query Editorial Split (`@container turn-card (inline-size > 820px)`) in `AssistantTurn.svelte`, `aspect-[16/10]` architectural photography framing & blurred `max-w-5xl` Lightbox in `MediaGallery.svelte`, 2-column collapsible `ExecutionTimeline.svelte`, and deep-linked Verified Sources (`api.open-meteo.com/v1/forecast`, `restcountries.com/v3.1/name/...?fields=...`, `api.frankfurter.dev/v1/latest`, `itunes.apple.com/search`) ([#57](https://github.com/fmatar/quarkus-agui/issues/57))
- feat(showcase): implement Full-Duplex Quarkus WebSockets Next (`/api/agent/ws`), Multi-Turn Conversational Memory & `traveler://preferences` extraction, `quarkus-langchain4j` Input/Output Guardrails + Cultural Field Notes RAG + Specialist Persona Council, `quarkus-mcp-server` `@ResourceTemplate` + `@CompletePrompt` + `mcp:sampling` + `.md` export, and GSAP motion choreography ([#51](https://github.com/fmatar/quarkus-agui/issues/51), [#52](https://github.com/fmatar/quarkus-agui/issues/52), [#53](https://github.com/fmatar/quarkus-agui/issues/53), [#54](https://github.com/fmatar/quarkus-agui/issues/54), [#55](https://github.com/fmatar/quarkus-agui/issues/55))

### Fixed
- fix(showcase): resolve Svelte 5 `each_key_duplicate` runtime crash in `ExecutionTimeline.svelte` when streaming multiple A2A steps, configure `AGUI_A2A_PUBLIC_URL=http://a2a-server:8082` in `SentinelA2aAgentConfiguration` & `docker-compose.yml`, wire `McpGatewayClient` to invoke `mcp-server (:8081)` over HTTP for live Open-Meteo/Wikipedia/iTunes 30s audio previews, and make Nomad Concierge the default persona for free-form chat ([#38](https://github.com/fmatar/quarkus-agui/issues/38))
- fix(showcase): include `pnpm-workspace.yaml` (`allowBuilds: esbuild: true`) in `frontend/Dockerfile`, add `frontend/.dockerignore`, and use `install -DskipTests` in `Dockerfile.quarkus` so all 4 Docker Compose images build cleanly ([#36](https://github.com/fmatar/quarkus-agui/issues/36))

### Added
- feat(showcase): add `docker-compose.yml`, multi-stage `Dockerfile.quarkus`, `frontend/Dockerfile`, and `run-showcase.sh` launcher for one-command execution of the 4-service Nomad Tri-Protocol showcase ([#34](https://github.com/fmatar/quarkus-agui/issues/34))
- feat(showcase): implement Nomad AI Travel, Weather, Currency & Local Culture Concierge (Open-Meteo, Wikipedia REST, Apple iTunes 30s audio preview, Frankfurter FX, REST Countries), 10-type protocol call taxonomy (`callType`), modular Svelte 5 Runes AG-UI Component Kit (`src/lib/components/agui/*`), and commercial minimalist UI (`ADR-004`, [#32](https://github.com/fmatar/quarkus-agui/issues/32))
- feat(arch): introduce `quarkus-ag-ui-bom` and modular transport sub-extensions (`quarkus-ag-ui-core`, `quarkus-ag-ui-sse`, `quarkus-ag-ui-websockets`) adhering to concentric Clean Architecture ports and adapters (`ADR-003`, [#30](https://github.com/fmatar/quarkus-agui/issues/30))
- feat(wave4): migrate showcase `backend` to consume `org.agui:quarkus-ag-ui` (`AgUiBridge`, `AgUiMcpBridge`, `AgUiA2aBridge`), verify `< 10ms` serialization overhead, and complete 4-Wave Tri-Protocol evolution ([#9](https://github.com/fmatar/quarkus-agui/issues/9))
- feat(wave4): implement `quarkus-ag-ui-deployment` (`AgUiProcessor`, GraalVM 25 `ReflectiveClassBuildItem`s, `AdditionalBeanBuildItem`, Dev UI Inspector metadata) and `quarkus-ag-ui-integration-tests` ([#8](https://github.com/fmatar/quarkus-agui/issues/8))
- feat(wave4): extract `quarkus-ag-ui` runtime module (`AgUiBridge`, `AgUiMcpBridge`, `AgUiA2aBridge`) with SmallRye Mutiny `Multi<AgUiEvent<?>>` and SSE streaming ([#7](https://github.com/fmatar/quarkus-agui/issues/7))
- feat(wave3): extract zero-dependency Java 25 `ag-ui-core` library (`org.agui.core.domain`, `application`, `adapters`: `AgUiEvent`, 10 typed payload records, `AgUiSseFormatter`, `AgUiFlowAdapter`) and refactor showcase backend ([#6](https://github.com/fmatar/quarkus-agui/issues/6))
- feat(wave2): implement `ClusterStatePanel.svelte` (bidirectional replica state stepper & A2A skill triggers) and `ProtocolInspector.svelte` (`ALL`/`AG-UI`/`MCP`/`A2A` filter tabs) in the 3-Pane SvelteKit Workbench ([#5](https://github.com/fmatar/quarkus-agui/issues/5))
- feat(wave2): implement in-place AG-UI protocol engine (`STATE_SNAPSHOT`, `STATE_DELTA`, HITL `interrupted` approval gate `POST /api/agent/approve`) and live local Ollama LLM token streaming (`llama3.2:latest`, `qwen3.5:4b`, `ministral-3:8b`) on Java 25 Virtual Threads ([#4](https://github.com/fmatar/quarkus-agui/issues/4))
- feat(wave1): implement OpsPilot Quarkus Orchestrator (`:8080`) with Dual-Mode LangChain4j and `pnpm` + SvelteKit 5 + `@skeletonlabs/skeleton-svelte` + `@lucide/svelte` 3-pane workbench baseline (`:5173`) ([#3](https://github.com/fmatar/quarkus-agui/issues/3))
- feat(wave1): implement Quarkus 3.40.1.1 + Java 25 MCP Server (`:8081`) and A2A Specialist Auditor Server (`:8082`) with `ADR-002` Tri-Protocol Example-First evolution ([#2](https://github.com/fmatar/quarkus-agui/issues/2))
- feat(genesis): initialize `PRODUCT.md` constitution (`CAP-01`..`CAP-08`, `INV-01`..`INV-08`), formal PRD/SRS/C4 specs, Archlens Clean Architecture policy, GitHub Project v2 board, and multi-module Maven + SvelteKit workspace scaffolding ([#1](https://github.com/fmatar/quarkus-agui/issues/1))
