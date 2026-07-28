-- Real age-eligibility gate for the Mini wallet (rw.itunda.wallet.MiniWalletService,
-- 2026-07-28) -- closes the "User has no birthdate/age field" gap that row's own doc
-- comment named as a deliberate, honest v1 scope-down. Nullable: existing accounts
-- have none until they set one, the same opt-in shape neighborhood/profile_photo_url
-- already use on this same table.

ALTER TABLE users ADD COLUMN birth_date DATE NULL;
