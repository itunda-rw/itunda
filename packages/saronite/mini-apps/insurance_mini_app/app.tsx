import React, { type PropsWithChildren } from 'react';
import { Granite, type InitialProps } from '@granite-js/react-native';
import { context } from './require.context';

/**
 * Real granite app registration for insurance (2026-07-13) -- fourth
 * mini-app on real granite, and the first one built directly on granite
 * from a real backend from the start rather than migrated from a working
 * legacy-bridge version (see pages/index.tsx's own header comment for the
 * disconnected stub this replaces). Same registration pattern pay-bills
 * proved out first.
 */
function AppContainer({ children }: PropsWithChildren<InitialProps>) {
  return <>{children}</>;
}

export default Granite.registerApp(AppContainer, {
  appName: 'SaroniteInsurance',
  context,
  initialScheme: 'itunda://saronite/insurance',
});
