-- Real 당근마켓-style Keyword Alert (rw.itunda.marketplace.KeywordAlertService,
-- 2026-07-26) -- Karrot's own official FAQ (cs.kr.karrotmarket.com/wv/faqs/43): a user
-- registers up to 30 real search keywords and is pushed a notification whenever a NEW
-- listing matching one is posted. The 30-per-user cap is enforced in
-- KeywordAlertService, not here, matching this codebase's existing convention of
-- application-level limits (RateLimiter) over DB CHECK constraints.

CREATE TABLE keyword_alerts (
    id         VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id    VARCHAR(64) NOT NULL,
    keyword    VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_keyword_alerts_user_keyword UNIQUE (user_id, keyword)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_keyword_alerts_user_id ON keyword_alerts (user_id);
