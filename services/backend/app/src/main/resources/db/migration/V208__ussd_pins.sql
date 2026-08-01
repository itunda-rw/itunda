-- Real USSD basic-banking PIN (item 231, rw.itunda.ussd.UssdService) -- see
-- UssdPin.kt's own doc comment.

CREATE TABLE ussd_pins (
    id          VARCHAR(64)  NOT NULL PRIMARY KEY,
    user_id     VARCHAR(64)  NOT NULL,
    pin_hash    VARCHAR(100) NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    CONSTRAINT uq_ussd_pins_user_id UNIQUE (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
