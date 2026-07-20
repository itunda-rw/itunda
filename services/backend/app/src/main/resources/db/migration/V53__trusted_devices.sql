-- Real device binding (rw.itunda.auth.DeviceService, 2026-07-20) -- modeled on Toss's
-- own real, published Gateway architecture (toss.tech/article/slash23-server): a
-- "Passport" token carrying user + device identity, with a device that hasn't yet
-- proven itself required to step up before it can move money. See DeviceService's own
-- doc comment for the full account.

CREATE TABLE trusted_devices (
    id             VARCHAR(64)  NOT NULL PRIMARY KEY,
    user_id        VARCHAR(64)  NOT NULL,
    device_id      VARCHAR(128) NOT NULL,
    device_name    VARCHAR(200) NULL,
    trusted        BOOLEAN      NOT NULL DEFAULT FALSE,
    first_seen_at  DATETIME(6)  NOT NULL,
    last_seen_at   DATETIME(6)  NOT NULL,
    verified_at    DATETIME(6)  NULL,
    CONSTRAINT uq_trusted_devices_user_device UNIQUE (user_id, device_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_trusted_devices_user_id ON trusted_devices (user_id);
