-- Real bug fix (rw.itunda.eats.EatsReviewService.submitReview, 2026-07-26): a real
-- Baemin-style PICKUP order (V124, 2026-07-26) can reach DELIVERED with no rider ever
-- assigned at all (EatsOrderService.completePickup), but submitReview required
-- rider_id/rider_rating to always be present -- making every PICKUP order permanently
-- unreviewable, a real regression this feature's own doc comment's stale invariant
-- ("order.riderId is guaranteed non-null here") hid until live-verified. rider_id and
-- rider_rating are now nullable: a PICKUP order's review simply has no rider to rate.

ALTER TABLE eats_reviews MODIFY COLUMN rider_id VARCHAR(64) NULL;
ALTER TABLE eats_reviews MODIFY COLUMN rider_rating INT NULL;
