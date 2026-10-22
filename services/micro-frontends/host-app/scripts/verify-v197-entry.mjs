import { readFileSync } from 'node:fs';

const root = new URL('../', import.meta.url);
const read = (path) => readFileSync(new URL(path, root), 'utf8');
const index = read('index.html');
const prototype = read('public/itunda-prototype-v197.html');
const reference = read('../../../itunda__clickable_prototype_v197.html');
const backend = read('public/itunda-backend.js');
const nginx = read('../nginx.conf');

const cacheHeader = 'no-store, no-cache, must-revalidate, max-age=0';
const protectedPaths = ['/', '/index.html', '/itunda-prototype-v197.html', '/itunda-backend.js'];
const productionEquivalent = (source) => source
  .replace(/\r?\n/g, '')
  .replace('<title>itunda, clickable prototype v153</title>', '<title>itunda, clickable prototype v197</title>')
  .replace("function obSkip(){S.auth=true;S.obStep=null;render('fade')}", "function obSkip(){alert('Please sign in or create an account to continue.')}")
  .replace(/<a href="#" class="sm" style="[^"]*" onclick="obSkip\(\);return false">[^<]*prototype[^<]*<\/a>/gi, '')
  .replace(/<a href="#" class="sm" style="color:var\(--lk\);font-weight:700;display:inline-block;margin-top:6px" onclick="phDemo\(\);return false">Prototype: fill a returning number<\/a>/, '')
  .replace("window.phDemo=()=>{S.ob.ph='788123456';render('fade-none')};", '')
  .replace('<script src="/itunda-backend.js"></script>', '')
  .replace('</body></html>', '<script src="/itunda-backend.js"></script></body></html>');

const runtimeV197 = "document.title='itunda, clickable prototype v197';";
const runtimeV191 = "document.title='itunda, clickable prototype v191';";
const checks = [
  ['production v197 matches canonical prototype apart from explicit auth bridge changes', productionEquivalent(reference) === productionEquivalent(prototype)],
  ['consumer app route uses v197', index.includes('frame.src = "/itunda-prototype-v197.html"')],
  ['consumer app route no longer uses v192', !index.includes('src="/itunda-prototype-v192.html"')],
  ['host frame fills viewport', index.includes('height: 100dvh') && index.includes('width: 100%; height: 100%')],
  ['launch splash respects reduced-motion preferences', index.includes('@media (prefers-reduced-motion: reduce)')],
  ['production document title is v197', prototype.includes('<title>itunda, clickable prototype v197</title>')],
  ['runtime browser title ends at v197 after legacy v191 assignment', prototype.lastIndexOf(runtimeV197) > prototype.lastIndexOf(runtimeV191)],
  ['production prototype loads backend bridge', prototype.includes('<script src="/itunda-backend.js"></script>')],
  ['prototype-only sign-in bypass links are absent', !/onclick="obSkip\(\);return false">[^<]*prototype/i.test(prototype)],
  ['prototype phone-fill shortcut and handler are absent', !prototype.includes('Prototype: fill a returning number') && !prototype.includes("window.phDemo=()=>{S.ob.ph='788123456';render('fade-none')};")],
  ['real sign-in endpoint is wired', backend.includes('/api/v1/auth/login')],
  ['demo mode disables all backend API calls', backend.includes("if (demoMode) throw new Error('Demo mode uses sample data only. Exit demo mode and sign in to use real account services.')")],
  ['demo mode does not restore a real saved session', backend.includes('if (demoMode || !localStorage.getItem(ACCESS_KEY)) return;')],
  ['host exposes demo mode only with an explicit query flag', index.includes('get("demo") === "1"') && index.includes('DEMO MODE · Sample data only')],
  ['real P2P send endpoint is used', backend.includes("api('/api/v1/p2p/send'")],
  ['transfer uses idempotency key', backend.includes("headers: { 'Idempotency-Key': idempotencyKey }")],
  ['real Pay balance comes from the authenticated PAY account', backend.includes("const payAccount = accountList.find(function (a) { return String(a.type || '').toUpperCase() === 'PAY'; });") && backend.includes('livePayBalance = Number.isFinite(payBalanceValue) ? payBalanceValue : null;')],
  ['missing Pay balance is shown as unavailable rather than prototype sample', backend.includes("livePayBalance === null ? 'Not available' : fmt(livePayBalance) + ' RWF'")],
  ['recipient must be verified before transfer', backend.includes('Verify the recipient name before sending money.')],
  ['skip-auth is only available in explicit demo mode', backend.includes("if (!demoMode) return fail(new Error('Please sign in or create an account to continue.'))")],
  ['phone validation helper is defined in the auth bridge', backend.includes('function phOk(value)') && backend.includes("if (!phOk(S.ob.ph))")],
  ['Nginx cache rules cover all protected entry points', protectedPaths.every((path) => nginx.includes('location = ' + path + ' {'))],
  ['Nginx disables caching for every protected entry point', protectedPaths.every((path) => { const block = nginx.split('location = ' + path + ' {')[1]?.split(/\n    \}/)[0]; return Boolean(block && block.includes(cacheHeader) && block.includes('add_header Pragma "no-cache" always;')); })],
  ['Nginx sends legacy no-cache headers for every protected entry point', (nginx.match(/add_header Pragma "no-cache" always;/g) || []).length >= protectedPaths.length]
];
const failed = checks.filter(([, passed]) => !passed);
for (const [label, passed] of checks) process.stdout.write((passed ? 'PASS ' : 'FAIL ') + label + '\\n');
if (failed.length) process.exitCode = 1;
