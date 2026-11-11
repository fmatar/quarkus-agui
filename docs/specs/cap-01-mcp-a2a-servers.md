# SPEC-01: Quarkus MCP Server (`:8081`) & Quarkus A2A Server (`:8082`) in Reference Showcase

- **Linked Capability**: `CAP-01`
- **Tickets**: `#2`, `#19`, `#32`, `#54`, `#66`, `#68`, `#76`, `#78`, `#100`
- **Modules**: `examples/quarkus-svelte-skeleton/mcp-server` & `examples/quarkus-svelte-skeleton/a2a-server`
- **Stack**: Java 25 + Quarkus `3.40.1.1` + SmallRye Health + Jib + Portable GraalVM 25 Native (`-Dnative` / `-Dnative-container`)
- **Status**: `AMENDED (ADR-004, ADR-005 & ADR-009)`

## 1. Problem Statement & Technical Scope
Provides standalone **MCP (Model Context Protocol)** and **A2A (Agent-to-Agent)** microservices built on Quarkus `3.40.1.1` and Java 25 for the Nomad Travel & Culture Concierge showcase.

## 2. Module Specifications

### 2.1 `mcp-server` (Port `8081` — `io.quarkiverse.mcp:quarkus-mcp-server-http`)
- **MCP `@Tool` Methods & Companion REST Endpoints (`/mcp` & `/api/mcp/tools`)**:
  1. `fetchCityWeather(String city)`: Live Open-Meteo geocoding & weather telemetry.
  2. `discoverLocalCultureAndMedia(String city, String interest)`: Wikipedia REST landmark dossiers & photography.
  3. `updateTripItinerary(String city, int nights, double budgetUsd, String style)`: Mutates live trip itinerary state.
  4. `saveTravelerPreferences(String travelerName, String homeCurrency, String musicGenre, String dietaryNotes)`: Idempotent `traveler://preferences` persistence.
  5. `sampleAcousticMood(String city, String genre)`: Curated 30s Apple iTunes audio preview sampling.
  6. `resetNomadState()`: Resets session dossier state.
- **MCP `@ResourceTemplate` (`nomad://city/{city}`) & `@CompletePrompt`**: Structured city dossiers and prompt argument completion.

### 2.2 `a2a-server` (Port `8082` — `org.a2aproject.sdk:1.5.0.Final`)
- **A2A Discovery (`GET /.well-known/agent-card.json`)**: Official `Nomad-A2A-Specialist` `AgentCard` (`1.5.0`) discovered by `A2ACardResolver`.
- **A2A Task Execution (JSON-RPC 2.0 `POST /` with `A2A-Version: 1.0`)**: Executes `convert-and-audit-budget` (Frankfurter FX + `open.er-api.com` ISO 4217 fallback), `verify-travel-advisory` (REST Countries v5), and `curate-neighborhood-cadence` via `AgentExecutor` returning structured `DataPart` + `TextPart` `Artifact`s.

## 3. Acceptance Criteria (DoD)
- **AC-01**: Both servers compile on Java 25, expose `/q/health/live` & `/q/health/ready` (5-minute TTL external probe cache), and run tests on ephemeral port `0`.
- **AC-02**: Dual-Gate verification passes (`>= 85%` coverage, `>= 80%` PITest mutation score).
