import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';

const validator = resolve('validate-manifest.mjs');
const validManifest = {
  manifestVersion: 1,
  id: 'rw.example.partner',
  name: 'Partner Example',
  version: '0.1.0',
  category: 'PRODUCTIVITY',
  description: 'Example Itunda mini-app.',
  entry: { type: 'saronite', bundleUrl: 'https://example.com/mini-app.bundle.js' },
  icon: { url: 'https://example.com/icon.png' },
  permissions: ['navigation'],
  brandTheme: {
    themeId: 'partner-example',
    light: { brand: '#4F46E5', brandStrong: '#4338CA', brandSurface: '#EEF2FF', onBrand: '#FFFFFF', focus: '#4F46E5', pressed: '#4338CA' },
    dark: { brand: '#A5B4FC', brandStrong: '#C7D2FE', brandSurface: '#312E81', onBrand: '#111827', focus: '#C7D2FE', pressed: '#A5B4FC' },
  },
};

function runValidator(manifest) {
  const dir = mkdtempSync(join(tmpdir(), 'saronite-manifest-'));
  const file = join(dir, 'manifest.json');
  try {
    writeFileSync(file, JSON.stringify(manifest));
    return spawnSync(process.execPath, [validator, file], { cwd: process.cwd(), encoding: 'utf8' });
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

test('accepts a valid partner manifest', () => {
  const result = runValidator(validManifest);
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /Saronite manifest valid/);
});

test('rejects an unsupported category', () => {
  const result = runValidator({ ...validManifest, category: 'TELEPORT' });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /category must be/);
});

test('rejects duplicate permissions', () => {
  const result = runValidator({ ...validManifest, permissions: ['navigation', 'navigation'] });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /permissions must be a unique array/);
});

test('rejects unsupported permissions', () => {
  const result = runValidator({ ...validManifest, permissions: ['teleport'] });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /unsupported permission/);
});

test('rejects non-HTTPS bundle URLs', () => {
  const result = runValidator({ ...validManifest, entry: { ...validManifest.entry, bundleUrl: 'http://example.com/bundle.js' } });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /bundleUrl must be an HTTPS URL/);
});

test('rejects invalid brand palette colors', () => {
  const result = runValidator({
    ...validManifest,
    brandTheme: { ...validManifest.brandTheme, light: { ...validManifest.brandTheme.light, brand: 'blue' } },
  });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /must be a six-digit hex color/);
});
