-- Real multi-select optional menu add-ons (rw.itunda.merchant.MenuOptionService,
-- 2026-07-26) -- closes the "multi-select optional add-ons... not built here" gap
-- MenuOptionGroup.kt's own doc comment originally named, per Coupang Eats/Baemin's own
-- real option-group model. Defaults preserve every pre-existing group's exact original
-- "exactly one required choice" behavior unchanged.

ALTER TABLE menu_option_groups
    ADD COLUMN required     BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN multi_select BOOLEAN NOT NULL DEFAULT FALSE;
