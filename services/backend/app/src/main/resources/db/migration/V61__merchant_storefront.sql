-- Real restaurant/merchant browse-card enrichment (2026-07-21) -- closes
-- docs/DESIGN_REFERENCES.md's Eats recommendation #1: ShoppingMerchantDto had no photo
-- or minimum-order-amount field at all. Both are merchant-set and nullable: unset means
-- the pre-existing behavior (generic storefront icon, no minimum), never a fabricated
-- default. See core/.../domain/Merchant.kt's own doc comment.

ALTER TABLE merchants ADD COLUMN photo_url VARCHAR(500) NULL;
ALTER TABLE merchants ADD COLUMN min_order_amount DECIMAL(18, 2) NULL;
