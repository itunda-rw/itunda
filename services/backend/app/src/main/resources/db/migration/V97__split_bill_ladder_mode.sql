-- Real KakaoPay 사다리타기 (ladder-game) randomized split mode (2026-07-25) -- see
-- SplitBill's own doc comment and SplitBillService.ladderSplit's own doc comment for
-- the 3 sourced variance levels this closes (docs/DESIGN_REFERENCES.md Section 6).
ALTER TABLE split_bills ADD COLUMN mode VARCHAR(16) NOT NULL DEFAULT 'EVEN';
ALTER TABLE split_bills ADD COLUMN ladder_variance_level INT NULL;
