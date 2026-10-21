# SPEC-07: Extract `quarkus-ag-ui-deployment`, GraalVM 25 Reflection, Dev UI & Integration Tests

- **Linked Capability**: `CAP-07`
- **Depends On**: `CAP-06`
- **Ticket**: `#8`
- **Modules**: `quarkus-extension/deployment` & `quarkus-extension/integration-tests`
- **Status**: `ACTIVE`

## 1. Problem Statement & Technical Scope
Implements the Quarkus build-time deployment processor (`AgUiProcessor`) in `quarkus-extension/deployment`:
- Registers `FeatureBuildItem("ag-ui")`.
- Registers `ReflectiveClassBuildItem` for all `ag-ui-core` records and DTOs to guarantee GraalVM 25 Native Image compilation (`< 50MB` RSS).
- Registers `AdditionalBeanBuildItem` for `AgUiBridge`, `AgUiMcpBridge`, and `AgUiA2aBridge`.
- Contributes the **Quarkus Dev UI AG-UI Inspector Card** (`/q/devui`).
- Validates the extension in `quarkus-extension/integration-tests` (`@QuarkusTest` & `@QuarkusIntegrationTest` with `-Dnative` / `-Dnative-container`).

## 2. Acceptance Criteria (Gherkin / DoD)
- **AC-01**: `AgUiProcessor` registers all core record classes for GraalVM native reflection and exposes Dev UI metadata.
- **AC-02**: `quarkus-extension/integration-tests` passes on ephemeral test port `0`.
- **AC-03**: Dual-Gate verification passes with $\ge 85\%$ coverage and $\ge 80\%$ PITest mutation kill score.
