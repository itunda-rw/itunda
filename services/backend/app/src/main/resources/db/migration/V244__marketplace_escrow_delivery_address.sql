-- Real gap found 2026-08-15: MarketplaceEscrow had no delivery-address field at all,
-- so a buyer paying via itunda's real escrow safety layer had no way to specify where
-- a non-local (shipped) item should go -- the escrow model only ever assumed an
-- in-person handoff. Nullable and optional: a local, in-person escrow trade (the
-- original, still-supported use case) leaves this null.
ALTER TABLE marketplace_escrows ADD COLUMN delivery_address VARCHAR(500);
