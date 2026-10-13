#!/usr/bin/env node
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const root = process.cwd();
const hostPath = resolve(root, 'host-app/package.json');
const lockPath = resolve(root, 'package-lock.json');
const host = JSON.parse(await readFile(hostPath, 'utf8'));
const lock = JSON.parse(await readFile(lockPath, 'utf8'));

if (lock.lockfileVersion !== 3 || !lock.packages) {
  throw new Error('Expected npm lockfile v3 with a packages map.');
}

const critical = [
  '@granite-js/brownfield-module',
  '@granite-js/native',
  '@granite-js/react-native',
  'react',
  'react-native',
];
const inventory = [];
const errors = [];

for (const name of critical) {
  const declared = host.dependencies?.[name];
  if (!declared) {
    errors.push(`host-app/package.json is missing direct dependency ${name}`);
    continue;
  }
  const lockKey = `node_modules/${name}`;
  const resolvedPackage = lock.packages[lockKey];
  if (!resolvedPackage?.version) {
    errors.push(`package-lock.json has no resolved package for ${name} at ${lockKey}`);
    continue;
  }
  if (declared !== resolvedPackage.version) {
    errors.push(`${name}: host declares ${declared}, lock resolves ${resolvedPackage.version}; pin exact host versions to keep runtime reproducible`);
  }
  const copies = Object.entries(lock.packages)
    .filter(([path, value]) => (path === lockKey || path.endsWith(`/node_modules/${name}`)) && value?.version)
    .map(([path, value]) => ({ path, version: value.version }));
  const versions = [...new Set(copies.map((copy) => copy.version))];
  if (versions.length !== 1) {
    errors.push(`${name} has multiple locked versions: ${versions.join(', ')}`);
  }
  inventory.push({ name, declared, resolved: resolvedPackage.version, copies });
}

const graniteVersions = inventory
  .filter(({ name }) => name.startsWith('@granite-js/'))
  .map(({ resolved }) => resolved);
if (graniteVersions.length === 3 && new Set(graniteVersions).size !== 1) {
  errors.push(`Granite packages are not version-aligned: ${graniteVersions.join(', ')}`);
}

console.log(JSON.stringify({
  schemaVersion: 1,
  generatedAt: new Date().toISOString(),
  hostPackage: host.name,
  lockfileVersion: lock.lockfileVersion,
  runtime: inventory,
  valid: errors.length === 0,
  errors,
}, null, 2));

if (errors.length) process.exitCode = 1;
