---
id: ADR-003
title: Multi-Extension BOM and Concentric Transport Architecture (Core, SSE, WebSockets)
status: accepted
scope: technical
ticket: #30
supersedes: CAP-06, CAP-07
createdAt: 2026-11-08T05:51:47.280Z
---

# ADR-003: Multi-Extension BOM and Concentric Transport Architecture (Core, SSE, WebSockets)

- **Status**: `accepted`
- **Blast Radius / Scope**: `technical`
- **Triggered By Ticket**: #30
- **Supersedes / Amends**: CAP-06, CAP-07

## 1. Context & Spec Drift Trigger
To support multiple streaming transports (HTTP Server-Sent Events and bidirectional WebSockets) while allowing downstream Quarkus applications to import a single unified Bill of Materials (`quarkus-ag-ui-bom`) or cherry-pick transport modules, `quarkus-extension` needed to evolve from a monolithic runtime/deployment pair into modular sub-extensions governed by concentric Clean Architecture rings.

## 2. Decision
1. Introduce `org.agui:quarkus-ag-ui-bom` (`quarkus-ag-ui-bom/pom.xml`) managing versions for all `ag-ui-core` and `quarkus-ag-ui-*` runtime and deployment artifacts.
2. Split `quarkus-extension` into three concentric sub-extensions aggregated by the `quarkus-ag-ui` starter:
   - `quarkus-ag-ui-core`: Defines `AgUiTransportType` (`domain`), `AgUiTransportBridge`, `AgUiTransportSession`, and `AgUiSessionRegistry` (`application.port`), plus `AgUiBridge`, `AgUiMcpBridge`, and `AgUiA2aBridge` (`adapters`).
   - `quarkus-ag-ui-sse`: Implements `AgUiSseBridge` and `AgUiSseTransportSession` backed by SmallRye Mutiny `Multi<String>`.
   - `quarkus-ag-ui-websockets`: Implements `AgUiWebSocketEndpoint` (`/ag-ui/ws`) and `AgUiWebSocketTransportSession` on Quarkus WebSockets Next.

## 3. Consequences & Downstream Impact
- Existing consumers of `org.agui:quarkus-ag-ui` continue to work with zero configuration changes because the starter transitively aggregates `core`, `sse`, and `websockets`.
- Transport-specific dependencies are isolated to outer adapter modules, preserving zero outward dependency leaks in `domain` and `application.port` rings.

