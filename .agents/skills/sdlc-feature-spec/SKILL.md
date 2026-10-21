---
name: sdlc-feature-spec
description: Generates formal Product Requirements (PRDs), Software Requirements (SRS), C4 Architecture diagrams, ADRs, and standardized GitHub Issues for new features.
version: 0.0.1
license: Apache 2.0
allowed-tools: run_command, view_file, write_to_file, replace_file_content, mcp_github-mcp-server_issue_read, mcp_github-mcp-server_issue_write, mcp_github-mcp-server_search_issues
---

# SDLC Feature Specification & Architecture Planning Skill (`sdlc-feature-spec`)

Use this skill when initiating new features, architectural refactors, or submitting GitHub Issues. It establishes rigorous engineering contracts before code is written.

---

## 📋 Core Responsibilities

- Produce **Product Requirements Documents (PRDs)** based on the SVPG standard using `docs/templates/PRD_TEMPLATE.md`.
- Produce **Software Requirements Specifications (SRS)** based on IEEE 29148 using `docs/templates/SRS_TEMPLATE.md`.
- Create **C4 Architecture Diagrams** (Context, Container, Component) using Mermaid.js based on `docs/templates/C4_ARCHITECTURE_TEMPLATE.md`.
- Draft **Architecture Decision Records (ADRs)** for technical tradeoffs using `docs/templates/ADR_TEMPLATE.md`.
- Draft standardized GitHub Issues with User Stories, Technical Context, Acceptance Criteria (Definition of Done), and Target Files.

---

## 🛠️ Step-by-Step Specification Workflow

### Step 1: Requirements Discovery & Technical Scope
- Determine feature intent, target personas, and user problem statement.
- Identify architectural boundaries: frontend components / UI views, backend APIs / microservices, and persistence / storage entities.
- Identify external service integrations (e.g. OAuth providers, REST APIs, webhooks).

### Step 2: Draft the Technical Specification (RFC)
Structure the specification using this standard schema:

```markdown
# RFC: [Feature Title]

## 1. Problem Statement & User Value
- Core user need addressed.
- Target personas and key use cases.

## 2. Technical Architecture & Data Schema
- Data models to add/modify (entities, DTOs, schemas).
- Frontend / client state stores impacted.
- Backend endpoints, reactive/async contracts, and interface ports.

## 3. UI/UX & Responsive Layouts
- Impacted routes and components.
- State transitions (loading, error, empty, active).
- Accessibility (keyboard navigation, ARIA live regions, color contrast).

## 4. Acceptance Criteria (Gherkin Scenarios / DoD)
Scenario: Successful entity lifecycle transition
  Given an authenticated user with valid permissions
  When the user submits valid payload data
  Then the entity is persisted idempotently
  And a success status is returned within 200ms

## 5. Security & Performance Considerations
- Rate limiting, token validation, and authorization scopes.
- Zero plaintext credentials or leaked logs.
- Memory leak prevention and non-blocking I/O.
```

### Step 3: ADR Generation (When Applicable)
If introducing new architectural patterns, libraries, or storage models:
- Create `wiki/ADR-00X-[title].md` or `docs/specs/adr/` following the Michael Nygard format (Title, Status, Context, Decision, Consequences).

### Step 4: Issue & Ticket Creation (Features & Bug Fixes)
Determine the active logged-in user and repository project dynamically:
```bash
ASSIGNEE=$(gh api user -q .login 2>/dev/null || gh auth status 2>&1 | grep "account" | awk '{print $NF}')
OWNER=$(gh repo view --json owner -q .owner.login 2>/dev/null)
REPO_NAME=$(gh repo view --json name -q .name 2>/dev/null)
# Resolve project name matching repository name or SDLC board
PROJECT_NAME=$(gh project list --owner "$OWNER" --format json 2>/dev/null | jq -r --arg repo "$REPO_NAME" '.projects[] | select((.title | ascii_downcase) | contains($repo | ascii_downcase)) | .title' | head -n 1)
```

Create the structured ticket using either GitHub CLI (`gh issue create`), MCP `issue_write`, or the unified engine CLI with automatic template discovery:
```bash
# Automated template-aware multi-provider creation (Auto-discovers .github/ISSUE_TEMPLATE or Jira schemas):
node .agent/scripts/sdlc-engine.mjs ticket create \
  --title "<type>(<component>): <title>" \
  --type <feat|bug> \
  --problem "<concise problem statement>" \
  --solution "<acceptance criteria and proposed solution>" \
  [--reproduce "<reproduction steps for bugs>"] \
  [--expected "<expected TDD behavior>"] \
  [--environment "<OS/runtime info>"]
```

#### Template Conformance & Discovery:
The engine dynamically discovers and populates templates configured in the target project:
1. **GitHub Issue Templates**: Inspects `.github/ISSUE_TEMPLATE/*.md` or `.yml`. It extracts YAML frontmatter labels (e.g., `enhancement`, `bug`), merges them with SDLC status labels (`status:ready`), and interpolates the user problem, proposed solution, reproduction steps, and environment directly into the template's markdown sections.
2. **Jira & Local Kanban Fallbacks**: When GitHub templates are absent, it renders standardized Robert C. Martin specifications guaranteeing clean traceability, DoD, and TDD red-green verification steps.

#### Ticket Schemas:
- **For Features (`feat`)**:
  - **Title**: `feat(<component>): <short action-oriented title>`
  - **Labels**: `feature`, `status:ready`, `<layer>` (`backend`, `frontend`, etc.)
  - **Body**: Automatically populated via discovered template or Robert Martin RFC structure.
- **For Bug Fixes (`fix`)**:
  - **Title**: `fix(<component>): <concise symptom or failure>`
  - **Labels**: `bug`, `status:ready`
  - **Body**: Automatically populated via discovered bug template or TDD reproduction structure.

---

## ⏭️ Next Recommended Step

Once the RFC, acceptance criteria, and GitHub Issue are created and tagged `status:ready`:
👉 **Transition to [`sdlc-kanban`](../sdlc-kanban/SKILL.md)** to place the card on the board, and **[`sdlc-flow`](../sdlc-flow/SKILL.md)** to branch, implement with TDD, and run the automated lifecycle.
