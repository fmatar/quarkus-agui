import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { fileExists, readFileSafe } from './core.mjs';
import { parseProductMarkdown } from './product-dag.mjs';

const MODULE_DIR = path.dirname(fileURLToPath(import.meta.url));
const BUNDLED_README_TEMPLATE = path.resolve(MODULE_DIR, '../../../docs/templates/README_TEMPLATE.md');
const VALID_README_ARCHETYPES = new Set(['auto', 'cli', 'sdk', 'platform']);

const FALLBACK_README_TEMPLATE = `<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/logo-dark.svg">
  <img alt="{{PROJECT_NAME}} Logo" src="docs/logo-light.svg" width="320">
</picture>

# {{PROJECT_NAME}}

**{{ONE_SENTENCE_HOOK}}**

[![CI Status]({{CI_BADGE_URL}})]({{CI_LINK}})
[![Version]({{VERSION_BADGE_URL}})]({{VERSION_LINK}})
[![License]({{LICENSE_BADGE_URL}})]({{LICENSE_LINK}})
[![Docs]({{DOCS_BADGE_URL}})]({{DOCS_LINK}})

[Quickstart](#quickstart) · [Why {{PROJECT_SHORT_NAME}}?](#why-{{PROJECT_ANCHOR}}) · [How It Works](#how-it-works) · [Docs](#documentation--roadmap) · [Contributing](#contributing--license)

</div>

<!-- ARCHETYPE:cli -->
{{CLI_HERO_PROOF}}
<!-- /ARCHETYPE:cli -->
<!-- ARCHETYPE:sdk -->
\`\`\`{{CODE_LANG}}
{{SDK_HERO_SNIPPET}}
\`\`\`
<!-- /ARCHETYPE:sdk -->
<!-- ARCHETYPE:platform -->
{{PLATFORM_HERO_PROOF}}
<!-- /ARCHETYPE:platform -->

## Why {{PROJECT_SHORT_NAME}}?

{{PROBLEM_STATEMENT}}

- ⚡ **{{CAPABILITY_HEADLINE}}** — {{CAPABILITY_OUTCOME}}
- 🔒 **{{GUARANTEE_HEADLINE}}** — {{ARCHITECTURAL_GUARANTEE}}
- 🧩 **{{ECOSYSTEM_HEADLINE}}** — {{ECOSYSTEM_FIT}}

## Quickstart

> [!TIP]
> {{QUICKSTART_TIP}}

### 1. Install

\`\`\`bash
{{INSTALL_COMMAND}}
\`\`\`

### 2. Minimal Working Example

\`\`\`{{QUICKSTART_LANG}}
{{QUICKSTART_SNIPPET}}
\`\`\`

Expected output:

\`\`\`text
{{EXPECTED_OUTPUT}}
\`\`\`

## How It Works

\`\`\`mermaid
%%{init: {'theme': 'neutral'}}%%
flowchart LR
    Input["1. Input / Trigger"] --> Core["2. {{PROJECT_SHORT_NAME}} Core"]
    Core --> Output["3. Verified Output"]
\`\`\`

<details>
<summary><strong>Benchmarks & Advanced Configuration</strong></summary>

| Variable / Flag | Default | Description |
| :--- | :--- | :--- |
| \`{{CONFIG_KEY_1}}\` | \`{{CONFIG_DEFAULT_1}}\` | {{CONFIG_DESC_1}} |
| \`{{CONFIG_KEY_2}}\` | \`{{CONFIG_DEFAULT_2}}\` | {{CONFIG_DESC_2}} |

</details>

## Documentation & Roadmap

- **Full Documentation & Architecture**: [{{DOCS_LABEL}}]({{DOCS_LINK}})
- **AI Coding Agent Context**: [\`llms.txt\`](./llms.txt)
- **Specs & Open Milestones**: {{ROADMAP_LINK}}

## Contributing & License

Local development, source builds, and pull request guidelines live in [CONTRIBUTING.md]({{CONTRIBUTING_LINK}}).
Distributed under the [{{LICENSE_NAME}} License]({{LICENSE_LINK}}).
`;

export function escapeXml(value = '') {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

export function slugifyHeadingAnchor(headingText = '') {
  return String(headingText)
    .trim()
    .toLowerCase()
    .replace(/[^\w\s-]/g, '')
    .replace(/\s+/g, '-');
}

function humanizeSlug(raw = '') {
  const cleaned = String(raw)
    .replace(/^@[^/]+\//, '')
    .replace(/[-_]+/g, ' ')
    .trim();
  if (!cleaned) return 'Project';
  return cleaned
    .split(/\s+/)
    .map((w) => (/^(cli|sdk|api|ui|ai|sdlc|mcp|db)$/i.test(w) ? w.toUpperCase() : w.charAt(0).toUpperCase() + w.slice(1)))
    .join(' ');
}

/**
 * Detects the repository's primary README archetype (`cli`, `sdk`, or `platform`).
 */
export function detectReadmeArchetype(repoRoot = '.') {
  const absRoot = path.resolve(repoRoot);

  const composeCandidates = ['docker-compose.yml', 'docker-compose.yaml', 'compose.yml', 'compose.yaml'];
  if (composeCandidates.some((f) => fileExists(path.join(absRoot, f)))) {
    return 'platform';
  }

  const pkgPath = path.join(absRoot, 'package.json');
  if (fileExists(pkgPath)) {
    try {
      const pkg = JSON.parse(readFileSafe(pkgPath) || '{}');
      const hasBin =
        (typeof pkg.bin === 'string' && pkg.bin.trim().length > 0) ||
        (pkg.bin && typeof pkg.bin === 'object' && Object.keys(pkg.bin).length > 0);
      if (hasBin) return 'cli';
    } catch {}
  }

  const cargoPath = path.join(absRoot, 'Cargo.toml');
  if (fileExists(cargoPath)) {
    const cargo = readFileSafe(cargoPath) || '';
    if (cargo.includes('[[bin]]') || (fileExists(path.join(absRoot, 'src/main.rs')) && !cargo.includes('[lib]'))) {
      return 'cli';
    }
  }

  const pyPath = path.join(absRoot, 'pyproject.toml');
  if (fileExists(pyPath)) {
    const py = readFileSafe(pyPath) || '';
    if (py.includes('[project.scripts]') || py.includes('[tool.poetry.scripts]')) {
      return 'cli';
    }
  }

  if (fileExists(path.join(absRoot, 'go.mod')) && fileExists(path.join(absRoot, 'cmd'))) {
    return 'cli';
  }

  return 'sdk';
}

/**
 * Strips `<!-- TEMPLATE_GUIDE_START -->` blocks and non-matching `<!-- ARCHETYPE:... -->` blocks.
 */
export function stripArchetypeBlocks(templateMarkdown = '', selectedArchetype = 'cli') {
  const target = String(selectedArchetype || 'cli').trim().toLowerCase();
  let out = String(templateMarkdown).replace(/<!--\s*TEMPLATE_GUIDE_START\s*-->[\s\S]*?<!--\s*TEMPLATE_GUIDE_END\s*-->\n?/g, '');

  out = out.replace(/<!--\s*ARCHETYPE:([a-z0-9_-]+)\s*-->\n?([\s\S]*?)<!--\s*\/ARCHETYPE:\1\s*-->\n?/gi, (_match, arch, inner) => {
    if (String(arch).toLowerCase() === target) {
      return `${inner.replace(/^\n+|\n+$/g, '')}\n`;
    }
    return '';
  });

  return out.replace(/\n{3,}/g, '\n\n').trim() + '\n';
}

/**
 * Renders a crisp, theme-aware geometric SVG wordmark (`light` or `dark`).
 */
export function renderThemeWordmarkSvg({ title = 'Project', mode = 'light' } = {}) {
  const isDark = mode === 'dark';
  const ink = isDark ? '#f8fafc' : '#0f172a';
  const muted = isDark ? '#94a3b8' : '#475569';
  const cardStroke = isDark ? '#334155' : '#cbd5e1';
  const cardFill = isDark ? '#0f172a' : '#f8fafc';
  const accentA = isDark ? '#818cf8' : '#4f46e5';
  const accentB = isDark ? '#2dd4bf' : '#0d9488';
  const rawTitle = String(title || 'Project');
  const safeTitle = escapeXml(rawTitle.length > 28 ? `${rawTitle.slice(0, 27)}…` : rawTitle);

  return `<svg xmlns="http://www.w3.org/2000/svg" width="360" height="64" viewBox="0 0 360 64" role="img" aria-label="${safeTitle}">
  <rect x="4" y="8" width="48" height="48" rx="12" fill="${cardFill}" stroke="${cardStroke}" stroke-width="1.5"/>
  <polygon points="28,16 42,24 42,40 28,48 14,40 14,24" fill="none" stroke="${accentA}" stroke-width="2.2"/>
  <circle cx="28" cy="32" r="5" fill="${accentB}"/>
  <text x="66" y="35" fill="${ink}" font-family="system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif" font-size="20" font-weight="700" letter-spacing="-0.3">${safeTitle}</text>
  <text x="66" y="51" fill="${muted}" font-family="ui-monospace, SFMono-Regular, Menlo, monospace" font-size="11" font-weight="500">OPEN SOURCE DEVELOPER PLATFORM</text>
</svg>
`;
}

/**
 * Renders a declarative Charmbracelet VHS `.tape` terminal recording script (~8s).
 */
export function renderVhsDemoTape({ title = 'CLI Demo', demoCmd = 'npx my-cli --help', outputPath = 'docs/demo.gif' } = {}) {
  const cleanTitle = String(title || 'CLI Demo').replace(/[\r\n]+/g, ' ').trim();
  const cleanOutput = String(outputPath || 'docs/demo.gif').replace(/[\r\n]+/g, '').trim();
  const cleanCmd = String(demoCmd || 'npx my-cli --help')
    .replace(/[\r\n]+/g, ' ')
    .replace(/\\/g, '\\\\')
    .replace(/"/g, '\\"')
    .trim();
  return `# Charmbracelet VHS Declarative Hero Proof — ${cleanTitle}
# Generate GIF with: vhs docs/demo.tape
Output ${cleanOutput}
Set FontSize 16
Set Width 920
Set Height 440
Set Padding 24
Set TypingSpeed 45ms

Type "${cleanCmd}"
Enter
Sleep 4s
`;
}

/**
 * Renders an `llms.txt` index linking existing documentation & governance files for AI coding agents.
 */
export function renderLlmsTxt({ title = 'Project', hook = '', repoRoot = '.' } = {}) {
  const absRoot = path.resolve(repoRoot);
  const candidates = [
    { path: 'README.md', label: 'Developer Landing Page & 90-Second Quickstart' },
    { path: 'PRODUCT.md', label: 'Product Constitution, Invariants (INV-XX) & Capabilities (CAP-XX)' },
    { path: 'docs/PRD.md', label: 'Product Requirements Document (PRD)' },
    { path: 'docs/SRS.md', label: 'IEEE 29148 Software Requirements Specification (SRS)' },
    { path: 'wiki/Home.md', label: 'Project Wiki Home & Architecture Overview' },
    { path: 'wiki/Architecture.md', label: 'Concentric Clean Architecture & Dependency Rings' },
    { path: 'wiki/Skills-Reference.md', label: 'Complete Skills & CLI Reference' },
    { path: 'wiki/Codebase-Map.md', label: 'Deterministic AST Module & Ring Map' },
    { path: 'CONTRIBUTING.md', label: 'Local Build, TDD Workflow & Pull Request Guidelines' },
    { path: 'CHANGELOG.md', label: 'Release History & Semantic Versioning Changelog' }
  ];

  const present = candidates.filter((c) => c.path === 'README.md' || fileExists(path.join(absRoot, c.path)));
  const lines = [
    `# ${title}`,
    '',
    `> ${hook || `${title} — concise documentation index for humans and AI coding assistants.`}`,
    '',
    '## Documentation Index',
    ...present.map((item) => `- [${item.path}](./${item.path}): ${item.label}`),
    ''
  ];
  return lines.join('\n');
}

function inspectRepoMetadata(absRoot) {
  let pkgName = path.basename(absRoot);
  let displayName = humanizeSlug(pkgName);
  let version = '0.1.0';
  let license = 'MIT';
  let description = '';
  let binName = null;
  let ecosystem = 'node';
  let repoSlug = null;

  const pkgPath = path.join(absRoot, 'package.json');
  if (fileExists(pkgPath)) {
    try {
      const pkg = JSON.parse(readFileSafe(pkgPath) || '{}');
      if (pkg.name) {
        pkgName = pkg.name;
        displayName = humanizeSlug(pkg.name);
      }
      if (pkg.version) version = pkg.version;
      if (pkg.license) license = pkg.license;
      if (pkg.description) description = pkg.description;
      if (typeof pkg.bin === 'string') {
        binName = pkgName.replace(/^@[^/]+\//, '');
      } else if (pkg.bin && typeof pkg.bin === 'object' && Object.keys(pkg.bin).length > 0) {
        binName = Object.keys(pkg.bin)[0];
      }
      const repoUrl = typeof pkg.repository === 'string' ? pkg.repository : pkg.repository?.url || '';
      const ghMatch = repoUrl.match(/github\.com[:/]([^/]+\/[^/.]+?)(?:\.git)?$/i);
      if (ghMatch) repoSlug = ghMatch[1];
    } catch {}
  } else if (fileExists(path.join(absRoot, 'Cargo.toml'))) {
    ecosystem = 'rust';
    const cargo = readFileSafe(path.join(absRoot, 'Cargo.toml')) || '';
    const nMatch = cargo.match(/^\s*name\s*=\s*"([^"]+)"/m);
    const vMatch = cargo.match(/^\s*version\s*=\s*"([^"]+)"/m);
    const lMatch = cargo.match(/^\s*license\s*=\s*"([^"]+)"/m);
    if (nMatch) {
      pkgName = nMatch[1];
      displayName = humanizeSlug(pkgName);
      binName = pkgName;
    }
    if (vMatch) version = vMatch[1];
    if (lMatch) license = lMatch[1];
  } else if (fileExists(path.join(absRoot, 'pyproject.toml'))) {
    ecosystem = 'python';
    const py = readFileSafe(path.join(absRoot, 'pyproject.toml')) || '';
    const nMatch = py.match(/^\s*name\s*=\s*"([^"]+)"/m);
    const vMatch = py.match(/^\s*version\s*=\s*"([^"]+)"/m);
    if (nMatch) {
      pkgName = nMatch[1];
      displayName = humanizeSlug(pkgName);
      binName = pkgName;
    }
    if (vMatch) version = vMatch[1];
  } else if (fileExists(path.join(absRoot, 'go.mod'))) {
    ecosystem = 'go';
  } else if (fileExists(path.join(absRoot, 'pom.xml')) || fileExists(path.join(absRoot, 'build.gradle'))) {
    ecosystem = 'java';
  }

  let product = null;
  if (fileExists(path.join(absRoot, 'PRODUCT.md'))) {
    try {
      product = parseProductMarkdown(absRoot);
      if (product?.title && !product.title.includes('[')) {
        displayName = product.title;
      }
    } catch {}
  }

  return {
    pkgName,
    displayName,
    shortName: displayName,
    version,
    license,
    description,
    binName: binName || pkgName.replace(/^@[^/]+\//, ''),
    ecosystem,
    repoSlug,
    product
  };
}

function buildArchetypeDefaults(meta, archetype, absRoot) {
  const hasDemoGif = fileExists(path.join(absRoot, 'docs/demo.gif'));
  let installCommand = `npm install ${meta.pkgName}`;
  let quickstartLang = 'javascript';
  let quickstartSnippet = `import { createClient } from '${meta.pkgName}';\n\nconst client = createClient();\nconst status = await client.ping();\nconsole.log(status);`;
  let expectedOutput = `{ ok: true, version: "${meta.version}" }`;
  let codeLang = 'javascript';

  if (archetype === 'cli') {
    installCommand =
      meta.ecosystem === 'rust'
        ? `cargo install ${meta.pkgName}`
        : meta.ecosystem === 'python'
          ? `pip install ${meta.pkgName}`
          : `npx ${meta.pkgName} --help`;
    quickstartLang = 'bash';
    quickstartSnippet = `${meta.binName} init --yes\n${meta.binName} check`;
    expectedOutput = `✔ Initialized ${meta.displayName} (v${meta.version})\n✔ All checks passed in 42ms`;
  } else if (archetype === 'platform') {
    installCommand = 'docker compose up -d';
    quickstartLang = 'bash';
    quickstartSnippet = 'curl -s http://localhost:8080/health';
    expectedOutput = `{"status":"healthy","version":"${meta.version}"}`;
  } else if (meta.ecosystem === 'python') {
    installCommand = `pip install ${meta.pkgName}`;
    codeLang = 'python';
    quickstartLang = 'python';
    const modName = meta.pkgName.replace(/-/g, '_');
    quickstartSnippet = `from ${modName} import Client\n\nclient = Client()\nprint(client.ping())`;
    expectedOutput = `{'ok': True, 'version': '${meta.version}'}`;
  } else if (meta.ecosystem === 'rust') {
    installCommand = `cargo add ${meta.pkgName}`;
    codeLang = 'rust';
    quickstartLang = 'rust';
    const crateName = meta.pkgName.replace(/-/g, '_');
    quickstartSnippet = `use ${crateName}::Client;\n\nfn main() {\n    let client = Client::default();\n    println!("{:?}", client.ping());\n}`;
    expectedOutput = `Ok(Status { healthy: true })`;
  }

  const cliHeroProof = hasDemoGif
    ? `<div align="center">\n  <img alt="${meta.displayName} Terminal Demo" src="docs/demo.gif" width="760">\n</div>`
    : `\`\`\`text\n$ ${meta.binName} check\n✔ Configuration verified (.archlens/policy.json)\n✔ All quality checks passed (0 warnings, 0 errors)\n\`\`\``;

  const platformHeroProof = [
    '```mermaid',
    "%%{init: {'theme': 'neutral'}}%%",
    'flowchart LR',
    `    UI["Client / UI"] --> API["${meta.shortName} Gateway"]`,
    '    API --> Core["Domain Service & Store"]',
    '```',
    '',
    '```bash',
    'docker compose up -d && open http://localhost:8080',
    '```'
  ].join('\n');

  return {
    installCommand,
    quickstartLang,
    quickstartSnippet,
    expectedOutput,
    codeLang,
    sdkHeroSnippet: quickstartSnippet,
    cliHeroProof,
    platformHeroProof
  };
}

/**
 * Scaffolds a 10–30–90 Developer Landing Page `README.md` and companion assets (`docs/logo-*.svg`, `llms.txt`, `docs/demo.tape`).
 */
export function scaffoldReadme({
  repoRoot = '.',
  archetype = 'auto',
  title = null,
  hook = null,
  force = false,
  withAssets = true
} = {}) {
  const absRoot = path.resolve(repoRoot);
  const rawArchetype = archetype == null || archetype === '' ? 'auto' : String(archetype).trim().toLowerCase();
  if (!VALID_README_ARCHETYPES.has(rawArchetype)) {
    throw new Error(
      `Unsupported README archetype '${archetype}'. Expected one of: auto, cli, sdk, platform.`
    );
  }
  const selectedArchetype = rawArchetype === 'auto' ? detectReadmeArchetype(absRoot) : rawArchetype;

  const meta = inspectRepoMetadata(absRoot);
  const projectName = title || meta.displayName;
  const shortName = title || meta.shortName;
  const oneSentenceHook =
    hook ||
    meta.description ||
    meta.product?.vision ||
    `${projectName} for engineering teams that eliminates manual boilerplate with deterministic defaults.`;

  const createdAssets = [];
  if (withAssets) {
    const docsDir = path.join(absRoot, 'docs');
    fs.mkdirSync(docsDir, { recursive: true });

    const lightSvgPath = path.join(docsDir, 'logo-light.svg');
    if (force || !fileExists(lightSvgPath)) {
      fs.writeFileSync(lightSvgPath, renderThemeWordmarkSvg({ title: projectName, mode: 'light' }), 'utf8');
      createdAssets.push('docs/logo-light.svg');
    }

    const darkSvgPath = path.join(docsDir, 'logo-dark.svg');
    if (force || !fileExists(darkSvgPath)) {
      fs.writeFileSync(darkSvgPath, renderThemeWordmarkSvg({ title: projectName, mode: 'dark' }), 'utf8');
      createdAssets.push('docs/logo-dark.svg');
    }

    const llmsPath = path.join(absRoot, 'llms.txt');
    if (force || !fileExists(llmsPath)) {
      fs.writeFileSync(llmsPath, renderLlmsTxt({ title: projectName, hook: oneSentenceHook, repoRoot: absRoot }), 'utf8');
      createdAssets.push('llms.txt');
    }

    if (selectedArchetype === 'cli') {
      const tapePath = path.join(docsDir, 'demo.tape');
      if (force || !fileExists(tapePath)) {
        fs.writeFileSync(
          tapePath,
          renderVhsDemoTape({ title: projectName, demoCmd: `${meta.binName} --help`, outputPath: 'docs/demo.gif' }),
          'utf8'
        );
        createdAssets.push('docs/demo.tape');
      }
    }
  }

  const readmePath = path.join(absRoot, 'README.md');
  if (fileExists(readmePath) && !force) {
    return {
      written: false,
      path: 'README.md',
      archetype: selectedArchetype,
      createdAssets,
      reason: 'README.md already exists; pass --force to regenerate.'
    };
  }

  const localTemplate = path.join(absRoot, 'docs/templates/README_TEMPLATE.md');
  const rawTemplate =
    (fileExists(localTemplate) ? readFileSafe(localTemplate) : readFileSafe(BUNDLED_README_TEMPLATE)) ||
    FALLBACK_README_TEMPLATE;

  const stripped = stripArchetypeBlocks(rawTemplate, selectedArchetype);
  const defaults = buildArchetypeDefaults({ ...meta, displayName: projectName, shortName }, selectedArchetype, absRoot);

  const hasLightLogo = fileExists(path.join(absRoot, 'docs/logo-light.svg'));
  const hasDarkLogo = fileExists(path.join(absRoot, 'docs/logo-dark.svg'));
  const hasLlmsTxt = fileExists(path.join(absRoot, 'llms.txt'));
  const hasLicense = fileExists(path.join(absRoot, 'LICENSE'));
  const hasContributing = fileExists(path.join(absRoot, 'CONTRIBUTING.md'));
  const hasChangelog = fileExists(path.join(absRoot, 'CHANGELOG.md'));
  const hasWikiHome = fileExists(path.join(absRoot, 'wiki/Home.md'));
  const hasProductMd = fileExists(path.join(absRoot, 'PRODUCT.md'));
  const hasKanban = fileExists(path.join(absRoot, 'docs/KANBAN.md'));

  const docsLink = hasWikiHome
    ? './wiki/Home.md'
    : hasProductMd
      ? './PRODUCT.md'
      : hasLlmsTxt
        ? './llms.txt'
        : '#quickstart';
  const docsLabel = hasWikiHome
    ? 'Project Wiki (`wiki/Home.md`)'
    : hasProductMd
      ? 'Product Constitution (`PRODUCT.md`)'
      : hasLlmsTxt
        ? 'LLM Documentation Index (`llms.txt`)'
        : 'Quickstart Guide';
  const versionLink = hasChangelog ? './CHANGELOG.md' : '#quickstart';
  const licenseLink = hasLicense ? './LICENSE' : '#contributing--license';
  const contributingLink = hasContributing ? './CONTRIBUTING.md' : '#contributing--license';
  const roadmapLink = hasKanban
    ? '[`docs/KANBAN.md`](./docs/KANBAN.md)'
    : hasProductMd
      ? '[`PRODUCT.md`](./PRODUCT.md)'
      : 'Tracked via repository milestones and issues.';

  const encodedVer = encodeURIComponent(meta.version).replace(/-/g, '--');
  const encodedLic = encodeURIComponent(meta.license).replace(/-/g, '--');
  const ciBadgeUrl = meta.repoSlug
    ? `https://img.shields.io/github/actions/workflow/status/${meta.repoSlug}/ci.yml?label=CI`
    : 'https://img.shields.io/badge/CI-passing-16a34a.svg';
  const ciLink = meta.repoSlug ? `https://github.com/${meta.repoSlug}/actions` : '#how-it-works';

  const firstCap = meta.product?.capabilities?.[0];
  const firstInv = meta.product?.invariants?.[0];

  const tokens = {
    PROJECT_NAME: projectName,
    PROJECT_SHORT_NAME: shortName,
    PROJECT_ANCHOR: slugifyHeadingAnchor(shortName),
    ONE_SENTENCE_HOOK: oneSentenceHook,
    CI_BADGE_URL: ciBadgeUrl,
    CI_LINK: ciLink,
    VERSION_BADGE_URL: `https://img.shields.io/badge/Version-${encodedVer}-4f46e5.svg`,
    VERSION_LINK: versionLink,
    LICENSE_BADGE_URL: `https://img.shields.io/badge/License-${encodedLic}-64748b.svg`,
    LICENSE_LINK: licenseLink,
    DOCS_BADGE_URL: 'https://img.shields.io/badge/Docs-Quickstart-0d9488.svg',
    DOCS_LINK: docsLink,
    DOCS_LABEL: docsLabel,
    CLI_HERO_PROOF: defaults.cliHeroProof,
    CODE_LANG: defaults.codeLang,
    SDK_HERO_SNIPPET: defaults.sdkHeroSnippet,
    PLATFORM_HERO_PROOF: defaults.platformHeroProof,
    PROBLEM_STATEMENT:
      meta.product?.vision ||
      `Traditional setups force developers through brittle configuration files and slow feedback loops before running a single line of code. **${projectName}** provides an opinionated, zero- ceremony path from install to verified execution.`,
    CAPABILITY_HEADLINE: firstCap ? firstCap.title : 'Fast Time-to-Hello-World',
    CAPABILITY_OUTCOME: 'Copy-paste onboarding with deterministic execution in under 90 seconds.',
    GUARANTEE_HEADLINE: 'Architectural Safety',
    ARCHITECTURAL_GUARANTEE: firstInv ? firstInv.text : 'Clean boundaries, explicit contracts, and zero hidden runtime state.',
    ECOSYSTEM_HEADLINE: 'Drop-In Ecosystem Fit',
    ECOSYSTEM_FIT: hasLlmsTxt
      ? 'Works cleanly across local terminals, CI pipelines, and AI coding assistants (`llms.txt`).'
      : 'Works cleanly across local terminals, CI pipelines, and modern developer toolchains.',
    QUICKSTART_TIP: 'Zero manual configuration required for local evaluation—sensible defaults work out of the box.',
    INSTALL_COMMAND: defaults.installCommand,
    QUICKSTART_LANG: defaults.quickstartLang,
    QUICKSTART_SNIPPET: defaults.quickstartSnippet,
    EXPECTED_OUTPUT: defaults.expectedOutput,
    CONFIG_KEY_1: 'LOG_LEVEL',
    CONFIG_DEFAULT_1: 'info',
    CONFIG_DESC_1: 'Structured diagnostic verbosity (`debug`, `info`, `warn`, `error`)',
    CONFIG_KEY_2: 'TIMEOUT_MS',
    CONFIG_DEFAULT_2: '5000',
    CONFIG_DESC_2: 'Maximum execution budget per operation in milliseconds',
    ROADMAP_LINK: roadmapLink,
    CONTRIBUTING_LINK: contributingLink,
    LICENSE_NAME: meta.license
  };

  let rendered = stripped;
  for (const [key, val] of Object.entries(tokens)) {
    rendered = rendered.split(`{{${key}}}`).join(String(val));
  }

  if (!(hasLightLogo && hasDarkLogo)) {
    rendered = rendered.replace(/<picture>[\s\S]*?<\/picture>\n*/i, '');
  }
  if (!hasLlmsTxt) {
    rendered = rendered.replace(/^- \*\*AI Coding Agent Context\*\*:\s*\[`llms\.txt`\]\(\.\/llms\.txt\)\n?/m, '');
  }

  fs.writeFileSync(readmePath, rendered, 'utf8');
  return {
    written: true,
    path: 'README.md',
    archetype: selectedArchetype,
    createdAssets
  };
}

function maskFencedCodeBlocks(markdown = '') {
  const withoutFences = String(markdown).replace(/```[\s\S]*?```/g, (block) =>
    block
      .split('\n')
      .map(() => '')
      .join('\n')
  );
  return withoutFences
    .split('\n')
    .map((line) => line.replace(/`[^`\n]+`/g, ''))
    .join('\n');
}

function countHeroBadges(maskedMarkdown = '') {
  const firstH2Idx = maskedMarkdown.search(/^##\s+/m);
  const heroRegion = firstH2Idx >= 0 ? maskedMarkdown.slice(0, firstH2Idx) : maskedMarkdown;
  const mdBadges = heroRegion.match(/!\[[^\]]*\]\((?:https?:\/\/[^)]*(?:shields\.io|badge)[^)]*)\)/gi) || [];
  const htmlBadges = heroRegion.match(/<img\b[^>]*src=["']https?:\/\/[^"']*(?:shields\.io|badge)[^"']*["']/gi) || [];
  return mdBadges.length + htmlBadges.length;
}

function checkGitCloneFirstQuickstart(rawMarkdown = '', maskedMarkdown = '') {
  const rawLines = String(rawMarkdown).split('\n');
  const maskedLines = (maskedMarkdown ? String(maskedMarkdown) : maskFencedCodeBlocks(rawMarkdown)).split('\n');
  const qsLineIdx = maskedLines.findIndex((line) => /^##\s+.*(?:Quickstart|Quick\s+Install)/i.test(line));
  if (qsLineIdx < 0) return null;

  let endLineIdx = maskedLines.length;
  for (let i = qsLineIdx + 1; i < maskedLines.length; i++) {
    if (/^##\s+/.test(maskedLines[i])) {
      endLineIdx = i;
      break;
    }
  }

  const body = rawLines.slice(qsLineIdx + 1, endLineIdx).join('\n');
  const firstFence = body.match(/```(?:bash|sh|zsh|shell|console)?\n([\s\S]*?)```/i);
  if (!firstFence) return null;
  const firstCmdLine = firstFence[1]
    .split('\n')
    .map((l) => l.trim().replace(/^\$\s*/, ''))
    .find((l) => l && !l.startsWith('#'));
  if (firstCmdLine && /^git\s+clone\b/i.test(firstCmdLine)) {
    return firstCmdLine;
  }
  return null;
}

function findBrokenLocalLinks(maskedMarkdown = '', absRoot) {
  const broken = [];
  const lines = maskedMarkdown.split('\n');
  const mdLinkRegex = /!?\[[^\]]*\]\(([^)]+)\)/g;
  const htmlAttrRegex = /\b(src|srcset)=["']([^"']+)["']/gi;

  const inspectTarget = (rawTarget, lineNum) => {
    const cleaned = String(rawTarget).trim().split(/\s+/)[0].replace(/^<|>$/g, '');
    if (
      !cleaned ||
      cleaned.startsWith('#') ||
      cleaned.startsWith('http://') ||
      cleaned.startsWith('https://') ||
      cleaned.startsWith('mailto:') ||
      cleaned.startsWith('data:')
    ) {
      return;
    }
    const withoutFrag = cleaned.split('#')[0].split('?')[0];
    if (!withoutFrag) return;
    if (withoutFrag.startsWith('/')) {
      broken.push({ target: withoutFrag, line: lineNum });
      return;
    }
    const resolved = path.resolve(absRoot, withoutFrag);
    const insideRepo = resolved === absRoot || resolved.startsWith(absRoot + path.sep);
    if (!insideRepo || !fileExists(resolved)) {
      broken.push({ target: withoutFrag, line: lineNum });
    }
  };

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    let m;
    mdLinkRegex.lastIndex = 0;
    while ((m = mdLinkRegex.exec(line)) !== null) {
      inspectTarget(m[1], i + 1);
    }
    htmlAttrRegex.lastIndex = 0;
    while ((m = htmlAttrRegex.exec(line)) !== null) {
      const attrName = m[1].toLowerCase();
      const attrVal = m[2];
      if (attrName === 'srcset') {
        for (const candidate of attrVal.split(',')) {
          inspectTarget(candidate, i + 1);
        }
      } else {
        inspectTarget(attrVal, i + 1);
      }
    }
  }
  return broken;
}

/**
 * Audits `README.md` against the 10–30–90 Developer Landing Page Blueprint.
 * Hard anti-patterns emit `ERROR`s; optional polish opportunities emit non-blocking `WARN`s.
 */
export function auditReadme(repoRoot = '.', { strict = false } = {}) {
  const absRoot = path.resolve(repoRoot);
  const readmePath = path.join(absRoot, 'README.md');

  if (!fileExists(readmePath)) {
    return {
      readmeExists: false,
      passed: false,
      errorsCount: 1,
      warningsCount: 0,
      findings: [
        {
          rule: 'MISSING_README',
          severity: 'ERROR',
          file: 'README.md',
          line: 1,
          message: 'Repository is missing a root README.md landing page.'
        }
      ],
      summary: '❌ README Audit failed: README.md is missing.'
    };
  }

  const raw = readFileSafe(readmePath) || '';
  const masked = maskFencedCodeBlocks(raw);
  const findings = [];

  // 1. Leftover template tokens or archetype markers (ERROR)
  const maskedLines = masked.split('\n');
  for (let i = 0; i < maskedLines.length; i++) {
    const tokenMatch = maskedLines[i].match(/\{\{[A-Z][A-Z0-9_]+\}\}|<!--\s*\/?ARCHETYPE:|<!--\s*TEMPLATE_GUIDE_/);
    if (tokenMatch) {
      findings.push({
        rule: 'LEFTOVER_TEMPLATE_TOKEN',
        severity: 'ERROR',
        file: 'README.md',
        line: i + 1,
        target: tokenMatch[0],
        message: `Unresolved template marker '${tokenMatch[0]}' on README.md:${i + 1}.`
      });
    }
  }

  // 2. Badge Christmas tree > 4 badges in hero header (ERROR)
  const badgeCount = countHeroBadges(masked);
  if (badgeCount > 4) {
    findings.push({
      rule: 'BADGE_CHRISTMAS_TREE',
      severity: 'ERROR',
      file: 'README.md',
      line: 1,
      target: String(badgeCount),
      message: `Hero header has ${badgeCount} badges (max 4 allowed by 10-30-90 blueprint).`
    });
  }

  // 3. git-clone-first Quickstart anti-pattern (ERROR)
  const cloneCmd = checkGitCloneFirstQuickstart(raw, masked);
  if (cloneCmd) {
    findings.push({
      rule: 'GIT_CLONE_FIRST_QUICKSTART',
      severity: 'ERROR',
      file: 'README.md',
      line: 1,
      target: cloneCmd,
      message: 'Quickstart opens with `git clone`; lead with a one-line package/container install and move source builds to CONTRIBUTING.md.'
    });
  }

  // 4. Broken local links or images (ERROR)
  for (const b of findBrokenLocalLinks(masked, absRoot)) {
    findings.push({
      rule: 'BROKEN_README_LINK',
      severity: 'ERROR',
      file: 'README.md',
      line: b.line,
      target: b.target,
      message: `Referenced local link or image '${b.target}' on README.md:${b.line} does not exist.`
    });
  }

  // 5. Core 10-30-90 sections (ERROR in strict mode, WARN otherwise)
  const hasWhy = /^##+\s+.*\bwhy\b/im.test(masked);
  const hasQuickstart = /^##+\s+.*\bquick\s*(?:start|install)\b/im.test(masked);
  if (!hasWhy || !hasQuickstart) {
    findings.push({
      rule: 'MISSING_CORE_SECTIONS',
      severity: strict ? 'ERROR' : 'WARN',
      file: 'README.md',
      line: 1,
      message: `README.md should include both '## Why ...' and '## Quickstart' sections (found Why=${hasWhy}, Quickstart=${hasQuickstart}).`
    });
  }

  // 6. Optional Polish: Theme-aware logo (<picture> + prefers-color-scheme or #gh-dark-mode-only) (WARN)
  const hasThemeAwareLogo =
    (/<picture>[\s\S]*?prefers-color-scheme[\s\S]*?<\/picture>/i.test(masked)) ||
    /#gh-(?:dark|light)-mode-only\b/i.test(masked);
  if (!hasThemeAwareLogo) {
    findings.push({
      rule: 'MISSING_THEME_AWARE_LOGO',
      severity: 'WARN',
      file: 'README.md',
      line: 1,
      message: 'Consider wrapping the hero logo in `<picture>` with `prefers-color-scheme` so dark/light modes stay crisp.'
    });
  }

  // 7. Optional Polish: Mermaid blocks initialized with neutral theme (WARN)
  const mermaidRegex = /```mermaid\n([\s\S]*?)```/g;
  let mMatch;
  while ((mMatch = mermaidRegex.exec(raw)) !== null) {
    const block = mMatch[1];
    if (!/%%\{init:[\s\S]*?neutral[\s\S]*?\}%%/i.test(block)) {
      findings.push({
        rule: 'NON_NEUTRAL_MERMAID',
        severity: 'WARN',
        file: 'README.md',
        line: 1,
        message: "Initialize Mermaid diagrams with `%%{init: {'theme': 'neutral'}}%%` so dark mode never breaks contrast."
      });
    }
  }

  // 8. Optional Polish: llms.txt reference for AI coding agents (WARN)
  if (!/llms\.txt/i.test(masked)) {
    findings.push({
      rule: 'MISSING_LLMS_TXT_LINK',
      severity: 'WARN',
      file: 'README.md',
      line: 1,
      message: 'Link an `llms.txt` index in Documentation & Roadmap so AI coding assistants can ingest full docs without hallucinating.'
    });
  }

  // 9. Optional Polish: Progressive disclosure budget outside <details> blocks (WARN)
  const withoutDetails = raw.replace(/<details>[\s\S]*?<\/details>/gi, '');
  const visibleNonEmptyLines = withoutDetails
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean).length;
  if (visibleNonEmptyLines > 180) {
    findings.push({
      rule: 'INLINE_BLOAT_BUDGET',
      severity: 'WARN',
      file: 'README.md',
      line: 1,
      target: String(visibleNonEmptyLines),
      message: `README.md has ${visibleNonEmptyLines} visible non-empty lines outside <details> (target <= 180); tuck deep config matrices into <details> or wiki/.`
    });
  }

  const errorsCount = findings.filter((f) => f.severity === 'ERROR').length;
  const warningsCount = findings.filter((f) => f.severity === 'WARN').length;
  const passed = errorsCount === 0;

  return {
    readmeExists: true,
    passed,
    badgeCount,
    visibleNonEmptyLines,
    errorsCount,
    warningsCount,
    findingsCount: findings.length,
    findings,
    summary: passed
      ? `✅ README Audit passed (${badgeCount} badge(s), ${visibleNonEmptyLines} visible lines, ${warningsCount} polish warning(s)).`
      : `❌ README Audit failed (${errorsCount} error(s), ${warningsCount} warning(s)).`
  };
}
