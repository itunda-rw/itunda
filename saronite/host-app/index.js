/**
 * The real Metro entry point for Saronite mini-apps. Registers one
 * AppRegistry component per mini-app; `SaroniteMiniAppActivity` on the
 * native side tells `ReactActivityDelegate.getMainComponentName()` which of
 * these to render via an Intent extra — this is what actually makes the
 * "All" tab's Mini Apps section open a live mini-app instead of a
 * placeholder.
 */
import { AppRegistry } from 'react-native';
import WalletBalancePage from 'saronite-miniapp-wallet-balance/pages/index';
import PayBillsPage from 'saronite-miniapp-pay-bills/pages/index';
import RewardTasksPage from 'saronite-miniapp-reward-tasks/pages/index';

AppRegistry.registerComponent('SaroniteWalletBalance', () => WalletBalancePage);
AppRegistry.registerComponent('SaronitePayBills', () => PayBillsPage);
AppRegistry.registerComponent('SaroniteRewardTasks', () => RewardTasksPage);
