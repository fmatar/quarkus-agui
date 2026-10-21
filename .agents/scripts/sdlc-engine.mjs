#!/usr/bin/env node
/**
 * Agentic SDLC Framework: Universal Stack Discovery, Autopilot & Governance Engine
 * Modular barrel export & CLI router delegating to `.agent/scripts/lib/*.mjs`.
 */

import fs from 'node:fs';
import path from 'node:path';
import { execSync } from 'node:child_process';

import {
  loadEnv,
  fileExists,
  readFileSafe,
  execCommand,
  execAsync,
  runParallelTasks,
  parseFrontmatter,
  inspectArchlens,
  detectPMProvider,
  diagnoseEnvironment,
  getModifiedFiles
} from './lib/core.mjs';

import {
  getGateCachePath,
  computeTreeHash,
  getGateCacheHit,
  recordGateCache,
  getGateCacheStats,
  clearGateCache
} from './lib/cache.mjs';

import {
  AUDIT_LEDGER_FILE,
  GENESIS_HASH,
  calculateRecordHash,
  appendAuditRecord,
  verifyAuditChain,
  issueGateReceipt,
  verifyGateReceipt
} from './lib/audit-chain.mjs';

import {
  DECISION_GRAPH_FILE,
  DECISION_MAP_MD,
  appendDecisionEvent,
  buildDecisionGraph,
  syncDecisionMapMarkdown
} from './lib/decision-graph.mjs';

import {
  initProductConstitution,
  parseProductMarkdown,
  planProductDAG,
  controlProductDAG,
  deconstructEpic
} from './lib/product-dag.mjs';

import {
  listADRs,
  createADR,
  decideADR,
  checkSpecDrift,
  auditContractDrift
} from './lib/adr-drift.mjs';

import {
  extractExportSignatures,
  generateTicketDossier
} from './lib/dossier.mjs';

import {
  verifyCommitGuard,
  verifyMergeGuard,
  verifyPushGuard,
  installHooks,
  applyBranchProtection
} from './lib/git-guard.mjs';

import {
  loadIssueTemplate,
  formatTemplatedIssue,
  createTicket,
  transitionPMState,
  postPMComment,
  installFramework,
  pollCIPRChecks,
  parseSemVer,
  bumpSemVer
} from './lib/pm-provider.mjs';

import {
  getSessionPath,
  getSessionStatus,
  startSession,
  updateSession,
  switchSession,
  listSessions,
  renderDashboard,
  resumeSession,
  releaseSession,
  getExemptionsPath,
  recordExemption,
  getExemptions,
  calculateSessionStats
} from './lib/session.mjs';

import {
  checkArchDiff,
  parseMermaidDiagram,
  diffMermaidGraphs,
  recordArchTrace,
  scaffoldArchlensPolicy
} from './lib/arch-trace.mjs';

import {
  runDoctor,
  generateRegressionProof,
  monitorCanaryHealthAndWatchdog,
  installCIWorkflow,
  exportAgentRules,
  scaffoldReproductionTest,
  assessRisk,
  generatePRSummary,
  findAffectededTests,
  runFlakeCheck,
  auditSecurity,
  suggestMutationStrengthening,
  resolveAdHocTargets,
  generateReviewPlan,
  inspectCIWorkflows,
  detectStackInDir,
  discoverModules,
  matchFilesToModules,
  generateConfig,
  printPreview
} from './lib/quality-gate.mjs';

import {
  renderWorkbenchHTML,
  createWorkbenchServer
} from './lib/workbench-server.mjs';

// --- Re-export complete public API for backward compatibility & modular tests ---
export {
  loadEnv,
  fileExists,
  readFileSafe,
  execCommand,
  execAsync,
  runParallelTasks,
  parseFrontmatter,
  inspectArchlens,
  detectPMProvider,
  diagnoseEnvironment,
  getModifiedFiles,
  getGateCachePath,
  computeTreeHash,
  getGateCacheHit,
  recordGateCache,
  getGateCacheStats,
  clearGateCache,
  AUDIT_LEDGER_FILE,
  GENESIS_HASH,
  calculateRecordHash,
  appendAuditRecord,
  verifyAuditChain,
  issueGateReceipt,
  verifyGateReceipt,
  DECISION_GRAPH_FILE,
  DECISION_MAP_MD,
  appendDecisionEvent,
  buildDecisionGraph,
  syncDecisionMapMarkdown,
  initProductConstitution,
  parseProductMarkdown,
  planProductDAG,
  controlProductDAG,
  deconstructEpic,
  listADRs,
  createADR,
  decideADR,
  checkSpecDrift,
  auditContractDrift,
  extractExportSignatures,
  generateTicketDossier,
  verifyCommitGuard,
  verifyMergeGuard,
  verifyPushGuard,
  installHooks,
  applyBranchProtection,
  loadIssueTemplate,
  formatTemplatedIssue,
  createTicket,
  transitionPMState,
  postPMComment,
  installFramework,
  pollCIPRChecks,
  parseSemVer,
  bumpSemVer,
  getSessionPath,
  getSessionStatus,
  startSession,
  updateSession,
  switchSession,
  listSessions,
  renderDashboard,
  resumeSession,
  releaseSession,
  getExemptionsPath,
  recordExemption,
  getExemptions,
  calculateSessionStats,
  checkArchDiff,
  parseMermaidDiagram,
  diffMermaidGraphs,
  recordArchTrace,
  scaffoldArchlensPolicy,
  runDoctor,
  generateRegressionProof,
  monitorCanaryHealthAndWatchdog,
  installCIWorkflow,
  exportAgentRules,
  scaffoldReproductionTest,
  assessRisk,
  generatePRSummary,
  findAffectededTests,
  runFlakeCheck,
  auditSecurity,
  suggestMutationStrengthening,
  resolveAdHocTargets,
  generateReviewPlan,
  inspectCIWorkflows,
  detectStackInDir,
  discoverModules,
  matchFilesToModules,
  generateConfig,
  printPreview,
  renderWorkbenchHTML,
  createWorkbenchServer
};

export function executeRelease({
  bump = 'patch',
  dryRun = false,
  skipGate = false,
  repoRoot = '.'
} = {}) {
  console.log(`🚀 Preparing ${bump.toUpperCase()} Release (dryRun: ${dryRun})...`);

  if (!skipGate && !dryRun) {
    console.log('🛡️ Verifying Quality Gate before release...');
    const gateRes = runCLI(['gate'], null, repoRoot);
    if (!gateRes.success) {
      throw new Error('Quality gate failed. Release aborted.');
    }
  }

  let currentVersion = '1.0.0';
  let manifestType = 'package.json';
  const pkgPath = path.resolve(repoRoot, 'package.json');
  const pomPath = path.resolve(repoRoot, 'pom.xml');
  const pyprojectPath = path.resolve(repoRoot, 'pyproject.toml');
  const cargoPath = path.resolve(repoRoot, 'Cargo.toml');

  if (fileExists(pkgPath)) {
    try {
      const pkg = JSON.parse(readFileSafe(pkgPath));
      if (pkg.version) {
        currentVersion = pkg.version;
        manifestType = 'package.json';
      }
    } catch {}
  } else if (fileExists(pomPath)) {
    const pomContent = readFileSafe(pomPath) || '';
    const match = pomContent.match(/<version>([0-9]+\.[0-9]+\.[0-9]+[^<]*)<\/version>/);
    if (match) {
      currentVersion = match[1];
      manifestType = 'pom.xml';
    }
  } else if (fileExists(pyprojectPath)) {
    const pyContent = readFileSafe(pyprojectPath) || '';
    const match = pyContent.match(/version\s*=\s*["']([^"']+)["']/);
    if (match) {
      currentVersion = match[1];
      manifestType = 'pyproject.toml';
    }
  } else if (fileExists(cargoPath)) {
    const cargoContent = readFileSafe(cargoPath) || '';
    const match = cargoContent.match(/version\s*=\s*["']([^"']+)["']/);
    if (match) {
      currentVersion = match[1];
      manifestType = 'Cargo.toml';
    }
  }

  const newVersion = bumpSemVer(currentVersion, bump);
  const releaseTag = `v${newVersion}`;
  const today = new Date().toISOString().split('T')[0];

  if (dryRun) {
    return {
      success: true,
      dryRun: true,
      currentVersion,
      newVersion,
      releaseTag,
      manifestType
    };
  }

  const updatedFiles = [];
  if (manifestType === 'package.json') {
    const pkg = JSON.parse(readFileSafe(pkgPath));
    pkg.version = newVersion;
    fs.writeFileSync(pkgPath, JSON.stringify(pkg, null, 2) + '\n', 'utf8');
    updatedFiles.push('package.json');
  } else if (manifestType === 'pom.xml') {
    let pom = readFileSafe(pomPath);
    pom = pom.replace(/<version>[0-9]+\.[0-9]+\.[0-9]+[^<]*<\/version>/, `<version>${newVersion}</version>`);
    fs.writeFileSync(pomPath, pom, 'utf8');
    updatedFiles.push('pom.xml');
  } else if (manifestType === 'pyproject.toml') {
    let py = readFileSafe(pyprojectPath);
    py = py.replace(/version\s*=\s*["'][^"']+["']/, `version = "${newVersion}"`);
    fs.writeFileSync(pyprojectPath, py, 'utf8');
    updatedFiles.push('pyproject.toml');
  } else if (manifestType === 'Cargo.toml') {
    let cargo = readFileSafe(cargoPath);
    cargo = cargo.replace(/version\s*=\s*["'][^"']+["']/, `version = "${newVersion}"`);
    fs.writeFileSync(cargoPath, cargo, 'utf8');
    updatedFiles.push('Cargo.toml');
  }

  const changelogPath = path.resolve(repoRoot, 'CHANGELOG.md');
  if (fileExists(changelogPath)) {
    let changelog = readFileSafe(changelogPath);
    const unreleasedHeader = '## [Unreleased]';
    const releaseHeader = `## [Unreleased]\n\n## [${newVersion}] - ${today}`;
    if (changelog.includes(unreleasedHeader)) {
      changelog = changelog.replace(unreleasedHeader, releaseHeader);
      fs.writeFileSync(changelogPath, changelog, 'utf8');
      updatedFiles.push('CHANGELOG.md');
    }
  }

  try {
    execSync(`git add ${updatedFiles.join(' ')}`, { cwd: repoRoot, stdio: 'pipe' });
    execSync(`git commit -m "chore(release): ${releaseTag}"`, { cwd: repoRoot, stdio: 'pipe' });
    execSync(`git tag -a ${releaseTag} -m "Release ${releaseTag}"`, { cwd: repoRoot, stdio: 'pipe' });
  } catch {}

  return {
    success: true,
    dryRun: false,
    currentVersion,
    newVersion,
    releaseTag,
    updatedFiles
  };
}

// --- Universal CLI Router ---

export function runCLI(customArgs = null, customConfigPath = null, repoRoot = '.') {
  const args = customArgs || process.argv.slice(2);
  const command = args[0] || 'detect';
  const configPath = customConfigPath || path.resolve(repoRoot, '.agent/sdlc-config.json');

  let config;
  let reGenerated = false;

  if (fileExists(configPath)) {
    try {
      config = JSON.parse(readFileSafe(configPath));
      if (!config || !Array.isArray(config.modules)) throw new Error('Invalid config schema');
    } catch {
      config = generateConfig(repoRoot);
      fs.mkdirSync(path.dirname(configPath), { recursive: true });
      fs.writeFileSync(configPath, JSON.stringify(config, null, 2));
      reGenerated = true;
    }
  } else {
    config = generateConfig(repoRoot);
    fs.mkdirSync(path.dirname(configPath), { recursive: true });
    fs.writeFileSync(configPath, JSON.stringify(config, null, 2));
    reGenerated = true;
  }

  const result = {
    command,
    config,
    reGenerated,
    configPath,
    modulesCount: config.modules?.length || 0,
    success: true
  };

  switch (command) {
    case 'detect': {
      printPreview(config);
      return result;
    }

    case 'test': {
      const files = getModifiedFiles('main', repoRoot);
      const affectedOnly = args.includes('--affected-only');

      if (affectedOnly) {
        const affectedTests = findAffectededTests(files, repoRoot);
        if (affectedTests.length > 0) {
          for (const testFile of affectedTests) {
            execSync(`node --test "${testFile}"`, { stdio: 'inherit', cwd: repoRoot });
          }
          result.affectedTests = affectedTests;
          return result;
        }
      }

      const activeModules = files.length > 0 ? matchFilesToModules(files, config.modules) : config.modules;
      for (const mod of activeModules) {
        const cwd = path.resolve(repoRoot, mod.path);
        execSync(mod.commands.test, { stdio: 'inherit', cwd });
      }
      result.activeModules = activeModules;
      return result;
    }

    case 'coverage': {
      const files = getModifiedFiles('main', repoRoot);
      const activeModules = files.length > 0 ? matchFilesToModules(files, config.modules) : config.modules;
      for (const mod of activeModules) {
        const hit = getGateCacheHit({ checkType: 'coverage', modulePath: mod.path, repoRoot });
        if (hit.hit && !args.includes('--no-cache')) {
          console.log(`⚡ [${mod.name}] SHA-256 Coverage Gate Cache Hit (${hit.treeHash.slice(0, 10)})`);
          continue;
        }
        const cwd = path.resolve(repoRoot, mod.path);
        execSync(mod.commands.coverage, { stdio: 'inherit', cwd });
        recordGateCache({ checkType: 'coverage', modulePath: mod.path, result: { passed: true }, repoRoot });
      }
      result.activeModules = activeModules;
      return result;
    }

    case 'mutation': {
      const files = getModifiedFiles('main', repoRoot);
      const activeModules = files.length > 0 ? matchFilesToModules(files, config.modules) : config.modules;
      for (const mod of activeModules) {
        const hit = getGateCacheHit({ checkType: 'mutation', modulePath: mod.path, repoRoot });
        if (hit.hit && !args.includes('--no-cache')) {
          console.log(`⚡ [${mod.name}] SHA-256 Mutation Gate Cache Hit (${hit.treeHash.slice(0, 10)})`);
          continue;
        }
        const cwd = path.resolve(repoRoot, mod.path);
        execSync(mod.commands.mutation, { stdio: 'inherit', cwd });
        recordGateCache({ checkType: 'mutation', modulePath: mod.path, result: { passed: true }, repoRoot });
      }
      result.activeModules = activeModules;
      return result;
    }

    case 'gate': {
      for (const mod of config.modules) {
        const hit = getGateCacheHit({ checkType: 'gate', modulePath: mod.path, repoRoot });
        if (hit.hit && !args.includes('--no-cache') && !args.includes('--strict')) {
          console.log(`⚡ [${mod.name}] SHA-256 Quality Gate Cache Hit (${hit.treeHash.slice(0, 10)})`);
          continue;
        }
        const cwd = path.resolve(repoRoot, mod.path);
        if (mod.commands.lint) execSync(mod.commands.lint, { stdio: 'inherit', cwd });
        if (mod.commands.build) execSync(mod.commands.build, { stdio: 'inherit', cwd });
        execSync(mod.commands.coverage, { stdio: 'inherit', cwd });
        recordGateCache({ checkType: 'gate', modulePath: mod.path, result: { passed: true }, repoRoot });
      }

      const archInfo = inspectArchlens(repoRoot);
      if (archInfo.installed || config.archlens?.enabled) {
        try {
          execSync('npx -y @fmatar/archlens-skill prompt --path . 2>/dev/null || true', { cwd: repoRoot, stdio: 'inherit' });
        } catch {}
      }

      const isStrict = args.includes('--strict');
      if (isStrict) {
        result.risk = assessRisk([], repoRoot);
        const secRes = auditSecurity(repoRoot);
        result.security = secRes;
        if (!secRes.passed) throw new Error('Security Audit failed: Hardcoded secrets or critical CVEs detected.');

        const archDiffRes = checkArchDiff(repoRoot);
        result.archDiff = archDiffRes;
        if (archDiffRes.enabled && !archDiffRes.passed) {
          throw new Error(`Archlens Diff Check failed: ${archDiffRes.violationsCount} concentric ring layer inversion(s) detected.`);
        }

        const flakeRes = runFlakeCheck({ runs: 3, repoRoot });
        result.flake = flakeRes;
        if (flakeRes.flaky) throw new Error(`Flaky Test Check failed: ${flakeRes.flakyCount} flaky test(s) detected.`);

        const contractRes = auditContractDrift({ repoRoot });
        result.contract = contractRes;
        if (contractRes.hasDrift) {
          throw new Error(`API Contract Drift detected: ${contractRes.breakingChangesCount} breaking change(s) found!`);
        }

        const auditRes = verifyAuditChain(repoRoot);
        result.audit = auditRes;
        if (!auditRes.valid) throw new Error(`Merkle Audit Chain Verification failed: ${auditRes.error}`);
      }

      return result;
    }

    case 'product': {
      const sub = args[1] || 'status';
      result.subcommand = sub;
      if (sub === 'init') {
        let title = 'Autonomous Product System';
        let vision = 'End-to-end spec-driven system.';
        for (let i = 2; i < args.length; i++) {
          if (args[i] === '--title' && args[i + 1]) title = args[++i];
          else if (args[i] === '--vision' && args[i + 1]) vision = args[++i];
        }
        result.product = initProductConstitution({ title, vision, repoRoot });
        return result;
      }
      if (sub === 'plan') {
        const autoProvision = args.includes('--provision');
        result.product = planProductDAG({ repoRoot, autoProvision });
        return result;
      }
      if (sub === 'control') {
        const action = args[2] || 'status';
        result.product = controlProductDAG({ action, repoRoot });
        return result;
      }
      result.product = parseProductMarkdown(repoRoot);
      return result;
    }

    case 'adr': {
      const sub = args[1] || 'list';
      result.subcommand = sub;
      if (sub === 'create') {
        let title = 'Architectural Decision';
        let scope = 'technical';
        let ticketId = null;
        let supersedes = [];
        let context = '';
        let decision = '';
        let consequences = '';
        for (let i = 2; i < args.length; i++) {
          if (args[i] === '--title' && args[i + 1]) title = args[++i];
          else if (args[i] === '--scope' && args[i + 1]) scope = args[++i];
          else if (args[i] === '--ticket' && args[i + 1]) ticketId = Number(args[++i]) || args[i];
          else if (args[i] === '--supersedes' && args[i + 1]) supersedes = args[++i].split(',').map((s) => s.trim());
          else if (args[i] === '--context' && args[i + 1]) context = args[++i];
          else if (args[i] === '--decision' && args[i + 1]) decision = args[++i];
          else if (args[i] === '--consequences' && args[i + 1]) consequences = args[++i];
        }
        result.adr = createADR({ title, scope, ticketId, supersedes, context, decision, consequences, repoRoot });
        return result;
      }
      if (sub === 'decide') {
        const adrId = args[2];
        const action = args[3] || 'approve';
        result.adr = decideADR({ adrId, action, repoRoot });
        return result;
      }
      result.adrs = listADRs(repoRoot);
      return result;
    }

    case 'drift': {
      const sub = args[1] || 'check';
      result.subcommand = sub;
      const sess = getSessionStatus(repoRoot);
      result.drift = checkSpecDrift({ ticketId: sess.session?.issueId || null, repoRoot });
      result.success = result.drift.passed;
      return result;
    }

    case 'decision-graph': {
      const sub = args[1] || 'build';
      result.subcommand = sub;
      if (sub === 'sync') {
        result.decisionGraph = syncDecisionMapMarkdown(repoRoot);
        return result;
      }
      result.decisionGraph = buildDecisionGraph(repoRoot);
      return result;
    }

    case 'dossier': {
      let issueId = 1;
      let capabilityId = null;
      const targetFiles = [];
      for (let i = 1; i < args.length; i++) {
        if (args[i] === '--issue' && args[i + 1]) issueId = Number(args[++i]) || args[i];
        else if (args[i] === '--cap' && args[i + 1]) capabilityId = args[++i];
        else if (!args[i].startsWith('-') && args[i] !== 'generate') targetFiles.push(args[i]);
      }
      result.dossier = generateTicketDossier({ issueId, capabilityId, targetFiles, repoRoot });
      return result;
    }

    case 'guard': {
      const sub = args[1] || 'pre-commit';
      result.subcommand = sub;
      if (sub === 'pre-commit') {
        result.guard = verifyCommitGuard({ repoRoot });
        result.success = result.guard.allowed;
        if (!result.guard.allowed) console.error(result.guard.message);
        return result;
      }
      if (sub === 'pre-merge') {
        result.guard = verifyMergeGuard({ repoRoot });
        result.success = result.guard.allowed;
        if (!result.guard.allowed) console.error(result.guard.message);
        return result;
      }
      if (sub === 'pre-push') {
        result.guard = verifyPushGuard({ repoRoot });
        result.success = result.guard.allowed;
        if (!result.guard.allowed) console.error(result.guard.message);
        return result;
      }
      if (sub === 'receipt') {
        const sess = getSessionStatus(repoRoot);
        result.receipt = issueGateReceipt({
          issueId: sess.session?.issueId || 1,
          branch: sess.session?.branch || 'feature/branch',
          repoRoot
        });
        return result;
      }
      return result;
    }

    case 'cache': {
      const sub = args[1] || 'status';
      if (sub === 'clear') {
        result.cache = clearGateCache(repoRoot);
        return result;
      }
      result.cache = getGateCacheStats(repoRoot);
      return result;
    }

    case 'session': {
      const sub = args[1] || 'status';
      result.subcommand = sub;

      if (sub === 'status') {
        const status = getSessionStatus(repoRoot);
        result.status = status;
        return result;
      }
      if (sub === 'dashboard') {
        const status = getSessionStatus(repoRoot);
        if (status.active && status.session) {
          result.dashboard = renderDashboard(status.session);
        }
        return result;
      }
      if (sub === 'resume') {
        const resumeRes = resumeSession(repoRoot);
        return { ...result, ...resumeRes };
      }
      if (sub === 'start') {
        let issueId = null,
          branch = null,
          stage = 1,
          stageName = 'Intake & Specification',
          type = 'feat',
          worktree = null,
          force = false;
        for (let i = 2; i < args.length; i++) {
          if (args[i] === '--issue' && args[i + 1]) issueId = args[++i];
          else if (args[i] === '--branch' && args[i + 1]) branch = args[++i];
          else if (args[i] === '--stage' && args[i + 1]) stage = args[++i];
          else if (args[i] === '--name' && args[i + 1]) stageName = args[++i];
          else if (args[i] === '--type' && args[i + 1]) type = args[++i];
          else if (args[i] === '--worktree' && args[i + 1]) worktree = args[++i];
          else if (args[i] === '--force') force = true;
        }
        const sessionRes = startSession({ issueId, branch, stage, stageName, type, worktree, repoRoot, force });
        return { ...result, ...sessionRes };
      }
      if (sub === 'update') {
        let stage = undefined,
          stageName = undefined,
          activeSubagents = undefined,
          specPath = undefined,
          prNumber = undefined,
          incrementRemediation = false;
        for (let i = 2; i < args.length; i++) {
          if (args[i] === '--stage' && args[i + 1]) stage = args[++i];
          else if (args[i] === '--name' && args[i + 1]) stageName = args[++i];
          else if (args[i] === '--subagents' && args[i + 1]) activeSubagents = args[++i].split(',').map((s) => s.trim());
          else if (args[i] === '--spec' && args[i + 1]) specPath = args[++i];
          else if (args[i] === '--pr' && args[i + 1]) prNumber = args[++i];
          else if (args[i] === '--remediate') incrementRemediation = true;
        }
        const manifestUpdate = {};
        if (specPath) manifestUpdate.specPath = specPath;
        if (prNumber) manifestUpdate.prNumber = Number(prNumber) || prNumber;

        const sessionRes = updateSession({
          stage,
          stageName,
          activeSubagents,
          manifestUpdate,
          incrementRemediation,
          repoRoot
        });
        return { ...result, ...sessionRes };
      }
      if (sub === 'list') {
        return { ...result, ...listSessions(repoRoot) };
      }
      if (sub === 'switch') {
        const targetId = args[2];
        if (!targetId) {
          result.success = false;
          return result;
        }
        return { ...result, ...switchSession(targetId, repoRoot) };
      }
      if (sub === 'release') {
        const targetId = args[2] ? Number(args[2]) : null;
        return { ...result, ...releaseSession(repoRoot, targetId) };
      }

      result.success = false;
      return result;
    }

    case 'exemption': {
      const sub = args[1] || 'list';
      result.subcommand = sub;
      if (sub === 'list') {
        result.exemptions = getExemptions(repoRoot);
        return result;
      }
      if (sub === 'add') {
        let mutantId = '',
          file = '',
          reason = '',
          approvedBy = 'human-review';
        for (let i = 2; i < args.length; i++) {
          if (args[i] === '--mutant' && args[i + 1]) mutantId = args[++i];
          else if (args[i] === '--file' && args[i + 1]) file = args[++i];
          else if (args[i] === '--reason' && args[i + 1]) reason = args[++i];
          else if (args[i] === '--by' && args[i + 1]) approvedBy = args[++i];
        }
        if (!mutantId && !file) {
          result.success = false;
          return result;
        }
        return { ...result, ...recordExemption({ mutantId, file, reason, approvedBy, repoRoot }) };
      }
      result.success = false;
      return result;
    }

    case 'check-ci': {
      const prNumber = args[1];
      if (!prNumber) {
        result.success = false;
        return result;
      }
      let timeoutSeconds = 300;
      for (let i = 2; i < args.length; i++) {
        if (args[i] === '--timeout' && args[i + 1]) timeoutSeconds = Number(args[++i]) || 300;
      }
      return { ...result, ...pollCIPRChecks(prNumber, { timeoutSeconds, repoRoot }) };
    }

    case 'resolve-targets': {
      const targetFiles = args.slice(1);
      result.plan = resolveAdHocTargets(targetFiles, config.modules, repoRoot);
      return result;
    }

    case 'env': {
      result.diagnostic = diagnoseEnvironment(repoRoot);
      return result;
    }

    case 'archlens': {
      const sub = args[1] || 'check';
      result.subcommand = sub;
      const archlensInfo = inspectArchlens(repoRoot);

      if (sub === 'check') {
        if (!archlensInfo.installed) {
          result.passed = true;
          result.skipped = true;
          return result;
        }
        try {
          execSync('npx -y @fmatar/archlens-skill prompt --path . 2>/dev/null || true', { cwd: repoRoot, stdio: 'inherit' });
          result.passed = true;
        } catch (err) {
          result.passed = false;
          result.error = err.message;
        }
        return result;
      }
      if (sub === 'update') {
        try {
          execSync('npx -y @fmatar/archlens-skill update --path .', { cwd: repoRoot, stdio: 'inherit' });
          result.passed = true;
        } catch {
          result.passed = false;
        }
        return result;
      }
      if (sub === 'diff') {
        return { ...result, ...checkArchDiff(repoRoot) };
      }
      if (sub === 'trace') {
        let reason = 'Architectural refinement',
          ticket = null,
          diagramPath = null;
        for (let i = 2; i < args.length; i++) {
          if (args[i] === '--reason' && args[i + 1]) reason = args[++i];
          else if (args[i] === '--ticket' && args[i + 1]) ticket = args[++i];
          else if (args[i] === '--diagram' && args[i + 1]) diagramPath = args[++i];
        }
        result.archTrace = recordArchTrace({ reason, ticket, diagramPath, repoRoot });
        return result;
      }
      if (sub === 'scaffold') {
        result.scaffold = scaffoldArchlensPolicy(repoRoot);
        return result;
      }
      result.success = false;
      return result;
    }

    case 'arch': {
      const sub = args[1] || 'trace';
      if (sub === 'trace') {
        let reason = 'Architectural refinement',
          ticket = null,
          diagramPath = null;
        for (let i = 2; i < args.length; i++) {
          if (args[i] === '--reason' && args[i + 1]) reason = args[++i];
          else if (args[i] === '--ticket' && args[i + 1]) ticket = args[++i];
          else if (args[i] === '--diagram' && args[i + 1]) diagramPath = args[++i];
        }
        result.archTrace = recordArchTrace({ reason, ticket, diagramPath, repoRoot });
        return result;
      }
      if (sub === 'diff') {
        return { ...result, ...checkArchDiff(repoRoot) };
      }
      result.success = false;
      return result;
    }

    case 'flake-check': {
      let runs = 5,
        target = null;
      for (let i = 1; i < args.length; i++) {
        if (args[i] === '--runs' && args[i + 1]) runs = Number(args[++i]) || 5;
        else if (args[i] === '--target' && args[i + 1]) target = args[++i];
      }
      result.flakeCheck = runFlakeCheck({ runs, target, repoRoot });
      return result;
    }

    case 'security-audit': {
      result.security = auditSecurity(repoRoot);
      return result;
    }

    case 'strengthen': {
      let mutantType = 'ConditionalsBoundary',
        file = null;
      for (let i = 1; i < args.length; i++) {
        if (args[i] === '--type' && args[i + 1]) mutantType = args[++i];
        else if (args[i] === '--file' && args[i + 1]) file = args[++i];
      }
      result.strengthen = suggestMutationStrengthening({ mutantType, file });
      return result;
    }

    case 'pm': {
      const sub = args[1] || 'status';
      result.subcommand = sub;
      const pm = detectPMProvider(repoRoot);

      if (sub === 'status') {
        result.pm = pm;
        return result;
      }
      if (sub === 'transition') {
        const issueId = args[2];
        const state = args[3] || 'in-progress';
        if (!issueId) {
          result.success = false;
          return result;
        }
        result.transitioned = transitionPMState({ issueId, state, repoRoot });
        return result;
      }
      if (sub === 'comment') {
        const issueId = args[2];
        let commentText = '',
          prNumber = null;
        for (let i = 3; i < args.length; i++) {
          if (args[i] === '--pr' && args[i + 1]) prNumber = args[++i];
          else if (args[i] === '--body' && args[i + 1]) commentText = args[++i];
        }
        if (!commentText) commentText = args.slice(3).filter((a) => !a.startsWith('--')).join(' ');
        if (!issueId || !commentText) {
          result.success = false;
          return result;
        }
        result.commented = postPMComment({ issueId, prNumber, comment: commentText, repoRoot });
        return result;
      }
      result.success = false;
      return result;
    }

    case 'ticket': {
      const sub = args[1] || 'create';
      result.subcommand = sub;
      if (sub === 'create') {
        let title = '',
          type = 'feat',
          body = '',
          problem = '',
          solution = '',
          acceptanceCriteria = '',
          reproduce = '',
          expected = '',
          environment = '';
        for (let i = 2; i < args.length; i++) {
          if (args[i] === '--title' && args[i + 1]) title = args[++i];
          else if (args[i] === '--type' && args[i + 1]) type = args[++i];
          else if (args[i] === '--body' && args[i + 1]) body = args[++i];
          else if (args[i] === '--problem' && args[i + 1]) problem = args[++i];
          else if (args[i] === '--solution' && args[i + 1]) solution = args[++i];
          else if ((args[i] === '--acceptance-criteria' || args[i] === '--criteria') && args[i + 1])
            acceptanceCriteria = args[++i];
          else if (args[i] === '--reproduce' && args[i + 1]) reproduce = args[++i];
          else if (args[i] === '--expected' && args[i + 1]) expected = args[++i];
          else if (args[i] === '--environment' && args[i + 1]) environment = args[++i];
        }
        if (!title) title = args.slice(2).filter((a) => !a.startsWith('--')).join(' ');
        if (!title) {
          result.success = false;
          return result;
        }
        const ticketRes = createTicket({
          title,
          type,
          body,
          problem,
          solution,
          acceptanceCriteria,
          reproduce,
          expected,
          environment,
          repoRoot
        });
        return { ...result, ...ticketRes };
      }
      result.success = false;
      return result;
    }

    case 'init':
    case 'install': {
      let targetDir = '.',
        global = false;
      for (let i = 1; i < args.length; i++) {
        if (args[i] === '--global' || args[i] === '-g') global = true;
        else if ((args[i] === '--target' || args[i] === '-t') && args[i + 1]) targetDir = args[++i];
        else if (!args[i].startsWith('-') && i === 1) targetDir = args[i];
      }
      result.installed = installFramework({ targetDir, global, repoRoot });
      return result;
    }

    case 'update': {
      const archInfo = inspectArchlens(repoRoot);
      if (archInfo.installed) {
        try {
          execSync('npx -y @fmatar/archlens-skill update --path .', { cwd: repoRoot, stdio: 'inherit' });
        } catch {}
      }
      const freshConfig = generateConfig(repoRoot);
      fs.mkdirSync(path.dirname(configPath), { recursive: true });
      fs.writeFileSync(configPath, JSON.stringify(freshConfig, null, 2));
      result.updated = true;
      return result;
    }

    case 'doctor': {
      const fix = args.includes('--fix');
      result.doctor = runDoctor(repoRoot, { fix });
      return result;
    }

    case 'review': {
      const sub = args[1] || 'plan';
      result.subcommand = sub;
      if (sub === 'plan') {
        result.reviewPlan = generateReviewPlan({ repoRoot });
        return result;
      }
      result.success = false;
      return result;
    }

    case 'release': {
      let bump = 'patch',
        dryRun = false,
        skipGate = false;
      for (let i = 1; i < args.length; i++) {
        if (args[i] === '--dry-run') dryRun = true;
        else if (args[i] === '--skip-gate') skipGate = true;
        else if (['patch', 'minor', 'major'].includes(args[i].toLowerCase())) bump = args[i].toLowerCase();
      }
      result.release = executeRelease({ bump, dryRun, skipGate, repoRoot });
      return result;
    }

    case 'hooks': {
      const sub = args[1] || 'install';
      if (sub === 'install') {
        result.hooks = installHooks({ repoRoot });
        return result;
      }
      result.success = false;
      return result;
    }

    case 'protect': {
      const branches = args.slice(1).filter((a) => !a.startsWith('-'));
      const targetBranches = branches.length > 0 ? branches : ['main', 'develop'];
      result.protect = applyBranchProtection({ branches: targetBranches, repoRoot });
      return result;
    }

    case 'ci-workflow': {
      const sub = args[1] || 'install';
      if (sub === 'install') {
        const force = args.includes('--force');
        result.ciWorkflow = installCIWorkflow({ repoRoot, force });
        return result;
      }
      result.success = false;
      return result;
    }

    case 'risk-audit': {
      const files = args.slice(1).filter((a) => !a.startsWith('-'));
      result.risk = assessRisk(files, repoRoot);
      return result;
    }

    case 'pr-summary': {
      let issueId = null;
      for (let i = 1; i < args.length; i++) {
        if (args[i] === '--issue' && args[i + 1]) issueId = args[++i];
        else if (!args[i].startsWith('-') && i === 1) issueId = args[i];
      }
      result.prSummary = generatePRSummary({ issueId, repoRoot });
      return result;
    }

    case 'interop': {
      const sub = args[1] || 'export';
      let targetAgent = 'all';
      for (let i = 2; i < args.length; i++) {
        if (args[i] === '--agent' && args[i + 1]) targetAgent = args[++i];
        else if (!args[i].startsWith('-')) targetAgent = args[i];
      }
      if (sub === 'export') {
        result.interop = exportAgentRules({ agent: targetAgent, repoRoot });
        return result;
      }
      result.success = false;
      return result;
    }

    case 'contract': {
      const sub = args[1] || 'check';
      if (sub === 'check') {
        result.contract = auditContractDrift({ repoRoot });
        return result;
      }
      result.success = false;
      return result;
    }

    case 'reproduce': {
      let errorTrace = '',
        targetFile = null;
      for (let i = 1; i < args.length; i++) {
        if (args[i] === '--error' && args[i + 1]) errorTrace = args[++i];
        else if (args[i] === '--target' && args[i + 1]) targetFile = args[++i];
        else if (!args[i].startsWith('-') && !errorTrace) errorTrace = args[i];
      }
      result.reproduce = scaffoldReproductionTest({ errorTrace, targetFile, repoRoot });
      return result;
    }

    case 'stats': {
      result.stats = calculateSessionStats(repoRoot);
      return result;
    }

    case 'serve': {
      let port = 4120;
      for (let i = 1; i < args.length; i++) {
        if (args[i] === '--port' && args[i + 1]) port = Number(args[++i]) || 4120;
      }
      const workbench = createWorkbenchServer({ port, repoRoot });
      workbench.listen();
      result.workbench = { port, url: `http://localhost:${port}` };
      return result;
    }

    case 'audit': {
      const sub = args[1] || 'verify';
      if (sub === 'verify') {
        const auditRes = verifyAuditChain(repoRoot);
        result.audit = auditRes;
        result.success = auditRes.valid;
        return result;
      }
      if (sub === 'record') {
        let event = 'MANUAL_AUDIT_LOG',
          metadataStr = '{}';
        for (let i = 2; i < args.length; i++) {
          if (args[i] === '--event' && args[i + 1]) event = args[++i];
          else if (args[i] === '--metadata' && args[i + 1]) metadataStr = args[++i];
        }
        let metadata = {};
        try {
          metadata = JSON.parse(metadataStr);
        } catch {}
        result.audit = appendAuditRecord({ event, metadata, repoRoot });
        return result;
      }
      result.success = false;
      return result;
    }

    case 'proof': {
      const sub = args[1] || 'generate';
      if (sub === 'generate') {
        let targetName = 'system';
        for (let i = 2; i < args.length; i++) {
          if (args[i] === '--name' && args[i + 1]) targetName = args[++i];
          else if (!args[i].startsWith('-')) targetName = args[i];
        }
        result.proof = generateRegressionProof({
          targetName,
          apiEndpoints: [
            { method: 'GET', path: `/api/v1/${targetName}/health`, expectedStatus: 200 },
            { method: 'POST', path: `/api/v1/${targetName}/verify`, expectedStatus: 200 }
          ],
          uiRoutes: [{ path: `/${targetName}`, component: `${targetName}-dashboard` }],
          repoRoot
        });
        return result;
      }
      result.success = false;
      return result;
    }

    case 'deconstruct': {
      let epicText = '',
        epicTitle = 'Autonomous Initiative',
        autoProvision = false;
      for (let i = 1; i < args.length; i++) {
        if (args[i] === '--title' && args[i + 1]) epicTitle = args[++i];
        else if (args[i] === '--provision') autoProvision = true;
        else if (!args[i].startsWith('-') && !epicText) epicText = args[i];
      }
      result.deconstruct = deconstructEpic({ epicText, epicTitle, autoProvision, repoRoot });
      return result;
    }

    case 'watchdog': {
      let healthUrl = 'http://localhost:8080/health',
        maxErrorRate = 0.05,
        samples = 5,
        simulatedErrors = 0,
        rollbackCommand = null;
      for (let i = 2; i < args.length; i++) {
        if (args[i] === '--url' && args[i + 1]) healthUrl = args[++i];
        else if (args[i] === '--threshold' && args[i + 1]) maxErrorRate = Number(args[++i]) || 0.05;
        else if (args[i] === '--samples' && args[i + 1]) samples = Number(args[++i]) || 5;
        else if (args[i] === '--simulated-errors' && args[i + 1]) simulatedErrors = Number(args[++i]) || 0;
        else if (args[i] === '--rollback' && args[i + 1]) rollbackCommand = args[++i];
      }
      const watchRes = monitorCanaryHealthAndWatchdog({
        healthUrl,
        maxErrorRate,
        samples,
        simulatedErrors,
        rollbackCommand,
        repoRoot
      });
      result.watchdog = watchRes;
      result.success = watchRes.healthy;
      return result;
    }

    case 'help':
    default: {
      console.log(`
Agentic SDLC Universal Engine (sdlc-engine.mjs)

Usage:
  node .agent/scripts/sdlc-engine.mjs product <init|status|plan|control>
  node .agent/scripts/sdlc-engine.mjs adr <create|list|decide>
  node .agent/scripts/sdlc-engine.mjs drift check
  node .agent/scripts/sdlc-engine.mjs decision-graph <build|sync>
  node .agent/scripts/sdlc-engine.mjs dossier generate --issue <id>
  node .agent/scripts/sdlc-engine.mjs guard <pre-commit|pre-merge|pre-push|receipt>
  node .agent/scripts/sdlc-engine.mjs cache <status|clear>
  node .agent/scripts/sdlc-engine.mjs serve [--port <n>]
      `);
      return result;
    }
  }
}

if (process.argv[1]) {
  try {
    const isDirect = path.resolve(process.argv[1]) === path.resolve(import.meta.filename);
    const isRealTarget = fs.realpathSync(process.argv[1]) === path.resolve(import.meta.filename);
    if (isDirect || isRealTarget) {
      runCLI();
    }
  } catch {
    if (path.resolve(process.argv[1]) === path.resolve(import.meta.filename)) {
      runCLI();
    }
  }
}
