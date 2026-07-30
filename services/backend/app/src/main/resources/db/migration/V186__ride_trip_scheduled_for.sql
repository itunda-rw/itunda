-- Real Kakao T 예약 호출 (scheduled ride booking) -- see RideTrip.kt's own doc comment.

ALTER TABLE ride_trips
    ADD COLUMN scheduled_for DATETIME(6) NULL,
    ADD COLUMN scheduled_dispatch_started_at DATETIME(6) NULL;
