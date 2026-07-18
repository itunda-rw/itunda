-- Real post-delivery ratings & reviews (rw.itunda.eats.EatsReviewService, 2026-07-18) --
-- the single biggest remaining Coupang Eats-defining gap. One real review per real
-- eats_orders row, rating both the restaurant and the completing rider in one
-- submission. See EatsReview.kt's own doc comment.

CREATE TABLE eats_reviews (
    id                    VARCHAR(64)   NOT NULL PRIMARY KEY,
    order_id              VARCHAR(64)   NOT NULL,
    buyer_id              VARCHAR(64)   NOT NULL,
    restaurant_id         VARCHAR(64)   NOT NULL,
    rider_id              VARCHAR(64)   NOT NULL,
    restaurant_rating     INT           NOT NULL,
    restaurant_comment    VARCHAR(1000) NULL,
    rider_rating          INT           NOT NULL,
    rider_comment         VARCHAR(1000) NULL,
    created_at            DATETIME(6)   NOT NULL,
    CONSTRAINT uq_eats_reviews_order_id UNIQUE (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_eats_reviews_restaurant_id ON eats_reviews (restaurant_id);
CREATE INDEX idx_eats_reviews_rider_id ON eats_reviews (rider_id);
