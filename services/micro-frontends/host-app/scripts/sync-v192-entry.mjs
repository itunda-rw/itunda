import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const repoRoot = resolve(new URL('../../../../', import.meta.url).pathname);
const sourcePath = resolve(repoRoot, 'itunda__clickable_prototype_v192.html');
const outputPath = resolve(new URL('../public/itunda-prototype-v192.html', import.meta.url).pathname);
let html = readFileSync(sourcePath, 'utf8');

const replacements = [
  ['<title>itunda, clickable prototype v153</title>', '<title>itunda, clickable prototype v192</title>'],
  ['onclick="obSkip();return false">Skip (prototype)', 'onclick="obPhoneNext();return false">Sign in'],
];
for (const [from, to] of replacements) {
  if (!html.includes(from)) throw new Error('Expected v192 source marker missing: ' + from);
  html = html.replace(from, to);
}
const demoShortcut = /<a href="#" class="sm" style="color:var\(--lk\);font-weight:700;display:inline-block;margin-top:6px" onclick="phDemo\(\);return false">Prototype: fill a returning number<\/a>/;
if (!demoShortcut.test(html)) throw new Error('Prototype phone-fill shortcut not found; refusing to produce production HTML.');
html = html.replace(demoShortcut, '');
if (html.includes('Skip (prototype)') || html.includes('Prototype: fill a returning number')) {
  throw new Error('Prototype-only authentication shortcuts remain in production HTML.');
}
const bridge = '<script src="/itunda-backend.js"></script>';
if (html.includes(bridge)) throw new Error('Canonical prototype unexpectedly already contains the backend bridge.');
if (!html.includes('</body>')) throw new Error('Missing closing body tag.');
html = html.replace('</body>', bridge + '</body>');
writeFileSync(outputPath, html);
process.stdout.write('Synced canonical v192 prototype to public output with real-auth bridge.\n');
