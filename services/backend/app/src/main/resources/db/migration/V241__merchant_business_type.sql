-- Real Eats-vs-Shop vertical split -- see Merchant.kt's own doc comment
-- (MerchantBusinessType) for the full account: Eats' restaurant browse and Shop's
-- retail browse shared the same unfiltered Merchant directory, so non-food merchants
-- (Electronics, Fashion) appeared in Eats' own restaurant list and category chips.
-- Nullable: existing merchants haven't declared one, and Shop's own browse stays
-- intentionally unfiltered by this column so nothing is silently hidden from it.

ALTER TABLE merchants ADD COLUMN business_type VARCHAR(16) NULL;
