import { readdir, readFile } from 'node:fs/promises';
import { join, relative } from 'node:path';

const root = process.cwd();
const targets = [
  'services/micro-frontends',
  'packages/design-system-web/src',
];
const extensions = new Set(['.ts', '.tsx', '.css']);
const banned = [
  /#[0-9a-fA-F]{3,8}\b/g,
  /rgba?\(/g,
  /hsla?\(/g,
];
const allow = new Set([
  'packages/design-system-web/src/styles.css',
  'packages/design-tokens',
]);

async function walk(dir) {
  const entries = await readdir(dir, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    if (entry.name === 'node_modules' || entry.name.startsWith('.')) continue;
    const path = join(dir, entry.name);
    if (entry.isDirectory()) files.push(...await walk(path));
    else if (extensions.has(path.slice(path.lastIndexOf('.')))) files.push(path);
  }
  return files;
}

const violations = [];
for (const target of targets) {
  for (const file of await walk(join(root, target))) {
    const rel = relative(root, file).replaceAll('\\', '/');
    if ([...allow].some(prefix => rel === prefix || rel.startsWith(prefix + '/'))) continue;
    const source = await readFile(file, 'utf8');
    for (const pattern of banned) {
      if (pattern.test(source)) {
        violations.push(rel);
        break;
      }
    }
  }
}

if (violations.length) {
  console.error('IDS audit failed: raw color values/functions found outside token implementation layers.');
  for (const file of [...new Set(violations)].sort()) console.error(' - ' + file);
  process.exit(1);
}
console.log('IDS audit passed: product Web code contains no raw color values.');
