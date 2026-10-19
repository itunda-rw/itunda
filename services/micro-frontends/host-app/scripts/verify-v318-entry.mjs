const checks = [
  ['generated v318 matches canonical except real-auth production transformations', productionEquivalent(prototype) === productionEquivalent(reference)],
  ['app route uses v318', index.includes('frame.src = "/itunda-prototype-v318.html"')],
  ['frame fills viewport', index.includes('height: 100dvh') && index.includes('width: 100%; height: 100%')],
  ['reduced motion is supported', index.includes('@media (prefers-reduced-motion: reduce)')],
  ['demo mode is labeled sample data', index.includes('DEMO MODE · Sample data only')],
  ['bridge is loaded exactly once', prototype.split(bridge).length === 2],
  ['production entry has no prototype skip affordance', !prototype.includes(skipLabel) && !/onclick="obSkip\(\);return false"/i.test(prototype)],
  ['production entry has no sample-phone shortcut or handler', !demoShortcut.test(prototype) && !prototype.includes(demoHandler) && !/onclick="phDemo\(\);return false"/i.test(prototype)],
  ['sign-in affordance routes to backend phone validation', prototype.includes(signInLabel)],
  ['real login endpoint is wired', backend.includes("api('/api/v1/auth/login'")],
  ['account and transaction data use API endpoints', backend.includes("api('/api/v1/account'") && backend.includes("api('/api/v1/account/transactions'")],
  ['demo mode blocks API requests', backend.includes("if (demoMode) throw new Error('Demo mode uses sample data only.")],
  ['demo mode does not restore saved sessions', backend.includes('if (demoMode || !localStorage.getItem(ACCESS_KEY)) return;')],
  ['skip entry is blocked outside demo mode', backend.includes('Please sign in or create an account to continue.')],
  ['send endpoint is connected', backend.includes("api('/api/v1/p2p/send'")],
  ['bill provider catalog is loaded from backend', backend.includes("api('/api/v1/bills/providers')")],
  ['bill payments use authenticated backend and idempotency', backend.includes("api(airtime ? '/api/v1/bills/airtime' : '/api/v1/bills/pay'") && backend.includes("headers: { 'Idempotency-Key': idempotencyKey }")],
  ['bill retries reuse an idempotency key for the same payment', backend.includes('billRequestFingerprint !== fingerprint') && backend.includes('billRequestKey = null; billRequestFingerprint = null;')],
  ['bill receipt only renders after a backend transaction is returned', backend.includes('if (!lastBillTransaction) throw new Error(') && backend.includes("Receipt supplied by the itunda backend.")],
  ['unsupported bill providers cannot be charged', backend.includes('This bill provider is not supported by the live backend.')],
  ['savings goals are loaded from the backend', backend.includes("api('/api/v1/savings/goals')")],
  ['goal creation and deposits use idempotent backend writes', backend.includes("api('/api/v1/savings/deposit'") && backend.includes("headers: { 'Idempotency-Key': savingsCreateKey }") && backend.includes("headers: { 'Idempotency-Key': savingsDepositKey }")],
  ['savings deposits require confirmation', backend.includes('Confirm savings deposit') && backend.includes('confirmSavingsDeposit()')],
  ['unsupported savings deletion is not simulated locally', backend.includes('Deleting savings goals is not supported by the backend yet.')],
  ['requests use an idempotency key', backend.includes("'Idempotency-Key': idempotencyKey")],
  ['recipient verification is required', backend.includes('Verify the recipient name before sending money.')],
  ['cache rules cover app and bridge routes', protectedPaths.every((path) => nginx.includes('location = ' + path + ' {'))],
  ['cache is disabled on app and bridge routes', protectedPaths.every((path) => {
    const block = nginx.split('location = ' + path + ' {')[1]?.split(/\n    \}/)[0];
    return Boolean(block && block.includes(cacheHeader) && block.includes('add_header Pragma "no-cache" always;'));
  })]
];
  ['Maps adapter exposes real search, nearby, directions and bookmarks endpoints',
    backend.includes("window.itundaBackend.maps = {") && backend.includes("api('/api/v1/maps/search' + queryString({ q: q }))") && backend.includes("api('/api/v1/maps/nearby' + queryString(") && backend.includes("api('/api/v1/maps/directions' + queryString(") && backend.includes("api('/api/v1/maps/bookmarks'")],
  ['Marketplace adapter exposes authenticated browse, search and listing actions',
    backend.includes("window.itundaBackend.marketplace = {") && backend.includes("api('/api/v1/marketplace/listings' + queryString(options))") && backend.includes("api('/api/v1/marketplace/listings/search' + queryString(") && backend.includes("api('/api/v1/marketplace/my-listings' + queryString(options))") && backend.includes("/like', { method: 'POST'") && backend.includes("/hide', { method: 'POST'")],
  ['Maps and Marketplace adapters do not fabricate successful fallback records',
    !backend.includes("maps.search = function (query) { return Promise.resolve([])") && !backend.includes("marketplace.browse = function (options) { return Promise.resolve([])")],
const failed = checks.filter(([, passed]) => !passed);
for (const [label, passed] of checks) process.stdout.write((passed ? 'PASS ' : 'FAIL ') + label + '\n');
if (failed.length) process.exitCode = 1;