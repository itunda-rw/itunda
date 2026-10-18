import { readFileSync } from 'node:fs';

const root = new URL('../', import.meta.url);
const read = (path) => readFileSync(new URL(path, root), 'utf8');
const index = read('index.html');
const prototype = read('public/itunda-prototype-v266.html');
const reference = read('../../../itunda__clickable_prototype_v266.html');
const backend = read('public/itunda-backend.js');
const nginx = read('../nginx.conf');

const skipHandler = "function obSkip(){S.auth=true;S.obStep=null;render('fade')}";
const demoHandler = "window.phDemo=()=>{S.ob.ph='788123456';render('fade-none')};";
const bridge = '<script src="/itunda-backend.js"></script>';
const transform = (source) => {
  let html = source.replace('<title>itunda, clickable prototype v256</title>', '<title>itunda, clickable prototype v266</title>');
  html = html.replace(skipHandler, "function obSkip(){alert('Please sign in or create an account to continue.')}");
  html = html.replace(/<a href="#" class="sm" style="[^"]*" onclick="obSkip\(\);return false">[^<]*(?:prototype|skip sign-in)[^<]*<\/a>/gi, '');
  html = html.replace(/<a href="#" class="sm" style="color:var\(--lk\);font-weight:700;display:inline-block;margin-top:6px" onclick="phDemo\(\);return false">Prototype: fill a returning number<\/a>/, '');
  html = html.replace(demoHandler, '');
  return html.replace('</body>', bridge + '</body>');
};
const cacheHeader = 'no-store, no-cache, must-revalidate, max-age=0';
const protectedPaths = ['/', '/index.html', '/itunda-prototype-v266.html', '/itunda-backend.js'];
const checks = [
  ['generated v266 page exactly matches canonical source plus production auth/bridge transformations', transform(reference) === prototype],
  ['consumer app iframe points to v266', index.includes('frame.src = "/itunda-prototype-v266.html"') && !index.includes('frame.src = "/itunda-prototype-v197.html"')],
  ['page title and canonical prototype version are v266', prototype.includes('<title>itunda, clickable prototype v266</title>') && prototype.includes("document.title=document.title.replace(/v\\d+/,'v266')")],
  ['production backend bridge is loaded', prototype.includes(bridge)],
  ['prototype skip-sign-in shortcut is absent', !/onclick="obSkip\(\);return false">[^<]*(?:prototype|skip sign-in)/i.test(prototype) && !prototype.includes(skipHandler)],
  ['prototype phone-fill shortcut and sample phone handler are absent', !/onclick="phDemo\(\);return false">Prototype: fill a returning number<\/a>/.test(prototype) && !prototype.includes(demoHandler)],
  ['existing-account sign-in remains wired to the real backend', backend.includes('/api/v1/auth/login')],
  ['account data loads through real API endpoints', backend.includes('/api/v1/account') && backend.includes('/api/v1/account/transactions')],
  ['demo mode is explicit and disables backend API calls', backend.includes("get('demo') === '1'") && backend.includes('if (demoMode) throw new Error')],
  ['real P2P send endpoint and idempotency key remain wired', backend.includes('/api/v1/p2p/send') && backend.includes('Idempotency-Key')],
  ['Nginx disables caching for every production entry point', protectedPaths.every((path) => nginx.includes('location = ' + path + ' {') && (() => { const block = nginx.split('location = ' + path + ' {')[1]?.split(/\n    \}/)[0]; return Boolean(block && block.includes(cacheHeader) && block.includes('add_header Pragma "no-cache" always;')); })())]
];
const failed = checks.filter(([, passed]) => !passed);
for (const [label, passed] of checks) process.stdout.write((passed ? 'PASS ' : 'FAIL ') + label + '\n');
if (failed.length) process.exitCode = 1;
