import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { execSync, execFileSync } from 'node:child_process';

export const AUDIT_LEDGER_FILE = 'docs/AUDIT_CHAIN.jsonl';
export const GENESIS_HASH = '0000000000000000000000000000000000000000000000000000000000000000';

export function calculateRecordHash({ index, timestamp, event, metadata, previousHash }) {
  const payload = JSON.stringify({ index, timestamp, event, metadata, previousHash });
  return crypto.createHash('sha256').update(payload).digest('hex');
}

export function appendAuditRecord({ event, metadata = {}, repoRoot = '.' } = {}) {
  const auditPath = path.resolve(repoRoot, AUDIT_LEDGER_FILE);
  fs.mkdirSync(path.dirname(auditPath), { recursive: true });

  let previousHash = GENESIS_HASH;
  let index = 0;

  if (fs.existsSync(auditPath)) {
    const content = fs.readFileSync(auditPath, 'utf8').trim();
    if (content) {
      const lines = content.split('\n').filter(Boolean);
      if (lines.length > 0) {
        try {
          const lastEntry = JSON.parse(lines[lines.length - 1]);
          index = typeof lastEntry.index === 'number' ? lastEntry.index + 1 : lines.length;
          previousHash = lastEntry.currentHash || lastEntry.hash || GENESIS_HASH;
        } catch {
          index = lines.length;
        }
      }
    }
  }

  const timestamp = new Date().toISOString();
  const currentHash = calculateRecordHash({ index, timestamp, event, metadata, previousHash });

  const record = {
    index,
    timestamp,
    event,
    metadata,
    previousHash,
    currentHash
  };

  fs.appendFileSync(auditPath, JSON.stringify(record) + '\n', 'utf8');

  return {
    success: true,
    record,
    auditPath
  };
}

export function verifyAuditChain(repoRoot = '.') {
  const auditPath = path.resolve(repoRoot, AUDIT_LEDGER_FILE);
  if (!fs.existsSync(auditPath)) {
    return {
      valid: true,
      recordsCount: 0,
      chainHead: GENESIS_HASH,
      merkleRoot: GENESIS_HASH,
      records: [],
      message: 'No audit records present (chain empty).'
    };
  }

  const content = fs.readFileSync(auditPath, 'utf8').trim();
  if (!content) {
    return {
      valid: true,
      recordsCount: 0,
      chainHead: GENESIS_HASH,
      merkleRoot: GENESIS_HASH,
      records: [],
      message: 'Audit chain is empty.'
    };
  }

  const lines = content.split('\n').filter(Boolean);
  let expectedPrevHash = GENESIS_HASH;
  const records = [];

  for (let i = 0; i < lines.length; i++) {
    let parsed;
    try {
      parsed = JSON.parse(lines[i]);
    } catch (err) {
      return {
        valid: false,
        errorIndex: i,
        error: `Malformed JSON at line ${i + 1}: ${err.message}`,
        recordsCount: lines.length
      };
    }

    if (parsed.index !== i) {
      return {
        valid: false,
        errorIndex: i,
        error: `Index mismatch at record ${i}: expected ${i}, found ${parsed.index}`,
        recordsCount: lines.length
      };
    }

    if (parsed.previousHash !== expectedPrevHash) {
      return {
        valid: false,
        errorIndex: i,
        error: `Cryptographic chain broken at record ${i}: previousHash mismatch. Expected ${expectedPrevHash}, found ${parsed.previousHash}`,
        recordsCount: lines.length
      };
    }

    const recalculatedHash = calculateRecordHash({
      index: parsed.index,
      timestamp: parsed.timestamp,
      event: parsed.event,
      metadata: parsed.metadata,
      previousHash: parsed.previousHash
    });

    if (recalculatedHash !== parsed.currentHash) {
      return {
        valid: false,
        errorIndex: i,
        error: `SHA-256 tampering detected at record ${i}: calculated ${recalculatedHash}, recorded ${parsed.currentHash}`,
        recordsCount: lines.length
      };
    }

    expectedPrevHash = parsed.currentHash;
    records.push(parsed);
  }

  return {
    valid: true,
    recordsCount: records.length,
    chainHead: expectedPrevHash,
    // Deprecated alias: this is a linear hash chain, not a Merkle tree.
    merkleRoot: expectedPrevHash,
    records,
    message: `Audit chain verified successfully (${records.length} immutable records).`
  };
}

export const RECEIPT_KEY_FILE = '.agent/.receipt-key';

/**
 * Loads the HMAC key used to sign gate receipts.
 * Order: SDLC_RECEIPT_KEY env var (e.g. a CI secret), then `.agent/.receipt-key`
 * (gitignored, mode 0600). With `create: true` a random 256-bit key is generated.
 * Returns null when no key exists and none may be created.
 */
export function loadReceiptKey(repoRoot = '.', { create = false } = {}) {
  if (process.env.SDLC_RECEIPT_KEY) return Buffer.from(process.env.SDLC_RECEIPT_KEY, 'utf8');
  const keyPath = path.resolve(repoRoot, RECEIPT_KEY_FILE);
  if (fs.existsSync(keyPath)) {
    const raw = fs.readFileSync(keyPath, 'utf8').trim();
    if (raw) return Buffer.from(raw, 'utf8');
  }
  if (!create) return null;
  const key = crypto.randomBytes(32).toString('hex');
  fs.mkdirSync(path.dirname(keyPath), { recursive: true });
  fs.writeFileSync(keyPath, key + '\n', { encoding: 'utf8', mode: 0o600 });
  return Buffer.from(key, 'utf8');
}

function canonicalJSON(value) {
  if (Array.isArray(value)) return `[${value.map(canonicalJSON).join(',')}]`;
  if (value && typeof value === 'object') {
    return `{${Object.keys(value)
      .sort()
      .map((k) => `${JSON.stringify(k)}:${canonicalJSON(value[k])}`)
      .join(',')}}`;
  }
  return JSON.stringify(value);
}

export function signReceiptPayload(payload, key) {
  return crypto.createHmac('sha256', key).update(canonicalJSON(payload)).digest('hex');
}

export function verifyReceiptSignature(receipt, key) {
  if (!receipt || !receipt.signature || !key) return false;
  const { signature, ...payload } = receipt;
  if (signature.alg !== 'HMAC-SHA256' || typeof signature.value !== 'string') return false;
  const expected = signReceiptPayload(payload, key);
  const a = Buffer.from(expected, 'hex');
  const b = Buffer.from(signature.value, 'hex');
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

export function issueGateReceipt({
  issueId,
  branch,
  headSha = null,
  coveragePct,
  coverage,
  mutationPct,
  mutationScore,
  docsOnly,
  peerReviewApproved,
  reviewer = null,
  driftCheckPassed,
  repoRoot = '.'
} = {}) {
  const rawCov = coveragePct !== undefined ? coveragePct : coverage;
  const rawMut = mutationPct !== undefined ? mutationPct : mutationScore;
  const resolvedDocsOnly = docsOnly !== undefined ? Boolean(docsOnly) : rawCov === null && rawMut === null;
  const resolvedCoverage = resolvedDocsOnly && rawCov === undefined ? null : rawCov;
  const resolvedMutation = resolvedDocsOnly && rawMut === undefined ? null : rawMut;

  const missing = [];
  if (!resolvedDocsOnly && !Number.isFinite(resolvedCoverage)) missing.push('coveragePct');
  if (!resolvedDocsOnly && resolvedMutation !== null && !Number.isFinite(resolvedMutation)) missing.push('mutationPct');
  if (typeof peerReviewApproved !== 'boolean') missing.push('peerReviewApproved');
  if (typeof driftCheckPassed !== 'boolean') missing.push('driftCheckPassed');
  if (missing.length > 0) {
    return {
      success: false,
      missing,
      reason: `Refusing to issue gate receipt: unmeasured value(s): ${missing.join(', ')}. Run 'coverage', 'mutation' and 'review record' first.`
    };
  }

  let resolvedSha = headSha;
  if (!resolvedSha) {
    try {
      resolvedSha = execFileSync('git', ['rev-parse', 'HEAD'], { cwd: repoRoot, encoding: 'utf8', stdio: 'pipe' }).trim();
    } catch {
      return { success: false, missing: ['headSha'], reason: 'Refusing to issue gate receipt: cannot resolve HEAD commit.' };
    }
  }

  const productMdPath = path.resolve(repoRoot, 'PRODUCT.md');
  const productMdHash = fs.existsSync(productMdPath)
    ? crypto.createHash('sha256').update(fs.readFileSync(productMdPath, 'utf8')).digest('hex')
    : null;

  const key = loadReceiptKey(repoRoot, { create: true });
  const payload = {
    receiptId: `GATE-RECEIPT-#${issueId}-${resolvedSha.slice(0, 8)}`,
    issueId: Number(issueId) || issueId,
    branch: branch || 'feature/unknown',
    headSha: resolvedSha,
    productMdHash,
    stage: 7,
    coveragePct: resolvedCoverage,
    mutationPct: resolvedMutation,
    docsOnly: resolvedDocsOnly,
    mutationStatus: resolvedDocsOnly ? 'docs-only' : resolvedMutation === null ? 'not-configured' : 'measured',
    peerReviewApproved,
    reviewer,
    driftCheckPassed,
    issuedAt: new Date().toISOString()
  };
  const receipt = {
    ...payload,
    signature: {
      alg: 'HMAC-SHA256',
      keyId: crypto.createHash('sha256').update(key).digest('hex').slice(0, 16),
      value: signReceiptPayload(payload, key)
    }
  };

  const auditRes = appendAuditRecord({
    event: 'STAGE_7_GATE_RECEIPT_SIGNED',
    metadata: receipt,
    repoRoot
  });

  return {
    success: true,
    receipt: {
      ...receipt,
      chainHash: auditRes.record.currentHash
    },
    auditRecord: auditRes.record
  };
}

/**
 * Verifies that a valid, untampered Stage 7 Cryptographic Gate Receipt exists
 * in the hash-chained audit ledger for the given commit SHA or branch,
 * with a valid HMAC signature under the local receipt key.
 */
export function verifyGateReceipt({ headSha = null, branch = null, repoRoot = '.' } = {}) {
  const chain = verifyAuditChain(repoRoot);
  if (!chain.valid) {
    return {
      valid: false,
      reason: `Audit hash chain corrupted: ${chain.error}`
    };
  }

  let targetSha = headSha;
  if (!targetSha && branch) {
    try {
      targetSha = execFileSync('git', ['rev-parse', '--verify', '-q', '--end-of-options', branch], {
        cwd: repoRoot,
        encoding: 'utf8',
        stdio: 'pipe'
      }).trim();
    } catch {}
  }

  const key = loadReceiptKey(repoRoot);
  if (!key) {
    return { valid: false, targetSha, branch, reason: `No receipt signing key (${RECEIPT_KEY_FILE} or SDLC_RECEIPT_KEY); cannot verify receipts.` };
  }

  const receipts = (chain.records || [])
    .filter((r) => r.event === 'STAGE_7_GATE_RECEIPT_SIGNED' && r.metadata)
    .reverse();

  const matched = receipts.find((r) => {
    const m = r.metadata;
    const shaMatch = targetSha ? m.headSha === targetSha : true;
    const branchMatch = branch ? m.branch === branch : true;
    return shaMatch && branchMatch && m.peerReviewApproved === true && m.driftCheckPassed === true && verifyReceiptSignature(m, key);
  });

  if (!matched) {
    return {
      valid: false,
      targetSha,
      branch,
      reason: `No valid signed Stage 7 gate receipt found for ${targetSha || branch || 'HEAD'}.`
    };
  }

  return {
    valid: true,
    receipt: matched.metadata,
    chainHash: matched.currentHash,
    chainHead: chain.chainHead
  };
}
