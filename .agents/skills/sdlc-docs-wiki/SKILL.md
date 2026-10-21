---
name: sdlc-docs-wiki
description: Synchronizes documentation across the project, including root README showcase/quickstart, docs/specs/, CHANGELOG.md conventional commits, /wiki, OpenSpec delta specs, and inline code docs.
version: 0.0.1
license: Apache 2.0
allowed-tools: run_command, view_file, write_to_file, replace_file_content
---

# SDLC Documentation & Wiki Synchronization Skill (`sdlc-docs-wiki`)

Use this skill whenever features, data models, or architectural patterns are added, modified, or deprecated. This skill enforces continuous documentation hygiene across code, specs, and the developer-facing root README.

---

## 📚 Synchronized Documentation Targets

1. **Root Developer Onboarding & Showcase (`README.md`)**:
   - **5-Minute Developer Quickstart**: A runnable, copy-pasteable onboarding guide with 4 stages:
     1. *CLI / Toolchain Installation*
     2. *Local Environment / Cluster Bootstrap* (e.g. `install.sh`, `docker compose up`, or cluster bootstrap)
     3. *Exploring Pre-built Showcase Examples* with source code and benchmark metrics
     4. *Bring Your Own App (BYOA)*: scaffolding, container contract, deployment, and live evaluation.
   - **Polyglot Archetypes Matrix**: Comparative performance metrics (cold-start latency, memory footprint, container size, framework stack).
   - **System Architecture & Zero-Trust Governance**: C4 diagrams, runtime interactions, and protocol invariants.
2. **Functional Architectural Specs (`docs/specs/`)**: Detailed specifications, data contracts, and domain rules per feature area.
3. **Conventional Changelog (`CHANGELOG.md`)**: Adheres to [Keep a Changelog](https://keepachangelog.com/en/1.0.0/). Records changes under `[Unreleased]` with ticket links.
4. **Living Kanban Board (`docs/KANBAN.md`)**: Version-controlled visual board synchronized via `sdlc-kanban` (`.agent/skills/sdlc-kanban/scripts/kanban-sync.sh`).
5. **Repository Wiki & Architecture (`wiki/`)**: Deep-dive system architecture, ADRs, C4 diagrams, and deployment guides.
6. **Inline Code Documentation**: Idiomatic docstrings/annotations across detected languages (e.g. Javadoc for Java, TSDoc for TypeScript, Go doc comments, Rust doc comments `///`, Python docstrings).
7. **OpenSpec Change Synchronization**: If OpenSpec delta specs are present, sync changes across `openspec/specs/`.

---

## 🗂️ Wiki Structure Standard

```text
wiki/
├── Home.md                          # Vision, architecture overview, and quick navigation
├── Architecture.md                  # C4 diagrams, runtime interactions, and Fork-Join model
├── Skills-Reference.md              # Documentation of available agentic skills
├── Traceability.md                  # 7-layer traceability thread, 6 Project v2 views, custom fields
├── ADR-00X-[title].md               # Architectural Decision Records
├── _Sidebar.md                      # GitHub Wiki sidebar navigation
└── _Footer.md                       # Footer navigation and branding
```

---

## 🛠️ Step-by-Step Documentation Workflow

### Step 1: Detect Code Changes & Impact
Inspect `git diff --name-only main` to identify modified surfaces:
- New CLI commands, flags, or local workflows $\rightarrow$ Update root `README.md` Quickstart.
- New reference archetypes or templates $\rightarrow$ Update `README.md` Polyglot Matrix and `docs/specs/`.
- New backend endpoint or service $\rightarrow$ Update `docs/specs/<service>.md` and Javadoc.
- New frontend component or rune store $\rightarrow$ Update `docs/specs/<ui-feature>.md` and TSDoc.
- Architectural shift $\rightarrow$ Add `wiki/ADR-00X-[title].md` and update `wiki/Architecture.md`.

### Step 2: Maintain Root `README.md` Developer Showcase Invariant
Every repository MUST provide an immediate, frictionless path for new developers:
1. **Frictionless time-to-first-run (< 5 minutes)**: Ensure installation and bootstrap commands run without hidden prerequisites.
2. **Bring Your Own App (BYOA) Contract**: Explicitly document the protocol or interface requirements for custom user apps/agents to run on the platform.
3. **Showcase Gallery**: Direct links to runnable code examples with Dockerfiles and manifests.

### Step 3: Update `CHANGELOG.md`
Append entries under `[Unreleased]` following Keep a Changelog categories (`Added`, `Changed`, `Deprecated`, `Removed`, `Fixed`, `Security`):
```markdown
### Added
- feat(showcase): polyglot agent showcase and 4-step developer quickstart ([#27](https://github.com/org/repo/issues/27))

### Fixed
- fix(sync): prevent concurrent event race condition during calendar sync ([#1058](https://github.com/org/repo/issues/1058))
```

### Step 4: Update Wiki & Navigation
- Ensure new topics are indexed in `wiki/_Sidebar.md`.
- Verify all relative markdown links resolve without broken anchors.

### Step 5: Publishing Wiki Pages to GitHub
GitHub Wiki is a Git repository (`https://github.com/<owner>/<repo>.wiki.git`).
- **Automated Publishing**: Push changes to `main`. `.github/workflows/publish-wiki.yml` automatically syncs all files from `wiki/` directly to the GitHub Wiki.
- **First-Time Initializing**: If GitHub Wiki has never been used, visit `https://github.com/<owner>/<repo>/wiki` and click *"Create the first page"* to initialize the wiki repository.
- **Direct CLI Publishing**:
  ```bash
  TMP_WIKI=$(mktemp -d)
  git clone "https://x-access-token:$GITHUB_TOKEN@github.com/<owner>/<repo>.wiki.git" "$TMP_WIKI"
  cp -r wiki/* "$TMP_WIKI/"
  cd "$TMP_WIKI" && git add . && git commit -m "docs: sync wiki pages" && git push origin master
  rm -rf "$TMP_WIKI"
  ```

---

## ⏭️ Next Recommended Step

Once documentation, developer showcase quickstart, and specs are synchronized:
👉 **Transition to [`sdlc-quality-gate`](../sdlc-quality-gate/SKILL.md)** to execute pre-merge audits and static verification.
