import { lazy, Suspense, useEffect, useRef, useState } from 'react';
import { useI18n } from './i18n/I18nContext';
import { Button } from '@itunda/design-system-web';

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

type ProductConfig = { kicker:string; title:string; lead:string; primary:string; secondary:string; cards:[string,string,string][] };
const productConfigs: Record<string, ProductConfig> = {
  'business.itunda.im': { kicker:'ITUNDA BUSINESS · FOR MERCHANTS', title:'Business, made simpler.', lead:'Payments, customers and commerce — connected in one business experience.', primary:'Start with Itunda Business', secondary:'Open Itunda', cards:[['GROW','Reach customers','Build a business presence that fits naturally into everyday Itunda journeys.'],['PAY','Accept payments','Give customers simple, familiar ways to pay and keep your business moving.'],['CONNECT','Build the relationship','Bring orders, messages and customer journeys together.']] },
  'developers.itunda.im': { kicker:'ITUNDA DEVELOPERS · BUILD ON ITUNDA', title:'Build for what comes next.', lead:'APIs, Saronite and platform capabilities for developers building the next generation of Itunda experiences.', primary:'Explore the platform', secondary:'Open Itunda', cards:[['SARONITE','Build mini apps','Create modular experiences that live inside the Itunda ecosystem.'],['APIs','Connect services','Use focused platform capabilities to build useful integrations.'],['SHIP','Release confidently','Design, verify and publish experiences that respect platform boundaries.']] },
  'tech-blog.itunda.im': { kicker:'ITUNDA TECH · ENGINEERING', title:'Engineering the everyday.', lead:'Architecture, infrastructure, design systems and product technology behind Itunda — shared openly and built for scale.', primary:'Explore Itunda Tech', secondary:'View Developers', cards:[['ARCHITECTURE','Systems that scale','Explore the boundaries between products, platform services and infrastructure.'],['IDS','Design as infrastructure','A shared language across web, Android and iOS.'],['OPEN SOURCE','Build in public','Engineering notes, platform decisions and reusable work from Itunda.']] },
};
function HostProductShell({ config }: { config: ProductConfig }) {
  return <main className="host-product-shell"><header className="host-product-nav"><a href="https://itunda.im/" className="host-product-brand" aria-label="Itunda home">itunda.</a><span className="host-product-label">{config.kicker.split(' · ')[0]}</span><a href="https://app.itunda.im/" className="host-product-nav-link">Open Itunda</a></header><section className="host-product-hero"><p className="host-product-kicker">{config.kicker}</p><h1>{config.title}</h1><p className="host-product-lead">{config.lead}</p><div className="host-product-actions"><a href="#host-product-content" className="host-product-primary">{config.primary}</a><a href="https://app.itunda.im/" className="host-product-secondary">{config.secondary}</a></div></section><section id="host-product-content" className="host-product-grid" aria-label="Product capabilities">{config.cards.map(([eyebrow,title,body])=><article className="host-product-card" key={eyebrow}><span>{eyebrow}</span><h2>{title}</h2><p>{body}</p></article>)}</section><p className="host-product-footnote"><strong>Itunda.</strong> Rwanda-first, simple, secure and customer-first.</p></main>;
}

export default function App() {
  const product = productConfigs[window.location.hostname.toLowerCase()];
  if (product) return <HostProductShell config={product} />;
  const [tab, setTab] = useState<Tab>('bank');
  const { t } = useI18n();
  const bankTabRef = useRef<HTMLButtonElement>(null);
  const kycTabRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
      const target = event.target as HTMLElement | null;
      if (!target?.closest('.host-tabbar')) return;
      event.preventDefault();
      const nextTab = event.key === 'ArrowRight' ? 'kyc' : 'bank';
      setTab(nextTab);
      (nextTab === 'bank' ? bankTabRef.current : kycTabRef.current)?.focus();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, []);

  return (
    <div>
      <nav className="host-tabbar" aria-label={t('tabs.navigation')} role="tablist">
        <Button
          id="consumer-bank-tab"
          ref={bankTabRef}
          variant="tertiary"
          size="md"
          className={tab === 'bank' ? 'host-tab host-tab-active' : 'host-tab'}
          role="tab"
          aria-selected={tab === 'bank'}
          aria-controls="consumer-bank-panel"
          tabIndex={tab === 'bank' ? 0 : -1}
          onClick={() => setTab('bank')}
        >
          {t('tabs.home')}
        </Button>
        <Button
          id="consumer-kyc-tab"
          ref={kycTabRef}
          variant="tertiary"
          size="md"
          className={tab === 'kyc' ? 'host-tab host-tab-active' : 'host-tab'}
          role="tab"
          aria-selected={tab === 'kyc'}
          aria-controls="consumer-kyc-panel"
          tabIndex={tab === 'kyc' ? 0 : -1}
          onClick={() => setTab('kyc')}
        >
          {t('tabs.identity')}
        </Button>
      </nav>

      <Suspense fallback={<div className="itunda-card skeleton" style={{ height: '300px', margin: '20px' }} role="status" aria-live="polite" aria-label={t('common.loading')} />}>
        {tab === 'bank' ? (
          <section id="consumer-bank-panel" role="tabpanel" aria-labelledby="consumer-bank-tab">
          <BankI18nProvider>
            <BankDashboard />
          </BankI18nProvider>
          </section>
        ) : (
          <section id="consumer-kyc-panel" role="tabpanel" aria-labelledby="consumer-kyc-tab">
          <KycI18nProvider>
            <div style={{ padding: '20px' }}><KycDashboard /></div>
          </KycI18nProvider>
          </section>
        )}
      </Suspense>
    </div>
  );
}
