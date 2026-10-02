import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

const dist = new URL('../dist/', import.meta.url);

if (!existsSync(dist)) {
  throw new Error('Production boundary check requires an existing dist/ directory. Run npm run build first.');
}

const forbidden = [
  '@itunda/saronite-devtools',
  'saronite-devtools',
];

function walk(dir) {
  const entries = readdirSync(dir);
  const files = [];
  for (const entry of entries) {
    const path = join(dir, entry);
    const stat = statSync(path);
    if (stat.isDirectory()) files.push(...walk(path));
    else files.push(path);
  }
  return files;
}

const violations = [];

for (const file of walk(dist)) {
  const content = readFileSync(file, 'utf8');
  for (const marker of forbidden) {
    if (content.includes(marker)) {
      violations.push(`${file}: contains ${marker}`);
    }
  }
}

if (violations.length > 0) {
  console.error('Saronite production boundary violated:');
  for (const violation of violations) console.error(`- ${violation}`);
  process.exit(1);
}

console.log('Saronite production boundary passed: devtools are absent from dist/.');
