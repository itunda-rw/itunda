import { useEffect, useState } from 'react';
import { ApiError } from '../lib/api';
import { EmptyState } from '../components/EmptyState';
import { createCoupon, deactivateCoupon, fetchMyCoupons, type CouponDiscountType, type MerchantCoupon } from '../lib/merchant';
import { useI18n } from '../i18n/I18nContext';

// Real merchant coupons + 단골 loyalty gating (item 146) -- see lib/merchant.ts's own
// doc comment. Merchant-owner-facing create/list/deactivate half only; a coupon
// redeems against a real Pay-by-code payment, not here.
export default function CouponsScreen() {
  const { t } = useI18n();
  const [coupons, setCoupons] = useState<MerchantCoupon[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMyCoupons()
      .then(setCoupons)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('coupons.loadError')));
  };

  useEffect(load, []);

  return (
    <div style={{ maxWidth: '480px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
      <CreateCouponCard onCreated={load} />

      <div className="itunda-card">
        <h2 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '4px' }}>{t('coupons.title')}</h2>
        <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)', marginBottom: '16px' }}>
          {t('coupons.body')}
        </p>
        {error && (
          <p style={{ fontSize: '13px', color: 'var(--itunda-red)', marginBottom: '12px' }} role="alert">{error}</p>
        )}
        {coupons === null ? (
          <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>{t('coupons.loading')}</p>
        ) : // Real copy-voice fix (item 244, round 6 of the empty-state pass): points
        // back to the real CreateCouponCard form right above.
        coupons.length === 0 ? (
          <EmptyState message={t('coupons.empty')} />
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
  const { t } = useI18n();
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
      setError(err instanceof ApiError ? err.message : t('coupons.createError'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="itunda-card" style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      <h2 style={{ fontSize: '16px', fontWeight: 700 }}>{t('coupons.createTitle')}</h2>
      <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
        <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('coupons.titleLabel')}</span>
        <input
          type="text"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder={t('coupons.titlePlaceholder')}
          required
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
        />
      </label>
      <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
        <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('coupons.descriptionLabel')}</span>
        <input
          type="text"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder={t('coupons.descriptionPlaceholder')}
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
        />
      </label>
      <div style={{ display: 'flex', gap: '10px' }}>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 1 }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('coupons.discountTypeLabel')}</span>
          <select
            value={discountType}
            onChange={(e) => setDiscountType(e.target.value as CouponDiscountType)}
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
          >
            <option value="PERCENT">{t('coupons.discountTypePercent')}</option>
            <option value="FIXED_AMOUNT">{t('coupons.discountTypeFixed')}</option>
          </select>
        </label>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 1 }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>
            {discountType === 'PERCENT' ? t('coupons.percentLabel') : t('coupons.fixedAmountLabel')}
          </span>
          <input
            type="number"
            min="1"
            max={discountType === 'PERCENT' ? '100' : undefined}
            value={discountValue}
            onChange={(e) => setDiscountValue(e.target.value)}
            placeholder={discountType === 'PERCENT' ? '10' : '1000'}
            required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
          />
        </label>
      </div>
      <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
        <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('coupons.expiresLabel')}</span>
        <input
          type="date"
          value={expiresAt}
          onChange={(e) => setExpiresAt(e.target.value)}
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
        />
      </label>
      <label style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>
        <input type="checkbox" checked={regularsOnly} onChange={(e) => setRegularsOnly(e.target.checked)} />
        {t('coupons.regularsOnlyLabel')}
      </label>
      {error && (
        <p style={{ fontSize: '13px', color: 'var(--itunda-red)', margin: 0 }} role="alert">{error}</p>
      )}
      <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting}>
        {submitting ? t('coupons.creating') : t('coupons.createButton')}
      </button>
    </form>
  );
}

function CouponRow({ coupon, onChanged }: { coupon: MerchantCoupon; onChanged: () => void }) {
  const { t } = useI18n();
  const [deactivating, setDeactivating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleDeactivate = async () => {
    setError(null);
    setDeactivating(true);
    try {
      await deactivateCoupon(coupon.id);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('coupons.deactivateError'));
    } finally {
      setDeactivating(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', padding: '12px', background: 'var(--itunda-grey-100)', borderRadius: '10px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <p style={{ fontSize: '14px', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>{coupon.title}</p>
        <span style={{ fontSize: '12px', fontWeight: 600, color: coupon.active ? 'var(--itunda-indigo)' : 'var(--itunda-grey-500)' }}>
          {coupon.active ? t('coupons.statusActive') : t('coupons.statusDeactivated')}
        </span>
      </div>
      {coupon.description && <p style={{ fontSize: '13px', color: 'var(--itunda-grey-700)' }}>{coupon.description}</p>}
      <p style={{ fontSize: '13px', color: 'var(--itunda-grey-700)' }}>
        {coupon.discountType === 'PERCENT' ? t('coupons.percentOff', { value: coupon.discountValue }) : t('coupons.fixedOff', { value: coupon.discountValue.toLocaleString() })}
        {coupon.regularsOnly ? t('coupons.regularsOnlySuffix') : ''}
        {coupon.expiresAt ? t('coupons.expiresSuffix', { date: new Date(coupon.expiresAt).toLocaleDateString() }) : ''}
      </p>
      {error && (
        <p style={{ fontSize: '12px', color: 'var(--itunda-red)', margin: 0 }} role="alert">{error}</p>
      )}
      {coupon.active && (
        <button
          className="itunda-btn itunda-btn-secondary"
          style={{ alignSelf: 'flex-start', padding: '6px 10px', fontSize: '12px', marginTop: '4px' }}
          disabled={deactivating}
          onClick={handleDeactivate}
        >
          {deactivating ? '…' : t('coupons.deactivateButton')}
        </button>
      )}
    </div>
  );
}
