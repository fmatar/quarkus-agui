import fs from 'node:fs';
import path from 'node:path';
import { execSync } from 'node:child_process';
import { fileExists, readFileSafe, inspectArchlens, getModifiedFiles } from './core.mjs';
import { getSessionStatus } from './session.mjs';

export function checkArchDiff(repoRoot = '.') {
  console.log('🏛️ Auditing Git Diff for Architectural Ring Layer Inversions...');
  const archInfo = inspectArchlens(repoRoot);
  const diffFiles = getModifiedFiles('main', repoRoot);

  if (!archInfo.installed) {
    return {
      enabled: false,
      passed: true,
      violationsCount: 0,
      message: 'Archlens policy not installed (.archlens/policy.json missing).',
      violations: []
    };
  }

  const violations = [];
  const addedImportRegex =
    /^\+\s*(import\s+.*from\s+['"]([^'"]+)['"]|import\s+['"]([^'"]+)['"]|import\s+([a-zA-Z0-9_.]+);)/;

  for (const file of diffFiles) {
    try {
      const diffOutput = execSync(`git diff HEAD~1 -- "${file}" 2>/dev/null || git diff -- "${file}" 2>/dev/null || true`, {
        cwd: repoRoot,
        encoding: 'utf8'
      });

      const lines = diffOutput.split('\n');
      for (const line of lines) {
        if (!line.startsWith('+') || line.startsWith('+++')) continue;
        const match = line.match(addedImportRegex);
        if (match) {
          const importedPath = match[2] || match[3] || match[4] || '';
          const isDomainFile =
            file.toLowerCase().includes('domain') ||
            file.toLowerCase().includes('entity') ||
            file.toLowerCase().includes('entities');
          const importsOuter =
            importedPath.toLowerCase().includes('controller') ||
            importedPath.toLowerCase().includes('resource') ||
            importedPath.toLowerCase().includes('infra') ||
            importedPath.toLowerCase().includes('database') ||
            importedPath.toLowerCase().includes('web');

          if (isDomainFile && importsOuter) {
            violations.push({
              file,
              line: line.trim(),
              imported: importedPath,
              rule: 'Concentric Layer Inversion: Core Domain entity directly importing outer ring layer'
            });
          }
        }
      }
    } catch {}
  }

  const passed = violations.length === 0;
  if (passed) {
    console.log('✅ Archlens Diff Check: Zero architectural layer inversions introduced in diff.');
  } else {
    console.warn(`⚠️ Archlens Diff Check: ${violations.length} layer inversion(s) detected!`);
    for (const v of violations) {
      console.warn(`   • [${v.file}] ${v.rule} -> "${v.imported}"`);
    }
  }

  return {
    enabled: true,
    passed,
    violationsCount: violations.length,
    violations
  };
}

export function parseMermaidDiagram(content) {
  if (!content || typeof content !== 'string') return { nodes: [], edges: [], subgraphs: [] };

  const nodes = new Map();
  const edges = [];
  const subgraphs = [];

  const lines = content.split('\n');
  let currentSubgraph = null;

  for (const rawLine of lines) {
    const line = rawLine.trim();
    if (!line || line.startsWith('%%') || line.startsWith('flowchart') || line.startsWith('graph')) {
      continue;
    }

    const subMatch = line.match(/^subgraph\s+([A-Za-z0-9_]+)(\s*\["?([^"\]]+)"?\])?/i);
    if (subMatch) {
      currentSubgraph = { id: subMatch[1], label: subMatch[3] || subMatch[1] };
      subgraphs.push(currentSubgraph);
      continue;
    }
    if (line === 'end') {
      currentSubgraph = null;
      continue;
    }

    const edgeRegex =
      /([A-Za-z0-9_\-\.]+)(?:\["?([^"\]]+)"?\])?\s*(-->|-.->|==>)(?:\|([^|]+)\|)?\s*([A-Za-z0-9_\-\.]+)(?:\["?([^"\]]+)"?\])?/;
    const edgeMatch = line.match(edgeRegex);
    if (edgeMatch) {
      const fromId = edgeMatch[1];
      const fromLabel = edgeMatch[2] || fromId;
      const arrow = edgeMatch[3];
      const edgeLabel = edgeMatch[4] ? edgeMatch[4].trim() : '';
      const toId = edgeMatch[5];
      const toLabel = edgeMatch[6] || toId;

      if (!nodes.has(fromId)) {
        nodes.set(fromId, { id: fromId, label: fromLabel, subgraph: currentSubgraph?.id || null });
      }
      if (!nodes.has(toId)) {
        nodes.set(toId, { id: toId, label: toLabel, subgraph: currentSubgraph?.id || null });
      }

      edges.push({
        from: fromId,
        to: toId,
        type: arrow,
        label: edgeLabel
      });
      continue;
    }

    const nodeRegex = /^([A-Za-z0-9_\-\.]+)\["?([^"\]]+)"?\]/;
    const nodeMatch = line.match(nodeRegex);
    if (nodeMatch) {
      const id = nodeMatch[1];
      const label = nodeMatch[2] || id;
      if (!nodes.has(id)) {
        nodes.set(id, { id, label, subgraph: currentSubgraph?.id || null });
      }
    }
  }

  return {
    nodes: Array.from(nodes.values()),
    edges,
    subgraphs
  };
}

export function diffMermaidGraphs(oldGraph, newGraph) {
  const oldNodeIds = new Set(oldGraph.nodes.map((n) => n.id));
  const newNodeIds = new Set(newGraph.nodes.map((n) => n.id));

  const addedNodes = newGraph.nodes.filter((n) => !oldNodeIds.has(n.id));
  const removedNodes = oldGraph.nodes.filter((n) => !newNodeIds.has(n.id));

  const edgeKey = (e) => `${e.from}->${e.to}${e.label ? `[${e.label}]` : ''}`;
  const oldEdgeKeys = new Set(oldGraph.edges.map(edgeKey));
  const newEdgeKeys = new Set(newGraph.edges.map(edgeKey));

  const addedEdges = newGraph.edges.filter((e) => !oldEdgeKeys.has(edgeKey(e)));
  const removedEdges = oldGraph.edges.filter((e) => !newEdgeKeys.has(edgeKey(e)));

  return {
    hasChanges:
      addedNodes.length > 0 || removedNodes.length > 0 || addedEdges.length > 0 || removedEdges.length > 0,
    addedNodes,
    removedNodes,
    addedEdges,
    removedEdges
  };
}

export function recordArchTrace({
  reason = 'Architectural refinement',
  ticket = null,
  diagramPath = null,
  repoRoot = '.'
} = {}) {
  const current = getSessionStatus(repoRoot);
  const activeSess = current.session;
  const issueId = ticket || activeSess?.issueId || 'N/A';
  let branch = activeSess?.branch;
  if (!branch) {
    try {
      branch = execSync('git branch --show-current 2>/dev/null', { cwd: repoRoot, encoding: 'utf8' }).trim() || 'develop';
    } catch {
      branch = 'develop';
    }
  }

  if (branch === 'develop' || branch === 'main') {
    console.warn(`⚠️ Warning: Recording architectural changes on protected branch '${branch}'!`);
  }

  let commitSha = 'UNCOMMITTED';
  try {
    commitSha = execSync('git rev-parse --short HEAD 2>/dev/null', { cwd: repoRoot, encoding: 'utf8' }).trim() || 'UNCOMMITTED';
  } catch {}

  const candidateDiagrams = diagramPath
    ? [path.resolve(repoRoot, diagramPath)]
    : [
        path.resolve(repoRoot, 'docs/architecture/system.mmd'),
        path.resolve(repoRoot, 'docs/specs/architecture.mmd'),
        path.resolve(repoRoot, '.agent/skills/sdlc-flow/SKILL.md'),
        path.resolve(repoRoot, 'README.md')
      ].filter((f) => fs.existsSync(f));

  const timestamp = new Date().toISOString();
  const traceEntries = [];

  for (const diag of candidateDiagrams) {
    const relDiagPath = path.relative(path.resolve(repoRoot), diag);
    const content = fs.readFileSync(diag, 'utf8');

    let prevContent = '';
    try {
      prevContent = execSync(`git show HEAD~1:"${relDiagPath}" 2>/dev/null`, { cwd: repoRoot, encoding: 'utf8' });
    } catch {
      prevContent = '';
    }

    const currentGraph = parseMermaidDiagram(content);
    const prevGraph = parseMermaidDiagram(prevContent);
    const diff = diffMermaidGraphs(prevGraph, currentGraph);

    if (diff.hasChanges || !prevContent) {
      traceEntries.push({
        diagram: relDiagPath,
        diff,
        summary: `Nodes: +${diff.addedNodes.length}/-${diff.removedNodes.length}, Edges: +${diff.addedEdges.length}/-${diff.removedEdges.length}`
      });
    }
  }

  const modifiedFiles = getModifiedFiles('main', repoRoot);
  const archDiff = checkArchDiff(repoRoot);

  const entry = {
    id: `trace-${Date.now()}`,
    timestamp,
    ticket: `#${issueId}`,
    branch,
    commitSha,
    reason,
    diagramChanges: traceEntries,
    modifiedFiles,
    archlensViolations: archDiff.violationsCount,
    status: archDiff.passed ? 'PASSED' : 'VIOLATION'
  };

  const jsonlDir = path.resolve(repoRoot, '.agent');
  fs.mkdirSync(jsonlDir, { recursive: true });
  const jsonlPath = path.resolve(jsonlDir, 'arch-trace.jsonl');
  fs.appendFileSync(jsonlPath, JSON.stringify(entry) + '\n', 'utf8');

  const docsDir = path.resolve(repoRoot, 'docs');
  fs.mkdirSync(docsDir, { recursive: true });
  const traceMdPath = path.resolve(docsDir, 'ARCH_TRACE.md');

  if (!fs.existsSync(traceMdPath)) {
    const header = `# 🏛️ Architectural Traceability & Mutation Ledger

This document maintains an immutable audit log of all architectural transformations, Mermaid component evolutions, and Clean Architecture layer boundary shifts.

> **Hard Framework Invariant**: Direct pushes to \`develop\` or \`main\` are forbidden. All architectural modifications must occur on isolated branches (\`feature/*\` or \`arch/*\`) and be recorded here before Pull Request merge.

| Timestamp | Ticket | Branch | Commit | Reason | Diagrams / Components Changed | Archlens Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |\n`;
    fs.writeFileSync(traceMdPath, header, 'utf8');
  }

  const diagSummary =
    traceEntries.length > 0
      ? traceEntries.map((t) => `\`${t.diagram}\` (${t.summary})`).join('<br/>')
      : `Files modified (${modifiedFiles.length})`;

  const statusBadge = entry.status === 'PASSED' ? '✅ PASSED' : `⚠️ ${entry.archlensViolations} VIOLATIONS`;
  const mdRow = `| ${entry.timestamp.split('T')[0]} ${entry.timestamp.split('T')[1].slice(0, 5)} | [${entry.ticket}](https://github.com/org/repo/issues/${issueId}) | \`${entry.branch}\` | \`${entry.commitSha}\` | ${reason.replace(/\|/g, '/')} | ${diagSummary} | ${statusBadge} |\n`;

  fs.appendFileSync(traceMdPath, mdRow, 'utf8');
  console.log(`📝 Appended architectural trace entry to ${traceMdPath} and ${jsonlPath}`);

  return {
    success: true,
    entry,
    markdownPath: traceMdPath,
    jsonlPath
  };
}

export function scaffoldArchlensPolicy(repoRoot = '.') {
  console.log('🏛️ Auto-Scaffolding Native Archlens Clean Architecture Policy...');
  const archDir = path.resolve(repoRoot, '.archlens');
  fs.mkdirSync(archDir, { recursive: true });

  const policyPath = path.resolve(archDir, 'policy.json');
  if (fileExists(policyPath)) {
    console.log(`ℹ️ Archlens policy already exists at ${policyPath}`);
    return { success: true, created: false, path: policyPath };
  }

  const layers = [
    {
      name: 'Domain',
      description: 'Core entities, value objects, and domain invariants. Ring 0 (Zero dependencies).',
      paths: ['src/domain', 'domain', 'lib/domain', 'core/domain', 'entities'],
      allowedDependencies: []
    },
    {
      name: 'Application',
      description: 'Use cases, ports, service interfaces, DTOs. Ring 1 (Depends only on Domain).',
      paths: ['src/application', 'application', 'usecases', 'services'],
      allowedDependencies: ['Domain']
    },
    {
      name: 'Infrastructure',
      description: 'Database persistence, external API clients, message brokers. Ring 2.',
      paths: ['src/infrastructure', 'infrastructure', 'db', 'persistence', 'adapters'],
      allowedDependencies: ['Domain', 'Application']
    },
    {
      name: 'Web / Presentation',
      description: 'HTTP controllers, REST resources, UI views, routes. Ring 3.',
      paths: ['src/web', 'web', 'controllers', 'routes', 'ui', 'frontend'],
      allowedDependencies: ['Domain', 'Application']
    }
  ];

  const policy = {
    $schema: 'https://archlens.dev/schemas/policy.v1.json',
    version: '1.0.0',
    generatedAt: new Date().toISOString(),
    architecture: 'Clean Architecture (Concentric Rings)',
    rules: {
      strictConcentricRings: true,
      enforceInDiff: true,
      allowCircularDependencies: false
    },
    layers
  };

  fs.writeFileSync(policyPath, JSON.stringify(policy, null, 2) + '\n', 'utf8');
  console.log(`✅ Archlens Clean Architecture policy scaffolded at ${policyPath}`);

  return {
    success: true,
    created: true,
    path: policyPath,
    layersCount: layers.length
  };
}
