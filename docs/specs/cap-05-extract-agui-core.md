# SPEC-05: Adopt Upstream `com.ag-ui.community` Java 17 Core SDK (`ADR-007`)

- **Linked Capability**: `CAP-05`
- **Depends On**: `CAP-03`, `CAP-04`
- **Tickets**: `#6`, `#81`, `#85`
- **Module**: `ag-ui-bom` (superseding deleted `ag-ui-core`)
- **Status**: `AMENDED (ADR-007)`

## 1. Problem Statement & Technical Scope
Per [`ADR-007`](../adr/ADR-007-adopt-upstream-ag-ui-sdks-com-ag-ui-community-ag.md), custom `ag-ui-core` event POJOs and bespoke SSE formatters were deleted and replaced by upstream `com.ag-ui.community:{java-core,java-server,java-client}:0.1.1` managed in `ag-ui-bom` on a **Java 17 (`--release 17`, `major version: 61`)** baseline, dual-licensed under `Apache-2.0 OR MIT`.

## 2. Acceptance Criteria (DoD)
- **AC-01**: Zero duplicate AG-UI event POJOs remain in `org.agui` (`ag-ui-core` directory deleted).
- **AC-02**: `ag-ui-bom` manages `com.ag-ui.community:{java-core,java-server,java-client}:0.1.1` and all 4 `org.agui` Java 17 adapter modules.
