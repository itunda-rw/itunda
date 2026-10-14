#!/usr/bin/env node
import { readFile, stat } from 'node:fs/promises';
import { resolve } from 'node:path';
import { isReleaseReady, validateMiniAppManifest } from '../src/release-check.ts';

const [manifestArg, bundleArg, budgetArg] = process.argv.slice(2);
if (!manifestArg || !bundleArg || !budgetArg) {
  console.error('Usage: node --experimental-strip-types scripts/release-audit.mjs <manifest.json> <bundle-file> <max-bytes>');
  process.exit(2);
}
const maxBytes = Number(budgetArg);
if (!Number.isSafeInteger(maxBytes) || maxBytes <= 0) {
  console.error('max-bytes must be a positive safe integer');
  process.exit(2);
}

let manifest;
try {
  manifest = JSON.parse(await readFile(resolve(manifestArg), 'utf8'));
} catch (error) {
  console.error(`Unable to read/parse manifest: ${error instanceof Error ? error.message : String(error)}`);
  process.exit(2);
}

if (!manifest || typeof manifest !== 'object' || Array.isArray(manifest)) {
  console.error('Manifest must be a JSON object.');
  process.exit(2);
}
if (manifest.bundle !== undefined && (!manifest.bundle || typeof manifest.bundle !== 'object' || Array.isArray(manifest.bundle))) {
  console.error('Manifest bundle metadata must be an object when present.');
  process.exit(2);
}

let bundleStat;
try {
  bundleStat = await stat(resolve(bundleArg));
  if (!bundleStat.isFile()) {
    console.error('Bundle path must point to a regular file.');
    process.exit(2);
  }
} catch (error) {
  console.error(`Unable to stat bundle: ${error instanceof Error ? error.message : String(error)}`);
  process.exit(2);
}
const entryBytes = bundleStat.size;

const findings = validateMiniAppManifest({
  ...manifest,
  bundle: { ...(manifest.bundle ?? {}), maxBytes, entryBytes },
});
for (const finding of findings) {
  const line = `[${finding.severity.toUpperCase()}] ${finding.code}: ${finding.message}`;
  (finding.severity === 'error' ? console.error : console.log)(line);
}
console.log(`Measured bundle: ${entryBytes} bytes / budget: ${maxBytes} bytes`);
if (!isReleaseReady(findings)) process.exit(1);
console.log('Release manifest and configured bundle-size gate passed.');
