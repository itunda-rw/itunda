-- Real Naver Pay-style boosted merchant cashback opt-in (rw.itunda.merchant.
-- ShoppingCashbackService, 2026-07-26) -- see that class's own doc comment.

ALTER TABLE merchants
    ADD COLUMN cashback_rate DECIMAL(6, 4);
