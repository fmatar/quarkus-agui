# SPEC-07: Standard CDI Discovery & GraalVM 25 Reflection Metadata (`ADR-006` & `ADR-007`)

- **Linked Capability**: `CAP-07`
- **Depends On**: `CAP-06`
- **Tickets**: `#8`, `#74`, `#81`
- **Module**: `ag-ui-quarkus`
- **Status**: `AMENDED (ADR-006 & ADR-007)`

## 1. Problem Statement & Technical Scope
Per [`ADR-006`](../adr/ADR-006-framework-agnostic-module-topology-ag-ui-core-ag.md) and [`ADR-007`](../adr/ADR-007-adopt-upstream-ag-ui-sdks-com-ag-ui-community-ag.md), Quarkus build-time `-deployment` extension processors were replaced with standard CDI `META-INF/beans.xml` discovery (`AgUiObjectMapperCustomizer`, `AgUiQuarkusBridge`, `AgUiSessionRegistry`) and an embedded 59-entry `META-INF/native-image/org.agui/ag-ui-quarkus/reflect-config.json` registering all upstream `com.agui.community.core.*` event, message, tool, state patch, interrupt, and Jackson MixIn types for GraalVM / Mandrel native compilation.

## 2. Acceptance Criteria (DoD)
- **AC-01**: Importing `org.agui:ag-ui-quarkus` auto-registers Jackson MixIns and GraalVM reflection hints with zero manual configuration.
- **AC-02**: Verified native runner compilation via `quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25` (`Mandrel-25.0.5.0-Final`).
