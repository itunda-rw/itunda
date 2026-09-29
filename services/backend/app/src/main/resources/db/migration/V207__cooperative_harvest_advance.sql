-- Real Rwanda coffee-cooperative harvest-advance / input financing
-- (rw.itunda.loans.CooperativeService) -- see Cooperative.kt's own doc comment.

CREATE TABLE cooperatives (
    id                   VARCHAR(64)  NOT NULL PRIMARY KEY,
    name                 VARCHAR(200) NOT NULL,
    crop_type            VARCHAR(50)  NOT NULL,
    registration_number  VARCHAR(100) NULL,
    created_at           DATETIME(6)  NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE cooperative_memberships (
    id             VARCHAR(64)  NOT NULL PRIMARY KEY,
    cooperative_id VARCHAR(64)  NOT NULL,
    user_id        VARCHAR(64)  NOT NULL,
    wallet_id      VARCHAR(64)  NOT NULL,
    member_since   DATETIME(6)  NOT NULL,
    active         TINYINT(1)   NOT NULL DEFAULT 1,
    CONSTRAINT uq_cooperative_memberships_coop_user UNIQUE (cooperative_id, user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_cooperative_memberships_user_id ON cooperative_memberships (user_id);

CREATE TABLE harvest_advances (
    id                       VARCHAR(64)   NOT NULL PRIMARY KEY,
    membership_id            VARCHAR(64)   NOT NULL,
    wallet_id                VARCHAR(64)   NOT NULL,
    principal_amount         DECIMAL(18,2) NOT NULL,
    purpose                  VARCHAR(30)   NOT NULL,
    expected_harvest_date    DATETIME(6)   NOT NULL,
    repayment_due_date       DATETIME(6)   NOT NULL,
    status                   VARCHAR(16)   NOT NULL DEFAULT 'REQUESTED',
    disbursed_at             DATETIME(6)   NULL,
    repaid_at                DATETIME(6)   NULL,
    disbursement_transaction_id VARCHAR(64) NULL,
    repayment_transaction_id    VARCHAR(64) NULL,
    created_at               DATETIME(6)   NOT NULL,
    version                  BIGINT        NOT NULL DEFAULT 0
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_harvest_advances_membership_id ON harvest_advances (membership_id);
