import React, { type PropsWithChildren } from 'react';
import { Granite, type InitialProps } from '@granite-js/react-native';
import { context } from './require.context';

/**
 * Real granite app registration for wallet-balance (2026-07-13) -- second
 * mini-app migrated off the legacy plain-AppRegistry path onto real
 * granite, same pattern pay-bills proved out first. See pay-bills/app.tsx's
 * own header comment for the full account of `Granite.registerApp`'s real
 * behavior and why `initialScheme` is passed explicitly.
 */
function AppContainer({ children }: PropsWithChildren<InitialProps>) {
  return <>{children}</>;
}

export default Granite.registerApp(AppContainer, {
  appName: 'SaroniteWalletBalance',
  context,
  initialScheme: 'itunda://saronite/wallet-balance',
});
