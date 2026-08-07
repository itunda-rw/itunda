import { useEffect, useState } from 'react';
import { ApiError } from '../lib/api';
import { EmptyState } from '../components/EmptyState';
import { createCoupon, deactivateCoupon, fetchMyCoupons, type CouponDiscountType, type MerchantCoupon } from '../lib/merchant';

// Real merchant coupons + 단골 loyalty gating (item 146) -- see lib/merchant.ts's own
// doc comment. Merchant-owner-facing create/list/deactivate half only; a coupon
// redeems against a real Pay-by-code payment, not here.
export default function CouponsScreen() {
  const [coupons, setCoupons] = useState<MerchantCoupon[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMyCoupons()
      .then(setCoupons)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your coupons.'));
  };

  useEffect(load, []);

  return (
    <div style={{ maxWidth: '480px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
      <CreateCouponCard onCreated={load} />

      <div className="toss-card">
        <h2 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '4px' }}>Your coupons</h2>
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '16px' }}>
          A customer applies a coupon when paying by code — it's redeemed once per customer.
        </p>
        {error && (
          <p style={{ fontSize: '13px', color: '#E53935', marginBottom: '12px' }} role="alert">{error}</p>
        )}
        {coupons === null ? (
          <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>Loading…</p>
        ) : // Real copy-voice fix (item 244, round 6 of the empty-state pass): points
        // back to the real CreateCouponCard form right above.
        coupons.length === 0 ? (
          <EmptyState message="No coupons yet — use the form above to create your first one." />
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {coupons.map((coupon) => (
              <CouponRow key={coupon.id} coupon={coupon} onChanged={load} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function CreateCouponCard({ onCreated }: { onCreated: () => void }) {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [discountType, setDiscountType] = useState<CouponDiscountType>('PERCENT');
  const [discountValue, setDiscountValue] = useState('');
  const [regularsOnly, setRegularsOnly] = useState(false);
  const [expiresAt, setExpiresAt] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await createCoupon(
        title.trim(),
        description.trim() || undefined,
        discountType,
        Number(discountValue),
        regularsOnly,
        expiresAt ? new Date(expiresAt).toISOString() : undefined,
      );
      setTitle('');
      setDescription('');
      setDiscountValue('');
      setRegularsOnly(false);
      setExpiresAt('');
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create this coupon.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      <h2 style={{ fontSize: '16px', fontWeight: 700 }}>Create a coupon</h2>
      <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
        <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>Title</span>
        <input
          type="text"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="10% off your next visit"
          required
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '15px' }}
        />
      </label>
      <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
        <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>Description (optional)</span>
        <input
          type="text"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="Valid on any purchase"
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '15px' }}
        />
      </label>
      <div style={{ display: 'flex', gap: '10px' }}>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 1 }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>Discount type</span>
          <select
            value={discountType}
            onChange={(e) => setDiscountType(e.target.value as CouponDiscountType)}
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '15px' }}
          >
            <option value="PERCENT">Percent off</option>
            <option value="FIXED_AMOUNT">Fixed amount off</option>
          </select>
        </label>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 1 }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>
            {discountType === 'PERCENT' ? 'Percent (1-100)' : 'Amount (RWF)'}
          </span>
          <input
            type="number"
            min="1"
            max={discountType === 'PERCENT' ? '100' : undefined}
            value={discountValue}
            onChange={(e) => setDiscountValue(e.target.value)}
            placeholder={discountType === 'PERCENT' ? '10' : '1000'}
            required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '15px' }}
          />
        </label>
      </div>
      <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
        <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>Expires (optional)</span>
        <input
          type="date"
          value={expiresAt}
          onChange={(e) => setExpiresAt(e.target.value)}
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '15px' }}
        />
      </label>
      <label style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>
        <input type="checkbox" checked={regularsOnly} onChange={(e) => setRegularsOnly(e.target.checked)} />
        Reserve for regular customers only (3+ past payments)
      </label>
      {error && (
        <p style={{ fontSize: '13px', color: '#E53935', margin: 0 }} role="alert">{error}</p>
      )}
      <button type="submit" className="toss-btn toss-btn-primary" disabled={submitting}>
        {submitting ? 'Creating…' : 'Create coupon'}
      </button>
    </form>
  );
}

function CouponRow({ coupon, onChanged }: { coupon: MerchantCoupon; onChanged: () => void }) {
  const [deactivating, setDeactivating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleDeactivate = async () => {
    setError(null);
    setDeactivating(true);
    try {
      await deactivateCoupon(coupon.id);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not deactivate this coupon.');
    } finally {
      setDeactivating(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', padding: '12px', background: 'var(--toss-grey-100)', borderRadius: '10px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <p style={{ fontSize: '14px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{coupon.title}</p>
        <span style={{ fontSize: '12px', fontWeight: 600, color: coupon.active ? 'var(--toss-blue)' : 'var(--toss-grey-500)' }}>
          {coupon.active ? 'Active' : 'Deactivated'}
        </span>
      </div>
      {coupon.description && <p style={{ fontSize: '13px', color: 'var(--toss-grey-700)' }}>{coupon.description}</p>}
      <p style={{ fontSize: '13px', color: 'var(--toss-grey-700)' }}>
        {coupon.discountType === 'PERCENT' ? `${coupon.discountValue}% off` : `${coupon.discountValue.toLocaleString()} RWF off`}
        {coupon.regularsOnly ? ' · Regulars only' : ''}
        {coupon.expiresAt ? ` · Expires ${new Date(coupon.expiresAt).toLocaleDateString()}` : ''}
      </p>
      {error && (
        <p style={{ fontSize: '12px', color: '#E53935', margin: 0 }} role="alert">{error}</p>
      )}
      {coupon.active && (
        <button
          className="toss-btn toss-btn-secondary"
          style={{ alignSelf: 'flex-start', padding: '6px 10px', fontSize: '12px', marginTop: '4px' }}
          disabled={deactivating}
          onClick={handleDeactivate}
        >
          {deactivating ? '…' : 'Deactivate'}
        </button>
      )}
    </div>
  );
}
