import { useState, useEffect } from 'react';
import { fetchProductRating, fetchProductReviews, toggleProductReviewHelpful, type ProductReview } from './lib/commerce';
import { IconStar } from './icons/ItundaIcons';
import { EmptyState } from './EmptyState';

export function ProductRatingBadge({ productId }: { productId: string }) {
  const [rating, setRating] = useState<{ average: number | null; count: number } | null>(null);
  const [open, setOpen] = useState(false);
  const [reviews, setReviews] = useState<ProductReview[] | null>(null);
  // Real pagination-discard fix (2026-09-12) -- see lib/commerce.ts's own doc
  // comment on fetchProductReviews.
  const [reviewsPage, setReviewsPage] = useState(0);
  const [reviewsHasMore, setReviewsHasMore] = useState(false);
  const [loadingMoreReviews, setLoadingMoreReviews] = useState(false);
  // Real "도움돼요" (helpful) toggle (2026-08-25) -- see lib/commerce.ts's own doc
  // comment, mirrors RestaurantRatingBadge's own identical treatment exactly.
  const [helpfulVoted, setHelpfulVoted] = useState<Set<string>>(new Set());

  const handleToggleHelpful = async (reviewId: string) => {
    try {
      const helpful = await toggleProductReviewHelpful(reviewId);
      setHelpfulVoted((prev) => {
        const next = new Set(prev);
        if (helpful) next.add(reviewId); else next.delete(reviewId);
        return next;
      });
      setReviews((prev) => prev?.map((r) => (r.id === reviewId ? { ...r, helpfulCount: (r.helpfulCount ?? 0) + (helpful ? 1 : -1) } : r)) ?? null);
    } catch {
      // Real, non-critical -- a failed helpful-vote shouldn't block reading reviews.
    }
  };

  useEffect(() => {
    fetchProductRating(productId).then(setRating).catch(() => {
      // Real, non-critical -- a rating fetch failure shouldn't block browsing the catalog.
    });
  }, [productId]);

  const toggle = () => {
    const next = !open;
    setOpen(next);
    if (next && reviews === null) {
      fetchProductReviews(productId, 0)
        .then((r) => { setReviews(r.reviews); setReviewsHasMore(r.page + 1 < r.totalPages); })
        .catch(() => setReviews([]));
    }
  };

  const loadMoreReviews = () => {
    const nextPage = reviewsPage + 1;
    setLoadingMoreReviews(true);
    fetchProductReviews(productId, nextPage)
      .then((r) => {
        setReviews((prev) => [...(prev ?? []), ...r.reviews]);
        setReviewsPage(nextPage);
        setReviewsHasMore(r.page + 1 < r.totalPages);
      })
      .catch(() => {})
      .finally(() => setLoadingMoreReviews(false));
  };

  if (!rating || rating.count === 0) return null;
  return (
    <div>
      <button
        type="button"
        onClick={toggle}
        style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-700)', padding: 0 }}
      >
        <IconStar size={13} color="#F5A623" fill="#F5A623" />
        {rating.average?.toFixed(1)} ({rating.count})
      </button>
      {open && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', marginTop: '6px' }}>
          {reviews === null ? (
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Loading reviews…</p>
          ) : reviews.length === 0 ? (
            <EmptyState message="No written reviews yet — be the first to share how it went." />
          ) : (
            reviews.map((r) => (
              <div key={r.id} style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-700)' }}>
                <span style={{ color: '#F5A623' }}>{'★'.repeat(r.rating)}{'☆'.repeat(5 - r.rating)}</span>
                {r.comment && <span> — {r.comment}</span>}
                {r.ownerReply && (
                  <div style={{ marginTop: '2px', marginLeft: '12px', color: 'var(--itunda-grey-500)' }}>
                    ↳ Seller: {r.ownerReply}
                  </div>
                )}
                <button
                  type="button" onClick={() => handleToggleHelpful(r.id)}
                  style={{ display: 'block', marginTop: '2px', fontSize: 'var(--itunda-type-scale-11-size)', color: helpfulVoted.has(r.id) ? 'var(--itunda-indigo)' : 'var(--itunda-grey-500)' }}
                >
                  👍 Helpful{r.helpfulCount ? ` (${r.helpfulCount})` : ''}
                </button>
              </div>
            ))
          )}
          {reviewsHasMore && (
            <button
              type="button" onClick={loadMoreReviews} disabled={loadingMoreReviews}
              style={{ alignSelf: 'flex-start', fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)', padding: 0 }}
            >
              {loadingMoreReviews ? 'Loading…' : 'Load more reviews'}
            </button>
          )}
        </div>
      )}
    </div>
  );
}
