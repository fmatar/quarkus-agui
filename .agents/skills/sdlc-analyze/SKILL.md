---
name: sdlc-analyze
description: SDLC compliance auditor that evaluates repository health, documentation presence, Dual-Gate test coverage, mutation testing defense, static analysis, and a11y across polyglot stacks.
version: 0.0.1
license: Apache 2.0
allowed-tools: run_command, view_file
---

# SDLC Compliance Auditor (`sdlc-analyze`)

Use this skill to audit a repository for adherence to enterprise software quality, Robert C. Martin craftsmanship, and architecture standards. It evaluates any polyglot project (Java, Node/TypeScript, Python, Go, Rust), producing an informational dashboard or blocking execution in strict mode.

---

## 📋 Core Responsibilities

1. **Documentation & Showcase Audit**: Verifies the presence of the root `README.md` Developer Showcase (5-minute quickstart, BYOA contract), PRDs, SRSs, C4 Architecture, and ADRs in `docs/specs/` and `wiki/`.
2. **Universal Quality Gate Audit**: Runs `node .agent/scripts/sdlc-engine.mjs gate` to audit linting, static analysis, build compilation, and Archlens Clean Architecture policy.
3. **Dual-Gate Verification**: Executes `node .agent/scripts/sdlc-engine.mjs coverage` ($\ge 85\%$) and `node .agent/scripts/sdlc-engine.mjs mutation` ($\ge 80\%$).
4. **Report Generation**: Outputs `docs/SDLC_COMPLIANCE_REPORT.md` as a health score artifact.
5. **Strict Enforcement**: When invoked with `--strict`, the agent terminates with an error if compliance thresholds are not met, preventing faulty merges.

---

## 🛠️ Execution Workflow

### 1. Identify Project Structure & Auto-Detect Stack
- Run universal stack probe: `node .agent/scripts/sdlc-engine.mjs detect`
- Load `.agent/sdlc-config.json` to identify active tiers, test commands, and quality thresholds.

### 2. Documentation & Showcase Audit
- Check root `README.md` for a complete **5-Minute Developer Quickstart** (< 5 min time-to-first-run, BYOA workflow, runnable examples).
- Check `docs/specs/` and `docs/templates/` for required architecture documents.
- Verify `CHANGELOG.md` exists and tracks `[Unreleased]`.
- Verify `docs/KANBAN.md` living board exists and reflects active issues.

### 3. Universal Quality Gate & Static Analysis Audit
Execute the automated gate for all configured modules:
```bash
node .agent/scripts/sdlc-engine.mjs gate
```
- Checks formatting and linting across all active modules.
- Runs compiler/typechecker without errors.
- Runs Archlens Clean Architecture governance check if configured.

### 4. Dual-Gate Test & Mutation Audit
```bash
node .agent/scripts/sdlc-engine.mjs coverage
node .agent/scripts/sdlc-engine.mjs mutation
```
- Verifies $\ge 85\%$ diff coverage.
- Verifies $\ge 80\%$ mutation kill score on modified files.

### 5. Generate Report & Enforce
- Compile findings into a Markdown artifact `SDLC_COMPLIANCE_REPORT.md`.
- If running under strict mode (e.g. `sdlc-quality-gate`), halt the pipeline if any critical checks fail.

---

## ⏭️ Next Recommended Step

If the compliance score is below thresholds, invoke **`sdlc-flow`** or **`sdlc-test-coverage`** to remediate missing tests, surviving mutants, or static analysis issues.
