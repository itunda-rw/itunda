import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';

const validator = resolve('validate-manifest.mjs');
const validManifest = {
  schemaVersion: '1',
  id: 'rw.itunda.wallet-balance',
  name: 'Wallet Balance',
  version: '1.0.0',
  entry: 'pages/index.tsx',
  capabilities: ['auth', 'navigation'],
  protocolVersion: '1',
  permissions: [],
  release: {
    channel: 'sandbox',
    bundleSha256: 'a'.repeat(64),
    rolloutPercent: 10,
  },
};

function runValidator(manifest) {
  const dir = mkdtempSync(join(tmpdir(), 'saronite-manifest-'));
  const file = join(dir, 'manifest.json');
  try {
    writeFileSync(file, JSON.stringify(manifest));
    return spawnSync(process.execPath, [validator, file], {
      cwd: process.cwd(),
      encoding: 'utf8',
    });
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

test('accepts a valid sandbox manifest', () => {
  const result = runValidator(validManifest);
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /Saronite manifest valid/);
});

test('rejects an unsupported capability', () => {
  const result = runValidator({ ...validManifest, capabilities: ['teleport'] });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /unsupported capability/);
});

test('rejects duplicate capabilities', () => {
  const result = runValidator({ ...validManifest, capabilities: ['auth', 'auth'] });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /unique array/);
});

test('rejects permissions without the matching capability', () => {
  const result = runValidator({ ...validManifest, permissions: ['location'] });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /requires capability location/);
});

test('rejects an invalid release digest', () => {
  const result = runValidator({
    ...validManifest,
    release: { ...validManifest.release, bundleSha256: 'not-a-digest' },
  });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /bundleSha256 must be SHA-256/);
});

test('rejects rollout percentages outside 0..100', () => {
  const result = runValidator({
    ...validManifest,
    release: { ...validManifest.release, rolloutPercent: 101 },
  });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /rolloutPercent must be 0\.\.100/);
});
