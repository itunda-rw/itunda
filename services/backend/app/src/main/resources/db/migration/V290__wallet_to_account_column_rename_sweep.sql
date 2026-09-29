-- Real gap found live (2026-08-21), same class as V289: this session's real Toss
-- Bank/Toss Pay account-type separation renamed walletId -> accountId across every
-- entity in core/domain (and core/agents), but the Flyway migrations for most of
-- those tables were never written -- ddl-auto: validate (root CLAUDE.md: never
-- update) correctly refused to start rather than silently drift, surfacing one
-- missing-column table at a time on each deploy attempt (agent_cash_ins/outs fixed
-- in V289; agent_withdrawal_authorizations next). Rather than keep discovering
-- these one at a time across repeated deploys, queried live information_schema
-- directly for every table where COLUMN_NAME = 'wallet_id' still exists, then
-- cross-checked each against its entity's real @Column(name = "account_id")
-- mapping (core/domain/*.kt) to confirm the mismatch -- this is the complete
-- resulting list, all 27 remaining tables in one pass. No index/constraint is
-- renamed here (MySQL RENAME COLUMN keeps existing indexes/constraints pointing at
-- the renamed column automatically; their names staying "wallet"-prefixed is
-- cosmetic only, e.g. uq_wallet_auto_topup_settings_wallet,
-- idx_agent_withdrawal_authorizations_wallet_id).
ALTER TABLE agent_withdrawal_authorizations RENAME COLUMN wallet_id TO account_id;
ALTER TABLE auto_transfers RENAME COLUMN wallet_id TO account_id;
ALTER TABLE bikes RENAME COLUMN wallet_id TO account_id;
ALTER TABLE bus_trips RENAME COLUMN wallet_id TO account_id;
ALTER TABLE cooperative_memberships RENAME COLUMN wallet_id TO account_id;
ALTER TABLE customer_payment_codes RENAME COLUMN wallet_id TO account_id;
ALTER TABLE designated_drivers RENAME COLUMN wallet_id TO account_id;
ALTER TABLE group_accounts RENAME COLUMN wallet_id TO account_id;
ALTER TABLE grow31_savings_plans RENAME COLUMN wallet_id TO account_id;
ALTER TABLE harvest_advances RENAME COLUMN wallet_id TO account_id;
ALTER TABLE holdings RENAME COLUMN wallet_id TO account_id;
ALTER TABLE ikiminas RENAME COLUMN wallet_id TO account_id;
ALTER TABLE interest_jars RENAME COLUMN wallet_id TO account_id;
ALTER TABLE loan_accounts RENAME COLUMN wallet_id TO account_id;
ALTER TABLE merchants RENAME COLUMN wallet_id TO account_id;
ALTER TABLE overdraft_accounts RENAME COLUMN wallet_id TO account_id;
ALTER TABLE parking_spots RENAME COLUMN wallet_id TO account_id;
ALTER TABLE postpaid_credit_lines RENAME COLUMN wallet_id TO account_id;
ALTER TABLE ride_drivers RENAME COLUMN wallet_id TO account_id;
ALTER TABLE riders RENAME COLUMN wallet_id TO account_id;
ALTER TABLE sacco_shareholdings RENAME COLUMN wallet_id TO account_id;
ALTER TABLE savings_goals RENAME COLUMN wallet_id TO account_id;
ALTER TABLE scheduled_transfers RENAME COLUMN wallet_id TO account_id;
ALTER TABLE upfront_interest_deposits RENAME COLUMN wallet_id TO account_id;
ALTER TABLE vehicle_inspection_mechanics RENAME COLUMN wallet_id TO account_id;
ALTER TABLE wallet_auto_topup_settings RENAME COLUMN wallet_id TO account_id;
ALTER TABLE weekly_savings_plans RENAME COLUMN wallet_id TO account_id;
