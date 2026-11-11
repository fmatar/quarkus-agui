import fs from 'node:fs';
import path from 'node:path';
import { fileExists, readFileSafe } from './core.mjs';
import { createTicket } from './pm-provider.mjs';
import { appendDecisionEvent, syncDecisionMapMarkdown } from './decision-graph.mjs';
import { appendAuditRecord } from './audit-chain.mjs';

const DAG_STATE_FILE = '.agent/product-dag-state.json';

export function renderCapabilitySpecMarkdown({
  capId = 'CAP-01',
  title = 'Core Capability',
  problem = '',
  inputs = '',
  processing = '',
  outputs = '',
  dependsOn = []
} = {}) {
  const numPart = capId.includes('-') ? capId.split('-')[1] : '01';
  const specId = `SPEC-${numPart}`;
  const depText = Array.isArray(dependsOn) && dependsOn.length > 0 ? dependsOn.join(', ') : 'None';

  return `# ${specId}: ${title}

- **Linked Capability**: \`${capId}\`
- **Specification ID**: \`${specId}\`
- **Parent Product Docs**: \`PRODUCT.md\`, \`docs/PRD.md\`, \`docs/SRS.md\`
- **Depends On**: \`${depText}\`
- **Status**: \`ACTIVE\`

## 1. PRD Slice: Problem Alignment & Scope
- **Problem Statement & User Value**: ${problem || `Deliver ${title} with deterministic governance, Clean Architecture isolation, and zero manual plumbing.`}
- **In Scope**:
  - Core domain logic, interface ports, and automated Dual-Gate test suite for \`${capId}\`.
- **Out of Scope (Non-Goals)**:
  - Unrelated cross-capability mutations not declared in \`PRODUCT.md\`.
- **User Experience & Flows**:
  - **Happy Path**: Operator or agent invokes \`${capId}\` workflow and receives deterministic, verifiable output.
  - **Edge Cases & Error Handling**: Invalid inputs fail fast with structured diagnostic remediation.

## 2. Functional Requirements (IEEE 29148 SRS)
- **Inputs**: ${inputs || `Validated domain parameters, CLI arguments, or configuration state for ${capId}.`}
- **Processing**: ${processing || `Execute pure Ring 0/1 domain transformations and enforce invariant checks for ${title}.`}
- **Outputs**: ${outputs || `Structured result payload, updated state artifacts, and audit ledger events.`}

## 3. Domain Contract & Acceptance Criteria
- Enforce Clean Architecture concentric ring boundaries (\`.archlens/policy.json\`) and Robert C. Martin TDD.
- Achieve Dual-Gate verification (\`>= 85%\` diff line coverage and \`>= 80%\` mutation score).
- Zero shell command interpolation of untrusted inputs (\`INV-04\`).

## 4. C4 Component & Sequence Architecture
\`\`\`mermaid
flowchart LR
    Caller["Operator / Agent"] --> Port["${capId} Interface Port"]
    Port --> Domain["${capId} Domain Core"]
    Domain --> Audit["Audit & Decision Graph"]
\`\`\`
`;
}

export function renderProductPrdMarkdown({
  title = 'Autonomous Product System',
  vision = '',
  capabilities = [],
  invariants = []
} = {}) {
  const capRows = capabilities
    .map((c) => `- **${c.id} (${c.title})**: Governed by [\`${c.spec}\`](../${c.spec}) (Depends on: ${c.dependsOn.length > 0 ? c.dependsOn.join(', ') : 'None'})`)
    .join('\n');

  const invRows = invariants.map((i) => `- **${i.id}**: ${i.text}`).join('\n');

  return `# Product Requirements Document (PRD): ${title}

## 1. Meta & Traceability
- **Product Title**: ${title}
- **Constitutional Source of Truth**: [\`PRODUCT.md\`](../PRODUCT.md)
- **Companion System Specification**: [\`docs/SRS.md\`](./SRS.md)
- **Decision Lineage Map**: [\`docs/DECISION_MAP.md\`](./DECISION_MAP.md)
- **Status**: \`ACTIVE\`

## 2. Problem Alignment (The "Why")
- **Problem Statement**: ${vision}
- **Target Audience**: AI-native engineering teams, software architects, and autonomous coding agents (**Gemini / Antigravity**, **Claude Code**, **Cursor**, **Grok**, **Windsurf**, **GitHub Copilot**).
- **Value Proposition**: Replaces ad-hoc prompt engineering and fragile scripts with a deterministic, cryptographically verifiable 8-stage software engineering lifecycle.
- **Business & Engineering Goal**: Maximize feature velocity while guaranteeing zero architectural drift, zero shell injection vulnerabilities, and verified Dual-Gate test quality.

## 3. Scope & Product Capabilities (The "What")
### In Scope (\`CAP-XX\` Capability Portfolio)
${capRows}

### Out of Scope (Non-Goals)
- Bypassing the 4-Layer Git Guard or merging unverified code without a Stage 7 SHA-256 Merkle Gate Receipt.

## 4. User Experience & Core Flows
- **Onboarding Flow**: \`npx github:fmatar/agentic-sdlc-framework install\` -> select coding agents -> run \`/sdlc-doctor\`.
- **Product Architecture Flow**: \`/sdlc-product\` synthesizes \`PRODUCT.md\`, \`docs/PRD.md\`, \`docs/SRS.md\`, and \`docs/specs/cap-XX-*.md\`, then schedules topological DAG waves.
- **Ticket Delivery Flow**: \`/sdlc-flow\` executes \`stage begin\` -> \`/sdlc-tdd\` -> \`stage pre-pr\` -> \`/sdlc-gate\` -> \`stage complete\`.

## 5. Metrics, Success & Guardrails (The "How We Measure")
- **Primary Quality Metrics**: Diff Test Coverage \`>= 85%\`, Mutation Score \`>= 80%\`.
- **Guardrail Invariants (\`INV-XX\`)**:
${invRows}

## 6. Go-To-Market & Rollout
- **Rollout Plan**: Continuous semantic versioning via \`/sdlc-release\` with automated regression proofs (\`docs/proofs/\`) and live canary health watchdog probes.
`;
}

export function renderProductSrsMarkdown({
  title = 'Autonomous Product System',
  vision = '',
  capabilities = [],
  invariants = []
} = {}) {
  const traceRows = capabilities
    .map(
      (c) =>
        `| \`${c.id}\` | ${c.title} | [\`${c.spec}\`](../${c.spec}) | \`${c.dependsOn.length > 0 ? c.dependsOn.join(', ') : 'None'}\` | \`${c.status}\` |`
    )
    .join('\n');

  const functionalSections = capabilities
    .map(
      (c) => `#### ${c.id}: ${c.title}
- **Specification Contract**: [\`${c.spec}\`](../${c.spec})
- **Inputs**: Validated CLI/API parameters and repository state for \`${c.id}\`.
- **Processing**: Deterministic domain execution enforcing Clean Architecture rings and \`INV-XX\` invariants.
- **Outputs**: Verifiable state transitions, updated artifacts, and SHA-256 audit chain records.`
    )
    .join('\n\n');

  const invRows = invariants.map((i) => `- **${i.id}**: ${i.text}`).join('\n');

  return `# Software Requirements Specification (SRS): ${title}

*Based on IEEE 29148 Standard*

## 1. Introduction
### 1.1 Purpose
This Software Requirements Specification (SRS) defines the system-level functional interfaces, architectural boundaries, non-functional requirements (NFRs), and capability traceability matrix for **${title}**.
### 1.2 Scope
${vision}
### 1.3 References & Traceability Matrix
- **Product Constitution**: [\`PRODUCT.md\`](../PRODUCT.md)
- **Product Requirements Document**: [\`docs/PRD.md\`](./PRD.md)
- **Causal Decision Graph**: [\`docs/DECISION_MAP.md\`](./DECISION_MAP.md)

| Capability ID | Capability Title | Detailed Functional Spec | Depends On | Status |
| :--- | :--- | :--- | :--- | :--- |
${traceRows}

## 2. Overall Description & C4 System Architecture
### 2.1 Product Perspective (C4 Container View)
\`\`\`mermaid
flowchart TB
    Operator["Developer / AI Coding Agent"] --> Router["/sdlc Router & 8 Verb Skills\n(.agent/skills/)"]
    Router --> Engine["SDLC Universal Engine\n(.agent/scripts/sdlc-engine.mjs)"]
    Engine --> Domain["Modular Domain Libraries\n(.agent/scripts/lib/*.mjs)"]
    Domain --> Ledger["SHA-256 Merkle Audit Chain & Decision Graph\n(docs/AUDIT_CHAIN.jsonl, docs/DECISION_MAP.md)"]
    Domain --> Workbench["SvelteKit 5 Architecture & Decision Workbench\n(http://localhost:4120)"]
\`\`\`

### 2.2 Operating Environment & Constraints
- **Runtime**: Node.js \`>= 18.0.0\` (ES Modules), Git \`>= 2.30\`, polyglot project stacks (TypeScript/JS, Java/Kotlin, Python, Rust, Go).
- **Architectural Policy**: Governed by \`.archlens/policy.json\` concentric Clean Architecture rings.

## 3. Specific Requirements (IEEE 29148)
### 3.1 External Interface Requirements
- **3.1.1 Agent Slash-Command Interface**: 1 Universal Router (\`/sdlc\`) + 8 Verb Skills (\`/sdlc-flow\`, \`/sdlc-product\`, \`/sdlc-doctor\`, \`/sdlc-status\`, \`/sdlc-tdd\`, \`/sdlc-gate\`, \`/sdlc-adr\`, \`/sdlc-release\`).
- **3.1.2 CLI & NPX Interface**: \`npx github:fmatar/agentic-sdlc-framework install\` interactive ANSI TUI wizard and \`node .agent/scripts/sdlc-engine.mjs <command>\`.
- **3.1.3 HTTP & Visual Workbench Interface**: Local HTTP server (\`http://localhost:4120\`) serving \`/api/arch-diff\`, \`/widget\`, and inline \`<agent-embed>\` snapshots.

### 3.2 Functional Requirements by Capability (\`CAP-XX\`)
${functionalSections}

### 3.3 Non-Functional Requirements & System Invariants (\`INV-XX\`)
- **3.3.1 Verification & Test Quality**: Minimum \`85%\` diff test coverage and \`80%\` mutation score enforced prior to PR merge.
- **3.3.2 Security & Process Isolation**: Zero shell string interpolation of untrusted inputs; all external CLI calls use \`execFileSync\` argument arrays.
- **3.3.3 Hard Constitutional Invariants**:
${invRows}
`;
}

export function initProductConstitution({
  title = 'Autonomous Product System',
  vision = 'End-to-end spec-driven autonomous software system governed by Robert C. Martin Clean Architecture and Dual-Gate TDD.',
  capabilities = [],
  invariants = [],
  autoProvisionGenesis = false,
  createTicketFn = createTicket,
  repoRoot = '.'
} = {}) {
  const productPath = path.resolve(repoRoot, 'PRODUCT.md');
  const prdPath = path.resolve(repoRoot, 'docs/PRD.md');
  const srsPath = path.resolve(repoRoot, 'docs/SRS.md');
  const specsDir = path.resolve(repoRoot, 'docs/specs');
  const adrDir = path.resolve(repoRoot, 'docs/adr');
  fs.mkdirSync(specsDir, { recursive: true });
  fs.mkdirSync(adrDir, { recursive: true });

  const normalizedCaps = (capabilities.length > 0
    ? capabilities
    : [{ title: 'Core Domain & Architecture Foundation', spec: 'docs/specs/core-domain.md' }]
  ).map((c, idx) => {
    const id = c.id || `CAP-${String(idx + 1).padStart(2, '0')}`;
    const capTitle = typeof c === 'string' ? c : c.title || `Capability ${idx + 1}`;
    const specPath = (typeof c === 'object' && c.spec) || `docs/specs/${id.toLowerCase()}.md`;
    const dependsOn = (typeof c === 'object' && Array.isArray(c.dependsOn)) ? c.dependsOn : [];
    const status = (typeof c === 'object' && c.status) || 'PLANNED';
    const problem = (typeof c === 'object' && c.problem) || '';
    const inputs = (typeof c === 'object' && c.inputs) || '';
    const processing = (typeof c === 'object' && c.processing) || '';
    const outputs = (typeof c === 'object' && c.outputs) || '';
    return { id, title: capTitle, spec: specPath, dependsOn, status, problem, inputs, processing, outputs };
  });

  const normalizedInvs = (invariants.length > 0
    ? invariants
    : [
        'Zero direct commits, merges, or pushes to main or develop',
        '100% of code changes require an active SDLC ticket session and Dual-Gate verification (>=85% coverage, >=80% mutation)',
        'All spec drift must be recorded via an ADR (docs/adr/ADR-00X.md) and synchronized with PRODUCT.md'
      ]
  ).map((inv, idx) => {
    const id = typeof inv === 'object' && inv.id ? inv.id : `INV-${String(idx + 1).padStart(2, '0')}`;
    const text = typeof inv === 'string' ? inv : inv.text || inv.title;
    return { id, text };
  });

  // Scaffold missing spec files referenced by capabilities using rich PRD+SRS+C4 template
  for (const cap of normalizedCaps) {
    const absSpec = path.resolve(repoRoot, cap.spec);
    if (!fileExists(absSpec)) {
      fs.mkdirSync(path.dirname(absSpec), { recursive: true });
      fs.writeFileSync(
        absSpec,
        renderCapabilitySpecMarkdown({
          capId: cap.id,
          title: cap.title,
          problem: cap.problem,
          inputs: cap.inputs,
          processing: cap.processing,
          outputs: cap.outputs,
          dependsOn: cap.dependsOn
        }),
        'utf8'
      );
    }
  }

  // Scaffold macro docs/PRD.md and docs/SRS.md
  fs.writeFileSync(
    prdPath,
    renderProductPrdMarkdown({
      title,
      vision,
      capabilities: normalizedCaps,
      invariants: normalizedInvs
    }),
    'utf8'
  );

  fs.writeFileSync(
    srsPath,
    renderProductSrsMarkdown({
      title,
      vision,
      capabilities: normalizedCaps,
      invariants: normalizedInvs
    }),
    'utf8'
  );

  const capTableRows = normalizedCaps
    .map(
      (c) =>
        `| ${c.id} | ${c.title} | \`${c.spec}\` | ${c.dependsOn.length > 0 ? c.dependsOn.join(', ') : 'None'} | \`${c.status}\` |`
    )
    .join('\n');

  const invListRows = normalizedInvs.map((i) => `- **${i.id}**: ${i.text}`).join('\n');

  const productMd = `# 🏛️ Product Constitution: ${title}

> **Single Source of Truth (\`PRODUCT.md\`)**
> Generated & governed by **Agentic SDLC Framework (\`sdlc-product\`)**.
> Companion Macro Specifications: [\`docs/PRD.md\`](docs/PRD.md) (Product Requirements) & [\`docs/SRS.md\`](docs/SRS.md) (IEEE 29148 Software Requirements).
> Any architectural or contract deviation from this constitution requires a linked **ADR (\`docs/adr/ADR-00X.md\`)**.

---

## 1. Product Vision
${vision}

---

## 2. Hard Domain & System Invariants (\`INV-XX\`)
${invListRows}

---

## 3. Product Capabilities & Spec Traceability Matrix (\`CAP-XX\`)

| Capability ID | Capability Title | Functional Spec | Depends On | Status |
| :--- | :--- | :--- | :--- | :--- |
${capTableRows}

---

## 4. Genesis & Milestone Execution Rules
- **Genesis Ticket \`#1\`**: \`feature/issue-#1-product-genesis\` initializes \`PRODUCT.md\`, \`docs/PRD.md\`, \`docs/SRS.md\`, baseline specs, and project scaffolding.
- **Zero-Bypass Merge Rule**: No PR merges to \`main\` without a signed Stage 7 SHA-256 Merkle Gate Receipt.

---

## 5. Architectural Decision Log Index (\`ADR-XXX\`)
<!-- ADR_INDEX_START -->
| ADR ID | Title | Scope | Status | Ticket | Supersedes |
| :--- | :--- | :--- | :--- | :--- | :--- |
<!-- ADR_INDEX_END -->
`;

  fs.writeFileSync(productPath, productMd, 'utf8');

  // Provision Genesis Ticket #1 deterministically
  const genesisTicket = {
    issueId: 1,
    title: `chore(genesis): initialize PRODUCT.md and architecture baseline for ${title}`,
    type: 'feat',
    branch: 'feature/issue-#1-product-genesis',
    capabilityId: 'GENESIS'
  };

  if (autoProvisionGenesis && typeof createTicketFn === 'function') {
    try {
      createTicketFn({
        title: genesisTicket.title,
        type: 'feat',
        body: `Genesis Ticket #1 for ${title}. Establishes PRODUCT.md constitution, docs/PRD.md, docs/SRS.md, and initial specs.`,
        repoRoot
      });
    } catch {}
  }

  for (const cap of normalizedCaps) {
    appendDecisionEvent({
      type: 'CAPABILITY_REGISTERED',
      id: cap.id,
      label: `${cap.id}: ${cap.title}`,
      category: 'CAPABILITY',
      status: cap.status,
      ticketId: 1,
      linksTo: cap.dependsOn,
      metadata: { spec: cap.spec },
      repoRoot
    });
  }

  appendAuditRecord({
    event: 'PRODUCT_CONSTITUTION_INITIALIZED',
    metadata: {
      title,
      capabilitiesCount: normalizedCaps.length,
      invariantsCount: normalizedInvs.length,
      genesisTicketId: 1,
      prdPath: 'docs/PRD.md',
      srsPath: 'docs/SRS.md'
    },
    repoRoot
  });

  syncDecisionMapMarkdown(repoRoot);

  return {
    success: true,
    productPath,
    prdPath,
    srsPath,
    title,
    capabilities: normalizedCaps,
    invariants: normalizedInvs,
    genesisTicket
  };
}

export function scaffoldCapabilitySpec({
  capId = null,
  title = 'New Capability',
  problem = '',
  inputs = '',
  processing = '',
  outputs = '',
  dependsOn = [],
  status = 'PLANNED',
  force = false,
  repoRoot = '.'
} = {}) {
  const parsed = parseProductMarkdown(repoRoot);
  const existingCaps = parsed.capabilities || [];

  const resolvedCapId =
    capId ||
    `CAP-${String(existingCaps.length + 1).padStart(2, '0')}`;
  const upperCapId = resolvedCapId.toUpperCase();

  const slug = title
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '')
    .slice(0, 48);

  const existingMatch = existingCaps.find((c) => c.id.toUpperCase() === upperCapId);
  const relSpecPath = existingMatch?.spec || `docs/specs/${resolvedCapId.toLowerCase()}-${slug || 'spec'}.md`;
  const absSpecPath = path.resolve(repoRoot, relSpecPath);

  fs.mkdirSync(path.dirname(absSpecPath), { recursive: true });
  if (!fileExists(absSpecPath) || force) {
    const specMarkdown = renderCapabilitySpecMarkdown({
      capId: upperCapId,
      title,
      problem,
      inputs,
      processing,
      outputs,
      dependsOn
    });
    fs.writeFileSync(absSpecPath, specMarkdown, 'utf8');
  }

  const depCol = dependsOn.length > 0 ? dependsOn.join(', ') : 'None';

  // Register capability in PRODUCT.md if PRODUCT.md exists and doesn't yet list resolvedCapId
  const productPath = path.resolve(repoRoot, 'PRODUCT.md');
  if (fileExists(productPath) && !existingMatch) {
    const rawProduct = readFileSafe(productPath) || '';
    const newRow = `| ${upperCapId} | ${title} | \`${relSpecPath}\` | ${depCol} | \`${status}\` |`;
    const lines = rawProduct.split('\n');
    let lastCapRowIdx = -1;
    for (let i = 0; i < lines.length; i++) {
      if (/^\|\s*CAP-\d+\s*\|/.test(lines[i])) {
        lastCapRowIdx = i;
      }
    }
    if (lastCapRowIdx !== -1) {
      lines.splice(lastCapRowIdx + 1, 0, newRow);
      fs.writeFileSync(productPath, lines.join('\n'), 'utf8');
    }
  }

  // Non-destructively sync docs/PRD.md and docs/SRS.md whenever PRODUCT.md exists
  const refreshed = parseProductMarkdown(repoRoot);
  if (refreshed.exists) {
    const prdPath = path.resolve(repoRoot, 'docs/PRD.md');
    const srsPath = path.resolve(repoRoot, 'docs/SRS.md');
    fs.mkdirSync(path.dirname(prdPath), { recursive: true });

    if (!fileExists(prdPath) || force) {
      fs.writeFileSync(
        prdPath,
        renderProductPrdMarkdown({
          title: refreshed.title,
          vision: refreshed.vision || `Constitutional specification for ${refreshed.title}.`,
          capabilities: refreshed.capabilities,
          invariants: refreshed.invariants
        }),
        'utf8'
      );
    } else {
      const prdContent = readFileSafe(prdPath) || '';
      const prdBullet = `- **${upperCapId} (${title})**: Governed by [\`${relSpecPath}\`](../${relSpecPath}) (Depends on: ${depCol})`;
      if (!new RegExp(`\\*\\*${upperCapId}\\b`).test(prdContent)) {
        const prdLines = prdContent.split('\n');
        let lastCapBulletIdx = -1;
        for (let i = 0; i < prdLines.length; i++) {
          if (/^-\s*\*\*CAP-\d+/.test(prdLines[i].trim())) {
            lastCapBulletIdx = i;
          }
        }
        if (lastCapBulletIdx !== -1) {
          prdLines.splice(lastCapBulletIdx + 1, 0, prdBullet);
          fs.writeFileSync(prdPath, prdLines.join('\n'), 'utf8');
        } else {
          fs.writeFileSync(prdPath, `${prdContent.trimEnd()}\n${prdBullet}\n`, 'utf8');
        }
      }
    }

    if (!fileExists(srsPath) || force) {
      fs.writeFileSync(
        srsPath,
        renderProductSrsMarkdown({
          title: refreshed.title,
          vision: refreshed.vision || `Constitutional specification for ${refreshed.title}.`,
          capabilities: refreshed.capabilities,
          invariants: refreshed.invariants
        }),
        'utf8'
      );
    } else {
      let srsContent = readFileSafe(srsPath) || '';
      const srsRow = `| \`${upperCapId}\` | ${title} | [\`${relSpecPath}\`](../${relSpecPath}) | \`${depCol}\` | \`${status}\` |`;
      if (!new RegExp(`^\\|\\s*\`?${upperCapId}\`?\\s*\\|`, 'm').test(srsContent)) {
        const srsLines = srsContent.split('\n');
        let lastSrsCapRowIdx = -1;
        for (let i = 0; i < srsLines.length; i++) {
          if (/^\|\s*`?CAP-\d+`?\s*\|/.test(srsLines[i].trim())) {
            lastSrsCapRowIdx = i;
          }
        }
        if (lastSrsCapRowIdx !== -1) {
          srsLines.splice(lastSrsCapRowIdx + 1, 0, srsRow);
          srsContent = srsLines.join('\n');
        } else {
          srsContent = `${srsContent.trimEnd()}\n${srsRow}\n`;
        }
      }

      if (!new RegExp(`^####\\s+${upperCapId}:`, 'm').test(srsContent)) {
        const funcBlock = [
          `#### ${upperCapId}: ${title}`,
          `- **Specification Contract**: [\`${relSpecPath}\`](../${relSpecPath})`,
          `- **Inputs**: ${inputs || `Validated CLI/API parameters and repository state for \`${upperCapId}\`.`}`,
          `- **Processing**: ${processing || 'Deterministic domain execution enforcing Clean Architecture rings and `INV-XX` invariants.'}`,
          `- **Outputs**: ${outputs || 'Verifiable state transitions, updated artifacts, and SHA-256 audit chain records.'}`
        ].join('\n');

        const nfrHeadingIdx = srsContent.indexOf('### 3.3 ');
        if (nfrHeadingIdx !== -1) {
          srsContent = `${srsContent.slice(0, nfrHeadingIdx).trimEnd()}\n\n${funcBlock}\n\n${srsContent.slice(nfrHeadingIdx)}`;
        }
      }

      fs.writeFileSync(srsPath, srsContent, 'utf8');
    }
  }

  appendDecisionEvent({
    type: 'CAPABILITY_SPEC_SCAFFOLDED',
    id: upperCapId,
    label: `${upperCapId}: ${title}`,
    category: 'CAPABILITY',
    status,
    ticketId: null,
    linksTo: dependsOn,
    metadata: { spec: relSpecPath },
    repoRoot
  });

  syncDecisionMapMarkdown(repoRoot);

  return {
    success: true,
    capId: upperCapId,
    title,
    specPath: relSpecPath,
    absSpecPath,
    dependsOn,
    status
  };
}

export function parseProductMarkdown(repoRoot = '.') {
  const productPath = path.resolve(repoRoot, 'PRODUCT.md');
  if (!fileExists(productPath)) {
    return {
      exists: false,
      title: null,
      vision: '',
      capabilities: [],
      invariants: [],
      adrs: []
    };
  }

  const raw = readFileSafe(productPath) || '';
  const titleMatch = raw.match(/^#\s+(?:🏛️\s*Product Constitution:\s*)?(.+)$/m);
  const title = titleMatch ? titleMatch[1].trim() : 'Product';
  const visionMatch = raw.match(/##\s*1\.\s*Product Vision\s*\n+([^\n#]+)/i);
  const vision = visionMatch ? visionMatch[1].trim() : '';

  const capabilities = [];
  const invariants = [];
  const adrs = [];

  for (const line of raw.split('\n')) {
    const capMatch = line.match(/^\|\s*(CAP-\d+)\s*\|\s*([^|]+)\|\s*([^|]+)\|\s*([^|]+)\|\s*([^|]+)\|/);
    if (capMatch) {
      const dependsRaw = capMatch[4].trim();
      const dependsOn =
        dependsRaw === 'None' || dependsRaw === '-'
          ? []
          : dependsRaw.split(',').map((s) => s.trim()).filter(Boolean);
      capabilities.push({
        id: capMatch[1].trim(),
        title: capMatch[2].trim(),
        spec: capMatch[3].trim().replace(/`/g, ''),
        dependsOn,
        status: capMatch[5].trim().replace(/`/g, '')
      });
      continue;
    }

    const invMatch = line.match(/^-\s*\*\*(INV-\d+)\*\*:\s*(.+)$/);
    if (invMatch) {
      invariants.push({
        id: invMatch[1].trim(),
        text: invMatch[2].trim()
      });
      continue;
    }

    const adrMatch = line.match(/^\|\s*(ADR-\d+)\s*\|\s*([^|]+)\|\s*([^|]+)\|\s*([^|]+)\|\s*([^|]+)\|\s*([^|]+)\|/);
    if (adrMatch) {
      adrs.push({
        id: adrMatch[1].trim(),
        title: adrMatch[2].trim(),
        scope: adrMatch[3].trim().replace(/`/g, ''),
        status: adrMatch[4].trim().replace(/`/g, ''),
        ticket: adrMatch[5].trim(),
        supersedes: adrMatch[6].trim()
      });
    }
  }

  return {
    exists: true,
    productPath,
    title,
    vision,
    capabilities,
    invariants,
    adrs
  };
}

export function planProductDAG({ repoRoot = '.', autoProvision = false, createTicketFn = createTicket } = {}) {
  const parsed = parseProductMarkdown(repoRoot);
  const caps = parsed.capabilities || [];

  // Compute topological waves
  const resolved = new Set();
  const remaining = [...caps];
  const waves = [];
  let waveNum = 1;

  while (remaining.length > 0 && waveNum <= caps.length + 2) {
    const readyInWave = remaining.filter((c) => c.dependsOn.every((dep) => resolved.has(dep)));
    const batch = readyInWave.length > 0 ? readyInWave : [remaining[0]];

    waves.push({
      wave: waveNum++,
      parallelizable: batch.length > 1,
      items: batch.map((item) => ({
        ...item,
        worktreeBranch: `feature/issue-${item.id.toLowerCase()}-${item.title.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')}`
      }))
    });

    for (const item of batch) {
      resolved.add(item.id);
      const idx = remaining.findIndex((r) => r.id === item.id);
      if (idx !== -1) remaining.splice(idx, 1);
    }
  }

  const provisioned = [];
  if (autoProvision && typeof createTicketFn === 'function') {
    for (const w of waves) {
      for (const item of w.items) {
        try {
          const tRes = createTicketFn({
            title: `feat(${item.id}): ${item.title}`,
            type: 'feat',
            body: `Capability: ${item.id}\nSpec: ${item.spec}\nDepends On: ${item.dependsOn.join(', ') || 'None'}`,
            repoRoot
          });
          provisioned.push(tRes);
        } catch {}
      }
    }
  }

  const statePath = path.resolve(repoRoot, DAG_STATE_FILE);
  fs.mkdirSync(path.dirname(statePath), { recursive: true });
  let currentState = { status: 'idle', currentWave: 1, history: [] };
  if (fileExists(statePath)) {
    try {
      currentState = JSON.parse(readFileSafe(statePath)) || currentState;
    } catch {}
  }

  const plan = {
    success: true,
    title: parsed.title || 'Product DAG',
    totalCapabilities: caps.length,
    waves,
    provisioned,
    state: currentState
  };

  return plan;
}

export function controlProductDAG({ action = 'status', reason = '', wave = null, repoRoot = '.' } = {}) {
  const statePath = path.resolve(repoRoot, DAG_STATE_FILE);
  fs.mkdirSync(path.dirname(statePath), { recursive: true });

  let state = {
    status: 'running',
    currentWave: 1,
    updatedAt: new Date().toISOString(),
    reason: '',
    history: []
  };

  if (fileExists(statePath)) {
    try {
      state = { ...state, ...JSON.parse(readFileSafe(statePath)) };
    } catch {}
  }

  const normAction = String(action).toLowerCase();
  if (normAction === 'pause') {
    state.status = 'paused';
    state.reason = reason || 'Paused by operator';
  } else if (normAction === 'resume' || normAction === 'start') {
    state.status = 'running';
    state.reason = reason || 'Resumed execution';
  } else if (normAction === 'trigger-wave' || normAction === 'advance') {
    state.status = 'running';
    state.currentWave = wave ? Number(wave) : (state.currentWave || 1) + 1;
    state.reason = reason || `Triggered wave ${state.currentWave}`;
  }

  state.updatedAt = new Date().toISOString();
  state.history = Array.isArray(state.history) ? state.history : [];
  state.history.push({ action: normAction, timestamp: state.updatedAt, reason: state.reason });

  fs.writeFileSync(statePath, JSON.stringify(state, null, 2), 'utf8');

  return {
    success: true,
    action: normAction,
    state
  };
}

// --- Autonomous DAG Ticket & Epic Deconstruction Engine (Backward-Compatible Issue #41) ---

export function deconstructEpic({
  epicText = '',
  epicTitle = 'Epic Initiative',
  autoProvision = false,
  createTicketFn = createTicket,
  repoRoot = '.'
} = {}) {
  const lines = (epicText || '').split('\n').map((l) => l.trim()).filter(Boolean);
  const tasks = [];
  let currentTask = null;

  for (const line of lines) {
    const match = line.match(/^[-*•\d.]+\s*(?:\[[ xX]\]\s*)?(?:(feat|fix|hotfix|spike|chore|task):\s*)?(.+)$/i);
    if (match) {
      const type = (match[1] || 'feat').toLowerCase();
      const rawTitle = match[2].trim();
      const dependsOn = [];

      const depMatch = rawTitle.match(/\[(?:depends|after|deps):\s*([a-zA-Z0-9_,\s-]+)\]/i);
      let cleanTitle = rawTitle;
      if (depMatch) {
        cleanTitle = rawTitle.replace(depMatch[0], '').trim();
        const depTokens = depMatch[1].split(',').map((s) => s.trim()).filter(Boolean);
        dependsOn.push(...depTokens);
      }

      currentTask = {
        id: tasks.length + 1,
        title: cleanTitle,
        type: ['feat', 'fix', 'hotfix', 'spike'].includes(type) ? type : 'feat',
        dependsOn,
        ready: dependsOn.length === 0,
        ticketUrl: null
      };
      tasks.push(currentTask);
    } else if (currentTask && line.startsWith('>')) {
      currentTask.description = (currentTask.description ? currentTask.description + '\n' : '') + line.slice(1).trim();
    }
  }

  if (tasks.length === 0 && epicText.trim()) {
    tasks.push({
      id: 1,
      title: epicTitle,
      type: 'feat',
      dependsOn: [],
      ready: true,
      ticketUrl: null
    });
  }

  const provisioned = [];
  if (autoProvision && typeof createTicketFn === 'function') {
    for (const t of tasks) {
      try {
        const ticketRes = createTicketFn({
          title: t.title,
          type: t.type,
          body: `Part of Epic: ${epicTitle}\nDependencies: ${t.dependsOn.join(', ') || 'None'}\n\n${t.description || ''}`,
          repoRoot
        });
        t.ticketUrl = ticketRes.url;
        t.ticketId = ticketRes.issueId;
        provisioned.push(ticketRes);
      } catch {}
    }
  }

  const mermaidLines = ['graph TD'];
  for (const t of tasks) {
    mermaidLines.push(`  T${t.id}["[#${t.id}] ${t.title.replace(/"/g, "'")}"]`);
    for (const dep of t.dependsOn) {
      mermaidLines.push(`  T${dep} --> T${t.id}`);
    }
  }

  return {
    success: true,
    epicTitle,
    totalTasks: tasks.length,
    tasks,
    mermaidDAG: mermaidLines.join('\n'),
    provisionedCount: provisioned.length,
    provisioned
  };
}
