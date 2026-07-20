-- Real Kakao Bank 모임통장 (group/shared account) equivalent
-- (rw.itunda.savings.GroupAccountService, 2026-07-20) -- see GroupAccount.kt's own doc
-- comment. No shared/joint account concept existed anywhere in this backend before.

CREATE TABLE group_accounts (
    id             VARCHAR(64) NOT NULL PRIMARY KEY,
    name           VARCHAR(255) NOT NULL,
    owner_id       VARCHAR(64) NOT NULL,
    wallet_id      VARCHAR(64) NOT NULL,
    created_at     DATETIME(6) NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_group_accounts_owner_id ON group_accounts (owner_id);

CREATE TABLE group_account_members (
    id                 VARCHAR(64) NOT NULL PRIMARY KEY,
    group_account_id   VARCHAR(64) NOT NULL,
    user_id            VARCHAR(64) NOT NULL,
    joined_at          DATETIME(6) NOT NULL,
    CONSTRAINT uq_group_account_members UNIQUE (group_account_id, user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_group_account_members_user_id ON group_account_members (user_id);
