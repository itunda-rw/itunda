/// <reference types="vite/client" />
declare module 'kyc_mfe/KycDashboard';
declare module 'bank_mfe/BankDashboard';
declare module 'kyc_mfe/I18nProvider' {
  import type { ComponentType, PropsWithChildren } from 'react';
  const I18nProvider: ComponentType<PropsWithChildren>;
  export default I18nProvider;
}
declare module 'bank_mfe/I18nProvider' {
  import type { ComponentType, PropsWithChildren } from 'react';
  const I18nProvider: ComponentType<PropsWithChildren>;
  export default I18nProvider;
}
