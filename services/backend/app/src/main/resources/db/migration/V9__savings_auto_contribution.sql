-- Real recurring auto-save -- previously SavingsGoal.monthlyContribution was accepted and
-- stored at goal creation but nothing ever read or acted on it (see
-- docs/TOSS_PARITY_MATRIX.md's Savings row: "recurring auto-save scheduling still target").
ALTER TABLE savings_goals ADD COLUMN last_auto_contribution_at DATETIME(6) NULL;
