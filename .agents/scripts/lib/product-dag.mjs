import fs from 'node:fs';
import path from 'node:path';
import { fileExists, readFileSafe } from './core.mjs';
import { createTicket } from './pm-provider.mjs';
import { appendDecisionEvent, syncDecisionMapMarkdown } from './decision-graph.mjs';
import { appendAuditRecord } from './audit-chain.mjs';

const DAG_STATE_FILE = '.agent/product-dag-state.json';

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
    return { id, title: capTitle, spec: specPath, dependsOn, status };
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

  // Scaffold missing spec files referenced by capabilities
  for (const cap of normalizedCaps) {
    const absSpec = path.resolve(repoRoot, cap.spec);
    if (!fileExists(absSpec)) {
      fs.mkdirSync(path.dirname(absSpec), { recursive: true });
      const specId = `SPEC-${cap.id.split('-')[1]}`;
      fs.writeFileSync(
        absSpec,
        `# ${specId}: ${cap.title}\n\n- **Linked Capability**: \`${cap.id}\`\n- **Status**: \`ACTIVE\`\n\n## 1. Domain Contract & Acceptance Criteria\n- Enforce Clean Architecture boundaries and Robert Martin TDD.\n`,
        'utf8'
      );
    }
  }

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
- **Genesis Ticket \`#1\`**: \`feature/issue-#1-product-genesis\` initializes \`PRODUCT.md\`, baseline specs, and project scaffolding.
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
        body: `Genesis Ticket #1 for ${title}. Establishes PRODUCT.md constitution and initial specs.`,
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
      genesisTicketId: 1
    },
    repoRoot
  });

  syncDecisionMapMarkdown(repoRoot);

  return {
    success: true,
    productPath,
    title,
    capabilities: normalizedCaps,
    invariants: normalizedInvs,
    genesisTicket
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
