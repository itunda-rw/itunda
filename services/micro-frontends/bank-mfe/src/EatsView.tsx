import { useState, useEffect } from 'react';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import {
  EATS_MEMBERSHIP_TIERS, fetchMyMembership, fetchMyPlatformMembership, PLATFORM_MEMBERSHIP_TIERS, subscribeMembership, subscribePlatformMembership,
  type EatsMembership, type PlatformMembership,
} from './lib/eatsMembership';
import { RestaurantOrdersView } from './EatsOrdersAndFavorites';
import { OrderFoodView } from './OrderFoodView';
import { GroupOrderView } from './GroupOrderView';
import { DeliverView } from './DeliverView';
import { DineInRestaurantOrdersView, DineInCustomerView } from './DineInView';

// Real Coupang 와우 (Wow)-style unconditional delivery-fee waiver (item 211) -- see
// lib/eats.ts's own doc comment. Deliberately a separate card from Eats Club below,
// not a replacement: this waives the fee at every restaurant, no merchant opt-in
// required, the same real broader guarantee Coupang Wow has over Baemin Club's
// participating-seller-only free delivery.
function PlatformMembershipCard() {
  const { t } = useI18n();
  const [membership, setMembership] = useState<PlatformMembership | null | undefined>(undefined);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    fetchMyPlatformMembership().then(setMembership).catch(() => setMembership(null));
  };
  useEffect(load, []);

  const isActive = membership != null && new Date(membership.activeUntil).getTime() > Date.now();

  const handleSubscribe = async (days: number) => {
    setBusy(true);
    setError(null);
    try {
      await subscribePlatformMembership(days);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  if (membership === undefined) return null;

  // Real bold hero-banner treatment (itunda Eats redesign, 2026-08-28) -- same real
  // copy/pricing as before, just matching the reference's own real Coupang WOW
  // banner visual weight (a real, already-live feature deserved better merchandising
  // than a plain subscribe card, not a new membership product).
  return (
    <div style={{ padding: '18px', marginBottom: '16px', borderRadius: 'var(--itunda-radius-lg)', background: 'linear-gradient(135deg, var(--itunda-indigo), var(--itunda-indigo-active))', color: 'var(--itunda-white)' }}>
      <h3 style={{ fontSize: 'var(--itunda-type-scale-18-size)', fontWeight: 800, marginBottom: '4px' }}>⚡ itunda Plus</h3>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-white)', opacity: 0.9, marginBottom: '8px' }} role="alert">{error}</p>}
      {isActive ? (
        <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', opacity: 0.9 }}>
          Free delivery active until {new Date(membership!.activeUntil).toLocaleDateString()} at every restaurant, no participation required.
        </p>
      ) : (
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', opacity: 0.9, marginBottom: '12px' }}>
            Free delivery at every restaurant, every order — no minimum, no restaurant opt-in required.
          </p>
          <div style={{ display: 'flex', gap: '8px' }}>
            {PLATFORM_MEMBERSHIP_TIERS.map((tier) => (
              <button
                key={tier.days}
                disabled={busy}
                onClick={() => handleSubscribe(tier.days)}
                style={{ flex: 1, fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, padding: '10px', borderRadius: 'var(--itunda-radius-md)', backgroundColor: 'var(--itunda-white)', color: 'var(--itunda-indigo)' }}
              >
                {busy ? '…' : `${tier.days} days -- ${tier.priceRwf.toLocaleString()} RWF`}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

// Real Baemin Club (배민클럽)-style free-delivery membership -- see lib/eats.ts's own
// doc comment. First client UI for this backend feature (item 102, found backend-only
// via a fresh matrix scan for still-open "no client UI yet" notes).
function EatsMembershipCard() {
  const { t } = useI18n();
  const [membership, setMembership] = useState<EatsMembership | null | undefined>(undefined);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    fetchMyMembership().then(setMembership).catch(() => setMembership(null));
  };
  useEffect(load, []);

  const isActive = membership != null && new Date(membership.activeUntil).getTime() > Date.now();

  const handleSubscribe = async (days: number) => {
    setBusy(true);
    setError(null);
    try {
      await subscribeMembership(days);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  if (membership === undefined) return null;

  return (
    <div className="itunda-card" style={{ padding: '16px', marginBottom: '16px' }}>
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>Eats Club</h3>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{error}</p>}
      {isActive ? (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>
          Free delivery active until {new Date(membership!.activeUntil).toLocaleDateString()} at participating restaurants.
        </p>
      ) : (
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginBottom: '10px' }}>
            Free delivery at participating restaurants -- no minimum order.
          </p>
          <div style={{ display: 'flex', gap: '8px' }}>
            {EATS_MEMBERSHIP_TIERS.map((tier) => (
              <button
                key={tier.days}
                className="itunda-btn itunda-btn-primary"
                disabled={busy}
                onClick={() => handleSubscribe(tier.days)}
                style={{ flex: 1, fontSize: 'var(--itunda-type-scale-13-size)' }}
              >
                {busy ? '…' : `${tier.days} days -- ${tier.priceRwf.toLocaleString()} RWF`}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

export function EatsView({ onMessageSeller }: { onMessageSeller: (conversationId: string) => void }) {
  // Real fix (2026-08-19) -- same gap and same fix as MessagesView's identical
  // joinChatCode handling: a tapped ?joinEatsCode= link needs the Together-order sub-tab
  // pre-selected or GroupOrderView (which owns the actual auto-join effect) never mounts.
  const [mode, setMode] = useState<'ORDER' | 'TOGETHER' | 'DELIVER' | 'DINE_IN'>(() =>
    new URLSearchParams(window.location.search).has('joinEatsCode') ? 'TOGETHER' : 'ORDER'
  );

  return (
    <div>
      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--itunda-grey-100)', borderRadius: '10px', overflowX: 'auto' }}>
        {(['ORDER', 'TOGETHER', 'DELIVER', 'DINE_IN'] as const).map((v) => (
          <button
            key={v}
            onClick={() => setMode(v)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, whiteSpace: 'nowrap',
              color: mode === v ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
              backgroundColor: mode === v ? 'var(--itunda-indigo)' : 'transparent',
            }}
          >
            {v === 'ORDER' ? 'Order food' : v === 'TOGETHER' ? 'Together order' : v === 'DELIVER' ? 'Deliver' : 'Dine-in'}
          </button>
        ))}
      </div>
      {mode === 'ORDER' ? (
        <div>
          <PlatformMembershipCard />
          <EatsMembershipCard />
          <RestaurantOrdersView />
          <OrderFoodView onMessageSeller={onMessageSeller} />
        </div>
      ) : mode === 'TOGETHER' ? (
        <GroupOrderView />
      ) : mode === 'DELIVER' ? (
        <DeliverView />
      ) : (
        <div>
          <DineInRestaurantOrdersView />
          <DineInCustomerView />
        </div>
      )}
    </div>
  );
}
