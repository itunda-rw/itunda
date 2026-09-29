-- Real seller-paid sponsored placement (rw.itunda.marketplace.MarketplaceService
-- .boostListing, 2026-07-25) -- independently converged on by Coupang's real self-serve
-- seller Ads product and Baemin's real 오픈리스트/울트라콜 flat-fee listing slots.

ALTER TABLE listings ADD COLUMN boosted_until DATETIME(6) NULL;
