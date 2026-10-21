# SPEC-04: Quarkus AG-UI Deployment Processor, GraalVM Reflection & Dev UI Inspector (`quarkus-ag-ui-deployment`)

- **Linked Capability**: `CAP-04`
- **Depends On**: `CAP-01`, `CAP-02`
- **Module**: `quarkus-extension/deployment` (`src/main/java/org/agui/quarkus/deployment/`)
- **Clean Architecture Layer**: Level 3 (Build-Time Deployment Infrastructure)
- **Status**: `ACTIVE`

## 1. Problem Statement & Technical Scope
To achieve sub-50MB GraalVM native image RSS (`INV-06`) and zero-configuration developer ergonomics (`INV-07`), the Quarkus deployment module must register all `ag-ui-core` records for native reflection at build time, register CDI beans for `AgUiBridge`, and provide a **Quarkus Dev UI Inspector Card** (`/q/devui`).

## 2. Build Steps (`AgUiProcessor`)
1. **`FeatureBuildItem`**: Registers feature `"ag-ui"`.
2. **`ReflectiveClassBuildItem`**: Registers `AgUiEvent`, `RunStartedPayload`, `TextDeltaPayload`, `ToolCallPayload`, `ToolResultPayload`, `StatusPayload`, and `RunFinishedPayload` with methods, fields, and constructors enabled for Jackson native serialization.
3. **`AdditionalBeanBuildItem`**: Registers runtime `AgUiBridge` and SSE formatters as CDI beans.
4. **Dev UI Card (`CardPageBuildItem` / JSON-RPC Service)**: Injects the **AG-UI Inspector** card into Quarkus Dev UI allowing developers to inspect supported event types, mock events, and test stream formatting during development.

## 3. Acceptance Criteria (Gherkin / DoD)
- **AC-01**: `AgUiProcessor` produces `FeatureBuildItem("ag-ui")` and `ReflectiveClassBuildItem` covering all 7 core AG-UI record classes.
- **AC-02**: Dev UI metadata exposes the AG-UI Inspector card and event catalog.
- **AC-03**: Dual-Gate unit tests verify build item registration with $\ge 85\%$ coverage and $\ge 80\%$ mutation kill score.
