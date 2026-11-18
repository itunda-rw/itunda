import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const repoRoot = resolve(new URL('../../../../', import.meta.url).pathname);
const sourcePath = resolve(repoRoot, 'itunda_clickable_prototype_v1038.html');
const outputPath = resolve(new URL('../public/itunda-prototype-v1038.html', import.meta.url).pathname);
const html = readFileSync(sourcePath, 'utf8');
if (!html.includes('<html') || !html.includes('</body>')) {
  throw new Error('Canonical v1038 prototype is not a complete HTML document.');
}
mkdirSync(resolve(outputPath, '..'), { recursive: true });
writeFileSync(outputPath, html);
process.stdout.write('Copied canonical v1038 prototype to the web production asset.\n');
