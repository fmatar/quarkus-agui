import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import readline from 'node:readline';
import { fileExists, readFileSafe, parseFrontmatter } from './core.mjs';
import { scaffoldArchlensPolicy } from './arch-trace.mjs';
import { installHooks } from './git-guard.mjs';
import { scaffoldIssueTemplates } from './pm-provider.mjs';

export const DEPRECATED_SDLC_SKILLS = [
  'sdlc-analyze',
  'sdlc-arch-trace',
  'sdlc-docs-wiki',
  'sdlc-feature-spec',
  'sdlc-help',
  'sdlc-init',
  'sdlc-initialize',
  'sdlc-kanban',
  'sdlc-peer-review',
  'sdlc-quality-gate',
  'sdlc-release-changelog',
  'sdlc-review-gate',
  'sdlc-tdd-implement',
  'sdlc-test-coverage'
];

export const CANONICAL_SDLC_SKILLS = [
  'sdlc',
  'sdlc-adr',
  'sdlc-doctor',
  'sdlc-flow',
  'sdlc-gate',
  'sdlc-product',
  'sdlc-release',
  'sdlc-status',
  'sdlc-tdd',
  'sdlc-wiki'
];

export const ARCHLENS_SKILL_PACKAGE = '@fmatar/archlens-skill@1.2.0';

export const DEFAULT_GITIGNORE_ENTRIES = [
  '.agent/.cache/',
  '.agent/.sdlc-session.json',
  '.agent/.receipt-key',
  '.agent/skills/*/scripts/bin/',
  '.agent/workbench/index.html',
  '.agent/arch-trace.jsonl',
  'docs/ARCH_TRACE.md'
];

export function upsertGitignore(targetDir = '.') {
  const gitignorePath = path.resolve(targetDir, '.gitignore');
  const existing = fileExists(gitignorePath) ? readFileSafe(gitignorePath) || '' : '';
  const existingLines = new Set(existing.split('\n').map((l) => l.trim()));
  const missing = DEFAULT_GITIGNORE_ENTRIES.filter((entry) => !existingLines.has(entry));
  if (missing.length === 0 && fileExists(gitignorePath)) {
    return { updated: false, path: gitignorePath };
  }
  const block = `# Agentic SDLC Ephemeral Cache, Binaries & Snapshots\n${missing.join('\n')}\n`;
  const nextContent = existing.trimEnd() ? `${existing.trimEnd()}\n\n${block}` : block;
  fs.writeFileSync(gitignorePath, nextContent, 'utf8');
  return { updated: true, path: gitignorePath, added: missing };
}

export function pinArchlensMcpConfigs(targetDir = '.') {
  const candidates = ['.mcp.json', '.vscode/mcp.json', '.cursor/mcp.json'];
  const updated = [];
  for (const rel of candidates) {
    const abs = path.resolve(targetDir, rel);
    if (!fileExists(abs)) continue;
    const raw = readFileSafe(abs) || '';
    if (raw.includes('"@fmatar/archlens-skill"')) {
      const next = raw.replace(/"@fmatar\/archlens-skill"/g, `"${ARCHLENS_SKILL_PACKAGE}"`);
      fs.writeFileSync(abs, next, 'utf8');
      updated.push(rel);
    }
  }
  return updated;
}

export const SUPPORTED_AGENTS = [
  {
    id: 'gemini',
    name: 'Google Gemini / Antigravity',
    badge: 'Antigravity & Gemini CLI',
    localPaths: ['.agent/skills/<skill>/SKILL.md', '.agent/scripts/', 'AGENTS.md'],
    globalPaths: ['~/.gemini/config/skills/<skill>/SKILL.md', '~/.gemini/config/scripts/'],
    defaultSelected: true
  },
  {
    id: 'claude',
    name: 'Anthropic Claude Code & Cowork',
    badge: '.claude/skills + .claude/commands + .claude/cowork/*.zip + CLAUDE.md',
    localPaths: [
      '.claude/skills/<skill>/SKILL.md',
      '.claude/commands/<skill>.md',
      '.claude/cowork/<skill>.zip',
      '.claude/settings.json',
      '.claude-plugin/plugin.json',
      'CLAUDE.md'
    ],
    globalPaths: [
      '~/.claude/skills/<skill>/SKILL.md',
      '~/.claude/commands/<skill>.md',
      '~/.claude/cowork/<skill>.zip'
    ],
    defaultSelected: true
  },
  {
    id: 'cursor',
    name: 'Cursor IDE',
    badge: '.cursor/rules/*.mdc + .cursorrules',
    localPaths: ['.cursor/rules/agentic-sdlc.mdc', '.cursor/rules/<skill>.mdc', '.cursorrules'],
    globalPaths: ['~/.cursor/rules/agentic-sdlc.mdc'],
    defaultSelected: true
  },
  {
    id: 'grok',
    name: 'xAI Grok CLI / Studio',
    badge: '.grok/skills + .grok/settings.json + GROK.md',
    localPaths: ['.grok/skills/<skill>/SKILL.md', '.grok/settings.json', 'GROK.md'],
    globalPaths: ['~/.grok/skills/<skill>/SKILL.md', '~/.grok/GROK.md'],
    defaultSelected: false
  },
  {
    id: 'windsurf',
    name: 'Windsurf IDE',
    badge: '.windsurf/workflows + .windsurf/rules + .windsurfrules',
    localPaths: ['.windsurf/rules/agentic-sdlc.md', '.windsurf/workflows/<skill>.md', '.windsurfrules'],
    globalPaths: ['~/.codeium/windsurf/memories/global_rules.md'],
    defaultSelected: false
  },
  {
    id: 'copilot',
    name: 'GitHub Copilot & OpenAI Codex',
    badge: '.github/copilot-instructions.md + .github/prompts/*.prompt.md',
    localPaths: ['.github/copilot-instructions.md', '.github/prompts/<skill>.prompt.md', 'AGENTS.md'],
    globalPaths: ['~/.github/copilot-instructions.md'],
    defaultSelected: false
  }
];

export const CONSTITUTIONAL_RULES_SUMMARY = `# Agentic SDLC Framework Invariants & Autonomous Rules
_Generated automatically by @fmatar/agentic-sdlc (Universal Engine v0.1.0)_
_Adheres strictly to Robert C. Martin Clean Code & Clean Architecture_

## 1. Zero Direct Pushes (4-Layer Git Guard — INV-01)
- Direct git commits, merges, or pushes to \`develop\` or \`main\` are STRICTLY FORBIDDEN.
- Every commit requires an active session in \`.agent/.sdlc-session.json\` matching the current branch (\`node .agent/scripts/sdlc-engine.mjs stage begin\`).
- Every merge into \`main\` requires a signed Stage 7 SHA-256 Merkle Gate Receipt in \`docs/AUDIT_CHAIN.jsonl\`.

## 2. PRODUCT.md Constitution & Spec Drift ADR Protocol (INV-03)
- Full-spec project handovers synthesize \`PRODUCT.md\` (\`CAP-XX\`, \`INV-XX\`, \`SPEC-XX\`) starting at Genesis Ticket \`#1\`.
- Any mutation to dependencies, C4 architecture boundaries, DB schemas, or API contracts MUST be accompanied by an ADR (\`node .agent/scripts/sdlc-engine.mjs adr create\`).

## 3. Robert Martin TDD & Dual-Gate Quality Verification (INV-02)
- Diff coverage threshold: >= 85% line and branch coverage.
- Mutation kill score: >= 80% using PITest, Stryker, mutmut, or cargo-mutants.
- Concentric ring Clean Architecture compliance: zero outer-layer imports into domain entities (\`.archlens/policy.json\`).

## 4. Universal Router + 8 Verb Skills Suite (ADR-004)
- \`/sdlc\`: Universal 1:1 subcommand router (\`/sdlc doctor\`, \`/sdlc begin #id\`, \`/sdlc status\`, \`/sdlc drift\`, \`/sdlc pre-pr\`, \`/sdlc gate\`, \`/sdlc kanban\`, \`/sdlc ui\`).
- \`/sdlc-flow\`: End-to-end ticket autopilot (\`stage begin\` -> TDD -> \`stage pre-pr\` -> parallel review -> \`stage complete\`).
- \`/sdlc-product\`: Constitutional \`PRODUCT.md\` autopilot, \`docs/specs/\` architect, and topological DAG waves.
- \`/sdlc-doctor\`: Stack detection, \`doctor --fix\`, 4-Layer Git hooks, \`.archlens/policy.json\`, and multi-agent installer.
- \`/sdlc-status\`: Active session, 8-stage progress, drift check, \`KANBAN.md\` state, and live Architecture Diff UI.
- \`/sdlc-tdd\`: Robert Martin Red-Green-Refactor TDD, polyglot test runners, and real coverage/mutation report parsers.
- \`/sdlc-gate\`: Parallel fork-join PR review subagents, \`gate --strict\`, and Stage 7 SHA-256 Merkle Gate Receipt.
- \`/sdlc-adr\`: Mint, approve, or compact Spec-Drift ADRs (\`adr compact\`) and synchronize \`docs/DECISION_MAP.md\`.
- \`/sdlc-release\`: ADR compaction into \`docs/SRS.md\`, README/Docs/Wiki sync, SemVer tagging, regression proofs, and live canary watchdog.
`;

/**
 * Resolves the root directory of the bundled Agentic SDLC Framework package
 * (whether invoked via `npx github:fmatar/agentic-sdlc-framework` or locally).
 */
// --- Claude Code dialect (canonical skills are written in the Antigravity/Gemini tool vocabulary) ---

export const CLAUDE_TOOL_MAP = {
  read_file: 'Read',
  write_file: 'Write',
  edit_file: 'Edit',
  run_command: 'Bash',
  invoke_subagent: 'Agent',
  list_directory: 'Glob',
  grep_search: 'Grep',
  search_files: 'Grep'
};

const WORKBENCH_HTML = '.agent/workbench/index.html';
const WORKBENCH_NOTE =
  'Claude Code cannot embed HTML inline: tell the user the path of `' + WORKBENCH_HTML + '` so they can open it in a browser.';
const REGENERATE_WORKBENCH = 'regenerate `' + WORKBENCH_HTML + '` with `node .agent/scripts/sdlc-engine.mjs ui-snapshot`';
const EMBED_TAG = '<agent-embed[^>]*>(?:<\\/agent-embed>)?';

/**
 * Rewrites Antigravity tool names, `{{args}}` and `<agent-embed>` instructions into
 * Claude Code conventions (Read/Write/Edit/Bash/Agent, $ARGUMENTS, file path hand-off).
 */
export function toClaudeDialect(text = '') {
  let out = String(text);
  out = out.replace(/\{\{args\}\}/g, '$ARGUMENTS');
  out = out.replace(/\bAntigravity Inline\b/g, 'Inline');
  // Artifact-directory copies + inline embeds have no Claude Code equivalent.
  out = out.replace(/\s*\(with `ArtifactMetadata:[^\n]*?\}`\)/g, '');
  out = out.replace(/\s*\(`UserFacing: true`\)/g, '');
  out = out.replace(/`?<artifactDir>\/sdlc-architecture-diff\.html`?/g, '`' + WORKBENCH_HTML + '`');
  out = out.replace(/`?file:\/\/\/`?/g, '');
  out = out.replace(new RegExp('```[a-z]*\\n\\s*(' + EMBED_TAG + ')\\s*\\n```', 'g'), '$1');
  out = out.replace(
    /(?:copy|write|refresh)\s+`\.agent\/workbench\/index\.html`\s+(?:into|to|from)\s+[^\n`]*?`\.agent\/workbench\/index\.html`\)?(?:\s*(?:using|via)\s+`write_file`)?/gi,
    (m) => (m[0] === m[0].toUpperCase() ? 'R' : 'r') + REGENERATE_WORKBENCH.slice(1)
  );
  out = out.replace(new RegExp('\\s*\\+\\s*embed\\s*`' + EMBED_TAG + '`', 'g'), ' (then hand the user the path of `' + WORKBENCH_HTML + '`)');
  out = out.replace(
    new RegExp('\\s*(?:and\\s+)?(?:render it inline in your (?:chat )?response(?: via)?|embed it directly in your response|embed)\\s*:?\\s*`?' + EMBED_TAG + '`?\\.?', 'g'),
    '. ' + WORKBENCH_NOTE
  );
  out = out.replace(new RegExp(EMBED_TAG, 'g'), WORKBENCH_NOTE);
  for (const [from, to] of Object.entries(CLAUDE_TOOL_MAP)) {
    out = out.replace(new RegExp('\\b' + from + '\\b', 'g'), to);
  }
  return out.replace(/`\.\s*\.\s/g, '`. ').replace(/:\s*\.\s/g, '. ');
}

/** Renders a canonical skill as a Claude Code SKILL.md (allowed-tools frontmatter, translated body). */
export function toClaudeSkill(sk) {
  const parsed = parseFrontmatter(sk.raw || '');
  const fm = parsed.frontmatter || {};
  const allowedTools = Array.from(
    new Set(
      String(fm.tools || fm['allowed-tools'] || sk.tools || '')
        .split(',')
        .map((t) => t.trim())
        .filter(Boolean)
        .map((t) => CLAUDE_TOOL_MAP[t] || t)
    )
  );
  const description = toClaudeDialect(fm.description || sk.description || sk.id);
  const body = toClaudeDialect(parsed.body || sk.body || '');
  const lines = ['---', `name: ${fm.name || sk.id}`, `description: ${description}`];
  if (allowedTools.length) lines.push(`allowed-tools: ${allowedTools.join(', ')}`);
  lines.push('---', '', body, '');
  return { id: sk.id, description, body, allowedTools, markdown: lines.join('\n') };
}

const SDLC_BLOCK_BEGIN = '<!-- agentic-sdlc:begin -->';
const SDLC_BLOCK_END = '<!-- agentic-sdlc:end -->';

/**
 * Writes the constitutional summary into a rules file (CLAUDE.md, .cursorrules) WITHOUT
 * discarding project content: a missing/empty file gets the summary; an existing file gets a
 * marked block appended once (headings demoted one level) and updated in place on re-install.
 */
export function mergeRulesFile(filePath, summary = CONSTITUTIONAL_RULES_SUMMARY) {
  const existing = fileExists(filePath) ? readFileSafe(filePath) || '' : '';
  if (!existing.trim()) {
    fs.writeFileSync(filePath, summary, 'utf8');
    return { path: filePath, mode: 'created' };
  }
  const demoted = summary.replace(/^(#{1,5}) /gm, '#$1 ').trimEnd();
  const block = `${SDLC_BLOCK_BEGIN}\n${demoted}\n${SDLC_BLOCK_END}`;
  const b = existing.indexOf(SDLC_BLOCK_BEGIN);
  const e = existing.indexOf(SDLC_BLOCK_END);
  let next;
  let mode;
  if (b !== -1 && e > b) {
    next = existing.slice(0, b) + block + existing.slice(e + SDLC_BLOCK_END.length);
    mode = 'updated';
  } else if (existing.trimStart().startsWith(summary.split('\n')[0])) {
    next = summary;
    mode = 'replaced';
  } else {
    next = `${existing.trimEnd()}\n\n---\n\n${block}\n`;
    mode = 'appended';
  }
  if (next !== existing) fs.writeFileSync(filePath, next, 'utf8');
  return { path: filePath, mode };
}

export function resolvePackageRoot() {
  return path.resolve(import.meta.dirname, '../../..');
}

/**
 * Removes deprecated pre-v0.1.0 sdlc-* skill directories from any target directory.
 */
export function pruneDeprecatedSkills(skillsDir) {
  if (!skillsDir || !fileExists(skillsDir)) return [];
  const pruned = [];
  for (const deprecated of DEPRECATED_SDLC_SKILLS) {
    const candidate = path.join(skillsDir, deprecated);
    if (fileExists(candidate)) {
      try {
        fs.rmSync(candidate, { recursive: true, force: true });
        pruned.push(deprecated);
      } catch {}
    }
    const candidateMd = path.join(skillsDir, `${deprecated}.md`);
    if (fileExists(candidateMd)) {
      try {
        fs.unlinkSync(candidateMd);
        pruned.push(`${deprecated}.md`);
      } catch {}
    }
    const candidateMdc = path.join(skillsDir, `${deprecated}.mdc`);
    if (fileExists(candidateMdc)) {
      try {
        fs.unlinkSync(candidateMdc);
        pruned.push(`${deprecated}.mdc`);
      } catch {}
    }
  }
  return pruned;
}

function copyDirRecursive(srcDir, destDir) {
  if (!fileExists(srcDir)) return 0;
  fs.mkdirSync(destDir, { recursive: true });
  let count = 0;
  const entries = fs.readdirSync(srcDir, { withFileTypes: true });
  for (const entry of entries) {
    if (entry.name === '.cache' || entry.name === '.sdlc-session.json' || entry.name === '.DS_Store') {
      continue;
    }
    const srcPath = path.join(srcDir, entry.name);
    const destPath = path.join(destDir, entry.name);
    if (entry.isDirectory()) {
      count += copyDirRecursive(srcPath, destPath);
    } else if (entry.isFile()) {
      fs.copyFileSync(srcPath, destPath);
      if (destPath.endsWith('.sh') || destPath.endsWith('.mjs')) {
        try {
          fs.chmodSync(destPath, 0o755);
        } catch {}
      }
      count++;
    }
  }
  return count;
}

function loadCanonicalSkills(packageRoot = resolvePackageRoot()) {
  const skillsRoot = path.join(packageRoot, '.agent/skills');
  const loaded = [];
  for (const skillName of CANONICAL_SDLC_SKILLS) {
    const skillPath = path.join(skillsRoot, skillName, 'SKILL.md');
    if (fileExists(skillPath)) {
      const raw = readFileSafe(skillPath) || '';
      const parsed = parseFrontmatter(raw);
      loaded.push({
        id: skillName,
        description: parsed.frontmatter.description || skillName,
        raw,
        body: parsed.body || raw
      });
    }
  }
  return loaded;
}

export function normalizeAgentList(agentsInput = ['gemini', 'claude', 'cursor']) {
  const validIds = new Set(SUPPORTED_AGENTS.map((a) => a.id));
  const aliasMap = {
    antigravity: 'gemini',
    'google-gemini': 'gemini',
    'claude-code': 'claude',
    cowork: 'claude',
    anthropic: 'claude',
    xai: 'grok',
    'grok-cli': 'grok',
    codeium: 'windsurf',
    opencode: 'windsurf',
    'github-copilot': 'copilot',
    codex: 'copilot',
    openai: 'copilot'
  };

  const rawList = Array.isArray(agentsInput)
    ? agentsInput
    : String(agentsInput || '')
        .split(',')
        .map((s) => s.trim().toLowerCase())
        .filter(Boolean);

  if (rawList.includes('all') || rawList.includes('*')) {
    return SUPPORTED_AGENTS.map((a) => a.id);
  }

  const normalized = [];
  for (const item of rawList) {
    const key = aliasMap[item] || item;
    if (validIds.has(key) && !normalized.includes(key)) {
      normalized.push(key);
    }
  }

  return normalized.length > 0 ? normalized : ['gemini', 'claude', 'cursor'];
}

const SDLC_RULES_START = '<!-- AGENTIC-SDLC-RULES:START -->';
const SDLC_RULES_END = '<!-- AGENTIC-SDLC-RULES:END -->';

/**
 * Non-destructively creates or updates a markdown rules file (e.g. CLAUDE.md, AGENTS.md, GROK.md).
 * Preserves any existing project-specific content outside the managed block.
 */
export function upsertConstitutionalRulesFile(filePath, rulesContent = CONSTITUTIONAL_RULES_SUMMARY) {
  const managedBlock = `${SDLC_RULES_START}\n${rulesContent.trim()}\n${SDLC_RULES_END}`;
  fs.mkdirSync(path.dirname(filePath), { recursive: true });

  if (!fileExists(filePath)) {
    fs.writeFileSync(filePath, `${managedBlock}\n`, 'utf8');
    return { created: true, updated: false };
  }

  const existing = readFileSafe(filePath) || '';
  const startIdx = existing.indexOf(SDLC_RULES_START);
  const endIdx = existing.indexOf(SDLC_RULES_END);

  if (startIdx !== -1 && endIdx !== -1 && endIdx > startIdx) {
    const before = existing.slice(0, startIdx);
    const after = existing.slice(endIdx + SDLC_RULES_END.length);
    fs.writeFileSync(filePath, `${before}${managedBlock}${after}`, 'utf8');
    return { created: false, updated: true };
  }

  // Legacy install without markers: if file is purely the generated header block, wrap it
  if (existing.trim().startsWith('# Agentic SDLC Framework Invariants')) {
    fs.writeFileSync(filePath, `${managedBlock}\n`, 'utf8');
    return { created: false, updated: true };
  }

  // Preserve custom project instructions and append the managed block
  const prefix = existing.trimEnd();
  const combined = prefix ? `${prefix}\n\n${managedBlock}\n` : `${managedBlock}\n`;
  fs.writeFileSync(filePath, combined, 'utf8');
  return { created: false, updated: true };
}

/**
 * Non-destructively updates `.claude/settings.json` with valid Claude Code schema keys
 * (`$schema`, `permissions.allow`, `env`) while stripping legacy invalid keys (`rules`, `skillsDir`, `commands`)
 * and preserving existing user settings (`model`, `hooks`, `enabledPlugins`, custom `permissions`).
 */
export function upsertClaudeSettingsJson(settingsPath) {
  let existing = {};
  if (fileExists(settingsPath)) {
    try {
      const parsed = JSON.parse(readFileSafe(settingsPath) || '{}');
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        existing = { ...parsed };
      }
    } catch {}
  }

  delete existing.rules;
  delete existing.skillsDir;
  delete existing.commands;

  const currentPermissions =
    existing.permissions && typeof existing.permissions === 'object' && !Array.isArray(existing.permissions)
      ? { ...existing.permissions }
      : {};
  const allowSet = new Set(Array.isArray(currentPermissions.allow) ? currentPermissions.allow : []);
  allowSet.add('Bash(node .agent/scripts/sdlc-engine.mjs *)');
  allowSet.add('Bash(node .agent/scripts/sdlc-engine.mjs:*)');
  allowSet.add('Bash(.agent/scripts/kanban-sync.sh *)');
  allowSet.add('Bash(.agent/scripts/kanban-sync.sh:*)');
  currentPermissions.allow = Array.from(allowSet);

  const currentEnv =
    existing.env && typeof existing.env === 'object' && !Array.isArray(existing.env) ? { ...existing.env } : {};
  currentEnv.SDLC_FRAMEWORK = currentEnv.SDLC_FRAMEWORK || '1';

  const merged = {
    $schema: existing.$schema || 'https://json.schemastore.org/claude-code-settings.json',
    ...existing,
    permissions: currentPermissions,
    env: currentEnv
  };

  fs.mkdirSync(path.dirname(settingsPath), { recursive: true });
  fs.writeFileSync(settingsPath, JSON.stringify(merged, null, 2) + '\n', 'utf8');
  return merged;
}

/**
 * Adapts a canonical skill definition for a specific agent runtime.
 * - `gemini` preserves Antigravity-native `tools:` frontmatter and `<agent-embed>` directives.
 * - `claude` (Claude Code & Cowork) and other runtimes strip Antigravity `tools:` frontmatter
 *   and rewrite `<agent-embed>`, `<artifactDir>`, and Antigravity tool identifiers (`read_file`,
 *   `write_file`, `edit_file`, `run_command`, `invoke_subagent`) into runtime-appropriate device-shell
 *   and `ui-snapshot` instructions.
 */
export function adaptSkillForAgent(skill, agentId = 'claude') {
  if (agentId === 'gemini') {
    return {
      skillMarkdown: skill.raw,
      coworkSkillMarkdown: skill.raw,
      commandMarkdown: `---\ndescription: ${skill.description}\n---\n\n${skill.body}\n`,
      body: skill.body
    };
  }

  if (agentId === 'claude') {
    const claudeSkill = toClaudeSkill(skill);
    const cmdFrontmatter = [`description: ${claudeSkill.description}`];
    if (claudeSkill.allowedTools.length) cmdFrontmatter.push(`allowed-tools: ${claudeSkill.allowedTools.join(', ')}`);
    if (skill.id === 'sdlc') cmdFrontmatter.push('argument-hint: <subcommand> [args]');
    const commandMarkdown = `---\n${cmdFrontmatter.join('\n')}\n---\n\n${claudeSkill.body}\n`;

    return {
      skillMarkdown: claudeSkill.markdown,
      coworkSkillMarkdown: claudeSkill.markdown,
      commandMarkdown,
      body: claudeSkill.body,
      allowedTools: claudeSkill.allowedTools
    };
  }

  let body = String(skill.body || '').trim();

  // 1. Replace template placeholders with Claude slash command $ARGUMENTS convention
  body = body.replace(/\{\{args\}\}/g, '$ARGUMENTS');

  // 2. Rewrite /sdlc router table & execution protocol for Claude Code + Cowork device shell
  body = body.replace(
    /\|\s*`\/sdlc ui`\s*\/\s*`\/sdlc serve`\s*\|\s*`node \.agent\/scripts\/sdlc-engine\.mjs ui-snapshot`[^\n]*\|/g,
    '| `/sdlc ui` / `/sdlc serve` | `node .agent/scripts/sdlc-engine.mjs ui-snapshot` (generates `.agent/workbench/index.html`; publish or open the snapshot instead of blocking on `serve`) |'
  );

  body = body.replace(
    /2\. Execute the mapped `node \.agent\/scripts\/sdlc-engine\.mjs \.\.\.` command via `run_command`\.\s*3\. If the subcommand is `begin`, `pre-pr`, `ui`, or `status`, also refresh `<artifactDir>\/sdlc-architecture-diff\.html`[\s\S]*?<\/agent-embed>`\./g,
    `2. Execute the mapped \`node .agent/scripts/sdlc-engine.mjs ...\` command in the device shell / Bash tool against the connected project folder root (pass \`--json\` if structured JSON is needed).\n3. If the subcommand is \`begin\`, \`pre-pr\`, \`ui\`, \`serve\`, or \`status\`, run \`node .agent/scripts/sdlc-engine.mjs ui-snapshot\` to build \`.agent/workbench/index.html\` and publish or share \`.agent/workbench/index.html\` with the user (in Cowork, \`serve\` loopback ports are unreachable from the remote viewer, so always publish the self-contained \`ui-snapshot\` HTML artifact instead).\n4. Note: \`install\` and \`interop export\` update \`CLAUDE.md\` and \`.claude/settings.json\` non-destructively, preserving existing project instructions and valid settings.`
  );

  // 3. Rewrite Antigravity inline UI presentation blocks in sdlc-flow, sdlc-product, sdlc-status
  body = body.replace(
    /\*\*Mandatory Antigravity Inline Architecture UI Presentation\*\*:[\s\S]*?```\s*markdown\s*\n<agent-embed[^\n]*\n```/g,
    `**Architecture UI Snapshot Presentation**:\nAfter running \`stage begin\` or \`stage pre-pr\`, \`.agent/workbench/index.html\` is generated automatically (or refresh it via \`node .agent/scripts/sdlc-engine.mjs ui-snapshot\`). Publish or present \`.agent/workbench/index.html\` to the user (in Cowork, publish the static HTML snapshot rather than running \`serve\`).`
  );

  body = body.replace(
    /3\. \*\*Antigravity Inline UI Presentation\*\*:\s*\n\s*Write `\.agent\/workbench\/index\.html` to `<artifactDir>\/sdlc-architecture-diff\.html`[^\n]*/g,
    `3. **Architecture & Decision Lineage UI Snapshot**:\n   Publish or present the generated \`.agent/workbench/index.html\` snapshot from \`ui-snapshot\` so the user can inspect the interactive C4, DAG, and ADR lineage view.`
  );

  body = body.replace(
    /## 2\. Inline Architecture UI Presentation[\s\S]*?```\s*markdown\s*\n<agent-embed[^\n]*\n```/g,
    `## 2. Architecture UI Snapshot Presentation\nAfter running \`node .agent/scripts/sdlc-engine.mjs ui-snapshot\`, publish or share \`.agent/workbench/index.html\` so the user can inspect the 8-stage dashboard, Kanban state, and Archlens concentric ring diff (in Cowork, publish \`.agent/workbench/index.html\` directly instead of starting \`serve\`).`
  );

  // 4. Rewrite invoke_subagent blocks in sdlc-flow and sdlc-gate
  body = body.replace(
    /Dispatch two `invoke_subagent` calls in parallel using valid tool arguments:/g,
    'Dispatch two independent review tasks in parallel using your subagent / Task tool (or execute both structured review passes sequentially if subagents are unavailable):'
  );
  body = body.replace(
    /Dispatch two independent read-only subagents in parallel using `invoke_subagent` \(`TypeName: "research"`\):/g,
    'Dispatch two independent read-only review tasks in parallel using your subagent / Task tool (or execute both structured review passes sequentially if subagents are unavailable):'
  );
  body = body.replace(/^\s*-\s*`TypeName`:\s*`?"research"`?\r?\n/gm, '');
  body = body.replace(
    /\(start the live, token-protected workbench yourself with `sdlc-engine serve`\)/g,
    '(publish or open `.agent/workbench/index.html` directly; in Cowork, use `ui-snapshot` instead of `serve`)'
  );

  // 5. Rewrite remaining Antigravity tool references in sdlc-wiki or other skills
  body = body.replace(
    /Use `read_file`, `write_file`, `edit_file`, and `run_command` to/g,
    'Use your file inspection, file editing, and shell execution tools to'
  );
  body = body.replace(/using `read_file` and `wiki map`/g, 'by inspecting the source files and running `wiki map`');
  body = body.replace(/\b(read_file|write_file|edit_file|run_command|invoke_subagent)\b/g, (m) => {
    const map = {
      read_file: 'file-read',
      write_file: 'file-write',
      edit_file: 'file-edit',
      run_command: 'shell-command',
      invoke_subagent: 'subagent-task'
    };
    return map[m] || m;
  });
  body = body.replace(/<agent-embed[^>]*><\/agent-embed>/g, '`.agent/workbench/index.html`');
  body = body.replace(/<artifactDir>/g, '.agent/workbench');

  const safeDesc = String(skill.description || skill.id)
    .replace(/\{\{args\}\}/g, '$ARGUMENTS')
    .replace(/\r?\n/g, ' ')
    .trim();
  const quotedDesc = JSON.stringify(safeDesc);
  const argHint = skill.id === 'sdlc' ? '"<subcommand> [flags]"' : '"[args]"';

  const skillMarkdown = `---\nname: ${skill.id}\ndescription: ${quotedDesc}\nuser-invocable: true\nargument-hint: ${argHint}\n---\n\n${body}\n`;
  const coworkSkillMarkdown = `---\nname: ${skill.id}\ndescription: ${quotedDesc}\n---\n\n${body}\n`;
  const commandMarkdown = `---\ndescription: ${quotedDesc}\nargument-hint: ${argHint}\n---\n\n${body}\n`;

  return {
    skillMarkdown,
    coworkSkillMarkdown,
    commandMarkdown,
    body
  };
}

let CRC32_TABLE = null;
function crc32(buf) {
  if (!CRC32_TABLE) {
    CRC32_TABLE = new Uint32Array(256);
    for (let i = 0; i < 256; i++) {
      let c = i;
      for (let k = 0; k < 8; k++) {
        c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
      }
      CRC32_TABLE[i] = c >>> 0;
    }
  }
  let crc = 0xffffffff;
  for (let i = 0; i < buf.length; i++) {
    crc = CRC32_TABLE[(crc ^ buf[i]) & 0xff] ^ (crc >>> 8);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

/**
 * Builds a standards-compliant Stored ZIP archive containing `<skillId>/SKILL.md`
 * ready to upload directly into Claude Cowork / Claude.ai `Customize > Skills`.
 */
export function createSkillZipBuffer(skillId, skillMarkdown) {
  const entryName = Buffer.from(`${skillId}/SKILL.md`, 'utf8');
  const data = Buffer.from(skillMarkdown, 'utf8');
  const checksum = crc32(data);
  const size = data.length;

  // Local file header (30 bytes + entryName)
  const localHeader = Buffer.alloc(30 + entryName.length);
  localHeader.writeUInt32LE(0x04034b50, 0); // PK\x03\x04
  localHeader.writeUInt16LE(20, 4); // Version needed (2.0)
  localHeader.writeUInt16LE(0x0800, 6); // UTF-8 filename flag
  localHeader.writeUInt16LE(0, 8); // Compression method: 0 (Stored)
  localHeader.writeUInt16LE(0, 10); // Mod time
  localHeader.writeUInt16LE(0x5421, 12); // Mod date
  localHeader.writeUInt32LE(checksum, 14);
  localHeader.writeUInt32LE(size, 18); // Compressed size
  localHeader.writeUInt32LE(size, 22); // Uncompressed size
  localHeader.writeUInt16LE(entryName.length, 26);
  localHeader.writeUInt16LE(0, 28); // Extra field length
  entryName.copy(localHeader, 30);

  // Central directory header (46 bytes + entryName)
  const centralHeader = Buffer.alloc(46 + entryName.length);
  centralHeader.writeUInt32LE(0x02014b50, 0); // PK\x01\x02
  centralHeader.writeUInt16LE(20, 4); // Version made by
  centralHeader.writeUInt16LE(20, 6); // Version needed
  centralHeader.writeUInt16LE(0x0800, 8); // UTF-8 flag
  centralHeader.writeUInt16LE(0, 10); // Stored
  centralHeader.writeUInt16LE(0, 12);
  centralHeader.writeUInt16LE(0x5421, 14);
  centralHeader.writeUInt32LE(checksum, 16);
  centralHeader.writeUInt32LE(size, 20);
  centralHeader.writeUInt32LE(size, 24);
  centralHeader.writeUInt16LE(entryName.length, 28);
  centralHeader.writeUInt16LE(0, 30); // Extra length
  centralHeader.writeUInt16LE(0, 32); // Comment length
  centralHeader.writeUInt16LE(0, 34); // Disk number
  centralHeader.writeUInt16LE(0, 36); // Internal attrs
  centralHeader.writeUInt32LE(0, 38); // External attrs
  centralHeader.writeUInt32LE(0, 42); // Offset of local header
  entryName.copy(centralHeader, 46);

  // End of Central Directory (22 bytes)
  const centralOffset = localHeader.length + data.length;
  const eocd = Buffer.alloc(22);
  eocd.writeUInt32LE(0x06054b50, 0); // PK\x05\x06
  eocd.writeUInt16LE(0, 4); // Number of this disk
  eocd.writeUInt16LE(0, 6); // Disk where central directory starts
  eocd.writeUInt16LE(1, 8); // Total entries on this disk
  eocd.writeUInt16LE(1, 10); // Total entries
  eocd.writeUInt32LE(centralHeader.length, 12); // Size of central directory
  eocd.writeUInt32LE(centralOffset, 16); // Offset of central directory
  eocd.writeUInt16LE(0, 20); // Comment length

  return Buffer.concat([localHeader, data, centralHeader, eocd]);
}

/**
 * Provisions native skills, slash commands, Cowork account skill packages, and constitutional rule files
 * for the selected AI coding tools in either local project or global user scope.
 */
export function provisionAgentIntegrations({
  agents = ['gemini', 'claude', 'cursor'],
  targetDir = '.',
  global = false,
  homeDir = os.homedir(),
  packageRoot = resolvePackageRoot()
} = {}) {
  const selectedIds = normalizeAgentList(agents);
  const skills = loadCanonicalSkills(packageRoot);
  const absTarget = path.resolve(targetDir);
  const filesCreated = [];
  const prunedSkills = [];

  // Always prune deprecated skills in common project/global directories
  if (global) {
    prunedSkills.push(...pruneDeprecatedSkills(path.join(homeDir, '.gemini/config/skills')));
    prunedSkills.push(...pruneDeprecatedSkills(path.join(homeDir, '.claude/skills')));
    prunedSkills.push(...pruneDeprecatedSkills(path.join(homeDir, '.claude/commands')));
    prunedSkills.push(...pruneDeprecatedSkills(path.join(homeDir, '.grok/skills')));
  } else {
    prunedSkills.push(...pruneDeprecatedSkills(path.join(absTarget, '.agent/skills')));
    prunedSkills.push(...pruneDeprecatedSkills(path.join(absTarget, '.agents/skills')));
    prunedSkills.push(...pruneDeprecatedSkills(path.join(absTarget, '.claude/skills')));
    prunedSkills.push(...pruneDeprecatedSkills(path.join(absTarget, '.claude/commands')));
    prunedSkills.push(...pruneDeprecatedSkills(path.join(absTarget, '.grok/skills')));
    prunedSkills.push(...pruneDeprecatedSkills(path.join(absTarget, '.cursor/rules')));
    prunedSkills.push(...pruneDeprecatedSkills(path.join(absTarget, '.windsurf/workflows')));
  }

  for (const agentId of selectedIds) {
    if (agentId === 'gemini') {
      const baseSkillsDir = global
        ? path.join(homeDir, '.gemini/config/skills')
        : path.join(absTarget, '.agent/skills');
      for (const sk of skills) {
        const destFile = path.join(baseSkillsDir, sk.id, 'SKILL.md');
        fs.mkdirSync(path.dirname(destFile), { recursive: true });
        fs.writeFileSync(destFile, sk.raw, 'utf8');
        filesCreated.push(global ? `~/.gemini/config/skills/${sk.id}/SKILL.md` : `.agent/skills/${sk.id}/SKILL.md`);
      }
      if (!global) {
        const agentsMdPath = path.join(absTarget, 'AGENTS.md');
        upsertConstitutionalRulesFile(agentsMdPath, CONSTITUTIONAL_RULES_SUMMARY);
        filesCreated.push('AGENTS.md');
      }
    }

    if (agentId === 'claude') {
      const claudeRoot = global ? path.join(homeDir, '.claude') : path.join(absTarget, '.claude');
      const claudeSkillsDir = path.join(claudeRoot, 'skills');
      const claudeCommandsDir = path.join(claudeRoot, 'commands');
      const claudeCoworkDir = path.join(claudeRoot, 'cowork');
      fs.mkdirSync(claudeSkillsDir, { recursive: true });
      fs.mkdirSync(claudeCommandsDir, { recursive: true });
      fs.mkdirSync(claudeCoworkDir, { recursive: true });

      for (const sk of skills) {
        const adapted = adaptSkillForAgent(sk, 'claude');
        const skillFile = path.join(claudeSkillsDir, sk.id, 'SKILL.md');
        fs.mkdirSync(path.dirname(skillFile), { recursive: true });
        fs.writeFileSync(skillFile, adapted.skillMarkdown, 'utf8');
        filesCreated.push(global ? `~/.claude/skills/${sk.id}/SKILL.md` : `.claude/skills/${sk.id}/SKILL.md`);

        const cmdFile = path.join(claudeCommandsDir, `${sk.id}.md`);
        fs.writeFileSync(cmdFile, adapted.commandMarkdown, 'utf8');
        filesCreated.push(global ? `~/.claude/commands/${sk.id}.md` : `.claude/commands/${sk.id}.md`);

        const zipFile = path.join(claudeCoworkDir, `${sk.id}.zip`);
        fs.writeFileSync(zipFile, createSkillZipBuffer(sk.id, adapted.coworkSkillMarkdown));
        filesCreated.push(global ? `~/.claude/cowork/${sk.id}.zip` : `.claude/cowork/${sk.id}.zip`);
      }

      if (!global) {
        const claudeMdPath = path.join(absTarget, 'CLAUDE.md');
        upsertConstitutionalRulesFile(claudeMdPath, CONSTITUTIONAL_RULES_SUMMARY);
        filesCreated.push('CLAUDE.md');

        const settingsPath = path.join(claudeRoot, 'settings.json');
        upsertClaudeSettingsJson(settingsPath);
        filesCreated.push('.claude/settings.json');

        // Provision Claude plugin manifest so Claude Code & Cowork can load skills via plugin picker
        const pluginManifestPath = path.join(absTarget, '.claude-plugin', 'plugin.json');
        fs.mkdirSync(path.dirname(pluginManifestPath), { recursive: true });
        const pluginManifest = {
          name: 'agentic-sdlc',
          version: '0.1.0',
          description: 'Agentic SDLC Framework — Universal /sdlc router and 9 lifecycle skills for Claude Code & Cowork',
          skills: './.claude/skills',
          commands: './.claude/commands'
        };
        fs.writeFileSync(pluginManifestPath, JSON.stringify(pluginManifest, null, 2) + '\n', 'utf8');
        filesCreated.push('.claude-plugin/plugin.json');
      }
    }

    if (agentId === 'cursor') {
      const cursorRulesDir = global
        ? path.join(homeDir, '.cursor/rules')
        : path.join(absTarget, '.cursor/rules');
      fs.mkdirSync(cursorRulesDir, { recursive: true });

      const masterMdc = path.join(cursorRulesDir, 'agentic-sdlc.mdc');
      fs.writeFileSync(
        masterMdc,
        `---\ndescription: Agentic SDLC Framework Lifecycle, TDD, and Dual-Gate Quality Rules\nglobs: *\nalwaysApply: true\n---\n\n${CONSTITUTIONAL_RULES_SUMMARY}\n`,
        'utf8'
      );
      filesCreated.push(global ? '~/.cursor/rules/agentic-sdlc.mdc' : '.cursor/rules/agentic-sdlc.mdc');

      for (const sk of skills) {
        const adapted = adaptSkillForAgent(sk, 'cursor');
        const skillMdc = path.join(cursorRulesDir, `${sk.id}.mdc`);
        fs.writeFileSync(
          skillMdc,
          `---\ndescription: ${sk.description}\nglobs: *\nalwaysApply: false\n---\n\n${adapted.body}\n`,
          'utf8'
        );
        filesCreated.push(global ? `~/.cursor/rules/${sk.id}.mdc` : `.cursor/rules/${sk.id}.mdc`);
      }

      if (!global) {
        const cursorRulesLegacy = path.join(absTarget, '.cursorrules');
        upsertConstitutionalRulesFile(cursorRulesLegacy, CONSTITUTIONAL_RULES_SUMMARY);
        filesCreated.push('.cursorrules');
      }
    }

    if (agentId === 'grok') {
      const grokRoot = global ? path.join(homeDir, '.grok') : path.join(absTarget, '.grok');
      const grokSkillsDir = path.join(grokRoot, 'skills');
      fs.mkdirSync(grokSkillsDir, { recursive: true });

      for (const sk of skills) {
        const adapted = adaptSkillForAgent(sk, 'grok');
        const destFile = path.join(grokSkillsDir, sk.id, 'SKILL.md');
        fs.mkdirSync(path.dirname(destFile), { recursive: true });
        fs.writeFileSync(destFile, adapted.skillMarkdown, 'utf8');
        filesCreated.push(global ? `~/.grok/skills/${sk.id}/SKILL.md` : `.grok/skills/${sk.id}/SKILL.md`);
      }

      const grokMdPath = global ? path.join(grokRoot, 'GROK.md') : path.join(absTarget, 'GROK.md');
      upsertConstitutionalRulesFile(grokMdPath, CONSTITUTIONAL_RULES_SUMMARY);
      filesCreated.push(global ? '~/.grok/GROK.md' : 'GROK.md');

      const grokSettingsPath = path.join(grokRoot, 'settings.json');
      fs.writeFileSync(
        grokSettingsPath,
        JSON.stringify(
          {
            instructionsFile: global ? '~/.grok/GROK.md' : 'GROK.md',
            skillsDirectory: global ? '~/.grok/skills' : '.grok/skills',
            engineCommand: 'node .agent/scripts/sdlc-engine.mjs'
          },
          null,
          2
        ),
        'utf8'
      );
      filesCreated.push(global ? '~/.grok/settings.json' : '.grok/settings.json');
    }

    if (agentId === 'windsurf') {
      if (global) {
        const wsGlobal = path.join(homeDir, '.codeium/windsurf/memories/global_rules.md');
        upsertConstitutionalRulesFile(wsGlobal, CONSTITUTIONAL_RULES_SUMMARY);
        filesCreated.push('~/.codeium/windsurf/memories/global_rules.md');
      } else {
        const wsRulesDir = path.join(absTarget, '.windsurf/rules');
        const wsWorkflowsDir = path.join(absTarget, '.windsurf/workflows');
        fs.mkdirSync(wsRulesDir, { recursive: true });
        fs.mkdirSync(wsWorkflowsDir, { recursive: true });

        fs.writeFileSync(path.join(wsRulesDir, 'agentic-sdlc.md'), CONSTITUTIONAL_RULES_SUMMARY, 'utf8');
        filesCreated.push('.windsurf/rules/agentic-sdlc.md');

        for (const sk of skills) {
          const adapted = adaptSkillForAgent(sk, 'windsurf');
          const wfPath = path.join(wsWorkflowsDir, `${sk.id}.md`);
          fs.writeFileSync(wfPath, `---\ndescription: ${sk.description}\n---\n\n${adapted.body}\n`, 'utf8');
          filesCreated.push(`.windsurf/workflows/${sk.id}.md`);
        }

        const wsLegacy = path.join(absTarget, '.windsurfrules');
        upsertConstitutionalRulesFile(wsLegacy, CONSTITUTIONAL_RULES_SUMMARY);
        filesCreated.push('.windsurfrules');
      }
    }

    if (agentId === 'copilot') {
      const ghDir = global ? path.join(homeDir, '.github') : path.join(absTarget, '.github');
      const promptsDir = path.join(ghDir, 'prompts');
      fs.mkdirSync(promptsDir, { recursive: true });

      const copilotInstr = path.join(ghDir, 'copilot-instructions.md');
      upsertConstitutionalRulesFile(copilotInstr, CONSTITUTIONAL_RULES_SUMMARY);
      filesCreated.push(global ? '~/.github/copilot-instructions.md' : '.github/copilot-instructions.md');

      for (const sk of skills) {
        const adapted = adaptSkillForAgent(sk, 'copilot');
        const promptFile = path.join(promptsDir, `${sk.id}.prompt.md`);
        fs.writeFileSync(
          promptFile,
          `---\nmode: 'agent'\ndescription: '${sk.description.replace(/'/g, "''")}'\n---\n\n${adapted.body}\n`,
          'utf8'
        );
        filesCreated.push(global ? `~/.github/prompts/${sk.id}.prompt.md` : `.github/prompts/${sk.id}.prompt.md`);
      }

      if (!global) {
        const agentsMdPath = path.join(absTarget, 'AGENTS.md');
        if (!fileExists(agentsMdPath)) {
          upsertConstitutionalRulesFile(agentsMdPath, CONSTITUTIONAL_RULES_SUMMARY);
          filesCreated.push('AGENTS.md');
        }
      }
    }
  }

  return {
    selectedAgents: selectedIds,
    skillsCount: skills.length,
    filesCreated,
    prunedSkills: Array.from(new Set(prunedSkills))
  };
}

/**
 * Interactive ANSI Multi-Select & Single-Select TUI Wizard using Node's built-in `readline`.
 */
async function promptMultiSelect(title, subtitle, items) {
  return new Promise((resolve) => {
    let cursor = 0;
    const selected = new Set(items.filter((i) => i.defaultSelected).map((_, idx) => idx));

    const render = (first = false) => {
      if (!first) {
        process.stdout.write(`\x1b[${items.length + 3}A\x1b[0J`);
      }
      process.stdout.write(`\x1b[1m\x1b[36m? ${title}\x1b[0m \x1b[2m${subtitle}\x1b[0m\n`);
      for (let i = 0; i < items.length; i++) {
        const isCur = i === cursor;
        const isSel = selected.has(i);
        const pointer = isCur ? '\x1b[36m❯\x1b[0m' : ' ';
        const checkbox = isSel ? '\x1b[32m◉\x1b[0m' : '\x1b[2m◯\x1b[0m';
        const label = isCur ? `\x1b[1m${items[i].label}\x1b[0m` : items[i].label;
        const hint = items[i].hint ? ` \x1b[2m(${items[i].hint})\x1b[0m` : '';
        process.stdout.write(`  ${pointer} ${checkbox} ${label}${hint}\n`);
      }
      process.stdout.write(`\x1b[2m  [↑/↓] Navigate   [Space] Toggle   [a] All   [Enter] Confirm\x1b[0m\n\n`);
    };

    readline.emitKeypressEvents(process.stdin);
    const wasRaw = process.stdin.isRaw;
    if (process.stdin.isTTY) process.stdin.setRawMode(true);
    process.stdin.resume();

    render(true);

    const onKeypress = (str, key = {}) => {
      if (key.ctrl && key.name === 'c') {
        if (process.stdin.isTTY) process.stdin.setRawMode(Boolean(wasRaw));
        process.stdin.removeListener('keypress', onKeypress);
        process.stdout.write('\n\x1b[31m✖ Installation cancelled.\x1b[0m\n');
        process.exit(130);
      }
      if (key.name === 'up' || key.name === 'k') {
        cursor = (cursor - 1 + items.length) % items.length;
        render(false);
      } else if (key.name === 'down' || key.name === 'j') {
        cursor = (cursor + 1) % items.length;
        render(false);
      } else if (key.name === 'space') {
        if (selected.has(cursor)) selected.delete(cursor);
        else selected.add(cursor);
        render(false);
      } else if (str === 'a' || str === 'A') {
        if (selected.size === items.length) selected.clear();
        else items.forEach((_, idx) => selected.add(idx));
        render(false);
      } else if (key.name === 'return' || key.name === 'enter') {
        if (process.stdin.isTTY) process.stdin.setRawMode(Boolean(wasRaw));
        process.stdin.removeListener('keypress', onKeypress);
        process.stdin.pause();
        const chosen = Array.from(selected).map((idx) => items[idx].value);
        resolve(chosen.length > 0 ? chosen : [items[0].value]);
      }
    };

    process.stdin.on('keypress', onKeypress);
  });
}

async function promptSingleSelect(title, items) {
  return new Promise((resolve) => {
    let cursor = 0;

    const render = (first = false) => {
      if (!first) {
        process.stdout.write(`\x1b[${items.length + 3}A\x1b[0J`);
      }
      process.stdout.write(`\x1b[1m\x1b[36m? ${title}\x1b[0m\n`);
      for (let i = 0; i < items.length; i++) {
        const isCur = i === cursor;
        const pointer = isCur ? '\x1b[36m❯\x1b[0m' : ' ';
        const radio = isCur ? '\x1b[32m●\x1b[0m' : '\x1b[2m○\x1b[0m';
        const label = isCur ? `\x1b[1m${items[i].label}\x1b[0m` : items[i].label;
        const hint = items[i].hint ? ` \x1b[2m— ${items[i].hint}\x1b[0m` : '';
        process.stdout.write(`  ${pointer} ${radio} ${label}${hint}\n`);
      }
      process.stdout.write(`\x1b[2m  [↑/↓] Navigate   [Enter] Select\x1b[0m\n\n`);
    };

    readline.emitKeypressEvents(process.stdin);
    const wasRaw = process.stdin.isRaw;
    if (process.stdin.isTTY) process.stdin.setRawMode(true);
    process.stdin.resume();

    render(true);

    const onKeypress = (_, key = {}) => {
      if (key.ctrl && key.name === 'c') {
        if (process.stdin.isTTY) process.stdin.setRawMode(Boolean(wasRaw));
        process.stdin.removeListener('keypress', onKeypress);
        process.stdout.write('\n\x1b[31m✖ Installation cancelled.\x1b[0m\n');
        process.exit(130);
      }
      if (key.name === 'up' || key.name === 'k') {
        cursor = (cursor - 1 + items.length) % items.length;
        render(false);
      } else if (key.name === 'down' || key.name === 'j') {
        cursor = (cursor + 1) % items.length;
        render(false);
      } else if (key.name === 'return' || key.name === 'enter') {
        if (process.stdin.isTTY) process.stdin.setRawMode(Boolean(wasRaw));
        process.stdin.removeListener('keypress', onKeypress);
        process.stdin.pause();
        resolve(items[cursor].value);
      }
    };

    process.stdin.on('keypress', onKeypress);
  });
}

export async function runInteractiveWizard() {
  console.log('\n\x1b[1m\x1b[35m╭──────────────────────────────────────────────────────────────────────────╮\x1b[0m');
  console.log('\x1b[1m\x1b[35m│\x1b[0m   ⚡ \x1b[1mAGENTIC SDLC FRAMEWORK — INTERACTIVE MULTI-AGENT INSTALLER (v0.1.0)\x1b[0m  \x1b[1m\x1b[35m│\x1b[0m');
  console.log('\x1b[1m\x1b[35m│\x1b[0m   \x1b[2m6 Consolidated Skills • Dual-Gate TDD • 4-Layer Git Guard • Archlens\x1b[0m   \x1b[1m\x1b[35m│\x1b[0m');
  console.log('\x1b[1m\x1b[35m╰──────────────────────────────────────────────────────────────────────────╯\x1b[0m\n');

  const agents = await promptMultiSelect(
    'Select the AI coding agents & IDEs to configure:',
    '(Press Space to toggle, Enter to confirm)',
    SUPPORTED_AGENTS.map((a) => ({
      label: a.name,
      hint: a.badge,
      value: a.id,
      defaultSelected: a.defaultSelected
    }))
  );

  const scope = await promptSingleSelect('Select installation scope:', [
    {
      label: 'Local Workspace (Recommended)',
      hint: 'Installs skills, engine (.agent/scripts), templates, and project rules into current directory',
      value: 'local'
    },
    {
      label: 'Global User Environment',
      hint: 'Installs skills & scripts globally into ~/.gemini, ~/.claude, ~/.grok, ~/.cursor',
      value: 'global'
    }
  ]);

  let addons = ['detect', 'hooks', 'archlens'];
  let pmProvider = 'github';
  if (scope === 'local') {
    pmProvider = await promptSingleSelect('Select your Project Management & Ticket Provider:', [
      {
        label: 'GitHub Issues & Projects v2 (Recommended)',
        hint: 'Uses gh CLI + .github/ISSUE_TEMPLATE/*.md',
        value: 'github'
      },
      {
        label: 'Linear (GraphQL API)',
        hint: 'Uses LINEAR_API_KEY + LINEAR_TEAM_KEY with local KANBAN.md mirror',
        value: 'linear'
      },
      {
        label: 'Jira (REST API v3)',
        hint: 'Uses JIRA_BASE_URL + JIRA_EMAIL + JIRA_API_TOKEN with local KANBAN.md mirror',
        value: 'jira'
      },
      {
        label: 'Local Kanban Only (Offline)',
        hint: 'Manages tickets exclusively in docs/KANBAN.md',
        value: 'local'
      }
    ]);

    addons = await promptMultiSelect(
      'Select SDLC Governance & Automation Add-ons:',
      '(Press Space to toggle, Enter to confirm)',
      [
        {
          label: 'Auto-Detect Project Stack',
          hint: 'Generates .agent/sdlc-config.json for Node, Java, Python, Go, or Rust',
          value: 'detect',
          defaultSelected: true
        },
        {
          label: '4-Layer Git Guard Hooks',
          hint: 'Installs pre-commit, pre-merge-commit, and pre-push hooks',
          value: 'hooks',
          defaultSelected: true
        },
        {
          label: 'Archlens Concentric Ring Policy',
          hint: 'Scaffolds .archlens/policy.json Clean Architecture boundaries',
          value: 'archlens',
          defaultSelected: true
        },
        {
          label: 'GitHub Actions Quality Gate CI',
          hint: 'Scaffolds .github/workflows/sdlc-quality-gate.yml',
          value: 'ci',
          defaultSelected: false
        }
      ]
    );
  }

  return {
    agents,
    global: scope === 'global',
    pmProvider,
    skipDetect: !addons.includes('detect'),
    withHooks: addons.includes('hooks'),
    withArchlens: addons.includes('archlens'),
    withCi: addons.includes('ci')
  };
}

/**
 * Core programmatic installer used by both `npx github:fmatar/agentic-sdlc-framework install`
 * and unit tests.
 */
export function installFramework({
  targetDir = '.',
  repoRoot = '.',
  global = false,
  agents = ['gemini', 'claude', 'cursor'],
  pmProvider = null,
  withArchlens = false,
  withHooks = false,
  withCi = false,
  skipDetect = false,
  homeDir = os.homedir(),
  packageRoot = resolvePackageRoot(),
  onDetect = null,
  onInstallCi = null
} = {}) {
  const isGlobal = Boolean(global);
  const absTarget = path.resolve(repoRoot, targetDir);
  const srcSkills = path.join(packageRoot, '.agent/skills');
  const srcScripts = path.join(packageRoot, '.agent/scripts');
  const srcTemplates = path.join(packageRoot, 'docs/templates');

  let destSkills;
  let destScripts;
  const prePruned = [];

  if (isGlobal) {
    const selectedIds = normalizeAgentList(agents);
    if (selectedIds.includes('gemini')) {
      destSkills = path.join(homeDir, '.gemini/config/skills');
      destScripts = path.join(homeDir, '.gemini/config/scripts');
      fs.mkdirSync(destSkills, { recursive: true });
      fs.mkdirSync(destScripts, { recursive: true });
      prePruned.push(...pruneDeprecatedSkills(destSkills));
      if (path.resolve(srcScripts) !== path.resolve(destScripts)) {
        copyDirRecursive(srcScripts, destScripts);
      }
    } else if (selectedIds.includes('claude')) {
      destSkills = path.join(homeDir, '.claude/skills');
      destScripts = path.join(homeDir, '.claude/scripts');
      fs.mkdirSync(destSkills, { recursive: true });
      fs.mkdirSync(destScripts, { recursive: true });
      if (path.resolve(srcScripts) !== path.resolve(destScripts)) {
        copyDirRecursive(srcScripts, destScripts);
      }
    } else {
      destSkills = path.join(homeDir, `.${selectedIds[0]}/skills`);
      destScripts = path.join(homeDir, `.${selectedIds[0]}/scripts`);
    }
  } else {
    fs.mkdirSync(absTarget, { recursive: true });
    destSkills = path.join(absTarget, '.agent/skills');
    destScripts = path.join(absTarget, '.agent/scripts');
    const destTemplates = path.join(absTarget, 'docs/templates');

    prePruned.push(...pruneDeprecatedSkills(destSkills));
    prePruned.push(...pruneDeprecatedSkills(path.join(absTarget, '.agents/skills')));

    if (path.resolve(srcSkills) !== path.resolve(destSkills)) {
      copyDirRecursive(srcSkills, destSkills);
    }
    if (path.resolve(srcScripts) !== path.resolve(destScripts)) {
      copyDirRecursive(srcScripts, destScripts);
    }
    if (fileExists(srcTemplates) && path.resolve(srcTemplates) !== path.resolve(destTemplates)) {
      copyDirRecursive(srcTemplates, destTemplates);
    }
  }

  const agentResult = provisionAgentIntegrations({
    agents,
    targetDir: absTarget,
    global: isGlobal,
    homeDir,
    packageRoot
  });

  const allPruned = Array.from(new Set([...prePruned, ...agentResult.prunedSkills]));

  const addonsInstalled = [];
  if (!isGlobal) {
    upsertGitignore(absTarget);
    pinArchlensMcpConfigs(absTarget);
    scaffoldIssueTemplates({ repoRoot: absTarget });
    addonsInstalled.push('.github/ISSUE_TEMPLATE');

    if (withArchlens) {
      scaffoldArchlensPolicy(absTarget);
      addonsInstalled.push('.archlens/policy.json');
    }
    if (withHooks && fileExists(path.join(absTarget, '.git'))) {
      installHooks({ repoRoot: absTarget });
      addonsInstalled.push('git-hooks');
    }
    if (withCi && typeof onInstallCi === 'function') {
      onInstallCi({ repoRoot: absTarget });
      addonsInstalled.push('.github/workflows/sdlc-quality-gate.yml');
    }
    if (!skipDetect && typeof onDetect === 'function') {
      onDetect(absTarget);
      addonsInstalled.push('stack-detect');
    }
    if (pmProvider) {
      const cfgPath = path.join(absTarget, '.agent/sdlc-config.json');
      let cfgObj = {};
      if (fileExists(cfgPath)) {
        try {
          cfgObj = JSON.parse(readFileSafe(cfgPath)) || {};
        } catch {}
      }
      cfgObj.projectManagement = {
        ...(cfgObj.projectManagement || {}),
        provider: String(pmProvider).toLowerCase()
      };
      fs.mkdirSync(path.dirname(cfgPath), { recursive: true });
      fs.writeFileSync(cfgPath, JSON.stringify(cfgObj, null, 2) + '\n', 'utf8');
      addonsInstalled.push(`pm:${String(pmProvider).toLowerCase()}`);
    }
  }

  return {
    success: true,
    global: isGlobal,
    mode: isGlobal ? 'global' : 'local',
    targetDir: isGlobal ? homeDir : absTarget,
    destSkills,
    destScripts,
    selectedAgents: agentResult.selectedAgents,
    skillsCount: agentResult.skillsCount,
    filesCreated: agentResult.filesCreated,
    prunedSkills: allPruned,
    addonsInstalled
  };
}

export function parseInstallArgs(args = [], repoRoot = '.') {
  let isGlobal = false;
  let targetDir = '.';
  let agentsArg = null;
  let pmProvider = null;
  let withArchlens = false;
  let withHooks = false;
  let withCi = false;
  let skipDetect = false;
  let nonInteractive = false;
  let help = false;

  for (let i = 1; i < args.length; i++) {
    const arg = args[i];
    if (arg === '-h' || arg === '--help') {
      help = true;
    } else if (arg === '-g' || arg === '--global') {
      isGlobal = true;
    } else if (arg === '-l' || arg === '--local' || arg === '--target' || arg === '-t') {
      if (args[i + 1] && !args[i + 1].startsWith('-')) {
        targetDir = args[++i];
      }
    } else if (arg.startsWith('--target=')) {
      targetDir = arg.split('=')[1];
    } else if (arg === '--agents' || arg === '--agent' || arg === '-a') {
      if (args[i + 1]) agentsArg = args[++i];
    } else if (arg.startsWith('--agents=')) {
      agentsArg = arg.split('=')[1];
    } else if (arg === '--pm' || arg === '--provider') {
      if (args[i + 1]) pmProvider = args[++i].toLowerCase();
    } else if (arg.startsWith('--pm=') || arg.startsWith('--provider=')) {
      pmProvider = arg.split('=')[1].toLowerCase();
    } else if (arg === '--with-archlens') {
      withArchlens = true;
    } else if (arg === '--with-hooks') {
      withHooks = true;
    } else if (arg === '--with-ci') {
      withCi = true;
    } else if (arg === '--skip-detect') {
      skipDetect = true;
    } else if (arg === '-y' || arg === '--yes' || arg === '--non-interactive') {
      nonInteractive = true;
    } else if (!arg.startsWith('-') && i === 1) {
      targetDir = arg;
    }
  }

  return {
    help,
    repoRoot,
    targetDir,
    global: isGlobal,
    agentsArg,
    agents: agentsArg ? normalizeAgentList(agentsArg) : ['gemini', 'claude', 'cursor'],
    pmProvider,
    withArchlens,
    withHooks,
    withCi,
    skipDetect,
    nonInteractive
  };
}

export function printInstallerHelp() {
  console.log(`
⚡ Agentic SDLC Framework — Universal Multi-Agent Installer (v0.1.0)

Usage:
  npx github:fmatar/agentic-sdlc-framework install [options]

Options:
  --agents <list>      Comma-separated agents: gemini,claude,cursor,grok,windsurf,copilot,all
  -g, --global         Install globally into ~/.gemini, ~/.claude, ~/.grok, ~/.cursor
  -l, --local [dir]    Install into target project directory (default: current directory)
  --target <dir>       Alias for --local <dir>
  --with-archlens      Scaffold .archlens/policy.json Clean Architecture boundaries
  --with-hooks         Install 4-Layer Git Guard hooks (pre-commit, pre-merge, pre-push)
  --with-ci            Scaffold .github/workflows/sdlc-quality-gate.yml
  --skip-detect        Skip automatic polyglot stack detection
  -y, --yes            Run non-interactively with recommended defaults
  -h, --help           Display this help guide

Examples:
  # Launch the interactive multi-agent TUI wizard
  npx github:fmatar/agentic-sdlc-framework install

  # Install non-interactively for Claude Code, Gemini, Grok, and Cursor
  npx github:fmatar/agentic-sdlc-framework install --agents claude,gemini,grok,cursor --yes

  # Install globally for all supported agents
  npx github:fmatar/agentic-sdlc-framework install --global --agents all --yes
`);
}

export async function runInstallerCLI(args = [], { repoRoot = '.', onDetect = null, onInstallCi = null } = {}) {
  const parsed = parseInstallArgs(args, repoRoot);
  if (parsed.help) {
    printInstallerHelp();
    return { success: true, help: true };
  }

  const canRunTui =
    Boolean(process.stdin.isTTY && process.stdout.isTTY) &&
    !parsed.nonInteractive &&
    !parsed.agentsArg &&
    !process.env.CI &&
    !process.env.NODE_TEST_CONTEXT;

  let options = {
    ...parsed
  };

  if (canRunTui) {
    const wizardChoices = await runInteractiveWizard();
    options = {
      ...options,
      ...wizardChoices
    };
  }

  const res = installFramework({
    ...options,
    onDetect,
    onInstallCi
  });

  console.log('\n\x1b[1m\x1b[32m✅ Agentic SDLC Framework Installation Complete!\x1b[0m');
  console.log(`   • \x1b[1mMode\x1b[0m:             ${res.mode.toUpperCase()} (${res.targetDir})`);
  console.log(`   • \x1b[1mSDLC Skills\x1b[0m:      ${res.skillsCount} canonical skills loaded (${CANONICAL_SDLC_SKILLS.join(', ')})`);
  console.log(`   • \x1b[1mConfigured Tools\x1b[0m: ${res.selectedAgents.join(', ')}`);
  if (res.prunedSkills.length > 0) {
    console.log(`   • \x1b[1mPruned Legacy\x1b[0m:    Removed ${res.prunedSkills.length} deprecated skill item(s)`);
  }
  if (res.addonsInstalled.length > 0) {
    console.log(`   • \x1b[1mGovernance\x1b[0m:       ${res.addonsInstalled.join(', ')}`);
  }
  console.log('\n👉 \x1b[1mNext Step\x1b[0m: Prompt your agent with \x1b[36m/sdlc-product\x1b[0m or \x1b[36m/sdlc-flow\x1b[0m!\n');

  return res;
}
