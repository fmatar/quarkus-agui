import fs from 'node:fs';
import path from 'node:path';
import { execSync, execFileSync, exec } from 'node:child_process';
import { promisify } from 'node:util';

const execPromise = promisify(exec);

// --- Environment Variables Loader (.env support without external dependencies) ---

export function loadEnv(repoRoot = '.') {
  const envPath = path.resolve(repoRoot, '.env');
  if (!fs.existsSync(envPath)) return process.env;

  try {
    const content = fs.readFileSync(envPath, 'utf8');
    const lines = content.split('\n');
    for (const line of lines) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith('#')) continue;
      const eqIdx = trimmed.indexOf('=');
      if (eqIdx !== -1) {
        const key = trimmed.slice(0, eqIdx).trim();
        let val = trimmed.slice(eqIdx + 1).trim();
        if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) {
          val = val.slice(1, -1);
        }
        if (!process.env[key]) {
          process.env[key] = val;
        }
      }
    }
  } catch {
    // Ignore read errors
  }
  return process.env;
}

// --- Helper Functions & Async Subprocess Pool ---

export function fileExists(filePath) {
  try {
    return fs.existsSync(filePath);
  } catch {
    return false;
  }
}

export function readFileSafe(filePath) {
  try {
    return fs.readFileSync(filePath, 'utf8');
  } catch {
    return null;
  }
}

export function execCommand(cmd, options = {}) {
  try {
    return execSync(cmd, { stdio: 'pipe', encoding: 'utf8', ...options });
  } catch (error) {
    if (options.throwOnError) throw error;
    return error.stdout || error.stderr || '';
  }
}

export async function execAsync(cmd, options = {}) {
  try {
    const { stdout, stderr } = await execPromise(cmd, { encoding: 'utf8', ...options });
    return { ok: true, stdout: stdout || '', stderr: stderr || '' };
  } catch (error) {
    if (options.throwOnError) throw error;
    return {
      ok: false,
      stdout: error.stdout || '',
      stderr: error.stderr || error.message || '',
      error
    };
  }
}

export async function runParallelTasks(tasks = [], concurrency = 4) {
  if (!Array.isArray(tasks) || tasks.length === 0) return [];
  const results = new Array(tasks.length);
  let nextIdx = 0;

  async function worker() {
    while (nextIdx < tasks.length) {
      const current = nextIdx++;
      results[current] = await tasks[current]();
    }
  }

  const workersCount = Math.max(1, Math.min(Number(concurrency) || 4, tasks.length));
  await Promise.all(Array.from({ length: workersCount }, () => worker()));
  return results;
}

export function parseFrontmatter(markdown = '') {
  if (!markdown.startsWith('---')) return { frontmatter: {}, body: markdown };
  const endIdx = markdown.indexOf('\n---', 3);
  if (endIdx === -1) return { frontmatter: {}, body: markdown };
  const rawYaml = markdown.slice(3, endIdx).trim();
  const body = markdown.slice(endIdx + 4).trim();
  const frontmatter = {};
  for (const line of rawYaml.split('\n')) {
    const colonIdx = line.indexOf(':');
    if (colonIdx !== -1) {
      const key = line.slice(0, colonIdx).trim();
      let val = line.slice(colonIdx + 1).trim();
      if ((val.startsWith("'") && val.endsWith("'")) || (val.startsWith('"') && val.endsWith('"'))) {
        val = val.slice(1, -1);
      }
      frontmatter[key] = val;
    }
  }
  return { frontmatter, body };
}

// --- Archlens Governance Bridge ---

export function inspectArchlens(repoRoot = '.') {
  const policyPath = path.resolve(repoRoot, '.archlens/policy.json');
  const legacyPolicyPath = path.resolve(repoRoot, '.uml-viewer/policy.json');
  const hasPolicy = fs.existsSync(policyPath) || fs.existsSync(legacyPolicyPath);

  return {
    installed: hasPolicy,
    policyFile: hasPolicy ? (fs.existsSync(policyPath) ? '.archlens/policy.json' : '.uml-viewer/policy.json') : null,
    workbenchPort: process.env.ARCHLENS_PORT ? Number(process.env.ARCHLENS_PORT) : 8088
  };
}

// --- Project Management Provider Abstraction ---

export function detectPMProvider(repoRoot = '.') {
  loadEnv(repoRoot);

  const explicitEnv = (process.env.SDLC_PM_PROVIDER || '').toLowerCase().trim();
  if (explicitEnv === 'jira') {
    return { provider: 'jira', configured: true, key: process.env.JIRA_PROJECT_KEY || 'SDLC' };
  }
  if (explicitEnv === 'linear') {
    return { provider: 'linear', configured: true, key: process.env.LINEAR_TEAM_KEY || 'DEV' };
  }
  if (explicitEnv === 'github') {
    return { provider: 'github', configured: true };
  }
  if (explicitEnv === 'local') {
    return { provider: 'local', configured: true, file: 'docs/KANBAN.md' };
  }

  const hasJira = !!(process.env.JIRA_API_TOKEN && process.env.JIRA_BASE_URL);
  const hasLinear = !!process.env.LINEAR_API_KEY;

  if (hasJira) return { provider: 'jira', configured: true, key: process.env.JIRA_PROJECT_KEY || 'SDLC' };
  if (hasLinear) return { provider: 'linear', configured: true, key: process.env.LINEAR_TEAM_KEY || 'DEV' };

  const cfgPath = path.resolve(repoRoot, '.agent/sdlc-config.json');
  if (fs.existsSync(cfgPath)) {
    try {
      const cfg = JSON.parse(fs.readFileSync(cfgPath, 'utf8'));
      const cfgProvider = (cfg?.projectManagement?.provider || '').toLowerCase().trim();
      const cfgKey = cfg?.projectManagement?.projectKey || cfg?.projectManagement?.key || 'SDLC';
      if (cfgProvider === 'jira') return { provider: 'jira', configured: true, key: process.env.JIRA_PROJECT_KEY || cfgKey };
      if (cfgProvider === 'linear') return { provider: 'linear', configured: true, key: process.env.LINEAR_TEAM_KEY || cfgKey };
      if (cfgProvider === 'github') return { provider: 'github', configured: true, key: cfgKey };
      if (cfgProvider === 'local') return { provider: 'local', configured: true, file: 'docs/KANBAN.md' };
    } catch {}
  }

  if (hasGitHubRemote(repoRoot)) {
    return { provider: 'github', configured: true };
  }

  let hasGhCliAuth = false;
  try {
    execSync('gh auth status 2>/dev/null', { cwd: repoRoot, stdio: 'pipe' });
    hasGhCliAuth = true;
  } catch {}

  const hasGitHub = !!(process.env.GITHUB_TOKEN || process.env.GH_TOKEN || hasGhCliAuth);
  if (hasGitHub) return { provider: 'github', configured: true };

  return { provider: 'local', configured: true, file: 'docs/KANBAN.md' };
}

export function diagnoseEnvironment(repoRoot = '.') {
  loadEnv(repoRoot);

  let hasGhCliAuth = false;
  try {
    execSync('gh auth status 2>/dev/null', { cwd: repoRoot, stdio: 'pipe' });
    hasGhCliAuth = true;
  } catch {}

  const hasGithubToken = !!(process.env.GITHUB_TOKEN || process.env.GH_TOKEN);

  const checks = [
    {
      name: 'GITHUB_AUTH',
      status: hasGithubToken || hasGhCliAuth,
      details: hasGithubToken ? 'Configured via GITHUB_TOKEN' : (hasGhCliAuth ? 'Active via gh CLI (Keyring authenticated)' : 'Missing (Configure GITHUB_TOKEN or run gh auth login)')
    },
    {
      name: 'JIRA_API_TOKEN',
      status: !!process.env.JIRA_API_TOKEN,
      details: process.env.JIRA_API_TOKEN ? `Configured for ${process.env.JIRA_BASE_URL || 'Jira'}` : 'Not configured'
    },
    {
      name: 'LINEAR_API_KEY',
      status: !!process.env.LINEAR_API_KEY,
      details: process.env.LINEAR_API_KEY ? `Configured for team ${process.env.LINEAR_TEAM_KEY || 'Default'}` : 'Not configured'
    },
    {
      name: 'ARCHLENS',
      status: fs.existsSync(path.resolve(repoRoot, '.archlens/policy.json')),
      details: fs.existsSync(path.resolve(repoRoot, '.archlens/policy.json')) ? 'Active (.archlens/policy.json found)' : 'Not configured (Run sdlc-initialize with Archlens)'
    },
    {
      name: 'LIVING_KANBAN',
      status: fs.existsSync(path.resolve(repoRoot, 'docs/KANBAN.md')),
      details: fs.existsSync(path.resolve(repoRoot, 'docs/KANBAN.md')) ? 'Present (docs/KANBAN.md)' : 'Missing'
    }
  ];

  return {
    pm: detectPMProvider(repoRoot),
    archlens: inspectArchlens(repoRoot),
    checks
  };
}

/**
 * Runs `git` with an argv array (never through a shell) and returns stdout,
 * or null when git exits non-zero. Filenames and refs are passed verbatim,
 * so values like `$(cmd).js` can never be interpreted by a shell.
 */
export function gitOutput(args, repoRoot = '.') {
  try {
    return execFileSync('git', args, { cwd: repoRoot, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] });
  } catch {
    return null;
  }
}

export function hasGitHubRemote(repoRoot = '.') {
  if (process.env.GH_HOST === 'invalid.localhost') return false;
  const originUrl = gitOutput(['config', '--get', 'remote.origin.url'], repoRoot) || '';
  return /github\.com/i.test(originUrl.trim());
}

/**
 * Returns a unified diff for one file: last commit first, then working tree,
 * and for untracked files a synthetic all-added diff of its content.
 */
export function gitDiffForFile(file, repoRoot = '.') {
  const lastCommit = gitOutput(['diff', 'HEAD~1', '--', file], repoRoot);
  if (lastCommit) return lastCommit;
  const working = gitOutput(['diff', '--', file], repoRoot);
  if (working) return working;
  const tracked = gitOutput(['ls-files', '--error-unmatch', '--', file], repoRoot);
  if (tracked === null) {
    try {
      const abs = path.resolve(repoRoot, file);
      if (fs.statSync(abs).isFile()) {
        const body = fs.readFileSync(abs, 'utf8');
        return `+++ b/${file}\n` + body.split('\n').map((l) => `+${l}`).join('\n');
      }
    } catch {}
  }
  return '';
}

export function getModifiedFiles(baseBranch = 'main', repoRoot = '.') {
  const lines = (out) => (out || '').split('\n').map((s) => s.trim()).filter(Boolean);
  const branchOut =
    gitOutput(['diff', '--name-only', `origin/${baseBranch}...HEAD`], repoRoot) ??
    gitOutput(['diff', '--name-only', `${baseBranch}...HEAD`], repoRoot) ??
    gitOutput(['diff', '--name-only', 'HEAD~1'], repoRoot);
  const workingFiles = [
    ...lines(gitOutput(['diff', '--name-only', 'HEAD'], repoRoot)),
    ...lines(gitOutput(['ls-files', '--others', '--exclude-standard'], repoRoot))
  ];
  return Array.from(new Set([...lines(branchOut), ...workingFiles]));
}
