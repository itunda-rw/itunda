-- Real menu-item option groups (2026-07-21, v1: required single-select only) -- closes
-- docs/DESIGN_REFERENCES.md's Eats recommendation #3, flagged as the single biggest
-- structural gap: MerchantProduct had no way to represent spice level, size, or add-ons
-- at all. See core/.../domain/MenuOptionGroup.kt's own doc comment for the full,
-- honestly-scoped account (required + single-select only in this pass; multi-select
-- optional add-ons are a real, separate follow-up).

CREATE TABLE menu_option_groups (
    id            VARCHAR(64)  NOT NULL PRIMARY KEY,
    product_id    VARCHAR(64)  NOT NULL,
    name          VARCHAR(100) NOT NULL,
    display_order INT          NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_menu_option_groups_product_id ON menu_option_groups (product_id);

CREATE TABLE menu_option_choices (
    id            VARCHAR(64)    NOT NULL PRIMARY KEY,
    group_id      VARCHAR(64)    NOT NULL,
    name          VARCHAR(100)   NOT NULL,
    price_delta   DECIMAL(18, 2) NOT NULL DEFAULT 0,
    display_order INT            NOT NULL DEFAULT 0,
    created_at    DATETIME(6)    NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_menu_option_choices_group_id ON menu_option_choices (group_id);

-- Real per-line-item options snapshot -- see EatsOrderItem.kt's own doc comment. Nullable:
-- null for every pre-existing order line and every future line whose product has no
-- option groups defined.
ALTER TABLE eats_order_items ADD COLUMN selected_options_json VARCHAR(4000) NULL;
