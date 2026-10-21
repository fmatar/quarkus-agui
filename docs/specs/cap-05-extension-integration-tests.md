# SPEC-05: Quarkus AG-UI Extension JVM & GraalVM Native Integration Test Suite (`quarkus-ag-ui-integration-tests`)

- **Linked Capability**: `CAP-05`
- **Depends On**: `CAP-03`, `CAP-04`
- **Module**: `quarkus-extension/integration-tests`
- **Status**: `ACTIVE`

## 1. Problem Statement & Technical Scope
Validates the packaged `quarkus-ag-ui` extension inside a real Quarkus runtime (`@QuarkusTest`) and GraalVM native image (`@QuarkusIntegrationTest`), ensuring zero-configuration SSE streaming, Jackson serialization, and reflection metadata completeness.

## 2. Acceptance Criteria (Gherkin / DoD)
- **AC-01**: Test endpoint streams a full lifecycle (`RUN_STARTED`, `STATUS`, `TEXT_DELTA`, `TOOL_CALL`, `TOOL_RESULT`, `RUN_FINISHED`) over `text/event-stream` on random test port `0`.
- **AC-02**: Ephemeral port isolation (`quarkus.http.test-port=0`) ensures zero port collisions in parallel test runs.
- **AC-03**: Native reflection metadata test confirms all payload records serialize without reflection errors.
