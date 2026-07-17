/**
 * Real, standalone Metro entry point for the Partner SDK's own live-verification
 * bundle (2026-07-17) -- deliberately NOT imported by `index.js` (the host-app's own
 * real bundle every first-party mini-app rides on). This is bundled completely
 * separately via `react-native bundle --entry-file index.partner-demo.js`, producing
 * one standalone JS file that stands in for what a genuine external partner would
 * build and host themselves, then served from a real bundleUrl and downloaded at
 * runtime by `PartnerMiniAppLoader.kt` -- proving the mobile runtime loader against
 * something that was never compiled into itunda's own app bundle.
 *
 * `./granite-globals` first import matches index.js's own real requirement (see that
 * file's header comment) -- a genuine external partner's own bundler/CLI would need
 * the equivalent, since this same requirement applies to any granite app, not
 * something itunda invented only for its own four first-party mini-apps.
 */
import './granite-globals';

import 'saronite-miniapp-partner-demo/app';
