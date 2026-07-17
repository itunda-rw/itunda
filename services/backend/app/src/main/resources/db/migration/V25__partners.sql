-- Real third-party developer platform (rw.itunda.partners, 2026-07-17). See
-- core/.../domain/Partner.kt for the entities this transcribes column-for-column,
-- same discipline as V2__merchant.sql / V23__payroll.sql.

CREATE TABLE partners (
    id             VARCHAR(64)  NOT NULL PRIMARY KEY,
    company_name   VARCHAR(255) NOT NULL,
    contact_email  VARCHAR(255) NOT NULL,
    api_key_hash   VARCHAR(64)  NOT NULL,
    status         VARCHAR(16)  NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    CONSTRAINT uq_partners_contact_email UNIQUE (contact_email),
    CONSTRAINT uq_partners_api_key_hash UNIQUE (api_key_hash)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE partner_mini_apps (
    id               VARCHAR(64)  NOT NULL PRIMARY KEY,
    partner_id       VARCHAR(64)  NOT NULL,
    name             VARCHAR(255) NOT NULL,
    description      VARCHAR(500) NOT NULL,
    icon_url         VARCHAR(500) NULL,
    bundle_url       VARCHAR(500) NOT NULL,
    permissions      VARCHAR(500) NOT NULL,
    status           VARCHAR(16)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    reviewed_by      VARCHAR(64)  NULL,
    reviewed_at      DATETIME(6)  NULL,
    decision_reason  VARCHAR(255) NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_partner_mini_apps_partner_id_created_at ON partner_mini_apps (partner_id, created_at);
CREATE INDEX idx_partner_mini_apps_status ON partner_mini_apps (status);
