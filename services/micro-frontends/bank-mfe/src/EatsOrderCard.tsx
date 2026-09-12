import { useState, useEffect } from 'react';
import { ApiError } from './lib/api';
import { ChatGlyph } from './icons/ItundaFaceMisc';
import { IconStar } from './icons/ItundaIcons';
import { EmptyState } from './EmptyState';
import RouteMiniMap from './RouteMiniMap';
import LiveRiderMap from './LiveRiderMap';
import type { ShoppingMerchant } from './lib/shopping';
import {
  type EatsOrder, type EatsOrderStatus,
} from './lib/eats';
import {
  toggleReviewHelpful, fetchRestaurantRating, fetchRestaurantGoodPoints, fetchRestaurantReviews, reportEatsReview,
  EATS_GOOD_POINT_LABELS,
  type EatsReview, type EatsReviewReportReason, type RatingSummary,
} from './lib/eatsReviews';

export const EATS_STATUS_LABEL: Record<EatsOrderStatus, string> = {
  PLACED: 'Placed',
  ACCEPTED: 'Accepted by restaurant',
  PREPARING: 'Preparing',
  READY_FOR_PICKUP: 'Ready for pickup',
  RIDER_ASSIGNED: 'Rider on the way to restaurant',
  PICKED_UP: 'Picked up — on the way',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled — refunded',
};

export const RESTAURANT_STATUS_CHAIN: EatsOrderStatus[] = ['PLACED', 'ACCEPTED', 'PREPARING', 'READY_FOR_PICKUP'];
export const RIDER_STATUS_CHAIN: EatsOrderStatus[] = ['RIDER_ASSIGNED', 'PICKED_UP', 'DELIVERED'];

export function EatsOrderCard({ order, restaurant, action, onMessageRestaurant }: { order: EatsOrder; restaurant?: ShoppingMerchant; action?: React.ReactNode; onMessageRestaurant?: () => void }) {
  const [showRoute, setShowRoute] = useState(false);
  const [showLiveTracking, setShowLiveTracking] = useState(false);
  // Real "message restaurant" (2026-08-16, Uber Eats' own real Live Order Chat) --
  // only offered on the buyer's own active orders (onMessageRestaurant is only ever
  // passed by MyEatsOrdersView, never the rider/restaurant-facing renders of this same
  // card), and only while there's still something to coordinate about -- a delivered
  // or cancelled order has nothing left to confirm before the fact.
  const canMessageRestaurant = onMessageRestaurant && order.status !== 'DELIVERED' && order.status !== 'CANCELLED';
  const canShowRoute = restaurant?.latitude != null && restaurant?.longitude != null && order.deliveryLatitude != null && order.deliveryLongitude != null;
  // Real live rider tracking (2026-07-20) -- only meaningful while a real rider is
  // actually en route, matching EatsOrderService.getRiderLocation's own real state gate
  // (RIDER_ASSIGNED/PICKED_UP only; before/after that there's honestly nothing to show).
  const canShowLiveTracking = canShowRoute && (order.status === 'RIDER_ASSIGNED' || order.status === 'PICKED_UP');

  return (
    <div className="itunda-flat-section" style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: 'var(--itunda-indigo)' }}>{EATS_STATUS_LABEL[order.status]}</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{order.deliveryAddress}</p>
        </div>
        <span style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>{order.totalAmount.toLocaleString('en-US')} RWF</span>
      </div>
      {order.deliveryNotes && (
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-700)', backgroundColor: 'var(--itunda-grey-100)', borderRadius: '8px', padding: '8px 10px' }}>
          Note: {order.deliveryNotes}
        </p>
      )}
      {canMessageRestaurant && (
        <button className="itunda-btn itunda-btn-secondary" onClick={onMessageRestaurant}>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}><ChatGlyph size={16} /> Message restaurant</span>
        </button>
      )}
      {canShowLiveTracking && (
        <button className="itunda-btn itunda-btn-primary" onClick={() => { setShowLiveTracking((v) => !v); setShowRoute(false); }}>
          {showLiveTracking ? 'Hide live tracking' : '🛵 Track your rider live'}
        </button>
      )}
      {showLiveTracking && restaurant?.latitude != null && restaurant?.longitude != null && order.deliveryLatitude != null && order.deliveryLongitude != null && (
        <LiveRiderMap
          orderId={order.id}
          fromLat={restaurant.latitude}
          fromLng={restaurant.longitude}
          toLat={order.deliveryLatitude}
          toLng={order.deliveryLongitude}
          fromLabel={restaurant.businessName}
          toLabel="Delivery address"
        />
      )}
      {canShowRoute && !showLiveTracking && (
        <button className="itunda-btn itunda-btn-secondary" onClick={() => setShowRoute((v) => !v)}>
          {showRoute ? 'Hide route' : '🚗 View real delivery route'}
        </button>
      )}
      {showRoute && !showLiveTracking && restaurant?.latitude != null && restaurant?.longitude != null && order.deliveryLatitude != null && order.deliveryLongitude != null && (
        <RouteMiniMap
          fromLat={restaurant.latitude}
          fromLng={restaurant.longitude}
          toLat={order.deliveryLatitude}
          toLng={order.deliveryLongitude}
          fromLabel={restaurant.businessName}
          toLabel="Delivery address"
        />
      )}
      {action}
    </div>
  );
}

// Real written-review list + owner-reply display (item 184, 2026-07-29) -- the backend
// (getRestaurantReviews, real since restaurant reviews shipped) and a real dead
// fetchRestaurantReviews export both existed with zero UI anywhere calling it; see
// docs/DESIGN_REFERENCES.md section 2 recommendation 5. Mirrors ProductRatingBadge's
// own expand-on-click pattern exactly.
export function RestaurantRatingBadge({ restaurantId }: { restaurantId: string }) {
  const [rating, setRating] = useState<RatingSummary | null>(null);
  const [open, setOpen] = useState(false);
  const [reviews, setReviews] = useState<EatsReview[] | null>(null);
  // Real pagination-discard fix (2026-09-12) -- see lib/eats.ts's own doc
  // comment on fetchRestaurantReviews.
  const [reviewsPage, setReviewsPage] = useState(0);
  const [reviewsHasMore, setReviewsHasMore] = useState(false);
  const [loadingMoreReviews, setLoadingMoreReviews] = useState(false);
  // Real "도움돼요" (helpful) toggle -- see lib/eats.ts's own doc comment.
  const [helpfulVoted, setHelpfulVoted] = useState<Set<string>>(new Set());
  // Real preset-tag aggregate (itunda Maps redesign, 2026-08-28) -- see
  // EatsReviewService.restaurantGoodPointCounts' own doc comment on the backend.
  const [goodPointCounts, setGoodPointCounts] = useState<Record<string, number> | null>(null);

  const handleToggleHelpful = async (reviewId: string) => {
    try {
      const helpful = await toggleReviewHelpful(reviewId);
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
    fetchRestaurantRating(restaurantId).then(setRating).catch(() => {
      // Real, non-critical -- a rating fetch failure shouldn't block browsing the menu.
    });
    fetchRestaurantGoodPoints(restaurantId).then((r) => setGoodPointCounts(r.counts)).catch(() => {
      // Real, non-critical -- same bar as the rating fetch above.
    });
  }, [restaurantId]);

  const toggle = () => {
    const next = !open;
    setOpen(next);
    if (next && reviews === null) {
      fetchRestaurantReviews(restaurantId, 0)
        .then((r) => { setReviews(r.reviews); setReviewsHasMore(r.page + 1 < r.totalPages); })
        .catch(() => setReviews([]));
    }
  };

  const loadMoreReviews = () => {
    const nextPage = reviewsPage + 1;
    setLoadingMoreReviews(true);
    fetchRestaurantReviews(restaurantId, nextPage)
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
        style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-700)', padding: 0 }}
      >
        <IconStar size={14} color="#F5A623" fill="#F5A623" />
        {rating.average?.toFixed(1)} ({rating.count})
      </button>
      {open && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', marginTop: '6px' }}>
          {/* Real preset-tag aggregate (itunda Maps redesign, 2026-08-28, direct Naver
              Map reference: "이런 점이 좋았어요") -- real counts from real submitted
              tags only, never fabricated. */}
          {goodPointCounts && Object.keys(goodPointCounts).length > 0 && (
            <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', marginBottom: '4px' }}>
              {Object.entries(goodPointCounts)
                .sort((a, b) => b[1] - a[1])
                .map(([id, count]) => (
                  <span
                    key={id}
                    style={{
                      fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, padding: '4px 10px', borderRadius: '999px',
                      color: 'var(--itunda-grey-900)', backgroundColor: 'var(--itunda-grey-100)',
                    }}
                  >
                    {EATS_GOOD_POINT_LABELS.find(([pid]) => pid === id)?.[1] ?? id} {count}
                  </span>
                ))}
            </div>
          )}
          {reviews === null ? (
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Loading reviews…</p>
          ) : reviews.length === 0 ? (
            <EmptyState message="No written reviews yet — be the first to share how it went." />
          ) : (
            reviews.map((r) => (
              <div key={r.id} style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-700)' }}>
                <span style={{ color: '#F5A623' }}>{'★'.repeat(r.restaurantRating)}{'☆'.repeat(5 - r.restaurantRating)}</span>
                {r.restaurantComment && <span> — {r.restaurantComment}</span>}
                {/* Real review photo (itunda Eats redesign, 2026-08-28) -- see
                    EatsReview.photoUrl's own doc comment. */}
                {r.photoUrl && (
                  <img
                    src={r.photoUrl} alt="" loading="lazy"
                    style={{ display: 'block', width: '80px', height: '80px', borderRadius: 'var(--itunda-radius-md)', objectFit: 'cover', marginTop: '6px' }}
                    onError={(e) => { e.currentTarget.style.display = 'none'; }}
                  />
                )}
                {r.ownerReply && (
                  <div style={{ marginTop: '2px', marginLeft: '12px', color: 'var(--itunda-grey-500)' }}>
                    ↳ Restaurant: {r.ownerReply}
                  </div>
                )}
                <div style={{ display: 'flex', gap: '10px', marginTop: '2px', alignItems: 'flex-start' }}>
                  <button
                    type="button" onClick={() => handleToggleHelpful(r.id)}
                    style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: helpfulVoted.has(r.id) ? 'var(--itunda-indigo)' : 'var(--itunda-grey-500)' }}
                  >
                    👍 Helpful{r.helpfulCount ? ` (${r.helpfulCount})` : ''}
                  </button>
                  <ReportReviewButton reviewId={r.id} />
                </div>
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

const EATS_REVIEW_REPORT_REASONS: { reason: EatsReviewReportReason; label: string }[] = [
  { reason: 'DEFAMATION', label: 'False or defamatory' },
  { reason: 'PERSONAL_INFO_EXPOSURE', label: 'Shares personal information' },
  { reason: 'OBSCENE_OR_VIOLENT', label: 'Obscene or violent' },
  { reason: 'UNRELATED_ABUSE', label: 'Unrelated or abusive' },
];

// Real 배달의민족 리뷰 신고하기 (report a review) -- see lib/eats.ts's own doc comment on
// reportEatsReview. Same real preset-reason-picker shape as HoodReportButton, but this
// review-specific endpoint is genuinely separate (HoodReportButton's own 4 real targets
// don't cover reviews at all).
function ReportReviewButton({ reviewId }: { reviewId: string }) {
  const [showChoices, setShowChoices] = useState(false);
  const [sending, setSending] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const send = async (reason: EatsReviewReportReason) => {
    setShowChoices(false);
    setSending(true);
    try {
      await reportEatsReview(reviewId, reason);
      setMessage('Thanks. Your report was sent for review.');
    } catch (err) {
      setMessage(err instanceof ApiError && err.code === 'REVIEW_ALREADY_REPORTED' ? 'You already reported this review.' : 'Could not send the report.');
    } finally {
      setSending(false);
    }
  };

  if (message) {
    return <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: message.startsWith('Thanks') ? 'var(--itunda-green)' : 'var(--itunda-red)' }}>{message}</p>;
  }

  if (showChoices) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
        {EATS_REVIEW_REPORT_REASONS.map(({ reason, label }) => (
          <button key={reason} className="itunda-btn itunda-btn-secondary" style={{ fontSize: 'var(--itunda-type-scale-11-size)', padding: '4px 10px' }} onClick={() => send(reason)}>
            {label}
          </button>
        ))}
        <button style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }} onClick={() => setShowChoices(false)}>Cancel</button>
      </div>
    );
  }

  return (
    <button type="button" style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }} disabled={sending} onClick={() => setShowChoices(true)}>
      {sending ? 'Reporting…' : 'Report'}
    </button>
  );
}
