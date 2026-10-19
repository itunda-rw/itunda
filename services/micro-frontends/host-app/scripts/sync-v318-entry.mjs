import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const repoRoot = resolve(new URL('../../../../', import.meta.url).pathname);
const sourcePath = resolve(repoRoot, 'itunda__clickable_prototype_v318.html');
const outputPath = resolve(new URL('../public/itunda-prototype-v318.html', import.meta.url).pathname);
const bridge = '<script src="/itunda-backend.js"></script>';
let html = readFileSync(sourcePath, 'utf8');

if (!html.includes('<html') || !html.includes('</body>')) {
  throw new Error('Canonical v318 prototype is not a complete HTML document.');
}
if ((html.match(/<script src=["']\/itunda-backend\.js["']/g) || []).length > 0) {
  throw new Error('Canonical v318 already contains the backend bridge; review the source before syncing.');
}
if (!html.includes('function render') && !html.includes('render = function')) {
  throw new Error('Cannot confirm v318 exposes the render function required by the backend bridge.');
}
if (!html.includes('screens.bank')) {
  throw new Error('Cannot confirm v318 exposes the bank screen hook required by the backend bridge.');
}

html = html.replace('</body>', bridge + '</body>');
writeFileSync(outputPath, html);
process.stdout.write('Synced canonical v318 with the real-auth/backend bridge attached.\n');
