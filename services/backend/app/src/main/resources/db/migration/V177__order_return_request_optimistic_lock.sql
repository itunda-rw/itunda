-- A return/exchange request must receive exactly one merchant decision.
ALTER TABLE order_return_requests
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
