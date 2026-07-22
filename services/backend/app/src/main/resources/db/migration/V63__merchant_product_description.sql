-- Real product description (2026-07-21), closing docs/DESIGN_REFERENCES.md Section 5
-- recommendation #6 (a dedicated product-detail screen) -- the flat catalog grid added
-- 2026-07-21 has an image and a discount price block but nothing longer-form to show on
-- a real detail view. Merchant-entered free text, nullable: an unset description falls
-- back to showing just the name/price/image the detail screen already has, never a
-- fabricated description. V60 intentionally skipped -- already claimed by a different
-- concurrently-running agent's worktree against the shared migration sequence.

ALTER TABLE merchant_products ADD COLUMN description VARCHAR(2000) NULL;
