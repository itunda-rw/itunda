-- Real gap found live (2026-08-21), same class as V289/V290: V290's own sweep only
-- queried information_schema for COLUMN_NAME = 'wallet_id' exactly, missing every
-- prefixed variant (sender_wallet_id, from_wallet_id, etc). A second, broader query
-- (LIKE '%wallet_id%') found these three remaining tables -- cross-checked against
-- each entity's real @Column mapping (Transaction.kt, SupportTicket.kt,
-- P2pDelayedTransfer.kt) to confirm the mismatch. transactions is the highest-impact
-- of the three: every transaction-history read on every platform (including this
-- session's own new getAccountTransactionHistory) depends on from_account_id/
-- to_account_id existing. No index/constraint on any of these three tables names
-- these columns directly (idx_transactions_sender_id/recipient_id reference
-- sender_id/recipient_id, a different pair of columns entirely).
ALTER TABLE transactions RENAME COLUMN from_wallet_id TO from_account_id;
ALTER TABLE transactions RENAME COLUMN to_wallet_id TO to_account_id;
ALTER TABLE support_tickets RENAME COLUMN froze_wallet_id TO froze_account_id;
ALTER TABLE p2p_delayed_transfers RENAME COLUMN sender_wallet_id TO sender_account_id;
ALTER TABLE p2p_delayed_transfers RENAME COLUMN recipient_wallet_id TO recipient_account_id;
