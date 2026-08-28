-- Real preset-tag review checklist for Eats reviews (itunda Maps redesign, 2026-08-28,
-- direct Naver Map reference: "이런 점이 좋았어요") -- ports hood_transaction_reviews'
-- own good_points column exactly (pipe-separated preset tag ids, plain VARCHAR, no join
-- table for a small enumerated set).

ALTER TABLE eats_reviews ADD COLUMN good_points VARCHAR(500) NULL;
