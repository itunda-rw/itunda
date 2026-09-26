import { createHash } from 'node:crypto';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';

const input = process.argv[2] || '/tmp/ids-visual-matrix.json';
const baselinePath = process.argv[3] || 'design-system/components/visual-baseline.json';
const matrix = JSON.parse(readFileSync(input, 'utf8'));
const files = matrix.cases.map(c => ({
  key: [c.component,c.state,c.theme,c.scale,c.viewport,c.motion].join('|'),
  file: '/tmp/ids-visual-matrix/' + c.filename + '.png',
}));

const hash = file => createHash('sha256').update(readFileSync(file)).digest('hex');
const current = Object.fromEntries(files.map(({key,file}) => [key,hash(file)]));
const baselineExists = existsSync(baselinePath);
const baseline = baselineExists ? JSON.parse(readFileSync(baselinePath,'utf8')) : null;

if (!baselineExists) {
  const payload = {
    version: '1.0.0',
    generatedFrom: matrix.source,
    note: 'Deterministic visual evidence fingerprints. Regenerate intentionally when the design baseline changes.',
    cases: current,
  };
  writeFileSync(baselinePath, JSON.stringify(payload, null, 2) + '\n');
  console.log('IDS visual baseline initialized with', Object.keys(current).length, 'cases.');
  console.log('This first run records the current branch as the baseline; subsequent runs are regression-gated.');
  process.exit(0);
}

const expected = baseline.cases || {};
const failures = [];
for (const [key, value] of Object.entries(current)) {
  if (!(key in expected)) failures.push('new visual case: ' + key);
  else if (expected[key] !== value) failures.push('visual fingerprint changed: ' + key);
}
for (const key of Object.keys(expected)) {
  if (!(key in current)) failures.push('visual case removed: ' + key);
}

if (failures.length) {
  console.error('IDS visual regression failed.');
  for (const failure of failures) console.error(' - ' + failure);
  console.error('If this change is intentional, regenerate the baseline in a controlled baseline-update change.');
  process.exit(1);
}
console.log('IDS visual regression passed:', Object.keys(current).length, 'cases unchanged.');
