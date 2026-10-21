---
name: sdlc-initialize
description: Universal project bootstrap installer and wizard. Uses structured discovery to auto-detect any polyglot stack, scaffold clean-room templates if greenfield, configure Archlens Clean Architecture governance, and wire pluggable PM providers (GitHub, Jira, Linear, or local KANBAN.md).
version: 0.0.1
license: Apache 2.0
allowed-tools: run_command, view_file, write_to_file, ask_question
---

# SDLC Universal Initialization & Discovery Wizard (`sdlc-initialize`)

Use this skill to onboard an existing project (brownfield) or scaffold a brand-new project (greenfield) into the Agentic SDLC framework. Scaffolds projects ready for Robert C. Martin TDD craftsmanship, F.I.R.S.T. harness isolation, Dual-Gate mutation defense, Archlens concentric layer governance, and pluggable project tracking.

---

## 📋 Core Responsibilities

1. **Detect-First Stack Discovery**: Automatically probe the workspace for existing package manifests (`pom.xml`, `package.json`, `go.mod`, `pyproject.toml`, `Cargo.toml`, etc.) and CI workflows before asking questions.
2. **Interactive Configuration Wizard (`ask_question`)**:
   - Confirm auto-detected stack or choose a starter template for empty workspaces.
   - Configure **Archlens** Clean Architecture concentric layer governance (`.archlens/policy.json`) and dynamic visual workbench.
   - Select **Project Management Provider** (GitHub Projects v2, Jira, Linear, or Local Markdown).
3. **Environment & MCP Pre-Flight Verification**: Verify required environment variables (`.env.example`) and MCP server connectivity (`node .agent/scripts/sdlc-engine.mjs env`).
4. **Framework Setup**: Provision `.agent/sdlc-config.json`, `docs/specs/`, `docs/KANBAN.md`, and Keep a Changelog standard `CHANGELOG.md`.

---

## 🛠️ Step-by-Step Execution Workflow

### Step 1: Pre-Flight Stack Discovery
Execute stack detection to discover manifests and existing configurations:
```bash
node .agent/scripts/sdlc-engine.mjs detect
```

- **If existing manifests are detected**: Proceed to **Step 2A (Brownfield Adoption)**.
- **If the workspace is empty or has no manifests**: Proceed to **Step 2B (Greenfield Scaffolding)**.

---

### Step 2A: Brownfield Adoption (Existing Codebase)
Call `ask_question` to present the discovered configuration to the user:
```json
{
  "questions": [
    {
      "question": "Detected project stack from workspace manifests. How would you like to proceed?",
      "options": [
        "(Recommended) Confirm detected stack, test commands, and Dual-Gate thresholds (>=85% coverage, >=80% mutation)",
        "Fine-tune test, coverage, or mutation commands"
      ]
    },
    {
      "question": "Enable Archlens Clean Architecture governance & dynamic visual workbench companion?",
      "options": [
        "(Recommended) Yes, configure Archlens policy (.archlens/policy.json) and MCP workbench",
        "No, keep basic SDLC without concentric layer governance"
      ]
    },
    {
      "question": "Which Project Management provider will drive this project's Kanban board?",
      "options": [
        "(Recommended) GitHub Projects v2 (uses gh CLI / github-mcp-server)",
        "Jira (uses Jira MCP server and JIRA_API_TOKEN)",
        "Linear (uses Linear MCP server and LINEAR_API_KEY)",
        "Local Living Markdown only (docs/KANBAN.md)"
      ]
    }
  ]
}
```

---

### Step 2B: Greenfield Scaffolding (Empty Workspace)
Call `ask_question` to select the starter template and options:
```json
{
  "questions": [
    {
      "question": "Select the project architecture template to scaffold:",
      "options": [
        "(Recommended) Full-Stack: Svelte 5 (Runes) UI + Quarkus Java 25 Reactive Mutiny Backend",
        "Backend API: Quarkus Java 25 Reactive (PostgreSQL + Flyway + PITest)",
        "Backend API: Spring Boot 3 / Java 21 (PostgreSQL + JaCoCo + PITest)",
        "Full-Stack: Next.js / TypeScript + Node.js (Vitest + Stryker)",
        "Go Microservice (Go 1.23+ with table-driven testing)",
        "Python Service (FastAPI + pytest + mutmut)"
      ]
    },
    {
      "question": "Enable Archlens Clean Architecture governance & dynamic visual workbench companion?",
      "options": [
        "(Recommended) Yes, configure Archlens policy (.archlens/policy.json) and MCP workbench",
        "No, skip concentric layer governance"
      ]
    },
    {
      "question": "Which Project Management provider will drive this project's Kanban board?",
      "options": [
        "(Recommended) GitHub Projects v2",
        "Jira",
        "Linear",
        "Local Living Markdown only (docs/KANBAN.md)"
      ]
    }
  ]
}
```

---

### Step 3: Archlens Governance Setup (if selected)
If Archlens governance was selected:
```bash
npx -y @fmatar/archlens-skill --path . --yes --mcp
```
This scaffolds `.archlens/policy.json` mapping Level 0 (Domain Core) through Level 3 (Infrastructure) and wires MCP tools (`inspectArchitecture`, `exportLlmDossier`).

---

### Step 4: Environment & Pre-Flight Diagnostic Check
1. Copy `.env.example` to `.env` if `.env` does not exist:
   ```bash
   test -f .env || cp .env.example .env
   ```
2. Run the environment diagnostic:
   ```bash
   node .agent/scripts/sdlc-engine.mjs env
   ```
3. If GitHub Projects v2 was selected, optionally provision the board:
   ```bash
   .agent/skills/sdlc-kanban/scripts/project-setup.sh
   ```

---

### Step 5: Finalize Configuration & Documentation
1. Write `.agent/sdlc-config.json` reflecting the chosen stack, Archlens policy path, and PM provider.
2. Initialize `docs/specs/`, `docs/KANBAN.md`, and Keep a Changelog standard `CHANGELOG.md`.

---

## ⏭️ Next Recommended Step

- To scope your first feature or contract, invoke **`sdlc-feature-spec`**.
- To execute end-to-end automated implementation, invoke **`sdlc-flow`**.
- To audit the repository baseline, invoke **`sdlc-analyze`**.
