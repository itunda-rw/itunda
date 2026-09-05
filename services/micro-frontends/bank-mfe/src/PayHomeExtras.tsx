import { useEffect, useRef, type ReactElement } from 'react';
import { Bike as MotoIcon, Bus as BusIcon, Clock, Gift as GiftIcon, HandCoins, Navigation, RefreshCw, ScanFace, Timer, Wallet as AccountIcon } from 'lucide-react';
import { IconChevronRight } from './icons/ItundaIcons';
import maplibregl from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { TILES_SOURCE_URL } from './lib/maps';
import type { RewardTasksResult } from './lib/rewards';
import type { NearbyMerchant } from './lib/shopping';

// Real Toss Pay home reference (4 screenshots, 2026-08-22, direct user follow-up:
// "it should look 100% like toss pay UI/UX features everything") -- split out of
// PayHub (BankDashboard.tsx) to keep that file under its file-size-lint baseline,
// matching the same convention PayMoneyDetail.tsx already established for this file.
// Every section here is real itunda data -- see each component's own doc comment for
// exactly what backs it and what's honestly scoped out (no real backend): the
// reference's cross-merchant coupon wallet and external online-merchant integrations.

// Real MerchantDiscoveryService.nearby -- every ACTIVE merchant within radiusKm,
// distinct from the existing fetchNearbyAds rail (that's paid ad placements, a
// subset -- this is the real total). Every merchant earns the payer real cashback on
// collect() (ShoppingCashbackService.DEFAULT_CASHBACK_RATE), so "earn cashback" is a
// true claim for all of them, not just FacePay-enrolled ones.
export const averageCashbackRatePercent = (merchants: NearbyMerchant[]): number | null =>
  merchants.length > 0 ? Math.round((merchants.reduce((sum, m) => sum + m.cashbackRate, 0) / merchants.length) * 1000) / 10 : null;

// Same self-hosted vector tile style LiveRiderMap.tsx already established (background/
// landcover/water/roads only -- no route layer needed here, this map has no route to
// draw). Duplicated rather than shared, matching this codebase's own small-per-file-
// duplication precedent for style objects (LiveRiderMap's own header comment).
const NEARBY_MAP_STYLE: maplibregl.StyleSpecification = {
  version: 8,
  sources: { rwanda: { type: 'vector', tiles: [TILES_SOURCE_URL], minzoom: 0, maxzoom: 14 } },
  layers: [
    { id: 'background', type: 'background', paint: { 'background-color': '#f2efe9' } },
    { id: 'landcover', type: 'fill', source: 'rwanda', 'source-layer': 'landcover', paint: { 'fill-color': '#d8e8c8', 'fill-opacity': 0.6 } },
    { id: 'water', type: 'fill', source: 'rwanda', 'source-layer': 'water', paint: { 'fill-color': '#a8d0e6' } },
    {
      id: 'transportation', type: 'line', source: 'rwanda', 'source-layer': 'transportation',
      paint: { 'line-color': '#ffffff', 'line-width': ['interpolate', ['linear'], ['zoom'], 8, 0.5, 16, 3] },
    },
  ],
};

// Real Toss Bank reference (2 more screenshots, 2026-08-23, direct user follow-up:
// "map view etc as it's in that screen") -- a real embedded map, not the flat pill
// banner this replaces. Reuses LiveRiderMap.tsx's own real MapLibre + self-hosted
// tile pattern (already a real dependency, already live-verified for ride tracking)
// rather than starting from scratch. Deliberately non-interactive (pan/zoom/rotate
// all disabled) -- this is a compact PREVIEW, not itunda's real full Explore Map
// destination (a separate, heavier screen); tapping still opens the same real
// NearbyMerchantsDialog list as before, not an attempt to rebuild Explore here.
export function NearbyMerchantsMap({ merchants, userLocation, onTap }: { merchants: NearbyMerchant[]; userLocation: { latitude: number; longitude: number } | null; onTap: () => void }) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);

  useEffect(() => {
    if (!containerRef.current || !userLocation) return;
    const map = new maplibregl.Map({
      container: containerRef.current,
      style: NEARBY_MAP_STYLE,
      center: [userLocation.longitude, userLocation.latitude],
      zoom: 14,
      attributionControl: false,
      interactive: false,
    });
    mapRef.current = map;
    new maplibregl.Marker({ color: 'var(--itunda-indigo)' }).setLngLat([userLocation.longitude, userLocation.latitude]).addTo(map);
    merchants.slice(0, 30).forEach((m) => {
      const el = document.createElement('div');
      el.textContent = '🏪';
      el.style.fontSize = '18px';
      new maplibregl.Marker({ element: el }).setLngLat([m.longitude, m.latitude]).addTo(map);
    });
    return () => {
      map.remove();
      mapRef.current = null;
    };
  }, [userLocation, merchants]);

  if (!userLocation || merchants.length === 0) return null;
  return (
    <button onClick={onTap} style={{ position: 'relative', width: '100%', height: '150px', borderRadius: '16px', overflow: 'hidden', display: 'block' }}>
      <div ref={containerRef} style={{ width: '100%', height: '100%' }} />
      <span
        style={{
          position: 'absolute', left: '10px', bottom: '10px', right: '10px',
          display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '8px',
          padding: '10px 14px', borderRadius: '999px', backgroundColor: 'rgba(25,31,40,0.85)', backdropFilter: 'blur(4px)',
        }}
      >
        <span style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 600, color: '#fff' }}>
          <Navigation size={15} color="#fff" />
          {merchants.length} itunda merchant{merchants.length === 1 ? '' : 's'} nearby — earn cashback
        </span>
        <IconChevronRight size={16} color="rgba(255,255,255,0.7)" />
      </span>
    </button>
  );
}

// Real destination for NearbyMerchantsMap's tap -- a plain list of the same real
// merchants (name, category, distance, real per-merchant cashback rate), not a dead
// link. itunda's real full interactive map (pan/zoom/search) still lives on its own
// separate, heavier screen (the Explore tab's Map destination); this stays a
// lightweight preview + list, matching this file's own "preview, not the full
// destination" scope elsewhere (RewardsPreviewSection, payment history on PayHub
// itself).
export function NearbyMerchantsDialog({ merchants, onClose }: { merchants: NearbyMerchant[]; onClose: () => void }) {
  return (
    <div style={{ position: 'fixed', inset: 0, backgroundColor: 'rgba(0,0,0,0.4)', display: 'flex', alignItems: 'flex-end', zIndex: 50 }} onClick={onClose}>
      <div
        style={{ background: 'var(--itunda-white)', borderRadius: '20px 20px 0 0', padding: '20px', width: '100%', maxHeight: '70vh', overflowY: 'auto' }}
        onClick={(e) => e.stopPropagation()}
      >
        <h3 style={{ fontSize: 'var(--itunda-type-scale-17-size)', fontWeight: 700, marginBottom: '12px' }}>Merchants nearby</h3>
        {merchants.slice(0, 20).map((m) => (
          <div key={m.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 0', borderBottom: '1px solid var(--itunda-grey-100)' }}>
            <div>
              <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 600 }}>{m.businessName}</p>
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{m.category ?? 'Merchant'}</p>
            </div>
            <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>{m.distanceKm.toFixed(1)} km</span>
          </div>
        ))}
      </div>
    </div>
  );
}

// FacePay's own real enroll/disable toggle. cashbackRatePercent is derived from real
// fetched nearby-merchant data (ShoppingCashbackService's own per-merchant rate) --
// never hardcoded, so it only shows once real data has actually loaded. Stated as a
// general "earn on payments" fact (true regardless of FacePay enrollment, since
// cashback applies to every collect() channel), not a fabricated FacePay-exclusive
// rate the way the reference's own "Earning 3%" implies for real Toss.
export function FacePayStatusRow({ enrolled, busy, cashbackRatePercent, onToggle }: { enrolled: boolean; busy: boolean; cashbackRatePercent: number | null; onToggle: () => void }) {
  const toggleLabel = enrolled ? 'Turn off' : 'Enroll';
  const enrolledLabel = enrolled ? 'Enrolled' : 'Not enrolled';
  let subtitle = enrolledLabel;
  if (cashbackRatePercent != null) {
    subtitle = `${enrolledLabel} — earn ${cashbackRatePercent}% cashback on payments${enrolled ? '' : ' either way'}`;
  } else if (enrolled) {
    subtitle = 'Enrolled — pay with your face at any itunda merchant';
  }
  return (
    <div className="itunda-flat-section" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
        <div style={{ width: '38px', height: '38px', borderRadius: '999px', backgroundColor: enrolled ? 'var(--itunda-indigo)' : 'var(--itunda-grey-100)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
          <ScanFace size={19} color={enrolled ? '#fff' : 'var(--itunda-grey-500)'} />
        </div>
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>FacePay</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{subtitle}</p>
        </div>
      </div>
      <button className={enrolled ? 'itunda-btn itunda-btn-secondary' : 'itunda-btn itunda-btn-primary'} disabled={busy} onClick={onToggle} style={{ padding: '8px 14px', fontSize: 'var(--itunda-type-scale-12-size)', flexShrink: 0 }}>
        {busy ? '…' : toggleLabel}
      </button>
    </div>
  );
}

// Real "Rewards you received" summary -- rewardsTotal (RewardsService) plus the real
// itunda Pay balance. No coupon count -- itunda has no cross-merchant coupon wallet
// (coupons are scoped to one merchant at a time), the same honest scope-down this
// file applies everywhere else.
export function RewardsSummaryRow({ rewardsTotal, payBalance }: { rewardsTotal: number; payBalance: number | null }) {
  return (
    <div className="itunda-flat-section" style={{ display: 'flex', justifyContent: 'space-between' }}>
      <div>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Rewards earned</p>
        <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>{rewardsTotal.toLocaleString('en-US')} RWF</p>
      </div>
      {payBalance != null && (
        <div style={{ textAlign: 'right' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>itunda Pay balance</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>{payBalance.toLocaleString('en-US')} RWF</p>
        </div>
      )}
    </div>
  );
}

// Real "FAQ / Send feedback" -- itunda has no FAQ-content system, so both honestly
// route to the real Support tab rather than fabricating static FAQ copy -- same real
// destination, shown as two rows to match the reference's own layout.
export function GetHelpLinks({ onOpenSupport }: { onOpenSupport: () => void }) {
  return (
    <div>
      <button onClick={onOpenSupport} style={{ display: 'block', width: '100%', textAlign: 'left', padding: '10px 4px', fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>
        FAQ
      </button>
      <button onClick={onOpenSupport} style={{ display: 'block', width: '100%', textAlign: 'left', padding: '10px 4px', fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>
        Send feedback
      </button>
    </div>
  );
}

// Real "list of other pay services" (2026-08-26 redesign, direct user instruction:
// "list of other pay services as itunda bank hub does"), the exact same flat-row
// shape itunda Bank hub's own CooperativeSavingsRail uses (icon-in-tinted-square +
// title + subtitle inside .itunda-flat-section) -- see PayHub's own doc comment in
// BankDashboard.tsx for the full account. Split out here (not left in
// BankDashboard.tsx) to stay under this file's own file-size-lint baseline, same
// convention this file's own doc comment above already established. Each row scrolls
// smoothly to its own already-real section rendered further down PayHub, the same
// "rail links to real content, doesn't duplicate it" shape CooperativeSavingsRail
// itself uses. `onNavigateToTab` is narrowed to the one literal tab this rail ever
// navigates to, rather than importing BankDashboard's full `Tab` union back into this
// file (which is imported BY BankDashboard.tsx -- that would be circular).
export function PayHubOtherServicesRail({ onCardsClick, onTransitClick, onMotoFareClick, onNavigateToTab }: { onCardsClick: () => void; onTransitClick: () => void; onMotoFareClick: () => void; onNavigateToTab: (tab: 'REWARDS') => void }) {
  const items: { key: string; title: string; subtitle: string; icon: ReactElement; tint: string; onClick: () => void }[] = [
    { key: 'cards', title: 'Cards', subtitle: 'Manage your itunda cards', icon: <AccountIcon size={20} color="#8A2BE2" />, tint: 'rgba(138, 43, 226, 0.12)', onClick: onCardsClick },
    // Real Kigali public-transit stored-value balance (2026-08-27) -- see
    // lib/transit.ts's own doc comment for the full sourced account.
    { key: 'transit', title: 'Transit', subtitle: 'Top up and tap to pay your real Kigali bus fare', icon: <BusIcon size={20} color="#2F8F5B" />, tint: 'rgba(47, 143, 91, 0.12)', onClick: onTransitClick },
    // Real moto-taxi fare tap-collection (2026-08-27) -- see lib/motoFare.ts's own
    // doc comment for the full sourced account.
    { key: 'moto_fare', title: 'Collect a moto fare', subtitle: 'Drivers: tap or scan a rider’s code to collect a real fare', icon: <MotoIcon size={20} color="#D97706" />, tint: 'rgba(217, 119, 6, 0.12)', onClick: onMotoFareClick },
    { key: 'request_money', title: 'Request money', subtitle: 'Ask someone to send you a specific amount', icon: <HandCoins size={20} color="#14AE85" />, tint: 'rgba(20, 174, 133, 0.12)', onClick: () => document.getElementById('pay-request-money-section')?.scrollIntoView({ behavior: 'smooth' }) },
    { key: 'scheduled', title: 'Scheduled transfers', subtitle: 'Send on a future date, one time', icon: <Clock size={20} color="var(--itunda-indigo)" />, tint: 'var(--itunda-indigo-light)', onClick: () => document.getElementById('pay-scheduled-transfers-section')?.scrollIntoView({ behavior: 'smooth' }) },
    { key: 'delayed', title: 'Delayed transfers', subtitle: 'A short grace period to cancel before it sends', icon: <Timer size={20} color="#F2A93B" />, tint: 'rgba(242, 169, 59, 0.14)', onClick: () => document.getElementById('pay-delayed-transfers-section')?.scrollIntoView({ behavior: 'smooth' }) },
    { key: 'auto_topup', title: 'Auto top-up', subtitle: 'Automatically refill from a linked account', icon: <RefreshCw size={20} color="#7C5CFC" />, tint: 'rgba(124, 92, 252, 0.12)', onClick: () => document.getElementById('pay-auto-topup-section')?.scrollIntoView({ behavior: 'smooth' }) },
    { key: 'rewards', title: 'Rewards', subtitle: 'Cashback and reward tasks', icon: <GiftIcon size={20} color="#E0507A" />, tint: 'rgba(224, 80, 122, 0.12)', onClick: () => onNavigateToTab('REWARDS') },
  ];

  return (
    <div className="itunda-flat-section">
      <h3 style={{ fontSize: 'var(--itunda-type-scale-17-size)', fontWeight: 700, margin: 0, color: 'var(--itunda-grey-900)' }}>Other pay services</h3>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', marginTop: '12px' }}>
        {items.map((item) => (
          <button
            key={item.key}
            onClick={item.onClick}
            style={{ display: 'flex', alignItems: 'center', gap: '12px', padding: '10px 8px', borderRadius: '10px', textAlign: 'left', width: '100%' }}
          >
            <div style={{ width: '38px', height: '38px', borderRadius: '12px', backgroundColor: item.tint, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
              {item.icon}
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: '14.5px', fontWeight: 650, color: 'var(--itunda-grey-900)' }}>{item.title}</div>
              <div style={{ fontSize: '12.5px', color: 'var(--itunda-grey-500)' }}>{item.subtitle}</div>
            </div>
          </button>
        ))}
      </div>
    </div>
  );
}

export function RewardsPreviewSection({ tasks, onViewAll }: { tasks: RewardTasksResult; onViewAll: () => void }) {
  const preview = tasks.tasks.filter((t) => !t.claimed && t.eligible).slice(0, 3);
  if (preview.length === 0) return null;
  return (
    <div className="itunda-flat-section">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: '4px' }}>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700, margin: 0 }}>Get more rewards</h3>
        <button onClick={onViewAll} style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>View all</button>
      </div>
      {preview.map((t) => (
        <div key={t.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 0' }}>
          <div>
            <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 600 }}>{t.title}</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{t.subtitle}</p>
          </div>
          <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: 'var(--itunda-indigo)' }}>+{t.rewardAmount.toLocaleString('en-US')} RWF</span>
        </div>
      ))}
    </div>
  );
}
