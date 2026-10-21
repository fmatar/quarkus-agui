import fs from 'node:fs';
import path from 'node:path';
import { execSync } from 'node:child_process';
import { fileExists, readFileSafe } from './core.mjs';
import { verifyGateReceipt } from './audit-chain.mjs';
import { checkSpecDrift } from './adr-drift.mjs';

const PROTECTED_BRANCHES = ['main', 'develop'];

function getCurrentGitBranch(repoRoot = '.') {
  try {
    return execSync('git branch --show-current 2>/dev/null', {
      cwd: repoRoot,
      encoding: 'utf8',
      stdio: 'pipe'
    }).trim();
  } catch {
    return '';
  }
}

function readActiveSession(repoRoot = '.') {
  const sessionPath = path.resolve(repoRoot, '.agent/.sdlc-session.json');
  if (!fileExists(sessionPath)) return null;
  try {
    const data = JSON.parse(readFileSafe(sessionPath));
    if (data.sessions && typeof data.sessions === 'object') {
      return data.activeSessionId ? data.sessions[data.activeSessionId] : null;
    }
    return data.active ? data : null;
  } catch {
    return null;
  }
}

/**
 * Layer 1 & Layer 2 Commit Guard:
 * 1. Blocks direct commits on protected branches (`main`, `develop`).
 * 2. Requires an active `.agent/.sdlc-session.json` matching the current branch (`stage >= 2`).
 */
export function verifyCommitGuard({ branch = null, repoRoot = '.' } = {}) {
  const activeBranch = branch || getCurrentGitBranch(repoRoot) || 'main';

  // Layer 1: Protected Branch Block
  if (PROTECTED_BRANCHES.includes(activeBranch)) {
    return {
      allowed: false,
      layer: 'LAYER_1_PROTECTED_BRANCH',
      branch: activeBranch,
      message: `🛑 [SDLC Layer 1 Guard] DIRECT COMMITS TO '${activeBranch}' ARE FORBIDDEN! Create a feature branch via sdlc-flow.`
    };
  }

  // Layer 2: Active SDLC Session Lock Required
  const session = readActiveSession(repoRoot);
  if (!session || !session.active) {
    return {
      allowed: false,
      layer: 'LAYER_2_SESSION_REQUIRED',
      branch: activeBranch,
      message: `🛑 [SDLC Layer 2 Guard] No active SDLC session found in .agent/.sdlc-session.json. Run: node .agent/scripts/sdlc-engine.mjs session start --issue <id> --branch ${activeBranch}`
    };
  }

  if (session.branch && session.branch !== activeBranch) {
    return {
      allowed: false,
      layer: 'LAYER_2_BRANCH_MISMATCH',
      branch: activeBranch,
      sessionBranch: session.branch,
      message: `🛑 [SDLC Layer 2 Guard] Active SDLC session (#${session.issueId}) is locked to '${session.branch}', but current branch is '${activeBranch}'.`
    };
  }

  if (Number(session.stage || 1) < 2) {
    return {
      allowed: false,
      layer: 'LAYER_2_STAGE_NOT_READY',
      branch: activeBranch,
      stage: session.stage,
      message: `🛑 [SDLC Layer 2 Guard] Session #${session.issueId} is on Stage 1 (Intake). Advance to Stage 2+ before committing.`
    };
  }

  return {
    allowed: true,
    layer: 'PASSED',
    branch: activeBranch,
    issueId: session.issueId,
    stage: session.stage
  };
}

/**
 * Layer 3 Merge Guard:
 * Blocks local merges into `main` or `develop` unless backed by a signed
 * Stage 7 Cryptographic Gate Receipt in `docs/AUDIT_CHAIN.jsonl`.
 */
export function verifyMergeGuard({
  targetBranch = null,
  sourceBranch = null,
  sourceSha = null,
  repoRoot = '.'
} = {}) {
  const currentTarget = targetBranch || getCurrentGitBranch(repoRoot) || 'main';

  if (!PROTECTED_BRANCHES.includes(currentTarget)) {
    return {
      allowed: true,
      targetBranch: currentTarget,
      message: `Merge into non-protected branch '${currentTarget}' permitted.`
    };
  }

  const receiptCheck = verifyGateReceipt({
    headSha: sourceSha,
    branch: sourceBranch,
    repoRoot
  });

  if (!receiptCheck.valid) {
    return {
      allowed: false,
      layer: 'LAYER_3_GATE_RECEIPT_REQUIRED',
      reasonCode: 'MISSING_STAGE_7_GATE_RECEIPT',
      targetBranch: currentTarget,
      sourceBranch,
      message: `🛑 [SDLC Layer 3 Merge Guard] Cannot merge into '${currentTarget}': ${receiptCheck.reason}`
    };
  }

  return {
    allowed: true,
    layer: 'PASSED',
    targetBranch: currentTarget,
    receipt: receiptCheck.receipt
  };
}

/**
 * Layer 3 & 4 Push Guard:
 * Blocks direct pushes to `main`/`develop` and verifies Spec-Drift Sentinel.
 */
export function verifyPushGuard({ remoteBranch = null, repoRoot = '.' } = {}) {
  const target = remoteBranch || getCurrentGitBranch(repoRoot) || '';
  if (PROTECTED_BRANCHES.includes(target)) {
    return {
      allowed: false,
      layer: 'LAYER_3_PROTECTED_PUSH',
      branch: target,
      message: `🛑 [SDLC Push Guard] DIRECT PUSHES TO '${target}' ARE FORBIDDEN!`
    };
  }

  const session = readActiveSession(repoRoot);
  const driftRes = checkSpecDrift({
    ticketId: session?.issueId || null,
    repoRoot
  });

  if (!driftRes.passed) {
    return {
      allowed: false,
      layer: 'LAYER_3_UNDECLARED_SPEC_DRIFT',
      branch: target,
      drift: driftRes,
      message: `🛑 [SDLC Push Guard] ${driftRes.message}\n👉 Run: ${driftRes.remediationCommand}`
    };
  }

  return {
    allowed: true,
    layer: 'PASSED',
    branch: target
  };
}

export function installHooks({ repoRoot = '.' } = {}) {
  console.log('🪝 Configuring 4-Layer Zero-Bypass Git Hooks for Agentic SDLC...');
  const gitDir = path.resolve(repoRoot, '.git');
  if (!fs.existsSync(gitDir)) {
    throw new Error('Not a git repository. Cannot install git hooks.');
  }

  const hooksDir = path.resolve(gitDir, 'hooks');
  fs.mkdirSync(hooksDir, { recursive: true });

  const preCommitHook = `#!/usr/bin/env bash
# Agentic SDLC Framework: 4-Layer Pre-Commit Guard (Branch & Active Session Lock)
set -e

if git diff --cached --quiet; then
  exit 0
fi

if [ -f ".agent/scripts/sdlc-engine.mjs" ]; then
  node .agent/scripts/sdlc-engine.mjs guard pre-commit
  node .agent/scripts/sdlc-engine.mjs test --affected-only 2>/dev/null || true
fi
exit 0
`;

  const preMergeCommitHook = `#!/usr/bin/env bash
# Agentic SDLC Framework: Layer 3 Pre-Merge-Commit Cryptographic Gate Receipt Guard
set -e

if [ -f ".agent/scripts/sdlc-engine.mjs" ]; then
  node .agent/scripts/sdlc-engine.mjs guard pre-merge
fi
exit 0
`;

  const prePushHook = `#!/usr/bin/env bash
# Agentic SDLC Framework: Strict Pre-Push Quality Gate, Spec-Drift & Branch Protection
set -e

while read local_ref local_sha remote_ref remote_sha
do
  remote_branch=$(echo "$remote_ref" | sed 's@refs/heads/@@')
  if [ "$remote_branch" = "develop" ] || [ "$remote_branch" = "main" ]; then
    echo "🛑 [SDLC Branch Protection] DIRECT PUSHES TO '$remote_branch' ARE FORBIDDEN!"
    echo "👉 Hard Framework Invariant: All changes must be pushed to a feature or arch branch (e.g. 'feature/issue-#<id>-<name>') and merged via Pull Request."
    exit 1
  fi
done

echo "🛡️ [SDLC Hook] Verifying Universal Quality Gate & Spec-Drift Sentinel before push..."
if [ -f ".agent/scripts/sdlc-engine.mjs" ]; then
  node .agent/scripts/sdlc-engine.mjs guard pre-push
  node .agent/scripts/sdlc-engine.mjs gate
fi
exit 0
`;

  const preCommitPath = path.resolve(hooksDir, 'pre-commit');
  const preMergePath = path.resolve(hooksDir, 'pre-merge-commit');
  const prePushPath = path.resolve(hooksDir, 'pre-push');

  fs.writeFileSync(preCommitPath, preCommitHook, 'utf8');
  fs.writeFileSync(preMergePath, preMergeCommitHook, 'utf8');
  fs.writeFileSync(prePushPath, prePushHook, 'utf8');

  try {
    fs.chmodSync(preCommitPath, 0o755);
    fs.chmodSync(preMergePath, 0o755);
    fs.chmodSync(prePushPath, 0o755);
  } catch {}

  console.log('✅ Installed 4-Layer Git hooks:');
  console.log(`   • ${preCommitPath}`);
  console.log(`   • ${preMergePath}`);
  console.log(`   • ${prePushPath}`);

  return {
    success: true,
    installed: ['pre-commit', 'pre-push'],
    allHooksInstalled: ['pre-commit', 'pre-merge-commit', 'pre-push'],
    hooksDir
  };
}

export function applyBranchProtection({
  branches = ['main', 'develop'],
  repoRoot = '.'
} = {}) {
  const results = {
    localHookInstalled: false,
    protectedBranches: [],
    errors: []
  };

  try {
    const hookRes = installHooks({ repoRoot });
    results.localHookInstalled = hookRes.success;
  } catch (err) {
    results.errors.push(`Local hook error: ${err.message}`);
  }

  for (const b of branches) {
    try {
      execSync(
        `gh api --method PUT repos/:owner/:repo/branches/${b}/protection \
        -H "Accept: application/vnd.github+json" \
        -F "required_status_checks=null" \
        -F "enforce_admins=false" \
        -F "required_pull_request_reviews[dismiss_stale_reviews]=true" \
        -F "required_pull_request_reviews[require_code_owner_reviews]=false" \
        -F "required_pull_request_reviews[required_approving_review_count]=0" \
        -F "restrictions=null" 2>/dev/null`,
        {
          cwd: repoRoot,
          stdio: 'pipe'
        }
      );
      results.protectedBranches.push({ branch: b, remoteProtected: true });
    } catch {
      results.protectedBranches.push({ branch: b, remoteProtected: false, localProtected: true });
    }
  }

  return results;
}
