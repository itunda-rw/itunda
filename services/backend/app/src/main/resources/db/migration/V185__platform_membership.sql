-- Real Coupang 와우 (Wow)-style unconditional delivery-fee waiver membership
-- (rw.itunda.eats.PlatformMembershipService) -- see PlatformMembership.kt's own doc
-- comment.

CREATE TABLE platform_memberships (
    id           VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id      VARCHAR(64) NOT NULL,
    active_until DATETIME(6) NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    CONSTRAINT uq_platform_memberships_user_id UNIQUE (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
