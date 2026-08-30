// Extracted from BankDashboard.tsx (2026-08-30, itunda-vs-Toss architecture
// comparison thread's own open recommendation 4 -- see
// project_itunda_architecture_vs_toss.md, ARCHITECTURE_GUIDELINES.md §2). Real
// Coupang 정기배송-style subscription list (own lib/productSubscriptions.ts data
// layer, exactly one external call site -- `<MyProductSubscriptionsCard />` inside
// MyView). Note: `subscribeToProduct` stays in BankDashboard.tsx's own import --
// `SubscribeAndSaveButton` (a different, unrelated call site on a Shop product
// page) is the real caller for that one export, not this card.

import { useEffect, useState } from 'react';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import {
  cancelProductSubscription, fetchMyProductSubscriptions, pauseProductSubscription, resumeProductSubscription,
  skipNextProductSubscriptionDelivery, updateProductSubscription,
  type ProductSubscription,
} from './lib/productSubscriptions';

const PRODUCT_SUBSCRIPTION_STATUS_LABEL: Record<ProductSubscription['status'], string> = {
  ACTIVE: 'Active', PAUSED: 'Paused', CANCELLED: 'Cancelled',
};

// Real Coupang 정기배송-style subscription list -- see lib/productSubscriptions.ts's
// own doc comment.
export function MyProductSubscriptionsCard() {
  const { t } = useI18n();
  const [subscriptions, setSubscriptions] = useState<ProductSubscription[] | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  // Real Coupang 정기배송 수량/주기 변경 -- inline edit, one subscription open at a time.
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editQuantity, setEditQuantity] = useState('');
  const [editIntervalDays, setEditIntervalDays] = useState('');

  const load = () => {
    fetchMyProductSubscriptions().then(setSubscriptions).catch(() => {});
  };

  useEffect(load, []);

  const handleToggle = async (s: ProductSubscription) => {
    setBusyId(s.id);
    setError(null);
    try {
      if (s.status === 'ACTIVE') await pauseProductSubscription(s.id);
      else await resumeProductSubscription(s.id);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  const handleCancel = async (id: string) => {
    setBusyId(id);
    setError(null);
    try {
      await cancelProductSubscription(id);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  // Real Coupang 정기배송 "건너뛰기" -- see lib/productSubscriptions.ts's own doc
  // comment. Found via scripts/uncalled-endpoint-sweep.py.
  const handleSkipNext = async (id: string) => {
    setBusyId(id);
    setError(null);
    try {
      await skipNextProductSubscriptionDelivery(id);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  const startEdit = (s: ProductSubscription) => {
    setEditingId(s.id);
    setEditQuantity(String(s.quantity));
    setEditIntervalDays(String(s.intervalDays));
    setError(null);
  };

  const handleSaveEdit = async (id: string) => {
    setBusyId(id);
    setError(null);
    try {
      await updateProductSubscription(id, Number(editQuantity), Number(editIntervalDays));
      setEditingId(null);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  if (!subscriptions || subscriptions.length === 0) return null;

  // Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- one of many
  // stacked sections on MyView's linear screen (docs/UI_UX_GUIDELINES.md §10).
  return (
    <div className="itunda-flat-section">
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '8px' }}>Subscribe & save</h3>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{error}</p>}
      {subscriptions.map((s) => (
        <div key={s.id} style={{ padding: '8px 0', borderTop: '1px solid var(--itunda-grey-100)' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>Qty {s.quantity} · every {s.intervalDays}d</p>
              <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>
                {s.status === 'CANCELLED' && s.cancelledAt
                  ? `Cancelled ${new Date(s.cancelledAt).toLocaleDateString()}`
                  : `${PRODUCT_SUBSCRIPTION_STATUS_LABEL[s.status]} · ${s.deliveryCount} delivered · next ${new Date(s.nextDeliveryAt).toLocaleDateString()}`}
              </p>
              {s.lastFailureReason && s.status === 'ACTIVE' && (
                <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-red)' }}>Last delivery failed: {s.lastFailureReason}</p>
              )}
            </div>
            {s.status !== 'CANCELLED' && (
              <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                {s.status === 'ACTIVE' && (
                  <button className="itunda-btn itunda-btn-secondary" disabled={busyId === s.id} onClick={() => handleSkipNext(s.id)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
                    {busyId === s.id ? '…' : 'Skip next'}
                  </button>
                )}
                <button className="itunda-btn itunda-btn-secondary" disabled={busyId === s.id} onClick={() => startEdit(s)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
                  Edit
                </button>
                <button className="itunda-btn itunda-btn-secondary" disabled={busyId === s.id} onClick={() => handleToggle(s)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
                  {busyId === s.id ? '…' : s.status === 'ACTIVE' ? 'Pause' : 'Resume'}
                </button>
                <button className="itunda-btn itunda-btn-secondary" disabled={busyId === s.id} onClick={() => handleCancel(s.id)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
                  Cancel
                </button>
              </div>
            )}
          </div>
          {editingId === s.id && (
            <div style={{ display: 'flex', gap: '6px', marginTop: '8px', alignItems: 'center' }}>
              <input
                type="number" min="1" value={editQuantity} onChange={(e) => setEditQuantity(e.target.value)}
                placeholder="Quantity"
                style={{ flex: 1, padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-12-size)' }}
              />
              <input
                type="number" min="1" value={editIntervalDays} onChange={(e) => setEditIntervalDays(e.target.value)}
                placeholder="Every N days"
                style={{ flex: 1, padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-12-size)' }}
              />
              <button
                className="itunda-btn itunda-btn-primary" disabled={busyId === s.id || !(Number(editQuantity) > 0) || !(Number(editIntervalDays) > 0)}
                onClick={() => handleSaveEdit(s.id)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '8px 12px' }}
              >
                {busyId === s.id ? '…' : 'Save'}
              </button>
              <button className="itunda-btn itunda-btn-secondary" onClick={() => setEditingId(null)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '8px 12px' }}>
                Cancel
              </button>
            </div>
          )}
        </div>
      ))}
    </div>
  );
}
