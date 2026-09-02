import { useState, useEffect } from 'react';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import {
  fetchRestaurantReviews, replyToRestaurantReview, submitEatsReview, tipEatsOrderRider, EATS_GOOD_POINT_LABELS,
  type EatsOrder, type EatsReview,
} from './lib/eats';
import { StarRatingInput } from './BankDashboard';
import { useDeferredLoading } from './useDeferredLoading';

// Real owner-reply management (item 184) -- a restaurant owner's own reviews, with an
// inline reply form for anything not yet replied to. Lives on RestaurantOrdersView
// (the owner's own dashboard) since that's the only place this app already resolves
// "my own restaurant id" for an Eats seller.
export function RestaurantReviewsManageView({ restaurantId }: { restaurantId: string }) {
  const { t } = useI18n();
  const [reviews, setReviews] = useState<EatsReview[] | null>(null);
  const showSkeleton = useDeferredLoading(reviews === null);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    fetchRestaurantReviews(restaurantId).then(setReviews).catch((err) => {
      setError(err instanceof ApiError ? err.message : t('common.loadError'));
    });
  };
  useEffect(load, [restaurantId]);

  if (error) return <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>;
  if (reviews === null) return showSkeleton ? <div className="skeleton" style={{ height: '80px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;
  if (reviews.length === 0) return null;

  return (
    <div style={{ marginBottom: '20px' }}>
      <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Reviews for your restaurant</h4>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        {reviews.map((r) => <RestaurantReviewReplyCard key={r.id} review={r} onReplied={load} />)}
      </div>
    </div>
  );
}

function RestaurantReviewReplyCard({ review, onReplied }: { review: EatsReview; onReplied: () => void }) {
  const { t } = useI18n();
  const [replying, setReplying] = useState(false);
  const [reply, setReply] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await replyToRestaurantReview(review.id, reply.trim());
      setReplying(false);
      onReplied();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="itunda-flat-section">
      <span style={{ color: '#F5A623', fontSize: 'var(--itunda-type-scale-13-size)' }}>{'★'.repeat(review.restaurantRating)}{'☆'.repeat(5 - review.restaurantRating)}</span>
      {review.restaurantComment && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', marginTop: '4px' }}>{review.restaurantComment}</p>}
      {/* Real review photo (itunda Eats redesign, 2026-08-28) -- see
          EatsReview.photoUrl's own doc comment: real end-to-end on the backend since
          2026-08-04, never rendered anywhere on web until now. */}
      {review.photoUrl && (
        <img
          src={review.photoUrl} alt="" loading="lazy"
          style={{ width: '96px', height: '96px', borderRadius: 'var(--itunda-radius-md)', objectFit: 'cover', marginTop: '8px' }}
          onError={(e) => { e.currentTarget.style.display = 'none'; }}
        />
      )}
      {review.ownerReply ? (
        <div style={{ marginTop: '8px', paddingLeft: '10px', borderLeft: '2px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-700)' }}>
          Your reply: {review.ownerReply}
        </div>
      ) : replying ? (
        <form onSubmit={handleSubmit} style={{ marginTop: '8px', display: 'flex', gap: '8px' }}>
          <input
            type="text" placeholder="Write a reply…" value={reply} onChange={(e) => setReply(e.target.value)} required
            style={{ flex: 1, padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '8px 12px' }}>
            {submitting ? '…' : 'Reply'}
          </button>
        </form>
      ) : (
        <button
          type="button"
          onClick={() => setReplying(true)}
          className="itunda-btn itunda-btn-secondary"
          style={{ marginTop: '8px', fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}
        >
          Reply
        </button>
      )}
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', marginTop: '4px' }} role="alert">{error}</p>}
    </div>
  );
}

export function ReviewOrderCard({ order, onSubmitted }: { order: EatsOrder; onSubmitted: () => void }) {
  const { t } = useI18n();
  const [open, setOpen] = useState(false);
  const [restaurantRating, setRestaurantRating] = useState(0);
  const [restaurantComment, setRestaurantComment] = useState('');
  const [riderRating, setRiderRating] = useState(0);
  const [riderComment, setRiderComment] = useState('');
  // Real optional review photo (itunda Eats redesign, 2026-08-28) -- see
  // lib/eats.ts's submitEatsReview doc comment. itunda has no upload/storage
  // pipeline, so this is a real "paste your own already-hosted photo URL" field,
  // same honest bar as Merchant.photoUrl elsewhere in this codebase.
  const [photoUrl, setPhotoUrl] = useState('');
  // Real preset-tag checklist (itunda Maps redesign, 2026-08-28, direct Naver Map
  // reference: "이런 점이 좋았어요") -- see EatsReview.goodPoints' own doc comment on
  // the backend, ported from HoodReviewForm's own exact pill-picker pattern above.
  const [selectedGoodPoints, setSelectedGoodPoints] = useState<Set<string>>(new Set());
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  const toggleGoodPoint = (id: string) => {
    setSelectedGoodPoints((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  // Real bug fix (2026-07-26): a PICKUP order has riderId: null for its whole
  // lifecycle -- there's genuinely no rider to rate, so the rider star row is hidden
  // and never required, matching the backend's own real fix for the same order.
  const hasRider = order.fulfillmentType !== 'PICKUP';

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (restaurantRating === 0 || (hasRider && riderRating === 0)) {
      setError(hasRider ? 'Rate both the restaurant and the rider.' : 'Rate the restaurant.');
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await submitEatsReview(order.id, restaurantRating, restaurantComment, hasRider ? riderRating : null, riderComment, photoUrl, Array.from(selectedGoodPoints));
      setDone(true);
      onSubmitted();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'ORDER_ALREADY_REVIEWED') {
        setDone(true);
      } else {
        setError(err instanceof ApiError ? err.message : t('common.actionError'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (done) {
    return <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Thanks for your review!</p>;
  }

  if (!open) {
    return (
      <button className="itunda-btn itunda-btn-secondary" onClick={() => setOpen(true)}>
        Rate this order
      </button>
    );
  }

  return (
    <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginTop: '8px' }}>
      <div>
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '4px' }}>Restaurant</p>
        <StarRatingInput value={restaurantRating} onChange={setRestaurantRating} />
        <input
          type="text"
          value={restaurantComment}
          onChange={(e) => setRestaurantComment(e.target.value)}
          placeholder="How was the food? (optional)"
          style={{ marginTop: '6px', width: '100%', padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
        />
        <input
          type="url"
          value={photoUrl}
          onChange={(e) => setPhotoUrl(e.target.value)}
          placeholder="Photo URL (optional)"
          style={{ marginTop: '6px', width: '100%', padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
        />
        <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', marginTop: '8px' }}>
          {EATS_GOOD_POINT_LABELS.map(([id, label]) => {
            const selected = selectedGoodPoints.has(id);
            return (
              <button
                key={id}
                type="button"
                onClick={() => toggleGoodPoint(id)}
                style={{
                  fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, padding: '6px 12px', borderRadius: '999px',
                  color: selected ? 'var(--itunda-white)' : 'var(--itunda-grey-900)',
                  backgroundColor: selected ? 'var(--itunda-indigo)' : 'var(--itunda-grey-100)',
                }}
              >
                {label}
              </button>
            );
          })}
        </div>
      </div>
      {hasRider && (
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '4px' }}>Rider</p>
          <StarRatingInput value={riderRating} onChange={setRiderRating} />
          <input
            type="text"
            value={riderComment}
            onChange={(e) => setRiderComment(e.target.value)}
            placeholder="How was the delivery? (optional)"
            style={{ marginTop: '6px', width: '100%', padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
        </div>
      )}
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

// Real Uber Eats post-delivery tip -- see lib/eats.ts's own doc comment. Found via
// scripts/uncalled-endpoint-sweep.py, same real gap shape as TipDriverPrompt (rides)
// this session already closed -- mirrors it directly, reusing TIP_PRESETS. A PICKUP
// order has no rider (see ReviewOrderCard's own hasRider comment above), so the
// caller only renders this for a real DELIVERY order.
const TIP_PRESETS = [500, 1000, 2000];

export function TipRiderPrompt({ orderId, onTipped }: { orderId: string; onTipped: () => void }) {
  const { t } = useI18n();
  const [amount, setAmount] = useState<number | null>(null);
  const [customAmount, setCustomAmount] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (overrideAmount?: number) => {
    const finalAmount = overrideAmount ?? amount ?? Number(customAmount);
    if (!(finalAmount > 0)) return;
    setSubmitting(true);
    setError(null);
    try {
      await tipEatsOrderRider(orderId, finalAmount);
      onTipped();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ marginTop: '8px', paddingTop: '8px', borderTop: '1px solid var(--itunda-grey-100)' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, marginBottom: '6px' }}>Tip your rider</p>
      <div style={{ display: 'flex', gap: '6px', marginBottom: '6px' }}>
        {TIP_PRESETS.map((preset) => (
          <button
            key={preset} type="button" disabled={submitting}
            onClick={() => { setAmount(preset); setCustomAmount(''); handleSubmit(preset); }}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700,
              border: '1px solid var(--itunda-grey-200)', background: amount === preset ? 'var(--itunda-indigo)' : 'transparent',
              color: amount === preset ? 'white' : 'var(--itunda-grey-700)',
            }}
          >
            {preset.toLocaleString()}
          </button>
        ))}
      </div>
      <div style={{ display: 'flex', gap: '6px' }}>
        <input
          type="number" placeholder="Custom amount (RWF)" value={customAmount}
          onChange={(e) => { setCustomAmount(e.target.value); setAmount(null); }}
          style={{ flex: 1, padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-12-size)' }}
        />
        <button
          className="itunda-btn itunda-btn-primary" disabled={submitting || !(Number(customAmount) > 0)}
          onClick={() => handleSubmit()} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '8px 14px' }}
        >
          {submitting ? '…' : 'Send'}
        </button>
      </div>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-red)', marginTop: '4px' }} role="alert">{error}</p>}
    </div>
  );
}
