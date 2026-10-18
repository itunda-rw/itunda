import { readFileSync } from 'node:fs';

const root = new URL('../', import.meta.url);
const read = (path) => readFileSync(new URL(path, root), 'utf8');
const index = read('index.html');
const prototype = read('public/itunda-prototype-v197.html');
const reference = read('../../../itunda__clickable_prototype_v197.html');
const backend = read('public/itunda-backend.js');
const nginx = read('../nginx.conf');

const cacheHeader = 'no-store, no-cache, must-revalidate, max-age=0';
const protectedPaths = ['/', '/index.html', '/itunda-prototype-v197.html', '/itunda-backend.js'];
const productionEquivalent = (source) => source
  .replace(/\r?\n/g, '')
  .replace('<title>itunda, clickable prototype v153</title>', '<title>itunda, clickable prototype v197</title>')
  .replace("function obSkip(){S.auth=true;S.obStep=null;render('fade')}", "function obSkip(){alert('Please sign in or create an account to continue.')}")
  .replace(/<a href="#" class="sm" style="[^"]*" onclick="obSkip\(\);return false">[^<]*prototype[^<]*<\/a>/gi, '')
  .replace(/<a href="#" class="sm" style="color:var\(--lk\);font-weight:700;display:inline-block;margin-top:6px" onclick="phDemo\(\);return false">Prototype: fill a returning number<\/a>/, '')
  .replace("window.phDemo=()=>{S.ob.ph='788123456';render('fade-none')};", '')
  .replace('<script src="/itunda-backend.js"></script>', '')
  .replace('</body></html>', '<script src="/itunda-backend.js"></script></body></html>');

const runtimeV197 = "document.title='itunda, clickable prototype v197';";
const runtimeV191 = "document.title='itunda, clickable prototype v191';";
const checks = [
  ['production v197 matches canonical prototype apart from explicit auth bridge changes', productionEquivalent(reference) === productionEquivalent(prototype)],
  ['consumer app route uses v197', index.includes('frame.src = "/itunda-prototype-v197.html"')],
  ['consumer app route no longer uses v192', !index.includes('src="/itunda-prototype-v192.html"')],
  ['host frame fills viewport', index.includes('height: 100dvh') && index.includes('width: 100%; height: 100%')],
  ['launch splash respects reduced-motion preferences', index.includes('@media (prefers-reduced-motion: reduce)')],
  ['production document title is v197', prototype.includes('<title>itunda, clickable prototype v197</title>')],
  ['runtime browser title ends at v197 after legacy v191 assignment', prototype.lastIndexOf(runtimeV197) > prototype.lastIndexOf(runtimeV191)],
  ['production prototype loads backend bridge', prototype.includes('<script src="/itunda-backend.js"></script>')],
  ['prototype-only sign-in bypass links are absent', !/onclick="obSkip\(\);return false">[^<]*prototype/i.test(prototype)],
  ['prototype phone-fill shortcut and handler are absent', !prototype.includes('Prototype: fill a returning number') && !prototype.includes("window.phDemo=()=>{S.ob.ph='788123456';render('fade-none')};")],
  ['auto-transfer count is sourced from the authenticated API', backend.includes("api('/api/v1/p2p/auto-transfers').catch(function () { return null; })") && backend.includes("String(transfer.status || '').toUpperCase() === 'ACTIVE'")],
  ['transaction API failures are not converted into an empty history', backend.includes("api('/api/v1/account/transactions'),") && !backend.includes("api('/api/v1/account/transactions').catch(function () { return { transactions: [] }; })")],
  ['malformed transaction history responses are not treated as an empty ledger', backend.includes("!results[1] || !Array.isArray(results[1].transactions)") && backend.includes('Transaction history is unavailable because the server returned an invalid response. Please retry.')],
  ['missing transaction amounts and dates are shown as unavailable', backend.includes("const rawAmount = tx.amount;") && backend.includes("'Amount unavailable'") && backend.includes("'Date unavailable'") && !backend.includes('const amount = Number(tx.amount || 0);')],
  ['malformed transaction date strings render as unavailable', backend.includes('const parsedDate = date ? new Date(date) : null;') && backend.includes('Number.isFinite(parsedDate.getTime())') && backend.includes("'Date unavailable'")],
  ['compact activity never fabricates a zero amount, current date, or completed status', backend.includes('liveTransactions.filter(function (tx)') && backend.includes('const amount = Number(tx.amount) * (outgoing ? -1 : 1);') && backend.includes("const dateLabel = dateValid ? date.toLocaleDateString() : 'Date unavailable';") && backend.includes("String(tx.status || 'unknown').toLowerCase()") && !backend.includes('const amount = Number(tx.amount || 0);')],
  ['missing or invalid account balance is not converted to zero', backend.includes("balanceValue === null || balanceValue === undefined || balanceValue === '' || !Number.isFinite(Number(balanceValue))") && backend.includes('Account balance is unavailable. Please retry.') && backend.includes('S.bank = Number(balanceValue);')],
  ['unavailable auto-transfer data is not replaced with the prototype count', backend.includes("liveAutoTransferCount === null ? 'Unavailable' : liveAutoTransferCount + ' active'") && backend.includes("html.replace('2 active', autoTransferLabel)")],
  ['unconnected savings samples are hidden for real accounts', backend.includes('Savings products, rates and goals are not connected to your account yet.') && backend.includes('html.replace(/<div class="sec"><div class="sech">Save &amp; grow')],
  ['sample personalized recommendations are not presented as live account data', backend.includes('Personalized recommendations are not connected to your account yet.')],
  ['sample spending insights are not presented as real analytics', backend.includes('Spending insights are not available until real transaction analytics are connected.')],
  ['sample loan and credit offers are not presented as live products', backend.includes('Loan and credit products are not connected to your account yet.')],
  ['real-account profile never falls back to sample identity fields', backend.includes("html = html.replace('Amina Uwase', 'Name unavailable')") && backend.includes("html = html.replace('078 ••• 214', 'Phone unavailable')")],
  ['missing profile response is shown as unavailable rather than sample data', backend.includes('Your account profile could not be loaded. Please retry.')],
  ['real-account sample-data guards stay inside the non-demo branch', backend.includes('if (!demoMode) {\n      const autoTransferLabel') && backend.includes('if (demoMode) return previousBankScreen()')],
  ['real sign-in endpoint is wired', backend.includes('/api/v1/auth/login')],
  ['demo mode disables all backend API calls', backend.includes("if (demoMode) throw new Error('Demo mode uses sample data only. Exit demo mode and sign in to use real account services.')")],
  ['demo mode does not restore a real saved session', backend.includes('if (demoMode || !localStorage.getItem(ACCESS_KEY)) return;')],
  ['host exposes demo mode only with an explicit query flag', index.includes('get("demo") === "1"') && index.includes('DEMO MODE · Sample data only')],
  ['real P2P send endpoint is used', backend.includes("api('/api/v1/p2p/send'")],
  ['transfer retries reuse the same idempotency key for an unchanged intent', backend.includes("headers: { 'Idempotency-Key': transferIdempotencyKey }") && backend.includes('transferIntentFingerprint !== intentFingerprint') && backend.includes('transferIdempotencyKey = null;')],
  ['malformed transfer success responses do not create a confirmed receipt', backend.includes("result.success !== true || !result.transaction") && backend.includes('Transfer may have been submitted, but confirmation details are unavailable.')],
  ['invalid returned transfer balances cannot poison the displayed balance', backend.includes('Number.isFinite(returnedBalance)') && backend.includes('S.bank = returnedBalance;')],
  ['transfer uses idempotency key', backend.includes("headers: { 'Idempotency-Key': transferIdempotencyKey }")],
  ['recipient must be verified before transfer', backend.includes('Verify the recipient name before sending money.')],
  ['skip-auth is only available in explicit demo mode', backend.includes("if (!demoMode) return fail(new Error('Please sign in or create an account to continue.'))")],
  ['phone validation helper is defined in the auth bridge', backend.includes('function phOk(value)') && backend.includes("if (!phOk(S.ob.ph))")],
  ['Nginx cache rules cover all protected entry points', protectedPaths.every((path) => nginx.includes('location = ' + path + ' {'))],
  ['Nginx disables caching for every protected entry point', protectedPaths.every((path) => { const block = nginx.split('location = ' + path + ' {')[1]?.split(/\n    \}/)[0]; return Boolean(block && block.includes(cacheHeader) && block.includes('add_header Pragma "no-cache" always;')); })],
  ['Nginx sends legacy no-cache headers for every protected entry point', (nginx.match(/add_header Pragma "no-cache" always;/g) || []).length >= protectedPaths.length]
];
const failed = checks.filter(([, passed]) => !passed);
for (const [label, passed] of checks) process.stdout.write((passed ? 'PASS ' : 'FAIL ') + label + '\\n');
if (failed.length) process.exitCode = 1;
