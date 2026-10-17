import { readFileSync, writeFileSync } from 'node:fs';

const root = new URL('../', import.meta.url);
const source = readFileSync(new URL('../../../itunda__clickable_prototype_v186.html', root), 'utf8');
const skipLink = 'onclick="obSkip();return false">Skip (prototype)';
const demoLink = /<a href="#" class="sm" style="color:var\(--lk\);font-weight:700;display:inline-block;margin-top:6px" onclick="phDemo\(\);return false">Prototype: fill a returning number<\/a>/;

if (!source.includes("<title>itunda, clickable prototype v153</title>")) {
  throw new Error('Canonical v186 source title changed; review the production transform before updating.');
}
if (!source.includes(skipLink)) throw new Error('Expected prototype skip link not found.');
if (!demoLink.test(source)) throw new Error('Expected demo phone-fill link not found.');
if (source.includes('<script src="/itunda-backend.js"></script>')) {
  throw new Error('Canonical prototype unexpectedly already contains the production backend bridge.');
}

const production = source
  .replace('<title>itunda, clickable prototype v153</title>', '<title>itunda, clickable prototype v186</title>')
  .replace(skipLink, 'onclick="obPhoneNext();return false">Sign in')
  .replace(demoLink, '')
  .replace('</body>\n</html>', '<script src="/itunda-backend.js"></script>\n</body>\n</html>');

if (production === source || !production.includes('<script src="/itunda-backend.js"></script>')) {
  throw new Error('Production v186 transform did not apply.');
}
if (production.includes('Prototype: fill a returning number') || production.includes('Skip (prototype)')) {
  throw new Error('Prototype-only authentication shortcuts remain in the production page.');
}

writeFileSync(new URL('public/itunda-prototype-v186.html', root), production, 'utf8');
process.stdout.write('Generated public/itunda-prototype-v186.html from canonical v186 with production authentication bridge.\n');
