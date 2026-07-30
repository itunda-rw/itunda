-- Settlement rounds and final-settled state share the parent split-bill record.
ALTER TABLE split_bills
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Retried/concurrent payer requests must settle a participant share at most once.
ALTER TABLE split_bill_participants
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
