import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const repoRoot = resolve(new URL('../../../../', import.meta.url).pathname);
const sourcePath = resolve(repoRoot, 'itunda__clickable_prototype_v266.html');
const outputPath = resolve(new URL('../public/itunda-prototype-v266.html', import.meta.url).pathname);
let html = readFileSync(sourcePath, 'utf8');

const title = '<title>itunda, clickable prototype v256</title>';
if (!html.includes(title)) throw new Error('Expected v266 source title marker missing.');
html = html.replace(title, '<title>itunda, clickable prototype v266</title>');

const skipHandler = 'function obSkip(){S.auth=true;S.obStep=null;render(\'fade\')}';
if (!html.includes(skipHandler)) throw new Error('Expected prototype-only sign-in bypass handler missing.');
html = html.replace(skipHandler, "function obSkip(){if(new URLSearchParams(window.location.search).get('demo')==='1'){S.auth=true;S.obStep=null;render('fade')}else{alert('Please sign in or create an account to continue.')}}");

const skipLink = /<a href="#" class="sm" style="[^"]*" onclick="obSkip\(\);return false">[^<]*(?:prototype|skip sign-in)[^<]*<\/a>/gi;
const skipMatches = html.match(skipLink) || [];
if (skipMatches.length < 1) throw new Error('Expected prototype-only skip-sign-in link; refusing production output.');
html = html.replace(skipLink, '');

const demoShortcut = /<a href="#" class="sm" style="color:var\(--lk\);font-weight:700;display:inline-block;margin-top:6px" onclick="phDemo\(\);return false">Prototype: fill a returning number<\/a>/;
if (!demoShortcut.test(html)) throw new Error('Prototype phone-fill shortcut not found; refusing production output.');
html = html.replace(demoShortcut, '');

const demoHandler = "window.phDemo=()=>{S.ob.ph='788123456';render('fade-none')};";
if (!html.includes(demoHandler)) throw new Error('Prototype phone-fill handler not found.');
html = html.replace(demoHandler, '');

if (/onclick="obSkip\(\);return false">[^<]*(?:prototype|skip sign-in)/i.test(html) ||
    /onclick="phDemo\(\);return false">Prototype: fill a returning number<\/a>/.test(html) ||
    html.includes(demoHandler) ||
    html.includes("function obSkip(){S.auth=true;S.obStep=null;render('fade')}")) {
  throw new Error('Prototype-only authentication shortcuts remain in production HTML.');
}
const bridge = '<script src="/itunda-backend.js"></script>';
if (html.includes(bridge)) throw new Error('Canonical v266 source unexpectedly already contains the backend bridge.');
if (!html.includes('</body>')) throw new Error('Missing closing body tag.');
html = html.replace('</body>', bridge + '</body>');
writeFileSync(outputPath, html);
process.stdout.write('Generated production v266 entry with prototype auth shortcuts removed and real-auth bridge attached.\n');
