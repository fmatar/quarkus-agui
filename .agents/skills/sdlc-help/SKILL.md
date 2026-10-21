---
name: sdlc-help
description: Interactive user and agent guide for the Agentic SDLC ecosystem. Provides command recipes, workflow diagrams, decision matrices, and tool diagnostics adhering to Robert C. Martin craftsmanship.
version: 0.0.1
license: Apache 2.0
allowed-tools: run_command, view_file
---

# SDLC Guide, Recipes & Tool Diagnostics (`sdlc-help`)

Use this skill when you need guidance on navigating the Agentic SDLC ecosystem, selecting the right skill for a task, or finding copy-paste prompt recipes.

---

## 🧭 SDLC Skill Decision Matrix

| What You Want to Accomplish | Recommended Skill | Trigger Phrase / Command |
| :--- | :--- | :--- |
| **Execute full issue-to-PR automated lifecycle** | **`sdlc-flow`** | *"Run sdlc-flow for issue #<id>"* |
| **Trace architecture & block direct push to develop/main** | **`sdlc-arch-trace`** | *"Audit architectural trace and branch protection"* |
| **Draft RFC, acceptance criteria, or GitHub issue** | **`sdlc-feature-spec`** | *"Draft a spec / issue for [feature]"* |
| **Implement code via Robert Martin TDD & TPP** | **`sdlc-tdd-implement`** | *"Implement [component/service] with TDD"* |
| **Enforce Dual-Gate (85% coverage + 80% mutation score)** | **`sdlc-test-coverage`** | *"Run tests and enforce mutation defense"* |
| **Audit PR diff, detect test smells and mock abuse** | **`sdlc-peer-review`** | *"Run subagent peer review on PR"* |
| **Manage GitHub Projects v2 & living KANBAN board** | **`sdlc-kanban`** | *"Sync Kanban board and update issue status"* |
| **Update root README quickstart, specs, CHANGELOG** | **`sdlc-docs-wiki`** | *"Sync showcase docs and changelog"* |
| **Pre-commit audit, formatting, type-check, a11y, gates** | **`sdlc-quality-gate`** | *"Run quality gate checks"* |
| **Bump SemVer, tag release, Docker build** | **`sdlc-release-changelog`** | *"Cut a release vX.Y.Z"* |
| **Audit repository compliance, tests, and mutation score** | **`sdlc-analyze`** | *"Audit repository compliance"* |
| **Scaffold new full-stack project or install skills** | **`sdlc-initialize`** | *"Initialize project with Agentic SDLC"* |
| **Get interactive guidance and prompt recipes** | **`sdlc-help`** | *"Help me navigate SDLC skills"* |

---

## ⚡ 8-Stage Lifecycle Architecture

```
1. Intake & Contract Specification (sdlc-feature-spec & status:ready)
   │
   ▼
2. Branch & Kanban Automation (feature/issue-#<id>-<title> & status:in-progress)
   │
   ▼
3. Clean-Room TDD Implementation (sdlc-tdd-implement: Robert Martin's 3 Laws & TPP)
   │
   ▼
4. Dual-Gate Test & Mutation Enforcer (sdlc-test-coverage: >= 85% Diff Coverage, >= 80% Mutation Score)
   │
   ▼
5. Documentation, Showcase & Changelog Sync (sdlc-docs-wiki)
   │
   ▼
6. Pull Request Creation (create_pull_request & status:in-review)
   │
   ▼
7. Fork-Join Parallel Review & Quality Gate (invoke_subagent: sdlc-peer-review & sdlc-quality-gate)
   │
   ▼
8. Quality Gate Passed, Release Lock & Done (sdlc-release-changelog & status:done)
```

---

## 📋 Copy-Paste Prompt Recipes

### 1. The Autonomous Issue-to-PR Executor Recipe
> *"Execute `sdlc-flow` on issue #<id>. Fetch the issue, create the feature branch, implement the feature using Robert Martin's TDD, enforce Dual-Gate test coverage ($\ge 85\%$) and mutation score ($\ge 80\%$), sync README showcase, CHANGELOG.md and specs, push and open a PR, and trigger parallel peer review and quality gate subagents."*

### 2. The Project Kanban & Board Manager Recipe
> *"Run `sdlc-kanban` to configure GitHub Projects v2 views, transition issue #<id> to `status:in-progress`, and refresh `docs/KANBAN.md`."*

### 3. The Dual-Gate Coverage & Mutation Enforcer Recipe
> *"Run `sdlc-test-coverage` against the current diff. Ensure modified line and branch coverage reaches $\ge 85\%$, run PITest/Stryker mutation testing to verify an $\ge 80\%$ mutation kill score, and kill any surviving mutants."*

### 4. The Subagent PR Peer Reviewer Recipe
> *"Spawn a peer review subagent using `sdlc-peer-review` to inspect the PR diff, check reactive patterns and mutation defense, detect Robert Martin test smells (no trivial getter tests, no mock abuse), and post review comments."*

### 5. The Quality Gatekeeper Recipe
> *"Run `sdlc-quality-gate` to verify formatting, static typing, build validation, Dual-Gate test & mutation defense, Archlens Clean Architecture governance, and WCAG accessibility before creating the release."*

### 6. The Repository Compliance Auditor Recipe
> *"Run `sdlc-analyze --strict` to audit repo health, Dual-Gate test coverage thresholds, and documentation completeness."*

### 7. The Universal Project Bootstrapper & Onboarding Recipe
> *"Run `sdlc-initialize` to auto-detect the repository stack, optionally configure Archlens Clean Architecture governance and dynamic workbench, select the Project Management provider (GitHub, Jira, Linear, or Local), and wire SDLC workflows."*

### 8. The Framework & Governance Synchronizer Recipe
> *"Run `node .agent/scripts/sdlc-engine.mjs update` to synchronize the framework skills, refresh .agent/sdlc-config.json, and update .archlens/policy.json."*

### 9. The Ad-Hoc Fast-Path & Parallel Fan-Out Recipe
> *"Execute an ad-hoc test implementation on the active branch: resolve targets for `backend/UserService.java` and `frontend/UserView.svelte`, and dispatch parallel subagents without re-triggering the full intake lifecycle."*

### 10. The Session State & Concurrency Lock Recipe
> *"Run `node .agent/scripts/sdlc-engine.mjs session status` to inspect the active in-flight ticket, or `node .agent/scripts/sdlc-engine.mjs session release` to safely clear a completed or cancelled workflow lock."*

### 11. The Cryptographic Merkle Audit Verifier Recipe
> *"Run `node .agent/scripts/sdlc-engine.mjs audit verify` to cryptographically validate the SHA-256 Merkle chain in `docs/AUDIT_CHAIN.jsonl` and guarantee zero tampering across all delivery checkpoints."*

### 12. The Delivery Regression Proof Generator Recipe
> *"Run `node .agent/scripts/sdlc-engine.mjs proof generate --name [TargetName]` to capture visual DOM snapshots and verified API response digests into `docs/proofs/` and sign the delivery into the Merkle audit chain."*

### 13. The Epic Initiative DAG Deconstructor Recipe
> *"Run `node .agent/scripts/sdlc-engine.mjs deconstruct "[Epic Markdown]" --title "[Epic Name]" --provision` to break down large initiatives into topologically-ordered DAG tickets with dependency arrows and live Mermaid graph visualization."*

### 14. The Canary Health Watchdog & Rollback Recipe
> *"Run `node .agent/scripts/sdlc-engine.mjs watchdog monitor --url [HealthURL] --threshold [0.05] --rollback "[RollbackCommand]"` to sample deployment health and execute an automatic emergency rollback upon error rate breach."*

### 15. The Multi-Branch Protection Enforcer Recipe
> *"Run `node .agent/scripts/sdlc-engine.mjs protect main develop` to install local Git pre-push guards and configure remote GitHub branch protection requiring pull request reviews and forbidding direct pushes."*

---

## 🛠️ Tooling & Diagnostics Checklist

1. **Universal SDLC Engine Diagnostics (Polyglot / Multi-Module / Session)**:
   ```bash
   node .agent/scripts/sdlc-engine.mjs env                # Verify environment variables, PM provider & Archlens
   node .agent/scripts/sdlc-engine.mjs detect             # Probe stack & preview configuration
   node .agent/scripts/sdlc-engine.mjs doctor [--fix]     # Run healthcheck & auto-heal missing framework assets
   node .agent/scripts/sdlc-engine.mjs serve [--port n]   # Launch visual Mission Control dashboard webview
   node .agent/scripts/sdlc-engine.mjs archlens check     # Audit Clean Architecture concentric layer policy
   node .agent/scripts/sdlc-engine.mjs pm status          # Check active PM provider (GitHub/Jira/Linear/Local)
   node .agent/scripts/sdlc-engine.mjs update             # Sync framework & Archlens policies
   node .agent/scripts/sdlc-engine.mjs session status     # Inspect active workflow lock & stage
   node .agent/scripts/sdlc-engine.mjs session release    # Release or reset session lock
   node .agent/scripts/sdlc-engine.mjs resolve-targets .. # Analyze target files for parallel subagents
   node .agent/scripts/sdlc-engine.mjs test               # Run tests for touched modules
   node .agent/scripts/sdlc-engine.mjs coverage           # Run diff coverage check (>= 85%)
   node .agent/scripts/sdlc-engine.mjs mutation           # Run mutation defense check (>= 80%)
   node .agent/scripts/sdlc-engine.mjs contract check     # Check API OpenAPI/GraphQL contracts for breaking drift
   node .agent/scripts/sdlc-engine.mjs audit verify       # Verify SHA-256 Merkle chain in docs/AUDIT_CHAIN.jsonl
   node .agent/scripts/sdlc-engine.mjs proof generate     # Generate visual UI and API regression proof
   node .agent/scripts/sdlc-engine.mjs deconstruct "..."  # Deconstruct epic into topological DAG tickets
   node .agent/scripts/sdlc-engine.mjs watchdog monitor   # Monitor canary health & auto-rollback on breach
   node .agent/scripts/sdlc-engine.mjs protect [branches] # Enforce multi-branch protection on main & develop
   node .agent/scripts/sdlc-engine.mjs gate [--strict]    # Full quality gate (lint, build, test, arch, security, audit)
   ```

2. **Subagent Execution Capacity**:
   * Verify `invoke_subagent` and `manage_subagents` are ready for concurrent worker dispatch.

---

## ⏭️ Next Recommended Step

- To start an end-to-end automated run, invoke **`sdlc-flow`**.
- To scope a specific feature or ticket, invoke **`sdlc-feature-spec`**.
- To check project health and diagnostic status, run **`sdlc-engine doctor`**.
