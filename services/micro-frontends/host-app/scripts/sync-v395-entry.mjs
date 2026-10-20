import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const repoRoot = resolve(new URL('../../../../', import.meta.url).pathname);
const sourcePath = resolve(repoRoot, 'v395.html');
const outputPath = resolve(new URL('../public/itunda-prototype-v395.html', import.meta.url).pathname);
const html = readFileSync(sourcePath, 'utf8');

if (!html.includes('<html') || !html.includes('</body>')) {
  throw new Error('Canonical v395 prototype is not a complete HTML document.');
}

writeFileSync(outputPath, html);
process.stdout.write('Synced canonical v395 prototype. Backend integration is intentionally deferred.\n');
