-- Real KakaoPay-style up-to-5 sequential settlement "rounds" per split-bill thread --
-- see SplitBillService.requestNextRound's own doc comment. Every existing row starts at
-- round 1 (the original request already sent at creation time).

ALTER TABLE split_bills ADD COLUMN current_round INT NOT NULL DEFAULT 1;
