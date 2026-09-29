-- Real gap found live (2026-08-21): AgentCashIn/AgentCashOut (core/domain/Agent.kt)
-- were already renamed from walletId to accountId as part of this session's real
-- Toss Bank/Toss Pay account-type separation, matching every other renamed entity --
-- but agent_cash_ins/agent_cash_outs (V63/V64) were the two tables that rename pass
-- missed, so Hibernate's ddl-auto: validate rejected startup outright with
-- "missing column [account_id] in table [agent_cash_ins]" the moment this build
-- was deployed. No index or constraint on either table names wallet_id itself
-- (V63/V64's own indexes are on agent_id/receipt_number/ledger_transaction_id), so
-- a plain rename is safe.
ALTER TABLE agent_cash_ins RENAME COLUMN wallet_id TO account_id;
ALTER TABLE agent_cash_outs RENAME COLUMN wallet_id TO account_id;
