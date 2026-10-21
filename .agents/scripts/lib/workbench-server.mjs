import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import { fileExists, readFileSafe } from './core.mjs';
import { getSessionStatus } from './session.mjs';
import { buildDecisionGraph } from './decision-graph.mjs';
import { parseProductMarkdown, planProductDAG, controlProductDAG } from './product-dag.mjs';
import { listADRs, decideADR } from './adr-drift.mjs';
import { verifyAuditChain } from './audit-chain.mjs';

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.mjs': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.ico': 'image/x-icon'
};

function readJsonBody(req) {
  return new Promise((resolve) => {
    let raw = '';
    req.on('data', (chunk) => (raw += chunk));
    req.on('end', () => {
      try {
        resolve(raw ? JSON.parse(raw) : {});
      } catch {
        resolve({});
      }
    });
  });
}

export function renderWorkbenchHTML(repoRoot = '.', getSessionStatusFn = getSessionStatus) {
  const sessionStatus = typeof getSessionStatusFn === 'function' ? getSessionStatusFn(repoRoot) : { active: false, session: null };
  const activeSess = sessionStatus.session;
  const kanbanPath = path.resolve(repoRoot, 'docs/KANBAN.md');
  const kanbanRaw = fileExists(kanbanPath) ? readFileSafe(kanbanPath) : 'No KANBAN.md file found.';
  const archTracePath = path.resolve(repoRoot, 'docs/ARCH_TRACE.md');
  const archTraceRaw = fileExists(archTracePath) ? readFileSafe(archTracePath) : 'No ARCH_TRACE.md recorded yet.';

  // Check if compiled SvelteKit 5 static index.html exists in .agent/workbench/index.html
  const svelteIndex = path.resolve(repoRoot, '.agent/workbench/index.html');
  if (fileExists(svelteIndex)) {
    const builtHtml = readFileSafe(svelteIndex);
    if (builtHtml && builtHtml.includes('<!DOCTYPE html')) {
      return builtHtml;
    }
  }

  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>Agentic SDLC Framework: Workbench & Live Mission Control</title>
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <style>
    :root {
      --bg: #0f172a; --panel: #1e293b; --border: #334155; --text: #f8fafc;
      --accent: #38bdf8; --success: #22c55e; --warning: #f59e0b; --danger: #ef4444;
    }
    body { margin: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, monospace; background: var(--bg); color: var(--text); padding: 20px; }
    header { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--border); padding-bottom: 12px; margin-bottom: 20px; }
    h1 { margin: 0; font-size: 20px; color: var(--accent); }
    .status-badge { background: #0284c7; color: white; padding: 4px 10px; border-radius: 999px; font-size: 12px; font-weight: bold; }
    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 20px; }
    .card { background: var(--panel); border: 1px solid var(--border); border-radius: 8px; padding: 16px; }
    .card h2 { margin-top: 0; font-size: 16px; border-bottom: 1px solid var(--border); padding-bottom: 8px; display: flex; justify-content: space-between; }
    pre { background: #090d16; padding: 12px; border-radius: 6px; overflow-x: auto; font-size: 12px; border: 1px solid var(--border); }
    .stage-bar { display: flex; gap: 4px; margin: 15px 0; }
    .stage-step { flex: 1; padding: 8px 4px; text-align: center; font-size: 11px; background: #334155; border-radius: 4px; }
    .stage-step.active { background: #0284c7; font-weight: bold; }
    .stage-step.done { background: #15803d; }
  </style>
</head>
<body>
  <header>
    <div>
      <h1>⚡ Agentic SDLC Framework: Mission Control</h1>
      <small>Universal AI Agent Governance, Decision Lineage & Observability</small>
    </div>
    <div>
      <span class="status-badge">${activeSess ? 'Active Session #' + activeSess.issueId : 'No Active Session'}</span>
    </div>
  </header>

  <div class="card" style="margin-bottom: 20px;">
    <h2>📊 8-Stage Lifecycle Progress ${activeSess ? '(' + activeSess.branch + ')' : ''}</h2>
    <div class="stage-bar">
      <div class="stage-step ${activeSess?.stage >= 1 ? (activeSess?.stage === 1 ? 'active' : 'done') : ''}">1. Intake</div>
      <div class="stage-step ${activeSess?.stage >= 2 ? (activeSess?.stage === 2 ? 'active' : 'done') : ''}">2. Branch</div>
      <div class="stage-step ${activeSess?.stage >= 3 ? (activeSess?.stage === 3 ? 'active' : 'done') : ''}">3. TDD</div>
      <div class="stage-step ${activeSess?.stage >= 4 ? (activeSess?.stage === 4 ? 'active' : 'done') : ''}">4. Dual-Gate</div>
      <div class="stage-step ${activeSess?.stage >= 5 ? (activeSess?.stage === 5 ? 'active' : 'done') : ''}">5. Docs</div>
      <div class="stage-step ${activeSess?.stage >= 6 ? (activeSess?.stage === 6 ? 'active' : 'done') : ''}">6. PR</div>
      <div class="stage-step ${activeSess?.stage >= 7 ? (activeSess?.stage === 7 ? 'active' : 'done') : ''}">7. Review</div>
      <div class="stage-step ${activeSess?.stage >= 8 ? (activeSess?.stage === 8 ? 'active' : 'done') : ''}">8. Done</div>
    </div>
  </div>

  <div class="grid">
    <div class="card">
      <h2>🧬 Causal Decision Lineage & ADR Governance</h2>
      <div id="decision-graph-mount">Loading causal decision graph from <code>/api/decision-graph</code>...</div>
    </div>
    <div class="card">
      <h2>⏱️ Architecture Time-Machine & PRODUCT.md DAG</h2>
      <div id="product-dag-mount">Loading PRODUCT.md & DAG state from <code>/api/product</code>...</div>
    </div>
    <div class="card">
      <h2>📋 Living Kanban Dashboard</h2>
      <pre>${kanbanRaw.replace(/</g, '&lt;').replace(/>/g, '&gt;')}</pre>
    </div>
    <div class="card">
      <h2>🏛️ Architectural Trace Ledger</h2>
      <pre>${archTraceRaw.replace(/</g, '&lt;').replace(/>/g, '&gt;')}</pre>
    </div>
  </div>
  <script>
    async function bootWorkbench() {
      try {
        const [graph, prod] = await Promise.all([
          fetch('/api/decision-graph').then(r => r.json()),
          fetch('/api/product').then(r => r.json())
        ]);
        const dgEl = document.getElementById('decision-graph-mount');
        if (dgEl) {
          dgEl.innerHTML = '<pre>' + JSON.stringify({ stats: graph.stats, driftPivots: graph.driftPivots, nodes: graph.nodes }, null, 2) + '</pre>';
        }
        const pdEl = document.getElementById('product-dag-mount');
        if (pdEl) {
          pdEl.innerHTML = '<pre>' + JSON.stringify(prod, null, 2) + '</pre>';
        }
      } catch (e) {}
    }
    bootWorkbench();
  </script>
</body>
</html>`;
}

export function createWorkbenchServer({ port = 4120, repoRoot = '.', getSessionStatusFn = getSessionStatus } = {}) {
  const sseClients = new Set();

  const broadcast = (eventType, payload) => {
    const msg = `event: ${eventType}\ndata: ${JSON.stringify(payload)}\n\n`;
    for (const res of sseClients) {
      try {
        res.write(msg);
      } catch {
        sseClients.delete(res);
      }
    }
  };

  const server = http.createServer(async (req, res) => {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

    if (req.method === 'OPTIONS') {
      res.writeHead(204);
      res.end();
      return;
    }

    const urlObj = new URL(req.url || '/', 'http://localhost');
    const pathname = urlObj.pathname;

    // 1. Live Server-Sent Events (SSE) Stream
    if (pathname === '/api/events') {
      res.writeHead(200, {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
        Connection: 'keep-alive'
      });
      res.write(`event: connected\ndata: ${JSON.stringify({ timestamp: new Date().toISOString() })}\n\n`);
      sseClients.add(res);
      req.on('close', () => sseClients.delete(res));
      return;
    }

    // 2. Session & Audit Status API
    if (pathname === '/api/status') {
      const status = typeof getSessionStatusFn === 'function' ? getSessionStatusFn(repoRoot) : { active: false };
      const audit = verifyAuditChain(repoRoot);
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ ...status, audit }));
      return;
    }

    // 3. Causal Decision Graph & Architecture Time-Slider API
    if (pathname === '/api/decision-graph') {
      const graph = buildDecisionGraph(repoRoot);
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(graph));
      return;
    }

    // 4. PRODUCT.md & Topological DAG API
    if (pathname === '/api/product') {
      const product = parseProductMarkdown(repoRoot);
      const dag = planProductDAG({ repoRoot, autoProvision: false });
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ product, dag }));
      return;
    }

    // 5. ADR List API
    if (pathname === '/api/adr' && req.method === 'GET') {
      const adrs = listADRs(repoRoot);
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ adrs }));
      return;
    }

    // 6. Bidirectional Action: Approve / Reject Pending Product ADR
    if (pathname === '/api/adr/decide' && req.method === 'POST') {
      try {
        const body = await readJsonBody(req);
        const decided = decideADR({
          adrId: body.adrId,
          action: body.action || 'approve',
          reviewer: body.reviewer || '@workbench-operator',
          reason: body.reason || 'Decided in SvelteKit Workbench',
          repoRoot
        });
        broadcast('adr-updated', decided);
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify(decided));
      } catch (err) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: false, error: err.message }));
      }
      return;
    }

    // 7. Bidirectional Action: Pause / Resume / Advance Product DAG
    if (pathname === '/api/dag/control' && req.method === 'POST') {
      const body = await readJsonBody(req);
      const ctl = controlProductDAG({
        action: body.action || 'status',
        reason: body.reason || '',
        wave: body.wave || null,
        repoRoot
      });
      broadcast('dag-updated', ctl);
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(ctl));
      return;
    }

    // 8. Static SvelteKit Assets in .agent/workbench/
    const workbenchStaticDir = path.resolve(repoRoot, '.agent/workbench');
    if (pathname !== '/' && pathname !== '/index.html' && fileExists(workbenchStaticDir)) {
      const safeRel = pathname.replace(/^\/+/, '');
      const candidatePath = path.resolve(workbenchStaticDir, safeRel);
      if (candidatePath.startsWith(workbenchStaticDir) && fileExists(candidatePath) && fs.statSync(candidatePath).isFile()) {
        const ext = path.extname(candidatePath).toLowerCase();
        res.writeHead(200, { 'Content-Type': MIME_TYPES[ext] || 'application/octet-stream' });
        res.end(fs.readFileSync(candidatePath));
        return;
      }
    }

    // 9. Root HTML (Compiled SvelteKit App or Mission Control Fallback)
    if (pathname === '/' || pathname === '/index.html') {
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
      res.end(renderWorkbenchHTML(repoRoot, getSessionStatusFn));
      return;
    }

    res.writeHead(404, { 'Content-Type': 'text/plain' });
    res.end('Not Found');
  });

  return {
    server,
    port,
    broadcast,
    listen: () => {
      return new Promise((resolve, reject) => {
        server.listen(port, () => {
          const actualPort = server.address()?.port || port;
          console.log(`🌐 Agentic SDLC SvelteKit Workbench live at: http://localhost:${actualPort}`);
          resolve(server);
        });
        server.on('error', reject);
      });
    },
    close: () => {
      for (const client of sseClients) {
        try {
          client.end();
        } catch {}
      }
      sseClients.clear();
      return new Promise((resolve) => server.close(resolve));
    }
  };
}
