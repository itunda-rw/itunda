/**
 * Real granite apps get `global.__granite` injected by granite's own CLI/
 * bundler (`@granite-js/plugin-core`'s `getGraniteGlobalScript`, read
 * directly from `node_modules/@granite-js/plugin-core/dist/index.js`) --
 * a small script prepended to the bundle that sets
 * `global.__granite.app = { name, scheme, host }` before the app's own JS
 * runs. Itunda's mini-app host runs on plain `react-native start` (Metro),
 * not granite's `granite dev`/`granite build` pipeline, so nothing injects
 * this automatically -- confirmed by a repo-wide search of every
 * `@granite-js/*` package for any other place that sets it (none exists;
 * `AppRoot.tsx`'s own `global.__granite.app.scheme` read is the only real
 * *consumer*, no producer ships with the runtime library itself).
 *
 * This is the real, minimal equivalent of that injected script, imported
 * first in index.js (2026-07-13, granite-adoption stage 7 completion) so it
 * runs before anything that reads it. Scheme/host match itunda's real
 * existing scheme (`ItundaSaroniteHostBridge.getSchemeUri() ==
 * "itunda://saronite"`, see android/.../SaroniteBridge.kt), matching
 * pay-bills/app.tsx's own `initialScheme: 'itunda://saronite/pay-bills'`.
 */
global.__granite = global.__granite || {};
global.__granite.app = {
  name: 'SaronitePayBills',
  scheme: 'itunda',
  host: 'saronite',
};
