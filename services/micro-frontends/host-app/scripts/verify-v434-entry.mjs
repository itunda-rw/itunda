import { readFileSync } from 'node:fs';
const root = new URL('../', import.meta.url);
const read = (path) => readFileSync(new URL(path, root), 'utf8');
const reference = read('../../../itunda__clickable_prototype_v434.html');
const production = read('public/itunda-prototype-v434.html');
const index = read('index.html');
const checks = [
  ['production prototype is byte-for-byte the canonical v434 file', production === reference],
  ['web host points at the v434 prototype', index.includes('/itunda-prototype-v434.html')],
  ['web host labels the v434 experience', index.includes('v434')],
];
const failed = checks.filter(([, ok]) => !ok);
for (const [label, ok] of checks) process.stdout.write((ok ? 'PASS ' : 'FAIL ') + label + '\n');
if (failed.length) process.exitCode = 1;
