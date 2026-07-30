-- Real Kakao T-style post-trip driver rating (rw.itunda.rideshare.RideTripReviewService,
-- item 213) -- one real review per real ride_trips row, rating the completing driver.
-- Mirrors eats_reviews' own shape exactly. See RideTripReview.kt's own doc comment.

CREATE TABLE ride_trip_reviews (
    id            VARCHAR(64)   NOT NULL PRIMARY KEY,
    trip_id       VARCHAR(64)   NOT NULL,
    passenger_id  VARCHAR(64)   NOT NULL,
    driver_id     VARCHAR(64)   NOT NULL,
    rating        INT           NOT NULL,
    comment       VARCHAR(1000) NULL,
    created_at    DATETIME(6)   NOT NULL,
    CONSTRAINT uq_ride_trip_reviews_trip_id UNIQUE (trip_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_ride_trip_reviews_driver_id ON ride_trip_reviews (driver_id);
