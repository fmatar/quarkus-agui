---
id: ADR-007
title: Adopt Upstream AG-UI SDKs (com.ag-ui.community & @ag-ui/client) and Orthogonal Java 17 Quarkus Modules
status: accepted
scope: technical
ticket: #81
supersedes: CAP-05, CAP-06, ADR-006
createdAt: 2026-11-10T18:14:39.216Z
---

# ADR-007: Adopt Upstream AG-UI SDKs (com.ag-ui.community & @ag-ui/client) and Orthogonal Java 17 Quarkus Modules

- **Status**: `accepted`
- **Blast Radius / Scope**: `technical`
- **Triggered By Ticket**: #81
- **Supersedes / Amends**: CAP-05, CAP-06, ADR-006

## 1. Context & Spec Drift Trigger
Custom ag-ui-core event types (STATUS, TEXT_DELTA, TOOL_CALL, TOOL_RESULT) and custom JS SSE parsers drifted from the official AG-UI protocol wire specification (https://github.com/ag-ui-protocol/ag-ui), coupled quarkus-langchain4j-core into ag-ui-quarkus, and required Java 25 across reusable libraries.

## 2. Decision
1. Adopt upstream com.ag-ui.community:{java-core,java-server,java-client}:0.1.1 on Java 17 across 4 orthogonal library modules managed by ag-ui-bom: ag-ui-quarkus (RESTEasy Reactive SSE, Jackson MixIns for @ag-ui/core Zod compatibility, reusable AgUiWebSocketHandler, GraalVM reflect-config.json), ag-ui-langchain4j (TokenStream/Agent adapter, RFC 6902 AgUiStatePatch, HITL InterruptOutcome, Tri-Protocol rawEvent provenance), ag-ui-quarkus-langchain4j (Multi<ChatEvent> bridge), and ag-ui-a2a (A2A Task -> Step/ActivitySnapshot bridge). 2. Keep Java 25 + Virtual Threads + GraalVM 25 in examples/quarkus-svelte-skeleton. 3. Dual-license repository as Apache-2.0 OR MIT. 4. Build headless @agui/svelte over @ag-ui/client + @ag-ui/core (Wave 2) and delete ag-ui-core upon showcase cutover (Wave 3).

## 3. Consequences & Downstream Impact
Eliminates protocol drift, enables wire interoperability with CopilotKit and @ag-ui/client ecosystems, decouples ag-ui-quarkus from LangChain4j, lowers reusable library bytecode target to Java 17 while preserving Java 25 in the showcase, and maintains 0 Archlens concentric ring violations.
