-- Real Baemin Club (배민클럽)-style free-delivery membership (rw.itunda.eats.
-- EatsMembershipService, 2026-07-26) -- see EatsMembership.kt's own doc comment.

ALTER TABLE merchants
    ADD COLUMN participates_in_eats_membership BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE eats_memberships (
    id           VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id      VARCHAR(64) NOT NULL,
    active_until DATETIME(6) NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    CONSTRAINT uq_eats_memberships_user_id UNIQUE (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
