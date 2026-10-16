import { readFileSync } from 'node:fs';

const root = new URL('../', import.meta.url);
const read = (path) => readFileSync(new URL(path, root), 'utf8');
const index = read('index.html');
const prototype = read('public/itunda-prototype-v104.html');
const reference = read('../../../itunda__clickable_prototype_v104.html');
const backend = read('public/itunda-backend.js');
const nginx = read('../nginx.conf');

const cacheHeader = 'no-store, no-cache, must-revalidate, max-age=0';
const cacheProtectedPaths = ['/', '/index.html', '/itunda-prototype-v104.html', '/itunda-backend.js'];
const productionEquivalent = (source) => source
  .replace(/\r?\n/g, '')
  .replace('onclick="obSkip();return false">Skip (prototype)', 'onclick="obPhoneNext();return false">Sign in')
  .replace(/<a href="#" class="sm" style="color:var\(--lk\);font-weight:700;display:inline-block;margin-top:6px" onclick="phDemo\(\);return false">Prototype: fill a returning number<\/a>/, '')
  .replace('<script src="/itunda-backend.js"></script>', '')
  .replace('</body></html>', '<script src="/itunda-backend.js"></script></body></html>')
  .replace('  <script src="/itunda-backend.js"></script>', '<script src="/itunda-backend.js"></script>');

const checks = [
  ['production v104 matches the canonical prototype except the explicit production sign-in and bridge changes', productionEquivalent(reference) === productionEquivalent(prototype)],
  ['consumer app route uses v104', index.includes('src="/itunda-prototype-v104.html"')],
  ['consumer app route does not use v66', !index.includes('src="/itunda-prototype-v66.html"')],
  ['host frame fills the available viewport', index.includes('height: 100dvh') && index.includes('width: 100%; height: 100%')],
  ['launch splash respects reduced-motion preferences', index.includes('@media (prefers-reduced-motion: reduce)')],
  ['v104 document title is present', prototype.includes('<title>itunda, clickable prototype v104</title>')],
  ['v104 loads the real backend bridge', prototype.includes('<script src="/itunda-backend.js"></script>')],
  ['real sign-in endpoint is wired', backend.includes('/api/v1/auth/login')],
  ['existing-account sign-in sends the documented phone and password payload', backend.includes('body: JSON.stringify({ phoneNumber: normalizePhone(S.ob.ph), password: credential })')],
  ['sign-in requires a complete real session before setting authenticated state', backend.includes('if (!session.accessToken || !session.refreshToken || !session.user) throw new Error(\'The server returned an incomplete session.\');')],
  ['sign-in failures are shown in the login error area', backend.includes("document.getElementById('web-login-error')") && backend.includes("errorNode.textContent = error.message")],
  ['real P2P send endpoint is used', backend.includes("api('/api/v1/p2p/send'")],
  ['transfer uses idempotency key', backend.includes("headers: { 'Idempotency-Key': idempotencyKey }")],
  ['v104 bridge uses the deployed custom API domain', backend.includes("const API_BASE = 'https://api.itunda.im';")],
  ['password sign-in submit enables as the user types', backend.includes("id=\"web-login-submit\"") && backend.includes("if (submit && S.ob.usePassword) submit.disabled = !S.ob.password || busy;")],
  ['transfer resolves the real recipient before confirmation', backend.includes("api('/api/v1/p2p/recipient?identifier=' + encodeURIComponent(identifier))")],
  ['transfer blocks sending without a fresh recipient preview', backend.includes('Verify the recipient name before sending money.')],
  ['recipient phone normalization uses digit matching, not escaped backslashes', backend.includes("replace(/[\\s()-]/g, '')") && backend.includes('/^\\d{10,20}$/.test(raw)')],
  ['recipient validation accepts international phone numbers', backend.includes('/^\\+\\d{8,15}$/.test(recipient) || /^\\d{10,20}$/.test(recipient)')],
  ['legacy simulated-rail quote flow is not used', !backend.includes("api('/api/v1/account/transfer/quote'")],
  ['prototype skip-auth shortcut is blocked', backend.includes("window.obSkip = function () { fail(new Error('Please sign in or create an account to continue.')); };")],
  ['phone validation runs before sign-in', backend.includes("if (!phOk(S.ob.ph)) { fail(new Error('Enter a valid phone number first.')); return; }")],
  ['phone screen offers real sign-in', prototype.includes('onclick="obPhoneNext();return false">Sign in</a>')],
  ['Nginx cache rules cover every entry point', cacheProtectedPaths.every((path) => nginx.includes('location = ' + path + ' {'))],
  ['Nginx disables storage of the prototype and auth entry files', (nginx.match(new RegExp(cacheHeader.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'g')) || []).length >= cacheProtectedPaths.length],
  ['Nginx sends legacy no-cache compatibility headers for entry files', (nginx.match(/add_header Pragma "no-cache" always;/g) || []).length >= cacheProtectedPaths.length]
];

const failed = checks.filter(([, passed]) => !passed);
for (const [label, passed] of checks) {
  process.stdout.write((passed ? 'PASS ' : 'FAIL ') + label + '\n');
}
if (failed.length) process.exitCode = 1;
