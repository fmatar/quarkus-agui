import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import crypto from 'node:crypto';
import { spawn } from 'node:child_process';
import { fileExists, readFileSafe, getModifiedFiles } from './core.mjs';
import { getSessionStatus } from './session.mjs';
import { buildDecisionGraph } from './decision-graph.mjs';
import { parseProductMarkdown, planProductDAG, controlProductDAG } from './product-dag.mjs';
import { listADRs, decideADR } from './adr-drift.mjs';
import { verifyAuditChain } from './audit-chain.mjs';
import { checkArchDiff } from './arch-trace.mjs';

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.mjs': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.ico': 'image/x-icon'
};

function readJsonBody(req) {
  return new Promise((resolve) => {
    let raw = '';
    req.on('data', (chunk) => (raw += chunk));
    req.on('end', () => {
      try {
        resolve(raw ? JSON.parse(raw) : {});
      } catch {
        resolve({});
      }
    });
  });
}

function stripEmoji(text = '') {
  return String(text)
    .replace(/[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}\u{FE00}-\u{FE0F}\u{200D}]/gu, '')
    .replace(/\s+/g, ' ')
    .trim();
}

function parseMarkdownLink(cell = '') {
  const cleaned = stripEmoji(cell);
  const match = cleaned.match(/\[([^\]]+)\]\(([^)]+)\)/);
  if (match) {
    const label = match[1].trim();
    const url = match[2].trim();
    const idMatch = label.match(/^(#[A-Za-z0-9_-]+)\s*(.*)$/);
    return {
      id: idMatch ? idMatch[1] : label,
      title: idMatch && idMatch[2] ? idMatch[2] : label,
      url,
      raw: cleaned
    };
  }
  return { id: cleaned, title: cleaned, url: null, raw: cleaned };
}

export function parseKanbanMarkdown(raw = '') {
  const columns = [
    { key: 'backlog', label: 'Backlog', items: [] },
    { key: 'ready', label: 'Ready', items: [] },
    { key: 'in_progress', label: 'In Progress', items: [] },
    { key: 'in_review', label: 'In Review', items: [] },
    { key: 'done', label: 'Done', items: [] }
  ];
  const matrix = [];
  let repo = '';
  let lastSynced = '';

  const lines = String(raw || '').split('\n');
  let mode = null;

  for (const line of lines) {
    const trimmed = line.trim();
    const repoMatch = trimmed.match(/\*\*Repository\*\*:\s*`([^`]+)`/);
    if (repoMatch) repo = repoMatch[1];
    const syncMatch = trimmed.match(/\*\*Last Synced\*\*:\s*`([^`]+)`/);
    if (syncMatch) lastSynced = syncMatch[1];

    if (trimmed.includes('Board Overview')) {
      mode = 'board';
      continue;
    }
    if (trimmed.includes('End-to-End Traceability Matrix')) {
      mode = 'matrix';
      continue;
    }
    if (trimmed.startsWith('### ') || trimmed.startsWith('## ')) {
      if (!trimmed.includes('Board Overview') && !trimmed.includes('Traceability Matrix')) {
        mode = null;
      }
    }

    if (!trimmed.startsWith('|') || !trimmed.endsWith('|')) continue;
    const cells = trimmed
      .slice(1, -1)
      .split('|')
      .map((c) => c.trim());
    if (cells.every((c) => /^:?-{2,}:?$/.test(c))) continue;

    if (mode === 'board' && cells.length >= 5) {
      if (cells[0].toLowerCase().includes('backlog')) continue;
      for (let i = 0; i < 5; i++) {
        if (cells[i]) {
          const parsed = parseMarkdownLink(cells[i]);
          if (parsed.raw) columns[i].items.push(parsed);
        }
      }
    } else if (mode === 'matrix' && cells.length >= 5) {
      if (cells[0].toLowerCase().includes('issue')) continue;
      const nonEmpty = cells.filter(Boolean);
      if (nonEmpty.length === 1 && cells[3]) {
        const parsed = parseMarkdownLink(cells[3]);
        matrix.push({
          issue: parsed,
          status: 'status:in-review',
          priority: 'P2 - Medium',
          component: 'General',
          milestone: '—',
          spec: '—'
        });
      } else if (cells[0]) {
        const parsed = parseMarkdownLink(cells[0]);
        matrix.push({
          issue: parsed,
          status: cells[1]?.replace(/`/g, '') || 'status:done',
          priority: cells[2] || '—',
          component: cells[3] || 'General',
          milestone: cells[4] || '—',
          spec: cells[5] || '—'
        });
      }
    }
  }

  return { repo, lastSynced, columns, matrix };
}

export function parseArchTraceMarkdown(raw = '') {
  const entries = [];
  const lines = String(raw || '').split('\n');
  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed.startsWith('|') || !trimmed.endsWith('|')) continue;
    const cells = trimmed
      .slice(1, -1)
      .split('|')
      .map((c) => c.trim());
    if (cells.length < 7) continue;
    if (cells.every((c) => /^:?-{2,}:?$/.test(c))) continue;
    if (cells[0].toLowerCase().includes('timestamp')) continue;

    const ticket = parseMarkdownLink(cells[1]);
    const changesList = cells[5]
      .split(/<br\s*\/?>/i)
      .map((s) => stripEmoji(s.replace(/`/g, '')))
      .filter(Boolean);
    const statusText = stripEmoji(cells[6]) || 'PASSED';

    entries.push({
      timestamp: cells[0],
      ticket,
      branch: cells[2].replace(/`/g, ''),
      commit: cells[3].replace(/`/g, ''),
      reason: stripEmoji(cells[4]),
      changes: changesList,
      status: statusText
    });
  }
  return entries.reverse();
}

export function buildArchDiffPayload(repoRoot = '.') {
  const archDiff = checkArchDiff(repoRoot);
  const modifiedFiles = getModifiedFiles('main', repoRoot);

  const rings = {
    'Domain & Core Rules': [],
    'Application & Engine Libs': [],
    'Adapters, CLI & Skills': [],
    'Specs, ADRs & Verification': []
  };

  for (const file of modifiedFiles) {
    const lower = file.toLowerCase();
    if (
      lower.includes('domain') ||
      lower.includes('core.mjs') ||
      lower.includes('audit-chain.mjs') ||
      lower.includes('adr-drift.mjs') ||
      lower.includes('product-dag.mjs') ||
      path.basename(lower) === 'product.md'
    ) {
      rings['Domain & Core Rules'].push(file);
    } else if (lower.includes('.agent/scripts/lib/')) {
      rings['Application & Engine Libs'].push(file);
    } else if (
      lower.includes('.agent/skills/') ||
      lower.includes('.claude/skills/') ||
      lower.includes('.claude/commands/') ||
      lower.includes('.claude/cowork/') ||
      lower.includes('.claude-plugin/') ||
      lower.includes('sdlc-engine.mjs') ||
      lower.includes('workbench') ||
      lower.endsWith('.sh')
    ) {
      rings['Adapters, CLI & Skills'].push(file);
    } else {
      rings['Specs, ADRs & Verification'].push(file);
    }
  }

  const traceJsonlPath = path.resolve(repoRoot, '.agent/arch-trace.jsonl');
  const snapshots = [];
  if (fileExists(traceJsonlPath)) {
    const lines = (readFileSafe(traceJsonlPath) || '').split('\n').filter(Boolean);
    for (const line of lines) {
      try {
        const parsed = JSON.parse(line);
        if (parsed.reason) parsed.reason = stripEmoji(parsed.reason);
        if (Array.isArray(parsed.diagramChanges)) {
          parsed.diagramChanges = parsed.diagramChanges.map((dc) => ({
            ...dc,
            summary: stripEmoji(dc.summary || ''),
            diff: dc.diff
              ? {
                  ...dc.diff,
                  addedNodes: (dc.diff.addedNodes || []).map((n) => ({ ...n, label: stripEmoji(n.label || '') })),
                  removedNodes: (dc.diff.removedNodes || []).map((n) => ({ ...n, label: stripEmoji(n.label || '') }))
                }
              : undefined
          }));
        }
        snapshots.push(parsed);
      } catch {}
    }
  }

  const latestSnapshot = snapshots.length > 0 ? snapshots[snapshots.length - 1] : null;
  return {
    archDiff,
    modifiedFiles,
    rings,
    snapshotsCount: snapshots.length,
    latestSnapshot,
    snapshots: snapshots.slice(-15)
  };
}

export function generateArchitectureWidget({ outputPath = null, repoRoot = '.' } = {}) {
  const sessionStatus = getSessionStatus(repoRoot);
  const rawGraph = buildDecisionGraph(repoRoot);
  const compactGraph = {
    nodes: (rawGraph.nodes || []).map((n) => ({
      ...n,
      label: stripEmoji(n.label || ''),
      metadata: n.metadata
        ? {
            ...n.metadata,
            content: typeof n.metadata.content === 'string' ? stripEmoji(n.metadata.content.slice(0, 280)) : undefined
          }
        : {}
    })),
    edges: rawGraph.edges || [],
    driftPivots: rawGraph.driftPivots || [],
    stats: rawGraph.stats || {}
  };
  const product = parseProductMarkdown(repoRoot);
  const dag = planProductDAG({ repoRoot, autoProvision: false });
  const rawArchPayload = buildArchDiffPayload(repoRoot);
  const compactSnapshot = (s) =>
    s
      ? {
          ...s,
          diagramChanges: (s.diagramChanges || []).map((dc) => ({
            diagram: dc.diagram,
            summary: dc.summary,
            diff: dc.diff
              ? {
                  hasChanges: dc.diff.hasChanges,
                  addedNodes: (dc.diff.addedNodes || []).slice(0, 6),
                  removedNodes: (dc.diff.removedNodes || []).slice(0, 6)
                }
              : undefined
          }))
        }
      : null;
  const archPayload = {
    ...rawArchPayload,
    latestSnapshot: compactSnapshot(rawArchPayload.latestSnapshot),
    snapshots: (rawArchPayload.snapshots || []).map(compactSnapshot)
  };
  const audit = verifyAuditChain(repoRoot);

  const embeddedState = JSON.stringify({
    session: sessionStatus.session,
    audit: {
      valid: audit.valid,
      recordsCount: audit.recordsCount,
      merkleRoot: audit.merkleRoot,
      message: audit.message
    },
    graph: compactGraph,
    product,
    dag,
    arch: archPayload
  }).replace(/</g, '\\u003c');

  const html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>SDLC Architecture Diff & Decision Workbench</title>
  <style>
    :root {
      --paper: #eef2f6;
      --folio: #f8fafc;
      --well: #e2e8f0;
      --ink: #0f172a;
      --ink-secondary: #334155;
      --ink-muted: #475569;
      --rule: #cbd5e1;
      --rule-strong: #94a3b8;
      --cobalt: #1e40af;
      --cobalt-bg: #dbeafe;
      --seal-pass: #166534;
      --seal-pass-bg: #dcfce7;
      --seal-crimson: #991b1b;
      --seal-crimson-bg: #fee2e2;
      --seal-amber: #92400e;
      --seal-amber-bg: #fef3c7;
      --serif: "Iowan Old Style", "Palatino Linotype", "Book Antiqua", Palatino, Georgia, serif;
      --sans: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
      --mono: "SFMono-Regular", ui-monospace, Menlo, Monaco, Consolas, monospace;
    }
    * { box-sizing: border-box; }
    ::selection { background: var(--cobalt); color: var(--folio); }
    html, body {
      margin: 0;
      padding: 0;
      background: var(--paper);
      color: var(--ink);
      font-family: var(--sans);
      font-size: 14px;
      line-height: 1.5;
      caret-color: var(--cobalt);
      scrollbar-width: thin;
      scrollbar-color: var(--rule-strong) var(--well);
      -webkit-font-smoothing: antialiased;
    }
    a { color: var(--cobalt); text-underline-offset: 3px; }
    button, input {
      font-family: inherit;
      color: inherit;
    }
    :focus-visible {
      outline: 2px solid var(--cobalt);
      outline-offset: 2px;
    }
    .widget-shell {
      max-width: 1200px;
      margin: 0 auto;
      padding: 16px;
    }
    .ledger-sheet {
      background: var(--folio);
      border: 1px solid var(--rule-strong);
      border-radius: 6px;
      box-shadow: 0 2px 8px -2px rgba(20, 24, 31, 0.07);
      overflow: hidden;
    }
    .sheet-header {
      padding: 16px 20px;
      background: var(--well);
      border-bottom: 1px solid var(--rule-strong);
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      align-items: flex-start;
      gap: 12px;
    }
    .sheet-header h1 {
      margin: 0;
      font-family: var(--serif);
      font-size: clamp(1.15rem, 1rem + 0.6vw, 1.4rem);
      font-weight: 700;
      letter-spacing: -0.02em;
      color: var(--ink);
      text-wrap: balance;
    }
    .sheet-subtitle {
      margin: 4px 0 0 0;
      font-size: 12px;
      color: var(--ink-secondary);
      max-inline-size: 70ch;
    }
    .sheet-subtitle code {
      font-family: var(--mono);
      font-variant-numeric: tabular-nums;
      background: var(--folio);
      padding: 1px 5px;
      border-radius: 3px;
      border: 1px solid var(--rule);
    }
    .seal-group {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 8px;
    }
    .seal-pill {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      font-size: 12px;
      font-weight: 600;
      padding: 4px 10px;
      border-radius: 4px;
      border: 1px solid var(--rule-strong);
      background: var(--folio);
      color: var(--ink);
      font-variant-numeric: tabular-nums;
    }
    .seal-pill.pass {
      background: var(--seal-pass-bg);
      color: var(--seal-pass);
      border-color: #86efac;
    }
    .seal-pill.fail {
      background: var(--seal-crimson-bg);
      color: var(--seal-crimson);
      border-color: #fca5a5;
    }
    .tab-strip {
      display: flex;
      flex-wrap: wrap;
      gap: 4px;
      padding: 10px 20px;
      background: var(--paper);
      border-bottom: 1px solid var(--rule);
    }
    .tab-btn {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 7px 14px;
      font-size: 12px;
      font-weight: 600;
      background: transparent;
      color: var(--ink-secondary);
      border: 1px solid transparent;
      border-radius: 4px;
      cursor: pointer;
      transition: background-color 150ms ease, color 150ms ease, border-color 150ms ease;
    }
    .tab-btn:hover {
      background: var(--well);
      color: var(--ink);
    }
    .tab-btn[aria-selected="true"] {
      background: var(--cobalt);
      color: var(--folio);
      border-color: var(--cobalt);
    }
    .folio-body {
      padding: 20px;
    }
    .folio-panel {
      animation: folioReveal 200ms cubic-bezier(0.16, 1, 0.3, 1);
    }
    .folio-panel[hidden] {
      display: none !important;
    }
    @keyframes folioReveal {
      from { opacity: 0; transform: translateY(4px); }
      to { opacity: 1; transform: translateY(0); }
    }
    @media (prefers-reduced-motion: reduce) {
      .folio-panel { animation: none; }
    }
    .double-entry {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
      gap: 20px;
    }
    .folio-column {
      border: 1px solid var(--rule);
      border-radius: 4px;
      background: var(--paper);
      padding: 12px 14px;
      overflow: hidden;
    }
    .folio-column h2 {
      margin: -12px -14px 12px -14px;
      padding: 10px 14px;
      font-family: var(--serif);
      font-size: 14px;
      font-weight: 700;
      background: var(--well);
      border-bottom: 1px solid var(--rule);
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .ledger-table {
      width: 100%;
      border-collapse: separate;
      border-spacing: 0;
      font-size: 12px;
      font-variant-numeric: tabular-nums;
    }
    .ledger-table th,
    .ledger-table td {
      padding: 8px 12px;
      text-align: left;
      border-bottom: 1px solid var(--rule);
      vertical-align: top;
    }
    .ledger-table th {
      font-weight: 600;
      color: var(--ink-secondary);
      background: var(--folio);
    }
    .ledger-table tr:last-child td {
      border-bottom: none;
    }
    .mono {
      font-family: var(--mono);
      font-variant-numeric: tabular-nums;
    }
    .node-list {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
      gap: 10px;
      max-height: 260px;
      overflow-y: auto;
      padding: 4px 2px;
    }
    .node-item {
      text-align: left;
      background: var(--paper);
      border: 1px solid var(--rule);
      border-radius: 4px;
      padding: 10px 12px;
      cursor: pointer;
      transition: border-color 150ms ease, background-color 150ms ease;
    }
    .node-item:hover {
      background: var(--well);
      border-color: var(--cobalt);
    }
    .node-item-head {
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-size: 11px;
      color: var(--ink-secondary);
      font-family: var(--mono);
    }
    .node-item-title {
      font-weight: 600;
      margin-top: 4px;
      color: var(--ink);
      font-size: 12px;
    }
    .inspector-box {
      margin-top: 14px;
      padding: 12px 16px;
      background: var(--well);
      border: 1px solid var(--rule-strong);
      border-radius: 4px;
      font-size: 12px;
    }
    .scrubber-bar {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px 16px;
      background: var(--well);
      border: 1px solid var(--rule);
      border-radius: 4px;
      margin-bottom: 14px;
    }
    .scrubber-bar input[type="range"] {
      flex: 1;
      accent-color: var(--cobalt);
      cursor: pointer;
    }
  </style>
</head>
<body>
  <div class="widget-shell">
    <div class="ledger-sheet">
      <header class="sheet-header">
        <div>
          <h1>SDLC Architecture Diff & Decision Workbench</h1>
          <p class="sheet-subtitle">
            Concentric Ring Clearinghouse, Mermaid AST Diffs &amp; Causal Decision Lineage &middot; Live Server: <code>http://localhost:4120</code>
          </p>
        </div>
        <div class="seal-group">
          <span id="session-pill" class="seal-pill"></span>
          <span id="ring-badge" class="seal-pill"></span>
          <span id="merkle-badge" class="seal-pill"></span>
        </div>
      </header>

      <nav class="tab-strip" role="tablist" aria-label="Architecture workbench views">
        <button type="button" role="tab" aria-selected="true" onclick="switchTab('rings')" id="tab-rings" class="tab-btn">
          Ring &amp; Merkle Clearinghouse
        </button>
        <button type="button" role="tab" aria-selected="false" onclick="switchTab('lineage')" id="tab-lineage" class="tab-btn">
          Decision Lineage (CAP to ADR)
        </button>
        <button type="button" role="tab" aria-selected="false" onclick="switchTab('timemachine')" id="tab-timemachine" class="tab-btn">
          Time-Machine Ledger
        </button>
        <button type="button" role="tab" aria-selected="false" onclick="switchTab('waves')" id="tab-waves" class="tab-btn">
          DAG Waves &amp; Specs
        </button>
      </nav>

      <div class="folio-body">
        <section id="view-rings" class="folio-panel" role="tabpanel" aria-labelledby="tab-rings">
          <div class="double-entry">
            <div class="folio-column">
              <h2>
                <span>Debit Folio: Concentric Ring Boundaries</span>
                <span class="mono" id="rings-count-label"></span>
              </h2>
              <table class="ledger-table">
                <thead>
                  <tr>
                    <th>Architecture Ring</th>
                    <th>Delta</th>
                    <th>Modified Paths</th>
                  </tr>
                </thead>
                <tbody id="rings-grid"></tbody>
              </table>
            </div>
            <div class="folio-column">
              <h2>
                <span>Credit Folio: Merkle Audit &amp; AST Diff</span>
                <span class="mono" id="merkle-root-short"></span>
              </h2>
              <div id="mermaid-diff-box" style="padding: 12px 14px; font-size: 12px;"></div>
            </div>
          </div>
        </section>

        <section id="view-lineage" class="folio-panel" role="tabpanel" aria-labelledby="tab-lineage" hidden>
          <div id="nodes-grid" class="node-list"></div>
          <div id="node-inspector" class="inspector-box">
            Select any capability, specification, ticket, or ADR folio entry above to inspect its causal lineage and governance status.
          </div>
        </section>

        <section id="view-timemachine" class="folio-panel" role="tabpanel" aria-labelledby="tab-timemachine" hidden>
          <div class="scrubber-bar">
            <label for="time-slider" style="font-weight: 600; font-size: 12px;">Evolution Snapshot:</label>
            <input id="time-slider" type="range" min="0" max="0" value="0" oninput="renderSnapshot(this.value)" />
            <span id="slider-label" class="mono" style="font-size: 12px; color: var(--ink-secondary);"></span>
          </div>
          <div id="snapshot-detail" class="folio-column" style="padding: 14px;"></div>
        </section>

        <section id="view-waves" class="folio-panel" role="tabpanel" aria-labelledby="tab-waves" hidden>
          <div class="folio-column">
            <h2>
              <span>Topological Execution Wave Schedule</span>
              <span class="mono">PRODUCT.md</span>
            </h2>
            <table class="ledger-table">
              <thead>
                <tr>
                  <th>Wave</th>
                  <th>Execution Mode</th>
                  <th>Capabilities &amp; Status</th>
                </tr>
              </thead>
              <tbody id="waves-tbody"></tbody>
            </table>
          </div>
        </section>
      </div>
    </div>
  </div>

  <script>
    const STATE = ${embeddedState};

    function switchTab(tab) {
      for (const t of ['rings', 'lineage', 'timemachine', 'waves']) {
        const view = document.getElementById('view-' + t);
        const btn = document.getElementById('tab-' + t);
        if (t === tab) {
          view.hidden = false;
          btn.setAttribute('aria-selected', 'true');
        } else {
          view.hidden = true;
          btn.setAttribute('aria-selected', 'false');
        }
      }
    }

    function renderSnapshot(idxStr) {
      const snaps = STATE.arch.snapshots || [];
      const idx = Math.max(0, Math.min(snaps.length - 1, Number(idxStr) || 0));
      const snap = snaps[idx];
      const label = document.getElementById('slider-label');
      const box = document.getElementById('snapshot-detail');
      if (!snap) {
        label.textContent = '0 / 0';
        box.innerHTML = '<p style="margin:0;color:var(--ink-secondary);">No architectural snapshots recorded yet.</p>';
        return;
      }
      label.textContent = 'Entry ' + (idx + 1) + ' of ' + snaps.length + ' (' + (snap.timestamp || '').slice(0, 16).replace('T', ' ') + ')';
      const diagList = (snap.diagramChanges || []).map(d => '<li class="mono">' + d.diagram + ' &mdash; ' + d.summary + '</li>').join('');
      box.innerHTML =
        '<div style="display:flex;flex-wrap:wrap;justify-content:space-between;gap:8px;font-weight:600;border-bottom:1px solid var(--rule);padding-bottom:8px;margin-bottom:8px;">' +
          '<span>Ticket: <code class="mono">' + (snap.ticket || 'N/A') + '</code> &middot; Branch: <code class="mono">' + (snap.branch || 'main') + '</code></span>' +
          '<span>Commit: <code class="mono">' + (snap.commitSha || 'HEAD') + '</code> &middot; Seal: <strong>' + (snap.status || 'PASSED') + '</strong></span>' +
        '</div>' +
        '<p style="margin:0 0 8px 0;color:var(--ink-secondary);"><strong>Checkpoint Rationale:</strong> ' + (snap.reason || 'Architectural verification checkpoint') + '</p>' +
        (diagList ? '<ul style="margin:0;padding-left:18px;">' + diagList + '</ul>' : '<p style="margin:0;color:var(--ink-secondary);" class="mono">Modified Files: ' + ((snap.modifiedFiles || []).length) + '</p>');
    }

    function inspectNode(nodeId) {
      const node = (STATE.graph.nodes || []).find(n => n.id === nodeId);
      const el = document.getElementById('node-inspector');
      if (!node || !el) return;
      const connected = (STATE.graph.edges || [])
        .filter(e => e.from === node.id || e.to === node.id)
        .map(e => e.from + ' [' + e.relation + '] -> ' + e.to);
      el.innerHTML =
        '<div style="font-weight:700;color:var(--ink);">' + node.id + ' &mdash; ' + node.label + '</div>' +
        '<div style="margin-top:4px;color:var(--ink-secondary);">Category: <code class="mono">' + node.category + '</code> &middot; Governance Status: <code class="mono">' + (node.status || 'ACTIVE') + '</code></div>' +
        (connected.length ? '<div style="margin-top:6px;color:var(--cobalt);" class="mono">' + connected.join(' | ') + '</div>' : '<div style="margin-top:6px;color:var(--ink-muted);">Standalone constitutional node.</div>');
    }

    function init() {
      const sess = STATE.session;
      const sessPill = document.getElementById('session-pill');
      sessPill.textContent = sess
        ? 'Ticket #' + sess.issueId + ' (Stage ' + sess.stage + '/8)'
        : 'Mainline Standing By';

      const archPass = STATE.arch.archDiff?.passed !== false;
      const ringBadge = document.getElementById('ring-badge');
      ringBadge.className = 'seal-pill ' + (archPass ? 'pass' : 'fail');
      ringBadge.textContent = archPass
        ? 'Rings Balanced: 0 Violations'
        : 'Ring Violations: ' + STATE.arch.archDiff.violationsCount;

      const merkleBadge = document.getElementById('merkle-badge');
      merkleBadge.className = 'seal-pill ' + (STATE.audit?.valid ? 'pass' : 'fail');
      merkleBadge.textContent = STATE.audit?.valid
        ? 'Merkle Verified (' + (STATE.audit.recordsCount || 0) + ' entries)'
        : 'Merkle Check Failed';

      document.getElementById('rings-count-label').textContent = (STATE.arch.modifiedFiles?.length || 0) + ' modified';
      document.getElementById('merkle-root-short').textContent = STATE.audit?.merkleRoot
        ? 'Root ' + String(STATE.audit.merkleRoot).slice(0, 12) + '...'
        : 'Genesis Root';

      const ringsGrid = document.getElementById('rings-grid');
      const ringEntries = Object.entries(STATE.arch.rings || {});
      ringsGrid.innerHTML = ringEntries.map(([ringName, files]) => {
        const preview = files.slice(0, 4).map(f => '<div class="mono">' + f + '</div>').join('');
        const more = files.length > 4 ? '<div style="color:var(--ink-muted);">+' + (files.length - 4) + ' additional file(s)</div>' : '';
        return '<tr>' +
          '<td style="font-weight:600;">' + ringName + '</td>' +
          '<td class="mono">' + files.length + '</td>' +
          '<td>' + (files.length > 0 ? preview + more : '<span style="color:var(--ink-muted);">In Balance (Unchanged)</span>') + '</td>' +
        '</tr>';
      }).join('');

      const latest = STATE.arch.latestSnapshot;
      const diffBox = document.getElementById('mermaid-diff-box');
      const rootHash = STATE.audit?.merkleRoot || '0000000000000000';
      if (latest && (latest.diagramChanges || []).length > 0) {
        diffBox.innerHTML =
          '<div style="margin-bottom:8px;"><strong>SHA-256 Merkle Root:</strong> <code class="mono">' + rootHash.slice(0, 24) + '...</code></div>' +
          '<div style="font-weight:600;margin-bottom:4px;">Latest Mermaid AST Graph Diff (' + latest.ticket + '):</div>' +
          latest.diagramChanges.map(d => '<div class="mono">&bull; ' + d.diagram + ': ' + d.summary + '</div>').join('');
      } else {
        diffBox.innerHTML =
          '<div style="margin-bottom:8px;"><strong>SHA-256 Merkle Root:</strong> <code class="mono">' + rootHash.slice(0, 24) + '...</code></div>' +
          '<div style="font-weight:600;">Latest Architectural Checkpoint:</div>' +
          '<div style="color:var(--ink-secondary);margin-top:4px;">' + (latest ? (latest.reason + ' (' + latest.ticket + ' on ' + latest.branch + ')') : 'Zero breaking diagram mutations recorded.') + '</div>';
      }

      const nodesGrid = document.getElementById('nodes-grid');
      nodesGrid.innerHTML = (STATE.graph.nodes || []).map(n =>
        '<button type="button" onclick="inspectNode(\\'' + n.id.replace(/'/g, '') + '\\')" class="node-item">' +
          '<div class="node-item-head"><span>' + n.id + '</span><span>' + (n.status || n.category) + '</span></div>' +
          '<div class="node-item-title">' + n.label + '</div>' +
        '</button>'
      ).join('');

      const snaps = STATE.arch.snapshots || [];
      const slider = document.getElementById('time-slider');
      slider.max = String(Math.max(0, snaps.length - 1));
      slider.value = String(Math.max(0, snaps.length - 1));
      renderSnapshot(slider.value);

      const wavesTbody = document.getElementById('waves-tbody');
      wavesTbody.innerHTML = (STATE.dag?.waves || []).map(w =>
        '<tr>' +
          '<td class="mono" style="font-weight:600;">Wave ' + w.wave + '</td>' +
          '<td>' + (w.parallelizable ? 'Parallelizable Fork-Join' : 'Sequential Gate') + '</td>' +
          '<td>' + (w.items || []).map(it => '<div class="mono"><strong>' + it.id + '</strong>: ' + it.title + ' (' + it.status + ')</div>').join('') + '</td>' +
        '</tr>'
      ).join('');

      switchTab('rings');
    }

    init();
  </script>
</body>
</html>`;

  const defaultWidgetPath = path.resolve(repoRoot, '.agent/workbench/index.html');
  fs.mkdirSync(path.dirname(defaultWidgetPath), { recursive: true });
  fs.writeFileSync(defaultWidgetPath, html, 'utf8');

  let writtenPath = defaultWidgetPath;
  if (outputPath) {
    const absOut = path.resolve(repoRoot, outputPath);
    fs.mkdirSync(path.dirname(absOut), { recursive: true });
    fs.writeFileSync(absOut, html, 'utf8');
    writtenPath = absOut;
  }

  return {
    success: true,
    widgetPath: writtenPath,
    defaultWidgetPath,
    nodesCount: compactGraph.nodes?.length || 0,
    modifiedFilesCount: archPayload.modifiedFiles.length,
    ringViolationsCount: archPayload.archDiff.violationsCount
  };
}

export function renderWorkbenchHTML(repoRoot = '.', getSessionStatusFn = getSessionStatus) {
  const sessionStatus =
    typeof getSessionStatusFn === 'function' ? getSessionStatusFn(repoRoot) : { active: false, session: null };
  const activeSess = sessionStatus.session;
  const kanbanPath = path.resolve(repoRoot, 'docs/KANBAN.md');
  const kanbanRaw = fileExists(kanbanPath) ? readFileSafe(kanbanPath) : 'No KANBAN.md file found.';
  const archTracePath = path.resolve(repoRoot, 'docs/ARCH_TRACE.md');
  const archTraceRaw = fileExists(archTracePath) ? readFileSafe(archTracePath) : 'No ARCH_TRACE.md recorded yet.';

  const parsedKanban = parseKanbanMarkdown(kanbanRaw);
  const parsedTrace = parseArchTraceMarkdown(archTraceRaw);
  const initialArch = buildArchDiffPayload(repoRoot);
  const initialGraph = buildDecisionGraph(repoRoot);
  const initialProduct = parseProductMarkdown(repoRoot);
  const initialDag = planProductDAG({ repoRoot, autoProvision: false });
  const initialAdrs = listADRs(repoRoot);
  const initialAudit = verifyAuditChain(repoRoot);

  const initialBootstrap = JSON.stringify({
    session: activeSess,
    audit: initialAudit,
    arch: initialArch,
    graph: initialGraph,
    product: initialProduct,
    dag: initialDag,
    adrs: initialAdrs,
    kanban: parsedKanban,
    trace: parsedTrace
  }).replace(/</g, '\\u003c');

  const stageNames = [
    { num: 1, name: '1. Intake', role: 'Scope & Spec' },
    { num: 2, name: '2. Branch', role: 'Isolation' },
    { num: 3, name: '3. TDD', role: 'Red-Green' },
    { num: 4, name: '4. Dual-Gate', role: 'Cov & Mut' },
    { num: 5, name: '5. Docs', role: 'Sync & ADR' },
    { num: 6, name: '6. PR', role: 'Dossier' },
    { num: 7, name: '7. Review', role: 'Merkle Receipt' },
    { num: 8, name: '8. Done', role: 'Merged' }
  ];

  const stageStepsHtml = stageNames
    .map((s) => {
      const curStage = Number(activeSess?.stage || 0);
      let stateClass = 'pending';
      let stateText = 'Queued';
      if (curStage > s.num) {
        stateClass = 'done';
        stateText = 'Cleared';
      } else if (curStage === s.num) {
        stateClass = 'active';
        stateText = 'Active';
      }
      return `<li class="stage-step ${stateClass}">
        <div class="stage-step-top">
          <span class="stage-step-name">${s.name}</span>
          <span class="stage-step-state">${stateText}</span>
        </div>
        <div class="stage-step-role">${s.role}</div>
      </li>`;
    })
    .join('');

  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>Agentic SDLC Framework: Workbench & Live Mission Control</title>
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <style>
    :root {
      --paper: #eef2f6;
      --folio: #f8fafc;
      --well: #e2e8f0;
      --well-deep: #cbd5e1;
      --ink: #0f172a;
      --ink-secondary: #334155;
      --ink-muted: #475569;
      --rule: #cbd5e1;
      --rule-strong: #94a3b8;
      --cobalt: #1e40af;
      --cobalt-hover: #1d4ed8;
      --cobalt-soft: #dbeafe;
      --seal-pass: #166534;
      --seal-pass-bg: #dcfce7;
      --seal-pass-border: #86efac;
      --seal-crimson: #991b1b;
      --seal-crimson-bg: #fee2e2;
      --seal-crimson-border: #fca5a5;
      --seal-amber: #92400e;
      --seal-amber-bg: #fef3c7;
      --seal-amber-border: #fcd34d;
      --serif: "Iowan Old Style", "Palatino Linotype", "Book Antiqua", Palatino, Georgia, serif;
      --sans: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
      --mono: "SFMono-Regular", ui-monospace, Menlo, Monaco, Consolas, monospace;
    }

    * { box-sizing: border-box; }
    ::selection { background: var(--cobalt); color: var(--folio); }

    html, body {
      margin: 0;
      padding: 0;
      min-height: 100dvh;
      background: var(--paper);
      color: var(--ink);
      font-family: var(--sans);
      font-size: 14px;
      line-height: 1.5;
      caret-color: var(--cobalt);
      scrollbar-width: thin;
      scrollbar-color: var(--rule-strong) var(--well);
      -webkit-font-smoothing: antialiased;
    }

    a {
      color: var(--cobalt);
      text-decoration: underline;
      text-underline-offset: 3px;
    }
    a:hover {
      color: var(--cobalt-hover);
    }

    button, input, select {
      font-family: inherit;
      font-size: inherit;
      color: inherit;
    }

    :focus-visible {
      outline: 2px solid var(--cobalt);
      outline-offset: 2px;
    }

    .mono {
      font-family: var(--mono);
      font-variant-numeric: tabular-nums;
    }

    .tabular {
      font-variant-numeric: tabular-nums;
    }

    /* Outer Clearinghouse Frame */
    .clearinghouse-shell {
      max-width: 1560px;
      margin: 0 auto;
      padding: clamp(16px, 2vw, 28px);
    }

    /* Top Masthead */
    .masthead {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      align-items: flex-start;
      gap: 16px;
      padding: 18px 24px;
      background: var(--folio);
      border: 1px solid var(--rule-strong);
      border-radius: 6px;
      box-shadow: 0 2px 8px -2px rgba(20, 24, 31, 0.06);
      margin-bottom: 16px;
    }

    .masthead h1 {
      margin: 0;
      font-family: var(--serif);
      font-size: clamp(1.35rem, 1.1rem + 0.8vw, 1.85rem);
      font-weight: 700;
      letter-spacing: -0.02em;
      color: var(--ink);
      text-wrap: balance;
    }

    .masthead-sub {
      margin: 4px 0 0 0;
      color: var(--ink-secondary);
      font-size: 13px;
      max-inline-size: 72ch;
    }

    .masthead-seals {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 10px;
    }

    .seal-badge {
      display: inline-flex;
      align-items: center;
      gap: 7px;
      padding: 6px 12px;
      border-radius: 4px;
      font-size: 12px;
      font-weight: 600;
      font-variant-numeric: tabular-nums;
      white-space: nowrap;
      border: 1px solid var(--rule-strong);
      background: var(--well);
      color: var(--ink);
    }

    .seal-badge.pass {
      background: var(--seal-pass-bg);
      color: var(--seal-pass);
      border-color: var(--seal-pass-border);
    }

    .seal-badge.fail {
      background: var(--seal-crimson-bg);
      color: var(--seal-crimson);
      border-color: var(--seal-crimson-border);
    }

    .seal-badge.warn {
      background: var(--seal-amber-bg);
      color: var(--seal-amber);
      border-color: var(--seal-amber-border);
    }

    .seal-badge.cobalt {
      background: var(--cobalt-soft);
      color: var(--cobalt);
      border-color: #93c5fd;
    }

    .action-btn {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 6px 12px;
      font-size: 12px;
      font-weight: 600;
      border-radius: 4px;
      border: 1px solid var(--rule-strong);
      background: var(--folio);
      color: var(--ink);
      cursor: pointer;
      transition: background-color 140ms ease, border-color 140ms ease;
    }
    .action-btn:hover {
      background: var(--well);
      border-color: var(--cobalt);
    }
    .action-btn.primary {
      background: var(--cobalt);
      color: var(--folio);
      border-color: var(--cobalt);
    }
    .action-btn.primary:hover {
      background: var(--cobalt-hover);
    }
    .action-btn.danger {
      background: var(--seal-crimson-bg);
      color: var(--seal-crimson);
      border-color: var(--seal-crimson-border);
    }
    .action-btn.danger:hover {
      background: #fecaca;
    }

    /* 8-Stage Lifecycle Interlocking Rail */
    .lifecycle-rail {
      background: var(--folio);
      border: 1px solid var(--rule-strong);
      border-radius: 6px;
      padding: 16px 24px;
      margin-bottom: 18px;
      box-shadow: 0 2px 6px -2px rgba(20, 24, 31, 0.05);
    }

    .lifecycle-head {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      align-items: baseline;
      gap: 12px;
      margin-bottom: 12px;
    }

    .lifecycle-head h2 {
      margin: 0;
      font-family: var(--serif);
      font-size: 16px;
      font-weight: 700;
      color: var(--ink);
    }

    .lifecycle-meta {
      font-size: 12px;
      color: var(--ink-secondary);
      font-variant-numeric: tabular-nums;
    }

    .stage-bar {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      grid-template-columns: repeat(8, minmax(110px, 1fr));
      gap: 8px;
    }

    @media (max-width: 1080px) {
      .stage-bar {
        grid-template-columns: repeat(4, minmax(0, 1fr));
      }
    }

    @media (max-width: 600px) {
      .stage-bar {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }
    }

    .stage-step {
      padding: 8px 10px;
      border-radius: 4px;
      border: 1px solid var(--rule);
      background: var(--paper);
      color: var(--ink-secondary);
      font-variant-numeric: tabular-nums;
    }

    .stage-step-top {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 4px;
      font-size: 12px;
      font-weight: 700;
      color: var(--ink);
    }

    .stage-step-state {
      font-size: 11px;
      font-weight: 600;
      padding: 2px 6px;
      border-radius: 3px;
      background: var(--well);
      color: var(--ink-secondary);
    }

    .stage-step-role {
      font-size: 11px;
      color: var(--ink-muted);
      margin-top: 2px;
    }

    .stage-step.done {
      background: var(--seal-pass-bg);
      border-color: var(--seal-pass-border);
    }
    .stage-step.done .stage-step-top,
    .stage-step.done .stage-step-role {
      color: var(--seal-pass);
    }
    .stage-step.done .stage-step-state {
      background: var(--seal-pass);
      color: var(--folio);
    }

    .stage-step.active {
      background: var(--cobalt-soft);
      border-color: var(--cobalt);
    }
    .stage-step.active .stage-step-top,
    .stage-step.active .stage-step-role {
      color: var(--cobalt);
    }
    .stage-step.active .stage-step-state {
      background: var(--cobalt);
      color: var(--folio);
    }

    /* Workspace Switcher Navigation */
    .workspace-nav {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      align-items: center;
      gap: 10px;
      padding: 10px 14px;
      background: var(--well);
      border: 1px solid var(--rule-strong);
      border-bottom: none;
      border-radius: 6px 6px 0 0;
    }

    .workspace-tabs {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
    }

    .ws-tab {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 7px 11px;
      font-size: 12px;
      font-weight: 600;
      border-radius: 4px;
      border: 1px solid transparent;
      background: transparent;
      color: var(--ink-secondary);
      cursor: pointer;
      transition: background-color 140ms ease, color 140ms ease, border-color 140ms ease;
    }

    .ws-tab:hover {
      background: var(--folio);
      color: var(--ink);
      border-color: var(--rule);
    }

    .ws-tab[aria-selected="true"] {
      background: var(--cobalt);
      color: var(--folio);
      border-color: var(--cobalt);
    }

    .ws-count {
      font-family: var(--mono);
      font-size: 11px;
      padding: 1px 6px;
      border-radius: 3px;
      background: rgba(20, 24, 31, 0.08);
      font-variant-numeric: tabular-nums;
    }
    .ws-tab[aria-selected="true"] .ws-count {
      background: rgba(252, 250, 245, 0.22);
      color: var(--folio);
    }

    /* Main Folio Canvas */
    .workspace-canvas {
      background: var(--folio);
      border: 1px solid var(--rule-strong);
      border-radius: 0 0 6px 6px;
      padding: clamp(18px, 2vw, 26px);
      box-shadow: 0 2px 10px -2px rgba(20, 24, 31, 0.06);
    }

    .workspace-panel {
      animation: folioReveal 220ms cubic-bezier(0.16, 1, 0.3, 1);
    }
    .workspace-panel[hidden] {
      display: none !important;
    }

    @keyframes folioReveal {
      from { opacity: 0; transform: translateY(4px); }
      to { opacity: 1; transform: translateY(0); }
    }
    @media (prefers-reduced-motion: reduce) {
      .workspace-panel { animation: none; }
    }

    /* Double-Entry Clearinghouse Balance Sheet */
    .clearinghouse-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 420px), 1fr));
      gap: 24px;
      align-items: start;
    }

    .folio-box {
      background: var(--paper);
      border: 1px solid var(--rule-strong);
      border-radius: 5px;
      overflow: hidden;
    }

    .folio-box-header {
      padding: 12px 16px;
      background: var(--well);
      border-bottom: 1px solid var(--rule-strong);
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      align-items: center;
      gap: 8px;
    }

    .folio-box-header h2,
    .folio-box-header h3 {
      margin: 0;
      font-family: var(--serif);
      font-size: 16px;
      font-weight: 700;
      color: var(--ink);
    }

    .folio-box-sub {
      padding: 8px 16px;
      background: var(--folio);
      border-bottom: 1px solid var(--rule);
      font-size: 12px;
      color: var(--ink-secondary);
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      gap: 8px;
    }

    /* Responsive Sticky Tables (modern-web-guidance: responsive-table) */
    .table-wrapper {
      overflow: auto;
      max-inline-size: 100%;
      max-block-size: min(560px, 76dvh);
      container-type: inline-size;
      padding: 12px 16px;
    }

    .ledger-table {
      width: 100%;
      border-collapse: separate;
      border-spacing: 0;
      font-size: 13px;
      font-variant-numeric: tabular-nums;
    }

    .ledger-table caption {
      position: absolute;
      width: 1px;
      height: 1px;
      padding: 0;
      margin: -1px;
      overflow: hidden;
      clip: rect(0, 0, 0, 0);
      white-space: nowrap;
      border: 0;
    }

    .ledger-table thead {
      position: sticky;
      inset-block-start: 0;
      z-index: 2;
    }

    .ledger-table th,
    .ledger-table td {
      padding: 10px 14px;
      text-align: left;
      border-bottom: 1px solid var(--rule);
      vertical-align: top;
    }

    .ledger-table td.mono {
      word-break: break-all;
    }

    .ledger-table thead th {
      background: var(--well);
      color: var(--ink);
      font-weight: 700;
      font-size: 12px;
      border-bottom: 1px solid var(--rule-strong);
    }

    .ledger-table tbody th {
      position: sticky;
      inset-inline-start: 0;
      z-index: 1;
      background: var(--folio);
      font-weight: 600;
      color: var(--ink);
      border-right: 1px solid var(--rule);
    }

    .ledger-table tbody tr:hover td,
    .ledger-table tbody tr:hover th {
      background: var(--paper);
    }

    .ledger-table tbody tr.selected td,
    .ledger-table tbody tr.selected th {
      background: var(--cobalt-soft);
    }

    @container (width < 600px) {
      .ledger-table,
      .ledger-table thead,
      .ledger-table tbody,
      .ledger-table tr,
      .ledger-table th,
      .ledger-table td {
        display: block;
      }
      .ledger-table thead {
        display: none;
      }
      .ledger-table tr {
        margin-block-end: 12px;
        border: 1px solid var(--rule-strong);
        border-radius: 4px;
        background: var(--folio);
      }
      .ledger-table tbody th {
        position: sticky;
        inset-block-start: 0;
        z-index: 2;
        background: var(--well);
        border-right: none;
      }
      .ledger-table td:nth-child(2)::before {
        content: var(--label-2, "Detail") ": ";
        font-weight: 600;
        color: var(--ink-secondary);
      }
      .ledger-table td:nth-child(3)::before {
        content: var(--label-3, "Status") ": ";
        font-weight: 600;
        color: var(--ink-secondary);
      }
      .ledger-table td:nth-child(4)::before {
        content: var(--label-4, "Scope") ": ";
        font-weight: 600;
        color: var(--ink-secondary);
      }
    }

    /* Filter & Search Toolbar */
    .ledger-toolbar {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      align-items: center;
      gap: 12px;
      padding: 12px 16px;
      background: var(--paper);
      border: 1px solid var(--rule);
      border-radius: 5px;
      margin-bottom: 16px;
    }

    .filter-input {
      padding: 7px 12px;
      border: 1px solid var(--rule-strong);
      border-radius: 4px;
      background: var(--folio);
      min-width: 240px;
      font-size: 13px;
    }

    /* Kanban 5-Column Swimlanes (modern-web-guidance: interactions-in-complex-layouts) */
    .kanban-board {
      display: grid;
      grid-template-columns: repeat(5, minmax(240px, 1fr));
      gap: 14px;
      overflow-x: auto;
      padding-bottom: 6px;
    }

    .board-column {
      content-visibility: auto;
      contain-intrinsic-size: auto 260px auto 520px;
      background: var(--paper);
      border: 1px solid var(--rule-strong);
      border-radius: 5px;
      display: flex;
      flex-direction: column;
      max-height: 580px;
    }

    @supports not (content-visibility: auto) {
      .board-column {
        contain: layout style paint;
      }
    }

    .board-column-header {
      padding: 10px 14px;
      background: var(--well);
      border-bottom: 1px solid var(--rule-strong);
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-weight: 700;
      font-size: 13px;
    }

    .board-column-list {
      padding: 10px;
      overflow-y: auto;
      display: flex;
      flex-direction: column;
      gap: 8px;
      flex: 1;
    }

    .ticket-entry {
      background: var(--folio);
      border: 1px solid var(--rule);
      border-radius: 4px;
      padding: 10px 12px;
      font-size: 12px;
      transition: border-color 140ms ease;
    }
    .ticket-entry:hover {
      border-color: var(--cobalt);
    }

    .ticket-entry-top {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 6px;
      margin-bottom: 4px;
    }

    .ticket-id {
      font-family: var(--mono);
      font-weight: 700;
      color: var(--cobalt);
      font-size: 12px;
    }

    .ticket-title {
      color: var(--ink);
      font-weight: 500;
      line-height: 1.4;
      word-break: break-word;
    }

    /* ADR & DAG Governance Forms */
    .governance-desk {
      padding: 16px;
      background: var(--folio);
      border-top: 1px solid var(--rule);
    }

    .form-row {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
      align-items: center;
      margin-top: 10px;
    }

    .status-toast {
      margin-top: 10px;
      padding: 8px 12px;
      border-radius: 4px;
      font-size: 12px;
      font-weight: 600;
      background: var(--cobalt-soft);
      color: var(--cobalt);
      border: 1px solid #93c5fd;
    }
    .status-toast[hidden] {
      display: none !important;
    }
  </style>
</head>
<body>
  <div class="clearinghouse-shell">
    <header class="masthead">
      <div>
        <h1>Agentic SDLC Framework: Mission Control</h1>
        <p class="masthead-sub">
          Double-Entry Clean Architecture Clearinghouse, Causal ADR Lineage &amp; Cryptographic SHA-256 Merkle Audit Ledger
        </p>
      </div>
      <div class="masthead-seals">
        <span class="seal-badge cobalt" id="session-seal">
          ${activeSess ? 'Active Session #' + activeSess.issueId : 'Mainline Standing By'}
        </span>
        <span class="seal-badge ${initialArch.archDiff?.passed !== false ? 'pass' : 'fail'}" id="ring-seal">
          ${initialArch.archDiff?.passed !== false ? 'Rings Balanced: 0 Violations' : 'Ring Violations: ' + initialArch.archDiff.violationsCount}
        </span>
        <span class="seal-badge ${initialAudit.valid ? 'pass' : 'fail'}" id="merkle-seal">
          ${initialAudit.valid ? 'Merkle Chain Verified (' + (initialAudit.recordsCount || 0) + ')' : 'Merkle Verification Failed'}
        </span>
        <button type="button" class="action-btn" onclick="bootWorkbench()" title="Synchronize live ledger from server">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" aria-hidden="true">
            <path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/>
            <path d="M3 3v5h5"/>
            <path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16"/>
            <path d="M16 16h5v5"/>
          </svg>
          Sync Ledger
        </button>
      </div>
    </header>

    <section class="lifecycle-rail" aria-label="8-Stage Lifecycle Progress">
      <div class="lifecycle-head">
        <h2>8-Stage Lifecycle Progress ${activeSess ? '(' + activeSess.branch + ')' : ''}</h2>
        <div class="lifecycle-meta" id="lifecycle-meta-text">
          ${
            activeSess
              ? 'Active Ticket #' + activeSess.issueId + ' &middot; Stage ' + activeSess.stage + ' of 8 &middot; Branch: <span class="mono">' + activeSess.branch + '</span>'
              : 'Zero active feature branches locked &middot; Ready for <span class="mono">/sdlc begin &lt;issue&gt;</span>'
          }
        </div>
      </div>
      <ol class="stage-bar">
        ${stageStepsHtml}
      </ol>
    </section>

    <div class="workspace-nav">
      <div class="workspace-tabs" role="tablist" aria-label="Mission Control Workspaces">
        <button type="button" role="tab" aria-selected="true" id="wstab-clearinghouse" class="ws-tab" onclick="selectWorkspace('clearinghouse')">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" aria-hidden="true">
            <rect x="3" y="3" width="18" height="18" rx="2"/>
            <path d="M12 3v18"/>
            <path d="M3 9h18"/>
          </svg>
          Clearinghouse Balance Sheet
          <span class="ws-count" id="count-rings">${initialArch.modifiedFiles.length}</span>
        </button>
        <button type="button" role="tab" aria-selected="false" id="wstab-lineage" class="ws-tab" onclick="selectWorkspace('lineage')">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" aria-hidden="true">
            <circle cx="6" cy="6" r="3"/>
            <circle cx="18" cy="6" r="3"/>
            <circle cx="12" cy="18" r="3"/>
            <path d="M8.5 7.5 10.5 15.5"/>
            <path d="M15.5 7.5 13.5 15.5"/>
          </svg>
          Causal Decision Lineage &amp; ADRs
          <span class="ws-count" id="count-nodes">${(initialGraph.nodes || []).length}</span>
        </button>
        <button type="button" role="tab" aria-selected="false" id="wstab-timemachine" class="ws-tab" onclick="selectWorkspace('timemachine')">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" aria-hidden="true">
            <circle cx="12" cy="12" r="9"/>
            <polyline points="12 7 12 12 15 15"/>
          </svg>
          Time-Machine &amp; Product DAG
          <span class="ws-count" id="count-waves">${(initialDag.waves || []).length}</span>
        </button>
        <button type="button" role="tab" aria-selected="false" id="wstab-kanban" class="ws-tab" onclick="selectWorkspace('kanban')">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" aria-hidden="true">
            <rect x="3" y="3" width="5" height="18" rx="1"/>
            <rect x="10" y="3" width="5" height="12" rx="1"/>
            <rect x="17" y="3" width="4" height="15" rx="1"/>
          </svg>
          Living Kanban Dashboard
          <span class="ws-count" id="count-kanban">${parsedKanban.matrix.length}</span>
        </button>
        <button type="button" role="tab" aria-selected="false" id="wstab-trace" class="ws-tab" onclick="selectWorkspace('trace')">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" aria-hidden="true">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
            <polyline points="14 2 14 8 20 8"/>
            <line x1="8" y1="13" x2="16" y2="13"/>
            <line x1="8" y1="17" x2="16" y2="17"/>
          </svg>
          Architectural Trace Ledger
          <span class="ws-count" id="count-trace">${parsedTrace.length}</span>
        </button>
      </div>
      <div class="mono" style="font-size: 12px; color: var(--ink-secondary);" id="sse-indicator">
        Live Event Stream Ready
      </div>
    </div>

    <main class="workspace-canvas">
      <!-- WORKSPACE 1: CLEARINGHOUSE BALANCE SHEET (SIGNATURE MOVE) -->
      <section id="wspanel-clearinghouse" class="workspace-panel" role="tabpanel" aria-labelledby="wstab-clearinghouse">
        <div class="clearinghouse-grid">
          <!-- Left Folio: Concentric Ring Boundaries -->
          <div class="folio-box">
            <div class="folio-box-header">
              <h2>Clean Architecture Ring Diff &amp; AST Inspector</h2>
              <span class="seal-badge ${initialArch.archDiff?.passed !== false ? 'pass' : 'fail'}" id="ring-folio-status">
                ${initialArch.archDiff?.passed !== false ? 'Inward Dependency Law: Verified' : 'Boundary Violation Detected'}
              </span>
            </div>
            <div class="folio-box-sub">
              <span>Debit Folio &middot; Concentric Ring Allocation vs. <code class="mono">main</code></span>
              <span class="mono" id="modified-files-summary">${initialArch.modifiedFiles.length} file(s) in working delta</span>
            </div>
            <div tabindex="0" role="region" aria-labelledby="ring-ledger-caption" class="table-wrapper">
              <table class="ledger-table" style="--label-2: 'Layer Policy'; --label-3: 'Delta'; --label-4: 'Modified Paths';">
                <caption id="ring-ledger-caption">Clean Architecture Concentric Ring Allocation</caption>
                <thead>
                  <tr>
                    <th>Concentric Ring</th>
                    <th>Layer Policy</th>
                    <th>Delta</th>
                    <th>Modified File Paths</th>
                  </tr>
                </thead>
                <tbody id="arch-diff-mount"></tbody>
              </table>
            </div>
          </div>

          <!-- Right Folio: Cryptographic Merkle Audit & Mermaid AST Diff -->
          <div class="folio-box">
            <div class="folio-box-header">
              <h2>Cryptographic Merkle Audit &amp; Mermaid AST Schedule</h2>
              <span class="seal-badge ${initialAudit.valid ? 'pass' : 'fail'}" id="merkle-folio-status">
                ${initialAudit.valid ? 'SHA-256 Chain Intact' : 'Chain Integrity Alert'}
              </span>
            </div>
            <div class="folio-box-sub">
              <span>Credit Folio &middot; Stage 7 Gate Receipt &amp; Diagram Node/Edge Delta</span>
              <span class="mono" id="merkle-root-display">Root: ${(initialAudit.merkleRoot || 'GENESIS').slice(0, 16)}...</span>
            </div>
            <div tabindex="0" role="region" aria-labelledby="merkle-ledger-caption" class="table-wrapper">
              <table class="ledger-table" style="--label-2: 'Verification Value'; --label-3: 'Status';">
                <caption id="merkle-ledger-caption">SHA-256 Merkle Audit and Latest Mermaid AST Diff</caption>
                <thead>
                  <tr>
                    <th>Audit Instrument / Diagram</th>
                    <th>Recorded Proof / AST Delta</th>
                    <th>Seal</th>
                  </tr>
                </thead>
                <tbody id="merkle-ast-mount"></tbody>
              </table>
            </div>
          </div>
        </div>
      </section>

      <!-- WORKSPACE 2: CAUSAL DECISION LINEAGE & ADR GOVERNANCE -->
      <section id="wspanel-lineage" class="workspace-panel" role="tabpanel" aria-labelledby="wstab-lineage" hidden>
        <div class="ledger-toolbar">
          <div style="display:flex;flex-wrap:wrap;gap:8px;align-items:center;">
            <label for="lineage-search" style="font-weight:600;font-size:13px;">Filter Causal Graph:</label>
            <input id="lineage-search" type="search" class="filter-input" placeholder="Search CAP-XX, ADR-00X, ticket, or keyword..." oninput="renderLineageTable()" />
          </div>
          <div style="display:flex;flex-wrap:wrap;gap:6px;" role="group" aria-label="Filter by node category">
            <button type="button" class="action-btn primary" data-cat="ALL" onclick="setLineageCategory('ALL')">All Nodes</button>
            <button type="button" class="action-btn" data-cat="CAPABILITY" onclick="setLineageCategory('CAPABILITY')">Capabilities (CAP)</button>
            <button type="button" class="action-btn" data-cat="ADR" onclick="setLineageCategory('ADR')">Decisions (ADR)</button>
            <button type="button" class="action-btn" data-cat="SPEC" onclick="setLineageCategory('SPEC')">Specs</button>
          </div>
        </div>

        <div class="clearinghouse-grid">
          <div class="folio-box">
            <div class="folio-box-header">
              <h2>Causal Decision Lineage &amp; ADR Governance</h2>
              <span class="mono" style="font-size:12px;color:var(--ink-secondary);" id="lineage-visible-count"></span>
            </div>
            <div tabindex="0" role="region" aria-labelledby="decision-graph-caption" class="table-wrapper">
              <table class="ledger-table" style="--label-2: 'Category'; --label-3: 'Specification / Decision Title'; --label-4: 'Status';">
                <caption id="decision-graph-caption">Causal Decision Graph Nodes</caption>
                <thead>
                  <tr>
                    <th>Node ID</th>
                    <th>Category</th>
                    <th>Specification / Decision Title</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody id="decision-graph-mount"></tbody>
              </table>
            </div>
          </div>

          <div class="folio-box">
            <div class="folio-box-header">
              <h3>Causal Inspector &amp; ADR Adjudication Desk</h3>
              <span class="seal-badge cobalt" id="selected-node-badge">Select a Node</span>
            </div>
            <div id="lineage-inspector-body" style="padding:16px;font-size:13px;"></div>
            <div class="governance-desk">
              <div style="font-weight:700;font-size:13px;color:var(--ink);">Adjudicate Architecture Decision Record (<code class="mono">POST /api/adr/decide</code>)</div>
              <div class="form-row">
                <select id="adr-select" class="filter-input" style="min-width:160px;" aria-label="Select ADR to adjudicate"></select>
                <input id="adr-reason" type="text" class="filter-input" placeholder="Adjudication rationale for audit log..." aria-label="ADR adjudication rationale" />
                <button type="button" class="action-btn primary" onclick="submitAdrDecision('approve')">Approve ADR</button>
                <button type="button" class="action-btn danger" onclick="submitAdrDecision('reject')">Reject ADR</button>
              </div>
              <div id="adr-toast" class="status-toast" hidden></div>
            </div>
          </div>
        </div>
      </section>

      <!-- WORKSPACE 3: ARCHITECTURE TIME-MACHINE & PRODUCT.MD DAG -->
      <section id="wspanel-timemachine" class="workspace-panel" role="tabpanel" aria-labelledby="wstab-timemachine" hidden>
        <div class="clearinghouse-grid">
          <div class="folio-box">
            <div class="folio-box-header">
              <h2>Architecture Time-Machine &amp; PRODUCT.md DAG</h2>
              <span class="mono" style="font-size:12px;" id="tm-position-label">Snapshot Scrubber</span>
            </div>
            <div class="folio-box-sub" style="align-items:center;">
              <label for="tm-slider" style="font-weight:600;">Scrub Historical Checkpoints:</label>
              <input id="tm-slider" type="range" min="0" max="0" value="0" style="flex:1;min-width:180px;accent-color:var(--cobalt);cursor:pointer;" />
            </div>
            <div id="tm-snapshot-mount" style="padding:16px;"></div>
          </div>

          <div class="folio-box">
            <div class="folio-box-header">
              <h3>Topological Product DAG Waves (<code class="mono">PRODUCT.md</code>)</h3>
              <span class="seal-badge pass" id="dag-status-badge">DAG Active</span>
            </div>
            <div class="governance-desk" style="border-top:none;border-bottom:1px solid var(--rule);">
              <div style="font-weight:700;font-size:13px;">Wave Execution Controller (<code class="mono">POST /api/dag/control</code>)</div>
              <div class="form-row">
                <input id="dag-reason" type="text" class="filter-input" placeholder="Operator note (e.g., maintenance window, wave sign-off)..." aria-label="DAG control reason" />
                <button type="button" class="action-btn primary" onclick="submitDagControl('resume')">Resume DAG</button>
                <button type="button" class="action-btn" onclick="submitDagControl('advance')">Advance Wave</button>
                <button type="button" class="action-btn danger" onclick="submitDagControl('pause')">Pause DAG</button>
              </div>
              <div id="dag-toast" class="status-toast" hidden></div>
            </div>
            <div tabindex="0" role="region" aria-labelledby="product-dag-caption" class="table-wrapper">
              <table class="ledger-table" style="--label-2: 'Execution Mode'; --label-3: 'Capabilities & Specs';">
                <caption id="product-dag-caption">Topological Product Capability Waves</caption>
                <thead>
                  <tr>
                    <th>Wave</th>
                    <th>Execution Mode</th>
                    <th>Capabilities, Dependencies &amp; Governance Status</th>
                  </tr>
                </thead>
                <tbody id="product-dag-mount"></tbody>
              </table>
            </div>
          </div>
        </div>
      </section>

      <!-- WORKSPACE 4: LIVING KANBAN DASHBOARD -->
      <section id="wspanel-kanban" class="workspace-panel" role="tabpanel" aria-labelledby="wstab-kanban" hidden>
        <div class="ledger-toolbar">
          <div>
            <h2 style="margin:0;font-family:var(--serif);font-size:18px;">Living Kanban Dashboard</h2>
            <p style="margin:2px 0 0 0;font-size:12px;color:var(--ink-secondary);" id="kanban-meta-line"></p>
          </div>
          <div style="display:flex;flex-wrap:wrap;gap:8px;align-items:center;">
            <input id="kanban-search" type="search" class="filter-input" placeholder="Filter tickets by #ID, title, or type..." aria-label="Filter Kanban tickets" oninput="renderKanbanWorkspace()" />
          </div>
        </div>

        <div id="kanban-columns-mount" class="kanban-board"></div>

        <div class="folio-box" style="margin-top:20px;">
          <div class="folio-box-header">
            <h3>End-to-End Ticket Traceability Matrix</h3>
            <span class="mono" style="font-size:12px;" id="kanban-matrix-count"></span>
          </div>
          <div tabindex="0" role="region" aria-labelledby="kanban-matrix-caption" class="table-wrapper">
            <table class="ledger-table" style="--label-2: 'Status'; --label-3: 'Priority'; --label-4: 'Component';">
              <caption id="kanban-matrix-caption">End-to-End Ticket Traceability Matrix</caption>
              <thead>
                <tr>
                  <th>Ticket</th>
                  <th>Title</th>
                  <th>Status</th>
                  <th>Priority</th>
                  <th>Component</th>
                </tr>
              </thead>
              <tbody id="kanban-matrix-mount"></tbody>
            </table>
          </div>
        </div>
      </section>

      <!-- WORKSPACE 5: ARCHITECTURAL TRACE LEDGER -->
      <section id="wspanel-trace" class="workspace-panel" role="tabpanel" aria-labelledby="wstab-trace" hidden>
        <div class="ledger-toolbar">
          <div>
            <h2 style="margin:0;font-family:var(--serif);font-size:18px;">Architectural Trace Ledger</h2>
            <p style="margin:2px 0 0 0;font-size:12px;color:var(--ink-secondary);">
              Immutable audit log of Clean Architecture boundary evaluations, branch checkpoints, and Mermaid component diffs (<code class="mono">docs/ARCH_TRACE.md</code>)
            </p>
          </div>
          <div>
            <input id="trace-search" type="search" class="filter-input" placeholder="Search commit SHA, ticket, branch, or reason..." aria-label="Filter architectural trace ledger" oninput="renderTraceWorkspace()" />
          </div>
        </div>

        <div class="folio-box">
          <div tabindex="0" role="region" aria-labelledby="trace-ledger-caption" class="table-wrapper">
            <table class="ledger-table" style="--label-2: 'Timestamp'; --label-3: 'Branch & Commit'; --label-4: 'Checkpoint Reason';">
              <caption id="trace-ledger-caption">Architectural Traceability and Mutation Ledger</caption>
              <thead>
                <tr>
                  <th>Ticket</th>
                  <th>Timestamp</th>
                  <th>Branch &amp; Commit</th>
                  <th>Checkpoint Reason</th>
                  <th>Diagrams / Components Changed</th>
                  <th>Archlens Seal</th>
                </tr>
              </thead>
              <tbody id="trace-ledger-mount"></tbody>
            </table>
          </div>
        </div>
      </section>
    </main>
  </div>

  <script>
    // Workbench API token: passed once in the URL printed by \`sdlc-engine serve\`.
    const SDLC_TOKEN = new URLSearchParams(location.search).get('token') || '';
    const authFetch = (url, opts = {}) =>
      fetch(url, { ...opts, headers: { ...(opts.headers || {}), 'X-SDLC-Token': SDLC_TOKEN } });
    let STATE = ${initialBootstrap};
    let activeCategory = 'ALL';
    let selectedNodeId = null;

    function escapeHtml(str) {
      return String(str == null ? '' : str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
    }

    function selectWorkspace(ws) {
      const names = ['clearinghouse', 'lineage', 'timemachine', 'kanban', 'trace'];
      for (const name of names) {
        const panel = document.getElementById('wspanel-' + name);
        const tab = document.getElementById('wstab-' + name);
        if (name === ws) {
          panel.hidden = false;
          tab.setAttribute('aria-selected', 'true');
        } else {
          panel.hidden = true;
          tab.setAttribute('aria-selected', 'false');
        }
      }
    }

    function renderClearinghouse() {
      const arch = STATE.arch || {};
      const audit = STATE.audit || {};
      const ringPolicies = {
        'Domain & Core Rules': 'Inner Core (Zero Outward Deps)',
        'Application & Engine Libs': 'Use-Case Orchestration',
        'Adapters, CLI & Skills': 'Interface & Skill Routers',
        'Specs, ADRs & Verification': 'Constitutional & Test Gate'
      };

      const adEl = document.getElementById('arch-diff-mount');
      if (adEl) {
        const entries = Object.entries(arch.rings || {});
        adEl.innerHTML = entries.map(([ringName, files]) => {
          const list = Array.isArray(files) ? files : [];
          const fileRows = list.length > 0
            ? list.map(f => '<div class="mono" style="font-size:12px;">' + escapeHtml(f) + '</div>').join('')
            : '<span style="color:var(--ink-muted);">In Balance &mdash; Zero uncommitted ring modifications</span>';
          return '<tr>' +
            '<th>' + escapeHtml(ringName) + '</th>' +
            '<td style="color:var(--ink-secondary);font-size:12px;">' + escapeHtml(ringPolicies[ringName] || 'Concentric Layer') + '</td>' +
            '<td class="mono" style="font-weight:700;">' + list.length + '</td>' +
            '<td>' + fileRows + '</td>' +
          '</tr>';
        }).join('');
      }

      const maEl = document.getElementById('merkle-ast-mount');
      if (maEl) {
        const snap = arch.latestSnapshot;
        const rows = [];
        rows.push(
          '<tr>' +
            '<th>SHA-256 Merkle Root</th>' +
            '<td class="mono">' + escapeHtml(audit.merkleRoot || 'GENESIS_HASH') + '</td>' +
            '<td><span class="seal-badge ' + (audit.valid !== false ? 'pass' : 'fail') + '">' + (audit.valid !== false ? 'Verified' : 'Invalid') + '</span></td>' +
          '</tr>'
        );
        rows.push(
          '<tr>' +
            '<th>Ledger Chain Depth</th>' +
            '<td class="mono">' + escapeHtml(String(audit.recordsCount || 0)) + ' signed gate receipt(s)</td>' +
            '<td><span class="seal-badge pass">Immutable</span></td>' +
          '</tr>'
        );
        if (snap) {
          rows.push(
            '<tr>' +
              '<th>Latest Checkpoint (' + escapeHtml(snap.ticket || 'HEAD') + ')</th>' +
              '<td>' +
                '<div>' + escapeHtml(snap.reason || 'Architectural verification') + '</div>' +
                '<div class="mono" style="font-size:11px;color:var(--ink-secondary);margin-top:2px;">Branch: ' + escapeHtml(snap.branch || 'main') + ' &middot; Commit: ' + escapeHtml(snap.commitSha || 'HEAD') + '</div>' +
              '</td>' +
              '<td><span class="seal-badge pass">' + escapeHtml(snap.status || 'PASSED') + '</span></td>' +
            '</tr>'
          );
          for (const d of (snap.diagramChanges || [])) {
            rows.push(
              '<tr>' +
                '<th class="mono">' + escapeHtml(d.diagram) + '</th>' +
                '<td class="mono">' + escapeHtml(d.summary) + '</td>' +
                '<td><span class="seal-badge cobalt">AST Delta</span></td>' +
              '</tr>'
            );
          }
        }
        maEl.innerHTML = rows.join('');
      }
    }

    function setLineageCategory(cat) {
      activeCategory = cat;
      const buttons = document.querySelectorAll('[data-cat]');
      buttons.forEach(btn => {
        if (btn.getAttribute('data-cat') === cat) {
          btn.classList.add('primary');
        } else {
          btn.classList.remove('primary');
        }
      });
      renderLineageTable();
    }

    function renderLineageTable() {
      const nodes = STATE.graph?.nodes || [];
      const query = (document.getElementById('lineage-search')?.value || '').toLowerCase().trim();
      const filtered = nodes.filter(n => {
        if (activeCategory !== 'ALL' && String(n.category || '').toUpperCase() !== activeCategory) return false;
        if (!query) return true;
        return (
          String(n.id || '').toLowerCase().includes(query) ||
          String(n.label || '').toLowerCase().includes(query) ||
          String(n.status || '').toLowerCase().includes(query)
        );
      });

      const countEl = document.getElementById('lineage-visible-count');
      if (countEl) countEl.textContent = filtered.length + ' of ' + nodes.length + ' nodes shown';

      const dgEl = document.getElementById('decision-graph-mount');
      if (dgEl) {
        if (filtered.length === 0) {
          dgEl.innerHTML = '<tr><th colspan="4" style="color:var(--ink-muted);">No causal nodes match the current filter.</th></tr>';
        } else {
          dgEl.innerHTML = filtered.map(n => {
            const isSel = selectedNodeId === n.id;
            const statusStr = String(n.status || 'ACTIVE');
            const badgeClass = statusStr.toLowerCase().includes('reject')
              ? 'fail'
              : statusStr.toLowerCase().includes('pending')
                ? 'warn'
                : 'pass';
            return '<tr class="' + (isSel ? 'selected' : '') + '" style="cursor:pointer;" onclick="selectLineageNode(\\'' + escapeHtml(n.id).replace(/'/g, '') + '\\')">' +
              '<th class="mono">' + escapeHtml(n.id) + '</th>' +
              '<td class="mono" style="font-size:12px;">' + escapeHtml(n.category) + '</td>' +
              '<td>' + escapeHtml(n.label) + '</td>' +
              '<td><span class="seal-badge ' + badgeClass + '">' + escapeHtml(statusStr) + '</span></td>' +
            '</tr>';
          }).join('');
        }
      }

      const adrSelect = document.getElementById('adr-select');
      if (adrSelect && adrSelect.options.length === 0) {
        const adrNodes = nodes.filter(n => String(n.id || '').startsWith('ADR-'));
        adrSelect.innerHTML = adrNodes.map(a =>
          '<option value="' + escapeHtml(a.id) + '">' + escapeHtml(a.id + ' (' + (a.status || 'proposed') + ')') + '</option>'
        ).join('');
      }

      if (!selectedNodeId && filtered.length > 0) {
        selectLineageNode(filtered[0].id, false);
      }
    }

    function selectLineageNode(nodeId, rerender = true) {
      selectedNodeId = nodeId;
      if (rerender) renderLineageTable();
      const nodes = STATE.graph?.nodes || [];
      const edges = STATE.graph?.edges || [];
      const node = nodes.find(n => n.id === nodeId);
      const badge = document.getElementById('selected-node-badge');
      const body = document.getElementById('lineage-inspector-body');
      if (!node || !body) return;

      if (badge) badge.textContent = node.id + ' (' + node.category + ')';
      if (String(node.id).startsWith('ADR-')) {
        const sel = document.getElementById('adr-select');
        if (sel) sel.value = node.id;
      }

      const inbound = edges.filter(e => e.to === node.id);
      const outbound = edges.filter(e => e.from === node.id);

      const renderHopList = (list, dir) => {
        if (!list.length) return '<div style="color:var(--ink-muted);font-size:12px;">None recorded</div>';
        return '<div style="display:flex;flex-wrap:wrap;gap:6px;margin-top:4px;">' +
          list.map(e => {
            const targetId = dir === 'in' ? e.from : e.to;
            return '<button type="button" class="action-btn mono" onclick="selectLineageNode(\\'' + escapeHtml(targetId).replace(/'/g, '') + '\\')">' +
              escapeHtml(e.from + ' [' + e.relation + '] -> ' + e.to) +
            '</button>';
          }).join('') +
        '</div>';
      };

      body.innerHTML =
        '<div style="font-family:var(--serif);font-size:17px;font-weight:700;color:var(--ink);">' + escapeHtml(node.id + ': ' + node.label) + '</div>' +
        '<div style="margin-top:6px;font-size:12px;color:var(--ink-secondary);">' +
          'Category: <code class="mono">' + escapeHtml(node.category) + '</code> &middot; Status: <code class="mono">' + escapeHtml(node.status || 'ACTIVE') + '</code>' +
        '</div>' +
        '<div style="margin-top:14px;">' +
          '<div style="font-weight:700;font-size:12px;color:var(--ink-secondary);">Upstream Dependencies / Supersedes:</div>' +
          renderHopList(outbound, 'out') +
        '</div>' +
        '<div style="margin-top:12px;">' +
          '<div style="font-weight:700;font-size:12px;color:var(--ink-secondary);">Downstream Dependents / Governed By:</div>' +
          renderHopList(inbound, 'in') +
        '</div>';
    }

    async function submitAdrDecision(action) {
      const adrId = document.getElementById('adr-select')?.value;
      const reason = document.getElementById('adr-reason')?.value || 'Adjudicated in Mission Control Clearinghouse';
      const toast = document.getElementById('adr-toast');
      if (!adrId) return;
      try {
        const res = await authFetch('/api/adr/decide', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ adrId, action, reviewer: '@mission-control', reason })
        }).then(r => r.json());
        if (toast) {
          toast.hidden = false;
          toast.textContent = res.adr
            ? 'Recorded: ' + res.adr.id + ' is now ' + res.adr.status.toUpperCase()
            : (res.error || 'Updated ADR status');
        }
        await bootWorkbench();
      } catch (err) {
        if (toast) {
          toast.hidden = false;
          toast.textContent = 'Error updating ADR: ' + err.message;
        }
      }
    }

    function renderTimeMachineAndDag() {
      const snaps = STATE.arch?.snapshots || STATE.graph?.archSnapshots || [];
      const slider = document.getElementById('tm-slider');
      const posLabel = document.getElementById('tm-position-label');
      const mount = document.getElementById('tm-snapshot-mount');

      const showSnap = (idx) => {
        const s = snaps[idx];
        if (!s) {
          if (posLabel) posLabel.textContent = '0 / 0 Snapshots';
          if (mount) mount.innerHTML = '<p style="margin:0;color:var(--ink-muted);">No architectural snapshots recorded yet.</p>';
          return;
        }
        if (posLabel) posLabel.textContent = 'Checkpoint ' + (idx + 1) + ' of ' + snaps.length;
        const diagRows = (s.diagramChanges || []).map(d =>
          '<tr><th class="mono">' + escapeHtml(d.diagram) + '</th><td class="mono">' + escapeHtml(d.summary) + '</td></tr>'
        ).join('');
        if (mount) {
          mount.innerHTML =
            '<div style="display:flex;flex-wrap:wrap;justify-content:space-between;gap:8px;padding-bottom:10px;border-bottom:1px solid var(--rule);">' +
              '<div><strong>Ticket:</strong> <code class="mono">' + escapeHtml(s.ticket || 'N/A') + '</code> &middot; <strong>Branch:</strong> <code class="mono">' + escapeHtml(s.branch || 'main') + '</code></div>' +
              '<div><strong>Commit:</strong> <code class="mono">' + escapeHtml(s.commitSha || 'HEAD') + '</code> &middot; <span class="mono">' + escapeHtml((s.timestamp || '').slice(0, 19).replace('T', ' ')) + '</span></div>' +
            '</div>' +
            '<p style="margin:10px 0;color:var(--ink);"><strong>Checkpoint Reason:</strong> ' + escapeHtml(s.reason || 'Architectural verification') + '</p>' +
            (diagRows
              ? '<table class="ledger-table"><thead><tr><th>Diagram</th><th>AST Diff Summary</th></tr></thead><tbody>' + diagRows + '</tbody></table>'
              : '<div class="mono" style="font-size:12px;color:var(--ink-secondary);">Modified Files in Checkpoint: ' + ((s.modifiedFiles || []).length) + '</div>');
        }
      };

      if (slider) {
        slider.max = String(Math.max(0, snaps.length - 1));
        slider.value = String(Math.max(0, snaps.length - 1));
        slider.oninput = () => showSnap(Number(slider.value));
      }
      showSnap(Math.max(0, snaps.length - 1));

      const pdEl = document.getElementById('product-dag-mount');
      const waves = STATE.dag?.waves || [];
      if (pdEl) {
        if (waves.length === 0) {
          pdEl.innerHTML = '<tr><th colspan="3" style="color:var(--ink-muted);">No capability waves parsed from PRODUCT.md.</th></tr>';
        } else {
          pdEl.innerHTML = waves.map(w => {
            const itemsHtml = (w.items || []).map(it =>
              '<div style="margin-bottom:6px;">' +
                '<span class="mono" style="font-weight:700;color:var(--cobalt);">' + escapeHtml(it.id) + '</span> ' +
                '<strong>' + escapeHtml(it.title) + '</strong> ' +
                '<span class="seal-badge pass" style="padding:1px 6px;font-size:11px;">' + escapeHtml(it.status || 'ACTIVE') + '</span>' +
                (it.specPath ? '<div class="mono" style="font-size:11px;color:var(--ink-secondary);">Spec: ' + escapeHtml(it.specPath) + '</div>' : '') +
              '</div>'
            ).join('');
            return '<tr>' +
              '<th class="mono">Wave ' + escapeHtml(String(w.wave)) + '</th>' +
              '<td><span class="seal-badge ' + (w.parallelizable ? 'cobalt' : 'warn') + '">' + (w.parallelizable ? 'Parallel Fork-Join' : 'Sequential Gate') + '</span></td>' +
              '<td>' + itemsHtml + '</td>' +
            '</tr>';
          }).join('');
        }
      }
    }

    async function submitDagControl(action) {
      const reason = document.getElementById('dag-reason')?.value || 'Operator action from Mission Control';
      const toast = document.getElementById('dag-toast');
      const badge = document.getElementById('dag-status-badge');
      try {
        const res = await authFetch('/api/dag/control', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ action, reason })
        }).then(r => r.json());
        const newStatus = res.state?.status || action;
        if (badge) {
          badge.textContent = 'DAG ' + String(newStatus).toUpperCase();
          badge.className = 'seal-badge ' + (newStatus === 'paused' ? 'warn' : 'pass');
        }
        if (toast) {
          toast.hidden = false;
          toast.textContent = 'DAG state updated to ' + String(newStatus).toUpperCase();
        }
      } catch (err) {
        if (toast) {
          toast.hidden = false;
          toast.textContent = 'Failed to update DAG: ' + err.message;
        }
      }
    }

    function renderKanbanWorkspace() {
      const kb = STATE.kanban || { columns: [], matrix: [] };
      const query = (document.getElementById('kanban-search')?.value || '').toLowerCase().trim();
      const metaEl = document.getElementById('kanban-meta-line');
      if (metaEl) {
        metaEl.textContent = (kb.repo ? 'Repository: ' + kb.repo + ' · ' : '') +
          (kb.lastSynced ? 'Last Synced: ' + kb.lastSynced : 'Synchronized with docs/KANBAN.md');
      }

      const colMount = document.getElementById('kanban-columns-mount');
      if (colMount) {
        colMount.innerHTML = (kb.columns || []).map(col => {
          const items = (col.items || []).filter(it =>
            !query ||
            String(it.id || '').toLowerCase().includes(query) ||
            String(it.title || '').toLowerCase().includes(query)
          );
          const cardsHtml = items.length > 0
            ? items.map(it =>
                '<article class="ticket-entry">' +
                  '<div class="ticket-entry-top">' +
                    (it.url
                      ? '<a href="' + escapeHtml(it.url) + '" target="_blank" rel="noopener noreferrer" class="ticket-id">' + escapeHtml(it.id) + '</a>'
                      : '<span class="ticket-id">' + escapeHtml(it.id) + '</span>') +
                  '</div>' +
                  '<div class="ticket-title">' + escapeHtml(it.title) + '</div>' +
                '</article>'
              ).join('')
            : '<div style="color:var(--ink-muted);font-size:12px;padding:8px 4px;">No tickets in column</div>';
          return '<div class="board-column">' +
            '<div class="board-column-header">' +
              '<span>' + escapeHtml(col.label) + '</span>' +
              '<span class="ws-count">' + items.length + '</span>' +
            '</div>' +
            '<div class="board-column-list">' + cardsHtml + '</div>' +
          '</div>';
        }).join('');
      }

      const matrix = (kb.matrix || []).filter(row =>
        !query ||
        String(row.issue?.id || '').toLowerCase().includes(query) ||
        String(row.issue?.title || '').toLowerCase().includes(query) ||
        String(row.status || '').toLowerCase().includes(query)
      );
      const countEl = document.getElementById('kanban-matrix-count');
      if (countEl) countEl.textContent = matrix.length + ' tracked ticket(s)';

      const matMount = document.getElementById('kanban-matrix-mount');
      if (matMount) {
        if (matrix.length === 0) {
          matMount.innerHTML = '<tr><th colspan="5" style="color:var(--ink-muted);">No traceability matrix rows match filter.</th></tr>';
        } else {
          matMount.innerHTML = matrix.map(r => {
            const linkHtml = r.issue?.url
              ? '<a href="' + escapeHtml(r.issue.url) + '" target="_blank" rel="noopener noreferrer" class="mono">' + escapeHtml(r.issue.id) + '</a>'
              : '<span class="mono">' + escapeHtml(r.issue?.id || '—') + '</span>';
            return '<tr>' +
              '<th>' + linkHtml + '</th>' +
              '<td>' + escapeHtml(r.issue?.title || '—') + '</td>' +
              '<td><span class="seal-badge pass">' + escapeHtml(r.status) + '</span></td>' +
              '<td class="mono">' + escapeHtml(r.priority) + '</td>' +
              '<td>' + escapeHtml(r.component) + '</td>' +
            '</tr>';
          }).join('');
        }
      }
    }

    function renderTraceWorkspace() {
      const entries = STATE.trace || [];
      const query = (document.getElementById('trace-search')?.value || '').toLowerCase().trim();
      const filtered = entries.filter(e =>
        !query ||
        String(e.ticket?.id || '').toLowerCase().includes(query) ||
        String(e.branch || '').toLowerCase().includes(query) ||
        String(e.commit || '').toLowerCase().includes(query) ||
        String(e.reason || '').toLowerCase().includes(query)
      );

      const mount = document.getElementById('trace-ledger-mount');
      if (!mount) return;
      if (filtered.length === 0) {
        mount.innerHTML = '<tr><th colspan="6" style="color:var(--ink-muted);">No architectural trace records match the current filter.</th></tr>';
        return;
      }
      mount.innerHTML = filtered.map(e => {
        const ticketHtml = e.ticket?.url
          ? '<a href="' + escapeHtml(e.ticket.url) + '" target="_blank" rel="noopener noreferrer" class="mono">' + escapeHtml(e.ticket.id) + '</a>'
          : '<span class="mono">' + escapeHtml(e.ticket?.id || '—') + '</span>';
        const changesHtml = (e.changes || []).map(c => '<div class="mono" style="font-size:11px;">' + escapeHtml(c) + '</div>').join('');
        return '<tr>' +
          '<th>' + ticketHtml + '</th>' +
          '<td class="mono" style="white-space:nowrap;">' + escapeHtml(e.timestamp) + '</td>' +
          '<td class="mono">' +
            '<div>' + escapeHtml(e.branch) + '</div>' +
            '<div style="font-size:11px;color:var(--ink-secondary);">SHA: ' + escapeHtml(e.commit) + '</div>' +
          '</td>' +
          '<td>' + escapeHtml(e.reason) + '</td>' +
          '<td>' + changesHtml + '</td>' +
          '<td><span class="seal-badge pass">' + escapeHtml(e.status || 'PASSED') + '</span></td>' +
        '</tr>';
      }).join('');
    }

    async function bootWorkbench() {
      try {
        const [arch, graph, prod, statusRes] = await Promise.all([
          authFetch('/api/arch-diff').then(r => r.json()),
          authFetch('/api/decision-graph').then(r => r.json()),
          authFetch('/api/product').then(r => r.json()),
          authFetch('/api/status').then(r => r.json())
        ]);
        STATE.arch = arch;
        STATE.graph = graph;
        STATE.product = prod.product;
        STATE.dag = prod.dag;
        if (statusRes.audit) STATE.audit = statusRes.audit;
      } catch (e) {}

      renderClearinghouse();
      renderLineageTable();
      renderTimeMachineAndDag();
      renderKanbanWorkspace();
      renderTraceWorkspace();
    }

    bootWorkbench();
    try {
      const es = new EventSource('/api/events?token=' + encodeURIComponent(SDLC_TOKEN));
      es.addEventListener('connected', () => {
        const el = document.getElementById('sse-indicator');
        if (el) el.textContent = 'SSE Stream Connected';
      });
      es.addEventListener('adr-updated', bootWorkbench);
      es.addEventListener('dag-updated', bootWorkbench);
      es.addEventListener('arch-updated', bootWorkbench);
    } catch (e) {}
  </script>
</body>
</html>`;
}

/**
 * The workbench is no longer auto-spawned as a detached daemon: a background
 * process would print its access token nowhere and outlive the session.
 * Callers get the command to run instead.
 */
export function ensureWorkbenchRunning({
  port = 4120,
  repoRoot = '.',
  spawn: shouldSpawn = false,
  spawnFn = spawn
} = {}) {
  const scriptPath = path.resolve(repoRoot, '.agent/scripts/sdlc-engine.mjs');
  if (shouldSpawn && fileExists(scriptPath)) {
    try {
      const child = spawnFn(process.execPath, [scriptPath, 'serve', '--port', String(port)], {
        cwd: repoRoot,
        detached: true,
        stdio: 'ignore'
      });
      if (child && typeof child.unref === 'function') {
        child.unref();
      }
      return {
        started: true,
        port,
        url: `http://127.0.0.1:${port}`,
        command: `node .agent/scripts/sdlc-engine.mjs serve --port ${port}`,
        stdio: 'ignore',
        unrefed: true
      };
    } catch {}
  }
  return {
    started: false,
    port,
    url: `http://127.0.0.1:${port}`,
    command: `node .agent/scripts/sdlc-engine.mjs serve --port ${port}`,
    stdio: 'ignore',
    unrefed: true,
    reason: 'auto-spawn-disabled'
  };
}

export function createWorkbenchServer({
  port = 4120,
  host = '127.0.0.1',
  token = null,
  repoRoot = '.',
  getSessionStatusFn = getSessionStatus
} = {}) {
  const sseClients = new Set();
  const accessToken = token || crypto.randomBytes(24).toString('hex');
  const tokenBuf = Buffer.from(accessToken, 'utf8');

  const broadcast = (eventType, payload) => {
    const msg = `event: ${eventType}\ndata: ${JSON.stringify(payload)}\n\n`;
    for (const res of sseClients) {
      try {
        res.write(msg);
      } catch {
        sseClients.delete(res);
      }
    }
  };

  const tokenMatches = (candidate) => {
    if (typeof candidate !== 'string' || candidate.length === 0) return false;
    const buf = Buffer.from(candidate, 'utf8');
    return buf.length === tokenBuf.length && crypto.timingSafeEqual(buf, tokenBuf);
  };

  const server = http.createServer(async (req, res) => {
    const actualPort = server.address()?.port || port;
    const allowedOrigins = new Set([`http://127.0.0.1:${actualPort}`, `http://localhost:${actualPort}`]);
    const allowedHosts = new Set([`127.0.0.1:${actualPort}`, `localhost:${actualPort}`]);
    res.setHeader('Vary', 'Origin');

    // Reject DNS-rebinding (foreign Host) and cross-origin browser requests.
    const origin = req.headers.origin;
    if (!allowedHosts.has(String(req.headers.host || '')) || (origin && !allowedOrigins.has(origin))) {
      res.writeHead(403, { 'Content-Type': 'text/plain' });
      res.end('Forbidden: workbench only accepts same-origin requests on loopback.');
      return;
    }
    if (origin) {
      res.setHeader('Access-Control-Allow-Origin', origin);
      res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
      res.setHeader('Access-Control-Allow-Headers', 'Content-Type, X-SDLC-Token');
    }

    if (req.method === 'OPTIONS') {
      res.writeHead(204);
      res.end();
      return;
    }

    const queryToken = new URL(req.url || '/', 'http://127.0.0.1').searchParams.get('token');
    if (!tokenMatches(req.headers['x-sdlc-token']) && !tokenMatches(queryToken)) {
      res.writeHead(401, { 'Content-Type': 'text/plain' });
      res.end('Unauthorized: open the URL printed by `sdlc-engine serve` (it carries ?token=...).');
      return;
    }

    const urlObj = new URL(req.url || '/', 'http://localhost');
    const pathname = urlObj.pathname;

    // 1. Live Server-Sent Events (SSE) Stream
    if (pathname === '/api/events') {
      res.writeHead(200, {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
        Connection: 'keep-alive'
      });
      res.write(`event: connected\ndata: ${JSON.stringify({ timestamp: new Date().toISOString() })}\n\n`);
      sseClients.add(res);
      req.on('close', () => sseClients.delete(res));
      return;
    }

    // 2. Session & Audit Status API
    if (pathname === '/api/status') {
      const status = typeof getSessionStatusFn === 'function' ? getSessionStatusFn(repoRoot) : { active: false };
      const audit = verifyAuditChain(repoRoot);
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ ...status, audit }));
      return;
    }

    // 3. Architecture Ring Diff & Mermaid AST Snapshot API
    if (pathname === '/api/arch-diff') {
      const archPayload = buildArchDiffPayload(repoRoot);
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(archPayload));
      return;
    }

    // 4. Causal Decision Graph & Architecture Time-Slider API
    if (pathname === '/api/decision-graph') {
      const graph = buildDecisionGraph(repoRoot);
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(graph));
      return;
    }

    // 5. PRODUCT.md & Topological DAG API
    if (pathname === '/api/product') {
      const product = parseProductMarkdown(repoRoot);
      const dag = planProductDAG({ repoRoot, autoProvision: false });
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ product, dag }));
      return;
    }

    // 6. ADR List API
    if (pathname === '/api/adr' && req.method === 'GET') {
      const adrs = listADRs(repoRoot);
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ adrs }));
      return;
    }

    // 7. Bidirectional Action: Approve / Reject Pending Product ADR
    if (pathname === '/api/adr/decide' && req.method === 'POST') {
      try {
        const body = await readJsonBody(req);
        const decided = decideADR({
          adrId: body.adrId,
          action: body.action || 'approve',
          reviewer: body.reviewer || '@workbench-operator',
          reason: body.reason || 'Decided in SvelteKit Workbench',
          repoRoot
        });
        broadcast('adr-updated', decided);
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify(decided));
      } catch (err) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: false, error: err.message }));
      }
      return;
    }

    // 8. Bidirectional Action: Pause / Resume / Advance Product DAG
    if (pathname === '/api/dag/control' && req.method === 'POST') {
      const body = await readJsonBody(req);
      const ctl = controlProductDAG({
        action: body.action || 'status',
        reason: body.reason || '',
        wave: body.wave || null,
        repoRoot
      });
      broadcast('dag-updated', ctl);
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(ctl));
      return;
    }

    // 9. Interactive Generative UI Widget Endpoint (/widget)
    if (pathname === '/widget') {
      const generated = generateArchitectureWidget({ repoRoot });
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
      res.end(readFileSafe(generated.defaultWidgetPath) || '');
      return;
    }

    // 10. Root HTML (Mission Control Workbench)
    if (pathname === '/' || pathname === '/index.html') {
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
      res.end(renderWorkbenchHTML(repoRoot, getSessionStatusFn));
      return;
    }

    res.writeHead(404, { 'Content-Type': 'text/plain' });
    res.end('Not Found');
  });

  return {
    server,
    port,
    host,
    token: accessToken,
    broadcast,
    url: () => `http://127.0.0.1:${server.address()?.port || port}/?token=${accessToken}`,
    listen: () => {
      return new Promise((resolve, reject) => {
        server.listen(port, host, () => {
          const actualPort = server.address()?.port || port;
          console.log(`Agentic SDLC Workbench (loopback only) live at: http://127.0.0.1:${actualPort}/?token=${accessToken}`);
          resolve(server);
        });
        server.on('error', reject);
      });
    },
    close: () => {
      for (const client of sseClients) {
        try {
          client.end();
        } catch {}
      }
      sseClients.clear();
      return new Promise((resolve) => server.close(resolve));
    }
  };
}
