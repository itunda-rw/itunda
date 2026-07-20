-- Real KakaoPay-style "정산하기" (settlement/split-bill) chat-embedded in an existing
-- group conversation (rw.itunda.splitbill.SplitBillService, 2026-07-21). See
-- SplitBill.kt's own doc comment for why this is a direct-payback shape (each
-- participant pays the organizer directly), not an escrow-then-claim flow like Gift's.

CREATE TABLE split_bills (
    id                     VARCHAR(64)   NOT NULL PRIMARY KEY,
    organizer_id           VARCHAR(64)   NOT NULL,
    group_conversation_id  VARCHAR(64)   NOT NULL,
    message_id             VARCHAR(64)   NOT NULL,
    total_amount           DECIMAL(18,2) NOT NULL,
    description            VARCHAR(200)  NOT NULL,
    status                 VARCHAR(16)   NOT NULL DEFAULT 'OPEN',
    settled_at             DATETIME(6)   NULL,
    created_at             DATETIME(6)   NOT NULL,
    INDEX idx_split_bills_group (group_conversation_id),
    INDEX idx_split_bills_organizer (organizer_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE split_bill_participants (
    id                   VARCHAR(64)   NOT NULL PRIMARY KEY,
    split_bill_id        VARCHAR(64)   NOT NULL,
    user_id              VARCHAR(64)   NOT NULL,
    share_amount         DECIMAL(18,2) NOT NULL,
    status               VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    paid_transaction_id  VARCHAR(64)   NULL,
    paid_at              DATETIME(6)   NULL,
    created_at           DATETIME(6)   NOT NULL,
    CONSTRAINT uq_split_bill_participant UNIQUE (split_bill_id, user_id),
    INDEX idx_split_bill_participants_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
