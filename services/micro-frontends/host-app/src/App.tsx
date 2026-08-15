import { lazy, Suspense, useState } from 'react';
import { useI18n } from './i18n/I18nContext';

// Fixed (2026-07-11): this file used to render Toss Payments' real SDK/widget
// (@tosspayments/payment-widget-sdk, a real public Toss test key) while the UI text
// claimed "Secure payments via MTN MoMo, Airtel Money, and Bank Transfer" -- a widget
// that can only actually process Toss's own Korean payment methods, not Rwandan
// rails. Removed entirely rather than fixed in place; itunda needs its own payment
// integration, not Toss Payments'.
//
// This also finally wires up Module Federation the way vite.config.ts's `remotes`
// config and this file's own vite-env.d.ts declarations already implied but never
// used: bank_mfe and kyc_mfe are real, working itunda-built React apps (see their
// own READMEs) that were sitting unreferenced next to this file.
const BankDashboard = lazy(() => import('bank_mfe/BankDashboard'));
const KycDashboard = lazy(() => import('kyc_mfe/KycDashboard'));
// Real fix (2026-08-15) -- see bank-mfe's own src/RemoteI18nProvider.tsx doc
// comment: BankDashboard/KycDashboard each call useI18n() against THEIR OWN
// bundled Context object, which host-app's own I18nProvider (i18n/I18nContext.tsx)
// cannot satisfy even though all three share the same React instance. Each remote
// must be wrapped in its own federated I18nProvider, not host-app's.
const BankI18nProvider = lazy(() => import('bank_mfe/I18nProvider'));
const KycI18nProvider = lazy(() => import('kyc_mfe/I18nProvider'));

type Tab = 'bank' | 'kyc';

export default function App() {
  const [tab, setTab] = useState<Tab>('bank');
  const { t } = useI18n();

  return (
    <div>
      <nav className="host-tabbar">
        <button
          className={tab === 'bank' ? 'host-tab host-tab-active' : 'host-tab'}
          onClick={() => setTab('bank')}
        >
          {t('tabs.home')}
        </button>
        <button
          className={tab === 'kyc' ? 'host-tab host-tab-active' : 'host-tab'}
          onClick={() => setTab('kyc')}
        >
          {t('tabs.identity')}
        </button>
      </nav>

      <Suspense fallback={<div className="itunda-card skeleton" style={{ height: '300px', margin: '20px' }} />}>
        {tab === 'bank' ? (
          <BankI18nProvider>
            <BankDashboard />
          </BankI18nProvider>
        ) : (
          <KycI18nProvider>
            <div style={{ padding: '20px' }}><KycDashboard /></div>
          </KycI18nProvider>
        )}
      </Suspense>
    </div>
  );
}
