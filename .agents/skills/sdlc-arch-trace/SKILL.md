---
name: sdlc-arch-trace
description: Continuous architecture mutation tracer, Mermaid diagram diff auditor, and git branch protection enforcer. Guarantees 100% architectural traceability in docs/ARCH_TRACE.md and blocks direct pushes to develop or main.
version: 0.0.1
license: Apache 2.0
allowed-tools: run_command, view_file, write_to_file, replace_file_content
---

# Architectural Traceability & Mutation Auditor (`sdlc-arch-trace`)

Use this skill to continuously audit system architecture evolution, track Mermaid diagram diffs, enforce Clean Architecture layer integrity, and ensure zero unmonitored architectural drift.

---

## 🏛️ Hard Framework Invariants

1. **Branch Protection Guard**:
   Under **no circumstances** may any agent or developer push directly to `develop` or `main`.
   All work must be conducted on dedicated feature or architecture branches (`feature/issue-#<id>-<title>` or `arch/<name>`) and integrated via validated Pull Requests.

2. **Continuous Architectural Traceability**:
   Every time an architectural component, system boundary, Mermaid diagram, or dependency port is modified, an audit entry must be recorded in:
   - **`docs/ARCH_TRACE.md`**: Human-readable Markdown audit table.
   - **`.agent/arch-trace.jsonl`**: Machine-readable event-sourced log.

---

## ⚡ CLI Commands

```bash
# Record an architectural mutation trace event:
node .agent/scripts/sdlc-engine.mjs arch trace --reason "<explanation of architectural change>"

# Audit git diff for Clean Architecture concentric ring layer inversions:
node .agent/scripts/sdlc-engine.mjs arch diff

# Verify Spec-Drift Sentinel & synchronize Causal Decision Lineage (docs/DECISION_MAP.md):
node .agent/scripts/sdlc-engine.mjs drift check
node .agent/scripts/sdlc-engine.mjs decision-graph sync

# Install 4-Layer Git pre-commit, pre-merge-commit, and pre-push hooks enforcing branch & session locks:
node .agent/scripts/sdlc-engine.mjs hooks install
```

---

## 🔍 How the Diff & Trace Engine Operates

1. **Mermaid AST Extraction**:
   - Parses system Mermaid flowcharts and class diagrams across markdown specs, `docs/architecture/*.mmd`, and `SKILL.md` workflows.
   - Extracts nodes, connections/arrows, labels, and subgraph boundaries.

2. **Git Revision Graph Comparison**:
   - Compares the active working tree against `HEAD~1` (or baseline branch).
   - Computes:
     - Nodes added / removed
     - Dependency edges connected / severed
     - Concentric layer crossings

3. **Archlens Clean Architecture Verification**:
   - Verifies Robert Martin's Dependency Rule (dependencies must point inward).
   - Flags any inner-ring entity importing outer-ring web/db/controller packages.

4. **Audit Append**:
   - Writes timestamp, ticket ID, branch, commit SHA, diagram summaries, and Archlens pass/fail status into `docs/ARCH_TRACE.md` and `.agent/arch-trace.jsonl`.
