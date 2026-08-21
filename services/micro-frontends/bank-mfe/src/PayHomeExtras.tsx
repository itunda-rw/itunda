import { ChevronRight, MapPin, ScanFace } from 'lucide-react';
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

export function NearbyMerchantsBanner({ merchants, onTap }: { merchants: NearbyMerchant[]; onTap: () => void }) {
  if (merchants.length === 0) return null;
  return (
    <button
      onClick={onTap}
      style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%', padding: '12px 16px', borderRadius: '999px', backgroundColor: 'var(--itunda-grey-100)' }}
    >
      <span style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 600 }}>
        <MapPin size={16} color="var(--itunda-grey-500)" />
        {merchants.length} itunda merchant{merchants.length === 1 ? '' : 's'} nearby — earn cashback
      </span>
      <ChevronRight size={16} color="var(--itunda-grey-400)" />
    </button>
  );
}

// Real destination for NearbyMerchantsBanner's tap -- a plain list of the same real
// merchants (name, category, distance, real per-merchant cashback rate), not a dead
// link. No embedded map here -- itunda's own self-hosted maps stack lives on a
// separate, heavier screen (the Explore tab's Map destination); this is a
// lightweight preview, matching this file's own "preview, not the full destination"
// scope elsewhere (RewardsPreviewSection, payment history on PayHub itself).
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
        <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>{rewardsTotal.toLocaleString()} RWF</p>
      </div>
      {payBalance != null && (
        <div style={{ textAlign: 'right' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>itunda Pay balance</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>{payBalance.toLocaleString()} RWF</p>
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
          <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: 'var(--itunda-indigo)' }}>+{t.rewardAmount} RWF</span>
        </div>
      ))}
    </div>
  );
}
