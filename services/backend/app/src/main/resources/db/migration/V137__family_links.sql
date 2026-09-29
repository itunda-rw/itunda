-- Real Toss 유스 (Toss Youth)-style guardian-child account link
-- (rw.itunda.family.FamilyLinkService, 2026-07-27). See FamilyLink.kt's own doc
-- comment for the full sourced account and honest scope boundary.

CREATE TABLE family_links (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    guardian_user_id VARCHAR(64) NOT NULL,
    child_user_id VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(6) NOT NULL,
    responded_at DATETIME(6) NULL,
    INDEX idx_family_links_guardian_status (guardian_user_id, status),
    INDEX idx_family_links_child_status (child_user_id, status)
);
