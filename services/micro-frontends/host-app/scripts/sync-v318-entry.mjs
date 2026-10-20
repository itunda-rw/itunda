import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const repoRoot = resolve(new URL('../../../../', import.meta.url).pathname);
const sourcePath = resolve(repoRoot, 'itunda__clickable_prototype_v318.html');
const outputPath = resolve(new URL('../public/itunda-prototype-v318.html', import.meta.url).pathname);
const html = readFileSync(sourcePath, 'utf8');

if (!html.includes('<html') || !html.includes('</body>')) {
  throw new Error('Canonical v318 prototype is not a complete HTML document.');
}

writeFileSync(outputPath, html);
process.stdout.write('Synced canonical v318 prototype for the UI/UX preview; backend integration is intentionally deferred.\n');
