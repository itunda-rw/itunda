-- Real Bank product-completeness pass (2026-09-06): a loan-default review
-- queue for ops-mfe needs an actual audit trail (who reviewed a defaulted VUP
-- loan, when, and why), matching the exact real precedent every other
-- money/compliance review queue in this backend already establishes (see
-- PropertyOwnershipSubmission.reviewedBy/decisionReason/reviewedAt --
-- Identity/Insurance/Partners/RealEstate's decide() all reject a >255-char
-- reason, the current real bound, see V317's sibling ops-mfe fix). All three
-- columns default to NULL so every loan already in the table keeps behaving
-- exactly as before -- only loans an admin actually reviews get these
-- populated. review_note has no explicit length, matching
-- decision_reason's own bare `@Column` (Hibernate's default VARCHAR(255)).
ALTER TABLE vup_loans ADD COLUMN reviewed_by VARCHAR(64) NULL;
ALTER TABLE vup_loans ADD COLUMN review_note VARCHAR(255) NULL;
ALTER TABLE vup_loans ADD COLUMN reviewed_at DATETIME(6) NULL;
