import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const repoRoot = resolve(new URL('../../../../', import.meta.url).pathname);
const sourcePath = resolve(repoRoot, 'itunda__clickable_prototype_v434.html');
const outputPath = resolve(new URL('../public/itunda-prototype-v434.html', import.meta.url).pathname);
let html = readFileSync(sourcePath, 'utf8');
if (!/<html\b/i.test(html) || !/<\/body>/i.test(html)) throw new Error('Canonical v434 prototype is not a complete HTML document.');
html = html.replace(/<title>[^<]*<\/title>/i, '<title>itunda, clickable prototype v434</title>');
// Production must not permit the prototype's onboarding shortcut to create an authenticated session.
html = html.replace(/<a\b[^>]*onclick="obSkip\(\);return false"[^>]*>[\s\S]*?<\/a>/gi, '');
html = html.replace(/function\s+obSkip\s*\(\)\s*\{[^}]*\}/g, "function obSkip(){alert('Please sign in or create an account to continue.')}");
html = html.replace(/<a\b[^>]*onclick="phDemo\(\);return false"[^>]*>[\s\S]*?<\/a>/gi, '');
html = html.replace(/window\.phDemo\s*=\s*\(\)\s*=>\s*\{[^}]*\};?/g, "window.phDemo=()=>{alert('Enter your phone number to continue.')};");
if (/onclick="obSkip\(\);return false"/i.test(html) || /function\s+obSkip\s*\(\)\s*\{[^}]*S\.auth\s*=\s*true/i.test(html) || /onclick="phDemo\(\);return false"/i.test(html)) {
  throw new Error('Prototype-only authentication or phone-fill shortcut remains; refusing production output.');
}
const bridge = '<script src="/itunda-backend.js"></script>';
if (!html.includes(bridge)) html = html.replace(/<\/body>/i, bridge + '</body>');
writeFileSync(outputPath, html);
process.stdout.write('Generated v434 production entry with authentication shortcuts guarded and backend bridge attached.\n');
