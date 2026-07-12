-- Real merchant webhooks -- previously not built at all (see
-- docs/TOSS_PARITY_MATRIX.md's Merchant row). Payload shape matches Toss Payments' real
-- documented PAYMENT_STATUS_CHANGED event (docs/PAYMENTS.md's sourced research), adapted to
-- itunda's QR collection flow.
ALTER TABLE merchants ADD COLUMN webhook_url VARCHAR(500) NULL;
