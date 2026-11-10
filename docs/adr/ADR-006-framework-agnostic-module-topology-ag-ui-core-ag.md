---
id: ADR-006
title: Framework-Agnostic Module Topology (ag-ui-core, ag-ui-a2a, ag-ui-langchain4j, ag-ui-quarkus) and Removal of Quarkus Deployment Processors
status: accepted
scope: technical
ticket: #74
supersedes: CAP-06, CAP-07, ADR-003
createdAt: 2026-11-10T05:30:55.588Z
---

# ADR-006: Framework-Agnostic Module Topology (ag-ui-core, ag-ui-a2a, ag-ui-langchain4j, ag-ui-quarkus) and Removal of Quarkus Deployment Processors

- **Status**: `accepted`
- **Blast Radius / Scope**: `technical`
- **Triggered By Ticket**: #74
- **Supersedes / Amends**: CAP-06, CAP-07, ADR-003

## 1. Context & Spec Drift Trigger
The library previously trapped framework-agnostic session management, SSE/WebSocket transport abstractions, MCP/A2A bridges, and LangChain4j tool/event mapping inside Quarkus extension modules, and maintained 5 -deployment build-time modules that only registered beans and reflective classes achievable via standard META-INF/beans.xml, Jandex, and GraalVM reflect-config.json.

## 2. Decision
1. Drop all Quarkus -deployment modules and replace them with standard Jandex-indexed JARs containing META-INF/beans.xml and META-INF/native-image reflect-config.json. 2. Factor modules under ag-ui-bom into ag-ui-core (org.agui.core.*: domain payloads, formatters, Flow adapters, transport ports, session registry, SSE/WebSocket sessions, MCP bridge, token builders), ag-ui-a2a (org.agui.a2a.*: A2A SDK bridge), ag-ui-langchain4j (org.agui.langchain4j.*: pure upstream dev.langchain4j TokenStream and lifecycle event adapter), and ag-ui-quarkus (org.agui.quarkus.*: thin Quarkus adapter for Mutiny Multi, Quarkiverse Multi<ChatEvent>, and Quarkus WebSockets Next). 3. Keep jakarta.enterprise.cdi-api as provided scope across core and protocol modules so classes work with new in plain Java/Spring and @Inject in CDI/Quarkus.

## 3. Consequences & Downstream Impact
Any Java framework (Spring Boot, Quarkus, Helidon, Micronaut, Jakarta EE, or plain Java) can consume ag-ui-core, ag-ui-a2a, and ag-ui-langchain4j without Quarkus build-time dependencies, while Quarkus applications enjoy zero-boilerplate CDI injection and native image reflection via ag-ui-quarkus.
