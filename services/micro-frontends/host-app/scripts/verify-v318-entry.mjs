import { readFileSync } from 'node:fs';

const root = new URL('../', import.meta.url);
const read = (path) => readFileSync(new URL(path, root), 'utf8');
const index = read('index.html');
const prototype = read('public/itunda-prototype-v318.html');
const reference = read('../../../itunda__clickable_prototype_v318.html');
const backend = read('public/itunda-backend.js');
const nginx = read('../nginx.conf');

const cacheHeader = 'no-store, no-cache, must-revalidate, max-age=0';
const protectedPaths = ['/', '/index.html', '/itunda-prototype-v318.html', '/itunda-backend.js'];
const bridge = '<script src="/itunda-backend.js"></script>';
const titleScript = '<script>document.title = "itunda, clickable prototype v318";</script>';
const normalized = (source) => source
  .replace(/\r?\n/g, '')
  .replace("function obSkip(){S.auth=true;S.obStep=null;render('fade')}", "function obSkip(){alert('Please sign in or create an account to continue.')}")
  .replace(/<a href="#" class="sm" style="[^"]*" onclick="obSkip\(\);return false">[^<]*prototype[^<]*<\/a>/gi, '')
  .replace(/<a href="#" class="sm" style="color:var\(--lk\);font-weight:700;display:inline-block;margin-top:6px" onclick="phDemo\(\);return false">Prototype: fill a returning number<\/a>/, '')
  .replace("window.phDemo=()=>{S.ob.ph='788123456';render('fade-none')};", '')
  .replace(bridge, '')
  .replace(titleScript, '');
const checks = [
  ['production HTML matches canonical v318 except explicit production-auth changes', normalized(reference) === normalized(prototype)],
  ['customer app route uses v318', index.includes('frame.src = "/itunda-prototype-v318.html"')],
  ['host frame fills the viewport', index.includes('height: 100dvh') && index.includes('width: 100%; height: 100%')],
  ['launch splash respects reduced-motion preferences', index.includes('@media (prefers-reduced-motion: reduce)')],
  ['production document has v318 title', prototype.includes('<title>itunda, clickable prototype v318</title>') && prototype.includes(titleScript)],
  ['real-auth bridge is loaded', prototype.includes(bridge)],
  ['prototype sign-in bypass links are removed', !/onclick="obSkip\(\);return false">[^<]*prototype/i.test(prototype)],
  ['prototype phone-fill shortcut and handler are removed', !/<a href="#" class="sm" style="color:var\\(--lk\\);font-weight:700;display:inline-block;margin-top:6px" onclick="phDemo\\(\\);return false">Prototype: fill a returning number<\\/a>/.test(prototype) && !prototype.includes("window.phDemo=()=>{S.ob.ph='788123456';render('fade-none')};")],
  ['real sign-in endpoint is wired', backend.includes('/api/v1/auth/login')],
  ['demo mode cannot call authenticated APIs', backend.includes("if (demoMode) throw new Error('Demo mode uses sample data only.")],
  ['real P2P send endpoint and idempotency key are used', backend.includes("api('/api/v1/p2p/send'") && backend.includes("'Idempotency-Key': idempotencyKey")],
  ['recipient must be verified before transfer', backend.includes('Verify the recipient name before sending money.')],
  ['Nginx cache rules cover all protected entry points', protectedPaths.every((path) => nginx.includes('location = ' + path + ' {'))],
  ['Nginx disables caching on all protected entry points', protectedPaths.every((path) => { const block = nginx.split('location = ' + path + ' {')[1]?.split(/\n    \}/)[0]; return Boolean(block && block.includes(cacheHeader) && block.includes('add_header Pragma "no-cache" always;')); })]
];
const failed = checks.filter(([, passed]) => !passed);
for (const [label, passed] of checks) process.stdout.write((passed ? 'PASS ' : 'FAIL ') + label + '\n');
if (failed.length) process.exitCode = 1;
