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
 * pay-bills reverted to plain registration (2026-07-12, granite-adoption
 * stage 7 wrap-up) -- see pay-bills/pages/index.tsx's own header comment for
 * the full account of the real, diagnosed TurboModuleRegistry timing bug in
 * the vendored @granite-js/brownfield-module that blocks its real granite
 * registration from actually working yet. pay-bills/app.tsx (the real
 * Granite.registerApp entry) stays in the tree, correct and unused, for
 * when that upstream issue is resolved.
 */
import { AppRegistry } from 'react-native';

import WalletBalancePage from 'saronite-miniapp-wallet-balance/pages/index';
import PayBillsPage from 'saronite-miniapp-pay-bills/pages/index';
import RewardTasksPage from 'saronite-miniapp-reward-tasks/pages/index';

AppRegistry.registerComponent('SaroniteWalletBalance', () => WalletBalancePage);
AppRegistry.registerComponent('SaronitePayBills', () => PayBillsPage);
AppRegistry.registerComponent('SaroniteRewardTasks', () => RewardTasksPage);
