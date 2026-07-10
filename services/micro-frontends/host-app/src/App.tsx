import { lazy, Suspense, useState } from 'react';

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

type Tab = 'bank' | 'kyc';

export default function App() {
  const [tab, setTab] = useState<Tab>('bank');

  return (
    <div>
      <nav className="host-tabbar">
        <button
          className={tab === 'bank' ? 'host-tab host-tab-active' : 'host-tab'}
          onClick={() => setTab('bank')}
        >
          Home
        </button>
        <button
          className={tab === 'kyc' ? 'host-tab host-tab-active' : 'host-tab'}
          onClick={() => setTab('kyc')}
        >
          Identity
        </button>
      </nav>

      <Suspense fallback={<div className="toss-card skeleton" style={{ height: '300px', margin: '20px' }} />}>
        {tab === 'bank' ? <BankDashboard /> : <div style={{ padding: '20px' }}><KycDashboard /></div>}
      </Suspense>
    </div>
  );
}
