---
id: ADR-008
title: Canonical AG-UI ACTIVITY_SNAPSHOT Surface Kit, Complete Trip Dossier Report & local-quarkus-agui CI
status: accepted
scope: technical
ticket: #93
supersedes: CAP-03, CAP-04, CAP-08, ADR-004
createdAt: 2026-11-11T00:54:57.826Z
---

# ADR-008: Canonical AG-UI ACTIVITY_SNAPSHOT Surface Kit, Complete Trip Dossier Report & local-quarkus-agui CI

- **Status**: `accepted`
- **Blast Radius / Scope**: `technical`
- **Triggered By Ticket**: #93
- **Supersedes / Amends**: CAP-03, CAP-04, CAP-08, ADR-004

## 1. Context & Spec Drift Trigger
TripDossierStore previously dropped Open-Meteo coordinates/wind speed, neighborhood stops, and REST Countries v5 security/entry advisory details, TripDossierPanel rendered ad-hoc inline summary boxes instead of modular AG-UI components, and sdlc-quality-gate.yml targeted local-archlens without pnpm or Java Dual-Gate steps.

## 2. Decision
1. Extend ag-ui-langchain4j (AgUiRunOptions, AgUiLangChain4jRunSession) and @agui/svelte to emit and reduce canonical ActivitySnapshotEvent (ACTIVITY_SNAPSHOT) envelopes alongside StateDeltaEvent. 2. Deliver a 5-card Svelte 5 AG-UI surface kit (AgUiWeatherCard, AgUiLocationMapCard with Google/Apple/OSM/Wikipedia links + toggleable OSM preview, AgUiSecurityAdvisoryCard, AgUiFxBudgetCard, AgUiCultureAudioCard) dispatched via AgUiActivityRenderer across both AssistantTurn chat streams and TripDossierPanel (+ Markdown export). 3. Auto-populate all 5 live MCP/A2A sections on complete trip runs while emitting focused single cards on single-topic queries. 4. Upgrade sdlc-quality-gate.yml to run the unified Dual-Gate pipeline on runs-on: local-quarkus-agui.

## 3. Consequences & Downstream Impact
Establishes canonical AG-UI Generative UI activity interoperability, upgrades TripDossierPanel into a complete 5-section AG-UI report with Markdown parity, and executes full Maven + pnpm + PITest + SDLC quality gates on the local-quarkus-agui runner.

## 4. Governance Review Decision
- **Decision**: `ACCEPTED`
- **Reviewer**: @human-operator
- **Rationale**: Reviewed via SDLC Governance Gate
- **Decided At**: 2026-11-11T00:54:57.946Z
