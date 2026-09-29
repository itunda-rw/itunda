-- Real Korean 지연이체서비스 (Delayed Transfer Service, rw.itunda.p2p.P2pDelayedTransferService,
-- 2026-08-18) -- a mandated anti-voice-phishing safeguard every major Korean bank
-- offers: the sender's real money is held for a real window instead of landing
-- instantly, so a transfer made under active phishing pressure can still be cancelled
-- before it's irreversible. Purely additive, opt-in alongside the existing instant
-- P2pService.sendDirect path, which is completely unchanged.

CREATE TABLE p2p_delayed_transfers (
    id                        VARCHAR(64)    NOT NULL PRIMARY KEY,
    sender_user_id            VARCHAR(64)    NOT NULL,
    sender_wallet_id          VARCHAR(64)    NOT NULL,
    recipient_user_id         VARCHAR(64)    NOT NULL,
    recipient_wallet_id       VARCHAR(64)    NOT NULL,
    amount                    DECIMAL(18, 2) NOT NULL,
    description               VARCHAR(500)   NOT NULL,
    status                    VARCHAR(16)    NOT NULL DEFAULT 'PENDING',
    hold_transaction_id       VARCHAR(64)    NOT NULL,
    resolution_transaction_id VARCHAR(64),
    release_at                DATETIME(6)    NOT NULL,
    created_at                DATETIME(6)    NOT NULL,
    updated_at                DATETIME(6)    NOT NULL,
    version                   BIGINT         NOT NULL DEFAULT 0
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_p2p_delayed_transfers_sender_user_id ON p2p_delayed_transfers (sender_user_id);
CREATE INDEX idx_p2p_delayed_transfers_status ON p2p_delayed_transfers (status);
