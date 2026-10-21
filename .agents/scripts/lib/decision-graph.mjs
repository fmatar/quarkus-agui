import fs from 'node:fs';
import path from 'node:path';
import { fileExists, readFileSafe } from './core.mjs';

export const DECISION_GRAPH_FILE = '.agent/decision-graph.jsonl';
export const DECISION_MAP_MD = 'docs/DECISION_MAP.md';

export function appendDecisionEvent({
  type = 'DECISION_NODE',
  id,
  label = '',
  category = 'ARCH',
  status = 'active',
  ticketId = null,
  linksTo = [],
  metadata = {},
  repoRoot = '.'
} = {}) {
  const graphPath = path.resolve(repoRoot, DECISION_GRAPH_FILE);
  fs.mkdirSync(path.dirname(graphPath), { recursive: true });

  const event = {
    eventId: `evt-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
    timestamp: new Date().toISOString(),
    type,
    id: id || `NODE-${Date.now()}`,
    label: label || id,
    category,
    status,
    ticketId: ticketId ? Number(ticketId) || ticketId : null,
    linksTo: Array.isArray(linksTo) ? linksTo : [],
    metadata
  };

  fs.appendFileSync(graphPath, JSON.stringify(event) + '\n', 'utf8');
  return { success: true, event, graphPath };
}

export function buildDecisionGraph(repoRoot = '.') {
  const nodesMap = new Map();
  const edges = [];
  const timeline = [];
  const driftPivots = [];

  const ensureNode = (id, defaults = {}) => {
    if (!id) return null;
    if (!nodesMap.has(id)) {
      let category = defaults.category || 'GENERIC';
      if (id.startsWith('CAP-')) category = 'CAPABILITY';
      else if (id.startsWith('INV-')) category = 'INVARIANT';
      else if (id.startsWith('SPEC-')) category = 'SPEC';
      else if (id.startsWith('ADR-')) category = 'ADR';
      else if (id.startsWith('ARCH-')) category = 'ARCH';
      else if (id.startsWith('#') || id.startsWith('TICKET-')) category = 'TICKET';

      nodesMap.set(id, {
        id,
        label: defaults.label || id,
        category,
        status: defaults.status || 'active',
        ticketId: defaults.ticketId || null,
        timestamp: defaults.timestamp || new Date().toISOString(),
        metadata: defaults.metadata || {}
      });
    } else if (defaults.label || defaults.status || defaults.metadata) {
      const existing = nodesMap.get(id);
      if (defaults.label && defaults.label !== id) existing.label = defaults.label;
      if (defaults.status) existing.status = defaults.status;
      if (defaults.metadata) existing.metadata = { ...existing.metadata, ...defaults.metadata };
    }
    return nodesMap.get(id);
  };

  const addEdge = (from, to, relation = 'leads_to') => {
    if (!from || !to || from === to) return;
    ensureNode(from);
    ensureNode(to);
    const exists = edges.some((e) => e.from === from && e.to === to && e.relation === relation);
    if (!exists) {
      edges.push({ from, to, relation });
    }
  };

  // 1. Hydrate from PRODUCT.md if present
  const productPath = path.resolve(repoRoot, 'PRODUCT.md');
  if (fileExists(productPath)) {
    const raw = readFileSafe(productPath) || '';
    const lines = raw.split('\n');
    for (const line of lines) {
      const capMatch = line.match(/^\|\s*(CAP-\d+)\s*\|\s*([^|]+)\|\s*([^|]+)\|\s*([^|]+)\|\s*([^|]+)\|/);
      if (capMatch) {
        const capId = capMatch[1].trim();
        const title = capMatch[2].trim();
        const specCell = capMatch[3].trim();
        const depsCell = capMatch[4].trim();
        const statusCell = capMatch[5].trim();

        ensureNode(capId, {
          label: `${capId}: ${title}`,
          category: 'CAPABILITY',
          status: statusCell.replace(/`/g, '')
        });

        if (specCell && specCell !== 'N/A' && specCell !== '-') {
          const specId = `SPEC-${capId.split('-')[1]}`;
          ensureNode(specId, {
            label: `${specId} (${specCell.replace(/`/g, '')})`,
            category: 'SPEC',
            metadata: { path: specCell.replace(/`/g, '') }
          });
          addEdge(capId, specId, 'specified_by');
        }

        if (depsCell && depsCell !== 'None' && depsCell !== '-') {
          for (const dep of depsCell.split(',').map((s) => s.trim()).filter(Boolean)) {
            addEdge(dep, capId, 'depends_on');
          }
        }
      }

      const invMatch = line.match(/^-\s*\*\*(INV-\d+)\*\*:\s*(.+)$/);
      if (invMatch) {
        ensureNode(invMatch[1].trim(), {
          label: `${invMatch[1].trim()}: ${invMatch[2].trim()}`,
          category: 'INVARIANT',
          status: 'enforced'
        });
      }
    }
  }

  // 2. Hydrate from docs/adr/*.md
  const adrDir = path.resolve(repoRoot, 'docs/adr');
  if (fileExists(adrDir)) {
    try {
      const files = fs.readdirSync(adrDir).filter((f) => f.startsWith('ADR-') && f.endsWith('.md')).sort();
      for (const file of files) {
        const full = path.join(adrDir, file);
        const content = readFileSafe(full) || '';
        const idMatch = file.match(/^(ADR-\d+)/);
        const adrId = idMatch ? idMatch[1] : file.replace(/\.md$/, '');
        const titleMatch = content.match(/^title:\s*(.+)$/m) || content.match(/^#\s+(.+)$/m);
        const statusMatch = content.match(/^status:\s*(.+)$/m);
        const scopeMatch = content.match(/^scope:\s*(.+)$/m);
        const ticketMatch = content.match(/^ticket:\s*#?(\d+)/m);
        const supersedesMatch = content.match(/^supersedes:\s*(.+)$/m);
        const createdMatch = content.match(/^createdAt:\s*(.+)$/m);

        const title = titleMatch ? titleMatch[1].trim() : adrId;
        const status = statusMatch ? statusMatch[1].trim() : 'accepted';
        const scope = scopeMatch ? scopeMatch[1].trim() : 'technical';
        const ticketId = ticketMatch ? Number(ticketMatch[1]) : null;
        const supersedes = supersedesMatch
          ? supersedesMatch[1].split(',').map((s) => s.trim()).filter(Boolean)
          : [];

        ensureNode(adrId, {
          label: `${adrId}: ${title}`,
          category: 'ADR',
          status,
          ticketId,
          timestamp: createdMatch ? createdMatch[1].trim() : new Date().toISOString(),
          metadata: { file: `docs/adr/${file}`, scope, supersedes, content }
        });

        driftPivots.push({
          id: adrId,
          title,
          scope,
          status,
          ticketId,
          supersedes,
          file: `docs/adr/${file}`
        });

        if (ticketId) {
          const tNodeId = `#${ticketId}`;
          ensureNode(tNodeId, { label: `Ticket #${ticketId}`, category: 'TICKET', ticketId });
          addEdge(tNodeId, adrId, 'triggered_drift_adr');
        }

        for (const sup of supersedes) {
          addEdge(sup, adrId, 'amended_by_adr');
        }
      }
    } catch {}
  }

  // 3. Hydrate from .agent/decision-graph.jsonl
  const graphPath = path.resolve(repoRoot, DECISION_GRAPH_FILE);
  if (fileExists(graphPath)) {
    const lines = (readFileSafe(graphPath) || '').split('\n').filter(Boolean);
    for (const line of lines) {
      try {
        const evt = JSON.parse(line);
        timeline.push(evt);
        ensureNode(evt.id, {
          label: evt.label || evt.id,
          category: evt.category || (evt.id.startsWith('ARCH-') ? 'ARCH' : 'GENERIC'),
          status: evt.status || 'active',
          ticketId: evt.ticketId,
          timestamp: evt.timestamp,
          metadata: evt.metadata || {}
        });

        for (const target of evt.linksTo || []) {
          addEdge(target, evt.id, evt.type === 'ADR_CREATED' ? 'amended_by_adr' : 'realized_by');
        }
      } catch {}
    }
  }

  // 4. Hydrate from .agent/arch-trace.jsonl for Architecture Time-Machine
  const archTracePath = path.resolve(repoRoot, '.agent/arch-trace.jsonl');
  const archSnapshots = [];
  if (fileExists(archTracePath)) {
    const lines = (readFileSafe(archTracePath) || '').split('\n').filter(Boolean);
    for (const line of lines) {
      try {
        const entry = JSON.parse(line);
        archSnapshots.push(entry);
        if (entry.ticket && entry.ticket !== '#N/A') {
          ensureNode(entry.ticket, {
            label: `Ticket ${entry.ticket} (${entry.branch})`,
            category: 'TICKET',
            status: entry.status
          });
        }
      } catch {}
    }
  }

  const nodes = Array.from(nodesMap.values());
  return {
    nodes,
    edges,
    driftPivots,
    timeline,
    archSnapshots,
    stats: {
      totalNodes: nodes.length,
      totalEdges: edges.length,
      capabilitiesCount: nodes.filter((n) => n.category === 'CAPABILITY').length,
      adrsCount: driftPivots.length,
      pendingAdrsCount: driftPivots.filter((a) => a.status === 'pending_approval').length
    }
  };
}

export function syncDecisionMapMarkdown(repoRoot = '.') {
  const graph = buildDecisionGraph(repoRoot);
  const mapPath = path.resolve(repoRoot, DECISION_MAP_MD);
  fs.mkdirSync(path.dirname(mapPath), { recursive: true });

  const sanitizeId = (id) => String(id).replace(/[^a-zA-Z0-9_]/g, '_');
  const mermaidLines = ['flowchart TD'];

  for (const node of graph.nodes) {
    const safeId = sanitizeId(node.id);
    const cleanLabel = String(node.label || node.id).replace(/"/g, "'");
    mermaidLines.push(`  ${safeId}["${cleanLabel}"]`);
  }

  for (const edge of graph.edges) {
    const fromId = sanitizeId(edge.from);
    const toId = sanitizeId(edge.to);
    const arrow = edge.relation === 'amended_by_adr' ? '-.->|ADR Drift|' : `-->|${edge.relation}|`;
    mermaidLines.push(`  ${fromId} ${arrow} ${toId}`);
  }

  const adrRows =
    graph.driftPivots.length > 0
      ? graph.driftPivots
          .map(
            (a) =>
              `| **${a.id}** | ${a.title} | \`${a.scope}\` | \`${a.status}\` | ${a.ticketId ? '#' + a.ticketId : 'N/A'} | ${a.supersedes.join(', ') || 'None'} |`
          )
          .join('\n')
      : '| *None* | No architectural drift ADRs recorded yet | - | - | - | - |';

  const capNodes = graph.nodes.filter((n) => n.category === 'CAPABILITY');
  const capRows =
    capNodes.length > 0
      ? capNodes.map((c) => `| **${c.id}** | ${c.label.replace(/^CAP-\d+:\s*/, '')} | \`${c.status}\` |`).join('\n')
      : '| *None* | Run `sdlc-engine product init` to initialize `PRODUCT.md` | - |';

  const markdown = `# 🗺️ Causal Decision Graph & Architecture Lineage (\`DECISION_MAP.md\`)

> **Auto-Generated by Agentic SDLC Engine**: Tracks how product capabilities (\`PRODUCT.md\`), specifications, tickets, and Spec-Drift ADRs evolved into the current system state.

---

## 🧬 Causal Decision Lineage Graph

\`\`\`mermaid
${mermaidLines.join('\n')}
\`\`\`

---

## ⚡ Spec Drift & ADR Pivot Ledger

| ADR ID | Decision Title | Scope | Status | Triggered By | Supersedes / Amends |
| :--- | :--- | :--- | :--- | :--- | :--- |
${adrRows}

---

## 🎯 Product Capability Realization Matrix

| Capability ID | Description | Current Status |
| :--- | :--- | :--- |
${capRows}
`;

  fs.writeFileSync(mapPath, markdown, 'utf8');

  return {
    success: true,
    path: mapPath,
    relativePath: DECISION_MAP_MD,
    graph
  };
}
