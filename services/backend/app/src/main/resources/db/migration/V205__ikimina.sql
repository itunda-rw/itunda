-- Real ikimina -- Rwanda's own rotating savings & credit association (ROSCA),
-- rw.itunda.savings.IkiminaService. See Ikimina.kt's own doc comment for the full
-- sourced account. Genuinely distinct from every Toss/Kakao/Naver/Coupang-sourced
-- feature in this backend -- a Rwanda-specific financial primitive.

CREATE TABLE ikiminas (
    id                    VARCHAR(64)   NOT NULL PRIMARY KEY,
    name                  VARCHAR(255)  NOT NULL,
    organizer_id          VARCHAR(64)   NOT NULL,
    wallet_id             VARCHAR(64)   NOT NULL,
    contribution_amount   DECIMAL(18,2) NOT NULL,
    cycle_frequency_days  INT           NOT NULL,
    member_cap            INT           NOT NULL,
    current_round         INT           NOT NULL DEFAULT 1,
    status                VARCHAR(16)   NOT NULL DEFAULT 'FORMING',
    created_at            DATETIME(6)   NOT NULL,
    version               BIGINT        NOT NULL DEFAULT 0
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_ikiminas_organizer_id ON ikiminas (organizer_id);

CREATE TABLE ikimina_members (
    id                   VARCHAR(64)  NOT NULL PRIMARY KEY,
    ikimina_id           VARCHAR(64)  NOT NULL,
    user_id              VARCHAR(64)  NOT NULL,
    payout_order         INT          NOT NULL,
    has_received_payout  TINYINT(1)   NOT NULL DEFAULT 0,
    joined_at            DATETIME(6)  NOT NULL,
    CONSTRAINT uq_ikimina_members_user UNIQUE (ikimina_id, user_id),
    CONSTRAINT uq_ikimina_members_payout_order UNIQUE (ikimina_id, payout_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_ikimina_members_user_id ON ikimina_members (user_id);

CREATE TABLE ikimina_contributions (
    id              VARCHAR(64)   NOT NULL PRIMARY KEY,
    ikimina_id      VARCHAR(64)   NOT NULL,
    member_id       VARCHAR(64)   NOT NULL,
    round           INT           NOT NULL,
    amount          DECIMAL(18,2) NOT NULL,
    contributed_at  DATETIME(6)   NOT NULL,
    CONSTRAINT uq_ikimina_contributions_round UNIQUE (ikimina_id, member_id, round)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_ikimina_contributions_round ON ikimina_contributions (ikimina_id, round);
