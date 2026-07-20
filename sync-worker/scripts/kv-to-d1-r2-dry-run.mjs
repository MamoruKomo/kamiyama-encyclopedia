#!/usr/bin/env node

import { readFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';

const args = new Map();
for (let index = 2; index < process.argv.length; index += 1) {
  const arg = process.argv[index];
  if (arg.startsWith('--')) {
    const next = process.argv[index + 1];
    args.set(arg.slice(2), next && !next.startsWith('--') ? next : 'true');
    if (next && !next.startsWith('--')) {
      index += 1;
    }
  }
}

if (!args.has('input')) {
  console.error('Usage: node scripts/kv-to-d1-r2-dry-run.mjs --input kv-observations.json');
  console.error('');
  console.error('Input format: JSON array of legacy KV observation values, or an object with an observations array.');
  process.exit(1);
}

const inputPath = args.get('input');
const raw = await readFile(inputPath, 'utf8');
const parsed = JSON.parse(raw);
const observations = Array.isArray(parsed) ? parsed : parsed.observations;

if (!Array.isArray(observations)) {
  throw new Error('Input must be an array or { "observations": [...] }.');
}

const report = {
  dryRun: true,
  total: observations.length,
  migratable: 0,
  skipped: 0,
  missingImage: 0,
  invalidImage: 0,
  rows: [],
};

for (const item of observations) {
  const id = safeText(item.id) || `legacy-${report.rows.length + 1}`;
  const deviceId = safeText(item.device_id) || safeText(item.deviceId) || 'legacy-thinklet';
  const clientObservationId = safeText(item.client_observation_id) || id;
  const dataUrl = safeText(item.photoDataUrl);
  const capturedAt = normalizeDate(item.observedAt) || normalizeDate(item.captured_at) || new Date().toISOString();
  const receivedAt = normalizeDate(item.receivedAt) || new Date().toISOString();

  if (!dataUrl?.startsWith('data:image/')) {
    report.skipped += 1;
    report.missingImage += 1;
    report.rows.push({ id, action: 'skip', reason: 'missing_data_url' });
    continue;
  }

  const decoded = decodeDataUrl(dataUrl);
  if (!decoded) {
    report.skipped += 1;
    report.invalidImage += 1;
    report.rows.push({ id, action: 'skip', reason: 'invalid_data_url' });
    continue;
  }

  const extension = decoded.contentType === 'image/webp' ? 'webp' : 'jpg';
  const imageKey = `observations/${deviceId}/${clientObservationId}.${extension}`;
  const imageSha256 = createHash('sha256').update(decoded.bytes).digest('hex');
  report.migratable += 1;
  report.rows.push({
    id,
    action: 'migrate',
    device_id: deviceId,
    client_observation_id: clientObservationId,
    captured_at: capturedAt,
    received_at: receivedAt,
    image_key: imageKey,
    image_sha256: imageSha256,
    bytes: decoded.bytes.length,
  });
}

console.log(JSON.stringify(report, null, 2));

function safeText(value) {
  return typeof value === 'string' && value.trim() ? value.trim() : null;
}

function normalizeDate(value) {
  if (typeof value === 'number') {
    return new Date(value).toISOString();
  }
  if (typeof value === 'string') {
    const parsed = Date.parse(value);
    return Number.isNaN(parsed) ? null : new Date(parsed).toISOString();
  }
  return null;
}

function decodeDataUrl(value) {
  const match = value.match(/^data:(image\/(?:jpeg|webp));base64,(.+)$/);
  if (!match) {
    return null;
  }
  return {
    contentType: match[1],
    bytes: Buffer.from(match[2], 'base64'),
  };
}
