import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const repoRoot = resolve(new URL('../../../../', import.meta.url).pathname);
const sourcePath = resolve(repoRoot, 'itunda__clickable_prototype_v197.html');
const outputPath = resolve(new URL('../public/itunda-prototype-v197.html', import.meta.url).pathname);
let html = readFileSync(sourcePath, 'utf8');

const replacements = [
  ['<title>itunda, clickable prototype v153</title>', '<title>itunda, clickable prototype v197</title>'],
  ["function obSkip(){S.auth=true;S.obStep=null;render('fade')}", "function obSkip(){alert('Please sign in or create an account to continue.')}"],
];
for (const [from, to] of replacements) {
  if (!html.includes(from)) throw new Error('Expected v197 source marker missing: ' + from);
  html = html.replace(from, to);
}

const skipLink = /<a href="#" class="sm" style="[^"]*" onclick="obSkip\(\);return false">[^<]*prototype[^<]*<\/a>/gi;
const skipMatches = html.match(skipLink) || [];
if (skipMatches.length < 2) throw new Error('Expected both v197 prototype-only skip links; found ' + skipMatches.length);
html = html.replace(skipLink, '');

const demoShortcut = /<a href="#" class="sm" style="color:var\(--lk\);font-weight:700;display:inline-block;margin-top:6px" onclick="phDemo\(\);return false">Prototype: fill a returning number<\/a>/;
if (!demoShortcut.test(html)) throw new Error('Prototype phone-fill shortcut not found; refusing to produce production HTML.');
html = html.replace(demoShortcut, '');
const demoHandler = "window.phDemo=()=>{S.ob.ph='788123456';render('fade-none')};";
if (!html.includes(demoHandler)) throw new Error('Prototype phone-fill handler not found.');
html = html.replace(demoHandler, '');

if (/onclick="obSkip\(\);return false">[^<]*prototype/i.test(html) ||
    html.includes('Prototype: fill a returning number') ||
    html.includes("window.phDemo=()=>{S.ob.ph='788123456';render('fade-none')};")) {
  throw new Error('Prototype-only authentication shortcuts remain in production HTML.');
}
const bridge = '<script src="/itunda-backend.js"></script>';
if (html.includes(bridge)) throw new Error('Canonical v197 prototype unexpectedly already contains the backend bridge.');
if (!html.includes('</body>')) throw new Error('Missing closing body tag.');
html = html.replace('</body>', bridge + '</body>');
writeFileSync(outputPath, html);
process.stdout.write('Synced canonical v197 prototype to public output with real-auth bridge.\\n');
