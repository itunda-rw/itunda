-- Real search-engine parity across itunda's other content verticals (2026-08-14) --
-- see V242's own migration comment for the "why" (real relevance ranking instead of
-- an un-ranked substring match). Direct user reference: real Naver search blends
-- multiple content types on one results page (products, places, posts), not a
-- single flat list -- these four verticals are itunda's own real equivalents of
-- Naver's "Place"/"Blog"/"Cafe" result types.
ALTER TABLE listings ADD FULLTEXT INDEX ft_listings_search (title, description);
ALTER TABLE community_posts ADD FULLTEXT INDEX ft_community_posts_search (title, body);
ALTER TABLE job_posts ADD FULLTEXT INDEX ft_job_posts_search (title, description);
ALTER TABLE property_listings ADD FULLTEXT INDEX ft_property_listings_search (title, description);
