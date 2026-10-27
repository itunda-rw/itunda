import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const repoRoot = resolve(new URL('../../../../', import.meta.url).pathname);
const sourcePath = resolve(repoRoot, 'v501.html');
const outputPath = resolve(new URL('../public/itunda-prototype-v501.html', import.meta.url).pathname);
const canonical = readFileSync(sourcePath, 'utf8');

if (!canonical.includes('<html') || !canonical.includes('</body>')) {
  throw new Error('Canonical v501 prototype is not a complete HTML document.');
}

function replaceExactlyOnce(source, pattern, replacement, label) {
  const matches = source.match(pattern);
  if (!matches || matches.length !== 1) {
    throw new Error('Expected exactly one ' + label + '; found ' + (matches?.length ?? 0) + '.');
  }
  return source.replace(pattern, replacement);
}

// Preserve the supplied reference file, but do not ship prototype-only auth shortcuts
// in the production host. This is UI parity work, not a backend-authentication bypass.
let production = canonical;
production = replaceExactlyOnce(
  production,
  /<a href="#" class="sm" style="color:var\(--lk\);font-weight:700" onclick="obSkip\(\);return false">Skip sign-in \(prototype shortcut\)<\/a>/g,
  '',
  'skip-sign-in shortcut'
);
production = replaceExactlyOnce(
  production,
  /<a href="#" class="sm" style="color:var\(--tt\);font-weight:600" onclick="obSkip\(\);return false">Skip \(prototype\)<\/a>/g,
  '',
  'skip-prototype shortcut'
);
production = replaceExactlyOnce(
  production,
  /<a href="#" class="sm" style="color:var\(--lk\);font-weight:700;display:inline-block;margin-top:6px" onclick="phDemo\(\);return false">Prototype: fill a returning number<\/a>/g,
  '',
  'phone-fill shortcut'
);
production = replaceExactlyOnce(
  production,
  /function obSkip\(\)\{S\.auth=true;S\.obStep=null;render\('fade'\)\}/g,
  "function obSkip(){alert('Please sign in or create an account to continue.')}",
  'skip-auth handler'
);
production = replaceExactlyOnce(
  production,
  /window\.phDemo=\(\)=>\{S\.ob\.ph='788123456';render\('fade-none'\)\};/g,
  "window.phDemo=()=>{alert('Enter your phone number to continue.')};",
  'phone-fill handler'
);

mkdirSync(resolve(outputPath, '..'), { recursive: true });
writeFileSync(outputPath, production);
process.stdout.write('Synced canonical v501 UI; removed prototype-only sign-in and phone-fill shortcuts. Backend integration remains a separate task.\n');
