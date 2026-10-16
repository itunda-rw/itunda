import { lazy, Suspense } from 'react';

// Fixed (2026-07-11): this file used to render Toss Payments' real SDK/widget
// (@tosspayments/payment-widget-sdk, a real public Toss test key) while the UI text
// claimed "Secure payments via MTN MoMo, Airtel Money, and Bank Transfer" -- a widget
// that can only actually process Toss's own Korean payment methods, not Rwandan
// rails. Removed entirely rather than fixed in place; itunda needs its own payment
// integration, not Toss Payments'.
//
// The host shell must not impose a second, unrelated two-tab navigation over the
// consumer app. The actual consumer navigation and its domain screens live in the
// bank MFE (Home / Pay / Explore / Messages / You); wrapping it with a separate
// Bank / Identity tab bar made the production app structurally different from the
// v104 clickable prototype and hid the consumer app's own navigation hierarchy.
const BankDashboard = lazy(() => import('bank_mfe/BankDashboard'));
const BankI18nProvider = lazy(() => import('bank_mfe/I18nProvider'));

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

  return (
    <Suspense fallback={<div className="itunda-card skeleton" style={{ height: '300px', margin: '20px' }} role="status" aria-live="polite" aria-label="Loading Itunda" />}>
      <BankI18nProvider>
        <BankDashboard />
      </BankI18nProvider>
    </Suspense>
  );
}
