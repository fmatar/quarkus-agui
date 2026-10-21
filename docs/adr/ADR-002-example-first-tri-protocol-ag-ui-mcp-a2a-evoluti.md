---
id: ADR-002
title: Example-First Tri-Protocol (AG-UI + MCP + A2A) Evolution on Java 25, Quarkus 3.40.1.1 & pnpm
status: accepted
scope: product
ticket: #2
supersedes: CAP-01, CAP-02, CAP-03, CAP-04, CAP-05, CAP-06, CAP-07, CAP-08
createdAt: 2026-10-21T19:27:29.141Z
---

# ADR-002: Example-First Tri-Protocol (AG-UI + MCP + A2A) Evolution on Java 25, Quarkus 3.40.1.1 & pnpm

- **Status**: `accepted`
- **Blast Radius / Scope**: `product`
- **Triggered By Ticket**: #2
- **Supersedes / Amends**: CAP-01, CAP-02, CAP-03, CAP-04, CAP-05, CAP-06, CAP-07, CAP-08

## 1. Context & Spec Drift Trigger
Pivot from bottom-up library-first sequence to building the complete 4-module Tri-Protocol showcase (mcp-server, a2a-server, backend, frontend) first, evolving AG-UI in-place, and extracting ag-ui-core and quarkus-ag-ui.

## 2. Decision
Adopt 4-Wave Example-First progression using Java 25, Quarkus 3.40.1.1, portable GraalVM 25 Native builds (-Dnative + -Dnative-container), and pnpm with Svelte 5 + @skeletonlabs/skeleton-svelte + @lucide/svelte.

## 3. Consequences & Downstream Impact
Proves real tri-protocol ergonomics in a working system before extracting ag-ui-core and quarkus-ag-ui.

## 4. Governance Review Decision
- **Decision**: `ACCEPTED`
- **Reviewer**: @human-operator
- **Rationale**: Reviewed via SDLC Governance Gate
- **Decided At**: 2026-10-21T19:27:29.195Z
