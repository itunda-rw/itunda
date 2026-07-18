-- Real order cancellation/refund for both Commerce and Eats (2026-07-18) -- a genuinely
-- separate feature deliberately deferred when both modules first shipped. Reuses the
-- reversing-ledger-entry technique SupportService.reverseTransaction already established:
-- every original ledger leg is flipped and reposted as a new balanced transaction, whose
-- id is recorded here for a real audit trail. See OrderService.cancelOrder/
-- EatsOrderService.cancelOrder's own doc comments.

ALTER TABLE orders ADD COLUMN refund_transaction_id VARCHAR(64) NULL;
ALTER TABLE eats_orders ADD COLUMN refund_transaction_id VARCHAR(64) NULL;
