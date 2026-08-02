-- Real Rwanda National Agricultural Insurance Scheme (NAIS)-style parametric/weather-index
-- crop insurance -- see WeatherIndexInsuranceService's own doc comment for the full sourced
-- account (WFP: GoR covers 40% of premium, cooperatives/farmers 60%, for maize, rice, chilli
-- peppers, French beans, Irish potatoes; insured land grew from 357ha in 2019 to 3,333ha in
-- 2020; the earlier Kilimo Salama pilot insured 37,000+ smallholders on satellite rainfall
-- data specifically because Rwanda's civil-conflict history left no usable rain-gauge
-- network. NISR's own Seasonal Agricultural Survey is the source of the A/B/C season
-- convention.) Genuinely distinct from every other insurance product in this codebase: no
-- individual claim is ever filed -- a district+season's published rainfall index auto-pays
-- out EVERY enrolled policy in that district+season at once, or the season ends with no
-- payout.
--
-- Honest v1 limitations (see WeatherIndexInsuranceService's own doc comment for the full
-- account): (1) season_rainfall_indices.rainfall_index_percent is ADMIN-TRANSCRIBED from a
-- real published NISR/Rwanda Meteorology Agency bulletin, not a live satellite/gauge feed;
-- (2) only the farmer-paid 60% cooperative share is modeled as real money movement --
-- NAIS's real 40% government subsidy is not modeled as an actual ledger transfer.

CREATE TABLE weather_index_policies (
    id               VARCHAR(64)   NOT NULL PRIMARY KEY,
    user_id          VARCHAR(64)   NOT NULL,
    crop_type        VARCHAR(24)   NOT NULL,
    district         VARCHAR(100)  NOT NULL,
    season           VARCHAR(16)   NOT NULL,
    insured_amount   DECIMAL(18,2) NOT NULL,
    premium_amount   DECIMAL(18,2) NOT NULL,
    status           VARCHAR(24)   NOT NULL DEFAULT 'ENROLLED',
    created_at       DATETIME(6)   NOT NULL,
    payout_at        DATETIME(6)   NULL,
    version          BIGINT        NOT NULL DEFAULT 0
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_weather_index_policies_user_id ON weather_index_policies (user_id);
CREATE INDEX idx_weather_index_policies_district_season_status ON weather_index_policies (district, season, status);

CREATE TABLE season_rainfall_indices (
    id                        VARCHAR(64)   NOT NULL PRIMARY KEY,
    district                  VARCHAR(100)  NOT NULL,
    season                    VARCHAR(16)   NOT NULL,
    rainfall_index_percent    DOUBLE        NOT NULL,
    drought_threshold_percent DOUBLE        NOT NULL,
    published_at              DATETIME(6)   NOT NULL,
    published_by_admin_id     VARCHAR(64)   NOT NULL,
    -- Real race guard: a second publish attempt for the same district+season must
    -- real-409, never silently overwrite (and never double-pay every matching policy).
    CONSTRAINT uq_season_rainfall_indices_district_season UNIQUE (district, season)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
