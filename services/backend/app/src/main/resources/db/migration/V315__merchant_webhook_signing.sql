-- Real webhook signature verification (2026-08-30, market-readiness audit) -- see
-- WebhookDeliveryService's own doc comment. Found live: PaymentsApiController's real
-- external checkout API had a working retry-backed webhook delivery mechanism with NO
-- way for a merchant's receiver to verify a "payment completed" POST actually came from
-- itunda -- any attacker who learns/guesses a merchant's webhook URL could forge a fake
-- PAYMENT_STATUS_CHANGED event and trick that merchant's server into fulfilling an order
-- nobody paid for. webhook_secret is stored in plaintext (unlike api_key_hash, which is
-- one-way): itunda is the SIGNER here, re-using this same secret on every future
-- delivery, not just verifying an inbound value once -- the same real, necessary
-- reversible-storage tradeoff Stripe/real payment gateways' own signing-secret model
-- requires. signature on webhook_deliveries is computed once at creation time and
-- resent unchanged on every retry, so a merchant rotating their secret mid-retry-window
-- can never invalidate an already-queued delivery's signature.

ALTER TABLE merchants ADD COLUMN webhook_secret VARCHAR(64) NULL;
ALTER TABLE webhook_deliveries ADD COLUMN signature VARCHAR(64) NULL;
