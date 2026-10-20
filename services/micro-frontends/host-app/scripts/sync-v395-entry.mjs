import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const repoRoot = resolve(new URL('../../../../', import.meta.url).pathname);
const sourcePath = resolve(repoRoot, 'v395.html');
const outputPath = resolve(new URL('../public/itunda-prototype-v395.html', import.meta.url).pathname);
const html = readFileSync(sourcePath, 'utf8');

if (!html.includes('<html') || !html.includes('</body>')) {
  throw new Error('Canonical v395 prototype is not a complete HTML document.');
}

// Keep the canonical prototype intact, but remove prototype-only shortcuts from
// the production-host copy. The UI should never imply that sign-in can be skipped
// or pre-fill a returning user's phone number.
function replaceExactlyOnce(source, pattern, replacement, label) {
  const matches = source.match(pattern);
  if (!matches || matches.length !== 1) {
    throw new Error('Expected exactly one ' + label + '; found ' + (matches?.length ?? 0) + '.');
  }
  return source.replace(pattern, replacement);
}

let productionHtml = html;
productionHtml = replaceExactlyOnce(
  productionHtml,
  /<a href="#" class="sm" style="color:var\(--lk\);font-weight:700" onclick="obSkip\(\);return false">Skip sign-in \(prototype shortcut\)<\/a>/g,
  '',
  'skip-sign-in shortcut'
);
productionHtml = replaceExactlyOnce(
  productionHtml,
  /<a href="#" class="sm" style="color:var\(--tt\);font-weight:600" onclick="obSkip\(\);return false">Skip \(prototype\)<\/a>/g,
  '',
  'skip-prototype shortcut'
);
productionHtml = replaceExactlyOnce(
  productionHtml,
  /<a href="#" class="sm" style="color:var\(--lk\);font-weight:700;display:inline-block;margin-top:6px" onclick="phDemo\(\);return false">Prototype: fill a returning number<\/a>/g,
  '',
  'phone-fill shortcut'
);
productionHtml = replaceExactlyOnce(
  productionHtml,
  /function obSkip\(\)\{S\.auth=true;S\.obStep=null;render\('fade'\)\}/g,
  "function obSkip(){alert('Please sign in or create an account to continue.')}",
  'skip-auth handler'
);
productionHtml = replaceExactlyOnce(
  productionHtml,
  /window\.phDemo=\(\)=>\{S\.ob\.ph='788123456';render\('fade-none'\)\};/g,
  "window.phDemo=()=>{alert('Enter your phone number to continue.')};",
  'phone-fill handler'
);

writeFileSync(outputPath, productionHtml);
process.stdout.write('Synced v395 UI and removed prototype-only sign-in shortcuts. Backend integration remains deferred.\n');
