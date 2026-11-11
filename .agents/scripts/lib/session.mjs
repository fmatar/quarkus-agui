import fs from 'node:fs';
import path from 'node:path';
import { execSync, execFileSync } from 'node:child_process';
import { fileExists, readFileSafe } from './core.mjs';
import { createPMIssue, transitionPMState, syncKanbanBoard, ensurePullRequest } from './pm-provider.mjs';
import { checkSpecDrift } from './adr-drift.mjs';
import { syncDecisionMapMarkdown } from './decision-graph.mjs';
import { recordArchTrace } from './arch-trace.mjs';
import { issueGateReceipt } from './audit-chain.mjs';
import { generatePRSummary } from './quality-gate.mjs';
import { generateArchitectureWidget, ensureWorkbenchRunning } from './workbench-server.mjs';
import { auditWiki } from './wiki-sync.mjs';

const SESSION_FILE = '.sdlc-session.json';

export function getSessionPath(repoRoot = '.') {
  return path.resolve(repoRoot, '.agent', SESSION_FILE);
}

export function getSessionStatus(repoRoot = '.') {
  const sessionPath = getSessionPath(repoRoot);
  if (!fileExists(sessionPath)) {
    return { active: false, session: null, registry: null };
  }

  const content = readFileSafe(sessionPath);
  if (!content) {
    return { active: false, session: null, registry: null, error: 'Empty session file' };
  }

  try {
    const data = JSON.parse(content);
    if (data.sessions && typeof data.sessions === 'object') {
      const activeId = data.activeSessionId;
      const activeSession = activeId && data.sessions[activeId] ? data.sessions[activeId] : null;
      return {
        active: !!activeSession?.active,
        session: activeSession,
        activeSessionId: activeId,
        sessions: data.sessions,
        registry: data
      };
    }
    return {
      active: !!data.active,
      session: data,
      activeSessionId: data.issueId,
      sessions: data.issueId ? { [data.issueId]: data } : {},
      registry: null
    };
  } catch {
    return { active: false, session: null, registry: null, error: 'Corrupted session file' };
  }
}

export function startSession({
  issueId,
  branch,
  stage = 1,
  stageName = 'Intake & Specification',
  type = 'feat',
  worktree = null,
  repoRoot = '.',
  force = false
} = {}) {
  const sessionPath = getSessionPath(repoRoot);
  const current = getSessionStatus(repoRoot);

  if (!issueId) {
    throw new Error(
      'Mandatory Ticket Invariant Violated: Cannot start an SDLC workflow session without a valid issueId (--issue <id>). Create a ticket first via GitHub, PM provider, or sdlc-engine ticket create.'
    );
  }

  if (current.active && current.session?.issueId !== Number(issueId) && !worktree && !force) {
    throw new Error(
      `Active SDLC workflow session already in progress for issue #${current.session.issueId} on branch ${current.session.branch}. To run concurrent tasks, use Git Worktree isolation (--worktree <path>) or subagent Workspace: 'branch'.`
    );
  }

  fs.mkdirSync(path.dirname(sessionPath), { recursive: true });

  const idKey = String(issueId);
  const session = {
    active: true,
    issueId: Number(issueId) || issueId,
    type: type.toLowerCase(),
    branch: branch || (type === 'hotfix' ? `hotfix/issue-#${issueId}` : `feature/issue-#${issueId}`),
    worktree: worktree || null,
    stage: Number(stage) || 1,
    stageName: stageName || 'Intake & Specification',
    remediationCount: 0,
    maxRemediations: 3,
    manifest: {
      specPath: null,
      prNumber: null,
      coverageReport: null,
      mutationScore: null,
      uiScreenshotPath: null,
      checkpoints: {
        stage1: null,
        stage2: null,
        stage3: null,
        stage4: null,
        stage5: null,
        stage6: null,
        stage7: null,
        stage8: null
      }
    },
    startedAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    activeSubagents: []
  };

  session.manifest.checkpoints[`stage${stage}`] = new Date().toISOString();

  const sessions = current.sessions || {};
  sessions[idKey] = session;

  const registry = {
    activeSessionId: Number(issueId) || issueId,
    sessions
  };

  fs.writeFileSync(sessionPath, JSON.stringify(registry, null, 2), 'utf8');
  return { success: true, session, registry };
}

export function updateSession({
  stage,
  stageName,
  activeSubagents,
  manifestUpdate = {},
  incrementRemediation = false,
  repoRoot = '.'
} = {}) {
  const sessionPath = getSessionPath(repoRoot);
  const current = getSessionStatus(repoRoot);

  if (!current.active || !current.session) {
    throw new Error('No active SDLC workflow session found to update.');
  }

  const session = current.session;
  if (stage !== undefined) {
    const nextStage = Number(stage);
    if (nextStage === 3 && session.stage >= 6) {
      session.remediationCount = (session.remediationCount || 0) + 1;
      if (session.remediationCount > (session.maxRemediations || 3)) {
        throw new Error(
          `⚠️ Circuit Breaker Tripped: Ticket #${session.issueId} has exceeded max automated remediations (${session.maxRemediations || 3}). Human architectural review required.`
        );
      }
    }
    session.stage = nextStage;
    if (session.manifest && session.manifest.checkpoints) {
      session.manifest.checkpoints[`stage${nextStage}`] = new Date().toISOString();
    }

    if (session.issueId) {
      let stageState = 'in-progress';
      if (nextStage === 1) stageState = 'ready';
      else if (nextStage >= 2 && nextStage <= 5) stageState = 'in-progress';
      else if (nextStage === 6 || nextStage === 7) stageState = 'in-review';
      else if (nextStage === 8) stageState = 'done';

      try {
        transitionPMState({ issueId: session.issueId, state: stageState, repoRoot });
      } catch {}
    }
  }

  if (stageName !== undefined) session.stageName = stageName;
  if (activeSubagents !== undefined) session.activeSubagents = activeSubagents;
  if (incrementRemediation) {
    session.remediationCount = (session.remediationCount || 0) + 1;
  }

  if (manifestUpdate && session.manifest) {
    session.manifest = { ...session.manifest, ...manifestUpdate };
  }

  session.updatedAt = new Date().toISOString();

  const sessions = current.sessions || {};
  sessions[String(session.issueId)] = session;

  const registry = {
    activeSessionId: session.issueId,
    sessions
  };

  fs.writeFileSync(sessionPath, JSON.stringify(registry, null, 2), 'utf8');
  return { success: true, session, registry };
}

export function switchSession(targetIssueId, repoRoot = '.') {
  const sessionPath = getSessionPath(repoRoot);
  const current = getSessionStatus(repoRoot);
  const idKey = String(targetIssueId);

  if (!current.sessions || !current.sessions[idKey]) {
    throw new Error(
      `Session for ticket #${targetIssueId} does not exist in registry. Active sessions: ${Object.keys(current.sessions || {}).join(', ')}`
    );
  }

  const targetSession = current.sessions[idKey];
  const registry = {
    activeSessionId: targetSession.issueId,
    sessions: current.sessions
  };

  fs.writeFileSync(sessionPath, JSON.stringify(registry, null, 2), 'utf8');
  console.log(`🔀 Switched active session to ticket #${targetSession.issueId} (Branch: ${targetSession.branch})`);
  return { success: true, activeSessionId: targetSession.issueId, session: targetSession };
}

export function listSessions(repoRoot = '.') {
  const current = getSessionStatus(repoRoot);
  const list = [];

  if (current.sessions) {
    for (const [id, sess] of Object.entries(current.sessions)) {
      list.push({
        issueId: sess.issueId,
        type: sess.type,
        branch: sess.branch,
        worktree: sess.worktree,
        stage: sess.stage,
        stageName: sess.stageName,
        isActive: Number(id) === Number(current.activeSessionId),
        remediations: `${sess.remediationCount || 0}/${sess.maxRemediations || 3}`
      });
    }
  }

  return { activeSessionId: current.activeSessionId, sessions: list };
}

export function renderDashboard(session) {
  if (!session) return 'No active session.';
  const stages = [
    { num: 1, name: 'Intake & Spec' },
    { num: 2, name: 'Branch & Kanban' },
    { num: 3, name: 'TDD Implement' },
    { num: 4, name: 'Dual-Gate Tests' },
    { num: 5, name: 'Docs & Showcase' },
    { num: 6, name: 'Pull Request' },
    { num: 7, name: 'Subagent Review' },
    { num: 8, name: 'Release & Done' }
  ];

  let output = `\n================================================================\n`;
  output += `📊 SDLC LIFECYCLE PROGRESS: #${session.issueId} (${session.type?.toUpperCase() || 'FEAT'})\n`;
  output += `🌿 Branch: ${session.branch} ${session.worktree ? '| Worktree: ' + session.worktree : ''}\n`;
  output += `================================================================\n`;

  for (const s of stages) {
    let icon = '⬜';
    if (s.num < session.stage) {
      icon = '✅';
    } else if (s.num === session.stage) {
      icon = '▶️ ';
    }
    const checkpoint = session.manifest?.checkpoints?.[`stage${s.num}`];
    const timeStr = checkpoint ? ` (${new Date(checkpoint).toLocaleTimeString()})` : '';
    output += ` ${icon} Stage ${s.num}: ${s.name.padEnd(18)} ${s.num === session.stage ? '<-- CURRENT' : ''}${timeStr}\n`;
  }

  if (session.remediationCount > 0) {
    output += `\n⚠️ Remediation Retries: ${session.remediationCount}/${session.maxRemediations} (Circuit breaker threshold: ${session.maxRemediations})\n`;
  }
  output += `================================================================\n`;
  return output;
}

export function resumeSession(repoRoot = '.') {
  const current = getSessionStatus(repoRoot);
  if (!current.active || !current.session) {
    return { active: false, message: 'No active session to resume.' };
  }

  const session = current.session;
  console.log(`🔄 Resuming SDLC Workflow Session for #${session.issueId} on branch '${session.branch}'...`);
  console.log(`📍 Current Stage: Stage ${session.stage} (${session.stageName})`);
  console.log(`🔁 Remediation Loops: ${session.remediationCount || 0}/${session.maxRemediations || 3}`);
  if (session.worktree) {
    console.log(`🌲 Git Worktree: ${session.worktree}`);
  }

  return {
    active: true,
    session,
    nextRecommendedAction: `Continue from Stage ${session.stage}: ${session.stageName}`,
    dashboard: renderDashboard(session)
  };
}

export function releaseSession(repoRoot = '.', issueId = null) {
  const sessionPath = getSessionPath(repoRoot);
  const current = getSessionStatus(repoRoot);

  if (!fileExists(sessionPath)) {
    return { success: true, released: false };
  }

  const targetId = issueId || current.activeSessionId;

  if (current.sessions && targetId && current.sessions[String(targetId)]) {
    const targetSession = current.sessions[String(targetId)];
    if (targetSession.worktree && fileExists(path.resolve(repoRoot, targetSession.worktree))) {
      try {
        console.log(`🌲 Pruning Git worktree: ${targetSession.worktree}...`);
        try {
          execFileSync('git', ['worktree', 'remove', '--force', '--', targetSession.worktree], { cwd: repoRoot, stdio: 'pipe' });
        } catch {}
      } catch {}
    }

    delete current.sessions[String(targetId)];
    const remainingKeys = Object.keys(current.sessions);

    if (remainingKeys.length === 0) {
      try {
        fs.unlinkSync(sessionPath);
      } catch {}
      return { success: true, released: true, remainingSessions: 0 };
    }

    const nextActiveId = Number(remainingKeys[0]);
    const registry = {
      activeSessionId: nextActiveId,
      sessions: current.sessions
    };
    fs.writeFileSync(sessionPath, JSON.stringify(registry, null, 2), 'utf8');
    return { success: true, released: true, activeSessionId: nextActiveId, remainingSessions: remainingKeys.length };
  }

  try {
    fs.unlinkSync(sessionPath);
    return { success: true, released: true };
  } catch {
    return { success: false, released: false, error: 'Failed to delete session file' };
  }
}

export function getExemptionsPath(repoRoot = '.') {
  return path.resolve(repoRoot, '.agent', 'sdlc-exemptions.json');
}

export function recordExemption({ mutantId, file, reason, approvedBy = 'human-review', repoRoot = '.' } = {}) {
  const exPath = getExemptionsPath(repoRoot);
  fs.mkdirSync(path.dirname(exPath), { recursive: true });

  let ledger = { exemptions: [] };
  if (fileExists(exPath)) {
    try {
      ledger = JSON.parse(readFileSafe(exPath)) || { exemptions: [] };
    } catch {
      ledger = { exemptions: [] };
    }
  }

  const entry = {
    id: `EX-${Date.now() % 100000}`,
    mutantId: mutantId || 'unknown',
    file: file || null,
    reason: reason || 'Verified benign mutant / compiler artifact',
    approvedBy,
    timestamp: new Date().toISOString()
  };

  ledger.exemptions.push(entry);
  fs.writeFileSync(exPath, JSON.stringify(ledger, null, 2), 'utf8');
  console.log(`🛡️ Exemption registered: [${entry.id}] ${entry.mutantId} -> ${entry.reason}`);
  return { success: true, exemption: entry };
}

export function getExemptions(repoRoot = '.') {
  const exPath = getExemptionsPath(repoRoot);
  if (!fileExists(exPath)) return [];
  try {
    const data = JSON.parse(readFileSafe(exPath));
    return Array.isArray(data.exemptions) ? data.exemptions : [];
  } catch {
    return [];
  }
}

export function calculateSessionStats(repoRoot = '.') {
  const sessionStatus = getSessionStatus(repoRoot);
  const activeSess = sessionStatus.session;
  const sessions = sessionStatus.sessions || {};
  const totalSessions = Object.keys(sessions).length;

  const jsonlPath = path.resolve(repoRoot, '.agent/arch-trace.jsonl');
  let traceEventsCount = 0;
  if (fileExists(jsonlPath)) {
    try {
      const content = readFileSafe(jsonlPath);
      traceEventsCount = content.split('\n').filter(Boolean).length;
    } catch {}
  }

  const estimatedHoursSaved = totalSessions * 2.5 + traceEventsCount * 0.5;
  const standardHourlyRateUSD = 120;
  const totalDollarSavingsUSD = Math.round(estimatedHoursSaved * standardHourlyRateUSD);

  const completedSessions = Object.values(sessions).filter((s) => s.stage >= 5).length;
  const velocityScore = Math.min(100, Math.round((completedSessions / Math.max(1, totalSessions)) * 100));

  return {
    activeSessionId: sessionStatus.activeSessionId,
    totalSessions,
    completedSessions,
    traceEventsCount,
    estimatedHoursSaved: Number(estimatedHoursSaved.toFixed(1)),
    totalDollarSavingsUSD,
    velocityScore,
    currentStage: activeSess ? `${activeSess.stage}/8 (${activeSess.stageName})` : 'Idle',
    activeBranch: activeSess ? activeSess.branch : 'main'
  };
}

function parsePct(value) {
  if (value === null || value === undefined || value === '') return undefined;
  const n = Number.parseFloat(String(value).replace('%', ''));
  return Number.isFinite(n) ? n : undefined;
}

/**
 * Builds gate-receipt inputs strictly from what was measured and recorded in
 * the session manifest by `coverage`, `mutation` and `review record`.
 * Anything not measured is left undefined so issueGateReceipt refuses.
 * Mutation is reported as `null` (not configured) only when every module's
 * `thresholds.mutationScore` is explicitly null in .agent/sdlc-config.json.
 */
export function receiptInputsFromSession(session, { driftCheckPassed, repoRoot = '.' } = {}) {
  const manifest = session?.manifest || {};
  const isDocsOnly = Boolean(manifest.docsOnly);
  const coveragePct = isDocsOnly
    ? null
    : parsePct(manifest.coverageReport ?? manifest.coveragePct ?? manifest.coverage);
  let mutationPct = isDocsOnly
    ? null
    : parsePct(manifest.mutationScore ?? manifest.mutationPct ?? manifest.mutation);
  if (mutationPct === undefined && !isDocsOnly) {
    try {
      const cfg = JSON.parse(readFileSafe(path.resolve(repoRoot, '.agent/sdlc-config.json')) || '{}');
      const mods = Array.isArray(cfg.modules) ? cfg.modules : [];
      if (mods.length > 0 && mods.every((m) => m?.thresholds && m.thresholds.mutationScore === null)) mutationPct = null;
    } catch {}
  }
  const review = manifest.peerReview;
  return {
    coveragePct,
    mutationPct,
    docsOnly: isDocsOnly ? true : undefined,
    peerReviewApproved: review && review.verdict ? review.verdict === 'approve' : undefined,
    reviewer: review?.reviewer || null,
    driftCheckPassed
  };
}

/**
 * Composite Stage Orchestrator (CAP-01)
 * Collapses ~15 granular bookkeeping calls into 3 deterministic lifecycle transitions:
 * - begin: provisions/links ticket, starts Stage 2 session, checks out feature branch, transitions PM -> in-progress
 * - pre-pr: advances to Stage 6, runs Spec-Drift check, syncs DECISION_MAP.md, records ArchTrace, issues signed gate receipt from measured values, builds PR summary
 * - complete: advances to Stage 8, transitions PM -> done, syncs KANBAN.md, and releases session lock
 */
export function executeCompositeStage({
  action = 'begin',
  issueId = null,
  title = null,
  type = 'feat',
  branch = null,
  worktree = null,
  prNumber = null,
  summary = null,
  checkoutBranch = false,
  repoRoot = '.'
} = {}) {
  const normalizedAction = String(action || 'begin').toLowerCase();

  if (normalizedAction === 'begin') {
    let resolvedIssueId = issueId ? String(issueId).replace(/^#/, '') : null;
    if (!resolvedIssueId) {
      const created = createPMIssue({
        title: title || 'Automated SDLC Task',
        type,
        body: summary || title || 'Provisioned via sdlc-engine stage begin',
        repoRoot
      });
      resolvedIssueId = String(created.issueId || '1').replace(/^#/, '');
    }

    const slug = (title || 'sdlc-task')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-|-$/g, '')
      .slice(0, 40);
    const prefix = type === 'hotfix' ? 'hotfix' : 'feature';
    const targetBranch =
      branch || (title ? `${prefix}/issue-#${resolvedIssueId}-${slug}` : `${prefix}/issue-#${resolvedIssueId}`);

    const started = startSession({
      issueId: resolvedIssueId,
      branch: targetBranch,
      stage: 2,
      stageName: 'Architecture & Branch Creation',
      type,
      worktree,
      repoRoot,
      force: true
    });

    if (checkoutBranch && fileExists(path.resolve(repoRoot, '.git'))) {
      try {
        execFileSync('git', ['checkout', '-B', targetBranch], { cwd: repoRoot, stdio: 'pipe' });
      } catch {}
    }

    try {
      transitionPMState({ issueId: resolvedIssueId, state: 'in-progress', repoRoot });
    } catch {}

    const wb = ensureWorkbenchRunning({ repoRoot });
    const uiWidget = generateArchitectureWidget({ repoRoot });

    return {
      success: true,
      action: 'begin',
      issueId: resolvedIssueId,
      branch: targetBranch,
      stage: 2,
      session: started.session,
      workbenchUrl: wb.url,
      widgetPath: uiWidget.widgetPath
    };
  }

  if (normalizedAction === 'pre-pr') {
    const current = getSessionStatus(repoRoot);
    const resolvedIssueId = issueId
      ? String(issueId).replace(/^#/, '')
      : current.session?.issueId
        ? String(current.session.issueId)
        : null;

    if (!current.active && resolvedIssueId) {
      startSession({
        issueId: resolvedIssueId,
        stage: 5,
        stageName: 'Docs & Showcase',
        repoRoot,
        force: true
      });
    }

    updateSession({
      stage: 5,
      stageName: 'Docs & Showcase',
      repoRoot
    });

    const updated = updateSession({
      stage: 6,
      stageName: 'Pull Request & CI',
      repoRoot
    });

    const drift = checkSpecDrift({ repoRoot });
    const wikiAudit = auditWiki(repoRoot);
    const decisionMap = syncDecisionMapMarkdown(repoRoot);
    const trace = recordArchTrace({
      reason: summary || `Pre-PR verification for ticket #${updated.session.issueId}`,
      ticket: updated.session.issueId,
      repoRoot
    });

    const prePrPassed = Boolean(drift.passed && wikiAudit.passed);
    let receipt = null;
    let receiptError = null;
    if (prePrPassed) {
      const inputs = receiptInputsFromSession(updated.session, { driftCheckPassed: drift.passed, repoRoot });
      const issued = issueGateReceipt({
        issueId: updated.session.issueId,
        branch: updated.session.branch || 'feature/branch',
        ...inputs,
        repoRoot
      });
      if (issued.success) receipt = issued.receipt;
      else receiptError = issued.reason;
    } else {
      receiptError = 'Spec-drift or wiki audit failed; no receipt issued.';
    }

    const prSummary = generatePRSummary({ issueId: updated.session.issueId, repoRoot });
    const prRes = ensurePullRequest({
      issueId: updated.session.issueId,
      branch: updated.session.branch,
      title: summary
        ? `${updated.session.type || 'feat'}(#${updated.session.issueId}): ${summary}`
        : `${updated.session.type || 'feat'}(#${updated.session.issueId}): ${updated.session.branch || 'SDLC Delivery'}`,
      body: prSummary.markdown,
      repoRoot
    });
    const wb = ensureWorkbenchRunning({ repoRoot });
    const uiWidget = generateArchitectureWidget({ repoRoot });

    return {
      success: Boolean(prePrPassed),
      action: 'pre-pr',
      receiptError,
      issueId: updated.session.issueId,
      prNumber: prRes.prNumber || updated.session.manifest?.prNumber || null,
      prUrl: prRes.prUrl || updated.session.manifest?.prUrl || null,
      prCreated: Boolean(prRes.created),
      stage: 6,
      drift,
      wikiAudit,
      decisionMapPath: decisionMap.path || decisionMap.markdownPath || decisionMap.outputPath,
      traceEntry: trace.entry,
      receipt,
      prSummary,
      workbenchUrl: wb.url,
      widgetPath: uiWidget.widgetPath
    };
  }

  if (normalizedAction === 'complete') {
    const current = getSessionStatus(repoRoot);
    const resolvedIssueId = issueId
      ? String(issueId).replace(/^#/, '')
      : current.session?.issueId
        ? String(current.session.issueId)
        : null;

    if (current.active) {
      updateSession({
        stage: 8,
        stageName: 'Release & Retrospective',
        manifestUpdate: prNumber ? { prNumber } : {},
        repoRoot
      });
    }

    if (resolvedIssueId) {
      try {
        transitionPMState({ issueId: resolvedIssueId, state: 'done', repoRoot });
      } catch {}
    }

    try {
      syncKanbanBoard(repoRoot);
    } catch {}

    const released = releaseSession(repoRoot, resolvedIssueId);

    return {
      success: true,
      action: 'complete',
      issueId: resolvedIssueId,
      stage: 8,
      released: released.released
    };
  }

  throw new Error(`Unknown composite stage action '${action}'. Valid actions: begin, pre-pr, complete.`);
}
