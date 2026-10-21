import fs from 'node:fs';
import path from 'node:path';
import { fileExists, readFileSafe } from './core.mjs';
import { parseProductMarkdown } from './product-dag.mjs';
import { listADRs } from './adr-drift.mjs';

export function extractExportSignatures(fileContent = '') {
  const exports = [];
  const lines = String(fileContent || '').split('\n');

  for (const rawLine of lines) {
    const line = rawLine.trim();
    const match = line.match(
      /^export\s+(?:async\s+)?(?:function|class|const|let|interface|type)\s+([A-Za-z0-9_]+)/
    );
    if (match) {
      exports.push(match[1]);
    }
  }
  return exports;
}

export function generateTicketDossier({
  issueId,
  capabilityId = null,
  targetFiles = [],
  repoRoot = '.'
} = {}) {
  const dossierDir = path.resolve(repoRoot, '.agent/dossiers');
  fs.mkdirSync(dossierDir, { recursive: true });

  const product = parseProductMarkdown(repoRoot);
  const allAdrs = listADRs(repoRoot);

  const matchedCap =
    product.capabilities.find((c) => c.id === capabilityId) ||
    product.capabilities[0] ||
    null;

  let specExcerpt = '';
  if (matchedCap && matchedCap.spec) {
    const specPath = path.resolve(repoRoot, matchedCap.spec);
    if (fileExists(specPath)) {
      specExcerpt = (readFileSafe(specPath) || '').slice(0, 1200);
    }
  }

  const activeADRs = allAdrs
    .filter((a) => {
      if (a.status !== 'accepted') return false;
      if (capabilityId && a.supersedes.includes(capabilityId)) return true;
      if (issueId && Number(a.ticketId) === Number(issueId)) return true;
      return true;
    })
    .map((a) => ({
      id: a.id,
      title: a.title,
      scope: a.scope,
      supersedes: a.supersedes,
      file: a.file
    }));

  const fileSignatures = [];
  for (const relFile of targetFiles) {
    const full = path.resolve(repoRoot, relFile);
    if (fileExists(full)) {
      const content = readFileSafe(full) || '';
      const exports = extractExportSignatures(content);
      fileSignatures.push({
        file: relFile,
        linesCount: content.split('\n').length,
        exports
      });
    }
  }

  const dossier = {
    generatedAt: new Date().toISOString(),
    issueId: Number(issueId) || issueId,
    capability: matchedCap,
    invariants: product.invariants || [],
    activeADRs,
    specExcerpt,
    fileSignatures
  };

  const jsonPath = path.join(dossierDir, `issue-${issueId}.json`);
  const markdownPath = path.join(dossierDir, `issue-${issueId}.md`);

  fs.writeFileSync(jsonPath, JSON.stringify(dossier, null, 2), 'utf8');

  const md = `# 📦 Context-Compressed Subagent Dossier: Ticket #${issueId}

- **Capability**: \`${matchedCap ? matchedCap.id + ' - ' + matchedCap.title : 'N/A'}\`
- **Spec Contract**: \`${matchedCap?.spec || 'N/A'}\`
- **Active ADRs**: ${activeADRs.map((a) => `\`${a.id}\` (${a.title})`).join(', ') || 'None'}

## 🛡️ Hard Invariants
${(product.invariants || []).map((inv) => `- **${inv.id}**: ${inv.text}`).join('\n') || '- Standard SDLC Dual-Gate Invariants'}

## 📄 Target File Export Signatures
${fileSignatures.map((f) => `- \`${f.file}\` (${f.linesCount} lines): exports \`[${f.exports.join(', ')}]\``).join('\n') || '- No target files pre-scanned'}
`;

  fs.writeFileSync(markdownPath, md, 'utf8');

  return {
    success: true,
    dossier,
    jsonPath,
    markdownPath
  };
}
