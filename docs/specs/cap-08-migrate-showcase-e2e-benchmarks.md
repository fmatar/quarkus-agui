# SPEC-08: Nomad Showcase Cutover, E2E Wire Validation & `v0.1.0` Release

- **Linked Capability**: `CAP-08`
- **Depends On**: `CAP-07`
- **Tickets**: `#9`, `#85`, `#87`
- **Modules**: `examples/quarkus-svelte-skeleton` & Root
- **Status**: `IMPLEMENTED (ADR-007)`

## 1. Problem Statement & Technical Scope
Completes the `ADR-007` cutover across `examples/quarkus-svelte-skeleton` (`mcp-server`, `a2a-server`, `backend`, `frontend`), verifies live 3-service wire compatibility against `@ag-ui/client` (`HttpAgent` & `WebSocketAgent`) Zod schemas, validates portable GraalVM / Mandrel 25 native compilation, synchronizes the 5-Minute Developer Quickstart in `README.md`, and releases `v0.1.0`.

## 2. Acceptance Criteria (DoD)
- **AC-01**: Live 3-service E2E stack boots in `< 2s` on Java 25 and passes SSE + WebSocket + RFC 6902 `STATE_DELTA` `@ag-ui/client` schema validation.
- **AC-02**: All 10 Maven modules and 2 workspace npm packages pass Dual-Gate coverage & mutation checks with verified SHA-256 Merkle Gate Receipts in `docs/AUDIT_CHAIN.jsonl`.
