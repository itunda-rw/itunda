import React, { type PropsWithChildren } from 'react';
import { Granite, type InitialProps } from '@granite-js/react-native';
import { context } from './require.context';

/**
 * Real granite app registration for pay-bills (2026-07-12, granite-adoption
 * stage 6) -- matches the real official pattern
 * (`@granite-js/react-native`'s own documented example, read directly from
 * its source at `src/app/Granite.tsx`), with one deliberate difference:
 * `appName: 'SaronitePayBills'` instead of a bare service name.
 *
 * `Granite.registerApp` calls `AppRegistry.registerComponent(appName, ...)`
 * internally using whatever `appName` is passed (it only special-cases and
 * rejects the reserved name `'shared'`, read directly in the same source
 * file) -- so passing itunda's existing AppRegistry key here registers a
 * real, granite-powered component under the exact name
 * `PayBillsMiniAppActivity.getMainComponentName()` already expects on the
 * native side, with zero native/manifest changes required for this mapping.
 * `wallet-balance`/`reward-tasks` keep registering their own plain
 * components independently in host-app/index.js, untouched.
 *
 * `initialScheme` is passed explicitly rather than left to granite's default
 * (which calls the real native `getSchemeUri()` bridge method) because that
 * native method isn't wired yet -- granite-adoption stage 7, still open.
 * `itunda://saronite/pay-bills` extends itunda's existing real scheme
 * (`ItundaSaroniteHostBridge.getSchemeUri() == "itunda://saronite"`) with a
 * per-app segment, matching granite's own `getSchemePrefix` convention
 * (`scheme://host/appName` when a host is set).
 */
function AppContainer({ children }: PropsWithChildren<InitialProps>) {
  return <>{children}</>;
}

export default Granite.registerApp(AppContainer, {
  appName: 'SaronitePayBills',
  context,
  initialScheme: 'itunda://saronite/pay-bills',
});
