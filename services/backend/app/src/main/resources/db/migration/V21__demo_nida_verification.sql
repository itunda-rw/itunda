-- Real, honestly-scoped demo NIDA verification (2026-07-17). Real NIDA database access
-- remains genuinely blocked on a government/vendor relationship (unchanged -- see
-- docs/TOSS_PARITY_MATRIX.md's Compliance row). This adds an automated pre-check that
-- runs a real structural validator against Rwanda's own publicly documented 16-digit
-- National ID format, plus a deterministic simulated database-match outcome, and stores
-- the result alongside the submission for the human reviewer to see -- same "real
-- simulation, not a real integration" discipline SimulatedProviderConnector already
-- established for MTN/Airtel/bank rails, applied here for the first time to identity.
ALTER TABLE kyc_submissions ADD COLUMN auto_verification_status VARCHAR(24) NULL;
ALTER TABLE kyc_submissions ADD COLUMN auto_verification_detail VARCHAR(255) NULL;
