-- Real Rwanda VUP (Vision 2020 Umurenge Programme) Financial Services micro-loan --
-- see VupLoan.kt's own doc comment for the full sourced account. VUP, run by LODA
-- since 2008, subsidizes microloans for income-generating activities (farming,
-- livestock, small business) targeted at households in poorer Ubudehe categories.
-- NISR's own EICV7 (2023/24) thematic report on VUP cites an average loan size of
-- ~100,000 RWF. Since a real 2014-07-29 Cabinet decision, administration moved to
-- Umurenge SACCOs, which set the rate at 11% (up from an original flexible 2%) --
-- a real, documented controversy (Rwanda Inspirer reporting: uptake fell after the
-- rate hike). Sources: loda.gov.rw, NISR EICV7 VUP thematic report, Rwanda Inspirer.
--
-- Honest v1 limitation: declared_ubudehe_category is self-declared by the user, not
-- verified against Rwanda's real government Ubudehe household-classification
-- registry, which this backend has no access to.

CREATE TABLE vup_loans (
    id                          VARCHAR(64)   NOT NULL PRIMARY KEY,
    user_id                     VARCHAR(64)   NOT NULL,
    declared_ubudehe_category   INT           NOT NULL,
    purpose                     VARCHAR(16)   NOT NULL,
    principal_amount            DECIMAL(18,2) NOT NULL,
    outstanding_principal       DECIMAL(18,2) NOT NULL,
    interest_rate               DOUBLE        NOT NULL DEFAULT 0.11,
    status                      VARCHAR(16)   NOT NULL DEFAULT 'REQUESTED',
    applied_at                  DATETIME(6)   NOT NULL,
    disbursed_at                DATETIME(6)   NULL,
    due_date                    DATE          NULL,
    version                     BIGINT        NOT NULL DEFAULT 0
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_vup_loans_user_id ON vup_loans (user_id);
CREATE INDEX idx_vup_loans_user_status ON vup_loans (user_id, status);
CREATE INDEX idx_vup_loans_status_due_date ON vup_loans (status, due_date);
