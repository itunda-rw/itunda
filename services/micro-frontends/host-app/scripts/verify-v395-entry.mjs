import { readFileSync } from 'node:fs';

const root = new URL('../', import.meta.url);
const read = (path) => readFileSync(new URL(path, root), 'utf8');
const index = read('index.html');
const prototype = read('public/itunda-prototype-v395.html');
const reference = read('../../../v395.html');
const nginx = read('../nginx.conf');

const cacheHeader = 'no-store, no-cache, must-revalidate, max-age=0';
const protectedPaths = ['/', '/index.html', '/itunda-prototype-v395.html'];
const checks = [
  ['generated prototype matches canonical v395 byte-for-byte', reference === prototype],
  ['customer app route uses v395', index.includes('frame.src = "/itunda-prototype-v395.html"')],
  ['host frame fills the viewport', index.includes('height: 100dvh') && index.includes('width: 100%; height: 100%')],
  ['launch splash respects reduced-motion preferences', index.includes('@media (prefers-reduced-motion: reduce)')],
  ['demo mode is visibly labeled as sample data', index.includes('DEMO MODE · Sample data only')],
  ['Nginx cache rules cover UI entry points', protectedPaths.every((path) => nginx.includes('location = ' + path + ' {'))],
  ['Nginx disables caching on UI entry points', protectedPaths.every((path) => {
    const block = nginx.split('location = ' + path + ' {')[1]?.split(/\n    \}/)[0];
    return Boolean(block && block.includes(cacheHeader) && block.includes('add_header Pragma "no-cache" always;'));
  })]
];
const failed = checks.filter(([, passed]) => !passed);
for (const [label, passed] of checks) process.stdout.write((passed ? 'PASS ' : 'FAIL ') + label + '\n');
if (failed.length) process.exitCode = 1;
