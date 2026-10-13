#!/usr/bin/env node
import { readFileSync } from 'node:fs';

const fail = (message) => {
  console.error(`Invalid Saronite manifest: ${message}`);
  process.exitCode = 1;
};

const [manifestPath] = process.argv.slice(2);
if (!manifestPath) {
  console.error('Usage: node validate-manifest.mjs <manifest.json>');
  process.exit(2);
}

let manifest;
try {
  manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
} catch (error) {
  fail(`cannot read valid JSON (${error.message})`);
  process.exit(1);
}

const isRecord = (value) => value !== null && typeof value === 'object' && !Array.isArray(value);
const requiredString = (object, key, label = key) => {
  if (typeof object?.[key] !== 'string' || object[key].trim() === '') fail(`${label} must be a non-empty string`);
};
const uniqueStringArray = (value, label) => {
  if (!Array.isArray(value) || value.some((item) => typeof item !== 'string' || item.trim() === '')) {
    fail(`${label} must be an array of non-empty strings`);
    return false;
  }
  if (new Set(value).size !== value.length) fail(`${label} must be a unique array`);
  return true;
};

if (!isRecord(manifest)) {
  fail('root must be an object');
  process.exit(1);
}

if (manifest.schemaVersion !== '1') fail('schemaVersion must be "1"');
for (const key of ['id', 'name', 'version', 'entry', 'protocolVersion']) requiredString(manifest, key);
if (typeof manifest.id === 'string' && !/^[a-z0-9]+(?:[.-][a-z0-9]+)*$/.test(manifest.id)) {
  fail('id must use lowercase letters, digits, dots, or hyphens');
}
if (typeof manifest.entry === 'string' && (manifest.entry.startsWith('/') || manifest.entry.split('/').includes('..'))) {
  fail('entry must be a relative path without parent traversal');
}

const allowedCapabilities = new Set([
  'auth', 'navigation', 'location', 'contacts', 'camera', 'clipboard',
  'share', 'haptics', 'storage', 'network', 'locale', 'wallet', 'payments',
]);
const capabilitiesValid = uniqueStringArray(manifest.capabilities, 'capabilities');
if (capabilitiesValid) {
  for (const capability of manifest.capabilities) {
    if (!allowedCapabilities.has(capability)) fail(`unsupported capability "${capability}"`);
  }
}

const permissionsValid = uniqueStringArray(manifest.permissions, 'permissions');
if (permissionsValid && capabilitiesValid) {
  const permissionCapability = {
    location: 'location',
    contacts: 'contacts',
    camera: 'camera',
    clipboard: 'clipboard',
    share: 'share',
    storage: 'storage',
  };
  for (const permission of manifest.permissions) {
    const capability = permissionCapability[permission];
    if (!capability) fail(`unsupported permission "${permission}"`);
    else if (!manifest.capabilities.includes(capability)) fail(`permission "${permission}" requires capability ${capability}`);
  }
}

if (!isRecord(manifest.release)) {
  fail('release must be an object');
} else {
  requiredString(manifest.release, 'channel', 'release.channel');
  if (!['sandbox', 'staging', 'production'].includes(manifest.release.channel)) {
    fail('release.channel must be sandbox, staging, or production');
  }
  if (Object.hasOwn(manifest.release, 'bundleSha256') &&
      (typeof manifest.release.bundleSha256 !== 'string' || !/^[a-f0-9]{64}$/i.test(manifest.release.bundleSha256))) {
    fail('release.bundleSha256 must be SHA-256 (64 hexadecimal characters)');
  }
  if (typeof manifest.release.rolloutPercent !== 'number' ||
      !Number.isInteger(manifest.release.rolloutPercent) ||
      manifest.release.rolloutPercent < 0 || manifest.release.rolloutPercent > 100) {
    fail('release.rolloutPercent must be 0..100');
  }
}

if (process.exitCode) process.exit(process.exitCode);
console.log('Saronite manifest valid');
