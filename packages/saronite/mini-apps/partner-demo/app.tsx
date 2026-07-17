import React, { type PropsWithChildren } from 'react';
import { Granite, type InitialProps } from '@granite-js/react-native';
import { context } from './require.context';

/**
 * Real granite app registration for the Partner SDK's own live-verification bundle
 * (2026-07-17) -- registers under `SaronitePartnerMiniApp`, the one fixed component
 * name `PartnerMiniAppLoader.PARTNER_COMPONENT_NAME` (android/app/.../miniapps/
 * PartnerMiniAppLoader.kt) requires every downloaded partner bundle to use. This file
 * is NOT part of itunda's own host-app Metro build (packages/saronite/host-app/index.js
 * never imports it) -- it is bundled completely independently (see this package's own
 * README note in the matrix write-up) and served from a real bundleUrl, exactly the way
 * a genuine external partner's own bundle would be, to prove the mobile runtime loader
 * against something that was never compiled into itunda's own app.
 */
function AppContainer({ children }: PropsWithChildren<InitialProps>) {
  return <>{children}</>;
}

export default Granite.registerApp(AppContainer, {
  appName: 'SaronitePartnerMiniApp',
  context,
  initialScheme: 'itunda://saronite/partnerminiapp',
});
