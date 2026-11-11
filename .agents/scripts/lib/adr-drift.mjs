import fs from 'node:fs';
import path from 'node:path';
import { fileExists, readFileSafe, getModifiedFiles, gitOutput } from './core.mjs';
import { appendDecisionEvent, syncDecisionMapMarkdown } from './decision-graph.mjs';
import { appendAuditRecord } from './audit-chain.mjs';

function patchProductMdWithADR(repoRoot, adr) {
  const productPath = path.resolve(repoRoot, 'PRODUCT.md');
  if (!fileExists(productPath)) return false;

  let content = readFileSafe(productPath) || '';
  const row = `| ${adr.id} | ${adr.title} | \`${adr.scope}\` | \`${adr.status}\` | #${adr.ticketId || 'N/A'} | ${(adr.supersedes || []).join(', ') || 'None'} |`;

  // Update existing row if ADR ID already listed
  const rowRegex = new RegExp(`^\\|\\s*${adr.id}\\s*\\|.*$`, 'm');
  if (rowRegex.test(content)) {
    content = content.replace(rowRegex, row);
  } else if (content.includes('<!-- ADR_INDEX_END -->')) {
    content = content.replace('<!-- ADR_INDEX_END -->', `${row}\n<!-- ADR_INDEX_END -->`);
  } else {
    content += `\n${row}\n`;
  }

  // If ADR is accepted and supersedes any CAP-XX, annotate the capability status in PRODUCT.md
  if (adr.status === 'accepted' && Array.isArray(adr.supersedes)) {
    for (const supId of adr.supersedes) {
      if (supId.startsWith('CAP-')) {
        const capRegex = new RegExp(`^(\\|\\s*${supId}\\s*\\|[^|]+\\|[^|]+\\|[^|]+\\|)\\s*([^|]+)\\|`, 'm');
        if (capRegex.test(content)) {
          content = content.replace(capRegex, `$1 \`AMENDED (${adr.id})\` |`);
        }
      }
    }
  }

  fs.writeFileSync(productPath, content, 'utf8');
  return true;
}

export function listADRs(repoRoot = '.') {
  const adrDir = path.resolve(repoRoot, 'docs/adr');
  if (!fileExists(adrDir)) return [];

  const files = fs
    .readdirSync(adrDir)
    .filter((f) => f.startsWith('ADR-') && f.endsWith('.md'))
    .sort();

  const adrs = [];
  for (const file of files) {
    const full = path.join(adrDir, file);
    const raw = readFileSafe(full) || '';
    const idMatch = file.match(/^(ADR-\d+)/);
    const id = idMatch ? idMatch[1] : file.replace(/\.md$/, '');
    const title = (raw.match(/^title:\s*(.+)$/m) || raw.match(/^#\s+(.+)$/m) || [null, id])[1].trim();
    const status = (raw.match(/^status:\s*(.+)$/m) || [null, 'accepted'])[1].trim();
    const scope = (raw.match(/^scope:\s*(.+)$/m) || [null, 'technical'])[1].trim();
    const ticketMatch = raw.match(/^ticket:\s*#?(\d+)/m);
    const supersedesRaw = (raw.match(/^supersedes:\s*(.+)$/m) || [null, ''])[1].trim();
    const supersedes = supersedesRaw ? supersedesRaw.split(',').map((s) => s.trim()).filter(Boolean) : [];
    const createdAt = (raw.match(/^createdAt:\s*(.+)$/m) || [null, ''])[1].trim();

    adrs.push({
      id,
      title,
      status,
      scope,
      ticketId: ticketMatch ? Number(ticketMatch[1]) : null,
      supersedes,
      createdAt,
      file: `docs/adr/${file}`,
      fullPath: full,
      content: raw
    });
  }

  return adrs;
}

export function createADR({
  title,
  scope = 'technical',
  ticketId = null,
  supersedes = [],
  context = 'Implementation revealed new technical or product constraints.',
  decision = 'Updated architectural approach.',
  consequences = 'Downstream specs and tickets updated accordingly.',
  autoAccept = null,
  repoRoot = '.'
} = {}) {
  const adrDir = path.resolve(repoRoot, 'docs/adr');
  fs.mkdirSync(adrDir, { recursive: true });

  const existing = listADRs(repoRoot);
  const nextNum = existing.length + 1;
  const adrId = `ADR-${String(nextNum).padStart(3, '0')}`;
  const slug = String(title || 'decision')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '')
    .slice(0, 48);

  const normScope = String(scope).toLowerCase() === 'product' || String(scope).toLowerCase() === 'contract' ? 'product' : 'technical';
  const shouldAccept = autoAccept !== null ? !!autoAccept : normScope === 'technical';
  const status = shouldAccept ? 'accepted' : 'pending_approval';
  const createdAt = new Date().toISOString();
  const supList = Array.isArray(supersedes) ? supersedes : String(supersedes || '').split(',').map((s) => s.trim()).filter(Boolean);

  const fileName = `${adrId}-${slug}.md`;
  const filePath = path.join(adrDir, fileName);
  const relativePath = `docs/adr/${fileName}`;

  const markdown = `---
id: ${adrId}
title: ${title}
status: ${status}
scope: ${normScope}
ticket: ${ticketId ? '#' + ticketId : 'N/A'}
supersedes: ${supList.join(', ')}
createdAt: ${createdAt}
---

# ${adrId}: ${title}

- **Status**: \`${status}\`
- **Blast Radius / Scope**: \`${normScope}\`
- **Triggered By Ticket**: ${ticketId ? '#' + ticketId : 'N/A'}
- **Supersedes / Amends**: ${supList.join(', ') || 'None'}

## 1. Context & Spec Drift Trigger
${context}

## 2. Decision
${decision}

## 3. Consequences & Downstream Impact
${consequences}
`;

  fs.writeFileSync(filePath, markdown, 'utf8');

  const adrObj = {
    id: adrId,
    title,
    status,
    scope: normScope,
    ticketId,
    supersedes: supList,
    createdAt,
    file: relativePath
  };

  patchProductMdWithADR(repoRoot, adrObj);

  appendDecisionEvent({
    type: 'ADR_CREATED',
    id: adrId,
    label: `${adrId}: ${title}`,
    category: 'ADR',
    status,
    ticketId,
    linksTo: [...supList, ...(ticketId ? [`#${ticketId}`] : [])],
    metadata: { scope: normScope, file: relativePath, decision },
    repoRoot
  });

  appendAuditRecord({
    event: 'ADR_MINTED',
    metadata: adrObj,
    repoRoot
  });

  syncDecisionMapMarkdown(repoRoot);

  return {
    success: true,
    adr: adrObj,
    filePath,
    relativePath
  };
}

export function decideADR({
  adrId,
  action = 'approve',
  reviewer = '@human-operator',
  reason = 'Reviewed via SDLC Governance Gate',
  repoRoot = '.'
} = {}) {
  const all = listADRs(repoRoot);
  const target = all.find((a) => a.id.toLowerCase() === String(adrId).toLowerCase());
  if (!target) {
    throw new Error(`ADR '${adrId}' not found in docs/adr/.`);
  }

  const newStatus = String(action).toLowerCase().startsWith('approv') ? 'accepted' : 'rejected';
  let updatedContent = target.content
    .replace(/^status:\s*.+$/m, `status: ${newStatus}`)
    .replace(/^- \*\*Status\*\*:\s*`.+`$/m, `- **Status**: \`${newStatus}\``);

  updatedContent += `\n## 4. Governance Review Decision\n- **Decision**: \`${newStatus.toUpperCase()}\`\n- **Reviewer**: ${reviewer}\n- **Rationale**: ${reason}\n- **Decided At**: ${new Date().toISOString()}\n`;

  fs.writeFileSync(target.fullPath, updatedContent, 'utf8');

  const updatedAdr = { ...target, status: newStatus };
  patchProductMdWithADR(repoRoot, updatedAdr);

  appendDecisionEvent({
    type: 'ADR_DECIDED',
    id: target.id,
    label: `${target.id}: ${target.title}`,
    category: 'ADR',
    status: newStatus,
    ticketId: target.ticketId,
    linksTo: target.supersedes,
    metadata: { reviewer, reason, action },
    repoRoot
  });

  appendAuditRecord({
    event: 'ADR_GOVERNANCE_DECISION',
    metadata: { adrId: target.id, status: newStatus, reviewer, reason },
    repoRoot
  });

  syncDecisionMapMarkdown(repoRoot);

  return {
    success: true,
    adr: updatedAdr
  };
}

function extractDecisionSummary(adr) {
  const raw = String(adr?.content || '');
  const sectionMatch = raw.match(/##\s*(?:2\.\s*)?Decision\s*\n+([\s\S]*?)(?=\n##\s|$)/i);
  if (sectionMatch && sectionMatch[1]) {
    const firstLine = sectionMatch[1]
      .split('\n')
      .map((l) => l.trim())
      .find(Boolean);
    if (firstLine) {
      return firstLine.replace(/\|/g, '/');
    }
  }
  return String(adr?.title || '').replace(/\|/g, '/');
}

/**
 * First-Class ADR Lifecycle Compaction (`adr compact` & Release Compaction):
 * Consolidates accepted in-flight `docs/adr/ADR-*.md` files into `docs/SRS.md`
 * (`## Compacted Architectural Decisions`), resets the ADR index in `PRODUCT.md`,
 * deletes the individual ADR files, prunes `.agent/decision-graph.jsonl`, and
 * records an immutable `ADR_BATCH_COMPACTED` event in `docs/AUDIT_CHAIN.jsonl`.
 */
export function compactADRs({ releaseVersion = 'baseline', dryRun = false, repoRoot = '.' } = {}) {
  const adrs = listADRs(repoRoot);
  if (adrs.length === 0) {
    return {
      success: true,
      dryRun: Boolean(dryRun),
      releaseVersion,
      compactedCount: 0,
      compactedAdrIds: [],
      srsPath: 'docs/SRS.md',
      message: 'No ADRs present in docs/adr/ to compact.'
    };
  }

  const pending = adrs.filter((a) => a.status === 'pending_approval');
  if (pending.length > 0) {
    throw new Error(
      `Cannot compact ADRs while pending_approval exists: ${pending.map((a) => a.id).join(', ')}. Approve or reject pending ADRs first.`
    );
  }

  const compactedAdrIds = adrs.map((a) => a.id);
  if (dryRun) {
    return {
      success: true,
      dryRun: true,
      releaseVersion,
      compactedCount: adrs.length,
      compactedAdrIds,
      srsPath: 'docs/SRS.md'
    };
  }

  // 1. Append or update ## Compacted Architectural Decisions table in docs/SRS.md
  const srsPath = path.resolve(repoRoot, 'docs/SRS.md');
  if (fileExists(srsPath)) {
    let srsContent = readFileSafe(srsPath) || '';
    const rows = adrs.map((a) => {
      const summary = extractDecisionSummary(a);
      const ticketCell = a.ticketId ? `#${a.ticketId}` : 'N/A';
      const supCell = (a.supersedes || []).join(', ') || 'None';
      return `| \`${a.id}\` | ${a.title.replace(/\|/g, '/')} | \`${a.scope}\` | ${ticketCell} | ${supCell} | \`${releaseVersion}\` | ${summary} |`;
    });

    if (srsContent.includes('## Compacted Architectural Decisions')) {
      for (const row of rows) {
        const idMatch = row.match(/^\|\s*`(ADR-\d+)`/);
        const existingRowRegex = idMatch ? new RegExp(`^\\|\\s*\`?${idMatch[1]}\`?\\s*\\|.*$`, 'm') : null;
        if (existingRowRegex && existingRowRegex.test(srsContent)) {
          srsContent = srsContent.replace(existingRowRegex, row);
        } else {
          srsContent = `${srsContent.trimEnd()}\n${row}\n`;
        }
      }
    } else {
      const section = [
        '',
        '## Compacted Architectural Decisions',
        '',
        '| ID | Title | Scope | Ticket | Supersedes | Compacted In Release | Decision |',
        '| :--- | :--- | :--- | :--- | :--- | :--- | :--- |',
        ...rows,
        ''
      ].join('\n');
      srsContent = `${srsContent.trimEnd()}\n${section}`;
    }
    fs.writeFileSync(srsPath, srsContent, 'utf8');
  }

  // 2. Reset <!-- ADR_INDEX_START -->...<!-- ADR_INDEX_END --> in PRODUCT.md
  const productPath = path.resolve(repoRoot, 'PRODUCT.md');
  if (fileExists(productPath)) {
    let productContent = readFileSafe(productPath) || '';
    const resetBlock = [
      '<!-- ADR_INDEX_START -->',
      '| ADR ID | Title | Scope | Status | Ticket | Supersedes |',
      '| :--- | :--- | :--- | :--- | :--- | :--- |',
      `| *None* | *Compacted into SRS.md (${releaseVersion})* | - | - | - | - |`,
      '<!-- ADR_INDEX_END -->'
    ].join('\n');
    if (/<!-- ADR_INDEX_START -->[\s\S]*?<!-- ADR_INDEX_END -->/.test(productContent)) {
      productContent = productContent.replace(/<!-- ADR_INDEX_START -->[\s\S]*?<!-- ADR_INDEX_END -->/, resetBlock);
      fs.writeFileSync(productPath, productContent, 'utf8');
    }
  }

  // 3. Delete compacted docs/adr/ADR-*.md files from disk (preserving docs/adr/.gitkeep)
  const adrDir = path.resolve(repoRoot, 'docs/adr');
  for (const adr of adrs) {
    try {
      if (fileExists(adr.fullPath)) {
        fs.unlinkSync(adr.fullPath);
      }
    } catch {}
  }
  if (fileExists(adrDir)) {
    try {
      fs.writeFileSync(path.join(adrDir, '.gitkeep'), '', 'utf8');
    } catch {}
  }

  // 4. Prune compacted ADR_CREATED and ADR_DECIDED events from .agent/decision-graph.jsonl
  const graphPath = path.resolve(repoRoot, '.agent/decision-graph.jsonl');
  if (fileExists(graphPath)) {
    const compactedSet = new Set(compactedAdrIds);
    const lines = (readFileSafe(graphPath) || '').split('\n').filter(Boolean);
    const kept = lines.filter((line) => {
      try {
        const evt = JSON.parse(line);
        if ((evt.type === 'ADR_CREATED' || evt.type === 'ADR_DECIDED') && compactedSet.has(evt.id)) {
          return false;
        }
      } catch {}
      return true;
    });
    fs.writeFileSync(graphPath, kept.length > 0 ? kept.join('\n') + '\n' : '', 'utf8');
  }

  // 5. Append single ADR_BATCH_COMPACTED event to decision graph & audit chain
  const supLinks = Array.from(new Set(adrs.flatMap((a) => a.supersedes || [])));
  appendDecisionEvent({
    type: 'ADR_BATCH_COMPACTED',
    id: `ADR-COMPACT-${String(releaseVersion).replace(/[^A-Za-z0-9_.-]/g, '-')}`,
    label: `Compacted ${adrs.length} ADR(s) into SRS.md (${releaseVersion})`,
    category: 'ARCH',
    status: 'compacted',
    ticketId: null,
    linksTo: supLinks,
    metadata: {
      releaseVersion,
      compactedAdrIds,
      compactedCount: adrs.length,
      srsPath: 'docs/SRS.md'
    },
    repoRoot
  });

  appendAuditRecord({
    event: 'ADR_BATCH_COMPACTED',
    metadata: {
      releaseVersion,
      compactedAdrIds,
      compactedCount: adrs.length,
      srsPath: 'docs/SRS.md'
    },
    repoRoot
  });

  syncDecisionMapMarkdown(repoRoot);

  return {
    success: true,
    dryRun: false,
    releaseVersion,
    compactedCount: adrs.length,
    compactedAdrIds,
    srsPath: 'docs/SRS.md'
  };
}

/**
 * Dual-Enforced Spec-Drift Sentinel:
 * Inspects git diff for structural changes (manifest dependencies, API contracts,
 * DB schema migrations, or Mermaid C4 architecture diagrams). If mutated without
 * an accompanying accepted ADR linked to the active ticket, blocks the gate.
 */
export function checkSpecDrift({
  diffFiles = [],
  baseBranch = 'main',
  ticketId = null,
  repoRoot = '.'
} = {}) {
  const files = diffFiles.length > 0 ? diffFiles : getModifiedFiles(baseBranch, repoRoot);
  const triggers = [];

  for (const file of files) {
    const lower = file.toLowerCase();
    if (
      lower.endsWith('package.json') ||
      lower.endsWith('pom.xml') ||
      lower.endsWith('cargo.toml') ||
      lower.endsWith('pyproject.toml') ||
      lower.endsWith('go.mod')
    ) {
      triggers.push({ file, category: 'DEPENDENCY_MANIFEST_MUTATION', reason: 'Stack manifest or dependency modified' });
    } else if (
      lower.endsWith('openapi.json') ||
      lower.endsWith('openapi.yaml') ||
      lower.endsWith('openapi.yml') ||
      lower.endsWith('.graphql') ||
      lower.endsWith('.gql')
    ) {
      triggers.push({ file, category: 'API_CONTRACT_MUTATION', reason: 'Public API schema or contract modified' });
    } else if (lower.includes('migration') || lower.includes('flyway') || lower.endsWith('.sql')) {
      triggers.push({ file, category: 'DB_SCHEMA_MIGRATION', reason: 'Database DDL migration introduced or altered' });
    } else if (lower.endsWith('.mmd') || lower.includes('docs/architecture/') || lower.includes('.archlens/policy.json')) {
      triggers.push({ file, category: 'C4_ARCHITECTURE_BOUNDARY_SHIFT', reason: 'Architecture diagram or concentric layer policy modified' });
    } else if (lower === 'docs/prd.md' || lower.endsWith('/docs/prd.md') || lower === 'docs/srs.md' || lower.endsWith('/docs/srs.md')) {
      triggers.push({ file, category: 'PRD_SRS_CONTRACT_MUTATION', reason: 'Macro PRD or IEEE 29148 SRS specification modified' });
    }
  }

  if (triggers.length === 0) {
    return {
      passed: true,
      hasUndeclaredDrift: false,
      triggers: [],
      linkedADRs: [],
      message: 'No structural spec drift triggers detected in diff.'
    };
  }

  // Check if an ADR is present in the diff or already registered in docs/adr/ for this ticketId
  const allAdrs = listADRs(repoRoot);
  const diffHasAdr = files.some((f) => f.includes('docs/adr/ADR-') && f.endsWith('.md'));
  const matchingAdrs = allAdrs.filter((a) => {
    const matchesTicket = ticketId ? Number(a.ticketId) === Number(ticketId) : true;
    const isAccepted = a.status === 'accepted';
    return isAccepted && (matchesTicket || diffHasAdr);
  });

  // Also allow ADR Compaction diffs (where docs/adr/ADR-*.md files were compacted into docs/SRS.md and recorded in AUDIT_CHAIN.jsonl)
  let isAdrCompaction = false;
  if (matchingAdrs.length === 0) {
    const auditChainPath = path.resolve(repoRoot, 'docs/AUDIT_CHAIN.jsonl');
    const auditRaw = fileExists(auditChainPath) ? readFileSafe(auditChainPath) || '' : '';
    const hasCompactedAuditRecord = auditRaw.includes('"event":"ADR_BATCH_COMPACTED"');
    const diffDeletesAdr = files.some(
      (f) => f.includes('docs/adr/ADR-') && f.endsWith('.md') && !fileExists(path.resolve(repoRoot, f))
    );
    const srsPath = path.resolve(repoRoot, 'docs/SRS.md');
    const srsHasCompactedTable = fileExists(srsPath) && (readFileSafe(srsPath) || '').includes('## Compacted Architectural Decisions');
    if (hasCompactedAuditRecord && (diffDeletesAdr || srsHasCompactedTable)) {
      isAdrCompaction = true;
    }
  }

  const passed = matchingAdrs.length > 0 || isAdrCompaction;
  const linkedIds = matchingAdrs.length > 0 ? matchingAdrs.map((a) => a.id) : isAdrCompaction ? ['ADR_BATCH_COMPACTED'] : [];

  return {
    passed,
    hasUndeclaredDrift: !passed,
    triggers,
    linkedADRs: linkedIds,
    remediationCommand: `node .agent/scripts/sdlc-engine.mjs adr create --title "<decision>" --scope <technical|product> --ticket ${ticketId || '<id>'} --supersedes CAP-01`,
    message: passed
      ? `Spec drift verified and governed by ${linkedIds.join(', ')}.`
      : `UNDECLARED_SPEC_DRIFT: ${triggers.length} structural mutation(s) detected without a linked accepted ADR in docs/adr/.`
  };
}

// --- API Contract Drift & Schema Guardian (Issue #24) ---

export function auditContractDrift({ diffFiles = [], baseBranch = 'main', repoRoot = '.' } = {}) {
  const files = diffFiles.length > 0 ? diffFiles : getModifiedFiles(baseBranch, repoRoot);
  const contractPatterns = ['openapi.json', 'openapi.yaml', 'openapi.yml', 'swagger.json', 'schema.graphql', 'schema.gql'];

  const modifiedContracts = files.filter((f) => {
    const lower = f.toLowerCase();
    return contractPatterns.some((p) => lower.endsWith(p));
  });

  if (modifiedContracts.length === 0) {
    return {
      passed: true,
      hasDrift: false,
      breakingChangesCount: 0,
      breakingChanges: [],
      filesChecked: 0,
      message: 'No API contracts or schema files modified in diff.'
    };
  }

  const breakingChanges = [];

  for (const relPath of modifiedContracts) {
    const fullPath = path.resolve(repoRoot, relPath);
    let oldContent = '';
    try {
      oldContent = (
        gitOutput(['show', `origin/${baseBranch}:${relPath}`], repoRoot) ??
        gitOutput(['show', `HEAD~1:${relPath}`], repoRoot) ??
        ''
      ).trim();
    } catch {}

    const newContent = fileExists(fullPath) ? readFileSafe(fullPath) : '';

    if (!newContent) {
      breakingChanges.push({
        file: relPath,
        type: 'CONTRACT_DELETED',
        severity: 'CRITICAL',
        details: `API contract file was deleted completely: ${relPath}`
      });
      continue;
    }

    if (relPath.endsWith('.json')) {
      try {
        const oldJson = oldContent ? JSON.parse(oldContent) : null;
        const newJson = JSON.parse(newContent);

        if (oldJson && oldJson.paths) {
          for (const [route, methods] of Object.entries(oldJson.paths)) {
            if (!newJson.paths || !newJson.paths[route]) {
              breakingChanges.push({
                file: relPath,
                type: 'ENDPOINT_REMOVED',
                severity: 'CRITICAL',
                details: `Endpoint route removed: ${route}`
              });
            } else {
              const newMethods = newJson.paths[route];
              for (const method of Object.keys(methods)) {
                if (!newMethods[method]) {
                  breakingChanges.push({
                    file: relPath,
                    type: 'METHOD_REMOVED',
                    severity: 'CRITICAL',
                    details: `HTTP method ${method.toUpperCase()} removed from route: ${route}`
                  });
                }
              }
            }
          }
        }
      } catch {}
    } else if (relPath.endsWith('.graphql') || relPath.endsWith('.gql')) {
      if (oldContent) {
        const oldTypeMatches = Array.from(oldContent.matchAll(/(?:type|input|interface)\s+([A-Za-z0-9_]+)/g)).map(
          (m) => m[1]
        );
        const newTypeMatches = new Set(
          Array.from(newContent.matchAll(/(?:type|input|interface)\s+([A-Za-z0-9_]+)/g)).map((m) => m[1])
        );

        for (const t of oldTypeMatches) {
          if (!newTypeMatches.has(t)) {
            breakingChanges.push({
              file: relPath,
              type: 'GRAPHQL_TYPE_REMOVED',
              severity: 'CRITICAL',
              details: `GraphQL type removed: ${t}`
            });
          }
        }
      }
    }
  }

  const passed = breakingChanges.length === 0;

  return {
    passed,
    hasDrift: breakingChanges.length > 0,
    breakingChangesCount: breakingChanges.length,
    breakingChanges,
    filesChecked: modifiedContracts.length,
    message: passed
      ? `API contract check clean (${modifiedContracts.length} contract files verified).`
      : `Detected ${breakingChanges.length} breaking API contract change(s)!`
  };
}
