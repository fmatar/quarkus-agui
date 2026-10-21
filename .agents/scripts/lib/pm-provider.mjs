import fs from 'node:fs';
import path from 'node:path';
import { execSync } from 'node:child_process';
import { fileExists, readFileSafe, parseFrontmatter, detectPMProvider } from './core.mjs';

export function loadIssueTemplate({ type = 'feat', repoRoot = '.' } = {}) {
  const normType = (type || 'feat').toLowerCase();
  const templateDir = path.resolve(repoRoot, '.github/ISSUE_TEMPLATE');

  if (fs.existsSync(templateDir)) {
    try {
      const files = fs.readdirSync(templateDir);
      let matchFile = null;
      if (normType === 'bug' || normType === 'fix' || normType === 'hotfix') {
        matchFile = files.find((f) => /bug|fix/i.test(f) && (f.endsWith('.md') || f.endsWith('.yml') || f.endsWith('.yaml')));
      } else {
        matchFile = files.find((f) => /feat|feature|enhancement/i.test(f) && (f.endsWith('.md') || f.endsWith('.yml') || f.endsWith('.yaml')));
      }

      if (matchFile) {
        const fullPath = path.join(templateDir, matchFile);
        const raw = readFileSafe(fullPath);
        if (raw) {
          const { frontmatter, body } = parseFrontmatter(raw);
          return {
            source: 'github',
            file: path.relative(repoRoot, fullPath),
            frontmatter,
            rawTemplate: body || raw,
            name: frontmatter.name || matchFile,
            labels: frontmatter.labels ? frontmatter.labels.split(',').map((s) => s.trim()).filter(Boolean) : []
          };
        }
      }
    } catch {}
  }

  return {
    source: 'standard',
    file: null,
    frontmatter: {},
    rawTemplate: null,
    name: normType === 'bug' || normType === 'fix' || normType === 'hotfix' ? 'Standard Bug Report' : 'Standard Feature Request',
    labels: []
  };
}

export function formatTemplatedIssue({
  title = '',
  type = 'feat',
  body = '',
  problem = '',
  solution = '',
  acceptanceCriteria = '',
  reproduce = '',
  expected = '',
  environment = '',
  repoRoot = '.'
} = {}) {
  const template = loadIssueTemplate({ type, repoRoot });
  const normType = (type || 'feat').toLowerCase();
  const isBug = normType === 'bug' || normType === 'fix' || normType === 'hotfix';

  let renderedBody = '';

  if (template.rawTemplate) {
    let t = template.rawTemplate;
    if (isBug) {
      const bugDesc = problem || body || title;
      const reproSteps = reproduce || '1. Observe behavior\n2. Run automated test suite\n3. Note failure';
      const expBehavior = expected || solution || 'All Robert Martin assertions pass with zero regressions.';
      const envInfo = environment || '- OS: mac / linux\n- Runtime: node.js';

      t = t.replace(/A clear and concise description of what the bug is\./i, bugDesc);
      t = t.replace(/Steps to reproduce the behavior:[\s\S]*?(?=\*\*Expected|\n\n\*\*|$)/i, `Steps to reproduce the behavior:\n${reproSteps}\n`);
      t = t.replace(/A clear and concise description of what you expected to happen\.[^\n]*/i, expBehavior);
      t = t.replace(/- OS: \[e\.g\. iOS\][\s\S]*?(?=$|\n\n)/i, envInfo);
    } else {
      const probDesc = problem || body || `Enhancement required for ${title}.`;
      const solDesc = solution || acceptanceCriteria || body || 'Deliver complete end-to-end functionality adhering to Robert Martin Clean Architecture and TDD.';

      t = t.replace(/A clear and concise description of what the problem is\.[^\n]*/i, probDesc);
      t = t.replace(/A clear and concise description of what you want to happen\.[^\n]*/i, solDesc);
    }
    renderedBody = t;
  } else {
    if (isBug) {
      renderedBody =
        `### 🐛 Bug Description\n${problem || body || title}\n\n` +
        `### 🔄 Steps to Reproduce\n${reproduce || '1. Execute test suite\n2. Observe red failure'}\n\n` +
        `### 🎯 Expected Behavior (Robert Martin TDD)\n${expected || solution || 'Failing red test must be made green with minimal implementation.'}\n\n` +
        `### 💻 Environment\n${environment || '- OS: ' + (process.platform || 'mac')}\n`;
    } else {
      renderedBody =
        `### 🎯 Problem Statement & User Value\n${problem || body || title}\n\n` +
        `### 🚀 Solution & Acceptance Criteria\n${solution || acceptanceCriteria || body || 'Complete implementation with Dual-Gate test and mutation coverage.'}\n\n` +
        `### 🛡️ Clean Code Guarantees\n- 100% test pass rate with Robert Martin TDD\n- >= 85% diff test coverage\n- Zero direct pushes to protected branches\n`;
    }
  }

  const baseLabel = isBug ? (normType === 'hotfix' ? 'hotfix,status:ready,priority:critical' : 'bug,status:ready') : 'feature,status:ready';
  const templateLabels = template.labels || [];
  const mergedLabelsList = Array.from(new Set([...baseLabel.split(','), ...templateLabels])).filter(Boolean);

  return {
    template,
    title,
    body: renderedBody.trim(),
    labels: mergedLabelsList.join(',')
  };
}

export function transitionPMState({ issueId, state = 'in-progress', repoRoot = '.' } = {}) {
  const pm = detectPMProvider(repoRoot);
  const targetId = Number(issueId) || issueId;
  const normalizedState = String(state).toLowerCase().trim();

  let statusDisplayName = 'In Progress';
  if (normalizedState === 'backlog' || normalizedState === 'todo') {
    statusDisplayName = 'Backlog';
  } else if (normalizedState === 'ready') {
    statusDisplayName = 'Ready';
  } else if (normalizedState === 'in-progress' || normalizedState === 'inprogress' || normalizedState === 'in progress') {
    statusDisplayName = 'In Progress';
  } else if (normalizedState === 'in-review' || normalizedState === 'inreview' || normalizedState === 'review') {
    statusDisplayName = 'In Review';
  } else if (normalizedState === 'done' || normalizedState === 'closed') {
    statusDisplayName = 'Done';
  }

  const gateDisplayName = statusDisplayName === 'Done' ? 'Passed' : 'Pending';
  const gateLabel = statusDisplayName === 'Done' ? 'gate:passed' : 'gate:pending';

  let projectUpdated = false;
  let labelUpdated = false;
  let projectDetails = null;

  if (pm.provider === 'github') {
    try {
      const stateLabel = `status:${normalizedState}`;
      const statusLabels = ['status:backlog', 'status:ready', 'status:in-progress', 'status:in-review', 'status:done'];
      const gateLabels = ['gate:pending', 'gate:passed', 'gate:failed'];
      const toRemove = [...statusLabels.filter((l) => l !== stateLabel), ...gateLabels.filter((l) => l !== gateLabel)].join(',');
      try {
        execSync(`gh issue edit ${targetId} --remove-label "${toRemove}" 2>/dev/null || true`, { cwd: repoRoot, stdio: 'pipe' });
      } catch {}
      execSync(`gh issue edit ${targetId} --add-assignee "@me" --add-label "${stateLabel},${gateLabel}" 2>/dev/null || true`, { cwd: repoRoot, stdio: 'pipe' });
      if (statusDisplayName === 'Done') {
        try {
          execSync(`gh issue close ${targetId} 2>/dev/null || true`, { cwd: repoRoot, stdio: 'pipe' });
        } catch {}
      }
      labelUpdated = true;
    } catch {}

    try {
      const repoView = execSync('gh repo view --json nameWithOwner,owner,name 2>/dev/null || true', {
        cwd: repoRoot,
        stdio: 'pipe',
        encoding: 'utf8'
      }).trim();
      if (repoView) {
        const repoObj = JSON.parse(repoView);
        const owner = repoObj.owner?.login || repoObj.nameWithOwner?.split('/')[0];
        const repoName = repoObj.name || repoObj.nameWithOwner?.split('/')[1];

        const gqlQuery = `query($owner: String!, $repo: String!) {
          repository(owner: $owner, name: $repo) {
            projectsV2(first: 5) {
              nodes {
                id
                number
                title
                fields(first: 25) {
                  nodes {
                    ... on ProjectV2SingleSelectField {
                      id
                      name
                      options {
                        id
                        name
                      }
                    }
                  }
                }
              }
            }
          }
          user(login: $owner) {
            projectsV2(first: 10) {
              nodes {
                id
                number
                title
                fields(first: 25) {
                  nodes {
                    ... on ProjectV2SingleSelectField {
                      id
                      name
                      options {
                        id
                        name
                      }
                    }
                  }
                }
              }
            }
          }
        }`;

        const gqlOutput = execSync(
          `gh api graphql -f query='${gqlQuery.replace(/'/g, "'\\''")}' -F owner="${owner}" -F repo="${repoName}" 2>/dev/null || true`,
          { cwd: repoRoot, stdio: 'pipe', encoding: 'utf8' }
        ).trim();

        if (gqlOutput) {
          const gqlData = JSON.parse(gqlOutput);
          const repoProjects = gqlData.data?.repository?.projectsV2?.nodes || [];
          const userProjects = (gqlData.data?.user?.projectsV2?.nodes || []).filter((p) =>
            p && p.title && p.title.toLowerCase().includes(repoName.toLowerCase())
          );
          const seenIds = new Set();
          const projects = [...repoProjects, ...userProjects].filter((p) => {
            if (!p || seenIds.has(p.id)) return false;
            seenIds.add(p.id);
            return true;
          });

          for (const prj of projects) {
            const statusField = prj.fields?.nodes?.find((f) => f && f.name && f.name.toLowerCase() === 'status');
            const gateField = prj.fields?.nodes?.find((f) => f && f.name && f.name.toLowerCase() === 'quality gate');
            if (statusField && statusField.options && statusField.options.length > 0) {
              let matchedOption = statusField.options.find((opt) => opt.name.toLowerCase() === statusDisplayName.toLowerCase());
              if (!matchedOption) {
                matchedOption = statusField.options.find(
                  (opt) =>
                    opt.name.toLowerCase().includes(statusDisplayName.toLowerCase()) ||
                    statusDisplayName.toLowerCase().includes(opt.name.toLowerCase())
                );
              }
              if (!matchedOption) {
                if (statusDisplayName === 'Ready' || statusDisplayName === 'Backlog') {
                  matchedOption = statusField.options.find((opt) => opt.name.toLowerCase() === 'todo');
                } else if (statusDisplayName === 'In Review') {
                  matchedOption = statusField.options.find((opt) => opt.name.toLowerCase().includes('progress'));
                }
              }

              if (matchedOption) {
                let itemId = null;
                const issueUrl = `https://github.com/${owner}/${repoName}/issues/${targetId}`;
                try {
                  const addItemOut = execSync(
                    `gh project item-add ${prj.number} --owner "${owner}" --url "${issueUrl}" --format json 2>/dev/null || true`,
                    { cwd: repoRoot, stdio: 'pipe', encoding: 'utf8' }
                  ).trim();
                  if (addItemOut) {
                    const itemObj = JSON.parse(addItemOut);
                    itemId = itemObj.id;
                  }
                } catch {}

                if (!itemId) {
                  try {
                    const listItemsOut = execSync(
                      `gh project item-list ${prj.number} --owner "${owner}" --format json 2>/dev/null || true`,
                      { cwd: repoRoot, stdio: 'pipe', encoding: 'utf8' }
                    ).trim();
                    if (listItemsOut) {
                      const listData = JSON.parse(listItemsOut);
                      const found = (listData.items || []).find((it) => it.content?.number === Number(targetId));
                      if (found) itemId = found.id;
                    }
                  } catch {}
                }

                if (itemId) {
                  execSync(
                    `gh project item-edit --id "${itemId}" --project-id "${prj.id}" --field-id "${statusField.id}" --single-select-option-id "${matchedOption.id}" 2>/dev/null || true`,
                    { cwd: repoRoot, stdio: 'pipe' }
                  );
                  if (gateField && gateField.options) {
                    const gateOpt = gateField.options.find((o) => o.name.toLowerCase() === gateDisplayName.toLowerCase());
                    if (gateOpt) {
                      try {
                        execSync(
                          `gh project item-edit --id "${itemId}" --project-id "${prj.id}" --field-id "${gateField.id}" --single-select-option-id "${gateOpt.id}" 2>/dev/null || true`,
                          { cwd: repoRoot, stdio: 'pipe' }
                        );
                      } catch {}
                    }
                  }
                  projectUpdated = true;
                  projectDetails = {
                    projectNumber: prj.number,
                    projectTitle: prj.title,
                    statusField: statusField.name,
                    statusValue: matchedOption.name,
                    qualityGate: gateDisplayName
                  };
                  break;
                }
              }
            }
          }
        }
      }
    } catch {}

    return {
      success: true,
      provider: 'github',
      issueId: targetId,
      state: normalizedState,
      statusDisplayName,
      labelUpdated,
      projectUpdated,
      projectDetails
    };
  }

  const kanbanPath = path.resolve(repoRoot, 'docs/KANBAN.md');
  if (fs.existsSync(kanbanPath)) {
    try {
      let content = fs.readFileSync(kanbanPath, 'utf8');
      const ticketMarker = `#${targetId}`;
      if (content.includes(ticketMarker)) {
        const lines = content.split('\n');
        const updatedLines = [];
        for (const line of lines) {
          if (line.includes(ticketMarker) && line.startsWith('|')) {
            const cells = line.split('|').map((c) => c.trim());
            if (cells.length >= 7) {
              const itemContent = cells.slice(1, 6).find((c) => c.includes(ticketMarker)) || `#${targetId}`;
              let c1 = '', c2 = '', c3 = '', c4 = '', c5 = '';
              if (normalizedState === 'backlog') c1 = itemContent;
              else if (normalizedState === 'ready') c2 = itemContent;
              else if (normalizedState === 'in-progress') c3 = itemContent;
              else if (normalizedState === 'in-review') c4 = itemContent;
              else if (normalizedState === 'done') c5 = itemContent;
              updatedLines.push(`| ${c1} | ${c2} | ${c3} | ${c4} | ${c5} |`);
              continue;
            }
          }
          updatedLines.push(line);
        }
        fs.writeFileSync(kanbanPath, updatedLines.join('\n'), 'utf8');
      }
    } catch {}
  }

  return {
    success: true,
    provider: pm.provider,
    issueId: targetId,
    state: normalizedState,
    statusDisplayName
  };
}

export function createTicket({
  title,
  type = 'feat',
  body = '',
  problem = '',
  solution = '',
  acceptanceCriteria = '',
  reproduce = '',
  expected = '',
  environment = '',
  repoRoot = '.'
} = {}) {
  const pm = detectPMProvider(repoRoot);
  const normalizedType = type.toLowerCase();
  let prefix = 'feat';

  if (normalizedType === 'bug' || normalizedType === 'fix') {
    prefix = 'fix';
  } else if (normalizedType === 'hotfix') {
    prefix = 'hotfix';
  } else if (normalizedType === 'spike' || normalizedType === 'research') {
    prefix = 'spike';
  }

  const fullTitle = title.startsWith(`${prefix}:`) || title.startsWith(`${prefix}(`) ? title : `${prefix}: ${title}`;

  const templated = formatTemplatedIssue({
    title: fullTitle,
    type: prefix,
    body,
    problem,
    solution,
    acceptanceCriteria,
    reproduce,
    expected,
    environment,
    repoRoot
  });

  const issueBody = templated.body;
  const labelsToApply = templated.labels;

  if (pm.provider === 'github') {
    try {
      let output = '';
      try {
        output = execSync(
          `gh issue create --title "${fullTitle.replace(/"/g, '\\"')}" --body "${issueBody.replace(/"/g, '\\"')}" --label "${labelsToApply}"`,
          { cwd: repoRoot, stdio: 'pipe', encoding: 'utf8' }
        );
      } catch {
        output = execSync(
          `gh issue create --title "${fullTitle.replace(/"/g, '\\"')}" --body "${issueBody.replace(/"/g, '\\"')}"`,
          { cwd: repoRoot, stdio: 'pipe', encoding: 'utf8' }
        );
      }
      const url = output.trim();
      const match = url.match(/\/issues\/(\d+)/);
      const issueId = match ? Number(match[1]) : null;

      if (issueId) {
        try {
          transitionPMState({ issueId, state: 'ready', repoRoot });
        } catch {}
      }

      return { success: true, provider: 'github', issueId, url, title: fullTitle, type: prefix, template: templated.template.name };
    } catch {
      console.warn('⚠️ GitHub issue creation failed or CLI not authenticated. Falling back to local docs/KANBAN.md.');
    }
  }

  const kanbanPath = path.resolve(repoRoot, 'docs/KANBAN.md');
  const now = new Date().toISOString().replace('T', ' ').substring(0, 19) + ' UTC';
  let issueId = Date.now() % 10000;
  let content = readFileSafe(kanbanPath);

  if (!content) {
    fs.mkdirSync(path.dirname(kanbanPath), { recursive: true });
    content = `# 📋 Project Kanban Board & Traceability Matrix\n\n> **Living Project State & Audit Trail**\n> **Last Synced**: \`${now}\`\n\n---\n\n## 📊 Board Overview\n\n| 📥 Backlog | 📋 Ready | ⚡ In Progress | 🔍 In Review | ✅ Done |\n| :--- | :--- | :--- | :--- | :--- |\n\n---\n\n## 🎯 End-to-End Traceability Matrix\n\n| Issue / Spec | Status | Priority | Component | Milestone | Spec / Contract |\n| :--- | :--- | :--- | :--- | :--- |\n`;
  }

  const ticketRef = `#${issueId}: [${prefix.toUpperCase()}] ${fullTitle}`;
  const tableMarker = '| :--- | :--- | :--- | :--- | :--- |\n';
  const tableIdx = content.indexOf(tableMarker);

  if (tableIdx !== -1) {
    const insertPos = tableIdx + tableMarker.length;
    content = content.slice(0, insertPos) + `| | ${ticketRef} | | | |\n` + content.slice(insertPos);
  }

  const matrixMarker = '## 🎯 End-to-End Traceability Matrix\n\n| Issue / Spec | Status | Priority | Component | Milestone | Spec / Contract |\n| :--- | :--- | :--- | :--- | :--- |\n';
  const matrixIdx = content.indexOf(matrixMarker);
  if (matrixIdx !== -1) {
    const insertPos = matrixIdx + matrixMarker.length;
    const priority = prefix === 'hotfix' ? '`P0 - Blocker`' : (prefix === 'bug' ? '`P1 - High`' : '`P2 - Medium`');
    content = content.slice(0, insertPos) + `| **#${issueId}** | \`status:ready\` | ${priority} | Core | Current | ${fullTitle} |\n` + content.slice(insertPos);
  }

  fs.writeFileSync(kanbanPath, content, 'utf8');
  return { success: true, provider: 'local', issueId, url: `docs/KANBAN.md#${issueId}`, title: fullTitle, type: prefix };
}

export function postPMComment({ issueId, prNumber = null, comment = '', repoRoot = '.' } = {}) {
  const pm = detectPMProvider(repoRoot);
  const targetId = issueId || prNumber;
  if (!targetId || !comment) {
    throw new Error('Both issueId/prNumber and comment are required to post a review comment.');
  }

  if (pm.provider === 'github') {
    let posted = false;
    let errors = [];

    if (prNumber) {
      try {
        execSync(`gh pr comment ${prNumber} --body "${comment.replace(/"/g, '\\"')}"`, { cwd: repoRoot, stdio: 'pipe' });
        posted = true;
      } catch (err) {
        errors.push(`gh pr comment: ${err.message}`);
      }
    }

    try {
      execSync(`gh issue comment ${issueId || prNumber} --body "${comment.replace(/"/g, '\\"')}"`, { cwd: repoRoot, stdio: 'pipe' });
      posted = true;
    } catch (err) {
      errors.push(`gh issue comment: ${err.message}`);
    }

    if (posted) {
      console.log(`💬 [GITHUB] Review comment posted to #${targetId}`);
      return { success: true, provider: 'github', targetId, comment };
    }
    console.warn(`⚠️ GitHub comment failed (${errors.join('; ')}). Falling back to local KANBAN.md.`);
  }

  if (pm.provider === 'jira') {
    try {
      const auth = Buffer.from(`${process.env.JIRA_EMAIL || 'bot'}:${process.env.JIRA_API_TOKEN}`).toString('base64');
      const url = `${process.env.JIRA_BASE_URL.replace(/\/$/, '')}/rest/api/3/issue/${issueId}/comment`;
      const bodyPayload = JSON.stringify({
        body: {
          type: 'doc',
          version: 1,
          content: [{ type: 'paragraph', content: [{ type: 'text', text: comment }] }]
        }
      });
      execSync(`curl -s -X POST "${url}" -H "Authorization: Basic ${auth}" -H "Content-Type: application/json" -d '${bodyPayload.replace(/'/g, "'\\''")}'`, { cwd: repoRoot, stdio: 'pipe' });
      console.log(`💬 [JIRA] Review comment posted to Jira issue ${issueId}`);
      return { success: true, provider: 'jira', targetId: issueId, comment };
    } catch (err) {
      console.warn(`⚠️ Jira comment failed: ${err.message}. Falling back to local KANBAN.md.`);
    }
  }

  if (pm.provider === 'linear') {
    try {
      const gql = JSON.stringify({
        query: `mutation CreateComment($issueId: String!, $body: String!) { commentCreate(input: { issueId: $issueId, body: $body }) { success } }`,
        variables: { issueId: String(issueId), body: comment }
      });
      execSync(`curl -s -X POST https://api.linear.app/graphql -H "Authorization: ${process.env.LINEAR_API_KEY}" -H "Content-Type: application/json" -d '${gql.replace(/'/g, "'\\''")}'`, { cwd: repoRoot, stdio: 'pipe' });
      console.log(`💬 [LINEAR] Review comment posted to Linear issue ${issueId}`);
      return { success: true, provider: 'linear', targetId: issueId, comment };
    } catch (err) {
      console.warn(`⚠️ Linear comment failed: ${err.message}. Falling back to local KANBAN.md.`);
    }
  }

  const kanbanPath = path.resolve(repoRoot, 'docs/KANBAN.md');
  const now = new Date().toISOString().replace('T', ' ').substring(0, 19) + ' UTC';
  let content = readFileSafe(kanbanPath);

  if (!content) {
    fs.mkdirSync(path.dirname(kanbanPath), { recursive: true });
    content = `# 📋 Project Kanban Board & Traceability Matrix\n\n> **Living Project State & Audit Trail**\n> **Last Synced**: \`${now}\`\n\n---\n\n## 📊 Board Overview\n\n| 📥 Backlog | 📋 Ready | ⚡ In Progress | 🔍 In Review | ✅ Done |\n| :--- | :--- | :--- | :--- | :--- |\n`;
  }

  const commentSectionHeader = '## 💬 Review Audit Comments & Feedback\n\n';
  const commentBlock = `### [${now}] Audit for #${targetId}\n\n${comment}\n\n---\n\n`;

  if (content.includes(commentSectionHeader)) {
    content = content.replace(commentSectionHeader, commentSectionHeader + commentBlock);
  } else {
    content = content + `\n\n${commentSectionHeader}${commentBlock}`;
  }

  fs.writeFileSync(kanbanPath, content, 'utf8');
  console.log(`💬 [LOCAL KANBAN] Review comment recorded in docs/KANBAN.md for #${targetId}`);
  return { success: true, provider: 'local', targetId, comment, file: 'docs/KANBAN.md' };
}

export function installFramework({ targetDir = '.', global = false, repoRoot = '.' } = {}) {
  const isGlobal = !!global;
  const packageDir = path.resolve(import.meta.dirname, '../../..');
  const sourceSkills = path.resolve(packageDir, '.agent/skills');
  const sourceScripts = path.resolve(packageDir, '.agent/scripts');
  const sourceDocs = path.resolve(packageDir, 'docs/templates');

  let destSkills, destScripts, destDocs;

  if (isGlobal) {
    const home = process.env.HOME || process.env.USERPROFILE || '';
    destSkills = path.resolve(home, '.gemini/config/skills');
    destScripts = path.resolve(home, '.gemini/config/scripts');
    console.log(`⚡ Installing Agentic SDLC Framework globally into ${destSkills}...`);
  } else {
    const targetAbs = path.resolve(repoRoot, targetDir);
    const agentDirName = fs.existsSync(path.resolve(targetAbs, '.agents/skills')) ? '.agents' : '.agent';
    destSkills = path.resolve(targetAbs, agentDirName, 'skills');
    destScripts = path.resolve(targetAbs, agentDirName, 'scripts');
    destDocs = path.resolve(targetAbs, 'docs/templates');
    console.log(`⚡ Installing Agentic SDLC Framework locally into ${targetAbs}...`);
  }

  fs.mkdirSync(destSkills, { recursive: true });
  fs.mkdirSync(destScripts, { recursive: true });
  if (destDocs) fs.mkdirSync(destDocs, { recursive: true });

  function copyRecursive(src, dest) {
    if (!fs.existsSync(src)) return;
    const entries = fs.readdirSync(src, { withFileTypes: true });
    for (const entry of entries) {
      const srcPath = path.join(src, entry.name);
      const destPath = path.join(dest, entry.name);
      if (entry.isDirectory()) {
        fs.mkdirSync(destPath, { recursive: true });
        copyRecursive(srcPath, destPath);
      } else {
        fs.copyFileSync(srcPath, destPath);
        if (entry.name.endsWith('.sh') || entry.name.endsWith('.mjs')) {
          try {
            fs.chmodSync(destPath, 0o755);
          } catch {}
        }
      }
    }
  }

  copyRecursive(sourceSkills, destSkills);
  copyRecursive(sourceScripts, destScripts);
  if (destDocs && fs.existsSync(sourceDocs)) {
    copyRecursive(sourceDocs, destDocs);
  }

  const loadedSkills = fs.existsSync(destSkills)
    ? fs.readdirSync(destSkills).filter((f) => fs.statSync(path.join(destSkills, f)).isDirectory()).length
    : 0;
  console.log(`✅ Installation complete (${loadedSkills} skills loaded).`);

  return { success: true, global: isGlobal, skillsCount: loadedSkills, destSkills, destScripts };
}

export function pollCIPRChecks(prNumber, { timeoutSeconds = 300, pollIntervalSeconds = 15, repoRoot = '.' } = {}) {
  console.log(`🛰️ Polling remote CI/CD status checks for PR #${prNumber} (timeout: ${timeoutSeconds}s)...`);
  const startTime = Date.now();

  while (Date.now() - startTime < timeoutSeconds * 1000) {
    try {
      const output = execSync(`gh pr checks ${prNumber} 2>&1`, { cwd: repoRoot, encoding: 'utf8' });
      if (output.includes('fail') || output.includes('error')) {
        return { success: false, passed: false, error: 'Remote CI check failure detected.', output };
      }
      if (output.includes('pending') || output.includes('running')) {
        execSync(`sleep ${pollIntervalSeconds}`);
        continue;
      }
      return { success: true, passed: true, output };
    } catch (err) {
      if (err.stdout && (err.stdout.includes('fail') || err.stdout.includes('error'))) {
        return { success: false, passed: false, error: 'Remote CI check failure detected.', output: err.stdout };
      }
      try {
        execSync(`sleep ${pollIntervalSeconds}`);
      } catch {}
    }
  }

  return { success: false, passed: false, error: `CI check polling timed out after ${timeoutSeconds}s.` };
}

export function parseSemVer(verStr = '1.0.0') {
  const clean = verStr.replace(/^v/, '').trim();
  const parts = clean.split('.').map((n) => parseInt(n, 10));
  return {
    major: isNaN(parts[0]) ? 1 : parts[0],
    minor: isNaN(parts[1]) ? 0 : parts[1],
    patch: isNaN(parts[2]) ? 0 : parts[2],
    raw: clean
  };
}

export function bumpSemVer(currentVersion = '1.0.0', bumpType = 'patch') {
  const semver = parseSemVer(currentVersion);
  switch (bumpType.toLowerCase()) {
    case 'major':
      return `${semver.major + 1}.0.0`;
    case 'minor':
      return `${semver.major}.${semver.minor + 1}.0`;
    case 'patch':
    default:
      return `${semver.major}.${semver.minor}.${semver.patch + 1}`;
  }
}
