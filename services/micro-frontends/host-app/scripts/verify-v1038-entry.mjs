import { readFileSync } from 'node:fs';
const root = new URL('../', import.meta.url);
const read = (path) => readFileSync(new URL(path, root), 'utf8');
const reference = read('../../../itunda_clickable_prototype_v1038.html');
const production = read('public/itunda-prototype-v1038.html');
const index = read('index.html');
const checks = [
  ['production prototype is byte-for-byte the canonical v1038 file', production === reference],
  ['web host points at the v1038 prototype', index.includes('/itunda-prototype-v1038.html')],
  ['web host labels the v1038 experience', index.includes('v1038')],
];
const failed = checks.filter(([, ok]) => !ok);
for (const [label, ok] of checks) process.stdout.write((ok ? 'PASS ' : 'FAIL ') + label + '\n');
if (failed.length) process.exitCode = 1;
