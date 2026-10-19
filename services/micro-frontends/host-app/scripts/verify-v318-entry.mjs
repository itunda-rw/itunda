import { readFileSync } from 'node:fs';

const root = new URL('../', import.meta.url);
const read = (path) => readFileSync(new URL(path, root), 'utf8');
const repoRoot = new URL('../../../../', import.meta.url);
const index = read('index.html');
const prototype = read('public/itunda-prototype-v318.html');
const reference = read('../../../itunda__clickable_prototype_v318.html');
const backend = read('public/itunda-backend.js');
const nginx = read('../nginx.conf');
const mapsController = readFileSync(new URL('services/backend/maps/src/main/kotlin/rw/itunda/maps/web/MapsController.kt', repoRoot), 'utf8');
const marketplaceController = readFileSync(new URL('services/backend/marketplace/src/main/kotlin/rw/itunda/marketplace/web/MarketplaceController.kt', repoRoot), 'utf8');
const cacheHeader = 'no-store, no-cache, must-revalidate, max-age=0';
const bridge = '<script src="/itunda-backend.js"></script>';
const protectedPaths = ['/', '/index.html', '/itunda-prototype-v318.html', '/itunda-backend.js'];
const skipLabel = 'onclick="obSkip();return false">Skip (prototype)';
const signInLabel = 'onclick="obPhoneNext();return false">Continue';
const demoShortcut = /<a href="#" class="sm" style="color:var\(--lk\);font-weight:700;display:inline-block;margin-top:6px" onclick="phDemo\(\);return false">Prototype: fill a returning number<\/a>/;
const demoHandler = "window.phDemo=()=>{S.ob.ph='788123456';render('fade-none')};";
const productionEquivalent = (html) => html
  .replace(/\r?\n/g, '')
  .split(skipLabel).join(signInLabel)
  .replace(/<a\b[^>]*onclick=["']obSkip\(\);return false["'][^>]*>[^<]*prototype[^<]*<\/a>/gi, '')
  .replace(demoShortcut, '')
  .replace(demoHandler, '')
  .replace(bridge, '');

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
  ['Maps adapter exposes search, nearby, directions and bookmarks', backend.includes('window.itundaBackend.maps = {') && backend.includes("api('/api/v1/maps/search'") && backend.includes("api('/api/v1/maps/nearby'") && backend.includes("api('/api/v1/maps/directions'") && backend.includes("api('/api/v1/maps/bookmarks'")],
  ['Marketplace adapter exposes browse, search, personal listings, likes and hides', backend.includes('window.itundaBackend.marketplace = {') && backend.includes("api('/api/v1/marketplace/listings'") && backend.includes("api('/api/v1/marketplace/listings/search'") && backend.includes("api('/api/v1/marketplace/my-listings'") && backend.includes("api('/api/v1/marketplace/my-purchases'") && backend.includes("api('/api/v1/marketplace/listings/favorites'") && backend.includes("'/favorite', { method: 'POST'") && backend.includes("'/favorite', { method: 'DELETE'") && backend.includes("'/like', { method: 'POST'") && backend.includes("'/hide', { method: 'POST'")],
  ['v318 Marketplace browse/search uses authenticated API and disables simulated money/listing mutations', backend.includes('async function loadRealMarketplace(query, category)') && backend.includes('window.itundaBackend.marketplace.search(query') && backend.includes('window.itundaBackend.marketplace.browse(') && backend.includes('Showing live listings from Itunda.') && backend.includes('No payment was made.') && backend.includes('Nothing was published.') && backend.includes('window.itundaBackend.marketplace.favorites(') && backend.includes('window.itundaBackend.marketplace.addFavorite(real.id)') && backend.includes('window.itundaBackend.marketplace.removeFavorite(real.id)') && backend.includes('response.favorites.map') && backend.includes('Marketplace messaging is not connected')],
  ['v318 Maps search calls authenticated backend and renders real results', backend.includes('const prototypeMapSearch = window.mpQ;') && backend.includes('window.mpQ = async function (value)') && backend.includes('window.itundaBackend.maps.search(query)') && backend.includes('window.itundaSelectMapSearchResult') && backend.includes('window.itundaSaveMapSearchResult') && backend.includes('No real places found')],
  ['Maps adapter paths match controller routes and parameter names', mapsController.includes('@RequestMapping("/api/v1/maps")') && mapsController.includes('@GetMapping("/search")') && mapsController.includes('@GetMapping("/nearby")') && mapsController.includes('@GetMapping("/around-me")') && mapsController.includes('@GetMapping("/directions")') && mapsController.includes('@GetMapping("/bookmarks")') && mapsController.includes('@RequestParam lat: Double') && mapsController.includes('@RequestParam lng: Double')],
  ['Marketplace adapter paths match authenticated controller routes', marketplaceController.includes('@RequestMapping("/api/v1/marketplace")') && marketplaceController.includes('@GetMapping("/categories")') && marketplaceController.includes('@GetMapping("/listings")') && marketplaceController.includes('@GetMapping("/listings/search")') && marketplaceController.includes('@GetMapping("/listings/nearby")') && marketplaceController.includes('@GetMapping("/listings/my-neighborhood")') && marketplaceController.includes('@GetMapping("/my-listings")') && marketplaceController.includes('@GetMapping("/my-purchases")') && marketplaceController.includes('@PostMapping("/listings/{listingId}/like")') && marketplaceController.includes('@PostMapping("/listings/{listingId}/hide")') && marketplaceController.includes('@DeleteMapping("/listings/{listingId}/hide")')],
  ['Maps and Marketplace adapters do not fabricate fallback records', !backend.includes("maps.search = function (query) { return Promise.resolve([])") && !backend.includes("marketplace.browse = function (options) { return Promise.resolve([])")],
  ['cache rules cover app and bridge routes', protectedPaths.every((path) => nginx.includes('location = ' + path + ' {'))],
  ['cache is disabled on app and bridge routes', protectedPaths.every((path) => {
    const block = nginx.split('location = ' + path + ' {')[1]?.split(/\n    \}/)[0];
    return Boolean(block && block.includes(cacheHeader) && block.includes('add_header Pragma "no-cache" always;'));
  })]
];
const failed = checks.filter(([, passed]) => !passed);
for (const [label, passed] of checks) process.stdout.write((passed ? 'PASS ' : 'FAIL ') + label + '\n');
if (failed.length) process.exitCode = 1;
