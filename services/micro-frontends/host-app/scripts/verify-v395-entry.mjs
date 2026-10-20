import { readFileSync } from 'node:fs';

const root = new URL('../', import.meta.url);
const read = (path) => readFileSync(new URL(path, root), 'utf8');
const index = read('index.html');
const prototype = read('public/itunda-prototype-v395.html');
const builtPrototype = read('dist/itunda-prototype-v395.html');
const reference = read('../../../v395.html');
const nginx = read('../nginx.conf');

const cacheHeader = 'no-store, no-cache, must-revalidate, max-age=0';
const protectedPaths = ['/', '/index.html', '/itunda-prototype-v395.html'];
const sanitizers = [
  {
    label: 'skip-sign-in shortcut',
    pattern: /<a href="#" class="sm" style="color:var\(--lk\);font-weight:700" onclick="obSkip\(\);return false">Skip sign-in \(prototype shortcut\)<\/a>/g,
    replacement: ''
  },
  {
    label: 'skip-prototype shortcut',
    pattern: /<a href="#" class="sm" style="color:var\(--tt\);font-weight:600" onclick="obSkip\(\);return false">Skip \(prototype\)<\/a>/g,
    replacement: ''
  },
  {
    label: 'phone-fill shortcut',
    pattern: /<a href="#" class="sm" style="color:var\(--lk\);font-weight:700;display:inline-block;margin-top:6px" onclick="phDemo\(\);return false">Prototype: fill a returning number<\/a>/g,
    replacement: ''
  },
  {
    label: 'skip-auth handler',
    pattern: /function obSkip\(\)\{S\.auth=true;S\.obStep=null;render\('fade'\)\}/g,
    replacement: "function obSkip(){alert('Please sign in or create an account to continue.')}"
  },
  {
    label: 'phone-fill handler',
    pattern: /window\.phDemo=\(\)=>\{S\.ob\.ph='788123456';render\('fade-none'\)\};/g,
    replacement: "window.phDemo=()=>{alert('Enter your phone number to continue.')};"
  }
];

let expected = reference;
const sanitizerChecks = [];
for (const item of sanitizers) {
  const matches = expected.match(item.pattern);
  sanitizerChecks.push([item.label + ' exists exactly once in canonical source', Boolean(matches && matches.length === 1)]);
  expected = expected.replace(item.pattern, item.replacement);
}

const checks = [
  ['generated production copy matches the sanitized canonical v395 source', expected === prototype],
  ['built v395 asset exactly matches the verified production copy', builtPrototype === prototype],
  ...sanitizerChecks,
  ['production copy contains no skip-sign-in shortcut', !/Skip sign-in \(prototype shortcut\)|>Skip \(prototype\)<\/a>/.test(prototype)],
  ['production copy cannot set authenticated state through obSkip', !/function obSkip\(\)\{S\.auth=true;/.test(prototype)],
  ['production copy has no hardcoded returning-phone shortcut', !prototype.includes('onclick="phDemo();return false"') && !prototype.includes("S.ob.ph='788123456'")],
  ['customer app route uses v395', index.includes('frame.src = "/itunda-prototype-v395.html"')],
  ['host frame fills the viewport', index.includes('height: 100dvh') && index.includes('width: 100%; height: 100%')],
  ['launch splash respects reduced-motion preferences', /prefers-reduced-motion:\s*reduce/.test(index)],
  ['demo mode is visibly labeled as sample data', index.includes('DEMO MODE · Sample data only')],
  ['v395 keeps dark-mode support', /prefers-color-scheme:dark/.test(prototype)],
  ['v395 keeps reduced-motion support', /prefers-reduced-motion:reduce/.test(prototype)],
  ['v395 keeps English, Kinyarwanda and French strings', /\ben\s*:\s*\{/.test(prototype) && /\brw\s*:\s*\{/.test(prototype) && /\bfr\s*:\s*\{/.test(prototype)],
  ['v395 retains explicit sample-data disclaimers', /No real (?:account|payment|money|loan)|sample data/i.test(prototype)],
  ['Nginx cache rules cover UI entry points', protectedPaths.every((path) => nginx.includes('location = ' + path + ' {'))],
  ['Nginx disables caching on UI entry points', protectedPaths.every((path) => {
    const block = nginx.split('location = ' + path + ' {')[1]?.split(/\n    \}/)[0];
    return Boolean(block && block.includes(cacheHeader) && block.includes('add_header Pragma "no-cache" always;'));
  })]
];
const failed = checks.filter(([, passed]) => !passed);
for (const [label, passed] of checks) process.stdout.write((passed ? 'PASS ' : 'FAIL ') + label + '\n');
if (failed.length) process.exitCode = 1;
