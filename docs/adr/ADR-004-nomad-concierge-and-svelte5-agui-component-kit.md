# ADR-004: Nomad Cultural Concierge Showcase & Modular Svelte 5 AG-UI Component Kit

- **Status:** `accepted`
- **Date:** `2026-11-08`
- **Triggered By:** `#32` (`feature/issue-#32-nomad-concierge-agui-kit`)
- **Scope:** `product`
- **Amends / Realizes:** `CAP-01`, `CAP-02`, `CAP-03`, `CAP-04`, `CAP-06`, `CAP-07`, `CAP-08`

---

## 1. Context
With the multi-extension Quarkus AG-UI BOM and concentric Clean Architecture (`ADR-003`) in place, the reference showcase (`examples/quarkus-svelte-skeleton`) needed to evolve beyond an internal DevOps/SRE dashboard into a consumer-friendly, commercial-grade **Tri-Protocol (`AG-UI + MCP + A2A`) Reference Application** accompanied by a reusable **Svelte 5 Runes AG-UI Component Kit**.

## 2. Decision
1. **Nomad — AI Travel, Weather, Currency & Local Culture Concierge (`:8081`, `:8082`, `:8080`)**:
   - **MCP Server (`:8081` — `ClusterTelemetryService`)**: Exposes live zero-key public API tools (`fetchCityWeather` via Open-Meteo, `discoverLocalCultureAndMedia` via Wikipedia REST API v1 and Apple iTunes Search API 30-second audio previews, `updateTripItinerary`), MCP resources (`itinerary://active-trip`, `traveler://preferences`), and MCP prompt templates (`weekend-cultural-escape`, `local-music-and-food-walk`), with deterministic offline/CI fallbacks.
   - **A2A Specialist Server (`:8082` — `SpecialistAuditAgentService`)**: Extends `Sentinel-A2A-Auditor` with `convert-and-audit-budget` (live Frankfurter ECB FX API with deterministic fallback) and `verify-travel-advisory` (live REST Countries API v3.1 with deterministic fallback).
   - **Orchestrator (`:8080` — `OpsPilotOrchestratorService`)**: Streams AG-UI SSE events enriched with a 10-type protocol taxonomy (`callType`: `tool:function`, `tool:ui`, `mcp:tool`, `mcp:resource`, `mcp:prompt`, `mcp:sampling`, `a2a:discovery`, `a2a:task`, `a2a:artifact`, `hitl:approval`), `media` (`image`, playable 30s `audio`, `video`, `document`), `interaction` (`single_select`, `multi_select`, `actions`), `sources`, `followUps`, and `/` slash commands (`/kyoto`, `/lisbon`, `/reykjavik`, `/media`, `/choices`, `/mcp`, `/a2a`, `/hitl`).
2. **Modular Svelte 5 Runes AG-UI Component Kit (`examples/quarkus-svelte-skeleton/frontend/src/lib/components/agui/*`)**:
   - Extracted `AssistantTurn.svelte`, `ProtocolCallBadge.svelte`, `ReasoningAccordion.svelte`, `ExecutionTimeline.svelte`, `StreamingMarkdownBody.svelte`, `MediaGallery.svelte`, `StructuredResponseBlock.svelte`, `HitlApprovalCard.svelte`, and `TurnFooter.svelte`.
3. **Commercial Minimalist Design (`Precision Travel Instrument & Editorial Concierge Desk`)**:
   - Adopted warm anodized alabaster (`#F6F5F2`), brushed limestone (`#EFECE6`), crisp paper (`#FFFFFF`), obsidian ink (`#141413`), burnt terracotta (`#C84B31`), forest emerald (`#1F6E50`), and indigo (`#3B4CCA`) with tabular numerals and zero mechanical violations under `impeccable detect`.

## 3. Consequences
- **Positive:** Demonstrates every AG-UI, MCP, and A2A capability in an immediately relatable consumer product while retaining 100% deterministic CI testability and providing drop-in Svelte 5 AG-UI components.
