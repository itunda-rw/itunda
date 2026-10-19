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

// Convert the prototype-only "Skip" affordance into the real phone sign-in action.
const skipLabel = 'onclick="obSkip();return false">Skip (prototype)';
const signInLabel = 'onclick="obPhoneNext();return false">Continue';
const skipCount = html.split(skipLabel).length - 1;
if (skipCount < 1) {
  throw new Error('Expected v318 prototype skip label not found; refusing to ship a sign-in bypass affordance.');
}
html = html.split(skipLabel).join(signInLabel);

// Remove the prefilled sample phone shortcut and its handler from the production entry.
const demoShortcut = /<a href="#" class="sm" style="color:var\(--lk\);font-weight:700;display:inline-block;margin-top:6px" onclick="phDemo\(\);return false">Prototype: fill a returning number<\/a>/;
if (!demoShortcut.test(html)) {
  throw new Error('Prototype phone-fill shortcut not found; refusing to ship sample credentials UI.');
}
html = html.replace(demoShortcut, '');
const demoHandler = "window.phDemo=()=>{S.ob.ph='788123456';render('fade-none')};";
if (!html.includes(demoHandler)) {
  throw new Error('Prototype phone-fill handler not found; refusing to ship sample credentials behavior.');
}
html = html.replace(demoHandler, '');

if (html.includes(skipLabel) ||
    /onclick="obSkip\(\);return false"/i.test(html) ||
    demoShortcut.test(html) ||
    html.includes(demoHandler) ||
    /onclick="phDemo\(\);return false"/i.test(html)) {
  throw new Error('Prototype-only authentication shortcuts remain in production HTML.');
}
html = html.replace('</body>', bridge + '</body>');
writeFileSync(outputPath, html);
process.stdout.write('Synced canonical v318 with real auth bridge; removed prototype-only auth shortcuts.\n');
