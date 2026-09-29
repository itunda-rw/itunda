-- Guard concurrent ride dispatch and settlement transitions.  Hibernate includes this
-- value in updates so only one terminal action can settle the held fare.
ALTER TABLE ride_trips
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
