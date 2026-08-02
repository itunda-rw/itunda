-- Real bug found live (2026-08-02): StocksService.buyStock/sellStock and
-- AutoTopUpService.evaluateAndTopUp/topUpShortfall all read-then-mutate their own
-- entity with no @Version guard. Most severe: two concurrent sellStock calls on the
-- same holding could both pass the "not enough shares" guard before either committed,
-- both post a real ledger payout, and pay out for shares that were never actually
-- available to sell twice.
ALTER TABLE holdings
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE wallet_auto_topup_settings
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
