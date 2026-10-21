# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- feat(wave3): extract zero-dependency Java 25 `ag-ui-core` library (`org.agui.core.domain`, `application`, `adapters`: `AgUiEvent`, 10 typed payload records, `AgUiSseFormatter`, `AgUiFlowAdapter`) and refactor showcase backend ([#6](https://github.com/fmatar/quarkus-agui/issues/6))
- feat(wave2): implement `ClusterStatePanel.svelte` (bidirectional replica state stepper & A2A skill triggers) and `ProtocolInspector.svelte` (`ALL`/`AG-UI`/`MCP`/`A2A` filter tabs) in the 3-Pane SvelteKit Workbench ([#5](https://github.com/fmatar/quarkus-agui/issues/5))
- feat(wave2): implement in-place AG-UI protocol engine (`STATE_SNAPSHOT`, `STATE_DELTA`, HITL `interrupted` approval gate `POST /api/agent/approve`) and live local Ollama LLM token streaming (`llama3.2:latest`, `qwen3.5:4b`, `ministral-3:8b`) on Java 25 Virtual Threads ([#4](https://github.com/fmatar/quarkus-agui/issues/4))
- feat(wave1): implement OpsPilot Quarkus Orchestrator (`:8080`) with Dual-Mode LangChain4j and `pnpm` + SvelteKit 5 + `@skeletonlabs/skeleton-svelte` + `@lucide/svelte` 3-pane workbench baseline (`:5173`) ([#3](https://github.com/fmatar/quarkus-agui/issues/3))
- feat(wave1): implement Quarkus 3.40.1.1 + Java 25 MCP Server (`:8081`) and A2A Specialist Auditor Server (`:8082`) with `ADR-002` Tri-Protocol Example-First evolution ([#2](https://github.com/fmatar/quarkus-agui/issues/2))
- feat(genesis): initialize `PRODUCT.md` constitution (`CAP-01`..`CAP-08`, `INV-01`..`INV-08`), formal PRD/SRS/C4 specs, Archlens Clean Architecture policy, GitHub Project v2 board, and multi-module Maven + SvelteKit workspace scaffolding ([#1](https://github.com/fmatar/quarkus-agui/issues/1))
