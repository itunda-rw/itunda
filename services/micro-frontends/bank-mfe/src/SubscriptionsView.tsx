// Extracted from BankDashboard.tsx (2026-08-30, itunda-vs-Toss architecture
// comparison thread's own open recommendation 4 -- see
// project_itunda_architecture_vs_toss.md, ARCHITECTURE_GUIDELINES.md §2). Real
// Toss "구독 관리" recurring-payment detection + merchant billing plans (own
// lib/account.ts + lib/shopping.ts exports, exactly one external call site --
// `{tab === 'SUBSCRIPTIONS' && <SubscriptionsView />}`).

import { useEffect, useState } from 'react';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { EmptyState } from './EmptyState';
import { fetchSubscriptions, type DetectedSubscription } from './lib/account';
import { cancelBillingSubscription, fetchMyBillingSubscriptions, type MerchantBillingSubscription } from './lib/shopping';

// Real recurring-payment ("subscription") detection (2026-07-26) -- see
// SubscriptionDetectionService's own doc comment for the real Toss "구독 관리"
// capability this closes, including the same-day price-change alert. Found with zero
// client UI anywhere.
export function SubscriptionsView() {
  const { t } = useI18n();
  const [subscriptions, setSubscriptions] = useState<DetectedSubscription[] | null>(null);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [billingSubs, setBillingSubs] = useState<MerchantBillingSubscription[] | null>(null);
  const [billingError, setBillingError] = useState<string | null>(null);

  const loadBilling = () => {
    setBillingError(null);
    fetchMyBillingSubscriptions()
      .then(setBillingSubs)
      .catch((err) => setBillingError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(() => {
    fetchSubscriptions()
      .then((r) => { setSubscriptions(r.subscriptions); setTotal(r.estimatedMonthlyTotal); })
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
    loadBilling();
  }, []);

  if (subscriptions === null) {
    return error ? <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p> : <div className="itunda-flat-section skeleton" style={{ height: '160px' }} />;
  }

  // Real fix (2026-08-24, flat-design sweep): dropped itunda-card wrapping
  // throughout -- Estimated monthly total/detected subscriptions/Merchant
  // subscriptions are real sections shown together, now separated by
  // itunda-flat-section's own border-bottom divider (docs/UI_UX_GUIDELINES.md §10).
  return (
    <div>
      <div className="itunda-flat-section">
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Estimated monthly total</p>
        <h2 style={{ fontSize: 'var(--itunda-type-scale-26-size)', fontWeight: 700 }}>{total.toLocaleString('en-US')} RWF</h2>
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Detected from your own real payment history, not a linked-card feed.</p>
      </div>
      {// Real copy-voice fix (item 244, round 6 of the empty-state pass): this is
      // auto-detected from real payment history (see the real-data note above), not
      // a user-initiated setup step -- copy says so honestly instead of implying a
      // missing action.
      subscriptions.length === 0 ? (
        <EmptyState message="No recurring payments detected yet — once a payment repeats a few times, it'll show up here." />
      ) : (
        subscriptions.map((s) => (
          <div key={`${s.displayName}-${s.cadence}`} className="itunda-flat-section">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{s.displayName}</h4>
                <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{s.cadence === 'WEEKLY' ? 'Weekly' : 'Monthly'} · {s.occurrenceCount} payments seen</p>
              </div>
              <div style={{ textAlign: 'right' }}>
                <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{s.amount.toLocaleString('en-US')} RWF</p>
                {s.priceIncreased && s.previousAmount !== null && (
                  <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-red)' }}>↑ from {s.previousAmount.toLocaleString('en-US')} RWF</p>
                )}
              </div>
            </div>
          </div>
        ))
      )}

      {/* Real Kakao Pay 정기결제/Toss 빌링키-style merchant subscriptions the customer
          actually authorized (item 145) -- distinct from the detected-from-history
          section above: these are real active billing-key authorizations that charge
          automatically until cancelled, not a heuristic guess. */}
      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700, marginBottom: '4px' }}>Merchant subscriptions</h3>
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '12px' }}>
          Plans you've subscribed to. These charge your account automatically until you cancel.
        </p>
        {billingError && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{billingError}</p>}
        {billingSubs === null && !billingError ? (
          <div className="itunda-flat-section skeleton" style={{ height: '80px' }} />
        ) : billingSubs && billingSubs.length === 0 ? (
          <EmptyState message="No merchant subscriptions yet — plans you subscribe to will show up here." />
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {billingSubs?.map((sub) => (
              <MerchantBillingSubscriptionRow key={sub.id} subscription={sub} onChanged={loadBilling} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function MerchantBillingSubscriptionRow({ subscription, onChanged }: { subscription: MerchantBillingSubscription; onChanged: () => void }) {
  const { t } = useI18n();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleCancel = async () => {
    setError(null);
    setBusy(true);
    try {
      await cancelBillingSubscription(subscription.id);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', padding: '12px', background: 'var(--itunda-grey-100)', borderRadius: '10px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '10px' }}>
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{subscription.chargeCount} charge{subscription.chargeCount === 1 ? '' : 's'} so far</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
            {subscription.status === 'ACTIVE'
              ? `Next charge ${new Date(subscription.nextChargeAt).toLocaleDateString()}`
              : `Cancelled ${subscription.cancelledAt ? new Date(subscription.cancelledAt).toLocaleDateString() : ''}`}
          </p>
          {subscription.lastFailureReason && subscription.status === 'ACTIVE' && (
            <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-red)' }}>Last charge failed: {subscription.lastFailureReason}</p>
          )}
        </div>
        {subscription.status === 'ACTIVE' && (
          <button
            className="itunda-btn itunda-btn-secondary"
            style={{ padding: '6px 12px', fontSize: 'var(--itunda-type-scale-12-size)', whiteSpace: 'nowrap' }}
            disabled={busy}
            onClick={handleCancel}
          >
            {busy ? '…' : 'Cancel'}
          </button>
        )}
      </div>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', margin: 0 }} role="alert">{error}</p>}
    </div>
  );
}
