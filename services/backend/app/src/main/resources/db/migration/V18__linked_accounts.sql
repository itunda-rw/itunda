-- Real external bank/MoMo account consent registry -- closes
-- docs/TOSS_PARITY_MATRIX.md's Account aggregation row's own named gap ("no consent
-- registry, no provider access"). Never stores a live external balance -- see
-- LinkedAccount.kt's own doc comment for why.
CREATE TABLE linked_accounts (
    id                             VARCHAR(64)  NOT NULL PRIMARY KEY,
    user_id                        VARCHAR(64)  NOT NULL,
    provider                       VARCHAR(64)  NOT NULL,
    external_account_number_masked VARCHAR(32)  NOT NULL,
    status                         VARCHAR(24)  NOT NULL,
    failure_reason                 VARCHAR(255),
    linked_at                      DATETIME(6)  NOT NULL,
    unlinked_at                    DATETIME(6)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_linked_accounts_user ON linked_accounts (user_id);
