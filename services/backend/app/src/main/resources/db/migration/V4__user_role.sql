-- Adds the role every user needs for RBAC on /api/v1/system/** (2026-07-11 fix --
-- see SecurityConfig.kt and SECURITY.md's "no role-based access control on
-- /system/* ops endpoints -- any authenticated consumer can view fraud/compliance/
-- reconciliation data"). Every existing and new user defaults to USER; there is no
-- self-service admin-promotion flow yet, so granting ADMIN is a manual
-- `UPDATE users SET role = 'ADMIN' WHERE id = ...` -- the same out-of-band-provisioning
-- convention infra/k8s/production already uses for secrets, not a fabricated
-- onboarding flow this system doesn't actually have.
ALTER TABLE users ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'USER';
