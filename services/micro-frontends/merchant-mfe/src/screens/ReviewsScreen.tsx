import { useEffect, useState } from 'react';
import { ApiError } from '../lib/api';
import { EmptyState } from '../components/EmptyState';
import {
  fetchMerchantReviews, fetchProductReviews, getProductCatalog, replyToBookingReview, replyToProductReview,
  type Merchant, type MerchantBookingReview, type ProductReview,
} from '../lib/merchant';
import { useI18n } from '../i18n/I18nContext';

// Real post-appointment booking reviews + owner-side reply (item 143) -- see
// lib/merchant.ts's own doc comment. A merchant can post one real reply per review,
// editable (re-posting overwrites the same reply, no separate versioning), matching
// ProductReview/EatsReview's own already-proven reply pattern on the customer side.
export default function ReviewsScreen({ merchant }: { merchant: Merchant }) {
  const { t } = useI18n();
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
      .catch((err) => setError(err instanceof ApiError ? err.message : t('reviews.loadError')));
  };

  useEffect(load, [merchant.id]);

  return (
    <div style={{ maxWidth: '560px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
      <div className="itunda-card">
        <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '4px' }}>{t('reviews.bookingTitle')}</h2>
        {/* Real copy-voice fix (item 244, round 5 of the empty-state pass): honest
            about whose gap this is -- reviews only appear once customers leave
            them after a booking, not something the merchant is missing a step on. */}
        <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>
          {rating?.average != null
            ? t(rating.count === 1 ? 'reviews.ratingAverageSingular' : 'reviews.ratingAveragePlural', { average: rating.average.toFixed(1), count: rating.count })
            : t('reviews.bookingEmpty')}
        </p>
      </div>

      {error && (
        <div className="itunda-card">
          <p style={{ fontSize: '13px', color: 'var(--itunda-red)' }} role="alert">{error}</p>
        </div>
      )}

      {reviews === null && !error && (
        <div className="itunda-card skeleton" style={{ height: '120px' }} />
      )}

      {reviews !== null && reviews.length === 0 && (
        <div className="itunda-card">
          {/* Real copy-voice fix (item 244, round 6 of the empty-state pass): honest
              about whose gap this is, same reasoning as this screen's own summary
              paragraph above. */}
          <EmptyState message={t('reviews.bookingEmpty')} />
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
  const { t } = useI18n();
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
      .catch((err) => setError(err instanceof ApiError ? err.message : t('reviews.productLoadError')));
  };

  useEffect(load, []);

  return (
    <>
      <div className="itunda-card">
        <h2 style={{ fontSize: '18px', fontWeight: 700 }}>{t('reviews.productTitle')}</h2>
      </div>
      {error && (
        <div className="itunda-card">
          <p style={{ fontSize: '13px', color: 'var(--itunda-red)' }} role="alert">{error}</p>
        </div>
      )}
      {reviews === null && !error && (
        <div className="itunda-card skeleton" style={{ height: '120px' }} />
      )}
      {reviews !== null && reviews.length === 0 && (
        <div className="itunda-card">
          {/* Real copy-voice fix (item 244, round 6 of the empty-state pass): honest
              about whose gap this is -- reviews only appear once customers leave
              them after a purchase. */}
          <EmptyState message={t('reviews.productEmpty')} />
        </div>
      )}
      {reviews?.map((review) => (
        <ProductReviewCard key={review.id} review={review} onReplied={load} />
      ))}
    </>
  );
}

function ProductReviewCard({ review, onReplied }: { review: ProductReview & { productName: string }; onReplied: () => void }) {
  const { t } = useI18n();
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
      setError(err instanceof ApiError ? err.message : t('reviews.replyError'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="itunda-card" style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <p style={{ fontSize: '14px', fontWeight: 700 }}>{review.productName}</p>
        <span style={{ fontSize: '13px', fontWeight: 700, color: 'var(--itunda-blue)' }}>{'★'.repeat(review.rating)}{'☆'.repeat(5 - review.rating)}</span>
      </div>
      {review.comment && <p style={{ fontSize: '13px', color: 'var(--itunda-grey-700)' }}>{review.comment}</p>}
      <p style={{ fontSize: '11px', color: 'var(--itunda-grey-500)' }}>{new Date(review.createdAt).toLocaleDateString()}</p>

      {review.ownerReply && !replying && (
        <div style={{ padding: '10px', background: 'var(--itunda-grey-100)', borderRadius: '8px' }}>
          <p style={{ fontSize: '12px', fontWeight: 600, color: 'var(--itunda-grey-700)', marginBottom: '2px' }}>{t('reviews.yourReply')}</p>
          <p style={{ fontSize: '13px', color: 'var(--itunda-grey-900)' }}>{review.ownerReply}</p>
        </div>
      )}

      {replying ? (
        <form onSubmit={handleReply} style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <textarea
            value={reply}
            onChange={(e) => setReply(e.target.value)}
            placeholder={t('reviews.replyPlaceholder')}
            maxLength={1000}
            required
            rows={3}
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: '14px', resize: 'vertical' }}
          />
          {error && (
            <p style={{ fontSize: '13px', color: 'var(--itunda-red)', margin: 0 }} role="alert">{error}</p>
          )}
          <div style={{ display: 'flex', gap: '8px' }}>
            <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting || !reply.trim()}>
              {submitting ? t('reviews.posting') : review.ownerReply ? t('reviews.updateReply') : t('reviews.postReply')}
            </button>
            <button type="button" className="itunda-btn itunda-btn-secondary" onClick={() => setReplying(false)}>
              {t('reviews.cancel')}
            </button>
          </div>
        </form>
      ) : (
        <button
          className="itunda-btn itunda-btn-secondary"
          style={{ alignSelf: 'flex-start', padding: '8px 12px', fontSize: '13px' }}
          onClick={() => setReplying(true)}
        >
          {review.ownerReply ? t('reviews.editReply') : t('reviews.reply')}
        </button>
      )}
    </div>
  );
}

function ReviewCard({ review, onReplied }: { review: MerchantBookingReview; onReplied: () => void }) {
  const { t } = useI18n();
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
      setError(err instanceof ApiError ? err.message : t('reviews.replyError'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="itunda-card" style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <p style={{ fontSize: '14px', fontWeight: 700 }}>{review.serviceName}</p>
        <span style={{ fontSize: '13px', fontWeight: 700, color: 'var(--itunda-blue)' }}>{'★'.repeat(review.rating)}{'☆'.repeat(5 - review.rating)}</span>
      </div>
      {review.comment && <p style={{ fontSize: '13px', color: 'var(--itunda-grey-700)' }}>{review.comment}</p>}
      <p style={{ fontSize: '11px', color: 'var(--itunda-grey-500)' }}>{new Date(review.createdAt).toLocaleDateString()}</p>

      {review.ownerReply && !replying && (
        <div style={{ padding: '10px', background: 'var(--itunda-grey-100)', borderRadius: '8px' }}>
          <p style={{ fontSize: '12px', fontWeight: 600, color: 'var(--itunda-grey-700)', marginBottom: '2px' }}>{t('reviews.yourReply')}</p>
          <p style={{ fontSize: '13px', color: 'var(--itunda-grey-900)' }}>{review.ownerReply}</p>
        </div>
      )}

      {replying ? (
        <form onSubmit={handleReply} style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <textarea
            value={reply}
            onChange={(e) => setReply(e.target.value)}
            placeholder={t('reviews.replyPlaceholder')}
            maxLength={1000}
            required
            rows={3}
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: '14px', resize: 'vertical' }}
          />
          {error && (
            <p style={{ fontSize: '13px', color: 'var(--itunda-red)', margin: 0 }} role="alert">{error}</p>
          )}
          <div style={{ display: 'flex', gap: '8px' }}>
            <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting || !reply.trim()}>
              {submitting ? t('reviews.posting') : review.ownerReply ? t('reviews.updateReply') : t('reviews.postReply')}
            </button>
            <button type="button" className="itunda-btn itunda-btn-secondary" onClick={() => setReplying(false)}>
              {t('reviews.cancel')}
            </button>
          </div>
        </form>
      ) : (
        <button
          className="itunda-btn itunda-btn-secondary"
          style={{ alignSelf: 'flex-start', padding: '8px 12px', fontSize: '13px' }}
          onClick={() => setReplying(true)}
        >
          {review.ownerReply ? t('reviews.editReply') : t('reviews.reply')}
        </button>
      )}
    </div>
  );
}
