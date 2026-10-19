import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const repoRoot = resolve(new URL('../../../../', import.meta.url).pathname);
const sourcePath = resolve(repoRoot, 'itunda__clickable_prototype_v318.html');
const outputPath = resolve(new URL('../public/itunda-prototype-v318.html', import.meta.url).pathname);
let html = readFileSync(sourcePath, 'utf8');

const skipHandler = "function obSkip(){S.auth=true;S.obStep=null;render('fade')}";
if (!html.includes(skipHandler)) throw new Error('Expected prototype-only sign-in bypass was not found.');
html = html.replace(skipHandler, "function obSkip(){alert('Please sign in or create an account to continue.')}");

const skipLink = /<a href="#" class="sm" style="[^"]*" onclick="obSkip\(\);return false">[^<]*prototype[^<]*<\/a>/gi;
const skipMatches = html.match(skipLink) || [];
if (skipMatches.length < 2) throw new Error('Expected both prototype-only skip-sign-in links; found ' + skipMatches.length);
html = html.replace(skipLink, '');

const demoShortcut = /<a href="#" class="sm" style="color:var\(--lk\);font-weight:700;display:inline-block;margin-top:6px" onclick="phDemo\(\);return false">Prototype: fill a returning number<\/a>/;
if (!demoShortcut.test(html)) throw new Error('Prototype phone-fill shortcut not found; refusing to produce production HTML.');
html = html.replace(demoShortcut, '');
const demoHandler = "window.phDemo=()=>{S.ob.ph='788123456';render('fade-none')};";
if (!html.includes(demoHandler)) throw new Error('Prototype phone-fill handler not found.');
html = html.replace(demoHandler, '');

if (/onclick="obSkip\(\);return false">[^<]*prototype/i.test(html) ||
    html.includes('Prototype: fill a returning number') ||
    html.includes(demoHandler)) {
  throw new Error('Prototype-only authentication shortcuts remain in production HTML.');
}
if (html.includes('<script src="/itunda-backend.js"></script>')) {
  throw new Error('Canonical v318 prototype unexpectedly already contains the backend bridge.');
}
if (!html.includes('</body>')) throw new Error('Missing closing body tag.');
html = html.replace('</body>', '<script src="/itunda-backend.js"></script><script>document.title = "itunda, clickable prototype v318";</script></body>');
writeFileSync(outputPath, html);
process.stdout.write('Synced canonical v318 prototype to production output with real-auth bridge.\n');
