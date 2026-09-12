// Real "Coupon box" (itunda Pay redesign, 2026-08-28, direct user reference: real
// Toss Pay Coupon box screen). Backed by a genuinely new endpoint
// (GET /api/v1/merchant/coupons/browse) -- itunda had zero cross-merchant coupon
// read anywhere before this pass; every prior coupon read was scoped to one
// already-known merchant. Deliberately no curated Online/Offline/브랜드-campaign
// tabs (no real marketing partnerships to show) -- one flat, honest list of
// itunda's own real merchant coupons.
//
// "Used/expired" is sourced purely from real redemption history
// (GET /api/v1/merchant/coupons/my-redemptions), not from browse-endpoint rows past
// their expiresAt -- browseCoupons already excludes expired coupons server-side
// (MerchantCouponService.browseCoupons), so a coupon that expired unused simply
// never appears here at all, rather than fabricating an "expired" bucket the real
// data doesn't actually populate.

import { useEffect, useState } from 'react';
import { ApiError } from './lib/api';
import { browseCoupons, fetchMyCouponRedemptions, type CouponBrowseView, type CouponRedemption } from './lib/coupons';
import { useI18n } from './i18n/I18nContext';
import { IconBack } from './icons/ItundaIcons';
import { useDeferredLoading } from './useDeferredLoading';

function couponDiscountLabel(c: CouponBrowseView['coupon']) {
  return c.discountType === 'PERCENT' ? `${c.discountValue}% off` : `${c.discountValue.toLocaleString('en-US')} RWF off`;
}

export function CouponBoxView({ onBack, onBrowseMerchants }: { onBack: () => void; onBrowseMerchants: () => void }) {
  const { t } = useI18n();
  const [tab, setTab] = useState<'RECEIVED' | 'USED'>('RECEIVED');
  const [coupons, setCoupons] = useState<CouponBrowseView[] | null>(null);
  const [redemptions, setRedemptions] = useState<CouponRedemption[] | null>(null);
  const showSkeleton = useDeferredLoading(coupons === null || redemptions === null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([browseCoupons(), fetchMyCouponRedemptions()])
      .then(([c, r]) => { setCoupons(c); setRedemptions(r); })
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  }, [t]);

  const received = (coupons ?? []).filter((c) => c.eligible && !c.alreadyRedeemed);

  return (
    <div style={{ position: 'fixed', inset: 0, zIndex: 1000, backgroundColor: 'var(--itunda-white)', display: 'flex', flexDirection: 'column' }}>
      <div style={{ flex: 1, overflowY: 'auto' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', padding: '14px 16px' }}>
          <button onClick={onBack} aria-label="Back" style={{ display: 'flex', padding: '4px' }}>
            <IconBack size={24} color="var(--itunda-grey-900)" />
          </button>
          <h2 style={{ margin: 0, fontSize: 'var(--itunda-type-scale-17-size)', fontWeight: 700 }}>{t('pay.couponBoxTitle')}</h2>
        </div>
        <div style={{ padding: '0 20px' }}>
        <div role="tablist" style={{ display: 'flex', gap: '20px', borderBottom: '1px solid var(--itunda-grey-100)', marginBottom: '16px' }}>
          {(['RECEIVED', 'USED'] as const).map((key) => (
            <button
              key={key}
              role="tab"
              aria-selected={tab === key}
              onClick={() => setTab(key)}
              style={{
                background: 'none', border: 'none', padding: '4px 0 10px',
                fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: tab === key ? 700 : 400,
                color: tab === key ? 'var(--itunda-grey-900)' : 'var(--itunda-grey-500)',
                borderBottom: tab === key ? '2px solid var(--itunda-indigo)' : '2px solid transparent',
              }}
            >
              {key === 'RECEIVED' ? t('pay.couponsReceived', { count: received.length }) : t('pay.couponsUsedExpired', { count: (redemptions ?? []).length })}
            </button>
          ))}
        </div>

        {coupons === null || redemptions === null ? (
          showSkeleton ? <div className="skeleton" style={{ height: '160px', borderRadius: 'var(--itunda-radius-md)' }} /> : null
        ) : tab === 'RECEIVED' ? (
          received.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '48px 0' }}>
              <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '6px' }}>{t('pay.couponsEmptyTitle')}</p>
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginBottom: '16px' }}>{t('pay.couponsEmptySubtitle')}</p>
              <button className="itunda-btn itunda-btn-primary" onClick={onBrowseMerchants}>{t('pay.couponsFindNew')}</button>
            </div>
          ) : (
            received.map((c) => (
              <div key={c.coupon.id} style={{ padding: '12px 0', borderBottom: '1px solid var(--itunda-grey-100)' }}>
                <p style={{ margin: 0, fontWeight: 700, fontSize: 'var(--itunda-type-scale-14-size)' }}>{c.coupon.title}</p>
                <p style={{ margin: '2px 0 0', fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{c.merchantName} · {couponDiscountLabel(c.coupon)}</p>
                {c.coupon.expiresAt && (
                  <p style={{ margin: '2px 0 0', fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-400)' }}>{t('pay.couponExpires', { date: new Date(c.coupon.expiresAt).toLocaleDateString() })}</p>
                )}
              </div>
            ))
          )
        ) : (redemptions ?? []).length === 0 ? (
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', textAlign: 'center', padding: '32px 0' }}>{t('pay.couponsNoneUsed')}</p>
        ) : (
          (redemptions ?? []).map((r) => (
            <div key={r.id} style={{ display: 'flex', justifyContent: 'space-between', padding: '12px 0', borderBottom: '1px solid var(--itunda-grey-100)' }}>
              <p style={{ margin: 0, fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>{new Date(r.redeemedAt).toLocaleDateString()}</p>
              <p style={{ margin: 0, fontWeight: 700, fontSize: 'var(--itunda-type-scale-13-size)' }}>-{r.discountAmount.toLocaleString('en-US')} RWF</p>
            </div>
          ))
        )}
        {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginTop: '10px' }} role="alert">{error}</p>}
        </div>
      </div>
    </div>
  );
}
