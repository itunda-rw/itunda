-- Real Toss 유스 (Toss Youth)-style guardian spend-limit enforcement -- see
-- FamilyLink.kt's own doc comment, which names this as a "deliberately deferred, named
-- follow-up" to the read-only oversight FamilyLink already shipped with. NULL means no
-- limit set (unrestricted, matching this link's pre-existing behavior).

ALTER TABLE family_links ADD COLUMN daily_spend_limit DECIMAL(18,2) NULL;
