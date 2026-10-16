import { readFileSync } from 'node:fs';

const root = new URL('../', import.meta.url);
const read = (path) => readFileSync(new URL(path, root), 'utf8');
const index = read('index.html');
const prototype = read('public/itunda-prototype-v104.html');
const backend = read('public/itunda-backend.js');

const checks = [
  ['consumer app route uses v104', index.includes('src="/itunda-prototype-v104.html"')],
  ['consumer app route does not use v66', !index.includes('src="/itunda-prototype-v66.html"')],
  ['v104 document title is present', prototype.includes('<title>itunda, clickable prototype v104</title>')],
  ['v104 loads the real backend bridge', prototype.includes('<script src="/itunda-backend.js"></script>')],
  ['real sign-in endpoint is wired', backend.includes('/api/v1/auth/login')],
  ['real P2P send endpoint is used', backend.includes("api('/api/v1/p2p/send'")],
  ['transfer uses idempotency key', backend.includes("headers: { 'Idempotency-Key': idempotencyKey }")],
  ['v104 bridge uses the deployed Cloud Run API host', backend.includes("const API_BASE = 'https://itunda-api-hptgu43hsa-du.a.run.app';")],
  ['transfer resolves the real recipient before confirmation', backend.includes("api('/api/v1/p2p/recipient?identifier=' + encodeURIComponent(identifier))")],
  ['transfer blocks sending without a fresh recipient preview', backend.includes('Verify the recipient name before sending money.')],
  ['legacy simulated-rail quote flow is not used', !backend.includes("api('/api/v1/account/transfer/quote'")],
  ['prototype skip-auth shortcut is blocked', backend.includes("window.obSkip = function () { fail(new Error('Please sign in or create an account to continue.')); };")],
  ['phone validation runs before sign-in', backend.includes("if (!phOk(S.ob.ph)) { fail(new Error('Enter a valid phone number first.')); return; }")],
  ['sample phone shortcut is removed from live onboarding', !prototype.includes('Prototype: fill a returning number')],
  ['phone screen offers real sign-in', prototype.includes('onclick="obPhoneNext();return false">Sign in</a>')]
];

const failed = checks.filter(([, passed]) => !passed);
for (const [label, passed] of checks) {
  process.stdout.write((passed ? 'PASS ' : 'FAIL ') + label + '\n');
}
if (failed.length) process.exitCode = 1;
