import { readFileSync } from 'node:fs';

const root = new URL('../', import.meta.url);
const read = (path) => readFileSync(new URL(path, root), 'utf8');
const index = read('index.html');
const prototype = read('public/itunda-prototype-v186.html');
const reference = read('../../../itunda__clickable_prototype_v186.html');
const backend = read('public/itunda-backend.js');
const nginx = read('../nginx.conf');

const cacheHeader = 'no-store, no-cache, must-revalidate, max-age=0';
const cacheProtectedPaths = ['/', '/index.html', '/itunda-prototype-v104.html', '/itunda-prototype-v186.html', '/itunda-backend.js'];
const productionEquivalent = (source) => source
  .replace(/\r?\n/g, '')
  .replace('<title>itunda, clickable prototype v153</title>', '<title>itunda, clickable prototype v186</title>')
  .replace('onclick="obSkip();return false">Skip (prototype)', 'onclick="obPhoneNext();return false">Sign in')
  .replace(/<a href="#" class="sm" style="color:var\(--lk\);font-weight:700;display:inline-block;margin-top:6px" onclick="phDemo\(\);return false">Prototype: fill a returning number<\/a>/, '')
  .replace('</body></html>', '<script src="/itunda-backend.js"></script></body></html>');

const checks = [
  ['production v186 matches canonical source except explicit production auth changes', productionEquivalent(reference) === productionEquivalent(prototype)],
  ['consumer app route uses v186', index.includes('src="/itunda-prototype-v186.html"')],
  ['consumer app route no longer points at v104', !index.includes('src="/itunda-prototype-v104.html"')],
  ['host frame fills the viewport', index.includes('height: 100dvh') && index.includes('width: 100%; height: 100%')],
  ['launch splash respects reduced motion', index.includes('@media (prefers-reduced-motion: reduce)')],
  ['v186 document title is correct', prototype.includes('<title>itunda, clickable prototype v186</title>') && prototype.includes("document.title='itunda, clickable prototype v186'")],
  ['v186 loads the real backend bridge exactly once', (prototype.match(/<script src="/itunda-backend\.js"><\/script>/g) || []).length === 1],
  ['prototype-only phone-fill shortcut is removed', !prototype.includes('Prototype: fill a returning number') && !prototype.includes('phDemo();return false')],
  ['prototype skip-auth shortcut is removed', !prototype.includes('Skip (prototype)') && backend.includes("window.obSkip = function () { fail(new Error('Please sign in or create an account to continue.')); };")],
  ['phone validation does not rely on a cross-script helper', backend.includes("if (!/^7\\d{8}$/.test(String(S.ob.ph || ''))) { fail(new Error('Enter a valid phone number first.')); return; }") && !backend.includes('phOk(S.ob.ph)')],
  ['real sign-in endpoint is wired', backend.includes('/api/v1/auth/login')],
  ['real registration endpoint and required terms are wired', backend.includes('/api/v1/auth/register') && backend.includes('/api/v1/auth/terms')],
  ['real account and transaction endpoints are wired', backend.includes("api('/api/v1/account')") && backend.includes("api('/api/v1/account/transactions')")],
  ['real P2P send endpoint is used', backend.includes("api('/api/v1/p2p/send'")],
  ['transfer uses an idempotency key', backend.includes("headers: { 'Idempotency-Key': idempotencyKey }")],
  ['transfer resolves the real recipient before confirmation', backend.includes("api('/api/v1/p2p/recipient?identifier=' + encodeURIComponent(identifier))")],
  ['transfer blocks sending without a fresh recipient preview', backend.includes('Verify the recipient name before sending money.')],
  ['backend bridge uses the deployed Cloud Run API host', backend.includes("const API_BASE = 'https://itunda-api-hptgu43hsa-du.a.run.app';")],
  ['legacy simulated transfer quote flow is not used', !backend.includes("api('/api/v1/account/transfer/quote'")],
  ['Nginx cache rules cover every app entry point', cacheProtectedPaths.every((path) => nginx.includes('location = ' + path + ' {'))],
  ['Nginx disables storage of HTML and auth bridge files', (nginx.split(cacheHeader).length - 1) >= cacheProtectedPaths.length],
  ['Nginx sends no-cache compatibility headers for entry files', (nginx.match(/add_header Pragma "no-cache" always;/g) || []).length >= cacheProtectedPaths.length]
];

const failed = checks.filter(([, passed]) => !passed);
for (const [label, passed] of checks) process.stdout.write((passed ? 'PASS ' : 'FAIL ') + label + '\n');
if (failed.length) process.exitCode = 1;
