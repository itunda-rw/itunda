import { readFileSync } from 'node:fs';

const root = new URL('../', import.meta.url);
const read = (path) => readFileSync(new URL(path, root), 'utf8');
const index = read('index.html');
const prototype = read('public/itunda-prototype-v318.html');
const reference = read('../../../itunda__clickable_prototype_v318.html');
const backend = read('public/itunda-backend.js');
const nginx = read('../nginx.conf');
const cacheHeader = 'no-store, no-cache, must-revalidate, max-age=0';
const bridge = '<script src="/itunda-backend.js"></script>';
const protectedPaths = ['/', '/index.html', '/itunda-prototype-v318.html', '/itunda-backend.js'];
const skipLabel = 'onclick="obSkip();return false">Skip (prototype)';
const signInLabel = 'onclick="obPhoneNext();return false">Continue';
const demoShortcut = /<a href="#" class="sm" style="color:var\(--lk\);font-weight:700;display:inline-block;margin-top:6px" onclick="phDemo\(\);return false">Prototype: fill a returning number<\/a>/;
const demoHandler = "window.phDemo=()=>{S.ob.ph='788123456';render('fade-none')};";
const productionEquivalent = (html) => html
  .replace(/\r?\n/g, '')
  .split(skipLabel).join(signInLabel)
  .replace(demoShortcut, '')
  .replace(demoHandler, '')
  .replace(bridge, '');

const checks = [
  ['generated v318 matches canonical except real-auth production transformations', productionEquivalent(prototype) === productionEquivalent(reference)],
  ['app route uses v318', index.includes('frame.src = "/itunda-prototype-v318.html"')],
  ['frame fills viewport', index.includes('height: 100dvh') && index.includes('width: 100%; height: 100%')],
  ['reduced motion is supported', index.includes('@media (prefers-reduced-motion: reduce)')],
  ['demo mode is labeled sample data', index.includes('DEMO MODE · Sample data only')],
  ['bridge is loaded exactly once', prototype.split(bridge).length === 2],
  ['production entry has no prototype skip affordance', !prototype.includes('Skip (prototype)') && !/onclick="obSkip\(\);return false">[^<]*prototype/i.test(prototype)],
  ['production entry has no sample-phone shortcut or handler', !prototype.includes('Prototype: fill a returning number') && !prototype.includes(demoHandler) && !prototype.includes('phDemo(')],
  ['sign-in affordance routes to backend phone validation', prototype.includes(signInLabel)],
  ['real login endpoint is wired', backend.includes("api('/api/v1/auth/login'")],
  ['account and transaction data use API endpoints', backend.includes("api('/api/v1/account'") && backend.includes("api('/api/v1/account/transactions'")],
  ['demo mode blocks API requests', backend.includes("if (demoMode) throw new Error('Demo mode uses sample data only.")],
  ['demo mode does not restore saved sessions', backend.includes('if (demoMode || !localStorage.getItem(ACCESS_KEY)) return;')],
  ['skip entry is blocked outside demo mode', backend.includes('Please sign in or create an account to continue.')],
  ['send endpoint is connected', backend.includes("api('/api/v1/p2p/send'")],
  ['requests use an idempotency key', backend.includes("'Idempotency-Key': idempotencyKey")],
  ['recipient verification is required', backend.includes('Verify the recipient name before sending money.')],
  ['cache rules cover app and bridge routes', protectedPaths.every((path) => nginx.includes('location = ' + path + ' {'))],
  ['cache is disabled on app and bridge routes', protectedPaths.every((path) => {
    const block = nginx.split('location = ' + path + ' {')[1]?.split(/\n    \}/)[0];
    return Boolean(block && block.includes(cacheHeader) && block.includes('add_header Pragma "no-cache" always;'));
  })]
];
const failed = checks.filter(([, passed]) => !passed);
for (const [label, passed] of checks) process.stdout.write((passed ? 'PASS ' : 'FAIL ') + label + '\n');
if (failed.length) process.exitCode = 1;
