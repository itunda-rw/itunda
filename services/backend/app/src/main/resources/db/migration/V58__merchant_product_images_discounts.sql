-- Real product images + discount pricing (rw.itunda.merchant.MerchantProductService,
-- 2026-07-21), closing docs/DESIGN_REFERENCES.md Section 5 recommendation #4. See
-- core/.../domain/MerchantProduct.kt's own doc comment for the full account, incl. why
-- image_url is a merchant-supplied external URL (no upload/storage layer exists in this
-- backend) and why discount_percent is server-computed/stored rather than client-supplied.

ALTER TABLE merchant_products
    ADD COLUMN image_url       VARCHAR(2048)  NULL,
    ADD COLUMN original_price  DECIMAL(18, 2) NULL,
    ADD COLUMN discount_percent INT           NULL;
