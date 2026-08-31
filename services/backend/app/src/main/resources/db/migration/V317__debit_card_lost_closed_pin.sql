-- Real "분실신고"/"카드 재발급"/"카드 해지하기"/"카드 비밀번호 변경" (report
-- lost-or-stolen / reissue / close / change PIN) card-management features
-- (2026-09-01, direct user-supplied Toss Bank card-management screenshots).
-- `lost` and `closed_at` are deliberately separate from the existing `frozen`
-- column: `frozen` stays a self-service toggle the user can flip back
-- (unfreeze), while these two are one-way states (see CardService.unfreeze's
-- own block on unfreezing either). All four columns default to their "no
-- change" value so every card already issued before this migration keeps
-- behaving exactly as before.
ALTER TABLE debit_cards ADD COLUMN lost BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE debit_cards ADD COLUMN closed_at DATETIME(6) NULL;
ALTER TABLE debit_cards ADD COLUMN pin_hash VARCHAR(255) NULL;
ALTER TABLE debit_cards ADD COLUMN reissued_at DATETIME(6) NULL;
