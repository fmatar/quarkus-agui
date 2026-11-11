#!/usr/bin/env node
/**
 * Agentic SDLC Framework: Universal Stack Discovery, Autopilot & Governance Engine
 * Modular barrel export & CLI router delegating to `.agent/scripts/lib/*.mjs`.
 */

import fs from 'node:fs';
import path from 'node:path';
import { execSync, execFileSync } from 'node:child_process';

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
  hasGitHubRemote,
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
  verifyGateReceipt,
  loadReceiptKey,
  verifyReceiptSignature
} from './lib/audit-chain.mjs';

import {
  DECISION_GRAPH_FILE,
  DECISION_MAP_MD,
  appendDecisionEvent,
  buildDecisionGraph,
  syncDecisionMapMarkdown
} from './lib/decision-graph.mjs';

import {
  renderCapabilitySpecMarkdown,
  renderProductPrdMarkdown,
  renderProductSrsMarkdown,
  initProductConstitution,
  scaffoldCapabilitySpec,
  parseProductMarkdown,
  planProductDAG,
  controlProductDAG,
  deconstructEpic
} from './lib/product-dag.mjs';

import {
  listADRs,
  createADR,
  decideADR,
  compactADRs,
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
  CANONICAL_ISSUE_TEMPLATES,
  scaffoldIssueTemplates,
  loadIssueTemplate,
  formatTemplatedIssue,
  updateLocalKanbanState,
  createTicket,
  createPMIssue,
  syncKanbanBoard,
  transitionPMState,
  postPMComment,
  resolveOpenPRNumber,
  ensurePullRequest,
  formatConsolidatedReviewComment,
  pollCIPRChecks,
  parseSemVer,
  bumpSemVer
} from './lib/pm-provider.mjs';

import {
  ARCHLENS_SKILL_PACKAGE,
  DEFAULT_GITIGNORE_ENTRIES,
  upsertGitignore,
  pinArchlensMcpConfigs,
  SUPPORTED_AGENTS,
  CANONICAL_SDLC_SKILLS,
  DEPRECATED_SDLC_SKILLS,
  pruneDeprecatedSkills,
  upsertConstitutionalRulesFile,
  upsertClaudeSettingsJson,
  adaptSkillForAgent,
  createSkillZipBuffer,
  provisionAgentIntegrations,
  installFramework,
  parseInstallArgs,
  printInstallerHelp,
  runInstallerCLI
} from './lib/installer.mjs';

import {
  getSessionPath,
  getSessionStatus,
  startSession,
  updateSession,
  receiptInputsFromSession,
  switchSession,
  listSessions,
  renderDashboard,
  resumeSession,
  releaseSession,
  getExemptionsPath,
  recordExemption,
  getExemptions,
  calculateSessionStats,
  executeCompositeStage
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
  findAffectedTests,
  findAffectededTests,
  buildSingleTestCommand,
  buildSingleTestArgv,
  parseCoverageReport,
  parseMutationReport,
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
  auditSkills
} from './lib/quality-gate.mjs';

import {
  buildArchDiffPayload,
  generateArchitectureWidget,
  ensureWorkbenchRunning,
  renderWorkbenchHTML,
  createWorkbenchServer
} from './lib/workbench-server.mjs';

import {
  inspectSourceModule,
  resolveArchlensRing,
  buildWikiCodeMap,
  auditWiki,
  renderCodebaseMapMarkdown,
  syncWiki
} from './lib/wiki-sync.mjs';

import {
  escapeXml,
  slugifyHeadingAnchor,
  detectReadmeArchetype,
  stripArchetypeBlocks,
  renderThemeWordmarkSvg,
  renderVhsDemoTape,
  renderLlmsTxt,
  scaffoldReadme,
  auditReadme
} from './lib/readme-sync.mjs';

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
  hasGitHubRemote,
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
  loadReceiptKey,
  verifyReceiptSignature,
  DECISION_GRAPH_FILE,
  DECISION_MAP_MD,
  appendDecisionEvent,
  buildDecisionGraph,
  syncDecisionMapMarkdown,
  renderCapabilitySpecMarkdown,
  renderProductPrdMarkdown,
  renderProductSrsMarkdown,
  initProductConstitution,
  scaffoldCapabilitySpec,
  parseProductMarkdown,
  planProductDAG,
  controlProductDAG,
  deconstructEpic,
  listADRs,
  createADR,
  decideADR,
  compactADRs,
  checkSpecDrift,
  auditContractDrift,
  extractExportSignatures,
  generateTicketDossier,
  verifyCommitGuard,
  verifyMergeGuard,
  verifyPushGuard,
  installHooks,
  applyBranchProtection,
  CANONICAL_ISSUE_TEMPLATES,
  scaffoldIssueTemplates,
  loadIssueTemplate,
  formatTemplatedIssue,
  updateLocalKanbanState,
  createTicket,
  createPMIssue,
  syncKanbanBoard,
  transitionPMState,
  postPMComment,
  resolveOpenPRNumber,
  ensurePullRequest,
  formatConsolidatedReviewComment,
  ARCHLENS_SKILL_PACKAGE,
  DEFAULT_GITIGNORE_ENTRIES,
  upsertGitignore,
  pinArchlensMcpConfigs,
  SUPPORTED_AGENTS,
  CANONICAL_SDLC_SKILLS,
  DEPRECATED_SDLC_SKILLS,
  pruneDeprecatedSkills,
  upsertConstitutionalRulesFile,
  upsertClaudeSettingsJson,
  adaptSkillForAgent,
  createSkillZipBuffer,
  provisionAgentIntegrations,
  installFramework,
  parseInstallArgs,
  printInstallerHelp,
  runInstallerCLI,
  pollCIPRChecks,
  parseSemVer,
  bumpSemVer,
  getSessionPath,
  getSessionStatus,
  startSession,
  updateSession,
  receiptInputsFromSession,
  switchSession,
  listSessions,
  renderDashboard,
  resumeSession,
  releaseSession,
  getExemptionsPath,
  recordExemption,
  getExemptions,
  calculateSessionStats,
  executeCompositeStage,
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
  findAffectedTests,
  findAffectededTests,
  buildSingleTestCommand,
  buildSingleTestArgv,
  parseCoverageReport,
  parseMutationReport,
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
  auditSkills,
  buildArchDiffPayload,
  generateArchitectureWidget,
  ensureWorkbenchRunning,
  renderWorkbenchHTML,
  createWorkbenchServer,
  inspectSourceModule,
  resolveArchlensRing,
  buildWikiCodeMap,
  auditWiki,
  renderCodebaseMapMarkdown,
  syncWiki,
  escapeXml,
  slugifyHeadingAnchor,
  detectReadmeArchetype,
  stripArchetypeBlocks,
  renderThemeWordmarkSvg,
  renderVhsDemoTape,
  renderLlmsTxt,
  scaffoldReadme,
  auditReadme
};

export function executeRelease({
  bump = 'patch',
  dryRun = false,
  skipGate = false,
  noCompact = false,
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
  let compactedAdrs = null;
  if (!noCompact && listADRs(repoRoot).length > 0) {
    compactedAdrs = compactADRs({ releaseVersion: releaseTag, repoRoot });
    for (const rel of ['PRODUCT.md', 'docs/SRS.md', 'docs/DECISION_MAP.md', 'docs/AUDIT_CHAIN.jsonl', '.agent/decision-graph.jsonl']) {
      if (fileExists(path.resolve(repoRoot, rel)) && !updatedFiles.includes(rel)) {
        updatedFiles.push(rel);
      }
    }
  }

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
    execFileSync('git', ['add', '--', ...updatedFiles], { cwd: repoRoot, stdio: 'pipe' });
    if (compactedAdrs && compactedAdrs.compactedCount > 0) {
      execFileSync('git', ['add', '-A', '--', 'docs/adr'], { cwd: repoRoot, stdio: 'pipe' });
    }
    execFileSync('git', ['commit', '-m', `chore(release): ${releaseTag}`], { cwd: repoRoot, stdio: 'pipe' });
    execFileSync('git', ['tag', '-a', releaseTag, '-m', `Release ${releaseTag}`], { cwd: repoRoot, stdio: 'pipe' });
  } catch {}

  return {
    success: true,
    dryRun: false,
    currentVersion,
    newVersion,
    releaseTag,
    updatedFiles,
    compactedAdrs
  };
}

// --- Measured gate helpers ---

/**
 * Rejects a report that was not (re)written by the command that just ran, so a
 * stale artifact from an earlier run can never satisfy a gate.
 */
function requireFreshReport(report, mod, repoRoot, startedAt) {
  if (!report.found) return report;
  const modDir = mod?.path && mod.path !== '.' ? path.resolve(repoRoot, mod.path) : path.resolve(repoRoot);
  try {
    const mtime = fs.statSync(path.resolve(modDir, report.file)).mtimeMs;
    if (mtime + 2000 < startedAt) {
      return { ...report, passed: false, stale: true, reason: `Report ${report.file} is stale (not regenerated by this run).` };
    }
  } catch {}
  if (!report.passed && !report.reason) {
    return { ...report, reason: `${report.formatted} < ${report.threshold}% (${report.file})` };
  }
  return report;
}

/** Runs the module's coverage command and parses a fresh report (fails closed). */
function measureCoverage(mod, repoRoot) {
  if (!mod.commands?.coverage) {
    return { found: false, passed: false, reason: 'No coverage command configured.' };
  }
  const startedAt = Date.now();
  execSync(mod.commands.coverage, { stdio: 'inherit', cwd: path.resolve(repoRoot, mod.path) });
  return requireFreshReport(parseCoverageReport(repoRoot, mod), mod, repoRoot, startedAt);
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
        const affectedTests = findAffectedTests(files, repoRoot);
        if (affectedTests.length > 0) {
          for (const testFile of affectedTests) {
            const single = buildSingleTestArgv(testFile, repoRoot);
            execFileSync(single.file, single.args, { stdio: 'inherit', cwd: repoRoot });
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
        if (hit.hit && hit.entry?.result?.coverage && !args.includes('--no-cache')) {
          console.log(`⚡ [${mod.name}] Coverage cache hit (${hit.treeHash.slice(0, 10)}): ${hit.entry.result.coverage}`);
          try {
            updateSession({ manifestUpdate: { coverageReport: hit.entry.result.coverage }, repoRoot });
          } catch {}
          continue;
        }
        const covReport = measureCoverage(mod, repoRoot);
        result.coverageReport = covReport;
        if (!covReport.passed) {
          result.success = false;
          console.error(`🛑 [${mod.name}] Coverage gate failed: ${covReport.reason}`);
          return result;
        }
        try {
          updateSession({ manifestUpdate: { coverageReport: covReport.formatted }, repoRoot });
        } catch {}
        recordGateCache({
          checkType: 'coverage',
          modulePath: mod.path,
          result: { passed: true, coverage: covReport.formatted },
          repoRoot
        });
      }
      result.activeModules = activeModules;
      return result;
    }

    case 'mutation': {
      const files = getModifiedFiles('main', repoRoot);
      const activeModules = files.length > 0 ? matchFilesToModules(files, config.modules) : config.modules;
      for (const mod of activeModules) {
        if (mod.thresholds && mod.thresholds.mutationScore === null) {
          console.log(`ℹ️  [${mod.name}] Mutation testing not configured (thresholds.mutationScore: null); receipts will record 'not-configured'.`);
          continue;
        }
        const hit = getGateCacheHit({ checkType: 'mutation', modulePath: mod.path, repoRoot });
        if (hit.hit && hit.entry?.result?.mutation && !args.includes('--no-cache')) {
          console.log(`⚡ [${mod.name}] Mutation cache hit (${hit.treeHash.slice(0, 10)}): ${hit.entry.result.mutation}`);
          try {
            updateSession({ manifestUpdate: { mutationScore: hit.entry.result.mutation }, repoRoot });
          } catch {}
          continue;
        }
        const cwd = path.resolve(repoRoot, mod.path);
        const startedAt = Date.now();
        if (!mod.commands?.mutation) {
          result.success = false;
          console.error(`🛑 [${mod.name}] Mutation gate failed: no mutation command configured.`);
          return result;
        }
        execSync(mod.commands.mutation, { stdio: 'inherit', cwd });

        const mutReport = requireFreshReport(parseMutationReport(repoRoot, mod), mod, repoRoot, startedAt);
        result.mutationReport = mutReport;
        if (!mutReport.passed) {
          result.success = false;
          console.error(
            `🛑 [${mod.name}] Mutation gate failed: ${mutReport.reason || `${mutReport.formatted} < ${mutReport.threshold}% (${mutReport.file})`}`
          );
          return result;
        }
        try {
          updateSession({ manifestUpdate: { mutationScore: mutReport.formatted }, repoRoot });
        } catch {}
        recordGateCache({
          checkType: 'mutation',
          modulePath: mod.path,
          result: { passed: true, mutation: mutReport.formatted },
          repoRoot
        });
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
        const covReport = measureCoverage(mod, repoRoot);
        result.coverageReport = covReport;
        if (!covReport.passed) {
          result.success = false;
          console.error(`🛑 [${mod.name}] Quality gate failed: ${covReport.reason}`);
          return result;
        }
        try {
          updateSession({ manifestUpdate: { coverageReport: covReport.formatted }, repoRoot });
        } catch {}
        recordGateCache({ checkType: 'gate', modulePath: mod.path, result: { passed: true, coverage: covReport.formatted }, repoRoot });
      }

      const archInfo = inspectArchlens(repoRoot);
      if (archInfo.installed || config.archlens?.enabled) {
        try {
          execSync('npx -y @fmatar/archlens-skill@1.2.0 prompt --path . 2>/dev/null || true', { cwd: repoRoot, stdio: 'inherit' });
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

        const wikiRes = auditWiki(repoRoot, { strict: true });
        result.wikiAudit = wikiRes;
        if (wikiRes.enabled && !wikiRes.passed) {
          throw new Error(`Wiki Audit failed: ${wikiRes.errorsCount} documentation drift / broken path issue(s) detected.`);
        }

        const readmeRes = auditReadme(repoRoot, { strict: true });
        result.readmeAudit = readmeRes;
        if (readmeRes.readmeExists && !readmeRes.passed) {
          throw new Error(`README Audit failed: ${readmeRes.errorsCount} 10-30-90 blueprint violation(s) detected.`);
        }
      } else {
        result.wikiAudit = auditWiki(repoRoot);
        result.readmeAudit = auditReadme(repoRoot);
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
        ensureWorkbenchRunning({ repoRoot });
        result.uiWidget = generateArchitectureWidget({ repoRoot });
        return result;
      }
      if (sub === 'control') {
        const action = args[2] || 'status';
        result.product = controlProductDAG({ action, repoRoot });
        return result;
      }
      if (sub === 'spec') {
        let capId = null,
          title = 'New Capability',
          problem = '',
          inputs = '',
          processing = '',
          outputs = '',
          dependsOn = [];
        const force = args.includes('--force');
        for (let i = 2; i < args.length; i++) {
          if ((args[i] === '--cap' || args[i] === '--id') && args[i + 1]) capId = args[++i];
          else if (args[i] === '--title' && args[i + 1]) title = args[++i];
          else if (args[i] === '--problem' && args[i + 1]) problem = args[++i];
          else if (args[i] === '--inputs' && args[i + 1]) inputs = args[++i];
          else if (args[i] === '--processing' && args[i + 1]) processing = args[++i];
          else if (args[i] === '--outputs' && args[i + 1]) outputs = args[++i];
          else if ((args[i] === '--depends-on' || args[i] === '--depends') && args[i + 1]) {
            dependsOn = args[++i].split(',').map((s) => s.trim()).filter(Boolean);
          }
        }
        result.spec = scaffoldCapabilitySpec({ capId, title, problem, inputs, processing, outputs, dependsOn, force, repoRoot });
        return result;
      }
      result.product = parseProductMarkdown(repoRoot);
      return result;
    }

    case 'spec': {
      let capId = null,
        title = 'New Capability',
        problem = '',
        inputs = '',
        processing = '',
        outputs = '',
        dependsOn = [];
      const force = args.includes('--force');
      for (let i = 1; i < args.length; i++) {
        if ((args[i] === '--cap' || args[i] === '--id') && args[i + 1]) capId = args[++i];
        else if (args[i] === '--title' && args[i + 1]) title = args[++i];
        else if (args[i] === '--problem' && args[i + 1]) problem = args[++i];
        else if (args[i] === '--inputs' && args[i + 1]) inputs = args[++i];
        else if (args[i] === '--processing' && args[i + 1]) processing = args[++i];
        else if (args[i] === '--outputs' && args[i + 1]) outputs = args[++i];
        else if ((args[i] === '--depends-on' || args[i] === '--depends') && args[i + 1]) {
          dependsOn = args[++i].split(',').map((s) => s.trim()).filter(Boolean);
        }
      }
      result.spec = scaffoldCapabilitySpec({ capId, title, problem, inputs, processing, outputs, dependsOn, force, repoRoot });
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
        ensureWorkbenchRunning({ repoRoot });
        result.uiWidget = generateArchitectureWidget({ repoRoot });
        return result;
      }
      if (sub === 'decide') {
        let adrId = args[2] && !args[2].startsWith('-') ? args[2] : null;
        let action = args[3] && !args[3].startsWith('-') ? args[3] : 'approve';
        let reviewer = '@human-operator';
        let reason = 'Reviewed via SDLC Governance Gate';
        for (let i = 2; i < args.length; i++) {
          if (args[i] === '--id' && args[i + 1]) adrId = args[++i];
          else if ((args[i] === '--status' || args[i] === '--action') && args[i + 1]) action = args[++i];
          else if ((args[i] === '--by' || args[i] === '--reviewer') && args[i + 1]) reviewer = args[++i];
          else if (args[i] === '--reason' && args[i + 1]) reason = args[++i];
        }
        result.adr = decideADR({ adrId, action, reviewer, reason, repoRoot });
        return result;
      }
      if (sub === 'compact') {
        let releaseVersion = 'baseline';
        const dryRun = args.includes('--dry-run');
        for (let i = 2; i < args.length; i++) {
          if ((args[i] === '--release' || args[i] === '--version') && args[i + 1]) {
            releaseVersion = args[++i];
          } else if (args[i].startsWith('--release=')) {
            releaseVersion = args[i].split('=')[1];
          }
        }
        result.compact = compactADRs({ releaseVersion, dryRun, repoRoot });
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
        // Inside a pre-merge-commit hook MERGE_HEAD is the commit being merged in;
        // bind the receipt to that exact SHA rather than "any receipt".
        let sourceSha = null;
        try {
          sourceSha = execFileSync('git', ['rev-parse', '--verify', '-q', 'MERGE_HEAD'], {
            cwd: repoRoot,
            encoding: 'utf8',
            stdio: 'pipe'
          }).trim() || null;
        } catch {}
        result.guard = verifyMergeGuard({ repoRoot, sourceSha });
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
        if (!sess.session) {
          console.error('🛑 No active SDLC session; cannot issue a gate receipt.');
          result.success = false;
          return result;
        }
        let cliCoverage = undefined;
        let cliMutation = undefined;
        const docsOnly = args.includes('--docs-only') ? true : undefined;
        let cliReviewer = undefined;
        for (let i = 2; i < args.length; i++) {
          if ((args[i] === '--coverage' || args[i] === '--coverage-pct') && args[i + 1]) {
            const v = args[++i];
            cliCoverage = v === 'null' ? null : Number.parseFloat(String(v).replace('%', ''));
          } else if ((args[i] === '--mutation' || args[i] === '--mutation-pct') && args[i + 1]) {
            const v = args[++i];
            cliMutation = v === 'null' ? null : Number.parseFloat(String(v).replace('%', ''));
          } else if (args[i] === '--reviewer' && args[i + 1]) {
            cliReviewer = args[++i];
          }
        }
        const drift = checkSpecDrift({ ticketId: sess.session.issueId, repoRoot });
        const sessionInputs = receiptInputsFromSession(sess.session, { driftCheckPassed: Boolean(drift.passed), repoRoot });
        result.receipt = issueGateReceipt({
          issueId: sess.session.issueId,
          branch: sess.session.branch,
          ...sessionInputs,
          ...(cliCoverage !== undefined ? { coveragePct: cliCoverage } : {}),
          ...(cliMutation !== undefined ? { mutationPct: cliMutation } : {}),
          ...(docsOnly ? { docsOnly: true, coveragePct: null, mutationPct: null } : {}),
          ...(cliReviewer ? { reviewer: cliReviewer } : {}),
          repoRoot
        });
        result.success = result.receipt.success;
        if (!result.success) console.error(`🛑 ${result.receipt.reason}`);
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

    case 'stage': {
      const action = args[1] || 'begin';
      let issueId = null,
        title = null,
        type = 'feat',
        branch = null,
        worktree = null,
        prNumber = null,
        summary = null,
        checkoutBranch = false;
      for (let i = 2; i < args.length; i++) {
        if (args[i] === '--issue' && args[i + 1]) issueId = args[++i];
        else if (args[i].startsWith('--issue=')) issueId = args[i].split('=')[1];
        else if (args[i] === '--title' && args[i + 1]) title = args[++i];
        else if (args[i].startsWith('--title=')) title = args[i].split('=')[1];
        else if (args[i] === '--type' && args[i + 1]) type = args[++i];
        else if (args[i].startsWith('--type=')) type = args[i].split('=')[1];
        else if (args[i] === '--branch' && args[i + 1]) branch = args[++i];
        else if (args[i] === '--worktree' && args[i + 1]) worktree = args[++i];
        else if (args[i] === '--pr' && args[i + 1]) prNumber = args[++i];
        else if (args[i].startsWith('--pr=')) prNumber = args[i].split('=')[1];
        else if (args[i] === '--summary' && args[i + 1]) summary = args[++i];
        else if (args[i] === '--checkout') checkoutBranch = true;
      }
      const stageRes = executeCompositeStage({
        action,
        issueId,
        title,
        type,
        branch,
        worktree,
        prNumber,
        summary,
        checkoutBranch,
        repoRoot
      });
      result.stage = stageRes;
      result.success = stageRes.success;
      if (stageRes.action === 'pre-pr') {
        if (stageRes.receipt) {
          console.log(
            `🧾 Signed gate receipt ${stageRes.receipt.receiptId}: coverage ${stageRes.receipt.coveragePct}%, mutation ${stageRes.receipt.mutationPct ?? stageRes.receipt.mutationStatus}, reviewer ${stageRes.receipt.reviewer || 'n/a'}`
          );
        } else if (!stageRes.success) {
          console.error(`🛑 stage pre-pr: no gate receipt issued. ${stageRes.receiptError || ''}`);
        } else {
          console.log(
            `ℹ️ stage pre-pr: Stage 6 verified${stageRes.prNumber ? ` (PR #${stageRes.prNumber})` : ''}. Run Stage 7 ('review record') next to publish the PR review comment and sign the Stage 7 receipt.`
          );
        }
      }
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
          execSync('npx -y @fmatar/archlens-skill@1.2.0 prompt --path . 2>/dev/null || true', { cwd: repoRoot, stdio: 'inherit' });
          result.passed = true;
        } catch (err) {
          result.passed = false;
          result.error = err.message;
        }
        return result;
      }
      if (sub === 'update') {
        try {
          execSync('npx -y @fmatar/archlens-skill@1.2.0 update --path .', { cwd: repoRoot, stdio: 'inherit' });
          result.passed = true;
        } catch {
          result.passed = false;
        }
        return result;
      }
      if (sub === 'diff') {
        const diffRes = checkArchDiff(repoRoot);
        return { ...result, ...diffRes, success: !(diffRes.enabled && diffRes.passed === false) };
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
      result.success = result.security.passed !== false;
      if (!result.success) console.error('🛑 Security audit failed.');
      return result;
    }

    case 'skill-audit': {
      const live = args.includes('--live');
      result.skillAudit = auditSkills(repoRoot, { live });
      result.success = result.skillAudit.success;
      return result;
    }

    case 'wiki':
    case 'docs': {
      const sub = (args[1] || 'sync').toLowerCase();
      result.subcommand = sub;
      if (sub === 'map') {
        result.codeMap = buildWikiCodeMap(repoRoot);
        return result;
      }
      if (sub === 'audit' || sub === 'check' || sub === 'lint') {
        const strict = args.includes('--strict');
        const wikiAudit = auditWiki(repoRoot, { strict });
        result.wikiAudit = wikiAudit;
        result.success = wikiAudit.passed;
        console.log(wikiAudit.summary);
        for (const f of wikiAudit.findings) {
          console.log(`  [${f.severity}] ${f.rule} (${f.file}:${f.line}): ${f.message}`);
        }
        return result;
      }
      if (sub === 'sync' || sub === 'generate' || sub === 'update') {
        const generateMissing =
          args.includes('--init') ||
          args.includes('--scaffold') ||
          args.includes('--generate-missing') ||
          !fs.existsSync(path.resolve(repoRoot, 'wiki/Home.md'));
        const wikiSync = syncWiki({ repoRoot, generateMissing });
        result.wikiSync = wikiSync;
        result.wikiAudit = wikiSync.audit;
        result.success = wikiSync.success;
        console.log(`📚 Synchronized Wiki Codebase Map -> ${wikiSync.codeMapPath} (${wikiSync.modulesCount} modules across ${wikiSync.ringsCount} Archlens rings)`);
        console.log(wikiSync.audit.summary);
        for (const f of wikiSync.audit.findings) {
          console.log(`  [${f.severity}] ${f.rule} (${f.file}:${f.line}): ${f.message}`);
        }
        return result;
      }
      result.success = false;
      return result;
    }

    case 'readme': {
      const sub = (args[1] || 'audit').toLowerCase();
      result.subcommand = sub;
      if (sub === 'init' || sub === 'scaffold' || sub === 'generate') {
        let archetype = 'auto';
        let title = null;
        let hook = null;
        const force = args.includes('--force') || args.includes('-f');
        const withAssets = !args.includes('--no-assets');
        for (let i = 2; i < args.length; i++) {
          if (args[i] === '--archetype' && args[i + 1]) archetype = args[++i];
          else if (args[i] === '--title' && args[i + 1]) title = args[++i];
          else if (args[i] === '--hook' && args[i + 1]) hook = args[++i];
        }
        result.readme = scaffoldReadme({ repoRoot, archetype, title, hook, force, withAssets });
        result.readmeAudit = auditReadme(repoRoot, { strict: true });
        result.success = result.readmeAudit.passed;
        return result;
      }
      if (sub === 'audit' || sub === 'check' || sub === 'lint') {
        const strict = args.includes('--strict');
        const readmeAudit = auditReadme(repoRoot, { strict });
        result.readmeAudit = readmeAudit;
        result.success = readmeAudit.passed;
        for (const f of readmeAudit.findings) {
          console.log(`  [${f.severity}] ${f.rule} (${f.file}:${f.line}): ${f.message}`);
        }
        return result;
      }
      result.success = false;
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
      if (sub === 'sync') {
        result.synced = syncKanbanBoard(repoRoot);
        return result;
      }
      if (sub === 'templates' || sub === 'init-templates') {
        const force = args.includes('--force');
        result.templates = scaffoldIssueTemplates({ repoRoot, force });
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
        let issueId = args[2] && !args[2].startsWith('-') ? args[2] : null;
        let commentText = '',
          prNumber = null;
        const allowLocalFallback = args.includes('--allow-local-fallback');
        const startIdx = issueId ? 3 : 2;
        for (let i = startIdx; i < args.length; i++) {
          if (args[i] === '--pr' && args[i + 1]) prNumber = args[++i];
          else if (args[i].startsWith('--pr=')) prNumber = args[i].split('=')[1];
          else if (args[i] === '--issue' && args[i + 1]) issueId = args[++i];
          else if (args[i].startsWith('--issue=')) issueId = args[i].split('=')[1];
          else if (args[i] === '--body' && args[i + 1]) commentText = args[++i];
        }
        if (!commentText) commentText = args.slice(startIdx).filter((a) => !a.startsWith('--')).join(' ');
        const sess = getSessionStatus(repoRoot);
        const resolvedIssue = issueId || sess.session?.issueId || prNumber;
        if (!resolvedIssue || !commentText) {
          result.success = false;
          return result;
        }
        result.commented = postPMComment({
          issueId: resolvedIssue,
          prNumber,
          comment: commentText,
          allowLocalFallback,
          repoRoot
        });
        result.success = result.commented.success !== false;
        return result;
      }
      result.success = false;
      return result;
    }

    case 'ticket': {
      const knownSubs = ['create', 'transition', 'comment', 'sync', 'templates', 'init-templates'];
      const rawSub = args[1] || 'create';
      const sub = knownSubs.includes(rawSub) ? rawSub : 'create';
      const startIndex = knownSubs.includes(rawSub) ? 2 : 1;
      result.subcommand = sub;

      if (sub === 'templates' || sub === 'init-templates') {
        const force = args.includes('--force');
        result.templates = scaffoldIssueTemplates({ repoRoot, force });
        return result;
      }

      if (sub === 'sync') {
        result.synced = syncKanbanBoard(repoRoot);
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
        let issueId = args[2] && !args[2].startsWith('-') ? args[2] : null;
        let commentText = '',
          prNumber = null;
        const allowLocalFallback = args.includes('--allow-local-fallback');
        const startIdx = issueId ? 3 : 2;
        for (let i = startIdx; i < args.length; i++) {
          if (args[i] === '--pr' && args[i + 1]) prNumber = args[++i];
          else if (args[i].startsWith('--pr=')) prNumber = args[i].split('=')[1];
          else if (args[i] === '--issue' && args[i + 1]) issueId = args[++i];
          else if (args[i].startsWith('--issue=')) issueId = args[i].split('=')[1];
          else if (args[i] === '--body' && args[i + 1]) commentText = args[++i];
        }
        if (!commentText) commentText = args.slice(startIdx).filter((a) => !a.startsWith('--')).join(' ');
        const sess = getSessionStatus(repoRoot);
        const resolvedIssue = issueId || sess.session?.issueId || prNumber;
        if (!resolvedIssue || !commentText) {
          result.success = false;
          return result;
        }
        result.commented = postPMComment({
          issueId: resolvedIssue,
          prNumber,
          comment: commentText,
          allowLocalFallback,
          repoRoot
        });
        result.success = result.commented.success !== false;
        return result;
      }

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
        for (let i = startIndex; i < args.length; i++) {
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
        if (!title) title = args.slice(startIndex).filter((a) => !a.startsWith('--')).join(' ');
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
      const parsed = parseInstallArgs(args, repoRoot);
      if (parsed.help) {
        printInstallerHelp();
        result.help = true;
        return result;
      }
      const onDetect = (dir) => {
        const cfg = generateConfig(dir);
        const cfgFile = path.join(dir, '.agent', 'sdlc-config.json');
        fs.mkdirSync(path.dirname(cfgFile), { recursive: true });
        fs.writeFileSync(cfgFile, JSON.stringify(cfg, null, 2));
      };
      const onInstallCi = ({ repoRoot: dir }) => installCIWorkflow({ repoRoot: dir, force: true });
      result.installed = installFramework({
        ...parsed,
        onDetect,
        onInstallCi
      });
      return result;
    }

    case 'update': {
      const archInfo = inspectArchlens(repoRoot);
      if (archInfo.installed) {
        try {
          execSync('npx -y @fmatar/archlens-skill@1.2.0 update --path .', { cwd: repoRoot, stdio: 'inherit' });
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
      if (sub === 'record') {
        // Records a reviewer verdict in the session manifest, signs the Stage 7 receipt
        // when measurements are ready, formats the 4-section consolidated review comment,
        // and publishes it directly to the GitHub PR (unless --no-comment is passed).
        let verdict = null,
          reviewer = null,
          bodyText = '',
          peerReviewSummary = '',
          archSecuritySummary = '',
          prNumber = null,
          issueId = null;
        const noComment = args.includes('--no-comment');
        const allowLocalFallback = args.includes('--allow-local-fallback');
        for (let i = 2; i < args.length; i++) {
          if (args[i] === '--verdict' && args[i + 1]) verdict = args[++i];
          else if (args[i].startsWith('--verdict=')) verdict = args[i].split('=')[1];
          else if (args[i] === '--reviewer' && args[i + 1]) reviewer = args[++i];
          else if (args[i].startsWith('--reviewer=')) reviewer = args[i].split('=')[1];
          else if (args[i] === '--body' && args[i + 1]) bodyText = args[++i];
          else if (args[i] === '--peer-review' && args[i + 1]) peerReviewSummary = args[++i];
          else if (args[i] === '--arch-review' && args[i + 1]) archSecuritySummary = args[++i];
          else if (args[i] === '--pr' && args[i + 1]) prNumber = args[++i];
          else if (args[i].startsWith('--pr=')) prNumber = args[i].split('=')[1];
          else if (args[i] === '--issue' && args[i + 1]) issueId = args[++i];
          else if (args[i].startsWith('--issue=')) issueId = args[i].split('=')[1];
        }
        if (!reviewer && (bodyText || peerReviewSummary || archSecuritySummary)) {
          reviewer = '@sdlc-gate';
        }
        if (!['approve', 'request-changes'].includes(verdict) || !reviewer) {
          console.error(
            'Usage: review record --verdict <approve|request-changes> --reviewer <name> [--peer-review <md>] [--arch-review <md>] [--body <md>] [--pr <number>]'
          );
          result.success = false;
          return result;
        }
        const sessRes = updateSession({
          stage: 7,
          stageName: 'Subagent Review & Quality Gate',
          manifestUpdate: {
            ...(prNumber ? { prNumber: Number(String(prNumber).replace(/^#/, '')) || prNumber } : {}),
            peerReview: {
              verdict,
              reviewer,
              peerReviewSummary: peerReviewSummary || null,
              archSecuritySummary: archSecuritySummary || null,
              body: bodyText || null,
              recordedAt: new Date().toISOString()
            }
          },
          repoRoot
        });

        let issuedReceipt = null;
        if (verdict === 'approve' && sessRes?.session) {
          try {
            const drift = checkSpecDrift({ ticketId: sessRes.session.issueId, repoRoot });
            const sessionInputs = receiptInputsFromSession(sessRes.session, {
              driftCheckPassed: Boolean(drift.passed),
              repoRoot
            });
            const recRes = issueGateReceipt({
              issueId: sessRes.session.issueId,
              branch: sessRes.session.branch,
              ...sessionInputs,
              reviewer,
              repoRoot
            });
            if (recRes.success) {
              issuedReceipt = recRes.receipt;
            }
          } catch {}
        }

        const formattedComment = formatConsolidatedReviewComment({
          issueId: issueId || sessRes?.session?.issueId || null,
          prNumber: prNumber || sessRes?.session?.manifest?.prNumber || null,
          branch: sessRes?.session?.branch || null,
          verdict,
          reviewer,
          peerReviewSummary,
          archSecuritySummary,
          body: bodyText,
          receiptId: issuedReceipt?.receiptId || null,
          repoRoot
        });

        let commented = null;
        if (!noComment) {
          commented = postPMComment({
            issueId: issueId || sessRes?.session?.issueId || null,
            prNumber: prNumber || sessRes?.session?.manifest?.prNumber || null,
            comment: formattedComment,
            allowLocalFallback,
            repoRoot
          });
          if (commented && commented.success === false) {
            result.success = false;
          }
        }

        result.review = {
          verdict,
          reviewer,
          receipt: issuedReceipt,
          formattedComment,
          commented
        };
        return result;
      }
      result.success = false;
      return result;
    }

    case 'release': {
      let bump = 'patch',
        dryRun = false,
        skipGate = false,
        noCompact = false;
      for (let i = 1; i < args.length; i++) {
        if (args[i] === '--dry-run') dryRun = true;
        else if (args[i] === '--skip-gate') skipGate = true;
        else if (args[i] === '--no-compact') noCompact = true;
        else if (['patch', 'minor', 'major'].includes(args[i].toLowerCase())) bump = args[i].toLowerCase();
      }
      result.release = executeRelease({ bump, dryRun, skipGate, noCompact, repoRoot });
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

    case 'ui-snapshot': {
      let outputPath = null;
      for (let i = 1; i < args.length; i++) {
        if ((args[i] === '--output' || args[i] === '-o') && args[i + 1]) outputPath = args[++i];
        else if (args[i].startsWith('--output=')) outputPath = args[i].split('=')[1];
      }
      const wb = ensureWorkbenchRunning({ repoRoot });
      const widget = generateArchitectureWidget({ outputPath, repoRoot });
      result.workbench = wb;
      result.uiWidget = widget;
      return result;
    }

    case 'serve': {
      let port = 4120;
      for (let i = 1; i < args.length; i++) {
        if (args[i] === '--port' && args[i + 1]) port = Number(args[++i]) || 4120;
      }
      const widget = generateArchitectureWidget({ repoRoot });
      result.uiWidget = widget;
      if (args.includes('--snapshot-only') || process.env.COWORK || process.env.CLAUDE_COWORK) {
        result.workbench = {
          port,
          snapshotOnly: true,
          widgetPath: widget.widgetPath || widget.outputPath
        };
        return result;
      }
      const workbench = createWorkbenchServer({ port, repoRoot });
      workbench.listen();
      result.workbench = {
        port,
        url: `http://127.0.0.1:${port}/?token=${workbench.token}`,
        widgetPath: widget.widgetPath || widget.outputPath
      };
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
        probeLive = args.includes('--live'),
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
        probeLive,
        rollbackCommand,
        repoRoot
      });
      result.watchdog = watchRes;
      result.success = watchRes.healthy;
      return result;
    }

    case 'help':
    case '--help':
    case '-h':
    default: {
      const isHelp = ['help', '--help', '-h'].includes(command);
      if (!isHelp) {
        console.error(`❌ Unknown command '${command}'. Run 'sdlc-engine.mjs help' for usage.`);
        result.success = false;
        result.exitCode = 2;
      }
      (isHelp ? console.log : console.error)(`
Agentic SDLC Universal Engine (sdlc-engine.mjs)

Usage:
  npx github:fmatar/agentic-sdlc-framework install [--agents <list>] [--global]
  node .agent/scripts/sdlc-engine.mjs install [--agents gemini,claude,grok,cursor,windsurf,copilot]
  node .agent/scripts/sdlc-engine.mjs stage <begin|pre-pr|complete> [--issue <id>]
  node .agent/scripts/sdlc-engine.mjs product <init|status|plan|control>
  node .agent/scripts/sdlc-engine.mjs adr <create|list|decide>
  node .agent/scripts/sdlc-engine.mjs drift check
  node .agent/scripts/sdlc-engine.mjs decision-graph <build|sync>
  node .agent/scripts/sdlc-engine.mjs readme <init|audit> [--archetype <auto|cli|sdk|platform>] [--strict]
  node .agent/scripts/sdlc-engine.mjs wiki <sync|audit|map> [--strict]
  node .agent/scripts/sdlc-engine.mjs dossier generate --issue <id>
  node .agent/scripts/sdlc-engine.mjs guard <pre-commit|pre-merge|pre-push|receipt>
  node .agent/scripts/sdlc-engine.mjs cache <status|clear>
  node .agent/scripts/sdlc-engine.mjs serve [--port <n>]
      `);
      return result;
    }
  }
}

/**
 * Formats a `runCLI()` result into deterministic stdout text so CLI subcommands
 * never finish silently when invoked from a terminal or agent device shell.
 */
export function formatCLIResult(res, args = []) {
  if (!res || typeof res !== 'object') return '';
  if (args.includes('--json')) {
    return JSON.stringify(res, null, 2);
  }

  const cmd = res.command;
  if (!cmd || ['detect', 'doctor', 'help', '--help', '-h'].includes(cmd)) {
    return '';
  }

  if (cmd === 'session') {
    const sub = res.subcommand || 'status';
    if (sub === 'dashboard') {
      if (res.dashboard) return res.dashboard;
      return 'ℹ️ No active SDLC session. Start one with `node .agent/scripts/sdlc-engine.mjs stage begin --title "<title>" --checkout`.';
    }
    if (sub === 'status') {
      const s = res.status?.session;
      if (!res.status?.active || !s) {
        return 'ℹ️ SDLC Session Status: IDLE (no active session).';
      }
      return `📍 Active SDLC Session #${s.issueId} | Branch: ${s.branch} | Stage: ${s.stage}/8 (${s.stageName}) | Remediations: ${s.remediationCount || 0}/${s.maxRemediations || 3}`;
    }
    if (sub === 'list') {
      const ids = Object.keys(res.sessions || {});
      return `📋 Sessions (${ids.length}): ${ids.length > 0 ? ids.map((id) => `#${id}`).join(', ') : 'none'} (active: ${res.activeSessionId ? `#${res.activeSessionId}` : 'none'})`;
    }
    if (res.session) {
      return `✅ Session #${res.session.issueId} -> Stage ${res.session.stage}/8 (${res.session.stageName}) on ${res.session.branch}`;
    }
    if (res.released) {
      return `🔓 Released active SDLC session lock.`;
    }
  }

  if (cmd === 'stage' && res.stage) {
    const st = res.stage;
    if (st.action === 'begin') {
      return `🚀 Stage Begin (#${st.issueId}): branch '${st.branch}' | Stage ${st.stage}/8 | UI Snapshot: ${st.widgetPath || '.agent/workbench/index.html'}`;
    }
    if (st.action === 'pre-pr') {
      const driftStatus = st.drift?.passed ? 'PASS' : 'FAIL';
      const wikiStatus = st.wikiAudit?.passed ? 'PASS' : 'FAIL';
      const prLabel = st.prNumber ? ` | PR=#${st.prNumber}` : '';
      return `📦 Stage Pre-PR (#${st.issueId})${prLabel}: Drift=${driftStatus} | Wiki=${wikiStatus} | Receipt=${st.receipt?.receiptId || 'PENDING_REVIEW'} | UI Snapshot: ${st.widgetPath || '.agent/workbench/index.html'}`;
    }
    if (st.action === 'complete') {
      return `🏁 Stage Complete (#${st.issueId}): Stage ${st.stage}/8 | Kanban synchronized | Session released: ${Boolean(st.released)}`;
    }
  }

  if (cmd === 'drift' && res.drift) {
    const d = res.drift;
    if (d.passed) {
      return `✅ Spec-Drift Check Passed (${(d.modifiedSpecs || []).length} spec/contract touchpoint(s) verified).`;
    }
    return `🛑 Spec-Drift Check Failed: ${d.reason || 'Undeclared spec or contract drift requires an accepted ADR.'}`;
  }

  if (cmd === 'adr') {
    if (res.compact) {
      return `📦 Compacted ${res.compact.compactedCount} ADR(s) into ${res.compact.srsPath || 'docs/SRS.md'} (release: ${res.compact.releaseVersion})`;
    }
    if (Array.isArray(res.adrs)) {
      if (res.adrs.length === 0) return '📜 ADRs: none recorded in docs/adr/.';
      return ['📜 Architecture Decision Records:', ...res.adrs.map((a) => `  • ${a.id}: ${a.title} [${a.scope} | ${a.status}] (${a.file})`)].join('\n');
    }
    if (res.adr) {
      return `📜 ADR ${res.adr.id || res.adr.adrId || ''}: ${res.adr.title || res.adr.status || 'updated'} (${res.adr.file || 'docs/adr'})`;
    }
  }

  if (cmd === 'decision-graph' && res.decisionGraph) {
    const dg = res.decisionGraph;
    return `🗺️ Decision Graph (${res.subcommand}): ${dg.nodes?.length ?? dg.nodesCount ?? 0} node(s), ${dg.edges?.length ?? dg.edgesCount ?? 0} edge(s)${dg.markdownPath ? ` -> ${dg.markdownPath}` : ''}`;
  }

  if (cmd === 'ui-snapshot' && res.uiWidget) {
    const p = res.uiWidget.widgetPath || res.uiWidget.outputPath || '.agent/workbench/index.html';
    return `📸 Architecture & Decision Workbench snapshot generated: ${p}`;
  }

  if (cmd === 'serve' && res.workbench?.snapshotOnly) {
    return `📸 Architecture & Decision Workbench snapshot generated: ${res.workbench.widgetPath || '.agent/workbench/index.html'} (loopback server skipped in Cowork/snapshot mode)`;
  }

  if (cmd === 'skill-audit' && res.skillAudit) {
    const sa = res.skillAudit;
    return `🛡️ Skill Audit: ${sa.skillsAudited || sa.results?.length || 0} skill(s) checked, ${sa.totalFindings || 0} finding(s) (${sa.success ? 'PASSED' : 'FAILED'})`;
  }

  if (cmd === 'audit' && res.audit) {
    if (res.audit.valid !== undefined) {
      return `${res.audit.valid ? '✅' : '🛑'} Merkle Audit Chain: ${res.audit.valid ? 'VALID' : 'INVALID'} (${res.audit.entriesCount ?? 0} record(s))`;
    }
    return `📝 Audit record appended: ${res.audit.event || 'OK'} (${res.audit.hash || ''})`;
  }

  if (cmd === 'stats' && res.stats) {
    const st = res.stats;
    return `📊 SDLC Telemetry: Stage=${st.currentStage} | Branch=${st.activeBranch} | Sessions=${st.completedSessions}/${st.totalSessions} | Velocity=${st.velocityScore}% | Saved=${st.estimatedHoursSaved}h ($${st.totalDollarSavingsUSD})`;
  }

  if (cmd === 'cache' && res.cache) {
    if (res.cache.cleared !== undefined) return `🧹 Gate cache cleared.`;
    return `⚡ Gate Cache: ${res.cache.entriesCount ?? Object.keys(res.cache.entries || {}).length} cached module entry(s).`;
  }

  if ((cmd === 'ticket' || cmd === 'pm')) {
    if (res.issueId) return `🎫 Ticket #${res.issueId} created via ${res.provider || 'local'} (${res.title || ''})`;
    if (res.transitioned) return `🔄 Ticket #${res.transitioned.issueId || ''} transitioned to '${res.transitioned.state || ''}'`;
    if (res.commented) {
      const targetStr = res.commented.prNumber
        ? `PR #${res.commented.prNumber} (ticket #${res.commented.targetId || ''})`
        : `ticket #${res.commented.targetId || res.commented.issueId || ''}`;
      return `💬 Comment recorded on ${targetStr} via ${res.commented.provider || 'local'}`;
    }
    if (res.synced) return `📋 Synchronized living Kanban board -> docs/KANBAN.md`;
    if (res.templates) return `📄 Issue & PR templates ready (${(res.templates.created || []).length} created).`;
    if (res.pm) return `🔌 PM Provider: ${res.pm.provider} (${res.pm.key || 'local'})`;
  }

  if (cmd === 'product' || cmd === 'spec') {
    if (res.spec) return `📐 Capability Spec scaffolded: ${res.spec.capId || ''} -> ${res.spec.specPath || 'docs/specs/'}`;
    if (res.product) {
      const p = res.product;
      if (p.waves) return `🌊 Product DAG Plan: ${p.waves.length} wave(s) across ${(p.capabilities || []).length} capability(s).`;
      if (p.capabilities) return `🏛️ Product Constitution: ${(p.capabilities || []).length} capability(s), ${(p.invariants || []).length} invariant(s).`;
    }
  }

  if (cmd === 'pr-summary' && res.prSummary) {
    return typeof res.prSummary === 'string' ? res.prSummary : res.prSummary.markdown || JSON.stringify(res.prSummary, null, 2);
  }

  if (cmd === 'dossier' && res.dossier) {
    return `📦 Context Dossier (#${res.dossier.issueId}): ${res.dossier.outputPath || 'generated'}`;
  }

  if (cmd === 'review') {
    if (res.review) {
      const prPart = res.review.commented?.prNumber ? ` -> Published to PR #${res.review.commented.prNumber}` : '';
      const recPart = res.review.receipt?.receiptId ? ` | Receipt: ${res.review.receipt.receiptId}` : '';
      return `✅ Recorded peer review verdict '${res.review.verdict}' by ${res.review.reviewer}${recPart}${prPart}`;
    }
    if (res.reviewPlan) return `🔍 Review Plan generated (${(res.reviewPlan.files || []).length} modified file(s)).`;
  }

  if (cmd === 'interop' && res.interop) {
    return `✅ Exported rules & skills for [${(res.interop.selectedAgents || []).join(', ')}] (${(res.interop.filesCreated || []).length} file(s) updated).`;
  }

  if (cmd === 'readme') {
    if (res.readme) {
      const assets = (res.readme.createdAssets || []).length;
      return res.readme.written
        ? `📄 Scaffolded 10-30-90 README.md (archetype: ${res.readme.archetype}, ${assets} companion asset(s) written).`
        : `ℹ️ ${res.readme.reason || 'README.md unchanged.'}`;
    }
    if (res.readmeAudit) {
      return res.readmeAudit.summary;
    }
  }

  return '';
}

function invokeMainCLI() {
  const cliArgs = process.argv.slice(2);
  const cmd = (cliArgs[0] || '').toLowerCase();
  if (cmd === 'install' || cmd === 'init') {
    const repoRoot = process.cwd();
    const onDetect = (dir) => {
      const cfg = generateConfig(dir);
      const cfgFile = path.join(dir, '.agent', 'sdlc-config.json');
      fs.mkdirSync(path.dirname(cfgFile), { recursive: true });
      fs.writeFileSync(cfgFile, JSON.stringify(cfg, null, 2));
    };
    const onInstallCi = ({ repoRoot: dir }) => installCIWorkflow({ repoRoot: dir, force: true });
    const installRes = runInstallerCLI(cliArgs, { repoRoot, onDetect, onInstallCi });
    if (installRes && installRes.success === false) process.exitCode = 1;
    return;
  }
  let res;
  try {
    res = runCLI();
  } catch (err) {
    console.error(`❌ ${err?.message || err}`);
    process.exitCode = 1;
    return;
  }
  const formatted = formatCLIResult(res, cliArgs);
  if (formatted) {
    console.log(formatted);
  }
  process.exitCode = cliExitCode(res);
}

/**
 * Maps a runCLI() result onto a process exit code.
 * Any command reporting `success: false` exits non-zero so git hooks,
 * CI and agents can rely on the exit status.
 */
export function cliExitCode(res) {
  if (!res || typeof res !== 'object') return 0;
  if (Number.isInteger(res.exitCode)) return res.exitCode;
  return res.success === false ? 1 : 0;
}

if (process.argv[1]) {
  try {
    const isDirect = path.resolve(process.argv[1]) === path.resolve(import.meta.filename);
    const isRealTarget = fs.realpathSync(process.argv[1]) === path.resolve(import.meta.filename);
    if (isDirect || isRealTarget) {
      invokeMainCLI();
    }
  } catch {
    if (path.resolve(process.argv[1]) === path.resolve(import.meta.filename)) {
      invokeMainCLI();
    }
  }
}
