---
id: ADR-009
title: Adopt Official A2A Java SDK 1.5.0.Final Client (A2ACardResolver, ClientBuilder & VertxA2AHttpClient) and Remove Custom HTTP Audit Endpoint
status: accepted
scope: technical
ticket: #100
supersedes: CAP-01, CAP-06
createdAt: 2026-11-11T13:31:40.579Z
---

# ADR-009: Adopt Official A2A Java SDK 1.5.0.Final Client (A2ACardResolver, ClientBuilder & VertxA2AHttpClient) and Remove Custom HTTP Audit Endpoint

- **Status**: `accepted`
- **Blast Radius / Scope**: `technical`
- **Triggered By Ticket**: #100
- **Supersedes / Amends**: CAP-01, CAP-06

## 1. Context & Spec Drift Trigger
While `examples/quarkus-svelte-skeleton/a2a-server` (`:8082`) already exposed an official A2A server (`@PublicAgentCard` + `AgentExecutor` over JSON-RPC 2.0), the orchestrator's `A2aGatewayClient` (`:8080`) had been hand-rolled from scratch using `java.net.http.HttpClient` posting custom payloads to a bespoke JAX-RS side-door (`POST /api/a2a/audit`) and returning a hardcoded `AgentCard` map instead of discovering `/.well-known/agent-card.json`. Additionally, the root and BOM POMs pinned 11 manual `org.a2aproject.sdk` `1.4.0.Final` artifacts instead of importing the official `a2a-java-sdk-bom:1.5.0.Final`.

## 2. Decision
1. **Upgrade to `org.a2aproject.sdk:a2a-java-sdk-bom:1.5.0.Final`**: Import `a2a-java-sdk-bom:1.5.0.Final` in `pom.xml` and `ag-ui-bom/pom.xml`, and wire `a2a-java-sdk-http-client-vertx` in the Quarkus showcase `backend` so outbound A2A calls share Quarkus's managed `io.vertx.core.Vertx` pool (`new VertxA2AHttpClient(vertx)`).
2. **Introduce Reusable `AgUiA2aClient` & `A2aDelegationResult` in `ag-ui-a2a` (Java 17)**:
   - Add pure-JDK L0 domain record `org.agui.a2a.domain.A2aDelegationResult` (`taskId, agentName, skillId, status, isError, summary, data`) carrying structured `DataPart` artifacts and summary text with zero framework/SDK imports, paired with `AgUiA2aClient.DelegationExecution` (`agentCard, result, agUiEvents`) holding canonical AG-UI `Event` frames (`StepStartedEvent`, `ActivitySnapshotEvent`, `StepFinishedEvent`).
   - Enhance `AgUiA2aBridge` to extract `DataPart` payloads from both `Task.artifacts()` and `Message.parts()` and surface semantic payload errors (`status=ERROR` / `isError=true`).
   - Add thread-safe `AutoCloseable` `org.agui.a2a.adapters.AgUiA2aClient` wrapping `A2ACardResolver`, SSRF-safe container/proxy endpoint rebinding, bounded `connectTimeout` + `requestTimeout` (across both `resolveAgentCard()` and synchronous/SSE `SendMessage` / `SendStreamingMessage`), and `Client.builder(card)` + `ClientConfig` + `JSONRPCTransport` + `ClientCallContext`.
3. **Eliminate Custom `/api/a2a/audit` Side-Door**: Refactor `A2aGatewayClient` to delegate exclusively via `AgUiA2aClient` + `VertxA2AHttpClient` (including live `A2ACardResolver` discovery for `/api/agent/catalog`) and delete `@POST @Path("/audit")` from `NomadA2aHealthResource`.

## 3. Consequences & Downstream Impact
- 100% end-to-end compliance with the official A2A Java SDK `1.5.0.Final` client & server specification (`GET /.well-known/agent-card.json` + JSON-RPC 2.0 `POST /` with `A2A-Version: 1.0`).
- Reusable Java 17 (`--release 17`, `major version: 61`) `AgUiA2aClient` available to any JVM consumer of `org.agui:ag-ui-a2a` while preserving strict Archlens Clean Architecture concentric rings (`domain <- adapters`).
- Zero custom HTTP client boilerplate or non-standard `/api/a2a/audit` REST endpoints in the showcase.

## 4. Governance Review Decision
- **Decision**: `ACCEPTED`
- **Reviewer**: @fmatar
- **Rationale**: Verified via Dual-Gate unit/mutation suites (`97%–98%` line coverage, `82%–87%` PITest mutation score) and live Docker Compose Tri-Protocol E2E execution on `http://localhost:8080`.
- **Decided At**: 2026-11-11T13:32:00.000Z
