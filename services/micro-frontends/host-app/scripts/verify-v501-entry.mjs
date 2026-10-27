import { readFileSync } from 'node:fs';
const root = new URL('../', import.meta.url);
const read = (path) => readFileSync(new URL(path, root), 'utf8');
const canonical = read('../../../v501.html');
const production = read('public/itunda-prototype-v501.html');
const built = read('dist/itunda-prototype-v501.html');
const index = read('index.html');
const nginx = read('../nginx.conf');
const cacheHeader = 'no-store, no-cache, must-revalidate, max-age=0';
const protectedPaths = ['/', '/index.html', '/itunda-prototype-v501.html'];

const sanitizers = [
  ['skip-sign-in shortcut', '<a href="#" class="sm" style="color:var(--lk);font-weight:700" onclick="obSkip();return false">Skip sign-in (prototype shortcut)</a>', ''],
  ['skip-prototype shortcut', '<a href="#" class="sm" style="color:var(--tt);font-weight:600" onclick="obSkip();return false">Skip (prototype)</a>', ''],
  ['phone-fill shortcut', '<a href="#" class="sm" style="color:var(--lk);font-weight:700;display:inline-block;margin-top:6px" onclick="phDemo();return false">Prototype: fill a returning number</a>', ''],
  ['skip-auth handler', "function obSkip(){S.auth=true;S.obStep=null;render('fade')}", "function obSkip(){alert('Please sign in or create an account to continue.')}"],
  ['phone-fill handler', "window.phDemo=()=>{S.ob.ph='788123456';render('fade-none')};", "window.phDemo=()=>{alert('Enter your phone number to continue.')};"]
];
let expected = canonical;
const checks = [];
for (const [label, needle, replacement] of sanitizers) {
  const count = expected.split(needle).length - 1;
  checks.push([label + ' exists exactly once in canonical source', count === 1]);
  expected = expected.replace(needle, replacement);
}
checks.push(
  ['production copy exactly matches sanitized v501 source', expected === production],
  ['built v501 asset exactly matches verified production copy', built === production],
  ['host points at v501', index.includes('frame.src = "/itunda-prototype-v501.html"')],
  ['host iframe is labelled v501', index.includes('Itunda consumer app v501')],
  ['production copy has no sign-in bypass link', !production.includes('Skip sign-in (prototype shortcut)') && !production.includes('>Skip (prototype)</a>')],
  ['production copy has no hardcoded phone shortcut', !production.includes('onclick="phDemo();return false"') && !production.includes("S.ob.ph='788123456'")],
  ['production copy keeps English, Kinyarwanda and French', production.includes('en:{') && production.includes('rw:{') && production.includes('fr:{')],
  ['production copy keeps dark-mode support', production.includes('prefers-color-scheme:dark')],
  ['production copy keeps reduced-motion support', production.includes('prefers-reduced-motion:reduce')],
  ['Nginx cache rules cover all UI entry points', protectedPaths.every((path) => nginx.includes('location = ' + path + ' {'))],
  ['Nginx disables caching on all UI entry points', protectedPaths.every((path) => {
    const block = nginx.split('location = ' + path + ' {')[1]?.split('\n    }')[0];
    return Boolean(block && block.includes(cacheHeader) && block.includes('add_header Pragma "no-cache" always;'));
  })]
);
const failed = checks.filter(([, ok]) => !ok);
for (const [label, ok] of checks) process.stdout.write((ok ? 'PASS ' : 'FAIL ') + label + '\n');
if (failed.length) process.exitCode = 1;
