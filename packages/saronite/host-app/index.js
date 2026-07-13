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
 * fixed via patches/brick-module+0.5.2.patch. `pay-bills/app.tsx`'s
 * `Granite.registerApp(...)` call registers 'SaronitePayBills' with
 * AppRegistry itself as a side effect of being imported -- no explicit
 * `AppRegistry.registerComponent` call needed for it here, unlike
 * wallet-balance/reward-tasks, which stay on itunda's own plain
 * registration (deliberately not migrated this pass).
 *
 * ./granite-globals must be the first import: real granite apps get
 * `global.__granite.app` injected by granite's own CLI/bundler before their
 * JS runs (see that file's own header comment for the full account of why
 * itunda has to do this by hand) -- `app.tsx`'s AppRoot reads it as soon as
 * it first renders, so it has to already exist by then.
 */
import './granite-globals';
import { AppRegistry } from 'react-native';

import WalletBalancePage from 'saronite-miniapp-wallet-balance/pages/index';
import RewardTasksPage from 'saronite-miniapp-reward-tasks/pages/index';
import 'saronite-miniapp-pay-bills/app';

AppRegistry.registerComponent('SaroniteWalletBalance', () => WalletBalancePage);
AppRegistry.registerComponent('SaroniteRewardTasks', () => RewardTasksPage);
