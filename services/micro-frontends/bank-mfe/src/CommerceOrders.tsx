import { useState, useEffect } from 'react';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import {
  fetchOrderRiderLocation, fetchPriceTiers, fetchProductInquiries, askProductInquiry, submitProductReview,
  fetchOrderDetail, requestOrderReturn, fetchMyReturnRequests,
  ORDER_RETURN_REASON_CODES,
  type CommerceOrder, type CommerceOrderStatus, type CommerceOrderItem, type CommerceProduct, type PriceTier,
  type ProductInquiry, type OrderReturnType, type OrderReturnRequestDto,
} from './lib/commerce';
import SimpleLiveRiderMap from './SimpleLiveRiderMap';
import { EmptyState } from './EmptyState';
import { StarRatingInput } from './BankDashboard';

export const COMMERCE_STATUS_LABEL: Record<CommerceOrderStatus, string> = {
  PLACED: 'Placed',
  PACKED: 'Packed',
  SHIPPED: 'Shipped',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled — refunded',
};

export const COMMERCE_STATUS_CHAIN: CommerceOrderStatus[] = ['PLACED', 'PACKED', 'SHIPPED', 'DELIVERED'];

export function CommerceOrderCard({ order, action }: { order: CommerceOrder; action?: React.ReactNode }) {
  // Real live rider-location tracking (2026-08-05) -- see lib/commerce.ts's own
  // fetchOrderRiderLocation doc comment. Only ever real once a rider is actually en
  // route, matching OrderService.getRiderLocation's own real SHIPPED-only gate exactly.
  const [showLiveTracking, setShowLiveTracking] = useState(false);
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', padding: '10px 0' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: 'var(--itunda-indigo)' }}>{COMMERCE_STATUS_LABEL[order.status]}</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{order.deliveryAddress}</p>
        </div>
        <span style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>{order.totalAmount.toLocaleString('en-US')} RWF</span>
      </div>
      {order.status === 'SHIPPED' && (
        <button className="itunda-btn itunda-btn-primary" onClick={() => setShowLiveTracking((v) => !v)}>
          {showLiveTracking ? 'Hide live tracking' : '🛵 Track your rider live'}
        </button>
      )}
      {showLiveTracking && <SimpleLiveRiderMap orderId={order.id} fetchLocation={fetchOrderRiderLocation} />}
      {action}
    </div>
  );
}

// Real bulk/wholesale pricing buyer-facing display -- see lib/commerce.ts's own
// fetchPriceTiers doc comment. merchant-mfe already has the owner-config half
// (PosScreen.tsx's "Pricing" panel); this is the first buyer-facing client anywhere.
// Real checkout money impact, not cosmetic: OrderService already applies the
// highest-qualifying tier automatically once the buyer's order quantity meets
// minQuantity, so this previews what the buyer will actually pay, not a label.
export function PriceTiersDisplay({ productId, regularPrice }: { productId: string; regularPrice: number }) {
  const [tiers, setTiers] = useState<PriceTier[] | null>(null);

  useEffect(() => {
    fetchPriceTiers(productId)
      .then(setTiers)
      .catch(() => setTiers([]));
  }, [productId]);

  if (!tiers || tiers.length === 0) return null;

  return (
    <div style={{ padding: '12px', borderRadius: '10px', background: 'var(--itunda-grey-100)' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, marginBottom: '6px' }}>Buy more, pay less</p>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
          <span>1+</span>
          <span>{regularPrice.toLocaleString('en-US')} RWF each</span>
        </div>
        {tiers.map((t) => (
          <div key={t.minQuantity} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-700)', fontWeight: 600 }}>
            <span>{t.minQuantity}+</span>
            <span>{t.unitPrice.toLocaleString('en-US')} RWF each</span>
          </div>
        ))}
      </div>
    </div>
  );
}

// Real Coupang-style pre-purchase product Q&A (상품문의) (2026-07-26) -- see
// ProductInquiryService's own doc comment on the backend. Genuinely distinct from
// ProductRatingBadge's reviews above: no order/purchase required at all, so this is
// always visible on a product's detail page, not gated behind having bought it.
export function ProductInquirySection({ productId }: { productId: string }) {
  const { t } = useI18n();
  const [inquiries, setInquiries] = useState<ProductInquiry[] | null>(null);
  const [question, setQuestion] = useState('');
  const [asking, setAsking] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    fetchProductInquiries(productId).then(setInquiries).catch(() => setInquiries([]));
  };

  useEffect(load, [productId]);

  const handleAsk = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!question.trim()) return;
    setAsking(true);
    setError(null);
    try {
      await askProductInquiry(productId, question.trim());
      setQuestion('');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setAsking(false);
    }
  };

  return (
    <div style={{ borderTop: '1px solid var(--itunda-grey-100)', paddingTop: '12px' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: 'var(--itunda-grey-900)', marginBottom: '8px' }}>Questions & answers</p>
      <form onSubmit={handleAsk} style={{ display: 'flex', gap: '8px', marginBottom: '10px' }}>
        <input
          type="text"
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          placeholder="Ask the seller a question"
          style={{ flex: 1, padding: '8px 10px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
        />
        <button type="submit" className="itunda-btn itunda-btn-secondary" disabled={asking || !question.trim()} style={{ padding: '8px 14px' }}>
          Ask
        </button>
      </form>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{error}</p>}
      {inquiries === null ? (
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Loading questions…</p>
      ) : inquiries.length === 0 ? (
        <EmptyState message="No questions yet -- be the first to ask." />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {inquiries.map((q) => (
            <div key={q.id} style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-700)' }}>
              <span style={{ fontWeight: 700 }}>Q. </span>{q.question}
              {q.answer ? (
                <div style={{ marginTop: '2px', marginLeft: '12px', color: 'var(--itunda-grey-500)' }}>
                  <span style={{ fontWeight: 700 }}>A. </span>{q.answer}
                </div>
              ) : (
                <div style={{ marginTop: '2px', marginLeft: '12px', color: 'var(--itunda-grey-400)', fontStyle: 'italic' }}>
                  Awaiting seller response
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function ProductReviewRow({ item }: { item: CommerceOrderItem }) {
  const { t } = useI18n();
  const [open, setOpen] = useState(false);
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (rating === 0) {
      setError('Pick a star rating.');
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await submitProductReview(item.id, rating, comment);
      setDone(true);
    } catch (err) {
      if (err instanceof ApiError && err.code === 'PRODUCT_ALREADY_REVIEWED') {
        setDone(true);
      } else {
        setError(err instanceof ApiError ? err.message : t('common.actionError'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (done) {
    return (
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{item.productName}: thanks for your review!</p>
    );
  }

  if (!open) {
    return (
      <button className="itunda-btn itunda-btn-secondary" style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '8px 12px' }} onClick={() => setOpen(true)}>
        Rate {item.productName}
      </button>
    );
  }

  return (
    <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{item.productName}</p>
      <StarRatingInput value={rating} onChange={setRating} />
      <input
        type="text"
        value={comment}
        onChange={(e) => setComment(e.target.value)}
        placeholder="How was it? (optional)"
        style={{ width: '100%', padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
      />
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      <div style={{ display: 'flex', gap: '10px' }}>
        <button type="button" className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} onClick={() => setOpen(false)}>Cancel</button>
        <button type="submit" className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={submitting}>
          {submitting ? 'Submitting…' : 'Submit review'}
        </button>
      </div>
    </form>
  );
}

export function OrderItemReviews({ order }: { order: CommerceOrder }) {
  const [items, setItems] = useState<CommerceOrderItem[] | null>(null);

  useEffect(() => {
    fetchOrderDetail(order.id).then((r) => setItems(r.items)).catch(() => {
      // Real, non-critical -- if item fetch fails, the order card itself still renders fine.
    });
  }, [order.id]);

  if (!items || items.length === 0) return null;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginTop: '4px' }}>
      {items.map((item) => <ProductReviewRow key={item.id} item={item} />)}
    </div>
  );
}

// Real Coupang-style post-delivery Return & Exchange request (item 166) -- see
// lib/commerce.ts's own doc comment. A real 7-day window from delivery, enforced
// server-side; this button stays offered regardless (a stale/expired attempt just
// real-errors with an honest message, same discipline as every other time-gated action
// in this app).
export function ReturnExchangeAction({ orderId }: { orderId: string }) {
  const { t } = useI18n();
  const [open, setOpen] = useState(false);
  const [type, setType] = useState<OrderReturnType>('RETURN');
  const [reasonCode, setReasonCode] = useState<string>(ORDER_RETURN_REASON_CODES[0]);
  const [reasonNote, setReasonNote] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<OrderReturnRequestDto | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const req = await requestOrderReturn(orderId, type, reasonCode, reasonNote.trim() || undefined);
      setResult(req);
      setOpen(false);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmitting(false);
    }
  };

  if (result) {
    return <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-indigo)', fontWeight: 600, marginTop: '6px' }}>{result.type === 'RETURN' ? 'Return' : 'Exchange'} requested — awaiting seller review.</p>;
  }

  if (!open) {
    return (
      <button className="itunda-btn itunda-btn-secondary" style={{ marginTop: '6px', padding: '6px 12px', fontSize: 'var(--itunda-type-scale-12-size)' }} onClick={() => setOpen(true)}>
        Return or exchange
      </button>
    );
  }

  return (
    <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginTop: '8px', padding: '10px', background: 'var(--itunda-grey-100)', borderRadius: '10px' }}>
      <div style={{ display: 'flex', gap: '8px' }}>
        <select value={type} onChange={(e) => setType(e.target.value as OrderReturnType)} style={{ flex: 1, padding: '8px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}>
          <option value="RETURN">Return</option>
          <option value="EXCHANGE">Exchange</option>
        </select>
        <select value={reasonCode} onChange={(e) => setReasonCode(e.target.value)} style={{ flex: 1, padding: '8px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}>
          {ORDER_RETURN_REASON_CODES.map((r) => <option key={r} value={r}>{r.replace(/_/g, ' ').toLowerCase()}</option>)}
        </select>
      </div>
      <input
        type="text"
        value={reasonNote}
        onChange={(e) => setReasonNote(e.target.value)}
        placeholder="Details (optional)"
        style={{ padding: '8px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
      />
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', margin: 0 }} role="alert">{error}</p>}
      <div style={{ display: 'flex', gap: '8px' }}>
        <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting} style={{ flex: 1, padding: '8px', fontSize: 'var(--itunda-type-scale-13-size)' }}>
          {submitting ? 'Submitting…' : 'Submit request'}
        </button>
        <button type="button" className="itunda-btn itunda-btn-secondary" onClick={() => setOpen(false)} style={{ padding: '8px 12px', fontSize: 'var(--itunda-type-scale-13-size)' }}>Cancel</button>
      </div>
    </form>
  );
}

export function MyReturnRequestsView() {
  const [requests, setRequests] = useState<OrderReturnRequestDto[] | null>(null);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);

  useEffect(() => {
    fetchMyReturnRequests(0)
      .then((r) => { setRequests(r.returnRequests); setHasMore(r.page + 1 < r.totalPages); })
      .catch(() => setRequests([]));
  }, []);

  const loadMore = () => {
    const nextPage = page + 1;
    setLoadingMore(true);
    fetchMyReturnRequests(nextPage)
      .then((r) => {
        setRequests((prev) => [...(prev ?? []), ...r.returnRequests]);
        setPage(nextPage);
        setHasMore(r.page + 1 < r.totalPages);
      })
      .catch(() => {})
      .finally(() => setLoadingMore(false));
  };

  if (!requests || requests.length === 0) return null;

  return (
    <div style={{ marginBottom: '16px' }}>
      <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>My return &amp; exchange requests</h4>
      <div style={{ display: 'flex', flexDirection: 'column' }}>
        {requests.map((r) => (
          <div key={r.id} className="itunda-flat-section" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{r.type === 'RETURN' ? 'Return' : 'Exchange'}</p>
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{r.reasonCode.replace(/_/g, ' ').toLowerCase()}</p>
            </div>
            <span style={{
              fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700,
              color: r.status === 'APPROVED' ? 'var(--itunda-green)' : r.status === 'REJECTED' ? 'var(--itunda-red)' : 'var(--itunda-indigo)',
            }}>
              {r.status === 'REQUESTED' ? 'Pending' : r.status === 'APPROVED' ? 'Approved' : 'Rejected'}
            </span>
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

// Real cross-merchant cart (2026-07-20) -- closes the "real Coupang splits a
// multi-seller cart into per-seller orders, not attempted here" simplification this
// row's own text named. Keyed by merchantId so a buyer can browse merchant A, add
// items, go back, browse merchant B, add items there too, and check out everything
// in one pass -- each merchant's line items get a real, separate placeOrder() call
// (the backend already only ever accepted one merchantId per order; no backend
// change needed at all, this is purely a client-side cart-architecture change).
export interface CommerceCartGroup {
  businessName: string;
  lines: Record<string, { product: CommerceProduct; quantity: number }>;
}
export type CommerceCart = Record<string, CommerceCartGroup>;

export function cartTotalItems(cart: CommerceCart): number {
  return Object.values(cart).reduce((sum, group) => sum + Object.values(group.lines).reduce((s, l) => s + l.quantity, 0), 0);
}
