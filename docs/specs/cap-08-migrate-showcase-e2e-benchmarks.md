# SPEC-08: Migrate Showcase to `quarkus-ag-ui` Extension & Full E2E + Native Benchmarks

- **Linked Capability**: `CAP-08`
- **Depends On**: `CAP-07`
- **Ticket**: `#9`
- **Modules**: `examples/quarkus-svelte-skeleton` & Root
- **Status**: `ACTIVE`

## 1. Problem Statement & Technical Scope
Completes the evolutionary loop by migrating `examples/quarkus-svelte-skeleton/backend` to consume `org.agui:quarkus-ag-ui` directly, verifying sub-10ms serialization latency overhead (`INV-05`), validating portable GraalVM 25 Native Image builds (`INV-06`), and finalizing the 5-Minute Developer Quickstart in root `README.md`.

## 2. Acceptance Criteria (Gherkin / DoD)
- **AC-01**: `examples/quarkus-svelte-skeleton/backend` uses injected `AgUiBridge`, `AgUiMcpBridge`, and `AgUiA2aBridge` from `org.agui:quarkus-ag-ui`.
- **AC-02**: Performance benchmark test confirms $< 10\text{ms}$ serialization latency for 1,000 AG-UI events.
- **AC-03**: `node .agent/scripts/sdlc-engine.mjs gate --strict` passes with zero violations and verified Merkle audit chain.
