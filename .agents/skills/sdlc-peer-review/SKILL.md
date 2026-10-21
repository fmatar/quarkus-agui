---
name: sdlc-peer-review
description: Parallel peer reviewer subagent that audits git diffs, verifies Dual-Gate quality (>= 85% coverage, >= 80% mutation score), detects Robert Martin test smells and mock abuse, validates developer showcase onboarding quickstarts, and posts formal review comments to GitHub PRs.
version: 0.0.1
license: Apache 2.0
allowed-tools: run_command, view_file, mcp_github-mcp-server_pull_request_read, mcp_github-mcp-server_get_file_contents, mcp_github-mcp-server_add_comment_to_pending_review, mcp_github-mcp-server_pull_request_review_write
---

# SDLC Parallel Peer Reviewer Skill (`sdlc-peer-review`)

Use this skill when invoked as a parallel reviewer subagent via `invoke_subagent` to independently audit Pull Requests. This skill acts as a senior software craftsman inspecting code quality, architectural boundaries, and test craftsmanship.

---

## 🔍 Comprehensive Audit Checklist

1. **Code Standards & Architecture**:
   - **Clean Architecture & DIP (Archlens)**:
     - Verify dependencies strictly point inward: `Infrastructure -> Adapters -> Application -> Domain Core`.
     - Verify Dependency Inversion Principle (DIP): domain ports (interfaces) declared in inner layers; external adapters (DB, REST clients) implement them in outer layers.
     - Zero domain leaks: Domain entities must never import frameworks, ORMs, or HTTP abstractions.
   - **Frontend / UI Standards**: Encapsulated fine-grained reactivity (e.g. Svelte 5 Runes, React Hooks, Vue refs); no memory leaks in effects, no unhandled async rejections.
   - **Backend / Microservice Standards**: Clean domain models, non-blocking I/O where applicable, no split packages or circular layer dependencies.
2. **Security & Input Hygiene**:
   - Validate null checks, input sanitization, boundaries, and secure token/secret handling (zero plaintext credentials).
3. **Dual-Gate Testing & Test Craftsmanship (Robert Martin Standard)**:
   - **Diff Coverage**: Confirm modified line and branch coverage meets or exceeds **$\ge$ 85%** (JaCoCo `target/site/jacoco/index.html` or Vitest `coverage/index.html`).
   - **Mutation Kill Score**: Confirm mutation testing score reaches **$\ge$ 80%** on modified classes (PITest `target/pit-reports/index.html` or Stryker `reports/mutation/mutation.html`).
   - **Audit for Test Smells (Robert Martin)**:
     - ❌ *Flag trivial getter/setter or record field tests* written solely to inflate coverage.
     - ❌ *Flag tests of third-party framework plumbing* ("Don't test code you didn't write" - e.g. testing Panache entity persistence mechanics or Svelte runtime reactivity).
     - ❌ *Flag mock abuse*: Flag tests verifying internal mechanical call sequences on mock collaborators rather than testing observable state, outputs, or boundary contracts.
     - ❌ *Flag assertion-free tests* (`assertDoesNotThrow` without validating resulting state).
4. **Documentation & Changelog**:
   - Confirm `CHANGELOG.md` reflects changes under `[Unreleased]` with ticket links.
   - Confirm specs in `docs/specs/` or inline docstrings are updated.
5. **Kanban & Issue Association**:
   - Confirm PR description explicitly includes `Fixes #<id>` linking to a tracked GitHub issue tagged `status:in-review`.
6. **Developer Showcase & Quickstart Validity**:
   - Confirm root `README.md` maintains a functional, copy-pasteable **5-Minute Developer Quickstart**.
   - If new features, CLI commands, or archetypes were introduced, confirm runnable examples and the BYOA contract are updated.

---

## 🛠️ Execution & Posting (Mandatory Comment Invariant)

> [!IMPORTANT]
> **HARD FRAMEWORK INVARIANT**: Every review execution **MUST post a review comment** to the active issue/PR tracking tool (GitHub, Jira, Linear, or living `docs/KANBAN.md`). Silent or undocumented review outcomes are strictly forbidden.

1. Inspect the diff against `main` or base branch (`git diff origin/main...HEAD`).
2. Synthesize line-specific review comments highlighting architectural improvements, test smell remediation, diff coverage ($\ge 85\%$), and mutation score ($\ge 80\%$).
3. **Post Formal Review to PM Provider**:
   - **GitHub (Pull Request & Issue)**:
     - Post via `pull_request_review_write` or `add_issue_comment` via `github-mcp-server`.
     - Set event to `APPROVE` if all checks pass.
     - Set event to `REQUEST_CHANGES` if coverage $<85\%$, mutation $<80\%$, test smells exist, or regressions are found.
   - **Jira, Linear, or Local Fallback (`sdlc-engine pm comment`)**:
     - Run universal comment dispatcher:
       ```bash
       node .agent/scripts/sdlc-engine.mjs pm comment <issueId> [--pr <prNumber>] --body "<structured review summary>"
       ```
       *(Automatically delivers the review audit comment directly to Jira REST API, Linear GraphQL, or logs into `docs/KANBAN.md` under `## 💬 Review Audit Comments & Feedback` as transparent offline proof).*

---

## ⏭️ Next Recommended Step

- Report review status back to parent orchestrator (`sdlc-flow`).
