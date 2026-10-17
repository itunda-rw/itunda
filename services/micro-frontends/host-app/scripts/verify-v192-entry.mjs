import { readFileSync } from 'node:fs';

const root = new URL('../', import.meta.url);
const read = (path) => readFileSync(new URL(path, root), 'utf8');
const index = read('index.html');
const prototype = read('public/itunda-prototype-v192.html');
const reference = read('../../../itunda__clickable_prototype_v192.html');
const backend = read('public/itunda-backend.js');
const nginx = read('../nginx.conf');

const cacheHeader = 'no-store, no-cache, must-revalidate, max-age=0';
const protectedPaths = ['/', '/index.html', '/itunda-prototype-v192.html', '/itunda-backend.js'];
const productionEquivalent = (source) => source
  .replace(/\r?\n/g, '')
  .replace('<title>itunda, clickable prototype v153</title>', '<title>itunda, clickable prototype v192</title>')
  .replace("document.title='itunda, clickable prototype v192';", "document.title='itunda, clickable prototype v191';")
  .replace("function obSkip(){S.auth=true;S.obStep=null;render('fade')}", "function obSkip(){alert('Please sign in or create an account to continue.')}")
  .replace(/<a href="#" class="sm" style="[^"]*" onclick="obSkip\(\);return false">[^<]*prototype[^<]*<\/a>/gi, '')
  .replace(/<a href="#" class="sm" style="color:var\(--lk\);font-weight:700;display:inline-block;margin-top:6px" onclick="phDemo\(\);return false">Prototype: fill a returning number<\/a>/, '')
  .replace("window.phDemo=()=>{S.ob.ph='788123456';render('fade-none')};", '')
  .replace('<script src="/itunda-backend.js"></script>', '')
  .replace('</body></html>', '<script src="/itunda-backend.js"></script></body></html>');

const checks = [
  ['production v192 matches canonical prototype apart from explicit auth bridge changes', productionEquivalent(reference) === productionEquivalent(prototype)],
  ['consumer app route uses v192', index.includes('src="/itunda-prototype-v192.html"')],
  ['consumer app route no longer uses v104', !index.includes('src="/itunda-prototype-v104.html"')],
  ['host frame fills viewport', index.includes('height: 100dvh') && index.includes('width: 100%; height: 100%')],
  ['launch splash respects reduced-motion preferences', index.includes('@media (prefers-reduced-motion: reduce)')],
  ['production HTML title is v192', prototype.includes('<title>itunda, clickable prototype v192</title>')],
  ['production runtime title is v192', prototype.includes("document.title='itunda, clickable prototype v192';")],
  ['production prototype loads backend bridge', prototype.includes('<script src="/itunda-backend.js"></script>')],
  ['prototype-only sign-in bypass links are absent', !/onclick="obSkip\(\);return false">[^<]*prototype/i.test(prototype)],
  ['prototype phone-fill shortcut and handler are absent', !prototype.includes('Prototype: fill a returning number') && !prototype.includes("window.phDemo=()=>{S.ob.ph='788123456';render('fade-none')};")],
  ['real sign-in endpoint is wired', backend.includes('/api/v1/auth/login')],
  ['real P2P send endpoint is used', backend.includes("api('/api/v1/p2p/send'")],
  ['transfer uses idempotency key', backend.includes("headers: { 'Idempotency-Key': idempotencyKey }")],
  ['recipient must be verified before transfer', backend.includes('Verify the recipient name before sending money.')],
  ['skip-auth handler is blocked', backend.includes("window.obSkip = function () { fail(new Error('Please sign in or create an account to continue.')); };")],
  ['Nginx cache rules cover all protected entry points', protectedPaths.every((path) => nginx.includes('location = ' + path + ' {'))],
  ['Nginx disables caching for every protected entry point', protectedPaths.every((path) => { const block = nginx.split('location = ' + path + ' {')[1]?.split(/\n    \}/)[0]; return Boolean(block && block.includes(cacheHeader) && block.includes('add_header Pragma "no-cache" always;')); })],
  ['Nginx sends legacy no-cache headers for every protected entry point', (nginx.match(/add_header Pragma "no-cache" always;/g) || []).length >= protectedPaths.length]
];
const failed = checks.filter(([, passed]) => !passed);
for (const [label, passed] of checks) process.stdout.write((passed ? 'PASS ' : 'FAIL ') + label + '\n');
if (failed.length) process.exitCode = 1;
