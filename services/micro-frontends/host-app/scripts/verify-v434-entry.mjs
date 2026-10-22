import { readFileSync } from 'node:fs';
const root = new URL('../', import.meta.url);
const read = (path) => readFileSync(new URL(path, root), 'utf8');
const index = read('index.html');
const actual = read('public/itunda-prototype-v434.html');
const source = read('../../../itunda__clickable_prototype_v434.html');
const backend = read('public/itunda-backend.js');
const bridge = '<script src="/itunda-backend.js"></script>';
const expected = source
 .replace(/<title>[^<]*<\/title>/i, '<title>itunda, clickable prototype v434</title>')
 .replace(/<a\b[^>]*onclick="obSkip\(\);return false"[^>]*>[\s\S]*?<\/a>/gi, '')
 .replace(/function\s+obSkip\s*\(\)\s*\{[^}]*\}/g, "function obSkip(){alert('Please sign in or create an account to continue.')}")
 .replace(/<a\b[^>]*onclick="phDemo\(\);return false"[^>]*>[\s\S]*?<\/a>/gi, '')
 .replace(/window\.phDemo\s*=\s*\(\)\s*=>\s*\{[^}]*\};?/g, "window.phDemo=()=>{alert('Enter your phone number to continue.')};")
 .replace(/<\/body>/i, bridge + '</body>');
const checks = [
 ['production page matches sanitized canonical v434 source', actual === expected],
 ['web entry points to v434', index.includes('frame.src = "/itunda-prototype-v434.html"')],
 ['backend bridge is present', actual.includes(bridge)],
 ['no prototype skip-sign-in link remains', !/onclick="obSkip\(\);return false"/i.test(actual)],
 ['skip handler cannot set authenticated state', !/function\s+obSkip\s*\(\)\s*\{[^}]*S\.auth\s*=\s*true/i.test(actual)],
 ['prototype phone-fill shortcut is absent', !/onclick="phDemo\(\);return false"/i.test(actual)],
 ['existing-account login backend remains wired', backend.includes('/api/v1/auth/login')],
 ['account endpoints remain wired', backend.includes('/api/v1/account') && backend.includes('/api/v1/account/transactions')],
 ['real transfer endpoint retains idempotency protection', backend.includes('/api/v1/p2p/send') && backend.includes('Idempotency-Key')]
];
for (const [label, ok] of checks) process.stdout.write((ok ? 'PASS ' : 'FAIL ') + label + '\n');
if (checks.some(([, ok]) => !ok)) process.exitCode = 1;
