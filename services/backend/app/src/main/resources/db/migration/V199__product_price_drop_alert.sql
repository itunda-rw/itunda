-- Real Naver Shopping 가격 변동 알림 (price-drop alert) -- see
-- ProductFavorite.kt's own doc comment. price_at_last_check backfills existing rows
-- with the product's CURRENT price (the honest baseline: we never recorded the real
-- price at the original favorite time, so treating "now" as the starting point avoids
-- a false "price dropped" notification for every pre-existing favorite the moment this
-- migration runs).
ALTER TABLE product_favorites
    ADD COLUMN price_at_last_check DECIMAL(18, 2) NOT NULL DEFAULT 0;

UPDATE product_favorites pf
    JOIN merchant_products mp ON mp.id = pf.product_id
    SET pf.price_at_last_check = mp.price;
