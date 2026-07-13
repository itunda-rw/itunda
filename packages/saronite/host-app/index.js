/**
 * The real Metro entry point for Saronite mini-apps. Registers one
 * AppRegistry component per mini-app; each mini-app gets its own concrete
 * Activity subclass hardcoding which component to render (see
 * android/app/.../miniapps/MiniAppActivity.kt) — not an Intent-extra
 * dispatch, which was tried and reverted after a real crash (ReactActivity
 * builds its delegate in <init>, before Android attaches the launch Intent).
 * This is what actually makes the "All" tab's Mini Apps section open a live
 * mini-app instead of a placeholder.
 *
 * pay-bills re-wired onto real granite (2026-07-13) -- the upstream
 * TurboModuleRegistry timing bug that previously blocked it
 * (pay-bills/pages/index.tsx's own header comment has the full account) is
 * fixed via patches/brick-module+0.5.2.patch. wallet-balance and
 * reward-tasks followed the same pass, same day: each app.tsx's
 * `Granite.registerApp(...)` call registers its own AppRegistry name as a
 * side effect of being imported -- no explicit `AppRegistry.registerComponent`
 * call needed for any of the four anymore. insurance was rebuilt from a
 * previously disconnected stub (no package.json, no real backend call, an
 * import of a package with no package.json of its own -- see its own
 * pages/index.tsx header comment) directly onto real granite and real
 * `services/backend/insurance` endpoints, same day.
 *
 * ./granite-globals must be the first import: real granite apps get
 * `global.__granite.app` injected by granite's own CLI/bundler before their
 * JS runs (see that file's own header comment for the full account of why
 * itunda has to do this by hand) -- `app.tsx`'s AppRoot reads it as soon as
 * it first renders, so it has to already exist by then. `.scheme`/`.host`
 * are genuinely shared across all four granite apps here (confirmed by
 * reading `@granite-js/react-native`'s own `AppRoot.tsx`: only those two
 * fields are ever read at runtime, `.name` is unused JS-side) -- each app's
 * own distinct identity comes from its own `Granite.registerApp({ appName,
 * initialScheme })` call, not from this shared global.
 */
import './granite-globals';

import 'saronite-miniapp-wallet-balance/app';
import 'saronite-miniapp-reward-tasks/app';
import 'saronite-miniapp-pay-bills/app';
import 'saronite-miniapp-insurance/app';
