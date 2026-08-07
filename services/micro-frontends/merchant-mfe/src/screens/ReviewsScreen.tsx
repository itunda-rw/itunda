import { useEffect, useState } from 'react';
import { ApiError } from '../lib/api';
import { EmptyState } from '../components/EmptyState';
import {
  fetchMerchantReviews, fetchProductReviews, getProductCatalog, replyToBookingReview, replyToProductReview,
  type Merchant, type MerchantBookingReview, type ProductReview,
} from '../lib/merchant';

// Real post-appointment booking reviews + owner-side reply (item 143) -- see
// lib/merchant.ts's own doc comment. A merchant can post one real reply per review,
// editable (re-posting overwrites the same reply, no separate versioning), matching
// ProductReview/EatsReview's own already-proven reply pattern on the customer side.
export default function ReviewsScreen({ merchant }: { merchant: Merchant }) {
  const [reviews, setReviews] = useState<MerchantBookingReview[] | null>(null);
  const [rating, setRating] = useState<{ average: number | null; count: number } | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMerchantReviews(merchant.id)
      .then((r) => {
        setReviews(r.reviews);
        setRating(r.rating);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your reviews.'));
  };

  useEffect(load, [merchant.id]);

  return (
    <div style={{ maxWidth: '560px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
      <div className="toss-card">
        <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '4px' }}>Booking reviews</h2>
        {/* Real copy-voice fix (item 244, round 5 of the empty-state pass): honest
            about whose gap this is -- reviews only appear once customers leave
            them after a booking, not something the merchant is missing a step on. */}
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>
          {rating?.average != null ? `${rating.average.toFixed(1)} ★ average (${rating.count} review${rating.count === 1 ? '' : 's'})` : 'No reviews yet — reviews will show up here once customers leave them after a booking.'}
        </p>
      </div>

      {error && (
        <div className="toss-card">
          <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>
        </div>
      )}

      {reviews === null && !error && (
        <div className="toss-card skeleton" style={{ height: '120px' }} />
      )}

      {reviews !== null && reviews.length === 0 && (
        <div className="toss-card">
          {/* Real copy-voice fix (item 244, round 6 of the empty-state pass): honest
              about whose gap this is, same reasoning as this screen's own summary
              paragraph above. */}
          <EmptyState message="No booking reviews yet — reviews will show up here once customers leave them after a booking." />
        </div>
      )}

      {reviews?.map((review) => (
        <ReviewCard key={review.id} review={review} onReplied={load} />
      ))}

      <ProductReviewsSection />
    </div>
  );
}

// Real Commerce product reviews + owner-side reply (item 187) -- see lib/merchant.ts's
// own doc comment: ProductReviewService.replyToProductReview had been real since
// 2026-07-26 with zero client anywhere. Fans out one real per-product review fetch
// across the merchant's own catalog (getProductCatalog), same honest "no aggregate
// endpoint exists yet" scoping this file's own doc comment names.
function ProductReviewsSection() {
  const [reviews, setReviews] = useState<(ProductReview & { productName: string })[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    setError(null);
    getProductCatalog()
      .then((products) =>
        Promise.all(
          products.map((p) =>
            fetchProductReviews(p.id)
              .then((reviews) => reviews.map((r) => ({ ...r, productName: p.name })))
              .catch(() => []),
          ),
        ),
      )
      .then((perProduct) => setReviews(perProduct.flat().sort((a, b) => b.createdAt.localeCompare(a.createdAt))))
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your product reviews.'));
  };

  useEffect(load, []);

  return (
    <>
      <div className="toss-card">
        <h2 style={{ fontSize: '18px', fontWeight: 700 }}>Product reviews</h2>
      </div>
      {error && (
        <div className="toss-card">
          <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>
        </div>
      )}
      {reviews === null && !error && (
        <div className="toss-card skeleton" style={{ height: '120px' }} />
      )}
      {reviews !== null && reviews.length === 0 && (
        <div className="toss-card">
          {/* Real copy-voice fix (item 244, round 6 of the empty-state pass): honest
              about whose gap this is -- reviews only appear once customers leave
              them after a purchase. */}
          <EmptyState message="No product reviews yet — reviews will show up here once customers leave them after a purchase." />
        </div>
      )}
      {reviews?.map((review) => (
        <ProductReviewCard key={review.id} review={review} onReplied={load} />
      ))}
    </>
  );
}

function ProductReviewCard({ review, onReplied }: { review: ProductReview & { productName: string }; onReplied: () => void }) {
  const [replying, setReplying] = useState(false);
  const [reply, setReply] = useState(review.ownerReply ?? '');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleReply = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await replyToProductReview(review.id, reply);
      setReplying(false);
      onReplied();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not post your reply.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <p style={{ fontSize: '14px', fontWeight: 700 }}>{review.productName}</p>
        <span style={{ fontSize: '13px', fontWeight: 700, color: 'var(--toss-blue)' }}>{'★'.repeat(review.rating)}{'☆'.repeat(5 - review.rating)}</span>
      </div>
      {review.comment && <p style={{ fontSize: '13px', color: 'var(--toss-grey-700)' }}>{review.comment}</p>}
      <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>{new Date(review.createdAt).toLocaleDateString()}</p>

      {review.ownerReply && !replying && (
        <div style={{ padding: '10px', background: 'var(--toss-grey-100)', borderRadius: '8px' }}>
          <p style={{ fontSize: '12px', fontWeight: 600, color: 'var(--toss-grey-700)', marginBottom: '2px' }}>Your reply</p>
          <p style={{ fontSize: '13px', color: 'var(--toss-grey-900)' }}>{review.ownerReply}</p>
        </div>
      )}

      {replying ? (
        <form onSubmit={handleReply} style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <textarea
            value={reply}
            onChange={(e) => setReply(e.target.value)}
            placeholder="Write a reply to this review"
            maxLength={1000}
            required
            rows={3}
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', resize: 'vertical' }}
          />
          {error && (
            <p style={{ fontSize: '13px', color: '#E53935', margin: 0 }} role="alert">{error}</p>
          )}
          <div style={{ display: 'flex', gap: '8px' }}>
            <button type="submit" className="toss-btn toss-btn-primary" disabled={submitting || !reply.trim()}>
              {submitting ? 'Posting…' : review.ownerReply ? 'Update reply' : 'Post reply'}
            </button>
            <button type="button" className="toss-btn toss-btn-secondary" onClick={() => setReplying(false)}>
              Cancel
            </button>
          </div>
        </form>
      ) : (
        <button
          className="toss-btn toss-btn-secondary"
          style={{ alignSelf: 'flex-start', padding: '8px 12px', fontSize: '13px' }}
          onClick={() => setReplying(true)}
        >
          {review.ownerReply ? 'Edit reply' : 'Reply'}
        </button>
      )}
    </div>
  );
}

function ReviewCard({ review, onReplied }: { review: MerchantBookingReview; onReplied: () => void }) {
  const [replying, setReplying] = useState(false);
  const [reply, setReply] = useState(review.ownerReply ?? '');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleReply = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await replyToBookingReview(review.id, reply);
      setReplying(false);
      onReplied();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not post your reply.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <p style={{ fontSize: '14px', fontWeight: 700 }}>{review.serviceName}</p>
        <span style={{ fontSize: '13px', fontWeight: 700, color: 'var(--toss-blue)' }}>{'★'.repeat(review.rating)}{'☆'.repeat(5 - review.rating)}</span>
      </div>
      {review.comment && <p style={{ fontSize: '13px', color: 'var(--toss-grey-700)' }}>{review.comment}</p>}
      <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>{new Date(review.createdAt).toLocaleDateString()}</p>

      {review.ownerReply && !replying && (
        <div style={{ padding: '10px', background: 'var(--toss-grey-100)', borderRadius: '8px' }}>
          <p style={{ fontSize: '12px', fontWeight: 600, color: 'var(--toss-grey-700)', marginBottom: '2px' }}>Your reply</p>
          <p style={{ fontSize: '13px', color: 'var(--toss-grey-900)' }}>{review.ownerReply}</p>
        </div>
      )}

      {replying ? (
        <form onSubmit={handleReply} style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <textarea
            value={reply}
            onChange={(e) => setReply(e.target.value)}
            placeholder="Write a reply to this review"
            maxLength={1000}
            required
            rows={3}
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', resize: 'vertical' }}
          />
          {error && (
            <p style={{ fontSize: '13px', color: '#E53935', margin: 0 }} role="alert">{error}</p>
          )}
          <div style={{ display: 'flex', gap: '8px' }}>
            <button type="submit" className="toss-btn toss-btn-primary" disabled={submitting || !reply.trim()}>
              {submitting ? 'Posting…' : review.ownerReply ? 'Update reply' : 'Post reply'}
            </button>
            <button type="button" className="toss-btn toss-btn-secondary" onClick={() => setReplying(false)}>
              Cancel
            </button>
          </div>
        </form>
      ) : (
        <button
          className="toss-btn toss-btn-secondary"
          style={{ alignSelf: 'flex-start', padding: '8px 12px', fontSize: '13px' }}
          onClick={() => setReplying(true)}
        >
          {review.ownerReply ? 'Edit reply' : 'Reply'}
        </button>
      )}
    </div>
  );
}
