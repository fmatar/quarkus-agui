---
id: ADR-001
title: Multi-Module Maven & SvelteKit Clean Architecture for AG-UI Protocol
status: accepted
scope: technical
ticket: #1
supersedes: CAP-01, CAP-03, CAP-04, CAP-07
createdAt: 2026-10-21T18:00:35.801Z
---

# ADR-001: Multi-Module Maven & SvelteKit Clean Architecture for AG-UI Protocol

- **Status**: `accepted`
- **Blast Radius / Scope**: `technical`
- **Triggered By Ticket**: #1
- **Supersedes / Amends**: CAP-01, CAP-03, CAP-04, CAP-07

## 1. Context & Spec Drift Trigger
Establish multi-module Maven reactor (ag-ui-core, quarkus-extension runtime/deployment/integration-tests) and SvelteKit + Skeleton UI reference client.

## 2. Decision
Adopt zero-dependency Java 17+ ag-ui-core (domain/application/adapters concentric rings), Quarkus extension runtime/deployment modules, and SvelteKit streaming client.

## 3. Consequences & Downstream Impact
Enables sub-10ms core serialization, <50MB GraalVM native image RSS, and strict Archlens Clean Architecture layer governance.
