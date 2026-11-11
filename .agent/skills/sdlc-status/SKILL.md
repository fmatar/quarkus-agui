---
name: sdlc-status
description: Displays active SDLC session, 8-stage lifecycle progress, spec-drift check, living KANBAN.md board state, engineering ROI telemetry, and live Architecture Diff UI in one unified view.
tools: read_file, write_file, edit_file, run_command
---

# 📊 Live Session Status, Drift & Architecture Inspector (`sdlc-status`)

## 1. Unified Status Inspection Sequence

Execute the following commands to assemble a complete real-time snapshot of the repository's active SDLC state:

```bash
# 1. Active session & 8-stage ASCII dashboard
node .agent/scripts/sdlc-engine.mjs session status
node .agent/scripts/sdlc-engine.mjs session dashboard

# 2. Check for undeclared spec/contract/architecture drift against PRODUCT.md & docs/adr/
node .agent/scripts/sdlc-engine.mjs drift check
node .agent/scripts/sdlc-engine.mjs arch diff

# 3. Engineering velocity, ROI & hash-chained audit ledger status
node .agent/scripts/sdlc-engine.mjs stats
node .agent/scripts/sdlc-engine.mjs audit verify

# 4. Refresh and launch the live Architecture Diff & Decision Lineage UI (http://localhost:4120)
node .agent/scripts/sdlc-engine.mjs ui-snapshot
```

## 2. Inline Architecture UI Presentation
Copy `.agent/workbench/index.html` to `<artifactDir>/sdlc-architecture-diff.html` via `write_file` (`UserFacing: true`) and render it inline in your response:
```markdown
<agent-embed src="file:///<artifactDir>/sdlc-architecture-diff.html"></agent-embed>
```
