-- Redemption and automatic expiry both settle voucher holding funds.
ALTER TABLE gift_vouchers
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
