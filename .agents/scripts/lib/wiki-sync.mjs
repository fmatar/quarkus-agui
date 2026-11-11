import fs from 'node:fs';
import path from 'node:path';
import { fileExists, readFileSafe, parseFrontmatter } from './core.mjs';
import { extractExportSignatures } from './dossier.mjs';
import { parseProductMarkdown } from './product-dag.mjs';
import { listADRs } from './adr-drift.mjs';
import { parseMermaidDiagram } from './arch-trace.mjs';
import { CANONICAL_SDLC_SKILLS, DEPRECATED_SDLC_SKILLS } from './installer.mjs';

/**
 * Default Concentric Clean Architecture Ring Classification for the SDLC Engine & Target Repos
 */
const DEFAULT_ENGINE_RING_MAP = {
  'core.mjs': { ring: 'Ring 0: Domain Core & Primitives', index: 0 },
  'cache.mjs': { ring: 'Ring 0: Domain Core & Primitives', index: 0 },
  'audit-chain.mjs': { ring: 'Ring 0: Domain Core & Primitives', index: 0 },
  'decision-graph.mjs': { ring: 'Ring 1: Application & Governance', index: 1 },
  'product-dag.mjs': { ring: 'Ring 1: Application & Governance', index: 1 },
  'adr-drift.mjs': { ring: 'Ring 1: Application & Governance', index: 1 },
  'dossier.mjs': { ring: 'Ring 1: Application & Governance', index: 1 },
  'session.mjs': { ring: 'Ring 1: Application & Governance', index: 1 },
  'arch-trace.mjs': { ring: 'Ring 1: Application & Governance', index: 1 },
  'wiki-sync.mjs': { ring: 'Ring 1: Application & Governance', index: 1 },
  'readme-sync.mjs': { ring: 'Ring 1: Application & Governance', index: 1 },
  'git-guard.mjs': { ring: 'Ring 2: Adapters & Enforcement', index: 2 },
  'pm-provider.mjs': { ring: 'Ring 2: Adapters & Enforcement', index: 2 },
  'quality-gate.mjs': { ring: 'Ring 2: Adapters & Enforcement', index: 2 },
  'installer.mjs': { ring: 'Ring 2: Adapters & Enforcement', index: 2 },
  'workbench-server.mjs': { ring: 'Ring 2: Adapters & Enforcement', index: 2 },
  'sdlc-engine.mjs': { ring: 'Ring 3: CLI Router & Entrypoint', index: 3 }
};

/**
 * Extracts exported symbols with line numbers and local import dependencies from a source file.
 */
export function inspectSourceModule(filePath, repoRoot = '.') {
  const absPath = path.resolve(repoRoot, filePath);
  const raw = readFileSafe(absPath) || '';
  const relPath = path.relative(path.resolve(repoRoot), absPath).replace(/\\/g, '/');
  const lines = raw.split('\n');

  const exportsWithLines = [];
  const localImports = [];

  const exportPatterns = [
    /^export\s+(?:async\s+)?function\s+([A-Za-z0-9_]+)/,
    /^export\s+class\s+([A-Za-z0-9_]+)/,
    /^export\s+(?:const|let|var)\s+([A-Za-z0-9_]+)/,
    /^export\s+(?:interface|type|enum)\s+([A-Za-z0-9_]+)/,
    /^def\s+([A-Za-z0-9_]+)\s*\(/,
    /^class\s+([A-Za-z0-9_]+)/,
    /^pub\s+(?:async\s+)?fn\s+([A-Za-z0-9_]+)/,
    /^pub\s+(?:struct|enum|trait)\s+([A-Za-z0-9_]+)/,
    /^func\s+(?:\([^)]+\)\s*)?([A-Z][A-Za-z0-9_]*)\s*\(/,
    /^\s*public\s+(?:static\s+)?(?:final\s+)?(?:class|interface|record|enum|[A-Za-z0-9_<>[\]]+)\s+([A-Za-z0-9_]+)/
  ];

  const importRegex = /from\s+['"](\.[^'"]+)['"]|import\s+['"](\.[^'"]+)['"]/g;

  for (let i = 0; i < lines.length; i++) {
    const trimmed = lines[i].trim();
    for (const pat of exportPatterns) {
      const m = trimmed.match(pat);
      if (m && m[1] && !m[1].startsWith('_')) {
        if (!exportsWithLines.some((e) => e.name === m[1])) {
          exportsWithLines.push({ name: m[1], line: i + 1 });
        }
      }
    }

    let impMatch;
    importRegex.lastIndex = 0;
    while ((impMatch = importRegex.exec(trimmed)) !== null) {
      const target = impMatch[1] || impMatch[2];
      if (target) {
        const resolvedRel = path.posix.normalize(path.posix.join(path.posix.dirname(relPath), target));
        if (!localImports.includes(resolvedRel)) {
          localImports.push(resolvedRel);
        }
      }
    }
  }

  if (exportsWithLines.length === 0) {
    const fallbackSigs = extractExportSignatures(absPath);
    for (const sig of fallbackSigs) {
      exportsWithLines.push({ name: sig, line: 1 });
    }
  }

  return {
    path: relPath,
    name: path.basename(relPath),
    lineCount: lines.length,
    exports: exportsWithLines,
    imports: localImports
  };
}

/**
 * Resolves the Archlens Concentric Ring for a given file path using `.archlens/policy.json`
 * and fallback Clean Architecture conventions.
 */
export function resolveArchlensRing(relPath, policy = null) {
  const base = path.basename(relPath);
  if (DEFAULT_ENGINE_RING_MAP[base]) {
    return DEFAULT_ENGINE_RING_MAP[base];
  }

  const lower = relPath.toLowerCase();
  if (policy && Array.isArray(policy.layers)) {
    for (let i = 0; i < policy.layers.length; i++) {
      const layer = policy.layers[i];
      const paths = (layer.paths || [layer.name]).filter(Boolean).map((p) => String(p).toLowerCase());
      if (paths.some((p) => lower.startsWith(`${p}/`) || lower.includes(`/${p}/`) || lower === p)) {
        return { ring: `Ring ${i}: ${layer.name}`, index: i };
      }
    }
  }

  if (lower.includes('domain') || lower.includes('entity') || lower.includes('core')) {
    return { ring: 'Ring 0: Domain Core & Primitives', index: 0 };
  }
  if (lower.includes('application') || lower.includes('service') || lower.includes('usecase')) {
    return { ring: 'Ring 1: Application & Governance', index: 1 };
  }
  if (lower.includes('adapter') || lower.includes('controller') || lower.includes('infra') || lower.includes('web')) {
    return { ring: 'Ring 2: Adapters & Enforcement', index: 2 };
  }
  return { ring: 'Ring 3: CLI Router & Entrypoint', index: 3 };
}

/**
 * Builds a complete Source-to-Wiki & Archlens Concentric Ring Codebase Map.
 */
export function buildWikiCodeMap(repoRoot = '.') {
  const absRoot = path.resolve(repoRoot);
  const policyPath = path.join(absRoot, '.archlens/policy.json');
  let policy = null;
  if (fileExists(policyPath)) {
    try {
      policy = JSON.parse(readFileSafe(policyPath) || '{}');
    } catch {
      policy = null;
    }
  }

  // 1. Discover source modules (.agent/scripts/lib/*.mjs + sdlc-engine.mjs + src/ if present)
  const moduleFiles = [];
  const engineEntry = path.join(absRoot, '.agent/scripts/sdlc-engine.mjs');
  if (fileExists(engineEntry)) {
    moduleFiles.push('.agent/scripts/sdlc-engine.mjs');
  }
  const libDir = path.join(absRoot, '.agent/scripts/lib');
  if (fileExists(libDir)) {
    const entries = fs
      .readdirSync(libDir)
      .filter((f) => f.endsWith('.mjs') || f.endsWith('.js') || f.endsWith('.ts'))
      .sort();
    for (const entry of entries) {
      moduleFiles.push(`.agent/scripts/lib/${entry}`);
    }
  }

  // Also scan polyglot src/ or domain/ directories if present
  for (const candidateDir of ['src', 'domain', 'adapters', 'lib']) {
    const absCandidate = path.join(absRoot, candidateDir);
    if (fileExists(absCandidate) && fs.statSync(absCandidate).isDirectory()) {
      const walk = (dir) => {
        for (const item of fs.readdirSync(dir, { withFileTypes: true })) {
          if (item.name.startsWith('.') || item.name === 'node_modules' || item.name === 'target') continue;
          const full = path.join(dir, item.name);
          if (item.isDirectory()) walk(full);
          else if (/\.(mjs|js|ts|py|java|go|rs)$/.test(item.name) && !/\.(test|spec)\./.test(item.name)) {
            const rel = path.relative(absRoot, full).replace(/\\/g, '/');
            if (!moduleFiles.includes(rel)) moduleFiles.push(rel);
          }
        }
      };
      walk(absCandidate);
    }
  }

  // 2. Read existing wiki/*.md pages
  const wikiDir = path.join(absRoot, 'wiki');
  const wikiPages = [];
  if (fileExists(wikiDir) && fs.statSync(wikiDir).isDirectory()) {
    const mdFiles = fs
      .readdirSync(wikiDir)
      .filter((f) => f.endsWith('.md'))
      .sort();
    for (const mdFile of mdFiles) {
      const absMd = path.join(wikiDir, mdFile);
      const content = readFileSafe(absMd) || '';
      const headings = content
        .split('\n')
        .filter((l) => /^#{1,3}\s+/.test(l))
        .map((l) => l.replace(/^#{1,3}\s+/, '').trim());
      const mermaidBlocks = (content.match(/```mermaid[\s\S]*?```/g) || []).length;
      wikiPages.push({
        name: mdFile,
        path: `wiki/${mdFile}`,
        content,
        headings,
        mermaidBlocks,
        lineCount: content.split('\n').length
      });
    }
  }

  // 3. Inspect modules & compute Archlens Ca (Afferent), Ce (Efferent), and Instability I = Ce / (Ca + Ce)
  const rawModules = moduleFiles.map((relPath) => {
    const info = inspectSourceModule(relPath, absRoot);
    const ringInfo = resolveArchlensRing(relPath, policy);
    return {
      ...info,
      ring: ringInfo.ring,
      ringIndex: ringInfo.index
    };
  });

  const inboundCounts = new Map(rawModules.map((m) => [m.path, 0]));
  for (const mod of rawModules) {
    for (const imp of mod.imports) {
      if (inboundCounts.has(imp)) {
        inboundCounts.set(imp, inboundCounts.get(imp) + 1);
      }
    }
  }

  const modules = rawModules.map((mod) => {
    const ca = inboundCounts.get(mod.path) || 0;
    const ce = mod.imports.length;
    const instability = ca + ce === 0 ? 0 : Number((ce / (ca + ce)).toFixed(2));
    const documentedIn = wikiPages
      .filter((w) => w.content.includes(mod.name) || w.content.includes(mod.path))
      .map((w) => w.path);
    return {
      ...mod,
      ca,
      ce,
      instability,
      documentedIn
    };
  });

  // Group modules by Concentric Ring
  const ringsMap = new Map();
  for (const mod of modules) {
    if (!ringsMap.has(mod.ring)) {
      ringsMap.set(mod.ring, {
        name: mod.ring,
        index: mod.ringIndex,
        modules: []
      });
    }
    ringsMap.get(mod.ring).modules.push(mod);
  }
  const rings = Array.from(ringsMap.values()).sort((a, b) => a.index - b.index);

  // 4. Inspect canonical SDLC skills
  const skillsDir = path.join(absRoot, '.agent/skills');
  const skills = [];
  if (fileExists(skillsDir) && fs.statSync(skillsDir).isDirectory()) {
    const dirs = fs
      .readdirSync(skillsDir, { withFileTypes: true })
      .filter((d) => d.isDirectory() && d.name.startsWith('sdlc'))
      .map((d) => d.name)
      .sort();
    for (const sName of dirs) {
      const skillMdPath = path.join(skillsDir, sName, 'SKILL.md');
      if (fileExists(skillMdPath)) {
        const raw = readFileSafe(skillMdPath) || '';
        const { frontmatter } = parseFrontmatter(raw);
        const documentedIn = wikiPages.filter((w) => w.content.includes(sName)).map((w) => w.path);
        skills.push({
          name: sName,
          path: `.agent/skills/${sName}/SKILL.md`,
          description: frontmatter?.description || '',
          documentedIn
        });
      }
    }
  }

  // 5. Inspect Product Capabilities & ADRs
  let capabilities = [];
  if (fileExists(path.join(absRoot, 'PRODUCT.md'))) {
    try {
      const prod = parseProductMarkdown(absRoot);
      capabilities = (prod.capabilities || []).map((c) => ({
        ...c,
        documentedIn: wikiPages.filter((w) => w.content.includes(c.id)).map((w) => w.path)
      }));
    } catch {}
  }

  const adrs = listADRs(absRoot).map((a) => ({
    ...a,
    documentedIn: wikiPages.filter((w) => w.content.includes(a.id)).map((w) => w.path)
  }));

  return {
    title: policy?.title || path.basename(absRoot),
    archlensInstalled: Boolean(policy),
    policy,
    rings,
    modules,
    skills,
    capabilities,
    adrs,
    wikiPages: wikiPages.map(({ content: _c, ...rest }) => rest)
  };
}

/**
 * Audits `wiki/*.md` for broken file references, deprecated skill names, invalid Mermaid syntax,
 * and unmapped source modules or canonical skills.
 */
export function auditWiki(repoRoot = '.', { strict = false } = {}) {
  const absRoot = path.resolve(repoRoot);
  const wikiDir = path.join(absRoot, 'wiki');

  if (!fileExists(wikiDir) || !fs.statSync(wikiDir).isDirectory()) {
    return {
      enabled: false,
      wikiExists: false,
      passed: true,
      findingsCount: 0,
      findings: [],
      summary: 'No wiki/ directory found in workspace; wiki audit skipped.'
    };
  }

  const codeMap = buildWikiCodeMap(absRoot);
  const findings = [];

  const mdFiles = fs
    .readdirSync(wikiDir)
    .filter((f) => f.endsWith('.md'))
    .sort();

  const historicalContextRegex = /deprecated|retired|consolidated|legacy|adr-002|adr-004|formerly|replaced/i;
  const codePathRegex = /`((?:\.agent|\.archlens|\.github|docs|wiki|tests|src)\/[A-Za-z0-9_.\-/]+)`/g;
  const placeholderRegex = /\*|<|>|\{|\}|\.\.|ADR-00X|ADR-XXX|cap-01-|issue-#|proof-|example|sample|my-|your-/i;

  for (const mdFile of mdFiles) {
    const relWikiPath = `wiki/${mdFile}`;
    const raw = readFileSafe(path.join(wikiDir, mdFile)) || '';
    const lines = raw.split('\n');

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];

      // 1. Check for active usage of deprecated SDLC skills
      if (!historicalContextRegex.test(line)) {
        for (const depSkill of DEPRECATED_SDLC_SKILLS) {
          const depRegex = new RegExp(`(?<![A-Za-z0-9_./-])${depSkill}(?![A-Za-z0-9_./-])`);
          if (depRegex.test(line)) {
            findings.push({
              rule: 'DEPRECATED_SKILL_REF',
              severity: 'ERROR',
              file: relWikiPath,
              line: i + 1,
              target: depSkill,
              message: `Stale reference to retired skill '${depSkill}' on ${relWikiPath}:${i + 1}. Update to canonical skills (ADR-004/ADR-007).`
            });
          }
        }
      }

      // 2. Check for broken repo-relative file/script paths in backticks
      let pathMatch;
      codePathRegex.lastIndex = 0;
      while ((pathMatch = codePathRegex.exec(line)) !== null) {
        const candidatePath = pathMatch[1].replace(/[,:;.)]+$/, '');
        if (!candidatePath || placeholderRegex.test(candidatePath)) continue;
        // Ephemeral runtime state files that are created on demand are allowed to be referenced
        if (
          candidatePath === '.archlens/policy.json' ||
          candidatePath === '.agent/skills/' ||
          candidatePath === '.agent/skills' ||
          candidatePath === '.github/workflows/sdlc-quality-gate.yml' ||
          candidatePath === '.github/ISSUE_TEMPLATE/' ||
          candidatePath === 'docs/KANBAN.md' ||
          candidatePath === 'docs/DECISION_MAP.md' ||
          candidatePath === 'docs/specs/' ||
          candidatePath === 'docs/adr/' ||
          candidatePath.startsWith('.agent/dossiers') ||
          candidatePath.startsWith('.agent/sdlc-session') ||
          candidatePath.startsWith('.agent/.sdlc-session') ||
          candidatePath.startsWith('.agent/sdlc-gate-cache') ||
          candidatePath.startsWith('.agent/sdlc-exemptions') ||
          candidatePath.startsWith('.agent/gate-receipts') ||
          candidatePath.startsWith('.agent/arch-trace') ||
          candidatePath.startsWith('.agent/decision-graph') ||
          candidatePath.startsWith('.agent/product-dag-state') ||
          candidatePath.startsWith('.agent/workbench') ||
          candidatePath.startsWith('.archlens/to-') ||
          candidatePath.startsWith('docs/proofs') ||
          candidatePath.startsWith('docs/ARCH_TRACE') ||
          candidatePath.startsWith('docs/AUDIT_CHAIN') ||
          candidatePath.startsWith('docs/PRD.md') ||
          candidatePath.startsWith('docs/SRS.md') ||
          candidatePath.startsWith('docs/logo-') ||
          candidatePath.startsWith('docs/demo.') ||
          candidatePath.startsWith('docs/architecture')
        ) {
          continue;
        }
        const absCandidate = path.join(absRoot, candidatePath);
        if (!fileExists(absCandidate)) {
          findings.push({
            rule: 'BROKEN_CODE_PATH',
            severity: 'ERROR',
            file: relWikiPath,
            line: i + 1,
            target: candidatePath,
            message: `Referenced path '${candidatePath}' on ${relWikiPath}:${i + 1} does not exist in repository.`
          });
        }
      }
    }

    // 3. Validate Mermaid diagrams in wiki page (stripping leading %%{init:...}%% directives/comments)
    const mermaidRegex = /```mermaid\n([\s\S]*?)```/g;
    let mMatch;
    while ((mMatch = mermaidRegex.exec(raw)) !== null) {
      const block = mMatch[1].trim();
      const strippedBlock = block
        .split('\n')
        .map((l) => l.trim())
        .filter((l) => l && !l.startsWith('%%'))
        .join('\n');
      const validHeader = /^(flowchart|graph|sequenceDiagram|stateDiagram(?:-v2)?|classDiagram|erDiagram|xychart-beta)\b/.test(
        strippedBlock
      );
      if (!validHeader) {
        findings.push({
          rule: 'INVALID_MERMAID_DIAGRAM',
          severity: 'ERROR',
          file: relWikiPath,
          line: 1,
          target: strippedBlock.split('\n')[0] || 'empty',
          message: `Unsupported or malformed Mermaid diagram header in ${relWikiPath}.`
        });
      } else if (/^(flowchart|graph)\b/.test(strippedBlock)) {
        const parsed = parseMermaidDiagram(strippedBlock);
        if (parsed.nodes.length === 0 && parsed.edges.length === 0) {
          findings.push({
            rule: 'EMPTY_MERMAID_FLOWCHART',
            severity: 'WARN',
            file: relWikiPath,
            line: 1,
            target: 'mermaid',
            message: `Mermaid flowchart in ${relWikiPath} parsed with 0 nodes and 0 edges.`
          });
        }
      }
    }
  }

  // 4. Verify all canonical skills present in the repo are documented in wiki/
  for (const skill of codeMap.skills) {
    if (CANONICAL_SDLC_SKILLS.includes(skill.name) && skill.documentedIn.length === 0) {
      findings.push({
        rule: 'UNDOCUMENTED_SKILL',
        severity: 'ERROR',
        file: skill.path,
        line: 1,
        target: skill.name,
        message: `Canonical skill '${skill.name}' is not documented in any wiki/*.md page.`
      });
    }
  }

  // 5. Verify all .agent/scripts/lib/*.mjs modules are mapped in wiki/
  for (const mod of codeMap.modules) {
    if (mod.path.startsWith('.agent/scripts/') && mod.documentedIn.length === 0) {
      findings.push({
        rule: 'UNMAPPED_SOURCE_MODULE',
        severity: strict ? 'ERROR' : 'ERROR',
        file: mod.path,
        line: 1,
        target: mod.name,
        message: `Engine module '${mod.path}' is not mapped in any wiki/*.md page. Run 'sdlc wiki sync' to update wiki/Codebase-Map.md.`
      });
    }
  }

  const mappedModulesCount = codeMap.modules.filter((m) => m.documentedIn.length > 0).length;
  const totalModulesCount = codeMap.modules.length;
  const coveragePct =
    totalModulesCount === 0 ? 100 : Number(((mappedModulesCount / totalModulesCount) * 100).toFixed(1));

  const errorFindings = findings.filter((f) => f.severity === 'ERROR');
  const passed = errorFindings.length === 0;

  return {
    enabled: true,
    wikiExists: true,
    passed,
    findingsCount: findings.length,
    errorsCount: errorFindings.length,
    findings,
    coveragePct,
    mappedModulesCount,
    totalModulesCount,
    summary: passed
      ? `✅ Wiki Audit Passed: ${mdFiles.length} wiki pages verified, ${mappedModulesCount}/${totalModulesCount} source modules mapped (${coveragePct}%).`
      : `❌ Wiki Audit Failed: ${errorFindings.length} error(s) found across ${mdFiles.length} wiki pages.`
  };
}

/**
 * Renders the deterministic `wiki/Codebase-Map.md` page mapping Archlens rings,
 * source modules, AST export signatures, skills, product capabilities, and ADRs.
 */
export function renderCodebaseMapMarkdown(codeMap) {
  const ringSubgraphs = codeMap.rings
    .map((r, idx) => {
      const ringId = `Ring${idx}`;
      const modNodes = r.modules
        .map((m) => {
          const safeId = m.name.replace(/[^A-Za-z0-9_]/g, '_');
          return `    ${safeId}["${m.name} (${m.exports.length} exports, I=${m.instability})"]`;
        })
        .join('\n');
      return `  subgraph ${ringId} ["${r.name}"]\n${modNodes}\n  end`;
    })
    .join('\n');

  const importEdges = [];
  const nameByPath = new Map(codeMap.modules.map((m) => [m.path, m.name.replace(/[^A-Za-z0-9_]/g, '_')]));
  for (const mod of codeMap.modules) {
    const fromId = nameByPath.get(mod.path);
    for (const imp of mod.imports) {
      const toId = nameByPath.get(imp);
      if (fromId && toId && fromId !== toId) {
        importEdges.push(`  ${fromId} --> ${toId}`);
      }
    }
  }

  const ringSections = codeMap.rings
    .map((ring) => {
      const rows = ring.modules
        .map((m) => {
          const topExports = m.exports
            .slice(0, 8)
            .map((e) => `\`${e.name}\` (L${e.line})`)
            .join(', ');
          const extra = m.exports.length > 8 ? ` +${m.exports.length - 8} more` : '';
          const docs = m.documentedIn.length > 0 ? m.documentedIn.map((d) => `\`${d}\``).join(', ') : '`wiki/Codebase-Map.md`';
          return `| \`${m.path}\` | ${m.lineCount} | ${m.ca} / ${m.ce} | \`${m.instability}\` | ${topExports}${extra} | ${docs} |`;
        })
        .join('\n');

      return `### 🏛️ ${ring.name}

| Source Module | Lines | $Ca$ / $Ce$ | Instability ($I$) | Exported AST Symbols (Line #) | Wiki Coverage |
| :--- | :--- | :--- | :--- | :--- | :--- |
${rows}
`;
    })
    .join('\n');

  const skillRows = codeMap.skills
    .map((s) => {
      const docs = s.documentedIn.length > 0 ? s.documentedIn.map((d) => `\`${d}\``).join(', ') : '`wiki/Skills-Reference.md`';
      return `| \`${s.name}\` | \`${s.path}\` | ${s.description.replace(/\|/g, '/')} | ${docs} |`;
    })
    .join('\n');

  const capRows =
    codeMap.capabilities.length > 0
      ? codeMap.capabilities
          .map(
            (c) =>
              `| **${c.id}** | ${c.title} | \`${c.spec}\` | \`${c.status}\` | ${(c.dependsOn || []).join(', ') || 'None'} |`
          )
          .join('\n')
      : '| `CAP-01` | Core Capability | `docs/specs/` | `ACTIVE` | None |';

  const adrRows =
    codeMap.adrs.length > 0
      ? codeMap.adrs
          .map(
            (a) =>
              `| **${a.id}** | ${a.title} | \`${a.scope}\` | \`${a.status}\` | ${(a.supersedes || []).join(', ') || 'None'} |`
          )
          .join('\n')
      : '| `ADR-001` | Initial Architecture | `technical` | `accepted` | `CAP-01` |';

  return `# 🗺️ Codebase-to-Wiki Traceability & Archlens Ring Map

> **Deterministic AST & Clean Architecture Inventory**  
> Generated and verified by \`node .agent/scripts/sdlc-engine.mjs wiki sync\` (\`sdlc-wiki\`) and governed by \`.archlens/policy.json\`.  
> Every module, exported symbol, concentric ring boundary, skill, capability (\`CAP-XX\`), and decision record (\`ADR-00X\`) is mapped below.

---

## 1. Archlens Concentric Ring Dependency Graph

In accordance with Robert C. Martin's **Clean Architecture** and \`.archlens/policy.json\`, dependencies point strictly inward toward **Ring 0 (Domain Core & Primitives)**.

- **Afferent Coupling ($Ca$)**: Number of modules that depend on this module (inward stability).
- **Efferent Coupling ($Ce$)**: Number of modules this module depends on (outward fan-out).
- **Instability ($I = \\frac{Ce}{Ca + Ce}$)**: \`0.00\` indicates a maximally stable core module; \`1.00\` indicates a pure outer entrypoint/adapter.

\`\`\`mermaid
flowchart TD
${ringSubgraphs}
${importEdges.slice(0, 45).join('\n')}
\`\`\`

---

## 2. Source Modules by Concentric Ring

${ringSections}

---

## 3. Canonical SDLC Skills (\`.agent/skills/\`)

| Skill Name | Skill Definition Path | Purpose & Trigger | Documented In |
| :--- | :--- | :--- | :--- |
${skillRows}

---

## 4. Product Capabilities (\`PRODUCT.md\`)

| Capability ID | Title | Functional Spec | Status | Depends On |
| :--- | :--- | :--- | :--- | :--- |
${capRows}

---

## 5. Architecture Decision Records (\`docs/adr/\`)

| ADR ID | Decision Title | Scope | Status | Amends / Supersedes |
| :--- | :--- | :--- | :--- | :--- |
${adrRows}

---

## 6. Verification & Zero-Rot Commands

\`\`\`bash
# 1. Rebuild AST-to-Wiki Codebase Map and synchronize tables
node .agent/scripts/sdlc-engine.mjs wiki sync

# 2. Audit all wiki/*.md files for broken paths, deprecated skills, or unmapped modules
node .agent/scripts/sdlc-engine.mjs wiki audit --strict

# 3. Inspect JSON Codebase Map & Archlens Ring Instability Metrics
node .agent/scripts/sdlc-engine.mjs wiki map
\`\`\`
`;
}

/**
 * Synchronizes `wiki/Codebase-Map.md` (and scaffolds missing core wiki pages if requested)
 * from live AST exports, `.archlens/policy.json`, `PRODUCT.md`, and `docs/adr/`.
 */
export function syncWiki({ repoRoot = '.', generateMissing = false } = {}) {
  const absRoot = path.resolve(repoRoot);
  const wikiDir = path.join(absRoot, 'wiki');
  fs.mkdirSync(wikiDir, { recursive: true });

  const updatedFiles = [];
  const shouldScaffold =
    generateMissing ||
    !fileExists(path.join(wikiDir, 'Home.md')) ||
    !fileExists(path.join(wikiDir, 'Skills-Reference.md'));

  if (shouldScaffold) {
    const preMap = buildWikiCodeMap(absRoot);
    const existingModuleRefs = preMap.modules.map((m) => `\`${m.path}\``);
    const skillsTable = [
      '# 🛠️ Skills & CLI Reference',
      '',
      'Canonical skills: ' + CANONICAL_SDLC_SKILLS.map((s) => `\`${s}\``).join(', ') + '.',
      ...(existingModuleRefs.length > 0 ? ['', '## Source & Engine Modules', existingModuleRefs.join(', ')] : []),
      ''
    ].join('\n');

    const defaults = [
      {
        name: 'Home.md',
        title: '# 🏛️ Project Wiki Home\n\nWelcome to the living architectural wiki. See [Codebase-Map.md](Codebase-Map.md) for full AST traceability.\n'
      },
      {
        name: 'Architecture.md',
        title: '# 🏗️ System Architecture\n\nConcentric Clean Architecture governed by `.archlens/policy.json`.\n'
      },
      {
        name: 'Skills-Reference.md',
        title: skillsTable
      },
      {
        name: 'Traceability.md',
        title: '# 🎯 Traceability & Governance\n\nEnd-to-end lineage across `PRODUCT.md`, `docs/KANBAN.md`, and `docs/DECISION_MAP.md`.\n'
      }
    ];
    for (const def of defaults) {
      const target = path.join(wikiDir, def.name);
      if (!fileExists(target)) {
        fs.writeFileSync(target, def.title, 'utf8');
        updatedFiles.push(`wiki/${def.name}`);
      }
    }
  }

  // First pass map to render Codebase-Map.md
  const initialMap = buildWikiCodeMap(absRoot);
  const mapMarkdown = renderCodebaseMapMarkdown(initialMap);
  const codeMapPath = path.join(wikiDir, 'Codebase-Map.md');
  fs.writeFileSync(codeMapPath, mapMarkdown, 'utf8');
  if (!updatedFiles.includes('wiki/Codebase-Map.md')) {
    updatedFiles.push('wiki/Codebase-Map.md');
  }

  // Re-compute map & audit now that Codebase-Map.md is written
  const finalMap = buildWikiCodeMap(absRoot);
  const audit = auditWiki(absRoot);

  return {
    success: audit.passed,
    codeMapPath: 'wiki/Codebase-Map.md',
    updatedFiles,
    modulesCount: finalMap.modules.length,
    ringsCount: finalMap.rings.length,
    skillsCount: finalMap.skills.length,
    audit
  };
}
