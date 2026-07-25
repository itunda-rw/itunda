-- Real KakaoPay 송금봉투 (money envelope) themed presets (rw.itunda.gift.GiftService,
-- 2026-07-26) -- see Gift.kt's own GiftTheme doc comment.

ALTER TABLE gifts
    ADD COLUMN theme VARCHAR(20);
