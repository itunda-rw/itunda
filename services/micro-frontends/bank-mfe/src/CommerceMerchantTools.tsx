// Extracted from CommerceOrders.tsx (2026-09-03, file-size-lint crossing 500 lines) --
// the two merchant-facing (not buyer-facing) tools on that file's own ShopView.tsx call
// site: the return/exchange approval queue and gift-voucher in-person redemption.
import { useEffect, useState } from 'react';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { fetchMerchantReturnQueue, decideOrderReturn, type OrderReturnRequestDto } from './lib/commerce';
import { redeemGiftVoucher, type GiftVoucher } from './lib/giftVouchers';
import { useDeferredLoading } from './useDeferredLoading';

export function MerchantReturnQueueView() {
  const { t } = useI18n();
  const [requests, setRequests] = useState<OrderReturnRequestDto[] | null>(null);
  const showSkeleton = useDeferredLoading(requests === null);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  // Real pagination fix (2026-09-11): page 0 is polled every 8s for
  // real-time queue-status accuracy, so it must always stay a live,
  // page-0-only fetch. olderRequests is a separate accumulator populated
  // only by loadMore, never touched by the poll -- same design as the
  // Ride/Commerce/Eats order-history fixes.
  const [olderRequests, setOlderRequests] = useState<OrderReturnRequestDto[]>([]);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);

  const load = () => {
    fetchMerchantReturnQueue(0)
      .then((r) => { setRequests(r.returnRequests); setHasMore(r.page + 1 < r.totalPages); })
      .catch((err) => {
        if (err instanceof ApiError && err.code === 'MERCHANT_NOT_FOUND') {
          setRequests([]);
        } else {
          setError(err instanceof ApiError ? err.message : t('common.loadError'));
        }
      });
  };

  const loadMore = () => {
    const nextPage = page + 1;
    setLoadingMore(true);
    fetchMerchantReturnQueue(nextPage)
      .then((r) => {
        setOlderRequests((prev) => [...prev, ...r.returnRequests]);
        setPage(nextPage);
        setHasMore(r.page + 1 < r.totalPages);
      })
      .catch(() => {})
      .finally(() => setLoadingMore(false));
  };

  useEffect(() => {
    load();
    const interval = setInterval(load, 8000);
    return () => clearInterval(interval);
  }, []);

  const handleDecide = async (id: string, approve: boolean) => {
    setBusyId(id);
    setError(null);
    try {
      await decideOrderReturn(id, approve);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  if (error) {
    return (
      <div style={{ marginBottom: '16px' }}>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>
      </div>
    );
  }
  if (requests === null) return showSkeleton ? <div className="skeleton" style={{ height: '80px', marginBottom: '16px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;
  const open = [...requests, ...olderRequests].filter((r) => r.status === 'REQUESTED');
  if (open.length === 0) return null;

  return (
    <div style={{ marginBottom: '20px' }}>
      <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Return &amp; exchange requests</h4>
      <div style={{ display: 'flex', flexDirection: 'column' }}>
        {open.map((r) => (
          <div key={r.id} className="itunda-flat-section" style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{r.type === 'RETURN' ? 'Return' : 'Exchange'} requested</p>
              <span style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{r.reasonCode.replace(/_/g, ' ').toLowerCase()}</span>
            </div>
            {r.reasonNote && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-700)' }}>{r.reasonNote}</p>}
            <div style={{ display: 'flex', gap: '8px' }}>
              <button className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={busyId === r.id} onClick={() => handleDecide(r.id, true)}>
                {busyId === r.id ? '…' : 'Approve'}
              </button>
              <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} disabled={busyId === r.id} onClick={() => handleDecide(r.id, false)}>
                Reject
              </button>
            </div>
          </div>
        ))}
        {hasMore && (
          <button className="itunda-btn itunda-btn-secondary" disabled={loadingMore} onClick={loadMore}>
            {loadingMore ? 'Loading…' : 'Load more'}
          </button>
        )}
      </div>
    </div>
  );
}

// Real gift-voucher redemption (2026-08-16, Kakao 기프티콘-sourced) -- closes the
// terminal step of an already-shipped feature: a recipient could receive a voucher via
// purchaseGiftVoucher but no merchant had any way to actually redeem one. The recipient
// presents the voucher's id in person (same "physical presentation" convention as a
// paper gifticon barcode); the merchant types it in here. See
// GiftVoucherService.redeemVoucher's own doc comment for why this must be
// merchant-authenticated rather than recipient self-serve.
export function MerchantRedeemVoucherCard() {
  const { t } = useI18n();
  const [voucherId, setVoucherId] = useState('');
  const [redeemed, setRedeemed] = useState<GiftVoucher | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setRedeemed(null);
    const trimmed = voucherId.trim();
    if (!trimmed) return;
    setSubmitting(true);
    try {
      const voucher = await redeemGiftVoucher(trimmed);
      setRedeemed(voucher);
      setVoucherId('');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ marginBottom: '20px' }}>
      <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '4px' }}>Redeem a gift voucher</h4>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '10px' }}>
        Ask the customer for their voucher id and enter it below to redeem it in person.
      </p>
      <form onSubmit={handleSubmit} style={{ display: 'flex', gap: '8px' }}>
        <input
          type="text"
          value={voucherId}
          onChange={(e) => setVoucherId(e.target.value)}
          placeholder="giftvoucher_..."
          className="itunda-input"
          style={{ flex: 1 }}
        />
        <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting || !voucherId.trim()}>
          {submitting ? 'Redeeming…' : 'Redeem'}
        </button>
      </form>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginTop: '8px' }} role="alert">{error}</p>}
      {redeemed && (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-green-600, #16a34a)', marginTop: '8px' }}>
          ✅ Redeemed {redeemed.productNameSnapshot ?? `${redeemed.amount.toLocaleString('en-US')} RWF`}
        </p>
      )}
    </div>
  );
}
