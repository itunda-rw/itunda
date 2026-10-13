#!/usr/bin/env node
import { readFileSync } from 'node:fs';

const fail = (message) => {
  console.error(`Invalid Saronite manifest: ${message}`);
  process.exitCode = 1;
};
const isRecord = (value) => value !== null && typeof value === 'object' && !Array.isArray(value);
const requiredString = (object, key, label = key, maxLength = Infinity) => {
  const value = object?.[key];
  if (typeof value !== 'string' || value.trim() === '') {
    fail(`${label} must be a non-empty string`);
    return false;
  }
  if (value.length > maxLength) fail(`${label} must be at most ${maxLength} characters`);
  return true;
};
const exactKeys = (object, allowed, label) => {
  for (const key of Object.keys(object)) {
    if (!allowed.includes(key) && key !== '$schema') fail(`${label} contains unsupported field "${key}"`);
  }
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
  console.error(`Invalid Saronite manifest: cannot read valid JSON (${error.message})`);
  process.exit(1);
}

if (!isRecord(manifest)) {
  fail('root must be an object');
  process.exit(1);
}

exactKeys(manifest, ['manifestVersion', 'id', 'name', 'version', 'category', 'description', 'entry', 'icon', 'permissions', 'brandTheme'], 'manifest');
if (manifest.manifestVersion !== 1) fail('manifestVersion must be 1');
if (typeof manifest.id === 'string' && !/^[a-z0-9]+(?:[.-][a-z0-9]+)*$/.test(manifest.id)) fail('id has invalid format');
requiredString(manifest, 'id');
requiredString(manifest, 'name', 'name', 80);
if (typeof manifest.version !== 'string' || !/^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$/.test(manifest.version)) fail('version must be semantic version x.y.z');
if (!['FINANCE', 'SHOPPING', 'PRODUCTIVITY', 'LIFESTYLE', 'OTHER'].includes(manifest.category)) fail('category must be FINANCE, SHOPPING, PRODUCTIVITY, LIFESTYLE, or OTHER');
requiredString(manifest, 'description', 'description', 240);

if (!isRecord(manifest.entry)) {
  fail('entry must be an object');
} else {
  exactKeys(manifest.entry, ['type', 'bundleUrl'], 'entry');
  if (manifest.entry.type !== 'saronite') fail('entry.type must be saronite');
  if (typeof manifest.entry.bundleUrl !== 'string' || !/^https:\/\//.test(manifest.entry.bundleUrl)) fail('entry.bundleUrl must be an HTTPS URL');
  else {
    try { new URL(manifest.entry.bundleUrl); } catch { fail('entry.bundleUrl must be a valid URL'); }
  }
}

if (!isRecord(manifest.icon)) {
  fail('icon must be an object');
} else {
  exactKeys(manifest.icon, ['url'], 'icon');
  if (typeof manifest.icon.url !== 'string' || !/^https:\/\//.test(manifest.icon.url)) fail('icon.url must be an HTTPS URL');
  else {
    try { new URL(manifest.icon.url); } catch { fail('icon.url must be a valid URL'); }
  }
}

const allowedPermissions = new Set(['identity', 'navigation', 'share', 'storage', 'notifications', 'payments', 'location', 'camera', 'contacts']);
if (!Array.isArray(manifest.permissions)) {
  fail('permissions must be an array');
} else {
  if (new Set(manifest.permissions).size !== manifest.permissions.length) fail('permissions must be a unique array');
  for (const permission of manifest.permissions) {
    if (!allowedPermissions.has(permission)) fail(`unsupported permission "${permission}"`);
  }
}

if (manifest.brandTheme !== undefined) {
  if (!isRecord(manifest.brandTheme)) {
    fail('brandTheme must be an object');
  } else {
    exactKeys(manifest.brandTheme, ['themeId', 'light', 'dark'], 'brandTheme');
    requiredString(manifest.brandTheme, 'themeId', 'brandTheme.themeId', 64);
    for (const mode of ['light', 'dark']) {
      const palette = manifest.brandTheme[mode];
      if (!isRecord(palette)) {
        fail(`brandTheme.${mode} must be an object`);
        continue;
      }
      const keys = ['brand', 'brandStrong', 'brandSurface', 'onBrand', 'focus', 'pressed'];
      exactKeys(palette, keys, `brandTheme.${mode}`);
      for (const key of keys) {
        if (typeof palette[key] !== 'string' || !/^#[0-9A-Fa-f]{6}$/.test(palette[key])) {
          fail(`brandTheme.${mode}.${key} must be a six-digit hex color`);
        }
      }
    }
  }
}

if (process.exitCode) process.exit(process.exitCode);
console.log('Saronite manifest valid');
