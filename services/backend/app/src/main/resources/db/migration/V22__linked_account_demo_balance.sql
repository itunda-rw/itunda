-- Real demo external-balance sync (2026-07-17). A real live balance fetch from a real
-- external bank/MoMo provider remains genuinely blocked -- itunda has no real Open
-- Banking / provider API access, unchanged (see LinkedAccount.kt's own doc comment).
-- This adds a real, deterministically-generated demo balance so a linked account can
-- actually show something in the UI instead of permanently blank, clearly labeled as
-- demo (never counted in real netWorth) -- same "real simulation, not a real
-- integration" discipline as DemoNidaVerificationService/DemoCardAuthorizationService.
ALTER TABLE linked_accounts ADD COLUMN demo_balance DECIMAL(18,2) NULL;
ALTER TABLE linked_accounts ADD COLUMN demo_balance_currency VARCHAR(8) NULL;
