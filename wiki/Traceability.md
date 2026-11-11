# 🎯 Traceability, Governance & Operations (`Traceability`)

> **How-To & Operations Guide**  
> Connects constitutional invariants (`PRODUCT.md`), functional specs (`docs/specs/`), Architecture Decision Records (`docs/adr/`), Kanban tracking (`docs/KANBAN.md`), and cryptographic gate receipts (`docs/AUDIT_CHAIN.jsonl`).

---

## 1. Constitutional Invariants (`INV-01` – `INV-08`)

| Invariant | Enforcement Mechanism | Verification Command / Artifact |
| :--- | :--- | :--- |
| **`INV-01`** | Zero direct commits/merges/pushes to `main` or `develop`; 4-Layer Git Guard | `.agent/scripts/lib/git-guard.mjs`, `docs/AUDIT_CHAIN.jsonl` |
| **`INV-02`** | Dual-Gate TDD (`>= 85%` line/branch coverage & `>= 80%` PITest/JS mutation score) | `./mvnw test jacoco:report`, `pitest-maven:mutationCoverage` |
| **`INV-03`** | Concentric Clean Architecture (`adapters -> application -> domain`) | `.archlens/policy.json`, `node .agent/scripts/sdlc-engine.mjs archlens diff` |
| **`INV-04`** | Upstream `com.ag-ui.community:0.1.1` fidelity & `--release 17` bytecode (`major 61`) | `ag-ui-bom/pom.xml`, `docs/adr/ADR-007-adopt-upstream-ag-ui-sdks-com-ag-ui-community-ag.md` |
| **`INV-05`** | `< 10ms` p99 Jackson MixIn serialization & SSE/WebSocket framing overhead | `AgUiQuarkusAdaptersTest` |
| **`INV-06`** | Portable GraalVM 25 / Mandrel native binary (`< 50MB` RSS, 57 reflected types) | `./mvnw verify -Dnative`, `./mvnw verify -Dnative-container` |
| **`INV-07`** | Zero-boilerplate CDI discovery (`META-INF/beans.xml`), orthogonal deps, `Apache-2.0 OR MIT` | `LICENSE`, `LICENSE-MIT`, `docs/adr/ADR-006-framework-agnostic-module-topology-ag-ui-core-ag.md` |
| **`INV-08`** | Multi-release Java 17 (libs) / Java 25 (showcase) + `pnpm` + Svelte 5 Runes | `pom.xml`, `pnpm-workspace.yaml` |

---

## 2. Capability-to-Specification Matrix (`CAP-01` – `CAP-08`)

Every product capability in `PRODUCT.md` links to a formal specification in `docs/specs/` alongside `docs/specs/PRD.md`, `docs/specs/SRS.md`, and `docs/specs/C4_ARCHITECTURE.md`:

| ID | Capability | Specification File | Status |
| :--- | :--- | :--- | :--- |
| **CAP-01** | Quarkus MCP Server (`:8081`) & Quarkus A2A Server (`:8082`) | `docs/specs/cap-01-mcp-a2a-servers.md` | `AMENDED (ADR-009)` |
| **CAP-02** | Quarkus Orchestrator (`:8080`) & SvelteKit 5 + `pnpm` 3-Pane Baseline | `docs/specs/cap-02-orchestrator-svelte-baseline.md` | `IMPLEMENTED (ADR-002)` |
| **CAP-03** | AG-UI Protocol Engine (Streaming, MCP/A2A Provenance, State & HITL) | `docs/specs/cap-03-inplace-agui-backend.md` | `AMENDED (ADR-008)` |
| **CAP-04** | 3-Pane SvelteKit 5 + `@agui/svelte` + `ACTIVITY_SNAPSHOT` Workbench | `docs/specs/cap-04-svelte-agui-workbench.md` | `AMENDED (ADR-008)` |
| **CAP-05** | Upstream `com.ag-ui.community` Java 17 Adoption (Superseding `ag-ui-core`) | `docs/specs/cap-05-extract-agui-core.md` | `AMENDED (ADR-007)` |
| **CAP-06** | Orthogonal Bridges (`ag-ui-quarkus`, `ag-ui-langchain4j`, `ag-ui-quarkus-langchain4j`, `ag-ui-a2a`) | `docs/specs/cap-06-extract-quarkus-runtime.md` | `AMENDED (ADR-009)` |
| **CAP-07** | Standard CDI Discovery & 57-Type GraalVM 25 Reflection Metadata | `docs/specs/cap-07-extract-quarkus-deployment-it.md` | `AMENDED (ADR-006)` |
| **CAP-08** | Nomad Showcase Cutover, `local-quarkus-agui` CI & Native Verification | `docs/specs/cap-08-migrate-showcase-e2e-benchmarks.md` | `AMENDED (ADR-008)` |

---

## 3. Architecture Decision Records (`ADR-001` – `ADR-009`)

Causal lineage is rendered automatically in `docs/DECISION_MAP.md` (`node .agent/scripts/sdlc-engine.mjs decision-graph sync`):

- **`ADR-001`** (`docs/adr/ADR-001-multi-module-maven-sveltekit-clean-architecture-.md`): Multi-Module Maven & SvelteKit Clean Architecture (#1)
- **`ADR-002`** (`docs/adr/ADR-002-example-first-tri-protocol-ag-ui-mcp-a2a-evoluti.md`): Example-First Tri-Protocol (`AG-UI + MCP + A2A`) Evolution on Java 25, Quarkus `3.40.1.1` & `pnpm` (#2)
- **`ADR-003`** (`docs/adr/ADR-003-multi-extension-bom-and-concentric-transport-arc.md`): Multi-Extension BOM and Concentric Transport Architecture (#30)
- **`ADR-004`** (`docs/adr/ADR-004-nomad-concierge-and-svelte5-agui-component-kit.md`): Nomad Cultural Concierge Showcase & Modular Svelte 5 AG-UI Component Kit (#32)
- **`ADR-005`** (`docs/adr/ADR-005-quarkus-langchain4j-mcp-and-smallrye-health.md`): Quarkus LangChain4j Reactive AG-UI Bridge, `@McpToolBox` & SmallRye Health + REST Countries v5 (#68)
- **`ADR-006`** (`docs/adr/ADR-006-framework-agnostic-module-topology-ag-ui-core-ag.md`): Framework-Agnostic Module Topology and Removal of Quarkus `-deployment` Processors (#74)
- **`ADR-007`** (`docs/adr/ADR-007-adopt-upstream-ag-ui-sdks-com-ag-ui-community-ag.md`): Adopt Upstream AG-UI SDKs (`com.ag-ui.community` & `@ag-ui/client`) and Orthogonal Java 17 Modules (#81)
- **`ADR-008`** (`docs/adr/ADR-008-canonical-ag-ui-activity-snapshot-surface-kit-co.md`): Canonical AG-UI `ACTIVITY_SNAPSHOT` Surface Kit, Complete Trip Dossier Report & `local-quarkus-agui` CI (#93)
- **`ADR-009`** (`docs/adr/ADR-009-adopt-official-a2a-java-sdk-1-5-0-final-client-a.md`): Adopt Official A2A Java SDK `1.5.0.Final` Client (`A2ACardResolver`, `ClientBuilder` & `VertxA2AHttpClient`) and Remove Custom HTTP Audit Endpoint (#100)

---

## 4. Operational Runbooks

### 4.1 Kanban & Issue Synchronization (`GitHub` / `Linear` / `Jira`)

```bash
# Provision GitHub labels, milestones, and Project v2 fields
.agent/scripts/project-setup.sh

# Synchronize living docs/KANBAN.md from GitHub Issues & PRs
.agent/scripts/kanban-sync.sh

# Create or transition tickets via unified PM adapter
node .agent/scripts/sdlc-engine.mjs ticket templates
node .agent/scripts/sdlc-engine.mjs ticket sync
```

Issue and PR templates are maintained in `.github/ISSUE_TEMPLATE/` and `.github/pull_request_template.md`, while CI verification runs via `.github/workflows/sdlc-quality-gate.yml`.

### 4.2 Cryptographic Audit Chain, Proofs & Documentation Verification

- **Merkle Audit Ledger**: `docs/AUDIT_CHAIN.jsonl` records every ADR decision, gate verification, and release receipt; `.agent/gate-receipts.jsonl` stores local session gate receipts.
- **Delivery Regression Proofs**: `docs/proofs/` (`docs/proofs/proof-agui-v0-1-0-1794349292000.md`, `docs/proofs/proof-agui-v0-2-0-1794368924117.md`) hashes verified endpoints and UI components prior to each SemVer tag.
- **Zero-Rot Documentation Gate**:
  ```bash
  node .agent/scripts/sdlc-engine.mjs readme audit --strict
  node .agent/scripts/sdlc-engine.mjs wiki sync
  node .agent/scripts/sdlc-engine.mjs wiki audit --strict
  node .agent/scripts/sdlc-engine.mjs arch trace --reason "Synchronized wiki architecture diagrams with source AST"
  ```
