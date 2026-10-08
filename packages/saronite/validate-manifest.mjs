#!/usr/bin/env node
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const file = process.argv[2] || 'manifest.json';
const manifest = JSON.parse(await readFile(resolve(file), 'utf8'));
const fail = (message) => { console.error(`Saronite manifest invalid: ${message}`); process.exit(1); };

if (manifest.schemaVersion !== '1') fail('schemaVersion must be "1"');
if (!/^[a-z][a-z0-9._-]{2,63}$/.test(manifest.id || '')) fail('id is invalid');
if (!manifest.name || manifest.name.length > 80) fail('name is required and must be <= 80 characters');
if (!/^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$/.test(manifest.version || '')) fail('version must be semantic-version shaped');
if (!manifest.entry) fail('entry is required');
if (!Array.isArray(manifest.capabilities) || new Set(manifest.capabilities).size !== manifest.capabilities.length) fail('capabilities must be a unique array');

const capabilities = new Set(['auth','navigation','environment','permissions','storage','location','camera','contacts','clipboard','haptics','payment','notification','analytics','partner','events','game']);
for (const capability of manifest.capabilities) if (!capabilities.has(capability)) fail(`unsupported capability: ${capability}`);

if (manifest.protocolVersion && manifest.protocolVersion !== '1') fail('unsupported protocolVersion');
if (manifest.permissions) {
  if (!Array.isArray(manifest.permissions)) fail('permissions must be an array');
  const allowed = new Set(['location','camera','contacts','notifications','clipboard']);
  for (const permission of manifest.permissions) if (!allowed.has(permission)) fail(`unsupported permission: ${permission}`);
}
if (manifest.release) {
  const channels = new Set(['development','preview','sandbox','beta','production']);
  if (!channels.has(manifest.release.channel)) fail('invalid release channel');
  if (manifest.release.bundleSha256 && !/^[A-Fa-f0-9]{64}$/.test(manifest.release.bundleSha256)) fail('bundleSha256 must be SHA-256');
  if (manifest.release.rolloutPercent !== undefined && (!Number.isInteger(manifest.release.rolloutPercent) || manifest.release.rolloutPercent < 0 || manifest.release.rolloutPercent > 100)) fail('rolloutPercent must be 0..100');
}

console.log(`Saronite manifest valid: ${manifest.id}@${manifest.version}`);
